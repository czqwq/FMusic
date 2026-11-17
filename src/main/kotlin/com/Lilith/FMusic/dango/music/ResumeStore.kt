package com.Lilith.FMusic.dango.music

import com.google.gson.Gson
import com.Lilith.FMusic.dango.DangoLogger
import com.Lilith.FMusic.dango.DangoMusic

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.Executors

/**
 * 记住上次的播放队列、当前曲目和进度，下次进游戏恢复成「暂停在原处」，
 * 按播放键才开始下载和出声（和桌面音乐播放器的续播一致）。
 *
 * 队列只在换了一份队列时写（歌单可能有几千首，只存当前曲目前后一段）；
 * 进度文件很小，换歌、暂停、拖动、播放中每 10 秒、退出游戏时各写一次。
 * 主动按停止会清掉进度，下次就不恢复。
 */
object ResumeStore {

    private const val VERSION = 1
    /** 太长的歌单只存当前曲目前后这一段，恢复后照样能上一首 / 下一首 */
    private const val MAX_TRACKS = 5000
    /** 暂停在原处太久再启动游戏，恢复的价值不大；超过这个时间就不恢复了 */
    private const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000

    /** 存盘用的歌曲字段集合：只保留重新取流需要的，封面、时长这种可以重新拿 */
    private class StoredTrack(
        val id: Long = 0L,
        val name: String = "",
        val artists: List<String> = emptyList(),
        val durationMs: Long = 0L,
        val coverUrl: String = "",
        val source: String = "",
        val nativeId: String = "",
        val mediaId: String = "",
        val albumId: String = "",
        val albumAudioId: String = "",
        val cloud: Boolean = false,
        val cloudId: Long = 0L,
        val sourceSongId: Long = 0L,
        val artistIds: List<String> = emptyList(),
    ) {
        fun toTrack(): NeteaseTrack? {
            val kind = MusicSource.values().firstOrNull { it.key == source } ?: return null
            if (name.isEmpty()) return null
            return NeteaseTrack(
                id, name, artists, durationMs, coverUrl, kind,
                nativeId.ifEmpty { id.toString() }, mediaId.ifEmpty { nativeId.ifEmpty { id.toString() } }, albumId,
            ).apply {
                albumAudioId = this@StoredTrack.albumAudioId
                cloud = this@StoredTrack.cloud
                cloudId = this@StoredTrack.cloudId
                sourceSongId = this@StoredTrack.sourceSongId
                artistIds = this@StoredTrack.artistIds
            }
        }

        companion object {
            fun from(track: NeteaseTrack) = StoredTrack(
                id = track.id,
                name = track.name,
                artists = track.artists,
                durationMs = track.durationMs,
                coverUrl = track.coverUrl,
                source = track.source.key,
                nativeId = track.nativeId,
                mediaId = track.mediaId,
                albumId = track.albumId,
                albumAudioId = track.albumAudioId,
                cloud = track.cloud,
                cloudId = track.cloudId,
                sourceSongId = track.sourceSongId,
                artistIds = track.artistIds,
            )
        }
    }

    private class QueueFile(val version: Int = VERSION, val tracks: List<StoredTrack> = emptyList())

    private class StateFile(
        val version: Int = VERSION,
        val index: Int = -1,
        val key: String = "",
        val positionMs: Long = 0L,
        val savedAtMs: Long = 0L,
    )

    class Session(val tracks: List<NeteaseTrack>, val index: Int, val positionMs: Long)

