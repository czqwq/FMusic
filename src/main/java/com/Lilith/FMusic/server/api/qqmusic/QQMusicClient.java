package com.Lilith.FMusic.server.api.qqmusic;
import net.minecraft.util.StatCollector;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import com.Lilith.FMusic.server.core.FMusic;
import com.Lilith.FMusic.server.core.objs.HttpResObj;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class QQMusicClient {

    private static final int PLAYLIST_PAGE_SIZE = 500;

    public static List<QQSong> search(String keyword, int limit) {
        boolean guest = !QQMusicHttpClient.hasLoginCookie();
        if (guest) {
            List<QQSong> guestList = searchSmartbox(keyword, limit);
            if (guestList != null && !guestList.isEmpty()) {
                return guestList;
            }
            QQMusicHttpClient.log(StatCollector.translateToLocal("fmusic.log.qq.smartbox_empty_try_musicu"));
        }

        List<QQSong> list = searchMusicu(keyword, limit, guest);

        if (list != null && !list.isEmpty()) {
            return list;
        }

        QQMusicHttpClient.log(StatCollector.translateToLocal("fmusic.log.qq.musicu_empty_try_old"));
        return searchOld(keyword, limit, guest);
    }

    private static List<QQSong> searchSmartbox(String keyword, int limit) {
        List<QQSong> list = new ArrayList<>();

        try {
            if (keyword == null || keyword.trim()
                .isEmpty()) {
                QQMusicHttpClient.log(StatCollector.translateToLocal("fmusic.log.qq.guest_keyword_empty"));
                return null;
            }

            keyword = keyword.trim();
            String url = QQMusicHttpClient.SMARTBOX_URL + "?key="
                + QQMusicHttpClient.enc(keyword)
                + "&is_xml=0"
                + "&format=json"
                + "&hostUin=0";

            HttpResObj res = QQMusicHttpClient.getAnonymous(url);
            if (res == null || !res.ok || res.data == null || res.data.isEmpty()) {
                QQMusicHttpClient.log(StatCollector.translateToLocal("fmusic.log.qq.smartbox_req_fail"));
                return null;
            }

            JsonObject root = parseObj(res.data);
            JsonObject data = QQSong.getObj(root, "data");
            JsonObject song = QQSong.getObj(data, "song");
            JsonArray arr = QQSong.getArray(song, "itemlist");

            if (arr == null || arr.size() == 0) {
                QQMusicHttpClient
                    .log(StatCollector.translateToLocalFormatted("fmusic.log.qq.smartbox_empty", keyword, QQMusicHttpClient.cut(res.data, 1000)));
                return null;
            }

            int max = limit <= 0 ? arr.size() : Math.min(limit, arr.size());
            for (int i = 0; i < max; i++) {
                JsonElement element = arr.get(i);
                if (element == null || !element.isJsonObject()) {
                    continue;
                }

                QQSong temp = QQSong.fromSmartboxItem(element.getAsJsonObject());
                if (temp != null && temp.mid != null && !temp.mid.isEmpty()) {
                    list.add(temp);
                }
            }

            QQMusicHttpClient.log(StatCollector.translateToLocalFormatted("fmusic.log.qq.smartbox_ok", keyword, list.size()));
        } catch (Exception e) {
            QQMusicHttpClient.log(StatCollector.translateToLocal("fmusic.log.qq.smartbox_parse_error"));
            if (QQSong.debug) {
                e.printStackTrace();
            }
            return null;
        }

        return list.isEmpty() ? null : list;
    }

    private static List<QQSong> searchMusicu(String keyword, int limit, boolean guest) {
        List<QQSong> list = new ArrayList<>();

        try {
            if (keyword == null || keyword.trim()
                .isEmpty()) {
                QQMusicHttpClient.log(StatCollector.translateToLocal("fmusic.log.qq.keyword_empty"));
                return null;
            }

            keyword = keyword.trim();

            JsonObject req = new JsonObject();
            JsonObject comm = baseComm();
            comm.addProperty("inCharset", "utf-8");
            comm.addProperty("outCharset", "utf-8");
            comm.addProperty("platform", "yqq.json");
            comm.addProperty("needNewCode", 0);
            if (guest) {
                comm.addProperty("uin", "0");
            }
            req.add("comm", comm);

            JsonObject search = new JsonObject();
            search.addProperty("module", "music.search.SearchCgiService");
            search.addProperty("method", "DoSearchForQQMusicDesktop");

            JsonObject param = new JsonObject();
            param.addProperty("query", keyword);
            param.addProperty("num_per_page", limit <= 0 ? 30 : limit);
            param.addProperty("page_num", 1);
            param.addProperty("search_type", 0);
            param.addProperty("remoteplace", "txt.yqq.center");

            search.add("param", param);
            req.add("req_1", search);

            String body = FMusic.gson.toJson(req);
            HttpResObj res = guest ? QQMusicHttpClient.postJsonAnonymous(QQMusicHttpClient.MUSICU_URL, body)
                : QQMusicHttpClient.postJson(QQMusicHttpClient.MUSICU_URL, body);
            if (res == null || !res.ok || res.data == null || res.data.isEmpty()) {
                QQMusicHttpClient.log(StatCollector.translateToLocal("fmusic.log.qq.musicu_req_fail"));
                return null;
            }

            JsonObject root = parseObj(res.data);
            JsonArray arr = getSearchSongList(root);

            if (arr == null || arr.size() == 0) {
                QQMusicHttpClient.log(
                    "<yellow>QQ音乐musicu搜索结果为空，keyword=" + keyword + "，返回=" + QQMusicHttpClient.cut(res.data, 1000));
                return null;
            }

            for (JsonElement element : arr) {
                if (element == null || !element.isJsonObject()) {
                    continue;
                }

                QQSong temp = QQSong.fromSearchItem(element.getAsJsonObject());
                if (temp != null && !temp.realId()
                    .isEmpty()) {
                    list.add(temp);
                }
            }

            QQMusicHttpClient.log(StatCollector.translateToLocalFormatted("fmusic.log.qq.musicu_ok", keyword, list.size()));
        } catch (Exception e) {
            QQMusicHttpClient.log(StatCollector.translateToLocal("fmusic.log.qq.musicu_parse_error"));
            if (QQSong.debug) {
                e.printStackTrace();
            }
            return null;
        }

        return list.isEmpty() ? null : list;
    }

    private static List<QQSong> searchOld(String keyword, int limit, boolean guest) {
        List<QQSong> list = new ArrayList<>();

        try {
            if (keyword == null || keyword.trim()
                .isEmpty()) {
                return null;
            }

            keyword = keyword.trim();

            String url = QQMusicHttpClient.SEARCH_OLD_URL + "?ct=24"
                + "&qqmusic_ver=1298"
                + "&new_json=1"
                + "&remoteplace=txt.yqq.center"
                + "&searchid="
                + (100000000000000L + Math.abs(new Random().nextLong() % 899999999999999L))
                + "&t=0"
                + "&aggr=1"
                + "&cr=1"
                + "&catZhida=1"
                + "&lossless=0"
                + "&flag_qc=0"
                + "&p=1"
                + "&n="
                + (limit <= 0 ? 30 : limit)
                + "&w="
                + QQMusicHttpClient.enc(keyword)
                + "&g_tk=5381"
                + "&loginUin="
                + (guest ? "0" : QQMusicHttpClient.getUin())
                + "&hostUin=0"
                + "&format=json"
                + "&inCharset=utf8"
                + "&outCharset=utf-8"
                + "&notice=0"
                + "&platform=yqq.json"
                + "&needNewCode=0";

            HttpResObj res = guest ? QQMusicHttpClient.getAnonymous(url) : QQMusicHttpClient.get(url);
            if (res == null || !res.ok || res.data == null || res.data.isEmpty()) {
                QQMusicHttpClient.log(StatCollector.translateToLocal("fmusic.log.qq.old_req_fail"));
                return null;
            }

            JsonObject root = parseObj(res.data);
            JsonObject data = QQSong.getObj(root, "data");
            JsonObject song = QQSong.getObj(data, "song");
            JsonArray arr = QQSong.getArray(song, "list");

            if (arr == null || arr.size() == 0) {
                QQMusicHttpClient
                    .log(StatCollector.translateToLocalFormatted("fmusic.log.qq.old_empty", keyword, QQMusicHttpClient.cut(res.data, 1000)));
                return null;
            }

            for (JsonElement element : arr) {
                if (element == null || !element.isJsonObject()) {
                    continue;
                }

                QQSong temp = QQSong.fromSearchItem(element.getAsJsonObject());
                if (temp != null && !temp.realId()
                    .isEmpty()) {
                    list.add(temp);
                }
            }

            QQMusicHttpClient.log(StatCollector.translateToLocalFormatted("fmusic.log.qq.old_ok", keyword, list.size()));
        } catch (Exception e) {
            QQMusicHttpClient.log(StatCollector.translateToLocal("fmusic.log.qq.old_parse_error"));
            if (QQSong.debug) {
                e.printStackTrace();
            }
            return null;
        }

        return list.isEmpty() ? null : list;
    }

    private static JsonArray getSearchSongList(JsonObject root) {
        JsonArray arr = getSongListByPath(root, "req_1", "data", "body", "song");
        if (arr != null) {
            return arr;
        }

        arr = getSongListByPath(root, "req_1", "data", null, "song");
        if (arr != null) {
            return arr;
        }

        arr = getSongListByPath(root, "data", "body", null, "song");
        if (arr != null) {
            return arr;
        }

        return getSongListByPath(root, "data", null, null, "song");
    }

    private static JsonArray getSongListByPath(JsonObject root, String a, String b, String c, String d) {
        JsonObject obj = root;
        if (a != null) {
            obj = QQSong.getObj(obj, a);
        }
        if (b != null) {
            obj = QQSong.getObj(obj, b);
        }
        if (c != null) {
            obj = QQSong.getObj(obj, c);
        }
        if (d != null) {
            obj = QQSong.getObj(obj, d);
        }
        return QQSong.getArray(obj, "list");
    }

    public static PlaylistInfo getPlaylist(String playlistId) {
        String id = playlistId == null ? "" : playlistId.trim();
        if (!id.matches("[0-9]{1,20}")) {
            return null;
        }

        try {
            long dissId = Long.parseLong(id);
            Set<String> songIds = new LinkedHashSet<>();
            String name = "";
            int begin = 0;
            int total = Integer.MAX_VALUE;
            int page = 0;

            while (begin < total && begin >= 0 && page++ < 1000) {
                JsonObject req = new JsonObject();
                req.add("comm", baseComm());

                JsonObject detail = new JsonObject();
                detail.addProperty("module", "music.srfDissInfo.DissInfo");
                detail.addProperty("method", "CgiGetDiss");

                JsonObject param = new JsonObject();
                param.addProperty("disstid", dissId);
                param.addProperty("userinfo", 1);
                param.addProperty("tag", 1);
                param.addProperty("song_begin", begin);
                param.addProperty("song_num", PLAYLIST_PAGE_SIZE);
                detail.add("param", param);
                req.add("req_0", detail);

                HttpResObj res = QQMusicHttpClient.postJson(QQMusicHttpClient.MUSICU_URL, FMusic.gson.toJson(req));
                if (res == null || !res.ok || res.data == null || res.data.isEmpty()) {
                    return songIds.isEmpty() ? null : new PlaylistInfo(name, new ArrayList<>(songIds));
                }

                JsonObject root = parseObj(res.data);
                JsonObject req0 = QQSong.getObj(root, "req_0");
                JsonObject data = QQSong.getObj(req0, "data");
                if (getInt(req0, "code", -1) != 0 || data == null || getInt(data, "code", 0) != 0) {
                    break;
                }

                JsonObject dirInfo = QQSong.getObj(data, "dirinfo");
                String pageName = QQSong.getString(dirInfo, "title");
                if (!pageName.isEmpty()) {
                    name = pageName;
                }

                JsonArray items = QQSong.getArray(data, "songlist");
                if (items == null || items.size() == 0) {
                    break;
                }

                int oldSize = songIds.size();
                for (JsonElement element : items) {
                    if (element == null || !element.isJsonObject()) {
                        continue;
                    }
                    QQSong song = QQSong.fromSingleSong(element.getAsJsonObject());
                    if (song != null && song.realId()
                        .matches("[0-9A-Za-z]{10,32}")) {
                        songIds.add(song.realId());
                    }
                }

                total = getInt(data, "total_song_num", begin + items.size());
                begin += items.size();
                if (songIds.size() == oldSize) {
                    break;
                }
            }

            if (songIds.isEmpty()) {
                return null;
            }
            if (name.isEmpty()) {
                name = "QQMusic " + id;
            }
            return new PlaylistInfo(name, new ArrayList<>(songIds));
        } catch (Exception e) {
            QQMusicHttpClient.log(StatCollector.translateToLocalFormatted("fmusic.log.qq.playlist_error", id));
            if (QQSong.debug) {
                e.printStackTrace();
            }
            return null;
        }
    }

    public static QQSong getSong(String id) {
        try {
            if (id == null || id.trim()
                .isEmpty()) {
                return null;
            }

            id = id.trim();

            JsonObject req = new JsonObject();
            req.add("comm", baseComm());

            JsonObject detail = new JsonObject();
            detail.addProperty("module", "music.pf_song_detail_svr");
            detail.addProperty("method", "get_song_detail_yqq");

            JsonObject param = new JsonObject();
            param.addProperty("song_mid", id);

            detail.add("param", param);
            req.add("req_0", detail);

            HttpResObj res = QQMusicHttpClient.postJson(QQMusicHttpClient.MUSICU_URL, FMusic.gson.toJson(req));
            if (res == null || !res.ok || res.data == null || res.data.isEmpty()) {
                QQMusicHttpClient.log(StatCollector.translateToLocalFormatted("fmusic.log.qq.detail_req_fail", id));
                return null;
            }

            JsonObject root = parseObj(res.data);
            JsonObject req0 = QQSong.getObj(root, "req_0");
            JsonObject data = QQSong.getObj(req0, "data");
            JsonObject track = QQSong.getObj(data, "track_info");

            if (track == null) {
                QQMusicHttpClient.log(StatCollector.translateToLocalFormatted("fmusic.log.qq.detail_empty", id, QQMusicHttpClient.cut(res.data, 1000)));
                return null;
            }

            QQSong song = QQSong.fromSingleSong(track);
            if (song.realId()
                .isEmpty()) {
                song.mid = id;
            }

            return song;
        } catch (Exception e) {
            QQMusicHttpClient.log(StatCollector.translateToLocalFormatted("fmusic.log.qq.detail_parse_error", id));
            if (QQSong.debug) {
                e.printStackTrace();
            }
            return null;
        }
    }

    public static String getPlayUrl(String id) {
        try {
            if (id == null || id.trim()
                .isEmpty()) {
                return null;
            }

            id = id.trim();
            QQSong song = getSong(id);
            if (song == null) {
                song = new QQSong();
                song.mid = id;
                song.mediaMid = id;
            }
            return getPlayUrl(song);
        } catch (Exception e) {
            QQMusicHttpClient.log(StatCollector.translateToLocalFormatted("fmusic.log.qq.play_url_parse_error", id));
            if (QQSong.debug) {
                e.printStackTrace();
            }
            return null;
        }
    }

    public static String getPlayUrl(QQSong song) {
        try {
            if (song == null || song.realId()
                .isEmpty()) {
                return null;
            }

            String songMid = song.realId();
            String mediaMid = song.realMediaMid();
            String guid = getGuid();
            String uin = QQMusicHttpClient.getUin();
            String filename = "M500" + mediaMid + ".mp3";

            JsonObject req = new JsonObject();
            JsonObject comm = baseComm();
            comm.addProperty("uin", uin);
            req.add("comm", comm);

            JsonObject vkey = new JsonObject();
            vkey.addProperty("module", "vkey.GetVkeyServer");
            vkey.addProperty("method", "CgiGetVkey");

            JsonObject param = new JsonObject();

            JsonArray mids = new JsonArray();
            mids.add(songMid);

            JsonArray filenames = new JsonArray();
            filenames.add(filename);

            JsonArray songtypes = new JsonArray();
            songtypes.add(0);

            param.add("songmid", mids);
            param.add("filename", filenames);
            param.addProperty("guid", guid);
            param.add("songtype", songtypes);
            param.addProperty("uin", uin);
            param.addProperty("loginflag", 1);
            param.addProperty("platform", "20");

            vkey.add("param", param);
            req.add("req_0", vkey);

            HttpResObj res = QQMusicHttpClient.postJson(QQMusicHttpClient.MUSICU_URL, FMusic.gson.toJson(req));
            if (res == null || !res.ok || res.data == null || res.data.isEmpty()) {
                QQMusicHttpClient.log(StatCollector.translateToLocalFormatted("fmusic.log.qq.play_url_req_fail", songMid));
                return null;
            }

            JsonObject root = parseObj(res.data);
            JsonObject req0 = QQSong.getObj(root, "req_0");
            JsonObject data = QQSong.getObj(req0, "data");
            JsonArray midurlinfo = QQSong.getArray(data, "midurlinfo");

            if (midurlinfo == null || midurlinfo.size() == 0
                || !midurlinfo.get(0)
                    .isJsonObject()) {
                QQMusicHttpClient
                    .log(StatCollector.translateToLocalFormatted("fmusic.log.qq.midurlinfo_empty", songMid, QQMusicHttpClient.cut(res.data, 1000)));
                return null;
            }

            JsonArray sip = QQSong.getArray(data, "sip");
            String host = getSipHost(sip);

            JsonObject info = midurlinfo.get(0)
                .getAsJsonObject();
            String purl = QQSong.getString(info, "purl");
            int result = getInt(info, "result", -1);

            if (purl != null && !purl.isEmpty() && result == 0) {
                QQMusicHttpClient
                    .log(StatCollector.translateToLocalFormatted("fmusic.log.qq.play_url_ok", songMid, mediaMid, filename));
                return host + purl;
            }

            QQMusicHttpClient.log(
                "<yellow>QQ音乐正式播放链接为空：songmid=" + songMid
                    + "，media_mid="
                    + mediaMid
                    + "，filename="
                    + filename
                    + "，result="
                    + result
                    + "，返回="
                    + QQMusicHttpClient.cut(res.data, 1000));
            return null;
        } catch (Exception e) {
            String id = song == null ? "null" : song.realId();
            QQMusicHttpClient.log(StatCollector.translateToLocalFormatted("fmusic.log.qq.play_url_parse_error", id));
            if (QQSong.debug) {
                e.printStackTrace();
            }
            return null;
        }
    }

    private static String getGuid() {
        String guid = QQMusicHttpClient.getCookieValue("pgv_pvid", "");
        if (guid == null || guid.isEmpty()) {
            guid = QQMusicHttpClient.getCookieValue("fqm_pvqid", "");
        }
        return guid == null || guid.isEmpty() ? "10000" : guid;
    }

    private static String getSipHost(JsonArray sip) {
        String host = "http://aqqmusic.tc.qq.com/";
        if (sip != null && sip.size() > 0
            && !sip.get(0)
                .isJsonNull()) {
            String temp = sip.get(0)
                .getAsString();
            if (temp != null && !temp.isEmpty()) {
                host = temp;
            }
        }
        return host;
    }

    private static int getInt(JsonObject obj, String key, int def) {
        try {
            if (obj == null || !obj.has(key)
                || obj.get(key)
                    .isJsonNull()) {
                return def;
            }
            return obj.get(key)
                .getAsInt();
        } catch (Exception e) {
            return def;
        }
    }

    public static String getLyricText(String id) {
        try {
            if (id == null || id.trim()
                .isEmpty()) {
                return null;
            }

            id = id.trim();

            String url = QQMusicHttpClient.LYRIC_URL + "?songmid="
                + QQMusicHttpClient.enc(id)
                + "&format=json&nobase64=1";

            HttpResObj res = QQMusicHttpClient.get(url);
            if (res == null || !res.ok || res.data == null || res.data.isEmpty()) {
                QQMusicHttpClient.log(StatCollector.translateToLocalFormatted("fmusic.log.qq.lyric_req_fail", id));
                return null;
            }

            JsonObject root = parseObj(res.data);
            String lyric = QQSong.getString(root, "lyric");

            if (lyric == null || lyric.isEmpty()) {
                QQMusicHttpClient.log(StatCollector.translateToLocalFormatted("fmusic.log.qq.lyric_empty", id, QQMusicHttpClient.cut(res.data, 1000)));
            }

            return lyric;
        } catch (Exception e) {
            QQMusicHttpClient.log(StatCollector.translateToLocalFormatted("fmusic.log.qq.lyric_parse_error", id));
            if (QQSong.debug) {
                e.printStackTrace();
            }
            return null;
        }
    }

    private static JsonObject baseComm() {
        JsonObject comm = new JsonObject();
        comm.addProperty("ct", 24);
        comm.addProperty("cv", 0);
        comm.addProperty("format", "json");
        return comm;
    }

    public static final class PlaylistInfo {

        private final String name;
        private final List<String> songIds;

        private PlaylistInfo(String name, List<String> songIds) {
            this.name = name;
            this.songIds = songIds;
        }

        public String getName() {
            return name;
        }

        public List<String> getSongIds() {
            return songIds;
        }
    }

    @SuppressWarnings("deprecation")
    private static JsonObject parseObj(String body) {
        return new JsonParser().parse(body)
            .getAsJsonObject();
    }
}
