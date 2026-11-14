package com.Lilith.FMusic.dango.mc1710

import com.Lilith.FMusic.dango.DangoLogger

import cpw.mods.fml.common.Loader

internal object LegacyTextInput {

    private const val MOD_ID = "lwjgl3ify"
    private const val API_CLASS = "me.eigenraven.lwjgl3ify.api.InputEvents"

    private var start: (() -> Unit)? = null

    private var stop: (() -> Unit)? = null

    val available: Boolean get() = start != null && stop != null

    var active = false
        private set

    init {
        if (Loader.isModLoaded(MOD_ID)) {
            try {
                val cls = Class.forName(API_CLASS, false, LegacyTextInput::class.java.classLoader)
                val begin = cls.getMethod("beginTextInput")
                val end = cls.getMethod("endTextInput")
                start = { begin.invoke(null) }
                stop = { end.invoke(null) }
            } catch (t: Throwable) {
                start = null
                stop = null
                DangoLogger.warn("输入法（IME）支持不可用，输入框仍可用（只能粘贴中文）: {}", t.toString())
            }
        }
    }

    fun setActive(on: Boolean) {
        val target = on && available
        if (target == active) return
        active = target
        try {
            (if (target) start else stop)?.invoke()
        } catch (t: Throwable) {
            start = null
            stop = null
            active = false
            DangoLogger.warn("切换输入法状态失败，播放器输入框退回不支持 IME: {}", t.toString())
        }
    }

    fun reset() {
        setActive(false)
    }
}
