package com.Lilith.FMusic.server.api.qqmusic;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;

import com.Lilith.FMusic.server.core.FMusic;
import com.Lilith.FMusic.server.core.music.MusicHttpClient;
import com.Lilith.FMusic.server.core.objs.CookieObj;
import com.Lilith.FMusic.server.core.objs.HttpResObj;
import com.google.gson.reflect.TypeToken;

public class QQMusicHttpClient {

    public static final String MUSICU_URL = "https://u.y.qq.com/cgi-bin/musicu.fcg";
    public static final String LYRIC_URL = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg";
    public static final String SEARCH_OLD_URL = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp";
    public static final String SMARTBOX_URL = "https://c.y.qq.com/splcloud/fcgi-bin/smartbox_new.fcg";

    private static final String REFERER = "https://y.qq.com/";
    private static final String ORIGIN = "https://y.qq.com";
    private static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        + "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36";

    public static HttpResObj get(String url) {
        return get(url, true);
    }

    public static HttpResObj getAnonymous(String url) {
        return get(url, false);
    }

    private static HttpResObj get(String url, boolean includeCookie) {
        try {
            HttpGet request = new HttpGet(url);
            setHeaders(request, includeCookie);
            log("<gray>QQ音乐GET: " + url);
            return execute(request, "QQ音乐GET请求失败：" + url);
        } catch (Exception e) {
            log("<red>QQ音乐GET请求失败：" + url);
            if (QQSong.debug) {
                e.printStackTrace();
            }
            return null;
        }
    }

    public static HttpResObj postJson(String json) {
        return postJson(MUSICU_URL, json);
    }

    public static HttpResObj postJson(String url, String json) {
        return postJson(url, json, true);
    }

    public static HttpResObj postJsonAnonymous(String url, String json) {
        return postJson(url, json, false);
    }

