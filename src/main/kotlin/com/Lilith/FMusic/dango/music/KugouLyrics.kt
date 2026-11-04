package com.Lilith.FMusic.dango.music

import com.Lilith.FMusic.dango.lyrics.LyricLine
import com.Lilith.FMusic.dango.lyrics.LyricWord
import com.Lilith.FMusic.dango.lyrics.LyricsProvider
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.InflaterInputStream

/** KRC has a four-byte header, repeating XOR mask and a zlib-compressed UTF-8 document. */
internal object KugouLyrics {
    private val mask = intArrayOf(0x40,0x47,0x61,0x77,0x5e,0x32,0x74,0x47,0x51,0x36,0x31,0x2d,0xce,0xd2,0x6e,0x69)
    fun decode(content: String): List<LyricLine> {
        require(content.length <= 3*1024*1024)
        val bytes = Base64.getDecoder().decode(content)
        require(bytes.size >= 4 && String(bytes,0,4,Charsets.US_ASCII) == "krc1")
        val compressed = ByteArray(bytes.size-4) { (bytes[it+4].toInt() xor mask[it%mask.size]).toByte() }
        val text = InflaterInputStream(ByteArrayInputStream(compressed)).use { input ->
            val out = ByteArrayOutputStream(); val buffer = ByteArray(4096)
            while (true) {
                val n = input.read(buffer); if (n < 0) break
                require(out.size()+n <= 2*1024*1024); out.write(buffer,0,n)
            }
            String(out.toByteArray(),Charsets.UTF_8)
        }
        return parse(text)
    }
    /**
     * KRC 的翻译在 [language:...] 里：Base64 的 JSON，content 里 type = 1 的是翻译（0 是罗马音），
     * lyricContent 按顺序对应每一句带时间的歌词。
     */
    private fun translations(text: String): List<String?> {
        val tag = text.lineSequence().map { it.trim() }.firstOrNull { it.startsWith("[language:") && it.endsWith("]") } ?: return emptyList()
        return try {
            val json = String(Base64.getDecoder().decode(tag.substring(10, tag.length - 1).trim()), Charsets.UTF_8)
            val root = com.google.gson.JsonParser().parse(json).asJsonObject
            val content = root.getAsJsonArray("content") ?: return emptyList()
            for (el in content) {
                val o = el.asJsonObject
                if (o.get("type")?.asInt != 1) continue
                val rows = o.getAsJsonArray("lyricContent") ?: continue
                return rows.map { row ->
                    if (!row.isJsonArray) null else row.asJsonArray.joinToString("") { if (it.isJsonPrimitive) it.asString else "" }
                        .takeIf { LyricsProvider.isRealTranslation(it) }
                }
            }
            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun parse(text: String): List<LyricLine> {
        val linePattern = Regex("^\\[(\\d+),(\\d+)](.*)$")
        val wordPattern = Regex("<(\\d+),(\\d+),\\d+>([^<]*)")
        val trans = translations(text)
        var index = -1
        return text.lineSequence().mapNotNull { raw ->
            val line = linePattern.matchEntire(raw.trim()) ?: return@mapNotNull null
            index++
            val tr = trans.getOrNull(index)
            val start = line.groupValues[1].toLongOrNull() ?: return@mapNotNull null
            val words = wordPattern.findAll(line.groupValues[3]).mapNotNull { word ->
                val offset = word.groupValues[1].toLongOrNull() ?: return@mapNotNull null
                val duration = word.groupValues[2].toLongOrNull() ?: return@mapNotNull null
                if (offset > Long.MAX_VALUE-start) null else LyricWord(start+offset,duration,word.groupValues[3])
            }.toList()
            val value = if (words.isEmpty()) line.groupValues[3] else words.joinToString("") { it.text }
            if (value.isBlank() || LyricsProvider.isCredit(value)) null
            else LyricLine(start, value, words.takeIf { it.isNotEmpty() }, tr?.takeIf { it.trim() != value.trim() })
        }.sortedBy { it.timeMs }.toList()
    }
}
