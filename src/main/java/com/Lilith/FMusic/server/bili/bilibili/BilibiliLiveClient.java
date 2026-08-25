package com.Lilith.FMusic.server.bili.bilibili;
import net.minecraft.util.StatCollector;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;

import com.Lilith.FMusic.server.bili.BiliMusicBridge;
import com.Lilith.FMusic.server.bili.config.PluginSettings;
import com.Lilith.FMusic.server.bili.http.SimpleHttpClient;
import com.Lilith.FMusic.server.bili.json.Json;
import com.Lilith.FMusic.server.bili.json.JsonValue;
import com.Lilith.FMusic.server.bili.request.DanmakuMessage;
import com.Lilith.FMusic.server.bili.request.SongRequestService;
import com.Lilith.FMusic.server.bili.util.NamedThreadFactory;
import com.Lilith.FMusic.server.bili.util.Strings;
import com.Lilith.FMusic.server.bili.websocket.SimpleWebSocketClient;
import com.Lilith.FMusic.server.bili.websocket.WebSocketMessage;

public final class BilibiliLiveClient {

    public enum State {
        STOPPED,
        WAITING_FOR_ROOM,
        INITIALIZING,
        CONNECTING,
        AUTHENTICATING,
        CONNECTED,
        RECONNECT_WAIT,
        STOPPING
    }

    private static final byte[] DANMU_MSG_MARKER = "DANMU_MSG".getBytes(Strings.UTF_8);

    private final BiliMusicBridge plugin;
    private final CookieStore cookies;
    private final SongRequestService requests;
    private final Object supervisorLock = new Object();
    private final AtomicBoolean running = new AtomicBoolean();
    private final AtomicBoolean reconnectRequested = new AtomicBoolean();
    private final AtomicReference<State> state = new AtomicReference<State>(State.STOPPED);
    private final AtomicLong danmakuCount = new AtomicLong();
    private final AtomicLong reconnectCount = new AtomicLong();
    private final AtomicLong realRoomId = new AtomicLong();
    /** Bumped by every start()/stop() so a stale supervisor loop exits promptly. */
    private final AtomicLong epoch = new AtomicLong();
    private final AtomicReference<SimpleWebSocketClient> socket = new AtomicReference<SimpleWebSocketClient>();
    private final AtomicLong socketEpoch = new AtomicLong();
    private final Object wakeMonitor = new Object();
    private volatile ExecutorService supervisor;
    private volatile String connectedHost = "";
    private volatile long connectedAt;
    private volatile String lastError = "";
    private volatile PluginSettings prefixSettings;
    private volatile byte[][] prefixPatterns = new byte[0][];

    public BilibiliLiveClient(BiliMusicBridge plugin, CookieStore cookies, SongRequestService requests) {
        this.plugin = plugin;
        this.cookies = cookies;
        this.requests = requests;
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        final long myEpoch = epoch.incrementAndGet();
        executor().execute(new Runnable() {

            @Override
            public void run() {
                supervise(myEpoch);
            }
        });
    }

    private ExecutorService executor() {
        ExecutorService current = supervisor;
        if (current != null && !current.isShutdown()) {
            return current;
        }
        synchronized (supervisorLock) {
            current = supervisor;
            if (current == null || current.isShutdown()) {
                current = Executors.newSingleThreadExecutor(new NamedThreadFactory("BiliMusicBridge-Live", true));
                supervisor = current;
            }
            return current;
        }
    }

    public void reconnect() {
        if (!running.get()) {
            start();
            return;
        }
        reconnectRequested.set(true);
        closeSocket();
        wakeSupervisor();
    }

    public void stop() {
        if (!running.getAndSet(false)) {
            return;
        }
        epoch.incrementAndGet();
        state.set(State.STOPPING);
        closeSocket();
        wakeSupervisor();
        ExecutorService current = supervisor;
        if (current != null) {
            current.shutdownNow();
        }
        state.set(State.STOPPED);
    }

