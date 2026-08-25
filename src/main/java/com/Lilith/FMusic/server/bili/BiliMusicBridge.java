package com.Lilith.FMusic.server.bili;
import net.minecraft.util.StatCollector;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

import com.Lilith.FMusic.server.bili.allmusic.AllMusicBridge;
import com.Lilith.FMusic.server.bili.bilibili.BilibiliLiveClient;
import com.Lilith.FMusic.server.bili.bilibili.CookieStore;
import com.Lilith.FMusic.server.bili.config.PluginSettings;
import com.Lilith.FMusic.server.bili.platform.BridgePlatform;
import com.Lilith.FMusic.server.bili.platform.ForgeBridgePlatform;
import com.Lilith.FMusic.server.bili.request.SongRequestService;

/** Platform-independent lifecycle and service container. */
public final class BiliMusicBridge {

    private final BridgePlatform platform;
    private volatile boolean active;
    private volatile PluginSettings settings;
    private AllMusicBridge allMusicBridge;
    private volatile CookieStore cookieStore;
    private volatile SongRequestService requestService;
    private volatile BilibiliLiveClient liveClient;

    public BiliMusicBridge(BridgePlatform platform) {
        if (platform == null) {
            throw new IllegalArgumentException("platform");
        }
        this.platform = platform;
    }

    public synchronized boolean enable() {
        if (active) {
            return true;
        }
        try {
            saveDefaultConfig();
            settings = PluginSettings.load(configFile());
        } catch (Exception e) {
            log(Level.SEVERE, StatCollector.translateToLocal("fmusic.log.bili.invalid_config"), e);
            return false;
        }

        allMusicBridge = new AllMusicBridge(this);
        if (!buildRuntime()) {
            return false;
        }
        active = true;
        if (settings.autoConnect && settings.roomId > 0L) {
            liveClient.start();
        } else if (settings.roomId <= 0L) {
            warning(StatCollector.translateToLocalFormatted("fmusic.log.bili.set_room_id", configFile().getPath()));
        }
        info(StatCollector.translateToLocalFormatted("fmusic.log.bili.enabled", platform.platformName()));
        return true;
    }

    public synchronized void disable() {
        active = false;
        stopRuntime();
        if (allMusicBridge != null) {
            allMusicBridge.invalidate();
        }
        info(StatCollector.translateToLocal("fmusic.log.bili.disabled"));
    }

    public synchronized boolean reloadBridge() {
        if (!active) {
            return false;
        }
        stopRuntime();
        try {
            settings = PluginSettings.load(configFile());
        } catch (Exception e) {
            log(Level.SEVERE, StatCollector.translateToLocal("fmusic.log.bili.reload_fail"), e);
            return false;
        }
        if (allMusicBridge == null) {
            allMusicBridge = new AllMusicBridge(this);
        } else {
            allMusicBridge.invalidate();
        }
        if (!buildRuntime()) {
            return false;
        }
        if (settings.autoConnect && settings.roomId > 0L) {
            liveClient.start();
        }
        return true;
    }

    private boolean buildRuntime() {
        CookieStore newCookieStore = new CookieStore(platform.dataFolder(), settings.cookieFile);
        try {
            int count = newCookieStore.reload();
            info(
                "Loaded " + count
                    + " Bilibili cookie entries from "
                    + newCookieStore.file()
                        .getName()
                    + " (values are never logged).");
        } catch (IOException | IllegalArgumentException e) {
            log(Level.SEVERE, StatCollector.translateToLocalFormatted("fmusic.log.bili.cookie_load_fail", newCookieStore.file()), e);
            return false;
        }
        SongRequestService newRequestService = new SongRequestService(this, allMusicBridge);
        BilibiliLiveClient newLiveClient = new BilibiliLiveClient(this, newCookieStore, newRequestService);
        cookieStore = newCookieStore;
        requestService = newRequestService;
        liveClient = newLiveClient;
        return true;
    }

    private void stopRuntime() {
        BilibiliLiveClient currentLive = liveClient;
        liveClient = null;
        if (currentLive != null) {
            currentLive.stop();
        }
        SongRequestService currentRequests = requestService;
        requestService = null;
        if (currentRequests != null) {
            currentRequests.shutdown();
        }
    }

