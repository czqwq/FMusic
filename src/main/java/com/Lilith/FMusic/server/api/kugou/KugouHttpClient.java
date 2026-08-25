package com.Lilith.FMusic.server.api.kugou;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpMessage;
import org.apache.hc.core5.http.io.entity.EntityUtils;

import com.Lilith.FMusic.server.core.FMusic;
import com.Lilith.FMusic.server.core.music.MusicHttpClient;
import com.Lilith.FMusic.server.core.objs.CookieObj;
import com.Lilith.FMusic.server.core.objs.HttpResObj;
import com.google.gson.reflect.TypeToken;

public final class KugouHttpClient {

    public static final String WEB_SEARCH_URL = "https://songsearch.kugou.com/song_search_v2";
    public static final String WEB_COMPLEX_SEARCH_URL = "https://complexsearch.kugou.com/v2/search/song";
    public static final String WEB_SONGINFO_URL = "https://wwwapi.kugou.com/play/songinfo";
    public static final String WEB_DETAIL_URL = "https://wwwapi.kugou.com/yy/index.php";
    public static final String WEB_DETAIL_ALT_URL = "https://www.kugou.com/yy/index.php";
    public static final String ANDROID_PLAY_URL = "https://gateway.kugou.com/v5/url";
    public static final String LYRIC_SEARCH_OLD_URL = "https://lyrics.kugou.com/search";
    public static final String LYRIC_DOWNLOAD_URL = "https://lyrics.kugou.com/download";

    private static final String WEB_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        + "AppleWebKit/537.36 (KHTML, like Gecko) "
        + "Chrome/150.0.0.0 Safari/537.36";
    private static final String ANDROID_UA = "Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi";

    private static volatile String lastCookieSummary = "";
    private static final int SMALL_RESPONSE_LOG_LIMIT = 16_384;

    private static final String[] PROTECTED_PARAMETER_NAMES = { "token", "t", "key", "signature", "mid", "uuid", "dfid",
        "userid", "user_id", "kugooid", "kugoopwd", "cookie" };

    private static final String COOKIE_OVERRIDE = "";
    private static final String COOKIE_PROPERTY = "allmusic.kugou.cookie";
    private static final String COOKIE_ENV = "ALLMUSIC_KUGOU_COOKIE";

    private static final String[] COOKIE_HEADER_PRIORITY = { "kg_mid", "HMACCOUNT", "kg_dfid", "kg_dfid_collect",
        "KugooID", "userid", "user_id", "kg_userid", "t", "token", "a_id", "UserName", "kg_h_uid", "mid", "dfid",
        "KuGoo", "kg_mid_temp", "vip_token", "vip_type" };

    private KugouHttpClient() {}

    public static HttpResObj getWeb(String baseUrl, Map<String, String> params) {
        try {
            String url = appendQuery(baseUrl, params);
            HttpGet request = new HttpGet(url);
            setWebHeaders(request, params);
            logRequestParameters("酷狗Web", baseUrl, params);
            return execute(request, "酷狗音乐GET请求失败：" + baseUrl);
        } catch (Exception e) {
            log("<red>酷狗音乐GET请求创建失败：" + baseUrl);
            if (KugouSong.debug) {
                e.printStackTrace();
            }
            return null;
        }
    }

    public static HttpResObj getWebSongInfo(Map<String, String> params) {
        try {
            String url = appendQuery(WEB_SONGINFO_URL, params);
            logRequestParameters("酷狗Web会员", WEB_SONGINFO_URL, params);
            return executeWebSongInfo(url);
        } catch (Exception e) {
            log("<red>酷狗Web会员播放请求创建失败");
            if (KugouSong.debug) {
                e.printStackTrace();
            }
            return null;
        }
    }

    public static HttpResObj getAndroidPlay(Map<String, String> params) {
        try {
            String url = appendQuery(ANDROID_PLAY_URL, params);
            logRequestParameters("酷狗Android", ANDROID_PLAY_URL, params);
            return executeAndroid(url, params);
        } catch (Exception e) {
            log("<red>酷狗Android播放请求创建失败");
            if (KugouSong.debug) {
                e.printStackTrace();
            }
            return null;
        }
    }

    public static String getMid() {
        String value = firstCookie("mid", "kg_mid");
        return value.isEmpty() ? "-" : value;
    }

    public static String getDfid() {
        String value = firstCookie("dfid", "kg_dfid");
        return value.isEmpty() ? "-" : value;
    }

