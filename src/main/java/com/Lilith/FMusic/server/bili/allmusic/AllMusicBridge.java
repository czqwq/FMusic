package com.Lilith.FMusic.server.bili.allmusic;

import com.Lilith.FMusic.server.FMusicServer;
import com.Lilith.FMusic.server.bili.BiliMusicBridge;
import com.Lilith.FMusic.server.bili.config.PluginSettings;
import com.Lilith.FMusic.server.bili.request.SongRequest;
import com.Lilith.FMusic.server.bili.request.SongSelection;
import com.Lilith.FMusic.server.core.FMusic;
import com.Lilith.FMusic.server.core.FMusicApi;
import com.Lilith.FMusic.server.core.IMusicApi;
import com.Lilith.FMusic.server.core.music.PlayMusic;
import com.Lilith.FMusic.server.core.objs.SearchMusicObj;
import com.Lilith.FMusic.server.core.objs.music.PlayerAddMusicObj;
import com.Lilith.FMusic.server.core.objs.music.SearchPageObj;
import com.Lilith.FMusic.server.core.saves.BanSave;

/**
 * FMusic 集成层: 类型化直接调用 (原 BiliMusicBridge 反射适配层)。
 * 搜索默认音乐源第一首并加入 FMusic 播放队列, 保留原 AllMusic 队列检查。
 */
public final class AllMusicBridge {

    public interface Callback {

        void complete(QueueResult result);
    }

    public interface CancellationCheck {

        boolean cancelled();
    }

    private final BiliMusicBridge plugin;
    private volatile boolean available = true;

    public AllMusicBridge(BiliMusicBridge plugin) {
        this.plugin = plugin;
    }

    public void invalidate() {
        available = false;
    }

    public boolean available() {
        return available && FMusic.side != null && FMusic.isRun;
    }

    public void searchAndQueue(final SongRequest request, final PluginSettings settings,
        final CancellationCheck cancellation, final Callback callback) {
        if (request == null || settings == null || cancellation == null || callback == null) {
            return;
        }
        if (cancellation.cancelled()) {
            callback.complete(QueueResult.failure(QueueResult.Status.CANCELLED, "request service stopped"));
            return;
        }
        if (!settings.allMusic.useDefaultApi || !settings.allMusic.directQueue) {
            callback.complete(
                QueueResult.failure(
                    QueueResult.Status.DISABLED,
                    "allmusic.use-default-api and allmusic.direct-queue must both be true"));
            return;
        }
        if (!available()) {
            callback.complete(
                QueueResult.failure(QueueResult.Status.ALLMUSIC_MISSING, "FMusic is not running or not enabled"));
            return;
        }

        final IMusicApi api = FMusicApi.getApiMusic();
        if (api == null) {
            callback.complete(
                QueueResult.failure(QueueResult.Status.API_MISSING, "FMusic defaultApi is missing or not loaded"));
            return;
        }

        final SongSelection selection;
        try {
            SearchPageObj page = api.search(new String[] { request.keyword }, true);
            if (page == null || page.getIndex() <= 0) {
                callback.complete(QueueResult.failure(QueueResult.Status.NOT_FOUND, "empty search result"));
                return;
            }
            String id = page.getSong(0);
            String apiId = page.getApi();
            if (apiId == null || apiId.isEmpty()) {
                apiId = api.getId();
            }
            IMusicApi selectedApi = FMusicApi.getApiMusic(apiId);
            if (selectedApi == null) {
                callback.complete(
                    QueueResult.failure(QueueResult.Status.API_MISSING, "Search result API is missing: " + apiId));
                return;
            }
            SearchMusicObj res = page.getRes(0);
            String name = res == null ? "未知歌曲" : res.name;
            String artist = res == null ? "未知歌手" : res.author;
            selection = new SongSelection(id, apiId, name, artist);
        } catch (Exception e) {
            plugin.warning("FMusic default API search failed for: " + request.keyword + " - " + e);
            callback.complete(QueueResult.failure(QueueResult.Status.SEARCH_FAILED, safeMessage(e)));
            return;
        }
        if (cancellation.cancelled()) {
            callback.complete(QueueResult.failure(QueueResult.Status.CANCELLED, "request service stopped"));
            return;
        }

        boolean scheduled = plugin.executeGlobal(new Runnable() {

            @Override
            public void run() {
                if (!plugin.isActive() || cancellation.cancelled()) {
                    callback.complete(QueueResult.failure(QueueResult.Status.DISABLED, "plugin is disabling"));
                    return;
                }
                try {
                    callback.complete(queueDirect(request, selection, api, settings));
                } catch (Exception e) {
                    plugin.warning("FMusic queue operation failed: " + e);
                    callback.complete(QueueResult.failure(QueueResult.Status.INTERNAL_ERROR, safeMessage(e)));
                }
            }
        });
        if (!scheduled) {
            callback.complete(
                QueueResult.failure(
                    QueueResult.Status.DISABLED,
                    plugin.getPlatform()
                        .platformName() + " rejected the queue task"));
        }
    }

    private QueueResult queueDirect(SongRequest request, SongSelection song, IMusicApi api, PluginSettings settings) {
        if (song.id.isEmpty() || song.api.isEmpty()) {
            return QueueResult.failure(QueueResult.Status.INVALID_ID, "search result has no id or api");
        }
        if (!api.checkId(song.id)) {
            return QueueResult.failure(QueueResult.Status.INVALID_ID, "default API rejected the song id");
        }
        if (PlayMusic.getListSize() >= FMusic.getConfig().maxPlayList) {
            return QueueResult.failure(QueueResult.Status.LIST_FULL, "playlist is full");
        }
        if (BanSave.checkBanMusic(song.id, song.api)) {
            return QueueResult.failure(QueueResult.Status.SONG_BANNED, "song is banned");
        }
        if (PlayMusic.haveMusic(song.id, song.api)) {
            return QueueResult.failure(QueueResult.Status.DUPLICATE, "song already exists in playlist");
        }
        if (PlayMusic.isPlayerMax(request.requesterName)) {
            return QueueResult.failure(QueueResult.Status.PLAYER_LIMIT, "requester reached FMusic maxPlayerList");
        }
        if (settings.allMusic.respectPlayerBan && BanSave.checkBanPlayer(request.requesterName)) {
            return QueueResult.failure(QueueResult.Status.PLAYER_BANNED, "requester is banned by FMusic");
        }
        BanSave.removeMutePlayer(request.requesterName);
        if (settings.allMusic.requireOnlinePlayer && !FMusic.side.needPlay(false)) {
            return QueueResult.failure(QueueResult.Status.NO_PLAYER, "no eligible player is online");
        }

        PlayerAddMusicObj queueObject = new PlayerAddMusicObj();
        // Forge 服务器实例实现 ICommandSender, 作为"控制台"发送者
        queueObject.sender = FMusicServer.server;
        queueObject.id = song.id;
        queueObject.api = song.api;
        queueObject.name = request.requesterName;
        queueObject.isDefault = false;
        if (FMusic.side.onMusicAdd(queueObject.sender, queueObject)) {
            return QueueResult.failure(QueueResult.Status.EVENT_CANCELLED, "FMusic onMusicAdd cancelled the request");
        }
        PlayMusic.addTask(queueObject);
        return QueueResult.success(song);
    }

    private static String safeMessage(Throwable throwable) {
        if (throwable == null) {
            return "unknown error";
        }
        String message = throwable.getMessage();
        return message == null || message.trim()
            .isEmpty() ? throwable.getClass()
                .getSimpleName() : message;
    }
}
