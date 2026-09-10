package com.Lilith.FMusic.api;

import java.util.ArrayList;
import java.util.List;

import com.Lilith.FMusic.codec.HudPosObj;
import com.Lilith.FMusic.server.FMusicServer;
import com.Lilith.FMusic.server.core.FMusic;
import com.Lilith.FMusic.server.core.IMusicApi;
import com.Lilith.FMusic.server.core.music.PlayMusic;
import com.Lilith.FMusic.server.core.music.PlayRuntime;
import com.Lilith.FMusic.server.core.music.VoteItem;
import com.Lilith.FMusic.server.core.objs.SearchMusicObj;
import com.Lilith.FMusic.server.core.objs.music.PlayerAddMusicObj;
import com.Lilith.FMusic.server.core.objs.music.SearchPageObj;
import com.Lilith.FMusic.server.core.objs.music.SongInfoObj;
import com.Lilith.FMusic.server.core.saves.BanSave;
import com.Lilith.FMusic.server.core.saves.SaveTask;

/**
 * FMusic 对外调用接口 (供其他模组调用).
 *
 * <p>用法示例:
 * <pre>
 * // 点歌 (默认音源)
 * FMusicApi.addMusic("1887467085", "Steve", result -&gt; System.out.println(result));
 * // 指定音源点歌 (支持链接/短ID)
 * FMusicApi.addMusic("qqmusic", "https://i.y.qq.com/v8/playsong.html?songid=329062147", "Steve", null);
 * // 直链即时播放 (不入队)
 * FMusicApi.playUrl("Steve", "http://example.com/a.mp3");
 * // 搜索 (异步)
 * FMusicApi.search("qqmusic", "Mili", list -&gt; { ... });
 * // 投票
 * FMusicApi.startSwitchVote("Steve");
 * FMusicApi.agreeVote("Alex");
 * </pre>
 *
 * <p>线程说明: 带回调的方法内部使用 SaveTask/独立线程执行网络请求, 回调在**该后台线程**触发,
 * 不要在回调里直接操作世界/实体; 需要主线程时自行调度. 不带回调的方法为 fire-and-forget.
 */
public final class FMusicApi {

    private FMusicApi() {
    }

    /** 点歌回调 */
    public interface AddCallback {
        void onResult(AddResult result);
    }

    /** 搜索回调 */
    public interface SearchCallback {
        void onResult(List<SearchResult> results);
    }

    // ==================== 状态 ====================

    /** FMusic 是否已启动 */
    public static boolean isEnabled() {
        return FMusic.isRun && FMusic.side != null;
    }

    /** FMusic 版本 */
    public static String getVersion() {
        return FMusic.version;
    }

    /** 已注册音源 ID 列表 (netapi/qqmusic/kugou/外部 jar) */
    public static List<String> getApiIds() {
        return new ArrayList<String>(FMusic.MUSIC_APIS.keySet());
    }

    /** 音源是否已注册 */
    public static boolean hasApi(String apiId) {
        return apiId != null && FMusic.MUSIC_APIS.containsKey(apiId);
    }

    // ==================== 点歌 ====================

    /**
     * 点歌 (使用默认音源)
     *
     * @param musicId   歌曲 ID / songmid / 分享链接
     * @param requester 点歌者名字
     * @param callback  结果回调 (可为 null = fire-and-forget)
     */
    public static void addMusic(String musicId, String requester, AddCallback callback) {
        addMusic(FMusic.getConfig().defaultApi, musicId, requester, callback);
    }

    /**
     * 点歌 (指定音源)
     *
     * @param apiId     音源 ID (qqmusic/kugou/netapi)
     * @param musicId   歌曲 ID / songmid / hash / 分享链接
     * @param requester 点歌者名字
     * @param callback  结果回调 (可为 null)
     */
    public static void addMusic(final String apiId, final String musicId, final String requester,
                                final AddCallback callback) {
        if (!isEnabled()) {
            complete(callback, AddResult.failure(AddResult.Status.NOT_RUNNING, "FMusic is not running"));
            return;
        }
        final IMusicApi api = FMusic.MUSIC_APIS.get(apiId);
        if (api == null) {
            complete(callback, AddResult.failure(AddResult.Status.API_MISSING, "unknown api: " + apiId));
            return;
        }
        SaveTask.task(new Runnable() {
            @Override
            public void run() {
                try {
                    String id = api.getMusicId(musicId);
                    if (!api.checkId(id)) {
                        complete(callback, AddResult.failure(AddResult.Status.INVALID_ID, "invalid music id: " + musicId));
                        return;
                    }
                    complete(callback, addResolved(apiId, api, id, requester));
                } catch (Exception e) {
                    complete(callback, AddResult.failure(AddResult.Status.PARSE_FAILED, String.valueOf(e)));
                }
            }
        });
    }