    private static HttpResObj postJson(String url, String json, boolean includeCookie) {
        try {
            HttpPost request = new HttpPost(url);
            setHeaders(request, includeCookie);
            request.setHeader("Content-Type", "application/json;charset=UTF-8");
            request.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));
            log("<gray>QQ音乐POST: " + url);
            log("<gray>QQ音乐POST Body: " + cut(json, 1200));
            return execute(request, "QQ音乐POST请求失败：" + url);
        } catch (Exception e) {
            log("<red>QQ音乐POST请求失败：" + url);
            if (QQSong.debug) {
                e.printStackTrace();
            }
            return null;
        }
    }

    private static void setHeaders(org.apache.hc.core5.http.HttpMessage request, boolean includeCookie) {
        request.setHeader("User-Agent", UA);
        request.setHeader("Referer", REFERER);
        request.setHeader("Origin", ORIGIN);
        request.setHeader("Accept", "application/json, text/plain, */*");
        request.setHeader("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
        request.setHeader("Connection", "keep-alive");

        String cookie = includeCookie ? buildCookieHeader() : "";
        if (!cookie.isEmpty()) {
            request.setHeader("Cookie", cookie);
            log("<gray>QQ音乐Cookie已注入，cookie：" + cookie);
        } else if (!includeCookie) {
            log("<gray>QQ音乐使用游客搜索请求，不注入Cookie");
        } else {
            log("<yellow>QQ音乐Cookie为空，将以未登录状态请求");
        }
    }

    public static boolean hasLoginCookie() {
        return hasCookieValue("qqmusic_key") || hasCookieValue("qm_keyst")
            || hasCookieValue("psrf_qqaccess_token")
            || hasCookieValue("psrf_qqrefresh_token")
            || hasCookieValue("wxrefresh_token");
    }

    private static boolean hasCookieValue(String name) {
        return !getCookieValue(name, "").isEmpty();
    }

    private static volatile List<CookieObj> ownCookie;
    private static volatile long ownCookieStamp = -1;

    /**
     * 独立 cookie 文件 (fmusic_server/QQMusic_cookie.json, 前缀命名区别于网易云 cookie.json)。
     * 不存在时自动新建空列表; 始终优先使用独立文件。
     */
    private static List<CookieObj> ownCookies() {
        try {
            File file = new File(FMusic.SERVER_DIR, "QQMusic_cookie.json");
            if (!file.isFile()) {
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
                try (InputStreamReader reader = new InputStreamReader(
                    new FileInputStream(file),
                    StandardCharsets.UTF_8)) {
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

    private static String buildCookieHeader() {
        try {
            List<CookieObj> cookies = ownCookies();
            if (cookies == null || cookies.isEmpty()) {
                return "";
            }

            StringBuilder builder = new StringBuilder();

            appendCookie(builder, "login_type", getCookieValue("login_type", ""));
            appendCookie(builder, "tmeLoginType", getCookieValue("tmeLoginType", ""));
            appendCookie(builder, "euin", getCookieValue("euin", ""));
            appendCookie(builder, "RK", getCookieValue("RK", ""));
            appendCookie(builder, "_qpsvr_localtk", getCookieValue("_qpsvr_localtk", ""));
            appendCookie(builder, "music_ignore_pskey", getCookieValue("music_ignore_pskey", ""));
            appendCookie(builder, "psrf_qqrefresh_token", getCookieValue("psrf_qqrefresh_token", ""));

            String uin = getCookieValue("uin", "");
            if (uin.isEmpty()) {
                uin = getCookieValue("media_p_uin", "");
            }
            appendCookie(builder, "uin", uin);

            appendCookie(builder, "pgv_pvid", getCookieValue("pgv_pvid", ""));
            appendCookie(builder, "pgv_info", getCookieValue("pgv_info", ""));
            appendCookie(builder, "fqm_sessionid", getCookieValue("fqm_sessionid", ""));
            appendCookie(builder, "fqm_pvqid", getCookieValue("fqm_pvqid", ""));
            appendCookie(builder, "psrf_access_token_expiresAt", getCookieValue("psrf_access_token_expiresAt", ""));
            appendCookie(builder, "psrf_musickey_createtime", getCookieValue("psrf_musickey_createtime", ""));
            appendCookie(builder, "psrf_qqaccess_token", getCookieValue("psrf_qqaccess_token", ""));
            appendCookie(builder, "psrf_qqopenid", getCookieValue("psrf_qqopenid", ""));
            appendCookie(builder, "psrf_qqunionid", getCookieValue("psrf_qqunionid", ""));
            appendCookie(builder, "ptcz", getCookieValue("ptcz", ""));
            appendCookie(builder, "qm_keyst", getCookieValue("qm_keyst", ""));
            appendCookie(builder, "qqmusic_key", getCookieValue("qqmusic_key", ""));
            appendCookie(builder, "ts_last", getCookieValue("ts_last", ""));
            appendCookie(builder, "ts_uid", getCookieValue("ts_uid", ""));
            appendCookie(builder, "wxunionid", getCookieValue("wxunionid", ""));
            appendCookie(builder, "wxrefresh_token", getCookieValue("wxrefresh_token", ""));
            appendCookie(builder, "wxopenid", getCookieValue("wxopenid", ""));

            return builder.toString();
        } catch (Exception e) {
            log("<red>QQ音乐Cookie读取失败");
            if (QQSong.debug) {
                e.printStackTrace();
            }
            return "";
        }
    }

    private static void appendCookie(StringBuilder builder, String name, String value) {
        if (name == null || name.isEmpty() || value == null || value.isEmpty()) {
            return;
        }

        if (builder.length() > 0) {
            builder.append("; ");
        }

        builder.append(name)
            .append("=")
            .append(value);
    }

    public static String getCookieValue(String name, String def) {
        try {
            List<CookieObj> cookies = ownCookies();
            if (name == null || name.isEmpty() || cookies == null) {
                return def;
            }
            for (CookieObj cookie : cookies) {
                if (cookie != null && name.equals(cookie.name)) {
                    return cookie.value == null || cookie.value.isEmpty() ? def : cookie.value;
                }
            }
        } catch (Exception ignored) {}
        return def;
    }

    public static String getUin() {
        String uin = getCookieValue("uin", "");
        if (!uin.isEmpty()) {
            return uin;
        }
        uin = getCookieValue("media_p_uin", "");
        return uin.isEmpty() ? "0" : uin;
    }

    private static HttpResObj execute(org.apache.hc.client5.http.classic.methods.HttpUriRequestBase request,
        String errorMsg) {
        try (CloseableHttpResponse response = MusicHttpClient.client.execute(request)) {
            int httpCode = response.getCode();
            HttpEntity entity = response.getEntity();

            if (entity == null) {
                log("<red>QQ音乐返回空实体，HTTP=" + httpCode);
                return null;
            }

            String body = read(entity.getContent());
            EntityUtils.consume(entity);

            boolean ok = httpCode >= 200 && httpCode < 300;
            log("<gray>QQ音乐HTTP=" + httpCode + " 返回：" + cut(body, 1200));

            if (!ok) {
                log("<red>QQ音乐服务器返回错误：" + cut(body, 1200));
            }

            return new HttpResObj(body, ok);
        } catch (Exception e) {
            log("<red>" + errorMsg);
            if (QQSong.debug) {
                e.printStackTrace();
            }
            return null;
        }
    }

    private static String read(InputStream inputStream) throws Exception {
        if (inputStream == null) {
            return "";
        }
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int length;
        while ((length = inputStream.read(buffer)) != -1) {
            result.write(buffer, 0, length);
        }
        inputStream.close();
        return result.toString(StandardCharsets.UTF_8.toString());
    }

    public static String enc(String value) throws Exception {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8.toString());
    }

    public static String cut(String value, int max) {
        if (value == null) {
            return "null";
        }
        return value.length() > max ? value.substring(0, max) : value;
    }

    public static void log(String msg) {
        if (!QQSong.debug) {
            return;
        }
        FMusic.log.data("<light_purple>[AllMusic3]" + msg);
    }
}
