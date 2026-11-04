package com.Lilith.FMusic.dango.module

import com.Lilith.FMusic.dango.DangoMusic
import com.Lilith.FMusic.dango.event.Listener
import com.Lilith.FMusic.dango.events.ModuleToggleEvent

abstract class Module : Listener {
    val category = javaClass.getAnnotation(ModuleMeta::class.java).category
    val name = javaClass.getAnnotation(ModuleMeta::class.java).name
    var enabled = false
        set(value) {
            if (field == value) return
            field = value
            if (value) {
                DangoMusic.callEvent(ModuleToggleEvent.Enable(this))
                onEnable()
            }else {
                onDisable()
                DangoMusic.callEvent(ModuleToggleEvent.Disable(this))
            }
        }

    /** 第一次启动（还没有配置文件）时要不要打开 */
    open val enabledByDefault: Boolean get() = true

    open fun init() {}
    protected open fun onEnable() {}
    protected open fun onDisable() {}
}