    /**
     * 对指定玩家直接播放音频链接 (不入队, 立即播放; 适合自建直链/其他来源)
     *
     * <p>注意: 此方式不经过播放队列与音源解析, 不做时长/歌词处理;
     * 需要进入队列(有进度/歌词/投票)时请用 {@link #addMusic} 传音源歌曲 ID.
     *
     * @param player 目标玩家名
     * @param url    音频直链
     */
    public static void playUrl(String player, String url) {
        if (!isEnabled() || player == null || url == null || url.trim().isEmpty()) {
            return;
        }
        FMusic.side.sendMusic(player, url.trim());
    }

    /**
     * 对所有玩家直接播放音频链接 (不入队, 立即播放)
     *
     * @param url 音频直链
     */
    public static void playUrlAll(String url) {
        if (!isEnabled() || url == null || url.trim().isEmpty()) {
            return;
        }
        FMusic.side.sendMusic(url.trim());
    }

    /**
     * 同步点歌 (阻塞调用线程直到解析完成; 请在后台线程调用)
     *
     * @return 点歌结果
     */
    public static AddResult addMusicSync(String apiId, String musicId, String requester) {
        if (!isEnabled()) {
            return AddResult.failure(AddResult.Status.NOT_RUNNING, "FMusic is not running");
        }
        IMusicApi api = FMusic.MUSIC_APIS.get(apiId);
        if (api == null) {
            return AddResult.failure(AddResult.Status.API_MISSING, "unknown api: " + apiId);
        }
        try {
            String id = api.getMusicId(musicId);
            if (!api.checkId(id)) {
                return AddResult.failure(AddResult.Status.INVALID_ID, "invalid music id: " + musicId);
            }
            return addResolved(apiId, api, id, requester);
        } catch (Exception e) {
            return AddResult.failure(AddResult.Status.PARSE_FAILED, String.valueOf(e));
        }
    }

    /** 解析后的歌曲入队 (内部共用: 校验 + 事件 + 入队) */
    private static AddResult addResolved(String apiId, IMusicApi api, String id, String requester) {
        String player = requester == null ? "" : requester.toLowerCase();
        if (PlayMusic.getListSize() >= FMusic.getConfig().limit.maxPlayList) {
            return AddResult.failure(AddResult.Status.LIST_FULL, "playlist is full");
        }
        if (BanSave.checkBanMusic(id, apiId)) {
            return AddResult.failure(AddResult.Status.SONG_BANNED, "song is banned");
        }
        if (PlayMusic.haveMusic(id, apiId)) {
            return AddResult.failure(AddResult.Status.DUPLICATE, "song already in playlist");
        }
        if (PlayMusic.isPlayerMax(player)) {
            return AddResult.failure(AddResult.Status.PLAYER_LIMIT, "player reached limit");
        }
        if (BanSave.checkBanPlayer(player)) {
            return AddResult.failure(AddResult.Status.PLAYER_BANNED, "player is banned");
        }
        if (!FMusic.side.needPlay(false)) {
            return AddResult.failure(AddResult.Status.NO_PLAYER, "no eligible player online");
        }
        SongInfoObj info;
        try {
            info = api.getMusic(id, player, false);
        } catch (Exception e) {
            return AddResult.failure(AddResult.Status.PARSE_FAILED, String.valueOf(e));
        }
        if (info == null) {
            return AddResult.failure(AddResult.Status.PARSE_FAILED, "song info is null");
        }

        BanSave.removeMutePlayer(player);
        PlayerAddMusicObj obj = new PlayerAddMusicObj();
        obj.sender = FMusicServer.server;
        obj.id = id;
        obj.api = apiId;
        obj.name = player;
        obj.isDefault = false;
        if (FMusic.side.onMusicAdd(obj.sender, obj)) {
            return AddResult.failure(AddResult.Status.EVENT_CANCELLED, "cancelled by MusicAddEvent");
        }
        PlayMusic.addTask(obj);
        return AddResult.success(new AddResult.SongInfo(apiId, id, info.getName(), info.getAuthor(),
            info.getAl(), info.getAlia(), player, info.getLength(), info.getPicUrl(), info.isTrial()));
    }

    // ==================== 搜索 ====================

    /** 搜索 (默认音源, 异步) */
    public static void search(String keyword, SearchCallback callback) {
        search(FMusic.getConfig().defaultApi, keyword, callback);
    }

