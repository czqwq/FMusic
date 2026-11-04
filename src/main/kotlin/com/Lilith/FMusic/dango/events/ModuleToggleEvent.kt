package com.Lilith.FMusic.dango.events

import com.Lilith.FMusic.dango.event.Event
import com.Lilith.FMusic.dango.module.Module

abstract class ModuleToggleEvent(
    val module: Module,
) : Event {
    class Enable(module: Module) : ModuleToggleEvent(module)
    class Disable(module: Module) : ModuleToggleEvent(module)
}