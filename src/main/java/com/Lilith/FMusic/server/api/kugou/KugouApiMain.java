package com.Lilith.FMusic.server.api.kugou;

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

public class KugouApiMain implements IMusicApi {

    private static final Pattern HASH_PARAM = Pattern.compile("(?i)(?:^|[?&#])hash=([0-9a-f]{32})(?:$|[&#])");
    private static final Pattern HASH_ANYWHERE = Pattern.compile("(?i)(?:^|[^0-9a-f])([0-9a-f]{32})(?:$|[^0-9a-f])");
    private static final Pattern ALBUM_ID_PARAM = Pattern.compile("(?i)(?:^|[?&#])album_id=([0-9]+)(?:$|[&#])");
    private static final Pattern ALBUM_AUDIO_ID_PARAM = Pattern
        .compile("(?i)(?:^|[?&#])album_audio_id=([0-9]+)(?:$|[&#])");
    private static final Pattern PLAYLIST_ID_PARAM = Pattern
        .compile("(?i)(?:^|[?&#])(?:specialid|special_id)=([0-9]+)(?:$|[&#])");
    private static final Pattern PLAYLIST_PATH = Pattern.compile(
        "(?i)/(?:yy/special/single|special/single|plist/list|playlist|songlist)/" + "([0-9]+)(?:\\.html)?(?:$|[/?#])");
    private static final Pattern GENERIC_ID_PARAM = Pattern.compile("(?i)(?:^|[?&#])id=([0-9]+)(?:$|[&#])");
    private volatile boolean isUpdate;

    public KugouApiMain() {
        KugouHttpClient.log(StatCollector.translateToLocal("fmusic.log.kugou.init"));
    }

    @Override
    public String getId() {
        return "kugou";
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

        String playlistId = firstMatch(PLAYLIST_ID_PARAM, value);
        if (playlistId.isEmpty()) {
            playlistId = firstMatch(PLAYLIST_PATH, value);
        }
        String lowerValue = value.toLowerCase(Locale.ROOT);
        if (playlistId.isEmpty() && (lowerValue.contains("playlist") || lowerValue.contains("plist")
            || lowerValue.contains("special")
            || lowerValue.contains("songlist"))) {
            playlistId = firstMatch(GENERIC_ID_PARAM, value);
        }
        if (!playlistId.isEmpty()) {
            return playlistId;
        }

        String hash = "";
        if (value.matches("(?i)[0-9a-f]{32}")) {
            hash = value.toUpperCase(Locale.ROOT);
        } else {
            Matcher matcher = HASH_PARAM.matcher(value);
            if (matcher.find()) {
                hash = matcher.group(1)
                    .toUpperCase(Locale.ROOT);
            } else {
                matcher = HASH_ANYWHERE.matcher(value);
                if (matcher.find()) {
                    hash = matcher.group(1)
                        .toUpperCase(Locale.ROOT);
                }
            }
        }

        if (!hash.isEmpty()) {
            KugouClient.rememberSongIdentifiers(
                hash,
                firstMatch(ALBUM_ID_PARAM, value),
                firstMatch(ALBUM_AUDIO_ID_PARAM, value));
            return hash;
        }
        return value;
    }

