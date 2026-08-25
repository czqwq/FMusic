package com.Lilith.FMusic.server.core.music;
import net.minecraft.util.StatCollector;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.Lilith.FMusic.server.FMusicServer;
import com.Lilith.FMusic.server.core.FMusic;
import com.Lilith.FMusic.server.core.IMusicApi;
import com.Lilith.FMusic.server.core.objs.message.ARG;
import com.Lilith.FMusic.server.core.objs.music.MusicObj;
import com.Lilith.FMusic.server.core.objs.music.SongInfoObj;
import com.Lilith.FMusic.server.core.utils.HudUtils;

public class PlayRuntime {

    /**
     * 倒数计数器
     */
    private static int ping = 0;
    /**
     * 歌曲定时器
     */
    private static ScheduledExecutorService service;
    /**
     * 事务定时器
     */
    private static ScheduledExecutorService service2;

    private static boolean isPlay;

    /**
     * 启动歌曲工作
     */
    public static void start() {
        new Thread(PlayRuntime::musicPlayTask, "fMusic_play").start();

        service = Executors.newSingleThreadScheduledExecutor();
        service.scheduleAtFixedRate(PlayRuntime::time1, 0, 10, TimeUnit.MILLISECONDS);
        service2 = Executors.newSingleThreadScheduledExecutor();
        service2.scheduleAtFixedRate(PlayRuntime::time3, 0, 1, TimeUnit.SECONDS);
    }

    /**
     * 停止歌曲工作
     */
    public static void stop() {
        if (service != null) {
            service.shutdown();
            service = null;
        }
        if (service2 != null) {
            service2.shutdown();
            service2 = null;
        }
        PlayMusic.musicLessTime = 0;
    }

    /**
     * 清空歌曲数据
     */
    private static void clear() {
        isPlay = false;
        PlayMusic.musicNowTime = 0;
        PlayMusic.musicAllTime = 0;
        PlayMusic.musicLessTime = 0;
        PlayMusic.lyric = null;
        PlayMusic.nowPlayMusic = null;
        FMusic.side.updateInfo();
        HudUtils.sendClearHud();
        HudUtils.sendHudNowData();
        HudUtils.sendHudTime();
        HudUtils.sendHudLyricData();
    }

