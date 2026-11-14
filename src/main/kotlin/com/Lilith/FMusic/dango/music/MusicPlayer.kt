package com.Lilith.FMusic.dango.music

import com.Lilith.FMusic.dango.DangoLogger

import java.util.Random
import java.util.concurrent.Executors
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine
import kotlin.math.max

/**
 * 内置网易云播放器：下载 mp3 → JLayer 解码 → Java Sound 输出（安卓等不带声音库的 Java 改用游戏的 OpenAL，见 [AudioOutput]）。
 * 播放进度 = 起始偏移 + 声卡实际已经播放的时长，所以歌词同步是精确的，拖动进度条也没问题。
 */
object MusicPlayer {

    enum class State { STOPPED, LOADING, PLAYING, PAUSED, ERROR }

    @Volatile var state = State.STOPPED
        private set
    @Volatile var current: NeteaseTrack? = null
        private set
    @Volatile var message: String = ""
        private set

    @Volatile var queue: List<NeteaseTrack> = emptyList()
        private set
    @Volatile var index = -1
        private set

    @Volatile var shuffle = false
    /** 歌曲自然播完时重播当前曲目；手动切歌仍按队列 / Shuffle 工作。 */
    @Volatile var repeatOne = false
    /** 0..1 */
    @Volatile var volume = 0.2f

    /** 声音从哪里放：0 自动（安卓用游戏的 OpenAL，电脑用 Java Sound），1 Java Sound，2 游戏的 OpenAL。换了就从当前位置重新开始放 */
    @Volatile var outputMode = OUTPUT_AUTO
        set(v) {
            if (field == v) return
            field = v
            if (state == State.PLAYING) seekLocal(positionMs)
        }

    @Volatile var quality = AudioQuality.STANDARD
    @Volatile var currentQuality: AudioQuality? = null
        private set
    @Volatile var fromCache = false
        private set

    private val loader = Executors.newSingleThreadExecutor { r -> Thread(r, "Dango-MusicLoader").apply { isDaemon = true } }
    private val radioWorker = Executors.newSingleThreadExecutor { r -> Thread(r, "Dango-MusicRadio").apply { isDaemon = true } }

    /** 电台式的队列（私人 FM）：快放完时用它再要几首接在后面；普通歌单是 null */
    @Volatile var radio: (() -> List<NeteaseTrack>)? = null
        private set
    private val random = Random()

    /** 每次换歌 / 拖动都 +1，旧的解码线程看到代数变了就自己退出 */
    @Volatile private var generation = 0
    @Volatile private var audio: ByteArray? = null
    @Volatile private var line: AudioOutput? = null
    /** 声卡上次报的播放位置（微秒）和取样的时刻，由解码线程写、渲染线程读；见 [positionMs] */
    @Volatile private var positionMicrosAt = 0L
    @Volatile private var positionSampledAt = 0L
    @Volatile private var seekBaseMs = 0L
    @Volatile private var pausedAtMs = 0L

    val durationMs: Long get() = current?.durationMs ?: 0L

    // ---------------------------------------------------------------- 鼓点检测（文闪用）

    /** (歌曲内时间 ms, 强度 0..1)，解码时根据低频能量突增检测出来 */
    private class Beat(val timeMs: Long, val strength: Float)
    private val beats = ArrayDeque<Beat>()

    /**
     * 当前的"鼓点冲击"强度：最近一个鼓点之后迅速衰减，0..1。
     * posMs 用歌曲内时间（和歌词同一条时间线）。
     */
    fun beatPulse(posMs: Long): Float {
        var best = 0f
        synchronized(beats) {
            for (b in beats) {
                val dt = posMs - b.timeMs
                if (dt < 0 || dt > 600) continue
                best = max(best, b.strength * kotlin.math.exp(-dt / 110f))
            }
        }
        return best
    }

    private fun clearBeats() = synchronized(beats) { beats.clear() }

    /** 最近检测到的鼓点：[0] 时间（歌曲内 ms），[1] 强度 0..1，从早到晚 */
    fun beatSnapshot(): Array<FloatArray> = synchronized(beats) {
        Array(beats.size) { val b = beats.elementAt(it); floatArrayOf(b.timeMs.toFloat(), b.strength) }
    }

    // ---------------------------------------------------------------- 声卡时间 ↔ 歌曲时间（变速时进度靠它）

