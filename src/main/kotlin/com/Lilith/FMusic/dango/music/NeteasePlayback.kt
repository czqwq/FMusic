package com.Lilith.FMusic.dango.music

import com.google.gson.JsonObject
import com.Lilith.FMusic.dango.DangoLogger
import java.net.URI
import java.net.SocketTimeoutException

/**
 * 按音质档位取歌：从请求的档位开始逐档往下降，直到拿到能播放的完整音频。
 * 旧码率接口只作兼容兜底；试听不能抢在完整音频前面返回。
 */
internal class NeteasePlayback @JvmOverloads constructor(
    private val request: (String, JsonObject) -> JsonObject?,
    private val signedIn: Boolean = true,
) {
    @JvmOverloads
    fun resolve(
        id: Long,
        aliases: List<Long> = emptyList(),
        quality: NeteaseQuality = NeteaseQuality.LOSSLESS,
        active: () -> Boolean = { true },
    ): NeteaseApi.SongUrl? {
        val accepted = (aliases + id).filter { it > 0 }.toSet()
        var unsupported = false
        var preview: NeteaseApi.SongUrl? = null
        // 请求档位 → 标准档逐档回退，最后再试一次老码率接口
        val tiers = NeteaseQuality.values().take(quality.ordinal + 1).asReversed()
        for (tier in tiers) {
            if (!active()) throw java.util.concurrent.CancellationException()
            val body = JsonObject().apply {
                addProperty("ids", "[$id]")
                addProperty("level", tier.level)
                addProperty("encodeType", tier.encodeType)
            }
            val root = try {
                request("v1", body)
            } catch (_: SocketTimeoutException) {
                continue
            } catch (_: java.io.IOException) {
                DangoLogger.warn("NetEase playback stage={} transport failed", tier.level)
                continue
            }
            if (root == null) {
                DangoLogger.warn("NetEase playback stage={} no response", tier.level)
                continue
            }
            val code = root.number("code")
            DangoLogger.info("NetEase playback stage={} code={}", tier.level, code)
            refused(code)
            if (code != 200L) continue
            val rows = root.array("data")
            val row = rows.firstOrNull { it.isJsonObject && it.asJsonObject.number("id") in accepted }?.asJsonObject
            if (row == null) {
                DangoLogger.warn("NetEase playback stage={} rows={} match=false", tier.level, rows.size())
                continue
            }
            // 超过单曲下载/缓存上限的音频不要开始下载：跳过这一档，让后面更低音质先顶上。
            if (row.number("size") > AudioCache.MAX_AUDIO_BYTES) {
                DangoLogger.warn("NetEase playback stage={} size={} exceeds the download limit", tier.level, row.number("size"))
                continue
            }
            val format = row.text("type").lowercase(java.util.Locale.ROOT)
            val label = if (format.isEmpty()) "unspecified" else if (format in setOf("mp3", "ogg", "vorbis", "flac", "aac", "m4a", "opus", "wav")) format else "other"
            DangoLogger.info("NetEase playback stage={} itemCode={} hasUrl={} format={} preview={}", tier.level, row.number("code"), row.text("url").isNotEmpty(), label, row.get("freeTrialInfo")?.isJsonObject == true)
            if (row.text("url").isNotEmpty() && format.isNotEmpty() && format !in setOf("mp3", "ogg", "vorbis", "flac")) unsupported = true
            refused(row.number("code"))
            val result = parse(row) ?: continue
            if (!result.trialOnly) return result
            if (preview == null) preview = result
        }
        // 所有档位都没拿到完整音频：最后试一次老码率接口（只给 mp3 的旧接口，兼容用；
        // 试听结果不能抢在它前面返回，这里它有可能直接给完整音频）
        if (active()) {
            val body = JsonObject().apply {
                addProperty("ids", "[$id]")
                addProperty("br", quality.legacyBitrate)
            }
            val root = try {
                request("legacy", body)
            } catch (_: SocketTimeoutException) {
                null
            } catch (_: java.io.IOException) {
                DangoLogger.warn("NetEase playback legacy transport failed")
                null
            }
            if (root != null) {
                val code = root.number("code")
                DangoLogger.info("NetEase playback stage=legacy code={}", code)
                refused(code)
                if (code == 200L) {
                    val rows = root.array("data")
                    val row = rows.firstOrNull { it.isJsonObject && it.asJsonObject.number("id") in accepted }?.asJsonObject
                    if (row != null && row.number("size") <= AudioCache.MAX_AUDIO_BYTES) {
                        val format = row.text("type").lowercase(java.util.Locale.ROOT)
                        if (row.text("url").isNotEmpty() && format.isNotEmpty() && format !in setOf("mp3", "ogg", "vorbis", "flac")) unsupported = true
                        refused(row.number("code"))
                        parse(row)?.let { if (it.trialOnly) preview = preview ?: it else return it }
                    }
                }
            }
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
        val actual = AudioQuality.fromAudio(type, row.number("br"))
        val requestedLevel = row.text("level")
        DangoLogger.info(
            "NetEase playback result={} requested={} actual={} bitrate={} format={}",
            if (trial) "preview" else "full", requestedLevel.ifEmpty { "unknown" }, actual.key, row.number("br"), type.ifEmpty { "unknown" },
        )
        return NeteaseApi.SongUrl(url, trial, number("gain"), number("peak"), quality = actual)
    }
}
