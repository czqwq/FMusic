package com.Lilith.FMusic.dango.music

import com.Lilith.FMusic.dango.DangoMusic
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

internal object MusicCache {
    data class Status(val directory: String = "", val bytes: Long? = null, val files: Int = 0, val message: String = "", val busy: Boolean = false)
    private class Location(val directory: Path, val cache: AudioCache)
    class Access internal constructor(private val cache: AudioCache, private val valid: () -> Boolean) {
        fun read(identity: String, quality: AudioQuality, active: () -> Boolean): AudioCache.Entry? =
            cache.read(identity, quality) { valid() && active() }
        fun write(identity: String, info: NeteaseApi.SongUrl, bytes: ByteArray, active: () -> Boolean) =
            cache.write(identity, info, bytes) { valid() && active() }
    }

    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "Dango-AudioCache").apply { isDaemon = true } }
    private val revision = AtomicLong()
    private var configured: String? = null
    private var location: Location? = null
    private var nextRefresh = 0L
    @Volatile var status = Status()
        private set

    private fun resolveDirectory(value: String): Path {
        if (value.isBlank()) return DangoMusic.instance.clientDir.resolve("audio_cache").toAbsolutePath().normalize()
        val path = Paths.get(value.trim())
        require(path.isAbsolute || path.root == null)
        return if (path.isAbsolute) path.normalize()
            else DangoMusic.instance.platform.gameDir.toAbsolutePath().resolve(path).normalize()
    }

    fun directoryText(value: String): String {
        val directory = resolveDirectory(value)
        val base = DangoMusic.instance.platform.gameDir.toAbsolutePath().normalize()
        val path = if (directory.root == base.root) base.relativize(directory) else directory
        return path.toString().replace(java.io.File.separatorChar, '/').ifEmpty { "." }
    }

    @Synchronized fun configure(value: String) {
        if (configured == value) return
        configured = value
        revision.incrementAndGet()
        location = null
        nextRefresh = 0L
        try {
            val directory = resolveDirectory(value)
            location = Location(directory, AudioCache(directory))
            status = Status(directory.toString())
            refresh()
        } catch (_: Exception) {
            status = Status(value, message = "Invalid cache directory")
        }
    }

    @Synchronized fun access(): Access? {
        if (configured == null) configure("")
        val current = location ?: return null
        if (status.busy) return null
        val version = revision.get()
        return Access(current.cache) { revision.get() == version }
    }

    @Synchronized fun refresh() {
        val current = location ?: return
        val now = System.currentTimeMillis()
        if (status.busy || now < nextRefresh) return
        nextRefresh = now + 2000L
        val version = revision.get()
        worker.execute { updateUsage(current, version) }
    }

    @Synchronized fun clear() {
        val current = location ?: return
        if (status.busy) return
        val version = revision.incrementAndGet()
        status = status.copy(busy = true, message = "Clearing cache...")
        worker.execute {
            val success = try { current.cache.clear() } catch (_: Exception) { false }
            updateUsage(current, version, if (success) "Cache cleared" else "Some cache files could not be deleted")
        }
    }

    private fun updateUsage(current: Location, version: Long, message: String? = null) {
        val usage = try { current.cache.usage() } catch (_: Exception) { null }
        synchronized(this) {
            if (location !== current || revision.get() != version) return
            status = Status(current.directory.toString(), usage?.first, usage?.second ?: 0,
                if (usage == null) "Cache unavailable; check the directory and permissions" else message ?: status.message,
                busy = false)
            if (usage != null && message == null && status.message == "Cache unavailable; check the directory and permissions")
                status = status.copy(message = "")
        }
    }
}