    public static String getUserId(String def) {
        String value = firstCookie("userid", "user_id", "kg_userid", "KugooID");
        return value.matches("[0-9]+") ? value : def;
    }

    public static String getCookieValue(String name, String def) {
        try {
            if (name == null || name.trim()
                .isEmpty()) {
                return def;
            }

            String override = rawCookieOverride();
            if (!override.isEmpty()) {
                String value = cookieValueFromHeader(override, name);
                return value.isEmpty() ? def : value;
            }

            CookieObj cookie = kugouCookies().get(name.toLowerCase(Locale.ROOT));
            return cookie == null || cookie.value == null || cookie.value.isEmpty() ? def : cookie.value;
        } catch (Exception ignored) {
            return def;
        }
    }

    public static String enc(String value) throws Exception {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8.toString())
            .replace("+", "%20");
    }

    public static String cut(String value, int max) {
        if (value == null) {
            return "null";
        }
        return value.length() > max ? value.substring(0, max) : value;
    }

    public static void log(String message) {
        if (!KugouSong.debug) {
            return;
        }
        FMusic.log.data("<light_purple>[AllMusic3]" + message);
    }

    private static HttpResObj executeWebSongInfo(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(20_000);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(true);

            connection.setRequestProperty("User-Agent", WEB_UA);
            connection.setRequestProperty("Accept", "*/*");
            connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
            connection.setRequestProperty("Cache-Control", "no-cache");
            connection.setRequestProperty("Pragma", "no-cache");
            connection.setRequestProperty("Origin", "https://www.kugou.com");
            connection.setRequestProperty("Referer", "https://www.kugou.com/");
            connection.setRequestProperty("Sec-Fetch-Dest", "empty");
            connection.setRequestProperty("Sec-Fetch-Mode", "cors");
            connection.setRequestProperty("Sec-Fetch-Site", "same-site");

            log(
                "<gray>酷狗Web会员请求头：User-Agent=" + WEB_UA
                    + "; Accept=*/*; Accept-Language=zh-CN,zh;q=0.9"
                    + "; Origin=https://www.kugou.com"
                    + "; Referer=https://www.kugou.com/"
                    + "; Sec-Fetch-Dest=empty; Sec-Fetch-Mode=cors"
                    + "; Sec-Fetch-Site=same-site");

            int httpCode = connection.getResponseCode();
            InputStream stream = httpCode >= 200 && httpCode < 400 ? connection.getInputStream()
                : connection.getErrorStream();
            String body = read(stream);
            boolean ok = httpCode >= 200 && httpCode < 300;
            log(
                (ok ? "<gray>" : "<red>") + "酷狗Web会员 HTTP="
                    + httpCode
                    + "，响应长度="
                    + body.length()
                    + "，响应SHA-256="
                    + sha256Prefix(body));
            logResponseBody("酷狗Web会员", body);
            return new HttpResObj(body, ok);
        } catch (Exception e) {
            log("<red>酷狗Web会员播放请求失败");
            if (KugouSong.debug) {
                e.printStackTrace();
            }
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static HttpResObj executeAndroid(String url, Map<String, String> params) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(20_000);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(true);

            // 与 kugou_android_free_test_fixed.ps1 完全一致。不要手动设置
            // Connection: keep-alive/close，也不要给 Android 路由附加 Web Header。
            connection.setRequestProperty("User-Agent", ANDROID_UA);
            connection.setRequestProperty("Accept", "*/*");
            connection.setRequestProperty("x-router", "trackercdn.kugou.com");
            connection.setRequestProperty("dfid", value(params, "dfid", "-"));
            connection.setRequestProperty("mid", value(params, "mid", "-"));
            connection.setRequestProperty("clienttime", value(params, "clienttime", "0"));
            connection.setRequestProperty("kg-rc", "1");
            connection.setRequestProperty("kg-thash", "5d816a0");
            connection.setRequestProperty("kg-rec", "1");
            connection.setRequestProperty("kg-rf", "B9EDA08A64250DEFFBCADDEE00F8F25F");

            log(
                "<gray>酷狗Android请求头：User-Agent=" + ANDROID_UA
                    + "; Accept=*/*; x-router=trackercdn.kugou.com"
                    + "; dfid="
                    + protectedValueForLog(value(params, "dfid", "-"))
                    + "; mid="
                    + protectedValueForLog(value(params, "mid", "-"))
                    + "; clienttime="
                    + value(params, "clienttime", "0")
                    + "; kg-rc=1; kg-thash=5d816a0; kg-rec=1"
                    + "; kg-rf=B9EDA08A64250DEFFBCADDEE00F8F25F");

            int httpCode = connection.getResponseCode();
            InputStream stream = httpCode >= 200 && httpCode < 400 ? connection.getInputStream()
                : connection.getErrorStream();
            String body = read(stream);
            boolean ok = httpCode >= 200 && httpCode < 300;
            log(
                (ok ? "<gray>" : "<red>") + "酷狗Android HTTP="
                    + httpCode
                    + "，响应长度="
                    + body.length()
                    + "，响应SHA-256="
                    + sha256Prefix(body));
            logResponseBody("酷狗Android", body);
            return new HttpResObj(body, ok);
        } catch (Exception e) {
            log("<red>酷狗Android播放请求失败");
            if (KugouSong.debug) {
                e.printStackTrace();
            }
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static void setWebHeaders(HttpMessage request, Map<String, String> params) throws Exception {
        request.setHeader("User-Agent", WEB_UA);
        request.setHeader("Accept", "application/json, text/plain, */*");
        request.setHeader("Accept-Language", "zh-CN,zh;q=0.9");
        request.setHeader("Origin", "https://www.kugou.com");
        request.setHeader("Cache-Control", "no-cache");
        request.setHeader("Pragma", "no-cache");
        request.setHeader("Sec-Fetch-Dest", "empty");
        request.setHeader("Sec-Fetch-Mode", "cors");
        request.setHeader("Sec-Fetch-Site", "same-site");

        String hash = value(params, "hash", "");
        if (hash.matches("(?i)[0-9a-f]{32}")) {
            String albumId = value(params, "album_id", "0");
            String albumAudioId = value(params, "album_audio_id", "0");
            StringBuilder referer = new StringBuilder("https://www.kugou.com/song/#hash=").append(enc(hash))
                .append("&album_id=")
                .append(enc(albumId));
            if (!"0".equals(albumAudioId) && !albumAudioId.isEmpty()) {
                referer.append("&album_audio_id=")
                    .append(enc(albumAudioId));
            }
            request.setHeader("Referer", referer.toString());
        } else {
            request.setHeader("Referer", "https://www.kugou.com/");
        }

        String cookie = buildCookieHeader();
        if (!cookie.isEmpty()) {
            request.setHeader("Cookie", cookie);
            String summary = cookieDiagnosticsForLog(cookie);
            if (!summary.equals(lastCookieSummary)) {
                lastCookieSummary = summary;
                log("<gray>酷狗音乐Cookie已加载：" + summary);
            }
        } else {
            log("<yellow>酷狗音乐Cookie Header为空");
        }
    }

    /**
     * 将 AllMusic cookie.json 反序列化后的 CookieObj 列表转换成标准 Cookie Header。
     */
    static String buildCookieHeader() {
        String override = rawCookieOverride();
        if (!override.isEmpty()) {
            return normalizeCookieHeader(override);
        }

        Map<String, CookieObj> cookies = kugouCookies();
        if (cookies.isEmpty()) {
            return "";
        }

        List<String> parts = new ArrayList<>();
        Map<String, Boolean> added = new LinkedHashMap<>();

        appendCookie(parts, added, cookies, "kg_mid");
        appendCookiesWithPrefix(parts, added, cookies, "hm_lvt_");
        for (String preferredName : COOKIE_HEADER_PRIORITY) {
            appendCookie(parts, added, cookies, preferredName);
        }
        appendCookiesWithPrefix(parts, added, cookies, "hm_lpvt_");
        return String.join("; ", parts);
    }

    private static String rawCookieOverride() {
        String value = System.getProperty(COOKIE_PROPERTY, "");
        if (value == null || value.trim()
            .isEmpty()) {
            value = System.getenv(COOKIE_ENV);
        }
        if (value == null || value.trim()
            .isEmpty()) {
            value = COOKIE_OVERRIDE;
        }
        return normalizeCookieHeader(value);
    }

    private static String normalizeCookieHeader(String raw) {
        if (raw == null) {
            return "";
        }
        String value = raw.trim();
        if (value.regionMatches(true, 0, "Cookie:", 0, 7)) {
            value = value.substring(7)
                .trim();
        }
        if (value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1)
                .trim();
            // 兼容误把 List.toString() 结果粘贴进来的情况。仅在完全
            // 没有分号时，才把逗号视为 Cookie 分隔符。
            if (value.indexOf(';') < 0) {
                value = value.replaceAll(",\\s*(?=[^,;=\\s]+\\s*=)", "; ");
            }
        }
        if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0) {
            value = value.replace("\r", "")
                .replace("\n", "")
                .trim();
        }

        List<String> entries = new ArrayList<>();
        for (String item : value.split(";\\s*")) {
            String entry = item == null ? "" : item.trim();
            int index = entry.indexOf('=');
            if (index <= 0) {
                continue;
            }
            String name = entry.substring(0, index)
                .trim();
            String cookieValue = entry.substring(index + 1);
            if (!name.isEmpty() && name.indexOf(' ') < 0
                && name.indexOf('\r') < 0
                && name.indexOf('\n') < 0
                && cookieValue.indexOf('\r') < 0
                && cookieValue.indexOf('\n') < 0) {
                entries.add(name + '=' + cookieValue);
            }
        }
        return String.join("; ", entries);
    }

    private static String cookieValueFromHeader(String header, String wantedName) {
        if (header == null || wantedName == null) {
            return "";
        }
        for (String item : normalizeCookieHeader(header).split(";\\s*")) {
            int index = item.indexOf('=');
            if (index <= 0) {
                continue;
            }
            String name = item.substring(0, index)
                .trim();
            if (wantedName.equalsIgnoreCase(name)) {
                return item.substring(index + 1);
            }
        }
        return "";
    }

    private static volatile List<CookieObj> ownCookie;
    private static volatile long ownCookieStamp = -1;

    /**
     * 独立 cookie 文件 (fmusic_server/Kugou_cookie.json, 前缀命名区别于网易云 cookie.json)。
     * 不存在时自动新建空列表; 始终优先使用独立文件 (带修改时间缓存)。
     */
    private static List<CookieObj> ownCookies() {
        try {
            File file = new File(FMusic.SERVER_DIR, "Kugou_cookie.json");
            if (!file.isFile()) {
                // 独立 cookie 文件不存在: 自动新建空列表 (始终优先使用独立文件)
                File parent = file.getParentFile();
                if (parent != null && !parent.isDirectory() && !parent.mkdirs() && !parent.isDirectory()) {
                    return new ArrayList<>();
                }
                try (FileOutputStream out = new FileOutputStream(file)) {
                    out.write("[]".getBytes(StandardCharsets.UTF_8));
                }
                ownCookie = new ArrayList<>();
                ownCookieStamp = file.lastModified();
                return ownCookie;
            }
            long stamp = file.lastModified();
            if (ownCookie == null || stamp != ownCookieStamp) {
                try (InputStreamReader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
                    Type type = new TypeToken<ArrayList<CookieObj>>() {}.getType();
                    ownCookie = FMusic.gson.fromJson(reader, type);
                    ownCookieStamp = stamp;
                }
            }
            return ownCookie;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * /music reload 时清空独立 cookie 文件缓存, 强制下次请求重新读取
     */
    public static void clearCookieCache() {
        ownCookie = null;
        ownCookieStamp = -1;
    }

    private static Map<String, CookieObj> kugouCookies() {
        Map<String, CookieObj> result = new LinkedHashMap<>();
        try {
            List<CookieObj> cookies = ownCookies();
            if (cookies == null) {
                return result;
            }
            for (CookieObj cookie : cookies) {
                if (!isUsableKugouCookie(cookie)) {
                    continue;
                }
                result.put(cookie.name.toLowerCase(Locale.ROOT), cookie);
            }
        } catch (Exception ignored) {}
        return result;
    }

    private static boolean isUsableKugouCookie(CookieObj cookie) {
        if (cookie == null || cookie.name == null || cookie.value == null) {
            return false;
        }

        String name = cookie.name.trim();
        String value = cookie.value;
        if (name.isEmpty() || value.isEmpty()) {
            return false;
        }

        // 防止损坏的 JSON 值形成额外 HTTP Header。
        if (name.indexOf('\r') >= 0 || name.indexOf('\n') >= 0
            || name.indexOf(';') >= 0
            || name.indexOf('=') >= 0
            || value.indexOf('\r') >= 0
            || value.indexOf('\n') >= 0) {
            return false;
        }

        String domain = cookie.domain == null ? ""
            : cookie.domain.trim()
                .toLowerCase(Locale.ROOT);
        return domain.isEmpty() || "kugou.com".equals(domain) || domain.endsWith(".kugou.com");
    }

    private static void appendCookiesWithPrefix(List<String> parts, Map<String, Boolean> added,
        Map<String, CookieObj> cookies, String prefix) {
        String lowerPrefix = prefix.toLowerCase(Locale.ROOT);
        for (CookieObj cookie : cookies.values()) {
            if (cookie != null && cookie.name != null
                && cookie.name.toLowerCase(Locale.ROOT)
                    .startsWith(lowerPrefix)) {
                appendCookie(parts, added, cookies, cookie.name);
            }
        }
    }

    private static void appendCookie(List<String> parts, Map<String, Boolean> added, Map<String, CookieObj> cookies,
        String name) {
        String key = name.toLowerCase(Locale.ROOT);
        if (added.containsKey(key)) {
            return;
        }

        CookieObj cookie = cookies.get(key);
        if (cookie == null || cookie.name == null || cookie.value == null || cookie.value.isEmpty()) {
            return;
        }

        parts.add(cookie.name + '=' + cookie.value);
        added.put(key, Boolean.TRUE);
    }

    private static String cookieDiagnosticsForLog(String cookieHeader) {
        if (cookieHeader == null || cookieHeader.trim()
            .isEmpty()) {
            return "来源=" + cookieSourceForLog() + "，0项";
        }
        String[] entries = cookieHeader.split(";\\s*");
        List<String> fields = new ArrayList<>();
        for (String entry : entries) {
            int index = entry.indexOf('=');
            String name = index > 0 ? entry.substring(0, index)
                .trim() : "";
            String cookieValue = index > 0 ? entry.substring(index + 1) : "";
            if (!name.isEmpty()) {
                fields.add(name + protectedValueForLog(cookieValue));
            }
        }
        return "来源=" + cookieSourceForLog()
            + "，Header长度="
            + cookieHeader.length()
            + "，字段数="
            + fields.size()
            + " "
            + fields;
    }

    private static String cookieSourceForLog() {
        try {
            String property = System.getProperty(COOKIE_PROPERTY, "");
            if (property != null && !property.trim()
                .isEmpty()) {
                return "JVM参数-" + COOKIE_PROPERTY;
            }
        } catch (Exception ignored) {}
        try {
            String env = System.getenv(COOKIE_ENV);
            if (env != null && !env.trim()
                .isEmpty()) {
                return "环境变量-" + COOKIE_ENV;
            }
        } catch (Exception ignored) {}
        if (COOKIE_OVERRIDE != null && !COOKIE_OVERRIDE.trim()
            .isEmpty()) {
            return "源码COOKIE_OVERRIDE";
        }
        return "Kugou_cookie.json";
    }

    public static String protectedValueForLog(String value) {
        String safe = value == null ? "" : value;
        if (safe.isEmpty()) {
            return "{empty}";
        }
        return "{protected,length=" + safe.length() + ",sha256=" + sha256Prefix(safe) + "}";
    }

    private static boolean isProtectedParameter(String name) {
        if (name == null) {
            return false;
        }
        for (String protectedName : PROTECTED_PARAMETER_NAMES) {
            if (protectedName.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private static void logRequestParameters(String label, String baseUrl, Map<String, String> params) {
        StringBuilder normal = new StringBuilder(baseUrl);
        StringBuilder protectedValues = new StringBuilder();
        normal.append("?");
        boolean firstNormal = true;
        boolean firstProtected = true;
        if (params != null) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                String key = entry.getKey();
                String parameterValue = entry.getValue() == null ? "" : entry.getValue();
                if (isProtectedParameter(key)) {
                    if (!firstProtected) {
                        protectedValues.append("; ");
                    }
                    firstProtected = false;
                    protectedValues.append(key)
                        .append('=')
                        .append(protectedValueForLog(parameterValue));
                } else {
                    if (!firstNormal) {
                        normal.append('&');
                    }
                    firstNormal = false;
                    normal.append(key)
                        .append('=')
                        .append(parameterValue);
                }
            }
        }
        log("<gray>" + label + "请求地址与非凭据参数：" + normal);
        if (protectedValues.length() > 0) {
            log("<gray>" + label + "凭据参数指纹：" + protectedValues);
        }
    }

    private static void logResponseBody(String label, String body) {
        String value = body == null ? "" : body;
        if (value.length() <= SMALL_RESPONSE_LOG_LIMIT && !containsTemporaryUrl(value)) {
            log("<gray>" + label + "完整响应：" + value);
            return;
        }
        if (containsTemporaryUrl(value)) {
            log(
                "<gray>" + label
                    + "响应包含临时签名播放地址；为避免服务器日志成为可转发凭据，"
                    + "不打印地址正文。响应长度="
                    + value.length()
                    + "，SHA-256="
                    + sha256Prefix(value));
            return;
        }
        log(
            "<gray>" + label
                + "响应过长，长度="
                + value.length()
                + "，SHA-256="
                + sha256Prefix(value)
                + "，前"
                + SMALL_RESPONSE_LOG_LIMIT
                + "字符="
                + value.substring(0, SMALL_RESPONSE_LOG_LIMIT));
    }

    private static boolean containsTemporaryUrl(String value) {
        if (value == null) {
            return false;
        }
        String lower = value.toLowerCase(Locale.ROOT);
        return (lower.contains("\"play_url\"") || lower.contains("\"play_backup_url\"")
            || lower.contains("\"backup_url\"")) && lower.contains("http");
    }

    private static String sha256Prefix(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                builder.append(String.format("%02x", item & 0xff));
            }
            return builder.substring(0, 16);
        } catch (Exception e) {
            return "unavailable";
        }
    }

    public static String redactTextForLog(String value) {
        if (value == null || value.isEmpty()) {
            return String.valueOf(value);
        }
        if (containsTemporaryUrl(value) || containsCredentialField(value)) {
            return "{protected-response,length=" + value.length() + ",sha256=" + sha256Prefix(value) + "}";
        }
        return value;
    }

    private static boolean containsCredentialField(String value) {
        String lower = value == null ? "" : value.toLowerCase(Locale.ROOT);
        return lower.contains("\"token\"") || lower.contains("\"signature\"")
            || lower.contains("\"kugoopwd\"")
            || lower.contains("\"cookie\"");
    }

    private static String firstCookie(String... names) {
        for (String name : names) {
            String value = getCookieValue(name, "");
            if (!value.isEmpty()) {
                return value;
            }
        }
        return "";
    }

    private static String appendQuery(String baseUrl, Map<String, String> params) throws Exception {
        if (params == null || params.isEmpty()) {
            return baseUrl;
        }

        StringBuilder builder = new StringBuilder(baseUrl);
        builder.append(baseUrl.contains("?") ? '&' : '?');
        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) {
                builder.append('&');
            }
            first = false;
            builder.append(enc(entry.getKey()))
                .append('=')
                .append(enc(entry.getValue()));
        }
        return builder.toString();
    }

    private static HttpResObj execute(org.apache.hc.client5.http.classic.methods.HttpUriRequestBase request,
        String errorMessage) {
        try (CloseableHttpResponse response = MusicHttpClient.client.execute(request)) {
            int httpCode = response.getCode();
            HttpEntity entity = response.getEntity();
            if (entity == null) {
                log("<red>酷狗音乐返回空实体，HTTP=" + httpCode);
                return null;
            }

            String body = read(entity.getContent());
            EntityUtils.consume(entity);
            boolean ok = httpCode >= 200 && httpCode < 300;
            log(
                (ok ? "<gray>" : "<red>") + "酷狗音乐HTTP="
                    + httpCode
                    + "，响应长度="
                    + body.length()
                    + "，响应SHA-256="
                    + sha256Prefix(body));
            String requestUri = String.valueOf(request.getUri());
            if (isPlaybackRequest(requestUri) || !ok) {
                logResponseBody("酷狗Web", body);
            }
            return new HttpResObj(body, ok);
        } catch (Exception e) {
            log("<red>" + errorMessage);
            if (KugouSong.debug) {
                e.printStackTrace();
            }
            return null;
        }
    }

    private static boolean isPlaybackRequest(String requestUri) {
        if (requestUri == null) {
            return false;
        }
        String lower = requestUri.toLowerCase(Locale.ROOT);
        return lower.contains("play%2fgetdata") || lower.contains("play/getdata")
            || lower.contains("/v5/url")
            || lower.contains("/play/songinfo");
    }

    private static String read(InputStream inputStream) throws Exception {
        if (inputStream == null) {
            return "";
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            output.write(buffer, 0, length);
        }
        inputStream.close();
        return output.toString(StandardCharsets.UTF_8.toString());
    }

    private static String value(Map<String, String> params, String key, String def) {
        String value = params == null ? null : params.get(key);
        return value == null || value.isEmpty() ? def : value;
    }
}
