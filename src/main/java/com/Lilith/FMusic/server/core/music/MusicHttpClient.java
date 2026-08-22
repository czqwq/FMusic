package com.Lilith.FMusic.server.core.music;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.Cookie;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.cookie.BasicClientCookie;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;

import com.Lilith.FMusic.server.core.FMusic;
import com.Lilith.FMusic.server.core.objs.CookieObj;

public class MusicHttpClient {

    public static final String UserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36 Edg/145.0.0.0";
    private static final int CONNECT_TIMEOUT = 5;
    private static final int READ_TIMEOUT = 7;
    public static CloseableHttpClient client;

    public static void init() {
        try {
            RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(CONNECT_TIMEOUT))
                .setResponseTimeout(Timeout.ofSeconds(READ_TIMEOUT))
                .build();
            client = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .build();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static CookieStore createCookieStore() {
        BasicCookieStore cookieStore = new BasicCookieStore();
        for (CookieObj cookie : FMusic.cookie) {
            BasicClientCookie cookie1 = new BasicClientCookie(cookie.name, cookie.value);
            cookie1.setExpiryDate(Instant.MAX);
            cookie1.setDomain(cookie.domain);
            cookie1.setPath(cookie.path);
            cookie1.setHttpOnly(cookie.hostOnly);
            cookie1.setHttpOnly(cookie.httpOnly);
            cookieStore.addCookie(cookie1);
        }
        return cookieStore;
    }

    public static void saveCookies(CookieStore cookieStore) {
        List<Cookie> cookies = cookieStore.getCookies();
        for (Cookie cookie : cookies) {
            CookieObj obj = new CookieObj();
            obj.domain = cookie.getDomain();
            obj.hostOnly = cookie.isHttpOnly();
            obj.httpOnly = cookie.isHttpOnly();
            obj.name = cookie.getName();
            obj.value = cookie.getValue();
        }
        FMusic.saveCookie();
    }

    public static InputStream get(String path) {
        try {
            HttpGet request = new HttpGet(path);
            request.setHeader("user-agent", UserAgent);
            HttpClientContext context = HttpClientContext.create();
            CookieStore cookieStore = createCookieStore();
            context.setCookieStore(cookieStore);
            CloseableHttpResponse response = client.execute(request, context);
            int httpCode = response.getCode();
            HttpEntity entity = response.getEntity();
            if (entity == null) {
                FMusic.log.data("<gold>[FMusic]<red>获取网页错误");
                return null;
            }
            InputStream inputStream = entity.getContent();
            boolean ok = httpCode == 200;
            if (!ok) {
                EntityUtils.consume(entity);
                inputStream.close();
                response.close();
                return null;
            }
            // 保存 cookies
            saveCookies(cookieStore);
            // 注意：需要调用者关闭 InputStream
            return inputStream;
        } catch (Exception e) {
            FMusic.log.data("<gold>[FMusic]<red>获取网页错误");
            e.printStackTrace();
        }
        return null;
    }
}
