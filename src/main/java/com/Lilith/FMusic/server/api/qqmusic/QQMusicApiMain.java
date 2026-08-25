package com.Lilith.FMusic.server.api.qqmusic;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.util.StatCollector;

import com.Lilith.FMusic.server.core.FMusic;
import com.Lilith.FMusic.server.core.IMusicApi;
import com.Lilith.FMusic.server.core.music.LyricSave;
import com.Lilith.FMusic.server.core.objs.SearchMusicObj;
import com.Lilith.FMusic.server.core.objs.message.ARG;
import com.Lilith.FMusic.server.core.objs.music.SearchPageObj;
import com.Lilith.FMusic.server.core.objs.music.SongInfoObj;
import com.Lilith.FMusic.server.core.saves.MusicListSave;

public class QQMusicApiMain implements IMusicApi {

    private static final Pattern SONG_MID_PARAM = Pattern.compile("(?i)(?:^|[?&#])songmid=([0-9a-z]{10,32})(?:$|[&#])");
    private static final Pattern SONGID_PARAM = Pattern.compile("(?i)(?:^|[?&#])songid=([0-9]{1,20})(?:$|[&#])");
    private static final Pattern PLAYLIST_ID_PARAM = Pattern
        .compile("(?i)(?:^|[?&#])(?:disstid|dissid)=([0-9]+)(?:$|[&#])");
    private static final Pattern PLAYLIST_PATH = Pattern
        .compile("(?i)/(?:playlist|taoge)/([0-9]+)(?:\\.html)?(?:$|[/?#])");
    private static final Pattern GENERIC_ID_PARAM = Pattern.compile("(?i)(?:^|[?&#])id=([0-9]+)(?:$|[&#])");
    private static final Pattern SONG_PATH = Pattern
        .compile("(?i)/(?:song|songdetail)/([0-9a-z]{10,32})(?:\\.html)?(?:$|[/?#])");
    private volatile boolean isUpdate;

    public QQMusicApiMain() {
        QQMusicHttpClient.log(StatCollector.translateToLocal("fmusic.log.qq.init"));
    }

    @Override
    public String getId() {
        return "qqmusic";
    }

    @Override
    public boolean isBusy() {
        return isUpdate;
    }

    @Override
    public String getMusicId(String arg) {
        if (arg == null) {
            return "";
        }
        String value = arg.trim();
        try {
            value = URLDecoder.decode(value, StandardCharsets.UTF_8.toString());
        } catch (Exception ignored) {}

        String id = firstMatch(SONG_MID_PARAM, value);
        if (!id.isEmpty()) {
            return id;
        }
        id = firstMatch(SONGID_PARAM, value);
        if (!id.isEmpty()) {
            return id;
        }
        id = firstMatch(PLAYLIST_ID_PARAM, value);
        if (!id.isEmpty()) {
            return id;
        }
        id = firstMatch(PLAYLIST_PATH, value);
        if (!id.isEmpty()) {
            return id;
        }

        String lowerValue = value.toLowerCase(Locale.ROOT);
        if (lowerValue.contains("playlist") || lowerValue.contains("taoge") || lowerValue.contains("share/details")) {
            id = firstMatch(GENERIC_ID_PARAM, value);
            if (!id.isEmpty()) {
                return id;
            }
        }

        id = firstMatch(SONG_PATH, value);
        if (!id.isEmpty()) {
            return id;
        }
        return value;
    }

