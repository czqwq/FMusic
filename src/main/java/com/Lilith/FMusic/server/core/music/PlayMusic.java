package com.Lilith.FMusic.server.core.music;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.util.StatCollector;

import com.Lilith.FMusic.server.core.FMusic;
import com.Lilith.FMusic.server.core.IMusicApi;
import com.Lilith.FMusic.server.core.objs.config.LimitObj;
import com.Lilith.FMusic.server.core.objs.message.ARG;
import com.Lilith.FMusic.server.core.objs.music.MusicObj;
import com.Lilith.FMusic.server.core.objs.music.PlayerAddMusicObj;
import com.Lilith.FMusic.server.core.objs.music.SongInfoObj;
import com.Lilith.FMusic.server.core.saves.MusicListSave;
import com.Lilith.FMusic.server.core.utils.HudUtils;

public class PlayMusic {

    /**
     * 播放列表
     */
    private static final List<SongInfoObj> playList = new ArrayList<>();
    private static final Queue<PlayerAddMusicObj> tasks = new ConcurrentLinkedQueue<>();
    private static final Queue<MusicObj> deep = new ConcurrentLinkedQueue<>();
    /**
     * 正在播放的玩家
     */
    private static final Set<String> nowPlayPlayer = new HashSet<>();
    /**
     * 投票序列
     */
    private static final Queue<VoteItem> voteList = new ConcurrentLinkedQueue<>();
    /**
     * 当前投票
     */
    private static VoteItem vote;
    /**
     * 总歌曲长度
     */
    public static long musicAllTime = 0;
    /**
     * 剩余歌曲长度
     */
    public static long musicLessTime = 0;
    /**
     * 歌曲现在位置
     */
    public static long musicNowTime = 0;
    /**
     * 当前歌曲信息
     */
    public static SongInfoObj nowPlayMusic;
    /**
     * 当前歌词信息
     */
    public static LyricSave lyric;
    /**
     * 播放链接
     */
    public static String url;
    /**
     * 错误次数
     */
    public static int error;
    /**
     * 切歌投票时间
     */
    private static int voteTime = 0;
    /**
     * 空闲列表取出的歌曲序号
     */
    private static int idleIndex;

    /**
     * 开始歌曲逻辑
     */
    public static void start() {
        new Thread(PlayMusic::task, "fmusic_task").start();
    }

