package com.Lilith.FMusic.dango.music

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Guest-only HTTP transport. No browser cookies, login tokens, or third-party API services. */
internal object SourceHttp {
    fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    fun json(url: String, referer: String): JsonObject {
        val bytes = bytes(url, referer, 4 * 1024 * 1024) ?: throw IllegalStateException("Source request failed")
        val text = String(bytes, Charsets.UTF_8).trim()
        // Some web endpoints return JSONP even when format=json was requested.
        val json = if (text.startsWith("{")) text else text.substringAfter('(', "").substringBeforeLast(')', "")
        return JsonParser().parse(json).asJsonObject
    }

    fun bytes(url: String, referer: String, maxBytes: Int = 64 * 1024 * 1024): ByteArray? {
        var current = URL(url)
        repeat(6) {
            require(current.protocol == "https" || current.protocol == "http")
            val conn = current.openConnection() as HttpURLConnection
            try {
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 8000
                conn.readTimeout = 15000
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                conn.setRequestProperty("Referer", referer)
                conn.setRequestProperty("Accept-Encoding", "identity")
                val code = conn.responseCode
                if (code in 300..399) {
                    current = URL(current, conn.getHeaderField("Location") ?: return null)
                } else {
                    if (code !in 200..299) return null
                    return conn.inputStream.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(16384)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            if (output.size() + count > maxBytes) return null
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                }
            } finally { conn.disconnect() }
        }
        return null
    }
}

internal fun JsonObject.text(key: String): String = get(key)?.takeIf { it.isJsonPrimitive }?.asString ?: ""
internal fun JsonObject.number(key: String): Long = text(key).toLongOrNull() ?: 0L
internal fun cleanSourceText(value: String): String = value.replace(Regex("<[^>]*>"), "")
    .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
