package com.Lilith.FMusic.dango.mc1710

import com.Lilith.FMusic.client.core.FMusicCore
import com.Lilith.FMusic.dango.music.MusicPlayer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 「同时只允许一个播放器出声」的仲裁器。
 *
 * 为什么要这个类：FMusic 原有的播放器（服务端推送歌曲，OpenAL 流式播放）和移植进来的
 * DangoMusic 播放器（自己下载 mp3/flac/ogg，Java Sound / 游戏 OpenAL 输出）是两套完全独立的
 * 播放系统，互不知道对方的存在，两边一起放歌就会叠成两首歌。这个类只负责「谁出声，谁把对方让开」：
 * 两个系统仍然各自管自己的队列和音源，不共享队列、不混音，也不做任何重采样。
 *
 * 接线只有一处：客户端 tick 里调 [tick]（已经接在
 * `com.Lilith.FMusic.dango.mc1710.ForgeBridge#onClientTick` 里、`DangoMusic.callEvent(TickEvent)` 旁边）。
 * [tick] 同时轮询两边的「是否正在出声」，靠状态跳变识别「刚开播」，所以**不需要**改 FMusic 那边的
 * `FMusicPlayer` / `FMusicCore` 的播放路径，也不改 DangoMusic 的播放线程：无线程、无 IO、无回调。
 */
object DangoAudioArbiter {

    /** DangoMusic 此刻是否正在出声（加载中 / 暂停都不算） */
    val dangoPlaying: Boolean get() = try {
        MusicPlayer.state == MusicPlayer.State.PLAYING
    } catch (_: Throwable) {
        false
    }

    /** FMusic 原有播放器此刻是否正在出声 */
    private val fmusicPlaying: Boolean get() = try {
        FMusicCore.isPlay()
    } catch (_: Throwable) {
        false
    }

    /** 上一次 tick 时各自是否在出声，只用来识别「这一帧刚开播」 */
    private val dangoWasPlaying = AtomicBoolean(false)
    private val fmusicWasPlaying = AtomicBoolean(false)

    /**
     * DangoMusic 开始出声了：FMusic 如果正在放服务端推送的歌，立刻让它停下来。
     * 用 [FMusicCore.hardStop]（直接关掉 OpenAL 流 + 清 HUD），不用 STOP 指令那条路——
     * 那条路在歌曲结尾附近会「优雅收尾」把缓冲尾巴放完，会让两首歌短暂叠在一起。
     */
    @JvmStatic
    fun onDangoStarted() {
        dangoWasPlaying.set(true)
        if (!fmusicPlaying) return
        FMusicCore.hardStop()
    }

    /** FMusic 开始出声了：DangoMusic 如果正在放，暂停它（保留进度，用户还能接着放）。 */
    @JvmStatic
    fun onFMusicStarted() {
        fmusicWasPlaying.set(true)
        if (!dangoPlaying) return
        MusicPlayer.togglePause()
    }

    /**
     * 客户端 tick 调用：轮询两边的状态，发现「刚开播」就让另一方让路。
     * 单向依赖可终止：Dango 开播 → 硬停 FMusic → FMusic 下一帧不再是「在放」，不会反手暂停 Dango。
     * 每 tick 都会跑，所以整体兜一层：这里出问题最多是没让路，不该把客户端的 tick 处理器搞崩。
     */
    @JvmStatic
    fun tick() {
        try {
            if (dangoPlaying) {
                if (!dangoWasPlaying.getAndSet(true)) onDangoStarted()
            } else {
                dangoWasPlaying.set(false)
            }
            if (fmusicPlaying) {
                if (!fmusicWasPlaying.getAndSet(true)) onFMusicStarted()
            } else {
                fmusicWasPlaying.set(false)
            }
        } catch (t: Throwable) {
            // 用 kotlin 的 println 会绕过日志配置，这里保持安静：下一 tick 会重试。
            dangoWasPlaying.set(false)
            fmusicWasPlaying.set(false)
        }
    }
}
