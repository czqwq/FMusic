package com.Lilith.FMusic.server.api.qqmusic;
import net.minecraft.util.StatCollector;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public class QQSong {

    public static boolean debug = false;

    public String id;
    public String mid;
    public String mediaMid;
    public String name;
    public String singer;
    public String album;
    public String albumMid;
    public long interval;

    public String realId() {
        if (mid != null && !mid.isEmpty()) {
            return mid;
        }
        return id == null ? "" : id;
    }

    public String realMediaMid() {
        if (mediaMid != null && !mediaMid.isEmpty()) {
            return mediaMid;
        }
        return realId();
    }

    public String picUrl() {
        if (albumMid == null || albumMid.isEmpty()) {
            return null;
        }
        return "https://y.qq.com/music/photo_new/T002R300x300M000" + albumMid + ".jpg";
    }

    public long lengthMs() {
        return interval <= 0 ? 0 : interval * 1000;
    }

    public static QQSong fromSearchItem(JsonObject item) {
        QQSong song = new QQSong();
        song.id = getString(item, "songid");
        song.mid = firstNotEmpty(getString(item, "songmid"), getString(item, "mid"));
        song.name = firstNotEmpty(getString(item, "songname"), getString(item, "name"));
        song.interval = getLong(item, "interval");

        JsonObject file = getObj(item, "file");
        if (file != null) {
            song.mediaMid = getString(file, "media_mid");
        }
        if (song.mediaMid == null || song.mediaMid.isEmpty()) {
            song.mediaMid = getString(item, "media_mid");
        }

        JsonObject album = getObj(item, "album");
        if (album != null) {
            song.album = firstNotEmpty(getString(album, "name"), getString(album, "title"));
            song.albumMid = firstNotEmpty(getString(album, "mid"), getString(album, "pmid"));
        }
        if (song.album == null || song.album.isEmpty()) {
            song.album = getString(item, "albumname");
        }
        if (song.albumMid == null || song.albumMid.isEmpty()) {
            song.albumMid = getString(item, "albummid");
        }

        JsonArray singers = getArray(item, "singer");
        song.singer = joinSingers(singers);
        return song;
    }

    public static QQSong fromSmartboxItem(JsonObject item) {
        QQSong song = new QQSong();
        song.id = getString(item, "id");
        song.mid = firstNotEmpty(getString(item, "mid"), getString(item, "songmid"));
        song.mediaMid = firstNotEmpty(getString(item, "media_mid"), song.mid);
        song.name = firstNotEmpty(getString(item, "name"), getString(item, "songname"));
        song.singer = getString(item, "singer");
        if (song.singer == null || song.singer.isEmpty()) {
            song.singer = StatCollector.translateToLocal("fmusic.api.unknown_artist");
        }
        song.album = firstNotEmpty(getString(item, "album"), getString(item, "albumname"));
        song.albumMid = firstNotEmpty(getString(item, "albummid"), getString(item, "album_mid"));
        return song;
    }

    public static QQSong fromSingleSong(JsonObject item) {
        QQSong song = new QQSong();
        song.id = getString(item, "id");
        song.mid = firstNotEmpty(getString(item, "mid"), getString(item, "songmid"));
        song.name = firstNotEmpty(getString(item, "name"), getString(item, "songname"));
        song.interval = getLong(item, "interval");

        JsonObject file = getObj(item, "file");
        if (file != null) {
            song.mediaMid = getString(file, "media_mid");
        }
        if (song.mediaMid == null || song.mediaMid.isEmpty()) {
            song.mediaMid = getString(item, "media_mid");
        }

        JsonObject album = getObj(item, "album");
        if (album != null) {
            song.album = firstNotEmpty(getString(album, "name"), getString(album, "title"));
            song.albumMid = firstNotEmpty(getString(album, "mid"), getString(album, "pmid"));
        }

        JsonArray singers = getArray(item, "singer");
        song.singer = joinSingers(singers);
        return song;
    }

    private static String joinSingers(JsonArray singers) {
        if (singers == null || singers.size() == 0) {
            return StatCollector.translateToLocal("fmusic.api.unknown_artist");
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < singers.size(); i++) {
            JsonElement element = singers.get(i);
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            String name = getString(element.getAsJsonObject(), "name");
            if (name != null && !name.isEmpty()) {
                builder.append(name)
                    .append(",");
            }
        }
        if (builder.length() == 0) {
            return StatCollector.translateToLocal("fmusic.api.unknown_artist");
        }
        return builder.substring(0, builder.length() - 1);
    }

    static JsonObject getObj(JsonObject obj, String key) {
        if (obj == null || !obj.has(key)
            || obj.get(key)
                .isJsonNull()
            || !obj.get(key)
                .isJsonObject()) {
            return null;
        }
        return obj.getAsJsonObject(key);
    }

    static JsonArray getArray(JsonObject obj, String key) {
        if (obj == null || !obj.has(key)
            || obj.get(key)
                .isJsonNull()
            || !obj.get(key)
                .isJsonArray()) {
            return null;
        }
        return obj.getAsJsonArray(key);
    }

    static String getString(JsonObject obj, String key) {
        try {
            if (obj == null || !obj.has(key)
                || obj.get(key)
                    .isJsonNull()) {
                return "";
            }
            return obj.get(key)
                .getAsString();
        } catch (Exception e) {
            return "";
        }
    }

    static long getLong(JsonObject obj, String key) {
        try {
            if (obj == null || !obj.has(key)
                || obj.get(key)
                    .isJsonNull()) {
                return 0;
            }
            return obj.get(key)
                .getAsLong();
        } catch (Exception e) {
            return 0;
        }
    }

    static String firstNotEmpty(String a, String b) {
        if (a != null && !a.isEmpty()) {
            return a;
        }
        return b == null ? "" : b;
    }
}