    private void saveDefaultConfig() throws IOException {
        File folder = platform.dataFolder();
        if (!folder.isDirectory() && !folder.mkdirs() && !folder.isDirectory()) {
            throw new IOException("Unable to create plugin data folder: " + folder);
        }
        File target = configFile();
        if (target.isFile()) {
            return;
        }
        try (FileOutputStream output = new FileOutputStream(target)) {
            output.write(DEFAULT_CONFIG.getBytes(StandardCharsets.UTF_8));
        }
    }

    private File configFile() {
        return new File(platform.dataFolder(), "config.json");
    }

    public boolean executeGlobal(Runnable task) {
        return task != null && isActive() && platform.executeGlobal(task);
    }

    public void broadcast(String message) {
        if (isActive()) {
            platform.broadcast(message == null ? "" : message);
        }
    }

    public void debug(String message) {
        PluginSettings current = settings;
        if (current != null && current.debug) {
            info(StatCollector.translateToLocalFormatted("fmusic.log.bili.debug_prefix", message));
        }
    }

    public void info(String message) {
        platform.log(Level.INFO, message, null);
    }

    public void warning(String message) {
        platform.log(Level.WARNING, message, null);
    }

    public void log(Level level, String message, Throwable throwable) {
        platform.log(level, message, throwable);
    }

    public boolean isActive() {
        return active && platform.isActive();
    }

    public BridgePlatform getPlatform() {
        return platform;
    }

    public PluginSettings getSettings() {
        return settings;
    }

    public AllMusicBridge getAllMusicBridge() {
        return allMusicBridge;
    }

    public CookieStore getCookieStore() {
        return cookieStore;
    }

    public SongRequestService getRequestService() {
        return requestService;
    }

    public BilibiliLiveClient getLiveClient() {
        return liveClient;
    }

    // ===== FMusic 生命周期钩子 =====

    private static volatile BiliMusicBridge instance;

    public static synchronized void start() {
        if (instance != null) {
            return;
        }
        instance = new BiliMusicBridge(new ForgeBridgePlatform());
        instance.enable();
    }

    public static synchronized void stop() {
        if (instance != null) {
            instance.disable();
            instance = null;
        }
    }

    public static BiliMusicBridge instanceForCommand() {
        return instance;
    }

    public static synchronized void reload() {
        if (instance != null) {
            instance.reloadBridge();
        }
    }

    /** 默认配置 (JSON, 与原 config.yml 键一致) */
    private static final String DEFAULT_CONFIG = "{" + "\"room-id\": 0,"
        + "\"auto-connect\": true,"
        + "\"cookie-file\": \"cookie.json\","
        + "\"song-request\": {"
        + "  \"prefixes\": [\"点歌\"],"
        + "  \"max-keyword-length\": 80,"
        + "  \"queue-capacity\": 100,"
        + "  \"per-user-cooldown-seconds\": 20,"
        + "  \"global-cooldown-millis\": 1000,"
        + "  \"duplicate-window-seconds\": 8,"
        + "  \"requester-name-mode\": \"username\","
        + "  \"requester-fixed-name\": \"B站观众\""
        + "},"
        + "\"allmusic\": {"
        + "  \"use-default-api\": true,"
        + "  \"direct-queue\": true,"
        + "  \"respect-player-ban\": true,"
        + "  \"require-online-player\": true"
        + "},"
        + "\"network\": {"
        + "  \"connect-timeout-millis\": 10000,"
        + "  \"read-timeout-millis\": 15000,"
        + "  \"websocket-read-timeout-millis\": 1000,"
        + "  \"auth-timeout-millis\": 10000,"
        + "  \"heartbeat-seconds\": 30,"
        + "  \"reconnect-min-seconds\": 3,"
        + "  \"reconnect-max-seconds\": 60,"
        + "  \"max-http-response-bytes\": 4194304,"
        + "  \"max-websocket-frame-bytes\": 16777216,"
        + "  \"max-inflated-packet-bytes\": 33554432"
        + "},"
        + "\"messages\": {"
        + "  \"broadcast-success\": true,"
        + "  \"broadcast-failure\": false,"
        + "  \"success\": \"&d[B站点歌] &f{user} &7点了 &b{song} &7- &f{artist}\","
        + "  \"not-found\": \"&c[B站点歌] 没有搜索到：{keyword}\","
        + "  \"queue-full\": \"&c[B站点歌] 点歌队列已满。\","
        + "  \"rejected\": \"&c[B站点歌] 请求未加入队列：{reason}\""
        + "},"
        + "\"debug\": false"
        + "}";
}