    private val gson = Gson()
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "Dango-Resume").apply { isDaemon = true } }

    /** 设置里「记住上次播放」开着、并且模块启用时才写盘 / 恢复 */
    @Volatile var enabled = false

    /** 上次写进文件的那份队列（按引用比较）：同一份队列不重复写 */
    @Volatile private var writtenQueue: List<NeteaseTrack>? = null
    @Volatile private var writtenOffset = 0
    @Volatile private var writtenCount = 0

    private fun directory(): Path? = try {
        DangoMusic.instance.clientDir
    } catch (_: Throwable) {
        null
    }

    private fun queueFile(): Path? = directory()?.resolve("resume-queue.json")
    private fun stateFile(): Path? = directory()?.resolve("resume-state.json")

    /** 写入当前会话；[sync] = true 时同步落盘（退出游戏时用） */
    @Synchronized
    fun save(queue: List<NeteaseTrack>, index: Int, positionMs: Long, sync: Boolean = false) {
        if (!enabled) return
        if (queue.isEmpty() || index !in queue.indices) return
        val current = queue[index]
        if (current.name.isEmpty()) return
        val window = window(queue.size, index)
        if (writtenQueue !== queue || index - writtenOffset !in 0 until writtenCount) {
            writtenQueue = queue
            writtenOffset = window.first
            writtenCount = window.last - window.first + 1
            val tracks = queue.subList(window.first, window.last + 1).map(StoredTrack::from)
            write(queueFile(), gson.toJson(QueueFile(tracks = tracks)), sync)
        }
        val local = index - writtenOffset
        val state = StateFile(index = local, key = current.key, positionMs = positionMs.coerceAtLeast(0L), savedAtMs = System.currentTimeMillis())
        write(stateFile(), gson.toJson(state), sync)
    }

    /** 恢复后这份队列就是文件里那份，不用马上重写 */
    @Synchronized
    fun adopt(queue: List<NeteaseTrack>) {
        writtenQueue = queue
        writtenOffset = 0
        writtenCount = queue.size
    }

    /** 用户主动停止：清掉进度，下次进游戏不恢复 */
    fun clear() {
        write(stateFile(), gson.toJson(StateFile(savedAtMs = System.currentTimeMillis())))
    }

    fun load(): Session? = try {
        val queuePath = queueFile()?.takeIf { Files.isRegularFile(it) } ?: return null
        val statePath = stateFile()?.takeIf { Files.isRegularFile(it) } ?: return null
        val queue = gson.fromJson(String(Files.readAllBytes(queuePath), StandardCharsets.UTF_8), QueueFile::class.java)
        val state = gson.fromJson(String(Files.readAllBytes(statePath), StandardCharsets.UTF_8), StateFile::class.java)
        val tracks = queue?.tracks.orEmpty().mapNotNull { runCatching { it.toTrack() }.getOrNull() }
        val index = state?.index ?: -1
        when {
            state == null || state.key.isEmpty() || index !in tracks.indices -> null
            tracks[index].key != state.key -> null
            state.savedAtMs > 0L && System.currentTimeMillis() - state.savedAtMs > MAX_AGE_MS -> null
            else -> Session(tracks, index, state.positionMs)
        }
    } catch (e: Exception) {
        DangoLogger.warn("Resume state unreadable: {}", e.javaClass.simpleName)
        null
    }

    private fun window(size: Int, index: Int): IntRange {
        if (size <= MAX_TRACKS) return 0 until size
        val start = (index - MAX_TRACKS / 2).coerceIn(0, size - MAX_TRACKS)
        return start until start + MAX_TRACKS
    }

    private fun write(target: Path?, json: String, sync: Boolean = false) {
        target ?: return
        val task = Runnable {
            try {
                Files.createDirectories(target.parent)
                val temporary = target.resolveSibling(target.fileName.toString() + ".tmp")
                Files.write(temporary, json.toByteArray(StandardCharsets.UTF_8))
                try {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
                }
            } catch (e: Exception) {
                DangoLogger.warn("Resume state not saved: {}", e.javaClass.simpleName)
            }
        }
        if (!sync) {
            writer.execute(task)
            return
        }
        // 退出游戏时：排在已提交的异步写入后面，保证最后落盘的是最终进度；写线程不可用就直接写
        try {
            writer.submit(task).get(2, java.util.concurrent.TimeUnit.SECONDS)
        } catch (_: Exception) {
            task.run()
        }
    }
}
