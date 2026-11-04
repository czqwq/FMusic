package com.Lilith.FMusic.dango.music

import com.Lilith.FMusic.dango.lyrics.LyricLine
import com.Lilith.FMusic.dango.lyrics.LyricWord
import com.Lilith.FMusic.dango.lyrics.LyricsProvider
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.InflaterInputStream

/** QRC wire codec, implemented from the DES tables and QQ's format-specific bit ordering. */
internal object QqLyrics {
    private val ip = intArrayOf(58,50,42,34,26,18,10,2,60,52,44,36,28,20,12,4,62,54,46,38,30,22,14,6,64,56,48,40,32,24,16,8,57,49,41,33,25,17,9,1,59,51,43,35,27,19,11,3,61,53,45,37,29,21,13,5,63,55,47,39,31,23,15,7)
    private val inverse = IntArray(64).also { table -> ip.forEachIndexed { index, bit -> table[bit-1] = index+1 } }
    private val pc1 = intArrayOf(57,49,41,33,25,17,9,1,58,50,42,34,26,18,10,2,59,51,43,35,27,19,11,3,60,52,44,36,63,55,47,39,31,23,15,7,62,54,46,38,30,22,14,6,61,53,45,37,29,21,13,5,28,20,12,4)
    private val pc2 = intArrayOf(14,17,11,24,1,5,3,28,15,6,21,10,23,19,12,4,26,8,16,7,27,20,13,2,41,52,31,37,47,55,30,40,51,45,33,48,44,49,39,56,34,53,46,42,50,36,29,32)
    private val shifts = intArrayOf(1,1,2,2,2,2,2,2,1,2,2,2,2,2,2,1)
    private val expand = intArrayOf(32,1,2,3,4,5,4,5,6,7,8,9,8,9,10,11,12,13,12,13,14,15,16,17,16,17,18,19,20,21,20,21,22,23,24,25,24,25,26,27,28,29,28,29,30,31,32,1)
    private val p = intArrayOf(16,7,20,21,29,12,28,17,1,15,23,26,5,18,31,10,2,8,24,14,32,27,3,9,19,13,30,6,22,11,4,25)
    // QRC retains nonstandard values at S2[23] and S4[53]. Standard DESede cannot decode it.
    private val boxes = arrayOf(
        intArrayOf(14,4,13,1,2,15,11,8,3,10,6,12,5,9,0,7,0,15,7,4,14,2,13,1,10,6,12,11,9,5,3,8,4,1,14,8,13,6,2,11,15,12,9,7,3,10,5,0,15,12,8,2,4,9,1,7,5,11,3,14,10,0,6,13),
        intArrayOf(15,1,8,14,6,11,3,4,9,7,2,13,12,0,5,10,3,13,4,7,15,2,8,15,12,0,1,10,6,9,11,5,0,14,7,11,10,4,13,1,5,8,12,6,9,3,2,15,13,8,10,1,3,15,4,2,11,6,7,12,0,5,14,9),
        intArrayOf(10,0,9,14,6,3,15,5,1,13,12,7,11,4,2,8,13,7,0,9,3,4,6,10,2,8,5,14,12,11,15,1,13,6,4,9,8,15,3,0,11,1,2,12,5,10,14,7,1,10,13,0,6,9,8,7,4,15,14,3,11,5,2,12),
        intArrayOf(7,13,14,3,0,6,9,10,1,2,8,5,11,12,4,15,13,8,11,5,6,15,0,3,4,7,2,12,1,10,14,9,10,6,9,0,12,11,7,13,15,1,3,14,5,2,8,4,3,15,0,6,10,10,13,8,9,4,5,11,12,7,2,14),
        intArrayOf(2,12,4,1,7,10,11,6,8,5,3,15,13,0,14,9,14,11,2,12,4,7,13,1,5,0,15,10,3,9,8,6,4,2,1,11,10,13,7,8,15,9,12,5,6,3,0,14,11,8,12,7,1,14,2,13,6,15,0,9,10,4,5,3),
        intArrayOf(12,1,10,15,9,2,6,8,0,13,3,4,14,7,5,11,10,15,4,2,7,12,9,5,6,1,13,14,0,11,3,8,9,14,15,5,2,8,12,3,7,0,4,10,1,13,11,6,4,3,2,12,9,5,15,10,11,14,1,7,6,0,8,13),
        intArrayOf(4,11,2,14,15,0,8,13,3,12,9,7,5,10,6,1,13,0,11,7,4,9,1,10,14,3,5,12,2,15,8,6,1,4,11,13,12,3,7,14,10,15,6,8,0,5,9,2,6,11,13,8,1,4,10,7,9,5,0,15,14,2,3,12),
        intArrayOf(13,2,8,4,6,15,11,1,10,9,3,14,5,0,12,7,1,15,13,8,10,3,7,4,12,5,6,11,0,14,9,2,7,11,4,1,9,12,14,2,0,6,10,13,15,3,5,8,2,1,14,7,4,10,8,13,15,12,9,0,3,5,6,11))
    private fun permute(value: Long, width: Int, table: IntArray): Long {
        var result = 0L
        for (bit in table) result = (result shl 1) or ((value ushr (width-bit)) and 1)
        return result
    }
    private fun read(data: ByteArray, offset: Int): Long {
        var result = 0L
        for (index in intArrayOf(3,2,1,0,7,6,5,4)) result = (result shl 8) or (data[offset+index].toLong() and 255)
        return result
    }
    private fun write(value: Long, target: ByteArray, offset: Int) {
        for ((index, at) in intArrayOf(3,2,1,0,7,6,5,4).withIndex()) target[offset+at] = (value ushr (56-index*8)).toByte()
    }
    private fun keys(key: ByteArray, offset: Int, reverse: Boolean): LongArray {
        val bits = permute(read(key, offset), 64, pc1)
        var left = bits ushr 28
        var right = bits and 0xfffffff
        val keys = LongArray(16)
        for (round in 0..15) {
            val shift = shifts[round]
            left = ((left shl shift) or (left ushr (28-shift))) and 0xfffffff
            right = ((right shl shift) or (right ushr (28-shift))) and 0xfffffff
            // QQ's PC2 right-half indices are displaced by one bit; the final bit is zero.
            val combined = (left shl 28) or ((right shl 1) and 0xfffffff)
            keys[if (reverse) 15-round else round] = permute(combined, 56, pc2)
        }
        return keys
    }
    private val schedules = "!@#)(*$%123ZXC!@!@#)(NHL".toByteArray(Charsets.US_ASCII).let { key ->
        arrayOf(keys(key,16,true), keys(key,8,false), keys(key,0,true))
    }
    private fun des(value: Long, keys: LongArray): Long {
        val initial = permute(value,64,ip)
        var left = initial ushr 32
        var right = initial and 0xffffffffL
        for (key in keys) {
            val input = permute(right,32,expand) xor key
            var substituted = 0L
            for (box in 0..7) {
                val six = ((input ushr (42-box*6)) and 63).toInt()
                val row = ((six and 32) ushr 4) or (six and 1)
                substituted = (substituted shl 4) or boxes[box][row*16+((six ushr 1) and 15)].toLong()
            }
            val next = left xor permute(substituted,32,p)
            left = right; right = next
        }
        return permute((right shl 32) or left,64,inverse)
    }