    private void supervise(long myEpoch) {
        int backoff = 3;
        while (running.get() && myEpoch == epoch.get()) {
            boolean reconnectImmediately = false;
            PluginSettings settings = plugin.getSettings();
            if (settings.roomId <= 0L) {
                setState(State.WAITING_FOR_ROOM, myEpoch);
                sleepInterruptibly(5000L);
                continue;
            }
            backoff = Math.max(settings.network.reconnectMinSeconds, backoff);
            try {
                connectCycle(settings, myEpoch);
                backoff = settings.network.reconnectMinSeconds;
            } catch (ReconnectRequestedException e) {
                reconnectImmediately = true;
                backoff = settings.network.reconnectMinSeconds;
                lastError = "";
            } catch (InterruptedException e) {
                Thread.currentThread()
                    .interrupt();
                break;
            } catch (Throwable e) {
                if (!running.get() || myEpoch != epoch.get()) break;
                reconnectCount.incrementAndGet();
                lastError = safeMessage(e);
                if (settings.debug) {
                    plugin.log(Level.WARNING, StatCollector.translateToLocal("fmusic.log.bili.live_fail"), e);
                } else {
                    plugin.warning(StatCollector.translateToLocalFormatted("fmusic.log.bili.live_fail_detail", lastError));
                }
            } finally {
                closeSocketIfCurrent(myEpoch);
                connectedHost = "";
                connectedAt = 0L;
            }
            if (!running.get() || myEpoch != epoch.get()) break;
            if (reconnectImmediately) {
                continue;
            }
            setState(State.RECONNECT_WAIT, myEpoch);
            int max = Math.max(settings.network.reconnectMinSeconds, settings.network.reconnectMaxSeconds);
            int delay = Math.min(max, Math.max(settings.network.reconnectMinSeconds, backoff));
            long jitter = ThreadLocalRandom.current()
                .nextLong(0L, Math.max(1L, delay * 250L));
            sleepInterruptibly(delay * 1000L + jitter);
            backoff = Math.min(max, Math.max(delay + 1, delay * 2));
        }
        if (myEpoch == epoch.get()) {
            state.set(State.STOPPED);
        }
    }

    private void connectCycle(PluginSettings settings, long myEpoch) throws Exception {
        reconnectRequested.set(false);
        setState(State.INITIALIZING, myEpoch);
        cookies.reload();
        SimpleHttpClient http = new SimpleHttpClient(
            settings.network.connectTimeoutMillis,
            settings.network.readTimeoutMillis,
            settings.network.maxHttpResponseBytes);
        BilibiliApiClient api = new BilibiliApiClient(http, cookies);
        RoomInfo room = api.resolveRoom(settings.roomId);
        realRoomId.set(room.realRoomId);
        if (room.liveStatus == 0) {
            plugin.warning(StatCollector.translateToLocalFormatted("fmusic.log.bili.room_offline", room.realRoomId));
        }
        api.ensureDeviceIds();
        DanmuInfo danmuInfo = api.getDanmuInfo(room.realRoomId);
        Throwable last = null;
        for (DanmuServer server : danmuInfo.servers) {
            if (!running.get() || myEpoch != epoch.get()) {
                throw new InterruptedException("Plugin is stopping");
            }
            if (reconnectRequested.getAndSet(false)) {
                throw new ReconnectRequestedException();
            }
            try {
                runServer(settings, room.realRoomId, danmuInfo.token, server, myEpoch);
                return;
            } catch (ReconnectRequestedException e) {
                throw e;
            } catch (InterruptedException e) {
                throw e;
            } catch (Throwable e) {
                if (!running.get() || myEpoch != epoch.get()) {
                    throw new InterruptedException("Plugin is stopping");
                }
                if (reconnectRequested.getAndSet(false)) {
                    throw new ReconnectRequestedException();
                }
                last = e;
                closeSocketIfCurrent(myEpoch);
                plugin.debug(StatCollector.translateToLocalFormatted("fmusic.log.bili.host_failed", server.host, safeMessage(e)));
            }
        }
        if (last instanceof Exception) {
            throw (Exception) last;
        }
        throw new IOException("All Bilibili danmaku hosts failed");
    }

