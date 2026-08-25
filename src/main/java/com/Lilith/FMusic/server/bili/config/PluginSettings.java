package com.Lilith.FMusic.server.bili.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.Gson;

public final class PluginSettings {

    public final long roomId;
    public final boolean autoConnect;
    public final String cookieFile;
    public final SongRequest songRequest;
    public final AllMusic allMusic;
    public final Network network;
    public final Messages messages;
    public final boolean debug;

    private PluginSettings(long roomId, boolean autoConnect, String cookieFile, SongRequest songRequest,
        AllMusic allMusic, Network network, Messages messages, boolean debug) {
        this.roomId = roomId;
        this.autoConnect = autoConnect;
        this.cookieFile = cookieFile;
        this.songRequest = songRequest;
        this.allMusic = allMusic;
        this.network = network;
        this.messages = messages;
        this.debug = debug;
    }

    public static PluginSettings load(File file) throws IOException {
        if (file == null || !file.isFile()) {
            throw new IOException("Config file does not exist: " + file);
        }
        Object loaded;
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            loaded = new Gson().fromJson(reader, Object.class);
        }
        if (!(loaded instanceof Map)) {
            throw new IllegalArgumentException("config.json root must be a map");
        }
        return load(new Values((Map<?, ?>) loaded));
    }

    private static PluginSettings load(Values config) {
        long roomId = Math.max(0L, config.getLong("room-id", 0L));
        boolean autoConnect = config.getBoolean("auto-connect", true);
        String cookieFile = safeFileName(config.getString("cookie-file", "cookie.json"));

        List<String> prefixes = new ArrayList<String>(config.getStringList("song-request.prefixes"));
        if (prefixes.isEmpty()) {
            prefixes.add("点歌");
        }
        List<String> cleaned = new ArrayList<String>();
        for (String prefix : prefixes) {
            if (prefix != null && !prefix.trim()
                .isEmpty()) {
                cleaned.add(prefix.trim());
            }
        }
        if (cleaned.isEmpty()) {
            cleaned.add("点歌");
        }
        SongRequest songRequest = new SongRequest(
            Collections.unmodifiableList(cleaned),
            clamp(config.getInt("song-request.max-keyword-length", 80), 1, 256),
            clamp(config.getInt("song-request.queue-capacity", 100), 1, 10000),
            clamp(config.getInt("song-request.per-user-cooldown-seconds", 20), 0, 86400),
            clamp(config.getLong("song-request.global-cooldown-millis", 1000L), 0L, 3600000L),
            clamp(config.getInt("song-request.duplicate-window-seconds", 8), 0, 86400),
            enumText(config.getString("song-request.requester-name-mode", "username"), "username", "fixed"),
            text(config.getString("song-request.requester-fixed-name", "B站观众"), "B站观众"));

        AllMusic allMusic = new AllMusic(
            config.getBoolean("allmusic.use-default-api", true),
            config.getBoolean("allmusic.direct-queue", true),
            config.getBoolean("allmusic.respect-player-ban", true),
            config.getBoolean("allmusic.require-online-player", true));

        Network network = new Network(
            clamp(config.getInt("network.connect-timeout-millis", 10000), 1000, 120000),
            clamp(config.getInt("network.read-timeout-millis", 15000), 1000, 120000),
            clamp(config.getInt("network.websocket-read-timeout-millis", 1000), 100, 30000),
            clamp(config.getInt("network.auth-timeout-millis", 10000), 1000, 60000),
            clamp(config.getInt("network.heartbeat-seconds", 30), 10, 120),
            clamp(config.getInt("network.reconnect-min-seconds", 3), 1, 300),
            clamp(config.getInt("network.reconnect-max-seconds", 60), 1, 1800),
            clamp(config.getInt("network.max-http-response-bytes", 4194304), 65536, 67108864),
            clamp(config.getInt("network.max-websocket-frame-bytes", 16777216), 65536, 67108864),
            clamp(config.getInt("network.max-inflated-packet-bytes", 33554432), 65536, 134217728));
        if (network.reconnectMaxSeconds < network.reconnectMinSeconds) {
            network = new Network(
                network.connectTimeoutMillis,
                network.readTimeoutMillis,
                network.websocketReadTimeoutMillis,
                network.authTimeoutMillis,
                network.heartbeatSeconds,
                network.reconnectMinSeconds,
                network.reconnectMinSeconds,
                network.maxHttpResponseBytes,
                network.maxWebSocketFrameBytes,
                network.maxInflatedPacketBytes);
        }

        Messages messages = new Messages(
            config.getBoolean("messages.broadcast-success", true),
            config.getBoolean("messages.broadcast-failure", false),
            text(config.getString("messages.success", null), "§d[B站点歌] §f{user} §7点了 §b{song} §7- §f{artist}"),
            text(config.getString("messages.not-found", null), "§c[B站点歌] 没有搜索到：{keyword}"),
            text(config.getString("messages.queue-full", null), "§c[B站点歌] 点歌队列已满。"),
            text(config.getString("messages.rejected", null), "§c[B站点歌] 请求未加入队列：{reason}"));

        return new PluginSettings(
            roomId,
            autoConnect,
            cookieFile,
            songRequest,
            allMusic,
            network,
            messages,
            config.getBoolean("debug", false));
    }

    private static String safeFileName(String input) {
        String value = text(input, "cookie.json").replace('\\', '/');
        if (value.isEmpty() || value.startsWith("/") || value.indexOf(':') >= 0) {
            return "cookie.json";
        }
        String[] segments = value.split("/");
        for (String segment : segments) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                return "cookie.json";
            }
        }
        return value;
    }

    private static String enumText(String input, String first, String second) {
        String value = text(input, first).toLowerCase(Locale.ROOT);
        return value.equals(second) ? second : first;
    }

    private static String text(String input, String fallback) {
        return input == null || input.trim()
            .isEmpty() ? fallback : input.trim();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class Values {

        private final Map<?, ?> root;

        private Values(Map<?, ?> root) {
            this.root = root;
        }

        private Object get(String path) {
            Object current = root;
            String[] parts = path.split("\\.");
            for (String part : parts) {
                if (!(current instanceof Map)) {
                    return null;
                }
                current = ((Map<?, ?>) current).get(part);
            }
            return current;
        }

        private String getString(String path, String fallback) {
            Object value = get(path);
            return value == null ? fallback : String.valueOf(value);
        }

        private boolean getBoolean(String path, boolean fallback) {
            Object value = get(path);
            if (value instanceof Boolean) {
                return (Boolean) value;
            }
            if (value instanceof Number) {
                return ((Number) value).intValue() != 0;
            }
            if (value instanceof String) {
                String text = ((String) value).trim();
                if ("true".equalsIgnoreCase(text)) return true;
                if ("false".equalsIgnoreCase(text)) return false;
            }
            return fallback;
        }

        private int getInt(String path, int fallback) {
            long value = getLong(path, fallback);
            if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
                return fallback;
            }
            return (int) value;
        }

        private long getLong(String path, long fallback) {
            Object value = get(path);
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            if (value instanceof String) {
                try {
                    return Long.parseLong(((String) value).trim());
                } catch (NumberFormatException ignored) {
                    return fallback;
                }
            }
            return fallback;
        }

        private List<String> getStringList(String path) {
            Object value = get(path);
            if (!(value instanceof List)) {
                return Collections.emptyList();
            }
            List<String> result = new ArrayList<String>();
            for (Object item : (List<?>) value) {
                if (item != null) {
                    result.add(String.valueOf(item));
                }
            }
            return result;
        }
    }

    public static final class SongRequest {

        public final List<String> prefixes;
        public final int maxKeywordLength;
        public final int queueCapacity;
        public final int perUserCooldownSeconds;
        public final long globalCooldownMillis;
        public final int duplicateWindowSeconds;
        public final String requesterNameMode;
        public final String requesterFixedName;

        private SongRequest(List<String> prefixes, int maxKeywordLength, int queueCapacity, int perUserCooldownSeconds,
            long globalCooldownMillis, int duplicateWindowSeconds, String requesterNameMode,
            String requesterFixedName) {
            this.prefixes = prefixes;
            this.maxKeywordLength = maxKeywordLength;
            this.queueCapacity = queueCapacity;
            this.perUserCooldownSeconds = perUserCooldownSeconds;
            this.globalCooldownMillis = globalCooldownMillis;
            this.duplicateWindowSeconds = duplicateWindowSeconds;
            this.requesterNameMode = requesterNameMode;
            this.requesterFixedName = requesterFixedName;
        }
    }

    public static final class AllMusic {

        public final boolean useDefaultApi;
        public final boolean directQueue;
        public final boolean respectPlayerBan;
        public final boolean requireOnlinePlayer;

        private AllMusic(boolean useDefaultApi, boolean directQueue, boolean respectPlayerBan,
            boolean requireOnlinePlayer) {
            this.useDefaultApi = useDefaultApi;
            this.directQueue = directQueue;
            this.respectPlayerBan = respectPlayerBan;
            this.requireOnlinePlayer = requireOnlinePlayer;
        }
    }

    public static final class Network {

        public final int connectTimeoutMillis;
        public final int readTimeoutMillis;
        public final int websocketReadTimeoutMillis;
        public final int authTimeoutMillis;
        public final int heartbeatSeconds;
        public final int reconnectMinSeconds;
        public final int reconnectMaxSeconds;
        public final int maxHttpResponseBytes;
        public final int maxWebSocketFrameBytes;
        public final int maxInflatedPacketBytes;

        private Network(int connectTimeoutMillis, int readTimeoutMillis, int websocketReadTimeoutMillis,
            int authTimeoutMillis, int heartbeatSeconds, int reconnectMinSeconds, int reconnectMaxSeconds,
            int maxHttpResponseBytes, int maxWebSocketFrameBytes, int maxInflatedPacketBytes) {
            this.connectTimeoutMillis = connectTimeoutMillis;
            this.readTimeoutMillis = readTimeoutMillis;
            this.websocketReadTimeoutMillis = websocketReadTimeoutMillis;
            this.authTimeoutMillis = authTimeoutMillis;
            this.heartbeatSeconds = heartbeatSeconds;
            this.reconnectMinSeconds = reconnectMinSeconds;
            this.reconnectMaxSeconds = reconnectMaxSeconds;
            this.maxHttpResponseBytes = maxHttpResponseBytes;
            this.maxWebSocketFrameBytes = maxWebSocketFrameBytes;
            this.maxInflatedPacketBytes = maxInflatedPacketBytes;
        }
    }

    public static final class Messages {

        public final boolean broadcastSuccess;
        public final boolean broadcastFailure;
        public final String success;
        public final String notFound;
        public final String queueFull;
        public final String rejected;

        private Messages(boolean broadcastSuccess, boolean broadcastFailure, String success, String notFound,
            String queueFull, String rejected) {
            this.broadcastSuccess = broadcastSuccess;
            this.broadcastFailure = broadcastFailure;
            this.success = success;
            this.notFound = notFound;
            this.queueFull = queueFull;
            this.rejected = rejected;
        }
    }
}