    /**
     * 搜索 (异步)
     *
     * @param apiId    音源 ID
     * @param keyword  搜索词
     * @param callback 结果回调 (后台线程, 可为 null)
     */
    public static void search(final String apiId, final String keyword, final SearchCallback callback) {
        if (!isEnabled()) {
            complete(callback, new ArrayList<SearchResult>());
            return;
        }
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                complete(callback, searchSync(apiId, keyword));
            }
        }, "fmusic_api_search");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * 搜索 (同步, 阻塞调用线程; 请在后台线程调用)
     *
     * @return 搜索结果 (失败时为空列表)
     */
    public static List<SearchResult> searchSync(String apiId, String keyword) {
        List<SearchResult> result = new ArrayList<SearchResult>();
        if (!isEnabled() || keyword == null || keyword.trim().isEmpty()) {
            return result;
        }
        IMusicApi api = FMusic.MUSIC_APIS.get(apiId);
        if (api == null) {
            return result;
        }
        try {
            SearchPageObj page = api.search(new String[]{keyword.trim()}, true);
            if (page == null) {
                return result;
            }
            int count = page.getIndex();
            for (int a = 0; a < count; a++) {
                SearchMusicObj item = page.getRes(a);
                if (item == null) {
                    continue;
                }
                result.add(new SearchResult(apiId, item.id, item.name, item.author, item.al));
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    // ==================== 投票 ====================

    /** 对当前播放歌曲发起切歌投票 */
    public static AddResult startSwitchVote(String requester) {
        if (!isEnabled()) {
            return AddResult.failure(AddResult.Status.NOT_RUNNING, "FMusic is not running");
        }
        SongInfoObj now = PlayMusic.nowPlayMusic;
        if (now == null) {
            return AddResult.failure(AddResult.Status.NO_PLAYER, "nothing is playing");
        }
        String player = requester == null ? "" : requester.toLowerCase();
        VoteItem item = new VoteItem(now.getApi(), now.getId(), player, VoteItem.VoteType.NEXT);
        item.votePlayer.add(player);
        if (PlayMusic.startVote(item)) {
            return AddResult.success(new AddResult.SongInfo(now.getApi(), now.getId(), now.getName(),
                now.getAuthor(), now.getAl(), now.getAlia(), player, now.getLength(), now.getPicUrl(), now.isTrial()));
        }
        return AddResult.failure(AddResult.Status.DUPLICATE, "this song is already in vote queue");
    }

    /** 对队列中的歌曲发起插歌投票 (投票通过后移到队首) */
    public static AddResult startPushVote(String apiId, String musicId, String requester) {
        if (!isEnabled()) {
            return AddResult.failure(AddResult.Status.NOT_RUNNING, "FMusic is not running");
        }
        SongInfoObj music = PlayMusic.getMusic(musicId, apiId);
        if (music == null) {
            return AddResult.failure(AddResult.Status.INVALID_ID, "song not in playlist");
        }
        String player = requester == null ? "" : requester.toLowerCase();
        VoteItem item = new VoteItem(apiId, musicId, player, VoteItem.VoteType.PUSH);
        item.votePlayer.add(player);
        if (PlayMusic.startVote(item)) {
            return AddResult.success(new AddResult.SongInfo(apiId, musicId, music.getName(), music.getAuthor(),
                music.getAl(), music.getAlia(), player, music.getLength(), music.getPicUrl(), music.isTrial()));
        }
        return AddResult.failure(AddResult.Status.DUPLICATE, "this song is already in vote queue");
    }

    /** 同意当前投票 */
    public static AddResult agreeVote(String player) {
        if (!isEnabled()) {
            return AddResult.failure(AddResult.Status.NOT_RUNNING, "FMusic is not running");
        }
        VoteItem vote = PlayMusic.getVote();
        if (vote == null) {
            return AddResult.failure(AddResult.Status.INVALID_ID, "no vote in progress");
        }
        String name = player == null ? "" : player.toLowerCase();
        if (vote.votePlayer.contains(name)) {
            return AddResult.failure(AddResult.Status.DUPLICATE, "already agreed");
        }
        PlayMusic.addVote(name);
        return AddResult.success(new AddResult.SongInfo(vote.getApi(), vote.getId(), "", "", "", "", name, 0, null, false));
    }

    /** 取消自己发起的投票 */
    public static boolean cancelVote(String requester, boolean push) {
        if (!isEnabled()) {
            return false;
        }
        String name = requester == null ? "" : requester.toLowerCase();
        VoteItem.VoteType type = push ? VoteItem.VoteType.PUSH : VoteItem.VoteType.NEXT;
        if (!PlayMusic.haveVote(name, type)) {
            return false;
        }
        PlayMusic.removeVote(name, type);
        return true;
    }

    /** 当前投票信息 (无投票返回 null) */
    public static VoteInfo getCurrentVote() {
        if (!isEnabled()) {
            return null;
        }
        VoteItem vote = PlayMusic.getVote();
        if (vote == null) {
            return null;
        }
        VoteInfo.Type type = vote.getType() == VoteItem.VoteType.NEXT ? VoteInfo.Type.SWITCH : VoteInfo.Type.PUSH;
        return new VoteInfo(type, vote.getApi(), vote.getId(), vote.getVoteSender(), vote.votePlayer.size(),
            PlayRuntime.getMiniVote(), PlayMusic.getVoteTime(), PlayMusic.getVoteCount());
    }

    /** 排队中的投票数量 (不含当前进行中的) */
    public static int getQueuedVoteCount() {
        return isEnabled() ? PlayMusic.getVoteCount() : 0;
    }

    // ==================== 播放控制 ====================

    /** 强制切歌到下一首 */
    public static void skip() {
        if (isEnabled()) {
            PlayMusic.musicLessTime = 0;
        }
    }

    /** 停止所有客户端播放并清空队列 */
    public static void stopAll() {
        if (!isEnabled()) {
            return;
        }
        PlayMusic.clear();
        FMusic.side.sendStop();
    }

    /** 清空播放队列 (不影响当前播放) */
    public static void clearQueue() {
        if (isEnabled()) {
            PlayMusic.clear();
        }
    }

    /** 队列长度 */
    public static int getQueueSize() {
        return isEnabled() ? PlayMusic.getListSize() : 0;
    }

    /** 队列内容 ("歌名 - 作者") */
    public static List<String> getQueueList() {
        List<String> list = new ArrayList<String>();
        if (!isEnabled()) {
            return list;
        }
        for (SongInfoObj item : PlayMusic.getList()) {
            list.add(item.getName() + " - " + item.getAuthor());
        }
        return list;
    }

    /** 按序号删除队列中的歌曲 (从 1 开始) */
    public static boolean removeFromQueue(int index) {
        if (!isEnabled() || index < 1 || index > PlayMusic.getListSize()) {
            return false;
        }
        SongInfoObj music = PlayMusic.remove(index - 1);
        return music != null;
    }

    /** 让指定玩家重新同步当前播放 (重发 PLAY/POS) */
    public static void resyncPlayer(String player) {
        if (isEnabled() && player != null) {
            FMusic.joinPlayNow(player);
        }
    }

    // ==================== 当前播放 ====================

    /** 是否有音乐在播放 */
    public static boolean isPlaying() {
        return isEnabled() && PlayMusic.nowPlayMusic != null;
    }

    /** 当前播放信息 (无播放返回 playing=false 的对象) */
    public static NowPlaying getNowPlaying() {
        if (!isEnabled() || PlayMusic.nowPlayMusic == null) {
            return new NowPlaying(false, null, null, null, null, null, null, null, 0, 0, null, false);
        }
        SongInfoObj now = PlayMusic.nowPlayMusic;
        return new NowPlaying(true, now.getApi(), now.getId(), now.getName(), now.getAuthor(), now.getAl(),
            now.getAlia(), now.getCall(), now.getLength(), PlayMusic.musicNowTime, PlayMusic.url, now.isTrial());
    }

    // ==================== 消息 / HUD ====================

    /** 向指定玩家发送消息 (MiniMessage 格式) */
    public static void sendMessage(String player, String miniMessageText) {
        if (isEnabled() && player != null) {
            FMusic.side.sendMessage(player, miniMessageText);
        }
    }

    /** 广播消息 (MiniMessage 格式) */
    public static void broadcast(String miniMessageText) {
        if (isEnabled()) {
            FMusic.side.broadcastInTask(miniMessageText);
        }
    }

    /** 获取玩家 HUD 配置 */
    public static HudPosObj getHud(String player) {
        return com.Lilith.FMusic.server.core.saves.HudSave.getOrNew(player);
    }

    /** 设置玩家 HUD 配置 */
    public static void setHud(String player, HudPosObj hud) {
        if (player != null && hud != null) {
            com.Lilith.FMusic.server.core.saves.HudSave.update(player, hud);
        }
    }

    /** 主动刷新玩家 HUD 显示 */
    public static void updateHud(String player) {
        if (isEnabled() && player != null) {
            FMusic.side.sendHudPos(player);
        }
    }

    // ==================== 内部 ====================

    private static void complete(AddCallback callback, AddResult result) {
        if (callback != null) {
            callback.onResult(result);
        }
    }

    private static void complete(SearchCallback callback, List<SearchResult> results) {
        if (callback != null) {
            callback.onResult(results);
        }
    }
}