    /** 写到声卡第 [outUs] 微秒的地方，对应歌里第 [songMs] 毫秒，之后每放 1ms 歌走 [speed] ms */
    private class Anchor(val outUs: Long, val songMs: Double, val speed: Float)
    private val anchors = ArrayDeque<Anchor>()

    private fun clearAnchors() = synchronized(anchors) { anchors.clear() }

    private fun anchor(outUs: Long, songMs: Double, speed: Float) = synchronized(anchors) {
        anchors.addLast(Anchor(outUs, songMs, speed))
        while (anchors.size > 160) anchors.removeFirst()   // 每块 20~30ms，几秒的记录足够
    }

    /** 声卡已经放到 [playedUs]：找它之前最近的一笔往后推 */
    private fun songAt(playedUs: Long): Long? = synchronized(anchors) {
        if (anchors.isEmpty()) return null
        var a = anchors.first()
        for (i in anchors.indices.reversed()) if (anchors[i].outUs <= playedUs) { a = anchors[i]; break }
        (a.songMs + (playedUs - a.outUs) / 1000.0 * a.speed).toLong()
    }

    /**
     * 播放位置（毫秒）。
     *
     * 这里是渲染 / 界面线程每帧都会读的热点（3D 歌词、Song card、灵动岛、播放器进度条）。
     * **不能**在这里直接问声卡：`SourceDataLine.getMicrosecondPosition()` 和解码线程正在阻塞的
     * `write()` 抢同一把锁，一旦被写操作挡住，整个渲染线程就会卡在那里（单机下还会连带拖慢
     * 内置服务器 tick）。FMusic 原来的播放器没有这个问题，是因为它的位置来自 OpenAL（无锁查询）。
     *
     * 所以改成：解码线程每次 write 之后把声卡位置记进 [positionMicrosAt]（约每秒几十次），
     * 渲染线程只读这两个 @Volatile 字段，再用经过的时间做线性插值。
     * 播放是实时的，插值误差上限就是两次采样之间的间隔；这里再夹一个 200ms 的上限，
     * 万一解码线程停了也不会让歌词一直往前飘。
     */
    val positionMs: Long
        get() = when (state) {
            State.PLAYING -> {
                val elapsed = ((System.nanoTime() - positionSampledAt) / 1000).coerceIn(0L, 200_000L)
                val us = positionMicrosAt + elapsed
                songAt(us) ?: (seekBaseMs + us / 1000)
            }
            State.PAUSED -> pausedAtMs
            else -> 0L
        }

    val active: Boolean get() = current != null && (state == State.PLAYING || state == State.PAUSED || state == State.LOADING)

    // ---------------------------------------------------------------- 别的播放器（系统媒体）

    /** 自己没在放、别的播放器在放（设置里打开了"显示其它播放器"）：界面和 HUD 显示它，按钮也转给它 */
    val external: Boolean get() = !active && SystemMedia.track != null
    val shownTrack: NeteaseTrack? get() = if (external) SystemMedia.track else current
    val shownState: State get() = if (external) (if (SystemMedia.playing) State.PLAYING else State.PAUSED) else state
    val shownPositionMs: Long get() = if (external) SystemMedia.positionMs else positionMs
    val shownDurationMs: Long get() = if (external) SystemMedia.durationMs else durationMs
    val shownActive: Boolean get() = active || SystemMedia.track != null

    // ---------------------------------------------------------------- 队列控制

    @Synchronized @JvmOverloads fun playQueue(tracks: List<NeteaseTrack>, startIndex: Int, radio: (() -> List<NeteaseTrack>)? = null) {
        if (tracks.isNotEmpty() && !canPlay(tracks[Math.floorMod(startIndex, tracks.size)])) return
        queue = tracks
        this.radio = radio
        playIndex(startIndex)
    }

