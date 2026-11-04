package com.Lilith.FMusic.dango.musicui

import com.Lilith.FMusic.dango.utils.Color

import kotlin.math.abs
import kotlin.math.min

/**
 * 简单的弹簧：[x] 追目标值，[k] 越大越快、[d] 越大越不回弹；按 1/240 秒的小步走，跳帧时也稳定。
 * 界面动画（菜单、搜索框、开关、设置项变暗）都用它。
 */
internal class Spring(private val k: Float, private val d: Float, initial: Float = 0f) {
    var x = initial
    var v = 0f
    private var lastNanos = 0L

    /** 按调用方给的时间推进 */
    fun step(target: Float, frameDt: Float) {
        var dt = frameDt
        while (dt > 0f) {
            val h = min(dt, 1f / 240f)
            v += ((target - x) * k - v * d) * h
            x += v * h
            dt -= h
        }
        if (abs(target - x) < 0.0005f && abs(v) < 0.001f) {
            x = target
            v = 0f
        }
    }

    /**
     * 自己记时间，每帧画到它时调一次。第一次画、或者有一阵子没画（切了标签页、关了界面）时直接到位，
     * 免得重新出现时还在播放很久以前的动画。
     */
    fun tick(target: Float): Float {
        val now = System.nanoTime()
        val dt = (now - lastNanos) / 1e9f
        if (lastNanos == 0L || dt > 0.25f) {
            x = target
            v = 0f
        } else {
            step(target, dt)
        }
        lastNanos = now
        return x
    }
}

/** 两个颜色按 [t]（0..1）混合，动画里做渐变用 */
internal fun mixColor(a: Color, b: Color, t: Float): Color {
    val k = t.coerceIn(0f, 1f)
    return Color(a.r + (b.r - a.r) * k, a.g + (b.g - a.g) * k, a.b + (b.b - a.b) * k, a.a + (b.a - a.a) * k)
}

/** 以中心为基准缩放一个矩形（按钮按下 / 悬停时的缩放） */
internal fun com.Lilith.FMusic.dango.ui.LayoutBounds.scaled(k: Float): com.Lilith.FMusic.dango.ui.LayoutBounds =
    com.Lilith.FMusic.dango.ui.LayoutBounds(x + width * (1f - k) / 2f, y + height * (1f - k) / 2f, width * k, height * k)