    private static String firstMatch(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value == null ? "" : value);
        return matcher.find() ? matcher.group(1) : "";
    }

    @Override
    public boolean checkId(String id) {
        return id != null && (id.matches("[0-9]{1,20}") || id.matches("[0-9A-Za-z]{10,32}"));
    }

    @Override
    public SongInfoObj getMusic(String id, String player, boolean isList) {
        id = getMusicId(id);
        QQSong song = QQMusicClient.getSong(id);
        if (song == null) {
            List<QQSong> fallback = QQMusicClient.search(id, 1);
            if (fallback != null && !fallback.isEmpty()) {
                song = fallback.get(0);
            }
        }
        if (song == null) {
            QQMusicHttpClient.log(StatCollector.translateToLocalFormatted("fmusic.log.qq.song_empty", id));
            return null;
        }
        String playUrl = QQMusicClient.getPlayUrl(song);
        if (playUrl == null || playUrl.isEmpty()) {
            QQMusicHttpClient
                .log(StatCollector.translateToLocalFormatted("fmusic.log.qq.play_url_null", song.realId()));
            return null;
        }
        boolean trial = false;
        return new SongInfoObj(
            empty(song.singer, StatCollector.translateToLocal("fmusic.api.unknown_artist")),
            empty(song.name, song.realId()),
            song.realId(),
            null,
            player,
            empty(song.album, StatCollector.translateToLocal("fmusic.api.qqmusic.album")),
            isList,
            song.lengthMs(),
            song.picUrl(),
            trial,
            null,
            getId());
    }

    @Override
    public SearchPageObj search(String[] name, boolean isDefault) {
        List<SearchMusicObj> resData = new ArrayList<>();
        String keyword = joinKeyword(name, isDefault);
        if (keyword.isEmpty()) {
            QQMusicHttpClient.log(StatCollector.translateToLocal("fmusic.log.qq.keyword_empty"));
            return null;
        }
        List<QQSong> songs = QQMusicClient.search(keyword, 30);
        if (songs == null || songs.isEmpty()) {
            QQMusicHttpClient.log(StatCollector.translateToLocalFormatted("fmusic.log.qq.result_empty", keyword));
            return null;
        }
        for (QQSong temp : songs) {
            if (temp == null || temp.realId()
                .isEmpty()) {
                continue;
            }
            resData.add(
                new SearchMusicObj(
                    temp.realId(),
                    empty(temp.name, temp.realId()),
                    empty(temp.singer, StatCollector.translateToLocal("fmusic.api.unknown_artist")),
                    empty(temp.album, StatCollector.translateToLocal("fmusic.api.qqmusic.album"))));
        }
        if (resData.isEmpty()) {
            QQMusicHttpClient.log(StatCollector.translateToLocalFormatted("fmusic.log.qq.no_valid_song", keyword));
            return null;
        }
        int maxpage = Math.max(1, (resData.size() + 9) / 10);
        return new SearchPageObj(resData, maxpage, getId());
    }

    @Override
    public void setList(String id, Object sender) {
        final Thread thread = new Thread(() -> {
            isUpdate = true;
            try {
                String value = id == null ? "" : id.trim();
                if (value.matches("[0-9]{1,20}")) {
                    QQMusicClient.PlaylistInfo playlist = QQMusicClient.getPlaylist(value);
                    if (playlist == null || playlist.getSongIds()
                        .isEmpty()) {
                        FMusic.side.sendMessageTask(
                            sender,
                            StatCollector.translateToLocal("fmusic.api.qqmusic.playlist_fail_check"));
                        return;
                    }
                    MusicListSave.addIdleList(playlist.getSongIds(), getId());
                    FMusic.side.sendMessageTask(
                        sender,
                        FMusic.getMessage().musicPlay.listMusic.get.replace(ARG.name, playlist.getName()));
                    return;
                }

                String[] ids = value.split(",");
                List<String> list = new ArrayList<>();
                for (String item : ids) {
                    String temp = getMusicId(item.trim());
                    if (checkId(temp)) {
                        list.add(temp);
                    }
                }
                if (!list.isEmpty()) {
                    MusicListSave.addIdleList(list, getId());
                    FMusic.side.sendMessageTask(
                        sender,
                        FMusic.getMessage().musicPlay.listMusic.get.replace(ARG.name, "QQMusic"));
                } else {
                    FMusic.side.sendMessageTask(
                        sender,
                        StatCollector.translateToLocal("fmusic.api.qqmusic.playlist_fail_input"));
                }
            } catch (Exception e) {
                QQMusicHttpClient.log(StatCollector.translateToLocal("fmusic.log.qq.list_error"));
                if (QQSong.debug) {
                    e.printStackTrace();
                }
            } finally {
                isUpdate = false;
            }
        }, "AllMusic_QQMusic_setList");
        thread.start();
    }

    @Override
    public LyricSave getLyric(String id) {
        LyricSave save = new LyricSave();
        String lyric = QQMusicClient.getLyricText(getMusicId(id));
        Map<Long, com.Lilith.FMusic.server.core.objs.music.LyricItemObj> map = QQMusicLyricDecoder.parse(lyric);
        if (!map.isEmpty()) {
            save.setHaveLyric(FMusic.getConfig().sendLyric);
            save.setLyric(map);
        }
        return save;
    }

    @Override
    public String getPlayUrl(String id) {
        return QQMusicClient.getPlayUrl(getMusicId(id));
    }

    private static String joinKeyword(String[] name, boolean isDefault) {
        if (name == null || name.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = isDefault ? 0 : 1; i < name.length; i++) {
            if (name[i] != null && !name[i].trim()
                .isEmpty()) {
                builder.append(name[i].trim())
                    .append(" ");
            }
        }
        if (builder.length() == 0) {
            return "";
        }
        return builder.substring(0, builder.length() - 1);
    }

    private static String empty(String value, String def) {
        return value == null || value.isEmpty() ? def : value;
    }
}