    private val failedTracks = HashSet<String>()
    private val failureTimer = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "Dango-Skip").apply { isDaemon = true } }
    @Volatile var skipNotice = 0L
        private set

    @Synchronized fun playIndex(i: Int) {
        failedTracks.clear()
        playIndexInternal(i)
    }

    private fun playIndexInternal(i: Int) {
        val q = queue
        if (q.isEmpty()) return
        val idx = ((i % q.size) + q.size) % q.size
        val track = q[idx]
        if (!canPlay(track)) return
        index = idx
        val gen = ++generation
        stopLine()
        audio = null
        current = track
        state = State.LOADING
        message = "加载中…"
        currentQuality = null
        fromCache = false
        val requestedQuality = quality
        loader.execute { load(gen, track, requestedQuality) }
        if (radio != null && idx >= q.size - 2) extendRadio(null)
    }

    /** 电台：再要几首接到队列后面（去掉已经在队列里的），然后执行 [then] */
    private fun extendRadio(then: (() -> Unit)?) {
        val fetch = radio ?: return
        radioWorker.execute {
            if (radio !== fetch) return@execute
            // 已经有好几首没放的就不用再要了（连续调用时只要一次）
            if (queue.size - index <= 2) {
                val more = try { fetch() } catch (e: Exception) {
                    DangoLogger.warn("Radio fetch failed: {}", e.toString())
                    emptyList()
                }
                if (radio !== fetch) return@execute
                val have = queue.mapTo(HashSet()) { it.key }
                val fresh = more.filter { have.add(it.key) }
                if (fresh.isNotEmpty()) queue = queue + fresh
            }
            then?.invoke()
        }
    }

    /**
     * 歌单还在加载时就开始放了：加载出更多后把队列换成更长的版本。
     * 只在队列还是 [from] 本身、并且 [to] 以它为开头时才换，当前这首和播放位置都不变。
     */
    fun growQueue(from: List<NeteaseTrack>, to: List<NeteaseTrack>) {
        if (queue !== from || radio != null || to.size <= from.size) return
        for (i in from.indices) if (from[i].key != to[i].key) return
        queue = to
    }

    /** 一起听已移除：没有房间来源 / 私有云盘的限制，本地单人播放一律可放。 */
    @Suppress("UNUSED_PARAMETER")
    private fun canPlay(track: NeteaseTrack): Boolean {
        return true
    }

    /**
     * 从指定位置开始播放某首歌。
     * 起始位置在下载完成、真正开始播放前才计算（下载要好几秒，提前算会落后）。
     */
    @Synchronized fun playTrackAt(track: NeteaseTrack, startAt: () -> Long, paused: Boolean) {
        if (!canPlay(track)) return
        queue = listOf(track)
        radio = null
        index = 0
        val gen = ++generation
        stopLine()
        audio = null
        current = track
        state = State.LOADING
        message = "加载中…"
        currentQuality = null
        fromCache = false
        val requestedQuality = quality
        loader.execute { load(gen, track, requestedQuality, startAt, paused) }
    }

    /**
     * 切换请求音质：立即按新档位重新加载当前歌曲（保持播放位置和暂停状态），不用等下一首。
     * 新档位已经有缓存时重载很快；正在加载时切换会取消旧请求、按新档位重新开始。
     * 只改本机播放器自己的取流，不影响服务端点歌和其它播放器。
     */
    @Synchronized fun switchQuality(value: AudioQuality) {
        val changed = quality != value
        quality = value
        if (!changed || external) return
        val track = current ?: return
        if (state != State.PLAYING && state != State.PAUSED && state != State.LOADING) return
        val paused = state == State.PAUSED
        val at = if (state == State.PLAYING || paused) positionMs else 0L
        val gen = ++generation
        stopLine()
        audio = null
        current = track
        state = State.LOADING
        message = "加载中…"
        currentQuality = null
        fromCache = false
        loader.execute { load(gen, track, value, { at }, paused) }
    }

    /** 把一首歌插在当前这首后面，放完接着原来的队列 */
    @Synchronized fun playNext(track: NeteaseTrack) {
        val q = queue
        if (q.isEmpty() || index !in q.indices) {
            queue = listOf(track)
            playIndex(0)
            return
        }
        queue = q.subList(0, index + 1) + track + q.subList(index + 1, q.size)
        playIndex(index + 1)
    }

    /** 跳到 ms 并设成播放 / 暂停（当前这首已经下载好时用） */
    @Synchronized fun syncTo(ms: Long, play: Boolean) {
        val bytes = audio ?: return
        val target = ms.coerceIn(0L, max(0L, durationMs - 500))
        pausedAtMs = target
        state = if (play) State.PLAYING else State.PAUSED   // 解码线程据此决定声卡是开着还是停着
        val gen = ++generation
        stopLine()
        startDecode(gen, bytes, target)
    }

    fun next() {
        if (external) return SystemMedia.next()
        val q = queue
        if (q.isEmpty()) return
        // 电台按顺序往下放；放到最后一首时先要几首新的再切
        if (radio != null) {
            if (index + 1 < q.size) playIndex(index + 1)
            else { val from = index; extendRadio { if (index == from) playIndex(from + 1) } }
            return
        }
        playIndex(if (shuffle && q.size > 1) pickRandom(q.size) else index + 1)
    }

    fun prev() {
        if (external) return SystemMedia.prev()
        if (positionMs > 3000) {
            seek(0)
            return
        }
        playIndex(index - 1)
    }

    private fun pickRandom(size: Int): Int {
        var r: Int
        do { r = random.nextInt(size) } while (r == index)
        return r
    }

    fun togglePause() {
        if (external) return SystemMedia.togglePause()
        when (state) {
            State.PLAYING -> {
                pausedAtMs = positionMs
                state = State.PAUSED
                line?.stop()
            }
            State.PAUSED -> {
                val l = line
                if (l != null) {
                    // 声卡输出只是停住了，接着放就行；解码线程看到状态变回 PLAYING 会继续写数据
                    state = State.PLAYING
                    l.start()
                } else {
                    seekLocal(pausedAtMs)
                }
            }
            else -> {}
        }
    }

    fun seek(ms: Long) {
        if (external) return SystemMedia.seek(ms)
        if (audio == null) return
        seekLocal(ms)
    }

    @Synchronized private fun seekLocal(ms: Long) {
        val bytes = audio ?: return
        val target = ms.coerceIn(0L, max(0L, durationMs - 500))
        // 暂停时拖动：停在新位置，仍保持暂停
        if (state == State.PAUSED) pausedAtMs = target
        val gen = ++generation
        stopLine()
        startDecode(gen, bytes, target)
    }

    @Synchronized fun stop() {
        ++generation
        stopLine()
        audio = null
        current = null
        currentQuality = null
        fromCache = false
        state = State.STOPPED
        message = ""
    }

    // ---------------------------------------------------------------- 加载 / 解码

    private fun load(gen: Int, track: NeteaseTrack, requestedQuality: AudioQuality, startAt: (() -> Long)? = null, paused: Boolean = false) {
        try {
            if (gen != generation) return
            val active = { gen == generation }
            val cache = if (requestedQuality.cacheEnabled) MusicCache.access() else null
            val identity = if (cache != null) cacheIdentity(track) else ""
            val cached = if (requestedQuality.cacheEnabled) cache?.read(identity, requestedQuality, active) else null
            if (!active()) return
            val kugou = if (cached == null && track.source == MusicSource.KUGOU) KugouMusicSource.loadAudio(track, requestedQuality, active) else null
            val url = cached?.info ?: if (track.source == MusicSource.KUGOU) kugou?.info else MusicSources.api(track.source).songUrl(track, requestedQuality, active)
            if (gen != generation) return
            if (url == null) return fail(gen, if (track.source == MusicSource.NETEASE) "拿不到播放地址"
                else if (NativeAccounts.get(track.source) != null) "No playable MP3. Your login may have expired, or this song is restricted by membership or region."
                else "No guest MP3 available. Login, payment or regional restrictions may apply.")
            val bytes = cached?.bytes ?: kugou?.bytes ?: if (track.source == MusicSource.NETEASE)
                NeteaseApi.request(url.url, maxBytes = AudioCache.MAX_AUDIO_BYTES, active = active)
                else SourceHttp.bytes(url.url, if (track.source == MusicSource.QQ) "https://y.qq.com/" else "https://m.kugou.com/", AudioCache.MAX_AUDIO_BYTES, active)
            if (gen != generation) return
            if (bytes == null || bytes.isEmpty() || (!track.cloud && bytes.size < 10_000)) {
                return fail(gen, if (track.source != MusicSource.NETEASE) "Unable to download this guest MP3. Try another song."
                    else if (NeteaseApi.loggedIn) "这首歌没有播放权限" else "需要登录才能播放（VIP 歌曲）")
            }
            val actualQuality = when {
                Mp3Probe.kind(bytes) == "flac" -> AudioQuality.LOSSLESS
                url.quality == AudioQuality.LOSSLESS -> AudioQuality.STANDARD
                else -> url.quality
            }
            val info = NeteaseApi.SongUrl(url.url, url.trialOnly, url.gainDb, url.peak, url.replaced, actualQuality)
            if (cached == null && requestedQuality.cacheEnabled) cache?.write(identity, info, bytes, active)
            if (!active()) return
            // Cloud list duration units are not reliable. Use compressed audio timing before enabling seeking.
            val measuredDuration = if (track.cloud) AudioAnalysis.duration(bytes) { gen == generation } else 0L
            val measuredGain = if (normalize) AudioAnalysis.gain(bytes) { gen == generation } else null
            synchronized(this) {
                if (gen != generation) return
                currentQuality = actualQuality
                fromCache = cached != null
                if (measuredDuration > 0) track.durationMs = measuredDuration
                audio = bytes
                message = when {
                    url.trialOnly -> "Preview only: membership is needed for the full song"
                    url.replaced -> "The original was unavailable, playing another version"
                    actualQuality.ordinal < requestedQuality.ordinal -> "Requested quality unavailable; playing a lower quality"
                    else -> ""
                }
                trackGain = if (normalize) measuredGain ?: loudnessGain(url) else 1f
                DangoLogger.info("Audio analysis: source={} cloud={} durationMs={} localGain={} appliedGain={}",
                    track.source.key, track.cloud, measuredDuration, measuredGain != null, trackGain)
                val start = (startAt?.invoke() ?: 0L).coerceIn(0L, max(0L, track.durationMs - 1000))
                if (paused) {
                    pausedAtMs = start
                    state = State.PAUSED
                }
                startDecode(gen, bytes, start)
            }
        } catch (_: java.util.concurrent.CancellationException) {
        } catch (e: Throwable) {
            // 包括 NoClassDefFoundError 之类的 Error：不接住的话线程会悄悄退出，界面一直停在"加载中"
            if (track.source == MusicSource.NETEASE) {
                DangoLogger.warn("Music load failed: {}", e.javaClass.simpleName)
                fail(gen, if (e is SourceAccessException) e.message.orEmpty() else "加载失败：" + describe(e))
            } else {
                DangoLogger.warn("Source {} load failed: {}", track.source.key, e.javaClass.simpleName)
                fail(gen, if (e is SourceAccessException) e.message.orEmpty() else "Playback request failed. Check your connection or log in again.")
            }
        }
    }

    private fun cacheIdentity(track: NeteaseTrack): String {
        val account = if (track.source == MusicSource.NETEASE) NeteaseApi.audioCacheScope()
            else NativeAccounts.get(track.source)?.let { "${it.userId}:${it.concept}" } ?: "guest"
        return "${track.key}:${track.mediaId}:${track.cloud}:${track.cloudId}:$account"
    }

    /** 失败跳过独立计时，不占用加载线程；旧请求不能切掉用户新选的歌。 */
    @Synchronized private fun fail(gen: Int, msg: String) {
        if (gen != generation) return
        state = State.ERROR
        message = msg
        current?.let { failedTracks.add(it.key) }
        if (failedTracks.size >= 10 || queue.none { it.key !in failedTracks }) {
            message = msg + " · " + "Auto-skip stopped after repeated playback failures."
            return
        }
        failureTimer.schedule({
            synchronized(this) {
                if (gen == generation && state == State.ERROR && failedTracks.size < 10) {
                    val candidates = queue.indices.filter { queue[it].key !in failedTracks }
                    if (candidates.isNotEmpty()) {
                        val next = if (shuffle) candidates[random.nextInt(candidates.size)]
                            else candidates.firstOrNull { it > index } ?: candidates.first()
                        skipNotice = System.nanoTime()
                        playIndexInternal(next)
                    }
                }
            }
        }, 3, java.util.concurrent.TimeUnit.SECONDS)
    }

    private fun describe(e: Throwable): String {
        val m = e.message?.take(60)
        return e.javaClass.simpleName + if (m.isNullOrBlank()) "" else ": $m"
    }

    /**
     * 打开声卡输出。默认设备打不开时（被别的程序独占、格式不支持等），
     * 依次试系统里其它能输出这个格式的设备。
     */
    /** 测试用：-Ddangomusic.openal=true 时电脑上也用游戏的 OpenAL 放歌 */
    private val forceOpenAl = java.lang.Boolean.getBoolean("dangomusic.openal")
    private var loggedOutput = ""

    /** 这次该先试游戏的 OpenAL 吗 */
    private fun preferGameAudio(): Boolean = when (outputMode) {
        OUTPUT_JAVA_SOUND -> false
        OUTPUT_GAME -> true
        // 安卓上 Java Sound 是模拟出来的，放歌会掉帧；电脑上 Java Sound 不占游戏的声音通道，更稳
        else -> forceOpenAl || AndroidRuntime.detected || !AudioOutput.javaSoundNative
    }

    /**
     * 打开声音输出：按 [outputMode] 先试游戏的 OpenAL 或 Java Sound，打不开再换另一个。
     */
    private fun openOutput(format: AudioFormat, bufferBytes: Int): AudioOutput {
        if (preferGameAudio()) platformOutput(format)?.let { return it }
        return try {
            JavaSoundOutput(openLine(format, bufferBytes)).also { logOutput("Java Sound") }
        } catch (e: Throwable) {
            platformOutput(format) ?: throw e
        }
    }

    /**
     * 声音系统重启后重新打开输出。游戏要一会儿才能把声音系统开回来，最多等几秒；
     * 原来用的是游戏的 OpenAL 就继续等它，等不到再按平常的顺序打开。
     */
    private fun reopenOutput(gen: Int, old: AudioOutput, rate: Int, channels: Int): AudioOutput? {
        // Java Sound 线路卡住（换了默认设备，#55）：不用等游戏的声音系统，直接按平常的顺序在新的默认设备上重开
        val javaSound = old is JavaSoundOutput
        DangoLogger.info(if (javaSound) "Dango Music: audio device changed, reopening output" else "Dango Music: game audio restarted, reopening output")
        try { old.close() } catch (_: Exception) {}
        val format = AudioFormat(rate.toFloat(), 16, channels, true, false)
        var out: AudioOutput? = null
        val deadline = System.currentTimeMillis() + if (javaSound) 0L else 5000L
        while (out == null && gen == generation && System.currentTimeMillis() < deadline) {
            out = platformOutput(format)
            if (out == null) Thread.sleep(100)
        }
        if (gen != generation) {
            try { out?.close() } catch (_: Exception) {}
            return null
        }
        // 重开时用和 decodeLoop 一样的约 1 秒缓冲（上游这里是 0.25 秒，本模组为了抗 underrun 调大了，见 §6）
        if (out == null) out = openOutput(format, rate * channels * 2)
        line = out
        // 新输出从 0 开始：位置采样的基准也要跟着归零（见 positionMs 的说明）
        positionMicrosAt = 0L
        positionSampledAt = System.nanoTime()
        if (state == State.PAUSED) out.stop() else out.start()
        return out
    }

    private fun platformOutput(format: AudioFormat): AudioOutput? = try {
        com.Lilith.FMusic.dango.DangoMusic.instance.platform.openAudioOutput(format.sampleRate.toInt(), format.channels)
            ?.also { logOutput("game OpenAL") }
    } catch (e: Throwable) {
        DangoLogger.warn("Game audio output failed: {}", e.toString())
        null
    }

    private fun logOutput(name: String) {
        if (loggedOutput == name) return
        loggedOutput = name
        DangoLogger.info("Dango Music: playing through {}", name)
    }

    private fun openLine(format: AudioFormat, bufferBytes: Int): SourceDataLine {
        var last: Throwable? = null
        try {
            val l = AudioSystem.getSourceDataLine(format)
            try {
                l.open(format, bufferBytes)
            } catch (e: Exception) {
                l.open(format)
            }
            return l
        } catch (e: Throwable) {
            last = e
            DangoLogger.warn("Default audio line failed: {}", e.toString())
        }
        val info = DataLine.Info(SourceDataLine::class.java, format)
        for (mi in AudioSystem.getMixerInfo()) {
            try {
                val mixer = AudioSystem.getMixer(mi)
                if (!mixer.isLineSupported(info)) continue
                val l = mixer.getLine(info) as SourceDataLine
                l.open(format)
                DangoLogger.info("Using audio device: {}", mi.name)
                return l
            } catch (e: Throwable) {
                last = e
            }
        }
        throw last ?: IllegalStateException("没有可用的声音输出设备")
    }

    /** 音量均衡（设置里开关）：按音源给的响度把每首歌调到差不多响 */
    @Volatile var normalize = true
    /** 这首歌要乘的倍数（1 = 原样） */
    @Volatile private var trackGain = 1f

    private fun loudnessGain(url: NeteaseApi.SongUrl): Float {
        if (!normalize || url.gainDb == 0f) return 1f
        var gain = Math.pow(10.0, url.gainDb / 20.0).toFloat()
        // 有峰值就别把峰值推过 1（会破音）
        if (url.peak > 0f) gain = minOf(gain, 0.98f / url.peak)
        return gain.coerceIn(0.25f, 2f)
    }

    private fun startDecode(gen: Int, bytes: ByteArray, startMs: Long) {
        Thread({ decodeLoop(gen, bytes, startMs) }, "Dango-MusicDecode").apply {
            isDaemon = true
            start()
        }
    }

    private fun decodeLoop(gen: Int, bytes: ByteArray, startMs: Long) {
        var out: AudioOutput? = null
        var finished = false
        var source: AudioDecoder? = null
        try {
            val started = AudioDecoder.start(bytes, startMs) { gen == generation }
            val decoder = started.decoder
            source = decoder
            val skipped = started.skippedMs
            val first = started.first
            if (gen != generation) return
            seekBaseMs = skipped.toLong()
            clearBeats()
            EnvironmentAudio.reset()
            var decodedMs = skipped
            // 变速变调：写进声卡的每一块都记一笔"声卡时间 ↔ 歌曲时间"，进度按它算
            val trackKey = current?.key
            var stretcher: SoundStretcher? = null
            var equalizer: Equalizer? = null
            var outUs = 0L
            var songOutMs = skipped.toDouble()
            clearAnchors()
            var lowPass = 0f
            var avgEnergy = 0f
            var lastBeatMs = -1000f

            var appliedVolume = -1f
            var pending = first
            while (gen == generation) {
                val frame = pending ?: decoder.read()
                pending = null
                if (frame == null) {
                    finished = true
                    break
                }
                if (out == null) {
                    val format = AudioFormat(frame.rate.toFloat(), 16, frame.channels, true, false)
                    // 约 1 秒缓冲。原来是 0.25 秒，太紧：解码线程一旦被 GC 或游戏线程挤一下
                    // （单机下客户端和内置服务器共用一个 JVM），声卡就会 underrun，
                    // 听感就是"播放卡一下 / 爆音"。音乐不需要低延迟，缓冲给足更稳。
                    out = openOutput(format, frame.rate * frame.channels * 2)
                    line = out
                    positionMicrosAt = 0L
                    positionSampledAt = System.nanoTime()
                    if (state == State.PAUSED) {
                        out.stop()
                    } else {
                        out.start()
                        state = State.PLAYING
                    }
                }
                // 暂停：不再往声卡写数据，原地等待。
                // （Java Sound 在输出停止时被写数据会自动重新开始或直接返回，
                //   不这样等的话会一口气把整首歌"读完"，然后误以为放完了跳下一首）
                while (state == State.PAUSED && gen == generation) {
                    if (out.isRunning) out.stop()   // 写数据时可能被自动重新开始，确保停住
                    Thread.sleep(15)
                }
                if (gen != generation) break
                if (volume != appliedVolume) {
                    out.setVolume(volume)
                    appliedVolume = volume
                }
                val len = frame.length
                val pcm = frame.samples

                // 鼓点：低频（底鼓）能量比最近一秒的平均值突然高出很多
                val frameMs = frame.durationMs
                val ch = frame.channels.coerceAtLeast(1)
                var energy = 0f
                var k = 0
                while (k + ch - 1 < len) {
                    var mono = 0f
                    for (c in 0 until ch) mono += pcm[k + c].toFloat()
                    mono /= ch * 32768f
                    lowPass += (mono - lowPass) * 0.04f
                    energy += lowPass * lowPass
                    k += ch
                }
                energy /= (len / ch).coerceAtLeast(1)
                if (avgEnergy > 0f && energy > avgEnergy * 1.7f && energy > 2e-4f && decodedMs - lastBeatMs > 170f) {
                    val strength = ((energy / avgEnergy - 1.7f) / 2f + 0.45f).coerceIn(0f, 1f)
                    synchronized(beats) {
                        beats.addLast(Beat(decodedMs.toLong(), strength))
                        while (beats.size > 48) beats.removeFirst()
                    }
                    lastBeatMs = decodedMs
                }
                avgEnergy = if (avgEnergy == 0f) energy else avgEnergy + (energy - avgEnergy) * (frameMs / 1000f).coerceAtMost(1f)
                decodedMs += frameMs
                // 音量均衡、环境音效（水下变闷、洞穴回声……）在鼓点检测之后加，不影响节奏判断
                val gain = trackGain
                if (gain != 1f) for (idx in 0 until len) pcm[idx] = (pcm[idx] * gain).toInt().coerceIn(-32768, 32767).toShort()
                // 变速变调：都是 1 就不经过它（没有延迟）；从 1 调开时新建，调回 1 时扔掉，歌曲时间直接对回解码位置
                val tune = SoundTuning.effective(trackKey)
                var outPcm = pcm
                var outLen = len
                if (tune.bypass) {
                    if (stretcher != null) stretcher = null
                    songOutMs = (decodedMs - frameMs).toDouble()
                } else {
                    val st = stretcher ?: SoundStretcher(ch, frame.rate).also { stretcher = it }
                    st.speed = tune.speed
                    st.pitch = tune.pitch
                    outPcm = st.process(pcm, len)
                    outLen = st.lastLength
                }
                val outFrames = outLen / ch
                val outMs = outFrames * 1000f / frame.rate
                val chunkSongMs = songOutMs
                anchor(outUs, chunkSongMs, tune.speed)
                outUs += outFrames * 1_000_000L / frame.rate
                songOutMs += outMs * tune.speed
                if (outLen == 0) continue
                // 均衡器：关着就不经过（再开时滤波器从静止开始）
                val eqGains = tune.eq
                if (eqGains == null) equalizer = null
                else (equalizer ?: Equalizer(ch, frame.rate).also { equalizer = it }).process(outPcm, outLen, eqGains)
                EnvironmentAudio.process(outPcm, outLen, ch, frame.rate, outMs)
                val data = ByteArray(outLen * 2)
                for (k in 0 until outLen) {
                    val v = outPcm[k].toInt()
                    data[k * 2] = (v and 0xFF).toByte()
                    data[k * 2 + 1] = ((v shr 8) and 0xFF).toByte()
                }
                try {
                    out.write(data, 0, data.size)
                } catch (e: Throwable) {
                    if (!out.lost || gen != generation) throw e
                    // 游戏重启了声音系统：换一个新的输出，从这一块接着放（缓冲里没放完的零点几秒丢掉）
                    val reopened = reopenOutput(gen, out, frame.rate, frame.channels) ?: throw e
                    out = reopened
                    seekBaseMs = (decodedMs - frameMs).toLong()
                    // 新的输出从 0 开始计时
                    clearAnchors()
                    anchor(0L, chunkSongMs, tune.speed)
                    outUs = outFrames * 1_000_000L / frame.rate
                    appliedVolume = -1f
                    if (reopened.lost) throw e
                    reopened.setVolume(volume)
                    appliedVolume = volume
                    reopened.write(data, 0, data.size)
                }
                // 顺便把声卡的实际播放位置记下来给渲染线程用（见 positionMs 的说明）：
                // 只有解码线程碰声卡，渲染线程永远不会被阻塞的 write() 挡住。
                positionMicrosAt = out.positionMicros
                positionSampledAt = System.nanoTime()
            }
            if (finished && out == null && gen == generation) {
                finished = false
                fail(gen, "No decodable audio was found.")
            }
            if (finished && gen == generation) {
                // 最后一点正在放的时候声音系统重启了：就当放完了
                try { out?.drain() } catch (e: Throwable) { if (out?.lost != true) throw e }
                synchronized(this) { if (gen == generation) failedTracks.clear() }
            }
        } catch (e: Throwable) {
            finished = false
            DangoLogger.warn("Music decode failed: {}", e.toString())
            if (gen == generation) {
                fail(gen, "播放失败：" + describe(e))
            }
        } finally {
            try { source?.close() } catch (_: Exception) {}
            if (line === out) line = null
            try {
                out?.stop()
                out?.close()
            } catch (_: Exception) {
            }
        }
        if (finished && gen == generation) {
            if (repeatOne) {
                // 复用已下载的音频，从头解码，不重新请求播放地址或下载。
                seek(0L)
            } else {
                next()
            }
        }
    }

    private fun stopLine() {
        val l = line
        line = null
        positionMicrosAt = 0L
        positionSampledAt = System.nanoTime()
        try {
            l?.stop()
            l?.flush()
            l?.close()
        } catch (_: Exception) {
        }
    }

    const val OUTPUT_AUTO = 0
    const val OUTPUT_JAVA_SOUND = 1
    const val OUTPUT_GAME = 2
}
