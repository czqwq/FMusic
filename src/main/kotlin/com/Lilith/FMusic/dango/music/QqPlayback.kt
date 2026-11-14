package com.Lilith.FMusic.dango.music

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.Lilith.FMusic.dango.DangoLogger
import java.net.CookieManager
import java.net.URI
import java.net.URL
import java.util.UUID

/** Desktop App sessions use UrlGetVkey; the older web endpoint is a compatibility fallback. */
internal class QqPlayback(private val transport: (JsonObject, NativeAccount?) -> JsonObject = { request, account ->
    AuthHttp.request(MusicSource.QQ, "https://u.y.qq.com/cgi-bin/musicu.fcg", account?.cookies ?: CookieManager(),
        request.toString(), "application/json").json()
}) {
    private class Reply(val code: Long, val data: JsonObject?)
    private val guid = (UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE).toString().take(10)

    fun resolve(track: NeteaseTrack, account: NativeAccount?, quality: AudioQuality = AudioQuality.STANDARD): NeteaseApi.SongUrl? {
        require(track.source == MusicSource.QQ && validMid(track.nativeId) && validMid(track.mediaId))
        var media = track.mediaId
        // Older search responses sometimes omit media MID. Resolve metadata rather than
        // silently requesting a non-existent M500<song MID> file.
        if (media == track.nativeId) media = metadata(track, account) ?: media
        val replies = mutableListOf<Reply>()
        val filenames = quality.fallbacks.associate { level ->
            val (prefix, extension) = level.qqFile
            "$prefix$media.$extension" to level
        }
        var best: NeteaseApi.SongUrl? = null
        for (desktop in listOf(true, false)) {
            val params = JsonObject().apply {
                addProperty("guid", guid); addProperty("uin", account?.userId ?: "0"); addProperty("ctx", 0)
                if (!desktop) { addProperty("loginflag", 1); addProperty("platform", "20") }
                add("filename", JsonArray().apply { for (filename in filenames.keys) add(JsonPrimitive(filename)) })
                add("songmid", JsonArray().apply { repeat(filenames.size) { add(JsonPrimitive(track.nativeId)) } })
                add("songtype", JsonArray().apply { repeat(filenames.size) { add(JsonPrimitive(0)) } })
            }
            val reply = call(if (desktop) "desktop" else "web",
                if (desktop) "music.vkey.GetVkey" else "vkey.GetVkeyServer",
                if (desktop) "UrlGetVkey" else "CgiGetVkey", params, account, desktop)
            replies += reply
            if (reply.code == 0L) reply.data?.let { parse(it, filenames) }?.let { result ->
                if (result.quality == quality) return result
                if (best == null || result.quality.ordinal > best!!.quality.ordinal) best = result
            }
        }
        best?.let { return it }
        if (replies.all { it.code == -3L }) throw SourceAccessException("QQ Music playback request failed. Please retry.")
        if (account == null) return null
        // An empty purl alone says nothing about session expiry. Check it separately
        // before advising a player who has already completed the phone login to rescan.
        val verified = call("session", "music.UserInfo.userInfoServer", "GetLoginUserInfo", JsonObject(), account, true)
        if (verified.code in setOf(1000L,104400L,104401L))
            throw SourceAccessException("QQ Music login is no longer valid. Please log in again.")
        throw SourceAccessException("QQ Music did not return playable audio for this song. Try another version.")
    }

    private fun metadata(track: NeteaseTrack, account: NativeAccount?): String? {
        val reply = call("metadata", "music.pf_song_detail_svr", "get_song_detail_yqq",
            JsonObject().apply { addProperty("song_mid", track.nativeId) }, account, false)
        return if (reply.code == 0L) reply.data?.obj("track_info")?.obj("file")?.text("media_mid")?.takeIf(::validMid) else null
    }

    private fun call(phase: String, module: String, method: String, params: JsonObject, account: NativeAccount?, desktop: Boolean): Reply {
        val request = JsonObject().apply {
            add("comm", JsonObject().apply {
                addProperty("ct", if (desktop) 19 else 24); addProperty("cv", if (desktop) 2201 else 0)
                if (account != null) {
                    val key = NativeAccounts.cookie(account.cookies,"qqmusic_key")
                    addProperty("uin",account.userId); addProperty("authst",key)
                    addProperty("g_tk",NativeAccounts.hash33(key)); addProperty("tmeLoginType",account.loginType)
                }
            })
            add("req_0",JsonObject().apply { addProperty("module",module); addProperty("method",method); add("param",params) })
        }
        val root = try { transport(request,account) } catch (_: Exception) {
            DangoLogger.warn("QQ Music playback phase={} outcome=network-error",phase)
            return Reply(-3,null)
        }
        val block = root.obj("req_0")
        val code = when {
            !root.has("code") -> -1L
            root.number("code") != 0L -> root.number("code")
            block == null || !block.has("code") -> -1L
            else -> block.number("code")
        }
        val data = block?.obj("data")
        // Only protocol phase, numeric result codes and availability: no credentials or URLs.
        DangoLogger.info("QQ Music playback phase={} code={} media-result={}",phase,code,
            data?.array("midurlinfo")?.firstOrNull()?.takeIf { it.isJsonObject }?.asJsonObject?.number("result") ?: -1L)
        return Reply(code,data)
    }

    internal fun parse(data: JsonObject, filenames: Map<String, AudioQuality> = emptyMap()): NeteaseApi.SongUrl? {
        val hosts = data.array("sip").mapNotNull { it.takeIf { it.isJsonPrimitive }?.asString }
        val items = data.array("midurlinfo").filter { it.isJsonObject }.sortedBy { item ->
            val row = item.asJsonObject
            val filename = row.text("filename").ifEmpty { row.text("purl").substringBefore('?').substringAfterLast('/') }
            filenames.keys.indexOf(filename).takeIf { it >= 0 } ?: Int.MAX_VALUE
        }
        for (item in items) {
            val row = item.takeIf { it.isJsonObject }?.asJsonObject ?: continue
            if (row.has("result") && row.number("result") != 0L) continue
            val path = row.text("purl")
            if (path.isEmpty()) continue
            val candidates = if (path.startsWith("https://") || path.startsWith("http://")) listOf(path)
                else (hosts.sortedBy { if (it.startsWith("https://")) 0 else 1 } + "https://isure.stream.qqmusic.qq.com/")
                    .mapNotNull { host -> try { URL(URL(host),path).toString() } catch (_: Exception) { null } }
            for (candidate in candidates) {
                val uri = try { URI(candidate) } catch (_: Exception) { continue }
                if (uri.scheme !in listOf("https","http") || uri.host.isNullOrEmpty() || uri.userInfo != null ||
                    !listOf(".mp3", ".flac").any { uri.path.orEmpty().endsWith(it, true) }) continue
                val filename = row.text("filename").ifEmpty { uri.path.orEmpty().substringAfterLast('/') }
                if (filenames.isNotEmpty() && filename !in filenames) continue
                val level = filenames[filename] ?: when {
                    filename.startsWith("F000") -> AudioQuality.LOSSLESS
                    filename.startsWith("M800") -> AudioQuality.HIGH
                    else -> AudioQuality.STANDARD
                }
                return NeteaseApi.SongUrl(candidate, false, quality = level)
            }
        }
        return null
    }
    private fun validMid(mid: String) = mid.matches(Regex("[A-Za-z0-9]{1,64}"))
}