    /**
     * 歌曲时间定时器
     */
    private static void time1() {
        // 游戏暂停(单人, pause_at_freeze)时冻结计时, 保持歌词与音乐同步
        if (FMusic.frozen) {
            return;
        }
        if (isPlay) {
            PlayMusic.musicNowTime += 10;
            if (PlayMusic.musicLessTime >= 10) {
                PlayMusic.musicLessTime -= 10;
            } else {
                PlayMusic.musicLessTime = 0;
            }
        }

        if (PlayMusic.lyric != null && PlayMusic.lyric.isHaveLyric()) {
            try {
                if (PlayMusic.lyric == null) return;
                boolean res = PlayMusic.lyric.lyricGetNext(PlayMusic.musicNowTime);
                if (res) {
                    HudUtils.sendHudLyricData();
                    FMusic.side.updateLyric();
                    FMusic.side.sendHudKtv();
                }

                if (FMusic.getConfig().ktvMode) {
                    if (PlayMusic.lyric.ktvGetNext(PlayMusic.musicNowTime)) {
                        FMusic.side.sendHudKtv();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static boolean checkPush() {
        SongInfoObj music = PlayMusic.getPush();
        if (music != null) {
            if (PlayMusic.nowPlayMusic.getID()
                .equalsIgnoreCase(music.getID())) {
                return false;
            }
            List<SongInfoObj> list = PlayMusic.getList();
            if (list.isEmpty()) {
                return false;
            }
            SongInfoObj id1 = list.get(0);
            if (id1 != null && id1.getID()
                .equalsIgnoreCase(music.getID())) {
                return false;
            }
            for (int a = 1; a < list.size(); a++) {
                id1 = list.get(a);
                if (id1.getID()
                    .equalsIgnoreCase(music.getID())) return true;
            }
        }

        return false;
    }

    public static int getMiniVote() {
        return Math.min(
            FMusic.getConfig().minVote,
            FMusic.side.getPlayers()
                .size());
    }

    /**
     * 事务定时器
     */
    private static void time3() {
        try {
            ping++;
            if (ping >= 10) {
                FMusic.side.ping();
            }
            if (PlayMusic.getPushTime() > 0) {
                if (!checkPush()) {
                    PlayMusic.clearPush();
                    FMusic.side.broadcastInTask(FMusic.getMessage().push.cancel);
                } else {
                    PlayMusic.pushTick();
                    if (PlayMusic.getPushTime() == 0) {
                        PlayMusic.clearPush();
                        FMusic.side.broadcastInTask(FMusic.getMessage().push.timeOut);
                    } else {
                        if (PlayMusic.getPushCount() >= getMiniVote()) {
                            PlayMusic.pushMusic();
                            PlayMusic.clearPush();
                            FMusic.side.broadcastInTask(FMusic.getMessage().push.doPush);
                        }
                    }
                }
            }

            if (PlayMusic.getVoteTime() > 0) {
                PlayMusic.voteTick();
                if (PlayMusic.getVoteTime() == 0) {
                    PlayMusic.clearVote();
                    FMusic.side.broadcastInTask(FMusic.getMessage().vote.timeOut);
                } else {
                    if (PlayMusic.getVoteCount() >= getMiniVote()) {
                        PlayMusic.musicLessTime = 0;
                        PlayMusic.clearVote();
                        FMusic.side.broadcastInTask(FMusic.getMessage().vote.voteDone);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void musicPlayTask() {
        FMusic.log.data(StatCollector.translateToLocal("fmusic.log.core.play_thread_start"));
        while (FMusic.isRun) {
            try {
                if (PlayMusic.getListSize() == 0) {
                    if (PlayMusic.error >= 10) {
                        Thread.sleep(1000);
                    } else if (FMusic.side.needPlay(true) && PlayMusic.getIdleListSize() > 0) {
                        MusicObj music = PlayMusic.getIdleMusic();
                        if (music != null) {
                            IMusicApi api = FMusic.MUSIC_APIS.get(music.api);
                            if (api != null && !api.isBusy()) {
                                PlayMusic.addMusic(null, music.id, api, FMusic.getMessage().custom.idle, true);
                            }
                        }
                    }
                    Thread.sleep(1000);
                } else {
                    HudUtils.sendClearHud();
                    HudUtils.sendHudNowData();
                    HudUtils.sendHudTime();
                    HudUtils.sendHudLyricData();
                    FMusic.side.sendHudUtilsAll();
                    PlayMusic.nowPlayMusic = PlayMusic.remove(0);
                    if (FMusic.side.onMusicPlay(PlayMusic.nowPlayMusic)) {
                        FMusic.side.broadcastInTask(FMusic.getMessage().musicPlay.cancel);
                        continue;
                    }

                    IMusicApi api = FMusic.MUSIC_APIS.get(PlayMusic.nowPlayMusic.getApi());

                    PlayMusic.url = PlayMusic.nowPlayMusic.getPlayerUrl() == null
                        ? api.getPlayUrl(PlayMusic.nowPlayMusic.getID())
                        : PlayMusic.nowPlayMusic.getPlayerUrl();
                    if (PlayMusic.url == null) {
                        String data = FMusic.getMessage().musicPlay.emptyCanPlay;
                        FMusic.side.broadcastInTask(data.replace(ARG.musicId, PlayMusic.nowPlayMusic.getID()));
                        PlayMusic.nowPlayMusic = null;
                        continue;
                    }

                    if (PlayMusic.nowPlayMusic.getPlayerUrl() == null)
                        PlayMusic.lyric = api.getLyric(PlayMusic.nowPlayMusic.getID());
                    else PlayMusic.lyric = new LyricSave();

                    if (PlayMusic.nowPlayMusic.getLength() != 0) {
                        PlayMusic.musicAllTime = PlayMusic.musicLessTime = PlayMusic.nowPlayMusic.getLength()
                            + FMusic.getConfig().fixSongTime;
                        isPlay = true;
                        FMusicServer.LOGGER.debug(StatCollector.translateToLocalFormatted("fmusic.log.server.play_task", PlayMusic.url));
                        FMusic.side.sendMusic(PlayMusic.url);
                        if (!FMusic.getConfig().mutePlayMessage) {
                            SongInfoObj music = PlayMusic.nowPlayMusic;
                            if (FMusic.getConfig().showInBar) {
                                String info = FMusic.getMessage().musicPlay.nowPlay
                                    .replace(ARG.musicName, HudUtils.messageLimit(music.getName()))
                                    .replace(ARG.musicAuthor, HudUtils.messageLimit(music.getAuthor()))
                                    .replace(ARG.musicAl, HudUtils.messageLimit(music.getAl()))
                                    .replace(ARG.musicAlia, HudUtils.messageLimit(music.getAlia()))
                                    .replace(ARG.player, music.getCall());
                                FMusic.side.sendBarInTask(info);
                            } else {
                                String info = FMusic.getMessage().musicPlay.nowPlay
                                    .replace(ARG.musicName, music.getName())
                                    .replace(ARG.musicAuthor, music.getAuthor())
                                    .replace(ARG.musicAl, music.getAl())
                                    .replace(ARG.musicAlia, music.getAlia())
                                    .replace(ARG.player, music.getCall());
                                FMusic.side.broadcastInTask(info);
                            }
                        }
                        if (PlayMusic.nowPlayMusic.getPicUrl() != null) {
                            FMusic.side.sendPic(PlayMusic.nowPlayMusic.getPicUrl());
                        }
                        if (PlayMusic.nowPlayMusic.isTrial()) {
                            FMusic.side.broadcastInTask(FMusic.getMessage().musicPlay.trail);
                            PlayMusic.musicLessTime = PlayMusic.nowPlayMusic.getTrialInfo().end;
                            PlayMusic.musicNowTime = PlayMusic.nowPlayMusic.getTrialInfo().start;
                        }

                        FMusic.side.updateInfo();

                        while (PlayMusic.musicLessTime > 0) {
                            HudUtils.sendHudNowData();
                            HudUtils.sendHudTime();
                            if (PlayMusic.nowPlayMusic == null
                                || !FMusic.side.needPlay(PlayMusic.nowPlayMusic.isList())) {
                                PlayMusic.musicLessTime = 10;
                            }
                            Thread.sleep(FMusic.getConfig().sendDelay);
                        }
                        FMusic.side.sendStop();
                    } else {
                        String data = FMusic.getMessage().musicPlay.emptyCanPlay;
                        FMusic.side.broadcastInTask(data.replace(ARG.musicId, PlayMusic.nowPlayMusic.getID()));
                    }
                    clear();
                }
            } catch (Exception e) {
                FMusic.log.data(StatCollector.translateToLocal("fmusic.log.core.play_error"));
                e.printStackTrace();
            }
        }
        FMusic.log.data(StatCollector.translateToLocal("fmusic.log.core.play_thread_stop"));
    }
}