    private void runServer(PluginSettings settings, long roomId, String token, DanmuServer server, long myEpoch)
        throws Exception {
        if (!running.get() || myEpoch != epoch.get()) {
            throw new InterruptedException("Plugin is stopping");
        }
        setState(State.CONNECTING, myEpoch);
        SimpleWebSocketClient client = new SimpleWebSocketClient(
            server.url(),
            settings.network.connectTimeoutMillis,
            settings.network.websocketReadTimeoutMillis,
            settings.network.maxWebSocketFrameBytes,
            cookies.header());
        socketEpoch.set(myEpoch);
        SimpleWebSocketClient previous = socket.getAndSet(client);
        if (previous != null && previous != client) {
            previous.close();
        }
        client.connect();
        connectedHost = server.host + ':' + server.wssPort;
        setState(State.AUTHENTICATING, myEpoch);
        client.sendBinary(BiliPacketCodec.auth(cookies.uid(), roomId, token, cookies.get("buvid3")));
        awaitAuth(client, settings, myEpoch);
        setState(State.CONNECTED, myEpoch);
        connectedAt = System.currentTimeMillis();
        lastError = "";
        plugin.info(
            "Connected to Bilibili room " + roomId
                + " via "
                + connectedHost
                + " (cookie entries: "
                + cookies.snapshot()
                    .size()
                + ")");
        client.sendBinary(BiliPacketCodec.heartbeat());
        long nextHeartbeat = System.currentTimeMillis() + settings.network.heartbeatSeconds * 1000L;
        while (running.get() && !reconnectRequested.get() && myEpoch == epoch.get()) {
            long now = System.currentTimeMillis();
            if (now >= nextHeartbeat) {
                client.sendBinary(BiliPacketCodec.heartbeat());
                nextHeartbeat = now + settings.network.heartbeatSeconds * 1000L;
            }
            try {
                WebSocketMessage message = client.readMessage();
                processWebSocketMessage(message, settings.network.maxInflatedPacketBytes);
            } catch (SocketTimeoutException ignored) {
                // Short read timeout lets the same thread send protocol heartbeats and react to shutdown.
            }
        }
        if (!running.get() || myEpoch != epoch.get()) {
            throw new InterruptedException("Plugin is stopping");
        }
        if (reconnectRequested.getAndSet(false)) {
            throw new ReconnectRequestedException();
        }
    }

    private void awaitAuth(SimpleWebSocketClient client, PluginSettings settings, long myEpoch) throws Exception {
        long deadline = System.currentTimeMillis() + settings.network.authTimeoutMillis;
        while (running.get() && myEpoch == epoch.get() && System.currentTimeMillis() < deadline) {
            try {
                WebSocketMessage message = client.readMessage();
                if (message.text) {
                    JsonValue json = Json.parse(new String(message.payload, Strings.UTF_8));
                    if (json.get("code")
                        .asInt(-1) == 0) return;
                    continue;
                }
                List<BiliPacket> packets = BiliPacketCodec
                    .decode(message.payload, settings.network.maxInflatedPacketBytes);
                for (BiliPacket packet : packets) {
                    if (packet.operation == BiliPacketCodec.OP_AUTH_REPLY) {
                        JsonValue body = Json.parse(new String(packet.body, Strings.UTF_8));
                        int code = body.get("code")
                            .asInt(-1);
                        if (code == 0) return;
                        throw new IOException("Bilibili WebSocket authentication rejected: code=" + code);
                    }
                    if (packet.operation == BiliPacketCodec.OP_MESSAGE) {
                        processPacket(packet);
                    }
                }
            } catch (SocketTimeoutException ignored) {
                // Check deadline.
            }
        }
        throw new IOException("Bilibili WebSocket authentication timed out");
    }

    private void processWebSocketMessage(WebSocketMessage message, int maxInflatedBytes) {
        if (message == null) return;
        try {
            if (message.text) {
                if (!containsBytes(message.payload, DANMU_MSG_MARKER)) {
                    return;
                }
                JsonValue value = Json.parse(new String(message.payload, Strings.UTF_8));
                processCommand(value);
                return;
            }
            List<BiliPacket> packets = BiliPacketCodec.decode(message.payload, maxInflatedBytes);
            for (BiliPacket packet : packets) {
                processPacket(packet);
            }
        } catch (Exception e) {
            plugin.debug(StatCollector.translateToLocalFormatted("fmusic.log.bili.malformed", safeMessage(e)));
        }
    }

