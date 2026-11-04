package com.Lilith.FMusic.dango.mc1710

import com.Lilith.FMusic.client.core.FMusicCore
import com.Lilith.FMusic.dango.music.MusicPlayer

/**
 * 「同时只允许一个播放器出声」的仲裁器。**DangoMusic 绝对优先。**
 *
 * 两个播放系统是独立的：
 *   - FMusic 原有播放器：服务端点歌，OpenAL 流式播放，**和服务器同步**（服务端按歌曲时长推进，
 *     歌词/KTV 由服务端推送）；
 *   - DangoMusic 播放器：自己下载 mp3/flac/ogg，Java Sound / 游戏 OpenAL 输出。
 *
 * 所以两边"让路"的方式必须不一样：
 *
 *   - **点歌让路 = 静音，不是暂停**。点歌的位置和服务端绑定，本地一旦暂停就和服务器上其他人
 *     错开了，恢复时歌词/进度对不上（而且 `closePlayer()` 会把播放任务直接销毁，服务端不会重推，
 *     等于这首歌没了）。这里只把 OpenAL 源的增益压到 0，流照常按实时推进，
 *     DangoMusic 一停下来，取消静音听到的就是此刻的正确位置。
 *   - **DangoMusic 永远不需要让路**。它是纯客户端的，优先级更高：
 *     只要它在出声，点歌就一直静音；服务端推来新歌也**不会**打断它。
 *
 * 行为（纯电平判断，没有边沿逻辑）：
 *   - DangoMusic 在出声  → 点歌静音（无论此刻有没有在放，先静着，新歌进来也不会漏出一声）；
 *   - DangoMusic 不出声  → 点歌取消静音（只还原我们做的静音，用户自己调的音量不动）。
 *
 * 接线只有一处：客户端 tick 里调 [tick]（已接在
 * `com.Lilith.FMusic.dango.mc1710.ForgeBridge#onClientTick` 里、`DangoMusic.callEvent(TickEvent)` 旁边）。
 * 不装回调、不建线程、不做 IO，两边的播放路径都不用改。
 */
object DangoAudioArbiter {

    /** DangoMusic 此刻是否正在出声（加载中 / 暂停都不算） */
    val dangoPlaying: Boolean get() = try {
        MusicPlayer.state == MusicPlayer.State.PLAYING
    } catch (_: Throwable) {
        false
    }

    /** 当前的静音是不是我们做的（用户自己没要求静音），用来决定要不要还原 */
    @Volatile private var mutedByUs = false

    /**
     * 客户端 tick 调用。整体兜一层：这里出问题最多是没让路，不该把客户端的 tick 处理器搞崩。
     */
    @JvmStatic
    fun tick() {
        try {
            val dango = dangoPlaying
            if (dango) {
                // DangoMusic 在出声 → 点歌静音。这里不管点歌当前有没有在放：
                // 提前静着，服务端下一首推过来时就不会先漏出一两声。
                if (!mutedByUs) {
                    FMusicCore.setMuted(true)
                    mutedByUs = true
                }
            } else if (mutedByUs) {
                // DangoMusic 不出声了 → 放开点歌的声音（处于实时位置，和服务器同步）
                FMusicCore.setMuted(false)
                mutedByUs = false
            }
        } catch (t: Throwable) {
            // 下一 tick 会按状态重新对齐，这里不刷日志
        }
    }
}
