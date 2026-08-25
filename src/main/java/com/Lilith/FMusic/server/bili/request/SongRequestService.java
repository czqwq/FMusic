package com.Lilith.FMusic.server.bili.request;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import com.Lilith.FMusic.server.bili.BiliMusicBridge;
import com.Lilith.FMusic.server.bili.allmusic.AllMusicBridge;
import com.Lilith.FMusic.server.bili.allmusic.QueueResult;
import com.Lilith.FMusic.server.bili.config.PluginSettings;
import com.Lilith.FMusic.server.bili.util.NamedThreadFactory;
import com.Lilith.FMusic.server.bili.util.Strings;

public final class SongRequestService {

    private final BiliMusicBridge plugin;
    private final AllMusicBridge allMusic;
    private final SongRequestParser parser = new SongRequestParser();
    private final RequestGate gate = new RequestGate();
    private final AtomicLong received = new AtomicLong();
    private final AtomicLong accepted = new AtomicLong();
    private final AtomicLong succeeded = new AtomicLong();
    private final AtomicLong failed = new AtomicLong();
    private final AtomicBoolean active = new AtomicBoolean(true);
    private volatile ThreadPoolExecutor executor;

    public SongRequestService(BiliMusicBridge plugin, AllMusicBridge allMusic) {
        this.plugin = plugin;
        this.allMusic = allMusic;
        rebuildExecutor(plugin.getSettings().songRequest.queueCapacity);
    }

    public void shutdown() {
        active.set(false);
        gate.clear();
        ThreadPoolExecutor current = executor;
        if (current != null) {
            current.shutdownNow();
        }
    }

    public boolean acceptDanmaku(DanmakuMessage message) {
        if (!active.get()) {
            return false;
        }
        received.incrementAndGet();
        PluginSettings settings = plugin.getSettings();
        SongRequest request = parser.parse(message, settings.songRequest);
        if (request == null) {
            return false;
        }
        RequestGate.Decision decision = gate.checkAndMark(request, settings.songRequest, System.currentTimeMillis());
        if (decision != RequestGate.Decision.ACCEPTED) {
            plugin.debug("Ignored song request from " + request.username + ": " + decision);
            return true;
        }
        return submit(request);
    }

    public boolean submitManual(String username, String keyword) {
        if (!active.get()) {
            return false;
        }
        PluginSettings settings = plugin.getSettings();
        String cleanKeyword = Strings.safeTrim(keyword, settings.songRequest.maxKeywordLength);
        if (cleanKeyword.isEmpty()) {
            return false;
        }
        String requester = "fixed".equals(settings.songRequest.requesterNameMode)
            ? settings.songRequest.requesterFixedName
            : username;
        SongRequest request = new SongRequest(
            0L,
            username,
            Strings.sanitizeRequester(requester, settings.songRequest.requesterFixedName),
            cleanKeyword,
            System.currentTimeMillis());
        return submit(request);
    }

    private boolean submit(final SongRequest request) {
        ThreadPoolExecutor current = executor;
        if (!active.get() || current == null || current.isShutdown()) {
            return false;
        }
        try {
            current.execute(new Runnable() {

                @Override
                public void run() {
                    if (!active.get()) {
                        return;
                    }
                    accepted.incrementAndGet();
                    final PluginSettings settings = plugin.getSettings();
                    allMusic.searchAndQueue(request, settings, new AllMusicBridge.CancellationCheck() {

                        @Override
                        public boolean cancelled() {
                            return !active.get();
                        }
                    }, new AllMusicBridge.Callback() {

                        @Override
                        public void complete(final QueueResult result) {
                            if (!active.get()) {
                                return;
                            }
                            plugin.executeGlobal(new Runnable() {

                                @Override
                                public void run() {
                                    handleResult(request, settings, result);
                                }
                            });
                        }
                    });
                }
            });
            return true;
        } catch (RejectedExecutionException e) {
            failed.incrementAndGet();
            plugin.warning("Bilibili song request work queue is full; dropped: " + request.keyword);
            return false;
        }
    }

    private void handleResult(SongRequest request, PluginSettings settings, QueueResult result) {
        if (result != null && result.successful()) {
            succeeded.incrementAndGet();
            if (settings.messages.broadcastSuccess) {
                String message = settings.messages.success;
                message = Strings.replace(message, "user", request.requesterName);
                message = Strings.replace(message, "keyword", request.keyword);
                message = Strings.replace(message, "song", result.song.name);
                message = Strings.replace(message, "artist", result.song.artist);
                message = Strings.replace(message, "api", result.song.api);
                message = Strings.replace(message, "id", result.song.id);
                plugin.broadcast(message);
            }
            plugin.info(
                "Bilibili request queued: " + request.requesterName
                    + " -> "
                    + result.song.name
                    + " - "
                    + result.song.artist
                    + " ["
                    + result.song.api
                    + ':'
                    + result.song.id
                    + ']');
            return;
        }
        failed.incrementAndGet();
        QueueResult.Status status = result == null ? QueueResult.Status.INTERNAL_ERROR : result.status;
        String detail = result == null ? "result is null" : result.detail;
        plugin.warning(
            "Bilibili request rejected: " + request.requesterName
                + " -> "
                + request.keyword
                + " ("
                + status
                + ": "
                + detail
                + ')');
        if (!settings.messages.broadcastFailure) {
            return;
        }
        String message;
        if (status == QueueResult.Status.NOT_FOUND) {
            message = settings.messages.notFound;
        } else if (status == QueueResult.Status.LIST_FULL) {
            message = settings.messages.queueFull;
        } else {
            message = settings.messages.rejected;
        }
        message = Strings.replace(message, "user", request.requesterName);
        message = Strings.replace(message, "keyword", request.keyword);
        message = Strings.replace(message, "reason", status.name());
        plugin.broadcast(message);
    }

    private synchronized void rebuildExecutor(int capacity) {
        ThreadPoolExecutor previous = executor;
        ThreadPoolExecutor replacement = new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<Runnable>(Math.max(1, capacity)),
            new NamedThreadFactory("BiliMusicBridge-Search", true),
            new ThreadPoolExecutor.AbortPolicy());
        replacement.prestartCoreThread();
        executor = replacement;
        if (previous != null) {
            previous.shutdownNow();
        }
    }

    public int pending() {
        ThreadPoolExecutor current = executor;
        return current == null ? 0
            : current.getQueue()
                .size();
    }

    public long receivedCount() {
        return received.get();
    }

    public long acceptedCount() {
        return accepted.get();
    }

    public long succeededCount() {
        return succeeded.get();
    }

    public long failedCount() {
        return failed.get();
    }
}