    private void processPacket(BiliPacket packet) {
        if (packet.operation != BiliPacketCodec.OP_MESSAGE || packet.body.length == 0) {
            return;
        }
        byte[] data = packet.body;
        // Fast path: most packets (gifts, superchats, entries, ...) are not danmaku and
        // most danmaku do not start with a request prefix. Skip string allocation and
        // JSON parsing whenever the raw payload cannot contain a song request at all.
        if (!containsBytes(data, DANMU_MSG_MARKER)) {
            return;
        }
        PluginSettings settings = plugin.getSettings();
        if (settings != null && !containsAnyPrefix(data, prefixPatterns(settings))) {
            return;
        }
        try {
            JsonValue body = Json.parse(new String(data, Strings.UTF_8));
            processCommand(body);
        } catch (IllegalArgumentException e) {
            plugin.debug(StatCollector.translateToLocalFormatted("fmusic.log.bili.invalid_json", e.getMessage()));
        }
    }

    private void processCommand(JsonValue body) {
        String cmd = body.get("cmd")
            .asString("");
        if (!cmd.startsWith("DANMU_MSG")) {
            return;
        }
        JsonValue info = body.get("info");
        String text = info.get(1)
            .asString("");
        JsonValue user = info.get(2);
        long uid = user.get(0)
            .asLong(0L);
        String username = user.get(1)
            .asString(StatCollector.translateToLocal("fmusic.api.unknown_artist"));
        if (text.isEmpty()) {
            return;
        }
        danmakuCount.incrementAndGet();
        requests.acceptDanmaku(new DanmakuMessage(uid, username, text, System.currentTimeMillis()));
        plugin.debug(StatCollector.translateToLocalFormatted("fmusic.log.bili.danmaku", username, text));
    }

    private byte[][] prefixPatterns(PluginSettings settings) {
        byte[][] cached = prefixPatterns;
        if (prefixSettings == settings) {
            return cached;
        }
        List<String> prefixes = settings.songRequest.prefixes;
        byte[][] patterns = new byte[prefixes.size()][];
        for (int i = 0; i < prefixes.size(); i++) {
            patterns[i] = prefixes.get(i)
                .getBytes(Strings.UTF_8);
        }
        prefixPatterns = patterns;
        prefixSettings = settings;
        return patterns;
    }

    private static boolean containsAnyPrefix(byte[] data, byte[][] patterns) {
        for (byte[] pattern : patterns) {
            if (containsBytes(data, pattern)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsBytes(byte[] haystack, byte[] needle) {
        if (needle == null || needle.length == 0) {
            return true;
        }
        if (haystack == null || haystack.length < needle.length) {
            return false;
        }
        int last = haystack.length - needle.length;
        for (int i = 0; i <= last; i++) {
            int j = 0;
            while (j < needle.length && haystack[i + j] == needle[j]) {
                j++;
            }
            if (j == needle.length) {
                return true;
            }
        }
        return false;
    }

    private void setState(State next, long myEpoch) {
        if (running.get() && myEpoch == epoch.get()) {
            state.set(next);
        }
    }

    private void closeSocket() {
        SimpleWebSocketClient current = socket.getAndSet(null);
        if (current != null) {
            current.close();
        }
    }

    private void closeSocketIfCurrent(long myEpoch) {
        SimpleWebSocketClient current = socket.get();
        if (current == null || socketEpoch.get() != myEpoch) {
            return;
        }
        if (socket.compareAndSet(current, null)) {
            current.close();
        }
    }

    private void sleepInterruptibly(long millis) {
        if (millis <= 0L || !running.get()) return;
        synchronized (wakeMonitor) {
            try {
                wakeMonitor.wait(millis);
            } catch (InterruptedException e) {
                Thread.currentThread()
                    .interrupt();
            }
        }
    }

    private void wakeSupervisor() {
        synchronized (wakeMonitor) {
            wakeMonitor.notifyAll();
        }
    }

    private static String safeMessage(Throwable throwable) {
        if (throwable == null) return "unknown error";
        String message = throwable.getMessage();
        return message == null || message.trim()
            .isEmpty() ? throwable.getClass()
                .getSimpleName() : message;
    }

    public State state() {
        return state.get();
    }

    public long danmakuCount() {
        return danmakuCount.get();
    }

    public long reconnectCount() {
        return reconnectCount.get();
    }

    public long realRoomId() {
        return realRoomId.get();
    }

    public String connectedHost() {
        return connectedHost;
    }

    public long connectedAt() {
        return connectedAt;
    }

    public String lastError() {
        return lastError;
    }

    private static final class ReconnectRequestedException extends IOException {

        private static final long serialVersionUID = 1L;
    }
}
