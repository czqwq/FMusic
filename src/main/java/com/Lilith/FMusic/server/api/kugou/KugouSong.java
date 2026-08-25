package com.Lilith.FMusic.server.api.kugou;

import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class KugouSong {

    public static boolean debug = false;

    public String hash;
    public String name;
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
        song.name = clean(first(item, "SongName", "songname", "song_name", "OriSongName", "name"));
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
        song.pic = firstNotEmpty(song.pic, first(data, "img", "img_url", "image", "album_img"));
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
        long milliseconds = number(obj, 0, "TimeLength", "timelength", "time_length", "duration_ms", "DurationMs");
        if (milliseconds > 0) {
            return milliseconds;
        }
        long duration = number(obj, 0, "Duration", "duration", "interval");
        if (duration <= 0) {
            return 0;
        }
        return duration > 100000 ? duration : duration * 1000L;
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
