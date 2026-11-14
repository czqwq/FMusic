package com.Lilith.FMusic.dango.music

import com.google.gson.JsonObject
import com.Lilith.FMusic.dango.DangoLogger
import java.net.URI
import java.net.SocketTimeoutException

/** 新版按音质级别取歌；旧码率接口只作兼容，试听不能抢在完整音频前面返回。 */
internal class NeteasePlayback @JvmOverloads constructor(private val request: (String, JsonObject) -> JsonObject?, private val signedIn: Boolean = true) {
    @JvmOverloads fun resolve(id: Long, aliases: List<Long> = emptyList(), quality: AudioQuality = AudioQuality.STANDARD, active: () -> Boolean = { true }): NeteaseApi.SongUrl? {
        val accepted = (aliases + id).filter { it > 0 }.toSet()
        var unsupported = false
        var preview: NeteaseApi.SongUrl? = null
        val attempts = quality.fallbacks.map { it.neteaseLevel } + "legacy"
        for (level in attempts) {
            if (!active()) throw java.util.concurrent.CancellationException()
            val body = JsonObject().apply {
                addProperty("ids", "[$id]")
                if (level == "legacy") addProperty("br", if (quality == AudioQuality.STANDARD) 128000 else 320000)
                else { addProperty("level", level); addProperty("encodeType", "mp3") }
            }
            val root = try { request(if (level == "legacy") "legacy" else "v1", body) }
                catch (_: SocketTimeoutException) { if (level == "legacy") break else continue }
                catch (_: java.io.IOException) { DangoLogger.warn("NetEase playback stage={} transport failed", level); continue }
            if (root == null) { DangoLogger.warn("NetEase playback stage={} no response", level); continue }
            val code = root.number("code")
            DangoLogger.info("NetEase playback stage={} code={}", level, code)
            refused(code)
            if (code != 200L) continue
            val rows = root.array("data")
            val row = rows.firstOrNull { it.isJsonObject && it.asJsonObject.number("id") in accepted }?.asJsonObject
            if (row == null) {
                DangoLogger.warn("NetEase playback stage={} rows={} match=false", level, rows.size())
                continue
            }
            // 超过单曲下载/缓存上限的音频不要开始下载：跳过这一档，让后面更低音质先顶上。
            if (row.number("size") > AudioCache.MAX_AUDIO_BYTES) {
                DangoLogger.warn("NetEase playback stage={} size={} exceeds the download limit", level, row.number("size"))
                continue
            }
            val format = row.text("type").lowercase(java.util.Locale.ROOT)
            val label = if (format.isEmpty()) "unspecified" else if (format in setOf("mp3", "ogg", "vorbis", "flac", "aac", "m4a", "opus", "wav")) format else "other"
            DangoLogger.info("NetEase playback stage={} itemCode={} hasUrl={} format={} preview={}", level, row.number("code"), row.text("url").isNotEmpty(), label, row.get("freeTrialInfo")?.isJsonObject == true)
            if (row.text("url").isNotEmpty() && format.isNotEmpty() && format !in setOf("mp3", "ogg", "vorbis", "flac")) unsupported = true
            refused(row.number("code"))
            val result = parse(row) ?: continue
            if (!result.trialOnly) return result
            if (preview == null) preview = result
        }
        if (preview == null && unsupported) throw SourceAccessException("NetEase returned an audio format that this player does not support yet.")
        return preview
    }

    private fun refused(code: Long) {
        when (code) {
            301L -> if (signedIn) throw SourceAccessException("NetEase login has expired. Please log in again.")
            -462L, 405L, 429L -> throw SourceAccessException("NetEase rejected the playback request. Please retry later.")
        }
    }

    internal fun parse(row: JsonObject): NeteaseApi.SongUrl? {
        val url = row.text("url")
        val uri = try { URI(url) } catch (_: Exception) { return null }
        if (uri.scheme !in listOf("http", "https") || uri.host.isNullOrEmpty() || uri.userInfo != null) return null
        val type = row.text("type").lowercase(java.util.Locale.ROOT)
        if (type.isNotEmpty() && type !in setOf("mp3", "ogg", "vorbis", "flac")) return null
        val trial = row.get("freeTrialInfo")?.isJsonObject == true
        fun number(key: String) = row.get(key)?.takeIf { it.isJsonPrimitive }?.asString?.toFloatOrNull()?.takeIf { it.isFinite() } ?: 0f
        DangoLogger.info("NetEase playback result={} format={}", if (trial) "preview" else "full", if (type.isEmpty()) "unknown" else type)
        val quality = AudioQuality.fromAudio(type, row.number("br"))
        return NeteaseApi.SongUrl(url, trial, number("gain"), number("peak"), quality = quality)
    }
}
