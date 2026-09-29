package com.Lilith.FMusic.server.api.kugou;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class KugouSong {

    public static boolean debug = false;

    public String hash;
    public String name;
    /**
     * 原曲/别名 (酷狗 OriSongName 等)
     */
    public String alia;
    public String singer;
    public String album;
    public String albumId;
    public String albumAudioId;
    public String encodedAlbumAudioId;
    public String encodedAlbumId;
    public String audioId;
    public String pic;
    public String playUrl;
    public String lyricText;
    public long durationMs;
    public boolean trial;

    // Search privilege metadata. These values are diagnostic only; the final
    // playback decision is always made by the official playback endpoint.
    public int payType = -1;
    public int price = -1;
    public int privilege = -1;
    public int failProcess = -1;

    public String realId() {
        return hash == null ? ""
            : hash.trim()
                .toUpperCase(Locale.ROOT);
    }

    public long albumIdNumber() {
        return positiveLong(albumId);
    }

    public long albumAudioIdNumber() {
        long value = positiveLong(albumAudioId);
        if (value > 0) {
            return value;
        }
        return positiveLong(audioId);
    }

    public String picUrl() {
        if (pic == null || pic.trim()
            .isEmpty()) {
            return null;
        }
        String value = unescape(pic.trim());
        value = value.replace("{size}", "400")
            .replace("{width}", "400")
            .replace("{height}", "400");
        if (value.startsWith("//")) {
            value = "https:" + value;
        }
        return value;
    }

    public static KugouSong fromSearchItem(JsonObject item) {
        if (item == null) {
            return null;
        }
        KugouSong song = new KugouSong();
        song.hash = first(item, "FileHash", "Hash", "hash", "file_hash", "filehash", "HQFileHash", "SQFileHash");
        song.name = clean(first(item, "SongName", "songname", "song_name", "name"));
        // OriSongName 是"原曲名", 不能当歌名(外文原曲/翻唱会显示错误名字), 归入别名
        song.alia = clean(first(item, "OriSongName", "ori_song_name", "oriSongName", "alias", "aliases"));
        song.singer = clean(
            first(item, "SingerName", "singername", "singer_name", "author_name", "authorName", "singer"));
        song.album = clean(first(item, "AlbumName", "albumname", "album_name", "album"));
        song.albumId = numeric(first(item, "AlbumID", "album_id", "albumid"));
        song.albumAudioId = numeric(
            first(item, "MixSongID", "mixsongid", "AlbumAudioID", "album_audio_id", "audio_id"));
        song.encodedAlbumAudioId = encodedId(
            first(item, "EMixSongID", "EMixSongId", "emixsongid", "encode_album_audio_id", "encoded_album_audio_id"));
        song.encodedAlbumId = encodedId(
            first(item, "EAlbumID", "EAlbumId", "ealbumid", "encode_album_id", "encoded_album_id"));
        song.audioId = numeric(first(item, "AudioID", "audio_id", "audioid"));
        song.pic = first(item, "Image", "image", "img", "imgurl", "img_url", "album_img");
        song.durationMs = parseDuration(item);
        song.payType = (int) number(item, -1, "PayType", "pay_type", "HQPayType");
        song.price = (int) number(item, -1, "Price", "price", "HQPrice");
        song.privilege = (int) number(item, -1, "Privilege", "privilege", "HQPrivilege");
        song.failProcess = (int) number(item, -1, "FailProcess", "fail_process", "HQFailProcess");

        String fileName = clean(first(item, "FileName", "filename", "file_name"));
        if ((song.name == null || song.name.isEmpty()) && !fileName.isEmpty()) {
            int split = fileName.indexOf(" - ");
            song.name = split >= 0 ? fileName.substring(split + 3)
                .trim() : fileName;
        }
        if ((song.singer == null || song.singer.isEmpty()) && !fileName.isEmpty()) {
            int split = fileName.indexOf(" - ");
            if (split > 0) {
                song.singer = fileName.substring(0, split)
                    .trim();
            }
        }
        return song;
    }

    public static KugouSong fromDetail(JsonObject data) {
        if (data == null) {
            return null;
        }
        KugouSong song = fromSearchItem(data);
        if (song == null) {
            song = new KugouSong();
        }
        song.hash = firstNotEmpty(song.hash, first(data, "hash", "file_hash", "FileHash"));
        song.name = firstNotEmpty(song.name, clean(first(data, "song_name", "songname", "audio_name", "name")));
        song.alia = firstNotEmpty(song.alia, clean(first(data, "ori_song_name", "OriSongName", "alias", "aliases")));
        song.singer = firstNotEmpty(song.singer, clean(first(data, "author_name", "singer_name", "singer")));
        song.album = firstNotEmpty(song.album, clean(first(data, "album_name", "albumname", "album")));
        song.albumId = firstNotEmpty(song.albumId, numeric(first(data, "album_id", "AlbumID")));
        song.albumAudioId = firstNotEmpty(
            song.albumAudioId,
            numeric(first(data, "album_audio_id", "MixSongID", "audio_id")));
        song.encodedAlbumAudioId = firstNotEmpty(
            song.encodedAlbumAudioId,
            encodedId(first(data, "encode_album_audio_id", "encoded_album_audio_id", "EMixSongID", "EMixSongId")));
        song.encodedAlbumId = firstNotEmpty(
            song.encodedAlbumId,
            encodedId(first(data, "encode_album_id", "encoded_album_id", "EAlbumID", "EAlbumId")));
        song.audioId = firstNotEmpty(song.audioId, numeric(first(data, "audio_id", "AudioID")));
        // 封面优先使用专辑图 (album_img), 其次才是歌手头像 (imgUrl) — 与脚本一致
        song.pic = firstNotEmpty(first(data, "album_img", "image", "img", "img_url"), song.pic);
        song.playUrl = first(data, "play_url", "playUrl", "url");
        song.lyricText = first(data, "lyrics", "lyric", "lrc");
        song.trial = bool(data, false, "is_free_part", "isFreePart", "is_trial", "isTrial", "is_trail", "trial");
        if (song.durationMs <= 0) {
            song.durationMs = parseDuration(data);
        }
        return song;
    }

    public static JsonObject getObj(JsonObject obj, String... keys) {
        JsonElement element = find(obj, keys);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    public static JsonArray getArray(JsonObject obj, String... keys) {
        JsonElement element = find(obj, keys);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    public static String first(JsonObject obj, String... keys) {
        JsonElement element = find(obj, keys);
        if (element == null || element.isJsonNull() || element.isJsonObject() || element.isJsonArray()) {
            return "";
        }
        try {
            return unescape(element.getAsString());
        } catch (Exception ignored) {
            return "";
        }
    }

    public static long number(JsonObject obj, long def, String... keys) {
        String value = first(obj, keys);
        try {
            if (value == null || value.trim()
                .isEmpty()) {
                return def;
            }
            return (long) Double.parseDouble(value.trim());
        } catch (Exception ignored) {
            return def;
        }
    }

    public static boolean bool(JsonObject obj, boolean def, String... keys) {
        JsonElement element = find(obj, keys);
        if (element == null || element.isJsonNull()) {
            return def;
        }
        try {
            if (element.isJsonPrimitive() && element.getAsJsonPrimitive()
                .isBoolean()) {
                return element.getAsBoolean();
            }
            String value = element.getAsString();
            return "1".equals(value) || "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value);
        } catch (Exception ignored) {
            return def;
        }
    }

    private static JsonElement find(JsonObject obj, String... keys) {
        if (obj == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            if (key != null && obj.has(key)
                && !obj.get(key)
                    .isJsonNull()) {
                return obj.get(key);
            }
        }
        for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
            for (String key : keys) {
                if (key != null && key.equalsIgnoreCase(entry.getKey())
                    && entry.getValue() != null
                    && !entry.getValue()
                        .isJsonNull()) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    private static long parseDuration(JsonObject obj) {
        return normalizeDuration(
            number(
                obj,
                0,
                "TimeLength",
                "timelength",
                "time_length",
                "duration_ms",
                "DurationMs",
                "timeLength",
                "Duration",
                "duration",
                "interval"));
    }

    /**
     * 统一时长单位: 酷狗部分接口给秒 (49), 部分给毫秒 (49528), 秒值 ×1000。
     * 对应 kugou_share_parser.py 的 normalize_duration。
     */
    public static long normalizeDuration(long value) {
        if (value <= 0) {
            return 0;
        }
        return value < 10000 ? value * 1000L : value;
    }

    /**
     * 分享页/单曲页内嵌 dataFromSmarty 的单个条目 (对应脚本 resolve_share_page)。
     * 形如 /song/#hash=... 的占位对象会给出 hash=null, 此时 hash 为空的返回值由调用方判定失败。
     */
    public static KugouSong fromSmartyItem(JsonObject item) {
        if (item == null) {
            return null;
        }
        KugouSong song = fromSearchItem(item);
        if (song == null) {
            song = new KugouSong();
        }

        String hash = first(item, "hash", "FileHash", "file_hash");
        song.hash = hash.matches("(?i)[0-9a-f]{32}") ? hash.toUpperCase(Locale.ROOT) : "";

        song.name = firstNotEmpty(song.name, clean(first(item, "song_name", "songName", "audio_name", "name")));
        song.singer = firstNotEmpty(
            song.singer,
            clean(first(item, "author_name", "singer_name", "singerName", "singer")));
        song.alia = firstNotEmpty(song.alia, clean(first(item, "ori_song_name", "OriSongName", "alias")));
        song.album = firstNotEmpty(song.album, clean(first(item, "album_name", "albumname", "album")));
        song.albumId = firstNotEmpty(song.albumId, numeric(first(item, "album_id", "albumid", "AlbumID")));
        song.albumAudioId = firstNotEmpty(
            song.albumAudioId,
            numeric(first(item, "mixsongid", "album_audio_id", "MixSongID")));
        song.encodedAlbumAudioId = firstNotEmpty(
            song.encodedAlbumAudioId,
            encodedId(first(item, "encode_album_audio_id", "encode_album_id", "EMixSongID")));
        song.audioId = firstNotEmpty(song.audioId, numeric(first(item, "audio_id", "audioId", "AudioID")));
        song.pic = firstNotEmpty(song.pic, first(item, "album_img", "img", "img_url", "image", "imgUrl"));

        // audio_name 形如 "歌手 - 歌名", 用于补齐缺失字段 (与脚本一致)
        String audioName = clean(first(item, "audio_name"));
        if (!audioName.isEmpty()) {
            int split = audioName.indexOf(" - ");
            if (split > 0) {
                if (song.singer == null || song.singer.isEmpty()) {
                    song.singer = audioName.substring(0, split)
                        .trim();
                }
                if (song.name == null || song.name.isEmpty()) {
                    song.name = audioName.substring(split + 3)
                        .trim();
                }
            } else if (song.name == null || song.name.isEmpty()) {
                song.name = audioName;
            }
        }

        if (song.durationMs <= 0) {
            song.durationMs = parseDuration(item);
        }
        return song;
    }

    /**
     * 对应脚本 collect_play_urls: 从接口响应里挑选最优播放地址, 优先 /full/ 完整音频。
     */
    public static String bestPlayUrl(JsonObject data) {
        if (data == null) {
            return "";
        }
        List<String> urls = new ArrayList<>();
        addCandidate(urls, first(data, "url", "play_url", "playUrl"));
        collectStrings(urls, find(data, "backup_url", "backupUrl", "play_backup_url", "playBackupUrl"), 0);
        if (urls.isEmpty()) {
            return "";
        }
        for (String url : urls) {
            if (url.toLowerCase(Locale.ROOT)
                .contains("/full/")) {
                return url;
            }
        }
        return urls.get(0);
    }

    private static void addCandidate(List<String> urls, String value) {
        if (value == null) {
            return;
        }
        String url = unescape(value.trim());
        if (!url.isEmpty() && !urls.contains(url)) {
            urls.add(url);
        }
    }

    private static void collectStrings(List<String> urls, JsonElement element, int depth) {
        if (element == null || element.isJsonNull() || depth > 4) {
            return;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                collectStrings(urls, child, depth + 1);
            }
        } else if (element.isJsonPrimitive()) {
            try {
                addCandidate(urls, element.getAsString());
            } catch (Exception ignored) {}
        }
    }

    private static long positiveLong(String value) {
        try {
            if (value == null || !value.matches("[0-9]+")) {
                return 0;
            }
            long result = Long.parseLong(value);
            return Math.max(0, result);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static String numeric(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.matches("[0-9]+") ? trimmed : "";
    }

    private static String encodedId(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.matches("(?i)[0-9a-z]{3,32}") ? trimmed.toLowerCase(Locale.ROOT) : "";
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        return unescape(value).replaceAll("(?i)</?em>", "")
            .replaceAll("<[^>]+>", "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .trim();
    }

    private static String unescape(String value) {
        return value == null ? ""
            : value.replace("\\/", "/")
                .replace("\\u0026", "&");
    }

    private static String firstNotEmpty(String first, String second) {
        return first != null && !first.trim()
            .isEmpty() ? first : second == null ? "" : second;
    }
}
