package com.Lilith.FMusic.server.bili.bilibili;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.Lilith.FMusic.server.bili.http.HttpResult;
import com.Lilith.FMusic.server.bili.http.SimpleHttpClient;
import com.Lilith.FMusic.server.bili.json.Json;
import com.Lilith.FMusic.server.bili.json.JsonValue;

public final class BilibiliApiClient {

    private static final String ROOM_INIT = "https://api.live.bilibili.com/room/v1/Room/room_init";
    private static final String DANMU_INFO = "https://api.live.bilibili.com/xlive/web-room/v1/index/getDanmuInfo";
    private static final String NAV = "https://api.bilibili.com/x/web-interface/nav";
    private static final String SPI = "https://api.bilibili.com/x/frontend/finger/spi";

    private final SimpleHttpClient http;
    private final CookieStore cookies;
    private volatile String cachedMixinKey = "";
    private final AtomicLong mixinExpiresAt = new AtomicLong();

    public BilibiliApiClient(SimpleHttpClient http, CookieStore cookies) {
        this.http = http;
        this.cookies = cookies;
    }

    public RoomInfo resolveRoom(long roomId) throws IOException {
        JsonValue root = getJson(ROOM_INIT + "?id=" + roomId);
        requireCodeZero(root, "room_init");
        JsonValue data = root.get("data");
        long realRoom = data.get("room_id")
            .asLong(0L);
        if (realRoom <= 0L) {
            throw new IOException("room_init returned an invalid room_id");
        }
        return new RoomInfo(
            roomId,
            realRoom,
            data.get("live_status")
                .asInt(-1));
    }

    public void ensureDeviceIds() throws IOException {
        if (!cookies.get("buvid3")
            .isEmpty()) {
            return;
        }
        JsonValue root = getJson(SPI);
        requireCodeZero(root, "finger/spi");
        JsonValue data = root.get("data");
        String buvid3 = data.get("b_3")
            .asString("")
            .trim();
        String buvid4 = data.get("b_4")
            .asString("")
            .trim();
        if (buvid3.isEmpty()) {
            throw new IOException("finger/spi returned an empty buvid3");
        }
        cookies.saveDeviceIds(buvid3, buvid4);
    }

    public DanmuInfo getDanmuInfo(long realRoomId) throws IOException {
        IOException first = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                String mixinKey = mixinKey(attempt > 0);
                Map<String, String> params = new LinkedHashMap<String, String>();
                params.put("id", String.valueOf(realRoomId));
                params.put("type", "0");
                params.put("web_location", "444.8");
                Map<String, String> signed = WbiSigner.sign(params, mixinKey, System.currentTimeMillis() / 1000L);
                String url = DANMU_INFO + '?' + WbiSigner.queryString(signed, false);
                JsonValue root = getJson(url);
                requireCodeZero(root, "getDanmuInfo");
                JsonValue data = root.get("data");
                String token = data.get("token")
                    .asString("")
                    .trim();
                if (token.isEmpty()) {
                    throw new IOException("getDanmuInfo returned an empty token");
                }
                List<DanmuServer> servers = new ArrayList<DanmuServer>();
                JsonValue hostList = data.get("host_list");
                for (JsonValue item : hostList.asArray()) {
                    String host = item.get("host")
                        .asString("")
                        .trim();
                    int port = item.get("wss_port")
                        .asInt(443);
                    if (isSafeHost(host) && port >= 1 && port <= 65535) {
                        servers.add(new DanmuServer(host, port));
                    }
                }
                if (servers.isEmpty()) {
                    throw new IOException("getDanmuInfo returned no usable WebSocket server");
                }
                return new DanmuInfo(token, servers);
            } catch (IOException e) {
                if (first == null) first = e;
                cachedMixinKey = "";
                mixinExpiresAt.set(0L);
            }
        }
        throw first == null ? new IOException("Unable to get danmaku server information") : first;
    }

    private String mixinKey(boolean forceRefresh) throws IOException {
        long now = System.currentTimeMillis();
        String current = cachedMixinKey;
        if (!forceRefresh && !current.isEmpty() && now < mixinExpiresAt.get()) {
            return current;
        }
        synchronized (this) {
            now = System.currentTimeMillis();
            current = cachedMixinKey;
            if (!forceRefresh && !current.isEmpty() && now < mixinExpiresAt.get()) {
                return current;
            }
            JsonValue root = getJson(NAV);
            JsonValue wbi = root.get("data")
                .get("wbi_img");
            String img = wbi.get("img_url")
                .asString("");
            String sub = wbi.get("sub_url")
                .asString("");
            try {
                current = WbiSigner.mixinKey(img, sub);
            } catch (IllegalArgumentException e) {
                throw new IOException("Unable to derive WBI mixin key", e);
            }
            cachedMixinKey = current;
            mixinExpiresAt.set(now + 60L * 60L * 1000L);
            return current;
        }
    }

    private JsonValue getJson(String url) throws IOException {
        HttpResult response = http.get(url, cookies.header());
        if (!response.successful()) {
            throw new IOException("HTTP " + response.statusCode + " from " + endpointName(url));
        }
        try {
            return Json.parse(response.body);
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid JSON from " + endpointName(url), e);
        }
    }

    private static void requireCodeZero(JsonValue root, String apiName) throws IOException {
        int code = root.get("code")
            .asInt(Integer.MIN_VALUE);
        if (code != 0) {
            String message = root.get("message")
                .asString(
                    root.get("msg")
                        .asString("unknown error"));
            throw new IOException(apiName + " failed: code=" + code + ", message=" + message);
        }
    }

    private static boolean isSafeHost(String host) {
        if (host == null || host.isEmpty() || host.length() > 253) {
            return false;
        }
        for (int i = 0; i < host.length(); i++) {
            char c = host.charAt(i);
            boolean allowed = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9')
                || c == '.'
                || c == '-';
            if (!allowed) return false;
        }
        return true;
    }

    private static String endpointName(String url) {
        int query = url.indexOf('?');
        return query >= 0 ? url.substring(0, query) : url;
    }
}