    public static boolean haveVote(String name, VoteItem.VoteType voteType) {
        name = name.toLowerCase(Locale.ROOT);
        for (VoteItem item : voteList) {
            if (item.getType() == voteType && item.getVoteSender()
                .equalsIgnoreCase(name)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 通过投票
     *
     * @param player 用户名
     */
    public static void addVote(String player) {
        player = player.toLowerCase(Locale.ROOT);
        if (vote == null) {
            return;
        }

        vote.votePlayer.add(player);
    }

    /**
     * 发起投票
     *
     * @param voteItem 投票内容
     */
    public static boolean startVote(VoteItem voteItem) {
        String id = voteItem.getId();
        String api = voteItem.getApi();
        for (VoteItem item : voteList) {
            if (item.getId()
                .equalsIgnoreCase(id)
                && item.getApi()
                    .equalsIgnoreCase(api)) {
                return false;
            }
        }

        voteList.add(voteItem);
        voteTime = FMusic.getConfig().vote.voteTime;

        return true;
    }

    public static SongInfoObj getMusic(String id, String api) {
        for (SongInfoObj item : playList) {
            if (item.getId()
                .equalsIgnoreCase(id)
                && item.getApi()
                    .equalsIgnoreCase(api)) {
                return item;
            }
        }

        return null;
    }

    public static void doVote() {
        if (vote == null) {
            return;
        }

        if (vote.getType() == VoteItem.VoteType.NEXT) {
            if (nowPlayMusic != null && nowPlayMusic.getApi()
                .equalsIgnoreCase(vote.getApi())
                && nowPlayMusic.getId()
                    .equalsIgnoreCase(vote.getId())) {
                musicLessTime = 0;
                FMusic.side.broadcastInTask(FMusic.getMessage().vote.next);
            }
        } else {
            SongInfoObj obj = getMusic(vote.getId(), vote.getApi());
            if (obj != null) {
                synchronized (playList) {
                    playList.remove(obj);
                    playList.add(0, obj);
                }
                FMusic.side.broadcastInTask(FMusic.getMessage().push.doPush);
            }
        }

        vote = null;
    }

    public static void voteTick() {
        voteTime--;
    }

    public static int getVoteTime() {
        return voteTime;
    }

    public static VoteItem getVote() {
        return vote;
    }

    public static int getVoteCount() {
        return voteList.size();
    }

    public static void removeVote(String name, VoteItem.VoteType voteType) {
        if (vote != null) {
            if (vote.getVoteSender()
                .equalsIgnoreCase(name) && vote.getType() == voteType) {
                removeVote();
                if (voteType == VoteItem.VoteType.NEXT) {
                    FMusic.side.broadcast(FMusic.getMessage().push.cancel);
                } else {
                    FMusic.side.broadcast(FMusic.getMessage().vote.cancel);
                }
                return;
            }
        }

        VoteItem item1 = null;
        for (VoteItem item : voteList) {
            if (item.getType() == voteType && item.getVoteSender()
                .equalsIgnoreCase(name)) {
                item1 = item;
                break;
            }
        }

        if (item1 != null) {
            voteList.remove(item1);
        }
    }

    public static void removeVote() {
        vote = null;
    }

    /**
     * 下一个投票
     *
     * @return 投票
     */
    public static VoteItem nextVote() {
        vote = voteList.poll();
        return vote;
    }

    /**
     * 添加点歌任务
     *
     * @param obj 歌曲
     */
    public static void addTask(PlayerAddMusicObj obj) {
        tasks.add(obj);
    }

    private static void task() {
        FMusic.log.data(StatCollector.translateToLocal("fmusic.log.core.task_thread_start"));
        while (FMusic.isRun) {
            try {
                PlayerAddMusicObj obj = tasks.poll();
                if (obj != null) {
                    IMusicApi api = FMusic.MUSIC_APIS.get(obj.api);
                    if (api != null) {
                        addMusic(obj.sender, obj.id, api, obj.name, obj.isDefault);
                    }
                }
                Thread.sleep(10);
            } catch (Exception e) {
                FMusic.log.data(StatCollector.translateToLocal("fmusic.log.core.task_error"));
                e.printStackTrace();
            }
        }
        nowPlayPlayer.clear();
        playList.clear();
        voteList.clear();

        FMusic.log.data(StatCollector.translateToLocal("fmusic.log.core.task_thread_stop"));
    }

    /**
     * 添加歌曲
     *
     * @param sender 发送者
     * @param id     歌曲ID
     * @param player 用户名
     * @param isList 是否是空闲歌单
     */
    public static void addMusic(Object sender, String id, IMusicApi api, String player, boolean isList) {
        if (haveMusic(id, api.getId())) return;
        if (sender != null) {
            String text = FMusic.getMessage().musicPlay.checkMusic.replace(ARG.musicId, id);
            FMusic.side.sendMessageTask(sender, text);
        }
        FMusic.log.data(StatCollector.translateToLocalFormatted("fmusic.log.core.player_request", player, id));
        try {
            SongInfoObj info = api.getMusic(id, player, isList);
            if (info == null) {
                if (sender != null) {
                    String data = FMusic.getMessage().musicPlay.emptyCanPlay;
                    FMusic.side.sendMessageTask(sender, data.replace(ARG.musicId, id));
                }
                return;
            }
            LimitObj limit = FMusic.getConfig().limit;
            if (limit.musicTimeLimit && info.getLength() / 1000 > limit.maxMusicTime) {
                if (sender != null) {
                    FMusic.side.sendMessageTask(sender, FMusic.getMessage().addMusic.timeOut);
                }
                return;
            }
            playList.add(info);
            if (!FMusic.getConfig().muteAddMessage) {
                if (FMusic.getConfig().showInBar) {
                    String data = FMusic.getMessage().musicPlay.addMusic
                        .replace(ARG.musicName, HudUtils.messageLimit(info.getName()))
                        .replace(ARG.musicAuthor, HudUtils.messageLimit(info.getAuthor()))
                        .replace(ARG.musicAl, HudUtils.messageLimit(info.getAl()))
                        .replace(ARG.musicAlia, HudUtils.messageLimit(info.getAlia()))
                        .replace(ARG.player, info.getCall());
                    FMusic.side.sendBarInTask(data);
                } else {
                    String data = FMusic.getMessage().musicPlay.addMusic.replace(ARG.musicName, info.getName())
                        .replace(ARG.musicAuthor, info.getAuthor())
                        .replace(ARG.musicAl, info.getAl())
                        .replace(ARG.musicAlia, info.getAlia())
                        .replace(ARG.player, info.getCall());
                    FMusic.side.broadcastInTask(data);
                }
            }
            if (FMusic.getConfig().playListSwitch
                && (PlayMusic.nowPlayMusic != null && PlayMusic.nowPlayMusic.isList())) {
                PlayMusic.musicLessTime = 10;
                if (!isList) {
                    FMusic.side.broadcastInTask(FMusic.getMessage().musicPlay.switchMusic);
                }
            }
            error = 0;
        } catch (Exception e) {
            if (isList) {
                error++;
            }
            FMusic.log.data(StatCollector.translateToLocal("fmusic.log.core.song_parse_err"));
            e.printStackTrace();
        }
    }

    /**
     * 获取播放列表长度
     *
     * @return 长度
     */
    public static int getListSize() {
        synchronized (playList) {
            return playList.size();
        }
    }

    /**
     * 获取当前播放列表
     *
     * @return 播放列表
     */
    public static List<SongInfoObj> getList() {
        synchronized (playList) {
            return new ArrayList<>(playList);
        }
    }

    /**
     * 清理播放列表
     */
    public static void clear() {
        synchronized (playList) {
            playList.clear();
        }
    }

    /**
     * 从播放列表删除
     *
     * @param index 标号
     * @return 结果
     */
    public static SongInfoObj remove(int index) {
        synchronized (playList) {
            return playList.remove(index);
        }
    }

    /**
     * 从播放列表删除
     *
     * @param index
     */
    public static void remove(SongInfoObj index) {
        synchronized (playList) {
            playList.remove(index);
        }
    }

    /**
     * 获取播放列表所有信息
     *
     * @return 信息
     */
    public static String getAllList() {
        StringBuilder list = new StringBuilder();
        String a;

        SongInfoObj info;
        for (int i = 0; i < playList.size(); i++) {
            info = playList.get(i);
            a = FMusic.getMessage().musicPlay.listMusic.item;
            a = a.replace(ARG.index, String.valueOf(i + 1))
                .replace(ARG.musicName, info.getName())
                .replace(ARG.musicAuthor, info.getAuthor())
                .replace(ARG.musicAl, info.getAl())
                .replace(ARG.musicAlia, info.getAlia())
                .replace(ARG.player, info.getCall());
            list.append(a)
                .append("\n");
        }
        String temp = list.toString();
        if (temp.isEmpty()) return "";
        return temp.substring(0, temp.length() - 1);
    }

    /**
     * 是否在播放列表中
     *
     * @param music 音乐
     * @return 结果
     */
    public static boolean haveMusic(MusicObj music) {
        return haveMusic(music.id, music.api);
    }

    /**
     * 是否在播放列表中
     *
     * @param id  音乐编号
     * @param api 音乐API编号
     * @return 是否在列表种
     */
    public static boolean haveMusic(String id, String api) {
        if (nowPlayMusic != null && nowPlayMusic.getId()
            .equalsIgnoreCase(id) && Objects.equals(nowPlayMusic.getApi(), api)) return true;
        for (SongInfoObj item : playList) {
            if (item.getId()
                .equalsIgnoreCase(id) && Objects.equals(item.getApi(), api)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断玩家点歌数量是否超上限
     *
     * @param name 玩家名
     * @return 是否超过上限
     */
    public static boolean isPlayerMax(String name) {
        int list = FMusic.getConfig().limit.maxPlayerList;
        if (list == 0) {
            return false;
        }
        int count = 0;
        for (SongInfoObj obj : playList) {
            if (obj.getCall()
                .equalsIgnoreCase(name)) {
                count++;
            }
        }

        return list <= count;
    }

    public static void clearIdleList() {
        deep.clear();
        MusicListSave.clearIdleList();
    }

    public static int getIdleListSize() {
        return MusicListSave.getListSize();
    }

    /**
     * 检查这个空闲歌是否已经放了
     *
     * @param music 空闲音乐
     * @return 是否已经放过了
     */
    private static boolean checkDeep(MusicObj music) {
        for (MusicObj obj : deep) {
            if (Objects.equals(obj.id, music.id) && obj.api == music.api) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取空闲歌单的一首歌
     *
     * @return 结果
     */
    public static MusicObj getIdleMusic() {
        MusicObj music;
        int len = MusicListSave.getListSize();
        if (len == 0) return null;
        if (FMusic.getConfig().playListRandom) {
            if (len == 1) return MusicListSave.readListItem();
            if (len > 10) {
                int size = FMusic.getConfig().playListEscapeDeep;
                if (size > len / 2) {
                    size = len / 2;
                }
                while (deep.size() >= size) {
                    deep.poll();
                }
                do {
                    music = MusicListSave.readListItem();
                } while (checkDeep(music));
                deep.add(music);
            } else {
                music = MusicListSave.readListItem();
            }
        } else {
            music = MusicListSave.readListItem(idleIndex);
            idleIndex++;
            if (idleIndex >= len) {
                idleIndex = 0;
            }
        }
        return music;
    }

    public static SongInfoObj findPlayerMusic(String name) {
        List<SongInfoObj> list1 = getList();
        for (int a = 0; a < playList.size(); a++) {
            SongInfoObj item = list1.get(a);
            if (name.equalsIgnoreCase(item.getCall())) {
                return item;
            }
        }

        return null;
    }

    public static SongInfoObj findMusicIndex(int index) {
        List<SongInfoObj> list1 = getList();
        index--;
        if (index <= 0) {
            return null;
        }
        if (list1.size() <= index) {
            return null;
        }

        return list1.get(index);
    }

    /**
     * 获取正在播放的玩家列表
     *
     * @return 列表
     */
    public static Set<String> getNowPlayPlayer() {
        return nowPlayPlayer;
    }

    /**
     * 是否存在正在播放的玩家
     *
     * @param player 用户名
     * @return 是否存在
     */
    public static boolean containNowPlay(String player) {
        player = player.toLowerCase();
        return !nowPlayPlayer.contains(player);
    }

    /**
     * 添加正在播放的玩家
     *
     * @param player 用户名
     */
    public static void addNowPlayPlayer(String player) {
        player = player.toLowerCase();
        nowPlayPlayer.add(player);
    }

    /**
     * 删除正在播放的玩家
     *
     * @param player 用户名
     */
    public static void removeNowPlayPlayer(String player) {
        player = player.toLowerCase();
        nowPlayPlayer.remove(player);
    }

    /**
     * 清空正在播放玩家的列表
     */
    public static void clearNowPlayer() {
        nowPlayPlayer.clear();
    }
}
