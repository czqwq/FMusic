package com.Lilith.FMusic.server.api.kugou;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.Lilith.FMusic.server.core.objs.HttpResObj;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class KugouClient {

    private static final long PLAY_URL_CACHE_MILLIS = 20_000L;
    private static final int PLAYLIST_PAGE_SIZE = 500;
    private static final String PLAYLIST_INFO_URL = "http://mobilecdn.kugou.com/api/v3/special/info";
    private static final String PLAYLIST_SONG_URL = "http://mobilecdn.kugou.com/api/v3/special/song";

    private static final Map<String, KugouSong> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, CachedPlayUrl> PLAY_URL_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Object> PLAY_LOCKS = new ConcurrentHashMap<>();

    private KugouClient() {}

    public static PlaylistInfo getPlaylist(String playlistId) {
        String id = playlistId == null ? "" : playlistId.trim();
        if (!id.matches("[0-9]{1,20}")) {
            return null;
        }

        try {
            String name = getPlaylistName(id);
            if (name == null || name.trim()
                .isEmpty()) {
                name = "Kugou " + id;
            }
            Set<String> songIds = new LinkedHashSet<>();
            int page = 1;
            long total = Long.MAX_VALUE;

            while (page <= 1000 && songIds.size() < total) {
                Map<String, String> params = new LinkedHashMap<>();
                params.put("specialid", id);
                params.put("page", String.valueOf(page));
                params.put("pagesize", String.valueOf(PLAYLIST_PAGE_SIZE));
                params.put("plat", "0");
                params.put("version", "9108");
                params.put("area_code", "1");

                HttpResObj response = KugouHttpClient.getWeb(PLAYLIST_SONG_URL, params);
                if (response == null || !response.ok
                    || response.data == null
                    || response.data.trim()
                        .isEmpty()) {
                    return songIds.isEmpty() ? null : new PlaylistInfo(name, new ArrayList<>(songIds));
                }

                JsonObject root = parseObj(response.data);
                JsonObject data = KugouSong.getObj(root, "data");
                JsonArray items = KugouSong.getArray(data, "info");
                if (getInt(root, "status", 0) != 1 || items == null || items.size() == 0) {
                    break;
                }

                total = Math.max(0, KugouSong.number(data, 0, "total"));
                int oldSize = songIds.size();
                for (JsonElement element : items) {
                    if (element == null || !element.isJsonObject()) {
                        continue;
                    }
                    KugouSong song = KugouSong.fromSearchItem(element.getAsJsonObject());
                    if (song == null || song.realId()
                        .isEmpty()) {
                        continue;
                    }
                    cache(song);
                    songIds.add(song.realId());
                }
                if (songIds.size() == oldSize) {
                    break;
                }
                page++;
            }

            if (songIds.isEmpty()) {
                return null;
            }
            return new PlaylistInfo(name, new ArrayList<>(songIds));
        } catch (Exception e) {
            KugouHttpClient.log("<red>酷狗音乐歌单解析错误：" + id);
            if (KugouSong.debug) {
                e.printStackTrace();
            }
            return null;
        }
    }

    private static String getPlaylistName(String id) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("specialid", id);
        params.put("plat", "0");
        params.put("version", "9108");

        HttpResObj response = KugouHttpClient.getWeb(PLAYLIST_INFO_URL, params);
        if (response == null || !response.ok
            || response.data == null
            || response.data.trim()
                .isEmpty()) {
            return "";
        }
        JsonObject root = parseObj(response.data);
        JsonObject data = KugouSong.getObj(root, "data");
        return KugouSong.first(data, "specialname");
    }

    public static List<KugouSong> search(String keyword, int limit) {
        if (keyword == null || keyword.trim()
            .isEmpty()) {
            return null;
        }
        int size = limit <= 0 ? 30 : Math.min(limit, 100);
        List<KugouSong> songs = searchComplexWeb(keyword.trim(), size);
        if (songs == null || songs.isEmpty()) {
            KugouHttpClient.log("<yellow>酷狗复杂搜索不可用，降级到旧网页搜索：" + keyword);
            songs = searchWeb(keyword.trim(), size);
        }
        return songs == null || songs.isEmpty() ? null : songs;
    }

    private static List<KugouSong> searchComplexWeb(String keyword, int limit) {
        try {
            String mid = KugouHttpClient.getMid();
            String dfid = KugouHttpClient.getDfid();
            String userId = KugouHttpClient.getUserId("0");
            String token = firstCookieValue("token", "t");
            String clientTime = String.valueOf(System.currentTimeMillis());

            Map<String, String> params = new LinkedHashMap<>();
            params.put("callback", "callback123");
            params.put("srcappid", "2919");
            params.put("clientver", "1000");
            params.put("clienttime", clientTime);
            params.put("mid", mid);
            params.put("uuid", mid);
            params.put("dfid", dfid);
            params.put("keyword", keyword);
            params.put("page", "1");
            params.put("pagesize", String.valueOf(limit));
            params.put("bitrate", "0");
            params.put("isfuzzy", "0");
            params.put("inputtype", "0");
            params.put("platform", "WebFilter");
            params.put("userid", userId);
            params.put("iscorrection", "1");
            params.put("privilege_filter", "0");
            params.put("filter", "10");
            params.put("token", token);
            params.put("appid", "1014");
            params.put("signature", KugouCrypto.webSignature(params));

            HttpResObj response = KugouHttpClient.getWeb(KugouHttpClient.WEB_COMPLEX_SEARCH_URL, params);
            List<KugouSong> result = parseSearchResponse(response);
            if (result != null && !result.isEmpty()) {
                int encodedCount = 0;
                for (KugouSong song : result) {
                    if (song != null && song.encodedAlbumAudioId != null && !song.encodedAlbumAudioId.isEmpty()) {
                        encodedCount++;
                    }
                }
                KugouHttpClient.log(
                    "<green>酷狗复杂网页搜索成功：" + keyword
                        + "，数量="
                        + result.size()
                        + "，含encode_album_audio_id="
                        + encodedCount);
                return result;
            }
        } catch (Exception e) {
            KugouHttpClient.log("<yellow>酷狗复杂网页搜索解析失败：" + keyword);
            if (KugouSong.debug) {
                e.printStackTrace();
            }
        }
        return null;
    }

    private static List<KugouSong> searchWeb(String keyword, int limit) {
        try {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("keyword", keyword);
            params.put("page", "1");
            params.put("pagesize", String.valueOf(limit));
            params.put("userid", KugouHttpClient.getUserId("-1"));
            params.put("clientver", "1000");
            params.put("platform", "WebFilter");
            params.put("filter", "2");
            params.put("iscorrection", "1");
            params.put("privilege_filter", "0");
            params.put("area_code", "1");

            HttpResObj response = KugouHttpClient.getWeb(KugouHttpClient.WEB_SEARCH_URL, params);
            List<KugouSong> result = parseSearchResponse(response);
            if (result != null && !result.isEmpty()) {
                KugouHttpClient.log("<green>酷狗音乐网页搜索成功：" + keyword + "，数量=" + result.size());
                return result;
            }
            KugouHttpClient.log("<red>酷狗音乐网页搜索结果为空：" + keyword);
        } catch (Exception e) {
            KugouHttpClient.log("<red>酷狗音乐网页搜索解析错误");
            if (KugouSong.debug) {
                e.printStackTrace();
            }
        }
        return null;
    }

    private static List<KugouSong> parseSearchResponse(HttpResObj response) {
        if (response == null || !response.ok
            || response.data == null
            || response.data.trim()
                .isEmpty()) {
            return null;
        }
        JsonObject root = parseObj(response.data);
        JsonArray items = searchItems(root);
        if (items == null || items.size() == 0) {
            KugouHttpClient.log("<yellow>酷狗音乐搜索接口无列表，返回=" + KugouHttpClient.cut(response.data, 1000));
            return null;
        }

        List<KugouSong> result = new ArrayList<>();
        for (JsonElement element : items) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            KugouSong song = KugouSong.fromSearchItem(element.getAsJsonObject());
            if (song == null || song.realId()
                .isEmpty()) {
                continue;
            }
            cache(song);
            result.add(song);
        }
        return result;
    }

    private static JsonArray searchItems(JsonObject root) {
        JsonObject data = KugouSong.getObj(root, "data");
        JsonArray array = KugouSong.getArray(data, "lists", "list", "info", "items", "songs");
        if (array != null) {
            return array;
        }
        array = KugouSong.getArray(root, "lists", "list", "info", "items", "songs");
        if (array != null) {
            return array;
        }
        JsonObject body = KugouSong.getObj(data, "body");
        return KugouSong.getArray(body, "lists", "list", "info", "items", "songs");
    }

    public static KugouSong getSong(String hash) {
        String id = normalizeHash(hash);
        if (id.isEmpty()) {
            return null;
        }
        // 播放地址是有时效的，不在这里请求，避免 getMusic() + getPlayUrl() 重复调用网页接口。
        return CACHE.get(id);
    }

    public static void rememberSongIdentifiers(String hash, String albumId, String albumAudioId) {
        String id = normalizeHash(hash);
        if (id.isEmpty()) {
            return;
        }
        KugouSong song = new KugouSong();
        song.hash = id;
        song.albumId = numericId(albumId);
        song.albumAudioId = numericId(albumAudioId);
        cache(song);
    }

    private static String numericId(String value) {
        return value != null && value.matches("[0-9]+") ? value : "";
    }

    private static KugouSong getWebDetail(String hash, KugouSong known) {
        return requestWebDetail(hash, known);
    }

    private static KugouSong requestWebDetail(String hash, KugouSong known) {
        try {
            if (known == null) {
                KugouHttpClient.log("<yellow>酷狗网页播放缺少搜索缓存：" + hash);
                return null;
            }

            // Web 播放接口需要同时传两个不同的 ID：
            // album_id = 搜索结果中的 AlbumID（真正的专辑 ID）
            // album_audio_id = 搜索结果中的 MixSongID（歌曲实例 ID）
            long albumId = known.albumIdNumber();
            long albumAudioId = known.albumAudioIdNumber();
            if (albumAudioId <= 0) {
                KugouHttpClient.log("<yellow>酷狗网页播放缺少album_audio_id/MixSongID：" + hash);
                return null;
            }

            Map<String, String> params = new LinkedHashMap<>();
            params.put("r", "play/getdata");
            params.put("hash", hash);
            params.put("album_id", String.valueOf(Math.max(albumId, 0)));
            params.put("album_audio_id", String.valueOf(albumAudioId));
            params.put("mid", KugouHttpClient.getMid());
            params.put("platid", "4");
            params.put("appid", "1014");
            params.put("dfid", KugouHttpClient.getDfid());
            params.put("_", String.valueOf(System.currentTimeMillis()));

            HttpResObj response = requestWebPlay(params);
            if (response == null || !response.ok
                || response.data == null
                || response.data.trim()
                    .isEmpty()) {
                return null;
            }

            JsonObject root = parseObj(response.data);
            int status = getInt(root, "status", 0);
            int errorCode = getInt(root, "err_code", getInt(root, "error_code", 0));
            JsonObject data = KugouSong.getObj(root, "data");
            if (status != 1 || data == null
                || data.entrySet()
                    .isEmpty()) {
                String hint = errorCode == 20010 || errorCode == 30020 ? "；Web接口已拒绝请求，将由Android接口承担主播放链路" : "";
                KugouHttpClient.log(
                    "<yellow>酷狗音乐网页详情为空：" + hash
                        + "，err_code="
                        + errorCode
                        + "，album_id="
                        + albumId
                        + "，album_audio_id="
                        + albumAudioId
                        + hint
                        + "，返回="
                        + KugouHttpClient.redactTextForLog(KugouHttpClient.cut(response.data, 1000)));
                return null;
            }

            KugouSong song = KugouSong.fromDetail(data);
            if (song == null) {
                song = new KugouSong();
            }
            if (song.realId()
                .isEmpty()) {
                song.hash = hash;
            }

            // 网页接口字段可能变化，递归寻找 URL，并优先选择 /full/ 完整音频。
            String playUrl = findPreferredPlayUrl(data);
            if (!isLikelyAudioUrl(playUrl, true)) {
                playUrl = findPreferredPlayUrl(root);
            }
            if (isLikelyAudioUrl(playUrl, true)) {
                song.playUrl = normalizeUrl(playUrl);
                if (isClearlyFullAudioUrl(song.playUrl)) {
                    song.trial = false;
                } else if (isKnownTrialUrl(song.playUrl)) {
                    song.trial = true;
                }
            }
            return song;
        } catch (Exception e) {
            KugouHttpClient.log("<red>酷狗音乐Web歌曲详情解析错误：" + hash);
            if (KugouSong.debug) {
                e.printStackTrace();
            }
            return null;
        }
    }

    private static HttpResObj requestWebPlay(Map<String, String> params) {
        HttpResObj response = KugouHttpClient.getWeb(KugouHttpClient.WEB_DETAIL_URL, params);
        if (isSuccessfulWebPlayResponse(response)) {
            return response;
        }

        KugouHttpClient.log("<yellow>酷狗wwwapi网页播放失败，尝试www网页接口");
        return KugouHttpClient.getWeb(KugouHttpClient.WEB_DETAIL_ALT_URL, params);
    }

    private static boolean isSuccessfulWebPlayResponse(HttpResObj response) {
        if (response == null || !response.ok
            || response.data == null
            || response.data.trim()
                .isEmpty()) {
            return false;
        }
        try {
            JsonObject root = parseObj(response.data);
            JsonObject data = KugouSong.getObj(root, "data");
            return getInt(root, "status", 0) == 1 && data != null
                && !data.entrySet()
                    .isEmpty();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static KugouSong requestWebVipPlay(KugouSong known) {
        if (known == null || known.realId()
            .isEmpty()) {
            return null;
        }
        String encodedAudioId = known.encodedAlbumAudioId == null ? "" : known.encodedAlbumAudioId.trim();
        if (!encodedAudioId.matches("(?i)[0-9a-z]{3,32}")) {
            KugouHttpClient.log(
                "<yellow>酷狗Web会员播放缺少EMixSongID/encode_album_audio_id：" + known.realId()
                    + "，MixSongID="
                    + known.albumAudioId);
            return null;
        }

        try {
            String mid = KugouHttpClient.getMid();
            String dfid = KugouHttpClient.getDfid();
            String userId = KugouHttpClient.getUserId("0");
            String token = firstCookieValue("token", "t");
            if ("-".equals(mid) || "-".equals(dfid) || "0".equals(userId) || token.isEmpty()) {
                KugouHttpClient.log(
                    "<yellow>酷狗Web会员播放身份参数不完整：mid=" + KugouHttpClient.protectedValueForLog(mid)
                        + "，dfid="
                        + KugouHttpClient.protectedValueForLog(dfid)
                        + "，userid="
                        + KugouHttpClient.protectedValueForLog(userId)
                        + "，token="
                        + KugouHttpClient.protectedValueForLog(token));
                return null;
            }

            String clientTime = String.valueOf(System.currentTimeMillis());
            Map<String, String> params = new LinkedHashMap<>();
            params.put("srcappid", "2919");
            params.put("clientver", "20000");
            params.put("clienttime", clientTime);
            params.put("mid", mid);
            params.put("uuid", mid);
            params.put("dfid", dfid);
            params.put("appid", "1014");
            params.put("platid", "4");
            params.put("encode_album_audio_id", encodedAudioId.toLowerCase(Locale.ROOT));
            params.put("token", token);
            params.put("userid", userId);
            params.put("signature", KugouCrypto.webSignature(params));

            KugouHttpClient.log(
                "<gray>酷狗Web会员选中歌曲：name=" + safeLog(known.name)
                    + "，singer="
                    + safeLog(known.singer)
                    + "，hash="
                    + known.realId()
                    + "，album_id="
                    + known.albumId
                    + "，album_audio_id="
                    + known.albumAudioId
                    + "，encode_album_audio_id="
                    + encodedAudioId
                    + "，PayType="
                    + known.payType
                    + "，Price="
                    + known.price
                    + "，Privilege="
                    + known.privilege
                    + "，FailProcess="
                    + known.failProcess);

            HttpResObj response = KugouHttpClient.getWebSongInfo(params);
            if (response == null || !response.ok
                || response.data == null
                || response.data.trim()
                    .isEmpty()) {
                return null;
            }

            JsonObject root = parseObj(response.data);
            int status = getInt(root, "status", 0);
            int errorCode = getInt(root, "err_code", getInt(root, "error_code", 0));
            JsonObject data = KugouSong.getObj(root, "data");
            if (status != 1 || data == null
                || data.entrySet()
                    .isEmpty()) {
                KugouHttpClient.log(
                    "<yellow>酷狗Web会员播放未返回数据：" + known.realId()
                        + "，status="
                        + status
                        + "，err_code="
                        + errorCode
                        + "，响应="
                        + KugouHttpClient.redactTextForLog(response.data));
                return null;
            }

            KugouSong result = KugouSong.fromDetail(data);
            if (result == null) {
                result = new KugouSong();
            }
            result.hash = known.realId();
            result.encodedAlbumAudioId = choose(result.encodedAlbumAudioId, encodedAudioId);
            String playUrl = KugouSong.first(data, "play_url", "playUrl", "play_backup_url", "playBackupUrl");
            if (!isLikelyAudioUrl(playUrl, true)) {
                playUrl = findPreferredPlayUrl(data);
            }
            if (!isLikelyAudioUrl(playUrl, true)) {
                KugouHttpClient.log(
                    "<yellow>酷狗Web会员响应没有可用播放地址：" + known.realId()
                        + "，has_privilege="
                        + KugouSong.bool(data, false, "has_privilege")
                        + "，is_free_part="
                        + KugouSong.first(data, "is_free_part"));
                return null;
            }
            result.playUrl = normalizeUrl(playUrl);
            result.trial = KugouSong.bool(data, false, "is_free_part", "is_trial") || isKnownTrialUrl(result.playUrl);
            return result;
        } catch (Exception e) {
            KugouHttpClient.log("<red>酷狗Web会员播放解析错误：" + known.realId());
            if (KugouSong.debug) {
                e.printStackTrace();
            }
            return null;
        }
    }

    private static KugouSong requestAndroidPlay(KugouSong known) {
        if (known == null || known.realId()
            .isEmpty()) {
            return null;
        }
        try {
            String hash = known.realId()
                .toLowerCase(Locale.ROOT);
            String mid = KugouHttpClient.getMid();
            String dfid = KugouHttpClient.getDfid();
            String userId = firstCookieValue("userid", "user_id", "kg_userid", "KugooID");
            if (!userId.matches("[0-9]+")) {
                userId = "0";
            }
            String token = firstCookieValue("token", "t");
            if ("-".equals(mid) || "-".equals(dfid)) {
                KugouHttpClient.log("<red>酷狗Android播放缺少mid或dfid，请重新导出Cookie");
                return null;
            }
            if ("0".equals(userId)) {
                KugouHttpClient.log("<yellow>酷狗Android播放未找到KugooID，将按匿名账号请求");
            }
            if (token.isEmpty()) {
                KugouHttpClient.log("<yellow>酷狗Android播放未找到t/token，付费歌曲可能无法获取");
            }
            String clientTime = String.valueOf(System.currentTimeMillis() / 1000L);

            KugouHttpClient.log(
                "<gray>酷狗Android选中歌曲：name=" + safeLog(known.name)
                    + "，singer="
                    + safeLog(known.singer)
                    + "，hash="
                    + known.realId()
                    + "，album_id="
                    + Math.max(known.albumIdNumber(), 0)
                    + "，album_audio_id="
                    + Math.max(known.albumAudioIdNumber(), 0)
                    + "，PayType="
                    + known.payType
                    + "，Price="
                    + known.price
                    + "，Privilege="
                    + known.privilege
                    + "，FailProcess="
                    + known.failProcess);
            KugouHttpClient.log(
                "<gray>酷狗Android身份参数：mid=" + KugouHttpClient.protectedValueForLog(mid)
                    + "，dfid="
                    + KugouHttpClient.protectedValueForLog(dfid)
                    + "，userid="
                    + KugouHttpClient.protectedValueForLog(userId)
                    + "，token="
                    + KugouHttpClient.protectedValueForLog(token)
                    + "，clienttime="
                    + clientTime);

            Map<String, String> params = new LinkedHashMap<>();
            params.put("dfid", dfid);
            params.put("mid", mid);
            params.put("uuid", "-");
            params.put("appid", String.valueOf(KugouCrypto.APP_ID));
            params.put("clientver", String.valueOf(KugouCrypto.URL_CLIENT_VER));
            params.put("clienttime", clientTime);
            if (!token.isEmpty()) {
                params.put("token", token);
            }
            if (!"0".equals(userId)) {
                params.put("userid", userId);
            }

            params.put("album_id", String.valueOf(Math.max(known.albumIdNumber(), 0)));
            params.put("area_code", "1");
            params.put("hash", hash);
            params.put("ssa_flag", "is_fromtrack");
            params.put("version", String.valueOf(KugouCrypto.URL_CLIENT_VER));
            params.put("page_id", "151369488");
            params.put("quality", "128");
            params.put("album_audio_id", String.valueOf(Math.max(known.albumAudioIdNumber(), 0)));
            params.put("behavior", "play");
            params.put("pid", "2");
            params.put("cmd", "26");
            params.put("pidversion", "3001");
            params.put("IsFreePart", "0");
            params.put("ppage_id", "463467626,350369493,788954147");
            params.put("cdnBackup", "1");
            params.put("module", "");
            params.put("key", KugouCrypto.playKey(hash, mid, userId));
            params.put("signature", KugouCrypto.androidSignature(params));

            HttpResObj response = KugouHttpClient.getAndroidPlay(params);
            if (response == null || !response.ok
                || response.data == null
                || response.data.trim()
                    .isEmpty()) {
                return null;
            }

            JsonObject root = parseObj(response.data);
            String playUrl = findPreferredPlayUrl(root);
            int status = getInt(root, "status", 0);
            int errorCode = getInt(root, "error_code", getInt(root, "err_code", 0));
            if (!isLikelyAudioUrl(playUrl, true)) {
                KugouHttpClient.log(
                    "<yellow>酷狗Android播放未返回可用地址：" + known.realId()
                        + "，status="
                        + status
                        + "，error_code="
                        + errorCode
                        + "，响应="
                        + KugouHttpClient.redactTextForLog(response.data));
                if (errorCode == 20018) {
                    KugouHttpClient.log(
                        "<yellow>酷狗错误20018诊断：服务端返回priv_status=0。" + "当前搜索项权限元数据为PayType="
                            + known.payType
                            + "、Price="
                            + known.price
                            + "、Privilege="
                            + known.privilege
                            + "、FailProcess="
                            + known.failProcess
                            + "。PowerShell免费歌曲测试成功不能证明该付费歌曲可播放；"
                            + "请用同一首免费歌曲在插件中对照测试。");
                }
                return null;
            }

            JsonObject data = KugouSong.getObj(root, "data");
            KugouSong result = data == null ? new KugouSong() : KugouSong.fromDetail(data);
            if (result == null) {
                result = new KugouSong();
            }
            result.hash = known.realId();
            result.playUrl = normalizeUrl(playUrl);
            result.trial = isKnownTrialUrl(result.playUrl) || isTrial(root);
            return result;
        } catch (Exception e) {
            KugouHttpClient.log("<red>酷狗Android播放解析错误：" + known.realId());
            if (KugouSong.debug) {
                e.printStackTrace();
            }
            return null;
        }
    }

    private static String safeLog(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r", " ")
            .replace("\n", " ");
    }

    private static String firstCookieValue(String... names) {
        for (String name : names) {
            String value = KugouHttpClient.getCookieValue(name, "");
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    public static String getPlayUrl(String hash) {
        String id = normalizeHash(hash);
        if (id.isEmpty()) {
            return null;
        }
        KugouSong song = getSong(id);
        if (song == null) {
            song = new KugouSong();
            song.hash = id;
        }
        return getPlayUrl(song);
    }

    public static String getPlayUrl(KugouSong song) {
        if (song == null || song.realId()
            .isEmpty()) {
            return null;
        }

        String id = song.realId();
        String cached = cachedPlayUrl(id);
        if (cached != null) {
            return cached;
        }

        Object created = new Object();
        Object existing = PLAY_LOCKS.putIfAbsent(id, created);
        Object lock = existing == null ? created : existing;
        try {
            synchronized (lock) {
                cached = cachedPlayUrl(id);
                if (cached != null) {
                    return cached;
                }

                // 网页 /play/songinfo 使用 appid=1014，可识别网页会员权益。
                // Android /v5/url 保留为免费歌曲与兼容性降级。
                KugouSong webVip = requestWebVipPlay(song);
                if (webVip != null) {
                    webVip = merge(webVip, song);
                    cache(webVip);
                    if (isUsablePlayUrl(webVip)) {
                        String url = normalizeUrl(webVip.playUrl);
                        cachePlayUrl(id, url);
                        KugouHttpClient.log("<green>酷狗音乐Web会员完整播放链接获取成功：" + id);
                        return url;
                    }
                }

                KugouHttpClient.log("<yellow>酷狗Web会员未返回正式播放链接，尝试Android备用接口：" + id);
                KugouSong android = requestAndroidPlay(song);
                if (android != null) {
                    android = merge(android, song);
                    cache(android);
                    if (isUsablePlayUrl(android)) {
                        String url = normalizeUrl(android.playUrl);
                        cachePlayUrl(id, url);
                        KugouHttpClient.log("<green>酷狗音乐Android完整播放链接获取成功：" + id);
                        return url;
                    }
                }

                KugouHttpClient.log("<yellow>酷狗Android未返回正式播放链接，尝试旧Web兼容接口：" + id);
                KugouSong detail = getWebDetail(id, song);
                if (detail != null) {
                    detail = merge(detail, song);
                    cache(detail);
                    if (isUsablePlayUrl(detail)) {
                        String url = normalizeUrl(detail.playUrl);
                        cachePlayUrl(id, url);
                        KugouHttpClient.log("<green>酷狗音乐Web完整播放链接获取成功：" + id);
                        return url;
                    }
                }

                KugouHttpClient.log("<yellow>酷狗音乐正式播放链接为空：" + id);
                return null;
            }
        } finally {
            PLAY_LOCKS.remove(id, lock);
        }
    }

    private static String cachedPlayUrl(String id) {
        CachedPlayUrl cached = PLAY_URL_CACHE.get(id);
        if (cached == null) {
            return null;
        }
        if (cached.expiresAt <= System.currentTimeMillis() || !isLikelyAudioUrl(cached.url, true)) {
            PLAY_URL_CACHE.remove(id, cached);
            return null;
        }
        return cached.url;
    }

    private static void cachePlayUrl(String id, String url) {
        if (id == null || id.isEmpty() || !isLikelyAudioUrl(url, true)) {
            return;
        }
        PLAY_URL_CACHE.put(id, new CachedPlayUrl(url, System.currentTimeMillis() + PLAY_URL_CACHE_MILLIS));
    }

    public static String getLyricText(String hash) {
        String id = normalizeHash(hash);
        if (id.isEmpty()) {
            return null;
        }
        KugouSong song = getSong(id);
        if (song == null) {
            song = new KugouSong();
            song.hash = id;
        }
        if (song.lyricText != null && !song.lyricText.trim()
            .isEmpty()) {
            return song.lyricText;
        }

        // 纯 Web：只使用 PC/Web 歌词搜索与下载，不调用 Android 签名链路。
        LyricCandidate candidate = searchLyricLegacy(song);
        if (candidate != null) {
            return downloadLyric(candidate, false);
        }
        KugouHttpClient.log("<yellow>酷狗音乐歌词候选为空：" + id);
        return null;
    }

    private static LyricCandidate searchLyricLegacy(KugouSong song) {
        try {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("ver", "1");
            params.put("man", "yes");
            params.put("client", "pc");
            params.put("keyword", lyricKeyword(song));
            params.put("duration", String.valueOf(song.durationMs));
            params.put("hash", song.realId());
            HttpResObj response = KugouHttpClient.getWeb(KugouHttpClient.LYRIC_SEARCH_OLD_URL, params);
            return parseLyricCandidate(response);
        } catch (Exception e) {
            if (KugouSong.debug) {
                e.printStackTrace();
            }
            return null;
        }
    }

    private static LyricCandidate parseLyricCandidate(HttpResObj response) {
        if (response == null || !response.ok
            || response.data == null
            || response.data.trim()
                .isEmpty()) {
            return null;
        }
        JsonObject root = parseObj(response.data);
        JsonArray candidates = KugouSong.getArray(root, "candidates", "candidate", "items", "list");
        if (candidates == null) {
            JsonObject data = KugouSong.getObj(root, "data");
            candidates = KugouSong.getArray(data, "candidates", "candidate", "items", "list");
        }
        if (candidates == null || candidates.size() == 0) {
            return null;
        }
        for (JsonElement element : candidates) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject item = element.getAsJsonObject();
            String lyricId = KugouSong.first(item, "id", "lyric_id", "lyricId");
            String accessKey = KugouSong.first(item, "accesskey", "access_key", "accessKey");
            if (!lyricId.isEmpty() && !accessKey.isEmpty()) {
                return new LyricCandidate(lyricId, accessKey);
            }
        }
        return null;
    }

    private static String downloadLyric(LyricCandidate candidate, boolean ignored) {
        try {
            Map<String, String> params = new LinkedHashMap<>();
            params.put("ver", "1");
            params.put("client", "pc");
            params.put("id", candidate.id);
            params.put("accesskey", candidate.accessKey);
            params.put("fmt", "lrc");
            params.put("charset", "utf8");

            HttpResObj response = KugouHttpClient.getWeb(KugouHttpClient.LYRIC_DOWNLOAD_URL, params);
            if (response == null || !response.ok
                || response.data == null
                || response.data.trim()
                    .isEmpty()) {
                return null;
            }
            JsonObject root = parseObj(response.data);
            String content = KugouSong.first(root, "content");
            if (content.isEmpty()) {
                JsonObject data = KugouSong.getObj(root, "data");
                content = KugouSong.first(data, "content");
            }
            return decodeBase64Lyric(content);
        } catch (Exception e) {
            KugouHttpClient.log("<red>酷狗音乐歌词下载解析错误");
            if (KugouSong.debug) {
                e.printStackTrace();
            }
            return null;
        }
    }

    private static String decodeBase64Lyric(String content) {
        if (content == null || content.trim()
            .isEmpty()) {
            return null;
        }
        String value = content.trim();
        if (value.startsWith("[")) {
            return value;
        }
        try {
            byte[] bytes = Base64.getDecoder()
                .decode(value.replaceAll("\\s+", ""));
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String lyricKeyword(KugouSong song) {
        String singer = song.singer == null ? "" : song.singer.trim();
        String name = song.name == null ? "" : song.name.trim();
        if (!singer.isEmpty() && !name.isEmpty()) {
            return singer + " - " + name;
        }
        if (!name.isEmpty()) {
            return name;
        }
        return song.realId();
    }

    private static String findPreferredPlayUrl(JsonElement element) {
        String full = findUrlByPlaybackKeys(element, true, 0);
        if (isLikelyAudioUrl(full, true)) {
            return full;
        }

        String preferred = findUrlByPlaybackKeys(element, false, 0);
        if (isLikelyAudioUrl(preferred, true)) {
            return preferred;
        }

        // Last-resort compatibility path for response formats that renamed the
        // playback field. This accepts only URLs that look like audio, so album
        // covers and singer images are not mistaken for playback links.
        return findAnyAudioUrl(element, 0);
    }

    private static String findUrlByPlaybackKeys(JsonElement element, boolean fullOnly, int depth) {
        if (element == null || element.isJsonNull() || depth > 10) {
            return null;
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            String[] keys = { "play_url", "playUrl", "play_backup_url", "playBackupUrl", "backup_url", "backupUrl",
                "audio_url", "audioUrl", "cdn_url", "cdnUrl", "url", "urls" };
            for (String key : keys) {
                JsonElement value = getIgnoreCase(object, key);
                String candidate = findUrlValue(value, fullOnly, depth + 1);
                if (candidate != null) {
                    return candidate;
                }
            }
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                String candidate = findUrlByPlaybackKeys(entry.getValue(), fullOnly, depth + 1);
                if (candidate != null) {
                    return candidate;
                }
            }
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                String candidate = findUrlByPlaybackKeys(child, fullOnly, depth + 1);
                if (candidate != null) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static JsonElement getIgnoreCase(JsonObject object, String key) {
        if (object == null || key == null) {
            return null;
        }
        if (object.has(key)) {
            return object.get(key);
        }
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            if (key.equalsIgnoreCase(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static String findUrlValue(JsonElement element, boolean fullOnly, int depth) {
        if (element == null || element.isJsonNull() || depth > 10) {
            return null;
        }
        if (element.isJsonPrimitive()) {
            try {
                String value = element.getAsString();
                if (!isLikelyAudioUrl(value, true)) {
                    return null;
                }
                return !fullOnly || isClearlyFullAudioUrl(value) ? value : null;
            } catch (Exception ignored) {
                return null;
            }
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                String candidate = findUrlValue(child, fullOnly, depth + 1);
                if (candidate != null) {
                    return candidate;
                }
            }
            return null;
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            String[] nestedKeys = { "url", "play_url", "backup_url", "file", "src" };
            for (String key : nestedKeys) {
                String candidate = findUrlValue(getIgnoreCase(object, key), fullOnly, depth + 1);
                if (candidate != null) {
                    return candidate;
                }
            }
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                String candidate = findUrlValue(entry.getValue(), fullOnly, depth + 1);
                if (candidate != null) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static String findAnyAudioUrl(JsonElement element, int depth) {
        if (element == null || element.isJsonNull() || depth > 10) {
            return null;
        }
        if (element.isJsonPrimitive()) {
            try {
                String value = element.getAsString();
                return isLikelyAudioUrl(value, false) ? value : null;
            } catch (Exception ignored) {
                return null;
            }
        }
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject()
                .entrySet()) {
                String candidate = findAnyAudioUrl(entry.getValue(), depth + 1);
                if (candidate != null) {
                    return candidate;
                }
            }
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                String candidate = findAnyAudioUrl(child, depth + 1);
                if (candidate != null) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static boolean isTrial(JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return false;
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            String[] flags = { "is_free_part", "isFreePart", "is_trial", "isTrial", "is_trail", "trial" };
            for (String flag : flags) {
                JsonElement value = getIgnoreCase(object, flag);
                if (value == null || value.isJsonNull()) {
                    continue;
                }
                try {
                    String text = value.getAsString();
                    if ("1".equals(text) || "true".equalsIgnoreCase(text)) {
                        return true;
                    }
                } catch (Exception ignored) {}
            }
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                if (isTrial(entry.getValue())) {
                    return true;
                }
            }
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                if (isTrial(child)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isUsablePlayUrl(KugouSong song) {
        if (song == null || !isLikelyAudioUrl(song.playUrl, true)) {
            return false;
        }
        return isClearlyFullAudioUrl(song.playUrl) || (!song.trial && !isKnownTrialUrl(song.playUrl));
    }

    private static boolean isClearlyFullAudioUrl(String url) {
        if (!isLikelyAudioUrl(url, true)) {
            return false;
        }
        String value = normalizeUrl(url).toLowerCase(Locale.ROOT);
        return value.contains("/full/") || value.contains("/full_") || value.contains("_full.");
    }

    private static boolean isKnownTrialUrl(String url) {
        if (url == null || url.trim()
            .isEmpty()) {
            return false;
        }
        String value = normalizeUrl(url).toLowerCase(Locale.ROOT);
        return value.contains("/trial/") || value.contains("/clip/")
            || value.contains("/freepart/")
            || value.contains("/part/")
            || value.contains("isfreepart=1")
            || value.contains("is_free_part=1");
    }

    private static boolean isLikelyAudioUrl(String url, boolean fromPlaybackField) {
        if (url == null || url.trim()
            .isEmpty()) {
            return false;
        }
        String value = normalizeUrl(url);
        String lower = value.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return false;
        }

        try {
            URI uri = new URI(value);
            String host = uri.getHost();
            String path = uri.getPath();
            host = host == null ? "" : host.toLowerCase(Locale.ROOT);
            path = path == null ? "" : path.toLowerCase(Locale.ROOT);

            if (host.contains("kugouicon") || host.startsWith("img")
                || host.contains("image")
                || path.matches(".*\\.(?:jpg|jpeg|png|gif|webp|bmp)$")) {
                return false;
            }

            if (path.matches(".*\\.(?:mp3|flac|m4a|aac|ogg|wav|ape)(?:$|/).*")) {
                return true;
            }
            if (lower.contains("/full/") || lower.contains("/trial/") || lower.contains("/audio/")) {
                return true;
            }

            boolean knownWebPage = host.equals("www.kugou.com") || host.equals("m.kugou.com")
                || host.startsWith("songsearch.");
            return fromPlaybackField && !host.isEmpty() && !knownWebPage;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String normalizeUrl(String url) {
        if (url == null) {
            return null;
        }
        String value = url.trim()
            .replace("\\/", "/");
        if (value.startsWith("//")) {
            return "https:" + value;
        }
        return value;
    }

    private static KugouSong merge(KugouSong primary, KugouSong fallback) {
        if (primary == null) {
            return fallback;
        }
        if (fallback == null) {
            return primary;
        }
        primary.hash = choose(primary.hash, fallback.hash);
        primary.name = choose(primary.name, fallback.name);
        primary.singer = choose(primary.singer, fallback.singer);
        primary.album = choose(primary.album, fallback.album);
        primary.albumId = choose(primary.albumId, fallback.albumId);
        primary.albumAudioId = choose(primary.albumAudioId, fallback.albumAudioId);
        primary.encodedAlbumAudioId = choose(primary.encodedAlbumAudioId, fallback.encodedAlbumAudioId);
        primary.encodedAlbumId = choose(primary.encodedAlbumId, fallback.encodedAlbumId);
        primary.audioId = choose(primary.audioId, fallback.audioId);
        primary.pic = choose(primary.pic, fallback.pic);
        boolean primaryHasPlayUrl = primary.playUrl != null && !primary.playUrl.trim()
            .isEmpty();
        primary.playUrl = choose(primary.playUrl, fallback.playUrl);
        primary.lyricText = choose(primary.lyricText, fallback.lyricText);
        primary.trial = primaryHasPlayUrl ? primary.trial : fallback.trial;
        if (primary.payType < 0) {
            primary.payType = fallback.payType;
        }
        if (primary.price < 0) {
            primary.price = fallback.price;
        }
        if (primary.privilege < 0) {
            primary.privilege = fallback.privilege;
        }
        if (primary.failProcess < 0) {
            primary.failProcess = fallback.failProcess;
        }
        if (primary.durationMs <= 0) {
            primary.durationMs = fallback.durationMs;
        }
        return primary;
    }

    private static String choose(String first, String second) {
        return first != null && !first.trim()
            .isEmpty() ? first : second;
    }

    private static void cache(KugouSong song) {
        if (song == null || song.realId()
            .isEmpty()) {
            return;
        }
        String id = normalizeHash(song.realId());
        CACHE.merge(id, song, (oldValue, newValue) -> merge(newValue, oldValue));
    }

    private static String normalizeHash(String hash) {
        if (hash == null) {
            return "";
        }
        String value = hash.trim();
        if (!value.matches("(?i)[0-9a-f]{32}")) {
            return "";
        }
        return value.toUpperCase(Locale.ROOT);
    }

    private static int getInt(JsonObject object, String name, int defaultValue) {
        if (object == null || name == null
            || !object.has(name)
            || object.get(name)
                .isJsonNull()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(
                object.get(name)
                    .getAsString());
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    @SuppressWarnings("deprecation")
    private static JsonObject parseObj(String body) {
        String value = body == null ? "" : body.trim();
        int firstBrace = value.indexOf('{');
        int lastBrace = value.lastIndexOf('}');
        if (firstBrace > 0 && lastBrace > firstBrace) {
            value = value.substring(firstBrace, lastBrace + 1);
        }
        return new JsonParser().parse(value)
            .getAsJsonObject();
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

    private static final class CachedPlayUrl {

        private final String url;
        private final long expiresAt;

        private CachedPlayUrl(String url, long expiresAt) {
            this.url = url;
            this.expiresAt = expiresAt;
        }
    }

    private static final class LyricCandidate {

        private final String id;
        private final String accessKey;

        private LyricCandidate(String id, String accessKey) {
            this.id = id;
            this.accessKey = accessKey;
        }
    }
}
