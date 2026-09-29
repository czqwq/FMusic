package com.Lilith.FMusic.server.api.kugou;

import com.Lilith.FMusic.server.core.objs.music.LyricItemObj;
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
    /** 分享页地址: www.kugou.com/share/xxxx.html */
    private static final Pattern SHARE_PAGE = Pattern.compile("(?i)/share/([0-9a-zA-Z]+)[.]html");
    /** 单曲页地址: www.kugou.com/mixsong/xxxx.html (xxxx 即 encode_album_audio_id) */
    private static final Pattern MIXSONG_PAGE = Pattern.compile("(?i)/mixsong/([0-9a-zA-Z]+)[.]html");
    private static final Pattern ENCODE_AUDIO_ID_PARAM = Pattern
        .compile("(?i)(?:^|[?&#])(?:encode_album_audio_id|EMixSongID)=([0-9a-z]{3,32})(?:$|[&#])");
    private static final Pattern FRAGMENT = Pattern.compile("#([0-9a-zA-Z]+)$");
    private volatile boolean isUpdate;

    public KugouApiMain() {
        KugouHttpClient.log(StatCollector.translateToLocal("fmusic.log.kugou.init"));
        if (!KugouHttpClient.hasOwnCookie()) {
            // 独立 Cookie 文件为空: VIP/付费歌曲拿不到播放地址, 提前提示便于排障
            FMusic.log.data(StatCollector.translateToLocal("fmusic.log.kugou.cookie_missing"));
        }
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

        // 分享页/单曲页里没有明文 hash, 只能抓页面读内嵌的 dataFromSmarty
        // (对应 kugou_share_parser.py 的 resolve_share_page)。
        String shareUrl = shareUrlOf(value);
        if (!shareUrl.isEmpty()) {
            KugouSong shared = KugouClient.resolveShareLink(shareUrl);
            if (shared != null) {
                return shared.realId();
            }
        }

        // 只有 encode_album_audio_id (如分享链接片段 #1rm21af4) 时用单曲页兜底
        String encoded = firstMatch(ENCODE_AUDIO_ID_PARAM, value);
        if (encoded.isEmpty()) {
            String fragment = firstMatch(FRAGMENT, value);
            if (!fragment.isEmpty() && fragment.matches("(?i)[0-9a-z]{3,32}")
                && !fragment.matches("(?i)[0-9a-f]{32}")) {
                encoded = fragment.toLowerCase(Locale.ROOT);
            }
        }
        if (!encoded.isEmpty()) {
            KugouSong shared = KugouClient.resolveShareLink(encoded);
            if (shared != null) {
                return shared.realId();
            }
        }
        return value;
    }

    /**
     * 分享/单曲链接归一化: 只对确实是酷狗分享页或单曲页的地址返回页面地址, 其余返回空。
     */
    private static String shareUrlOf(String value) {
        String url = value;
        if (!url.matches("(?i)^https?://.*")) {
            // 允许省略协议头的 www.kugou.com/m.kugou.com 链接
            if (!url.matches("(?i)^(?:www|m)[.]kugou[.]com/.*")) {
                return "";
            }
            url = "https://" + url;
        }
        if (SHARE_PAGE.matcher(url)
            .find()
            || MIXSONG_PAGE.matcher(url)
                .find()) {
            return url;
        }
        return "";
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
        SongInfoObj info = new SongInfoObj(
            empty(song.singer, StatCollector.translateToLocal("fmusic.api.unknown_artist")),
            empty(song.name, id),
            id,
            empty(song.alia, ""),
            player,
            empty(song.album, StatCollector.translateToLocal("fmusic.api.kugou.album")),
            isList,
            song.durationMs,
            song.picUrl(),
            false,
            null,
            getId());
        // id 是 32 位 hash(播放/去重必需); 消息里改显示数字 ID 更可读
        info.setDisplayId(empty(firstNonEmpty(song.audioId, song.albumAudioId), id));
        return info;
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
        }, "FMusic_Kugou_setList");
        thread.start();
    }

    @Override
    public LyricSave getLyric(String id) {
        LyricSave save = new LyricSave();
        String lyric = KugouClient.getLyricText(getMusicId(id));
        Map<Long, LyricItemObj> map = KugouLyricDecoder.parse(lyric);
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

    /**
     * 取第一个非空字符串 (用于选取展示用数字 ID)
     */
    private static String firstNonEmpty(String first, String second) {
        if (first != null && !first.trim()
            .isEmpty()) {
            return first.trim();
        }
        return second == null ? "" : second.trim();
    }

    private static String empty(String value, String def) {
        return value == null || value.trim()
            .isEmpty() ? def : value;
    }
}