    fun plaintext(value: String): String {
        if (value.isEmpty() || value.length > 2*1024*1024) return ""
        if (value.trimStart().startsWith("<") || value.trimStart().startsWith("[")) return value
        require(value.length % 2 == 0 && value.all { it in "0123456789abcdefABCDEF" })
        val data = ByteArray(value.length/2) { value.substring(it*2,it*2+2).toInt(16).toByte() }
        val decoded = if (data.size % 8 == 0) ByteArray(data.size).also { output ->
            for (offset in data.indices step 8) {
                var block = read(data,offset)
                for (schedule in schedules) block = des(block,schedule)
                write(block,output,offset)
            }
        } else data
        // Empty/instrumental tracks may arrive as plain zlib rather than encrypted QRC.
        return try { inflate(decoded) } catch (_: Exception) { inflate(data) }
    }
    private fun inflate(data: ByteArray): String = InflaterInputStream(ByteArrayInputStream(data)).use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size()+count <= 2*1024*1024)
            output.write(buffer,0,count)
        }
        String(output.toByteArray(), Charsets.UTF_8)
    }
    fun decode(value: String): List<LyricLine> = parse(plaintext(value))
    fun parse(xml: String): List<LyricLine> {
        val attr = Regex("LyricContent\\s*=\\s*\"([\\s\\S]*?)\"").find(xml)?.groupValues?.get(1) ?: xml
        val content = attr.replace("&#10;", "\n").replace("&#13;", "\r").replace("&quot;", "\"")
            .replace("&apos;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
        val linePattern = Regex("^\\[(\\d+),(\\d+)](.*)$")
        val wordPattern = Regex("(.*?)\\((\\d+),(\\d+)\\)")
        val lines = content.lineSequence().mapNotNull { raw ->
            val line = linePattern.matchEntire(raw.trim()) ?: return@mapNotNull null
            val start = line.groupValues[1].toLongOrNull() ?: return@mapNotNull null
            val body = line.groupValues[3]
            val words = wordPattern.findAll(body).mapNotNull { match ->
                val at = match.groupValues[2].toLongOrNull() ?: return@mapNotNull null
                val duration = match.groupValues[3].toLongOrNull() ?: return@mapNotNull null
                LyricWord(at, duration, match.groupValues[1])
            }.toList()
            val text = if (words.isNotEmpty()) words.joinToString("") { it.text } else body
            if (text.isBlank() || LyricsProvider.isCredit(text)) null else LyricLine(start,text,words.takeIf { it.isNotEmpty() })
        }.sortedBy { it.timeMs }.toList()
        return lines.ifEmpty { LyricsProvider.parseLrc(content) }
    }
}
