package com.Lilith.FMusic.dango.music

import com.Lilith.FMusic.dango.DangoClient
import com.Lilith.FMusic.dango.i18n.I18n
import com.Lilith.FMusic.dango.musicui.Library
import com.Lilith.FMusic.network.PointSongChannel

/**
 * 右键菜单里「发送到点歌系统」的客户端桥。
 *
 * 三种东西交汇在这里：
 * - 收包：服务端的能力包/结果包由 `client/network/ClientPointSongHandler` 在**网络线程**调进来；
 * - 发包：界面在渲染线程调 [send]；
 * - 提示：统一走 [Library.showTemporaryStatus]（只发一个 volatile 状态，两个线程都能安全调）。
 *
 * 放在 `dango/music` 而不是平台层：除了「有没有连服务器」之外它不碰任何 Minecraft 类，
 * 那个判断走 [DangoClient.platform] 的抽象（和界面其它地方一致）。
 */
object PointSongBridge {
    private const val STATUS_MS = 4_000L

    /**
     * 服务端支不支持点歌：进服后由能力包（TYPE_FEATURE）设一次，断线时清掉。
     * 老服务端根本不会发这个包，所以默认 false = 不支持。
     */
    @Volatile
    private var supported = false

    val isSupported: Boolean get() = supported

    /** 服务端进服时发的能力包 / 断线时客户端自己清掉 */
    @JvmStatic
    fun onFeature(value: Boolean) {
        supported = value
    }

    /** 服务端处理完点歌请求后回的状态码：翻译成一句提示。未知状态把原始码带出来，别吃掉。 */
    @JvmStatic
    fun onResult(status: String, songName: String, author: String) {
        // 作者可能为空，别把 " - " 单独留在歌名后面
        val song = if (author.isEmpty()) songName else "$songName - $author"
        val text = when (status) {
            "SUCCESS" -> I18n.format("Song request sent: %s", song)
            "LIST_FULL" -> I18n.text("The request queue is full")
            "DUPLICATE" -> I18n.text("This song is already in the queue")
            "SONG_BANNED" -> I18n.text("This song is banned on this server")
            "PLAYER_LIMIT" -> I18n.text("You have reached the request limit")
            "PLAYER_BANNED" -> I18n.text("You are banned from requesting songs")
            "NO_PLAYER" -> I18n.text("No player can receive music right now")
            "INVALID_ID", "BAD_REQUEST" -> I18n.text("Invalid song ID")
            "PARSE_FAILED", "NO_PLAY_URL" -> I18n.text("The server could not fetch this song")
            "API_MISSING" -> I18n.text("The server does not have this music source")
            "EVENT_CANCELLED" -> I18n.text("The server cancelled this request")
            "NOT_RUNNING" -> I18n.text("The music system is not running")
            "PERMISSION_DENIED" -> I18n.text("You don't have permission to request songs")
            "NO_MONEY" -> I18n.text("Not enough money to request a song")
            else -> I18n.format("Song request failed: %s", status)
        }
        Library.showTemporaryStatus(text, STATUS_MS)
    }

    /** 这首歌能不能发去点歌系统：音源认得出、ID 是真的（不是「显示其它播放器」那条占位歌） */
    fun canSend(track: NeteaseTrack?): Boolean {
        if (track == null) return false
        if (apiId(track.source) == null) return false
        val id = track.nativeId
        return id.isNotEmpty() && !id.startsWith(SystemMedia.PLACEHOLDER_PREFIX)
    }

    /** 把这首歌发给服务端；未连接/服务端不支持/歌本身不对，都只提示，不发包 */
    fun send(track: NeteaseTrack) {
        if (DangoClient.platform.player == null) {
            Library.showTemporaryStatus(I18n.text("Not connected to a server"), STATUS_MS)
            return
        }
        if (!supported) {
            Library.showTemporaryStatus(
                I18n.text("The server does not support this (FMusic version is too old)"),
                STATUS_MS,
            )
            return
        }
        val api = apiId(track.source)
        // 界面本来会先问 canSend，这里再挡一次：占位歌 / 空 ID 发出去服务端也只能回 INVALID_ID
        if (api == null || !canSend(track)) {
            Library.showTemporaryStatus(I18n.text("Invalid song ID"), STATUS_MS)
            return
        }
        // 服务端是异步处理的（权限、经济、拉歌信息），先给个「已发出」的反馈
        Library.showTemporaryStatus(I18n.text("Requesting song…"), STATUS_MS)
        PointSongChannel.sendRequest(api, track.nativeId)
    }

    /** 本地音源 → 服务端 FMusicApi 的音源 ID；对不上就是 null */
    private fun apiId(source: MusicSource): String? = when (source) {
        MusicSource.NETEASE -> "netapi"
        MusicSource.QQ -> "qqmusic"
        MusicSource.KUGOU -> "kugou"
    }
}
