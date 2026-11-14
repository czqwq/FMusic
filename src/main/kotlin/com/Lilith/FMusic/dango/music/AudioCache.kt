package com.Lilith.FMusic.dango.music

import com.Lilith.FMusic.dango.DangoLogger
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.FileTime
import java.security.MessageDigest

internal class AudioCache(
    private val directory: Path,
    private val maxBytes: Long = 1024L * 1024 * 1024,
    private val maxAgeMs: Long = 7L * 24 * 60 * 60 * 1000,
) {
    class Entry(val bytes: ByteArray, val info: NeteaseApi.SongUrl)

    @Synchronized fun read(identity: String, quality: AudioQuality, active: () -> Boolean): Entry? {
        if (!quality.cacheEnabled || !active()) return null
        // 缓存文件按“实际音质”命名（写的时候就用实际档位），所以请求的档位没命中时，
        // 可以复用相邻档位已有的缓存；命中的档位就是当时的真实音质，不会冒充所选档位。
        for (tier in readTiers(quality)) {
            if (!active()) return null
            readTier(identity, tier, active, refreshLastUse = tier == quality)?.let { return it }
        }
        return null
    }

    private fun readTiers(quality: AudioQuality): List<AudioQuality> =
        (quality.fallbacks + AudioQuality.values().filter { it.cacheEnabled && it.ordinal > quality.ordinal })
            .filter { it.cacheEnabled }
            .distinct()

    /**
     * @param refreshLastUse 只有命中“请求档位”时才刷新最后使用时间。复用较低档位时不刷新，
     *                       这样它最多 7 天后会过期，重新尝试用户真正想要的档位（升级会员、补版权等）。
     */
    private fun readTier(identity: String, quality: AudioQuality, active: () -> Boolean, refreshLastUse: Boolean): Entry? {
        val file = path(identity, quality)
        return try {
            if (!Files.isRegularFile(file, NOFOLLOW_LINKS)) return null
            val size = Files.size(file)
            if (size !in 49L..(MAX_AUDIO_BYTES.toLong() + 48) || expired(file)) {
                Files.deleteIfExists(file)
                return null
            }
            val entry = DataInputStream(Files.newInputStream(file).buffered()).use { input ->
                check(input.readInt() == MAGIC)
                val gain = input.readFloat()
                val peak = input.readFloat()
                check(gain.isFinite() && peak.isFinite())
                val length = input.readInt()
                check(length in 1..MAX_AUDIO_BYTES && size == length.toLong() + 48)
                val hash = ByteArray(32).also(input::readFully)
                val bytes = ByteArray(length).also(input::readFully)
                check(MessageDigest.isEqual(hash, digest(bytes)))
                check(Mp3Probe.decodable(bytes))
                Entry(bytes, NeteaseApi.SongUrl("", false, gain, peak, quality = quality))
            }
            if (!active()) return null
            if (refreshLastUse) Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis()))
            entry
        } catch (e: Exception) {
            // 只有“内容确实损坏”才删除；临时性的 IO 失败（磁盘忙、文件被占用）留着下次再试。
            if (e is IllegalStateException || e is java.io.EOFException) {
                try { Files.deleteIfExists(file) } catch (_: Exception) {}
            }
            DangoLogger.warn("Audio cache read failed: {}", e.javaClass.simpleName)
            null
        }
    }

    @Synchronized fun write(identity: String, info: NeteaseApi.SongUrl, bytes: ByteArray, active: () -> Boolean) {
        if (!info.quality.cacheEnabled || info.trialOnly || info.replaced || !active() ||
            bytes.isEmpty() || bytes.size > MAX_AUDIO_BYTES || bytes.size.toLong() + 48 > maxBytes ||
            !info.gainDb.isFinite() || !info.peak.isFinite()) return
        var temporary: Path? = null
        try {
            // 只做一次轻量解码探测；整首解码留给播放线程，别为了写缓存把首播拖慢好几秒。
            if (!Mp3Probe.decodable(bytes)) return
            Files.createDirectories(directory)
            prune(bytes.size.toLong() + 48)
            temporary = Files.createTempFile(directory, "audio-", ".part")
            DataOutputStream(Files.newOutputStream(temporary).buffered()).use { output ->
                output.writeInt(MAGIC)
                output.writeFloat(info.gainDb)
                output.writeFloat(info.peak)
                output.writeInt(bytes.size)
                output.write(digest(bytes))
                output.write(bytes)
            }
            if (!active()) return
            val target = path(identity, info.quality)
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: Exception) {
            DangoLogger.warn("Audio cache write failed: {}", e.javaClass.simpleName)
        } finally {
            temporary?.let { try { Files.deleteIfExists(it) } catch (_: Exception) {} }
        }
    }

    @Synchronized fun usage(): Pair<Long, Int> {
        if (!Files.exists(directory)) {
            var parent = directory.toAbsolutePath().parent
            while (parent != null && !Files.exists(parent)) parent = parent.parent
            check(parent != null && Files.isDirectory(parent) && Files.isWritable(parent))
            return 0L to 0
        }
        check(Files.isDirectory(directory) && Files.isReadable(directory) && Files.isWritable(directory))
        var bytes = 0L
        var count = 0
        Files.newDirectoryStream(directory).use { files ->
            for (file in files) if (owned(file)) {
                bytes += Files.size(file)
                count++
            }
        }
        return bytes to count
    }

    @Synchronized fun clear(): Boolean {
        if (!Files.exists(directory)) return true
        var success = true
        Files.newDirectoryStream(directory).use { files ->
            for (file in files) if (owned(file)) {
                try { Files.deleteIfExists(file) } catch (_: Exception) { success = false }
            }
        }
        return success
    }

    private fun owned(file: Path): Boolean {
        if (!Files.isRegularFile(file, NOFOLLOW_LINKS)) return false
        val name = file.fileName.toString()
        if (!name.matches(Regex("[a-f0-9]{64}\\.audio")) && !name.matches(Regex("audio-[0-9]+\\.part"))) return false
        return try { DataInputStream(Files.newInputStream(file)).use { it.readInt() == MAGIC } } catch (_: Exception) { false }
    }

    private fun path(identity: String, quality: AudioQuality): Path {
        val hash = digest("v1\u0000$identity\u0000${quality.key}".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        return directory.resolve("$hash.audio")
    }

    private fun expired(file: Path): Boolean =
        System.currentTimeMillis() - Files.getLastModifiedTime(file, NOFOLLOW_LINKS).toMillis() > maxAgeMs

    private fun prune(incoming: Long) {
        val entries = ArrayList<Path>()
        Files.newDirectoryStream(directory).use { files ->
            for (file in files) {
                if (!owned(file)) continue
                val name = file.fileName.toString()
                if (name.matches(Regex("[a-f0-9]{64}\\.audio"))) {
                    if (expired(file)) Files.deleteIfExists(file) else entries.add(file)
                } else if (name.startsWith("audio-") && name.endsWith(".part") && expired(file)) {
                    Files.deleteIfExists(file)
                }
            }
        }
        var size = entries.sumOf { Files.size(it) }
        for (file in entries.sortedBy { Files.getLastModifiedTime(it, NOFOLLOW_LINKS).toMillis() }) {
            if (size + incoming <= maxBytes) break
            val length = Files.size(file)
            if (Files.deleteIfExists(file)) size -= length
        }
    }

    companion object {
        const val MAX_AUDIO_BYTES = 128 * 1024 * 1024
        private const val MAGIC = 0x464D4101
        private fun digest(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)
    }
}