    private static String firstMatch(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value == null ? "" : value);
        return matcher.find() ? matcher.group(1) : "";
    }

    @Override
    public boolean checkId(String id) {
        return id != null && (id.matches("(?i)[0-9a-f]{32}") || id.matches("[0-9]{1,20}"));
    }

    @Override
    public SongInfoObj getMusic(String id, String player, boolean isList) {
        id = getMusicId(id);
        if (!checkId(id)) {
            KugouHttpClient.log(StatCollector.translateToLocalFormatted("fmusic.log.kugou.hash_error", id));
            return null;
        }
        KugouSong song = KugouClient.getSong(id);
        if (song == null) {
            song = new KugouSong();
            song.hash = id;
        }
        // 这里只构造歌曲元数据，不提前请求播放地址。
        // AllMusic 核心会在真正开始播放时调用 getPlayUrl(id)；若此处也请求，
        // 成功场景会重复请求短时效 URL，并增加接口限流/风控概率。
        return new SongInfoObj(
            empty(song.singer, StatCollector.translateToLocal("fmusic.api.unknown_artist")),
            empty(song.name, id),
            id,
            null,
            player,
            empty(song.album, StatCollector.translateToLocal("fmusic.api.kugou.album")),
            isList,
            song.durationMs,
            song.picUrl(),
            false,
            null,
            getId());
    }

    @Override
    public SearchPageObj search(String[] name, boolean isDefault) {
        String keyword = joinKeyword(name, isDefault);
        if (keyword.isEmpty()) {
            KugouHttpClient.log(StatCollector.translateToLocal("fmusic.log.kugou.keyword_empty"));
            return null;
        }
        List<KugouSong> songs = KugouClient.search(keyword, 30);
        if (songs == null || songs.isEmpty()) {
            KugouHttpClient.log(StatCollector.translateToLocalFormatted("fmusic.log.kugou.result_empty", keyword));
            return null;
        }
        List<SearchMusicObj> result = new ArrayList<>();
        for (KugouSong song : songs) {
            if (song == null || song.realId()
                .isEmpty()) {
                continue;
            }
            result.add(
                new SearchMusicObj(
                    song.realId(),
                    empty(song.name, song.realId()),
                    empty(song.singer, StatCollector.translateToLocal("fmusic.api.unknown_artist")),
                    empty(song.album, StatCollector.translateToLocal("fmusic.api.kugou.album"))));
        }
        if (result.isEmpty()) {
            return null;
        }
        int maxPage = Math.max(1, (result.size() + 9) / 10);
        return new SearchPageObj(result, maxPage, getId());
    }

    @Override
    public void setList(String id, Object sender) {
        Thread thread = new Thread(() -> {
            isUpdate = true;
            try {
                String value = id == null ? "" : id.trim();
                if (value.matches("[0-9]{1,20}")) {
                    KugouClient.PlaylistInfo playlist = KugouClient.getPlaylist(value);
                    if (playlist == null || playlist.getSongIds()
                        .isEmpty()) {
                        FMusic.side.sendMessageTask(
                            sender,
                            StatCollector.translateToLocal("fmusic.api.kugou.playlist_fail_check"));
                        return;
                    }
                    MusicListSave.addIdleList(playlist.getSongIds(), getId());
                    FMusic.side.sendMessageTask(
                        sender,
                        FMusic.getMessage().musicPlay.listMusic.get.replace(ARG.name, playlist.getName()));
                    return;
                }

                String[] ids = id == null ? new String[0] : id.split(",");
                List<String> list = new ArrayList<>();
                for (String item : ids) {
                    String hash = getMusicId(item == null ? "" : item.trim());
                    if (checkId(hash)) {
                        list.add(hash);
                    }
                }
                if (!list.isEmpty()) {
                    MusicListSave.addIdleList(list, getId());
                    FMusic.side.sendMessageTask(
                        sender,
                        FMusic.getMessage().musicPlay.listMusic.get.replace(ARG.name, "Kugou"));
                } else {
                    FMusic.side.sendMessageTask(
                        sender,
                        StatCollector.translateToLocal("fmusic.api.kugou.playlist_fail_input"));
                }
            } catch (Exception e) {
                KugouHttpClient.log(StatCollector.translateToLocal("fmusic.log.kugou.list_import_error"));
                if (KugouSong.debug) {
                    e.printStackTrace();
                }
            } finally {
                isUpdate = false;
            }
        }, "AllMusic_Kugou_setList");
        thread.start();
    }

    @Override
    public LyricSave getLyric(String id) {
        LyricSave save = new LyricSave();
        String lyric = KugouClient.getLyricText(getMusicId(id));
        Map<Long, com.Lilith.FMusic.server.core.objs.music.LyricItemObj> map = KugouLyricDecoder.parse(lyric);
        if (!map.isEmpty()) {
            save.setHaveLyric(FMusic.getConfig().sendLyric);
            save.setLyric(map);
        }
        return save;
    }

    @Override
    public String getPlayUrl(String id) {
        return KugouClient.getPlayUrl(getMusicId(id));
    }

    private static String joinKeyword(String[] name, boolean isDefault) {
        if (name == null || name.length == 0) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = isDefault ? 0 : 1; i < name.length; i++) {
            if (name[i] != null && !name[i].trim()
                .isEmpty()) {
                if (builder.length() > 0) {
                    builder.append(' ');
                }
                builder.append(name[i].trim());
            }
        }
        return builder.toString();
    }

    private static String empty(String value, String def) {
        return value == null || value.trim()
            .isEmpty() ? def : value;
    }
}
