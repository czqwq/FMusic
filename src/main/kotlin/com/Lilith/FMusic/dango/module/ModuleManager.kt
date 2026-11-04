package com.Lilith.FMusic.dango.module

import com.Lilith.FMusic.dango.DangoLogger
import com.Lilith.FMusic.dango.event.EventManager
import com.Lilith.FMusic.dango.service.Registry
import com.Lilith.FMusic.dango.service.Service

class ModuleManager(private val eventManager: EventManager) : Service, Registry<Module> {

    val modules = mutableMapOf<Category, MutableList<Module>>()

    override fun load() {
        modules.values.flatten().forEach { module ->
            try {
                module.init()
                eventManager.addListener(module)
            }catch (e: Exception) {
                DangoLogger.error("${module.name} init failed, skip. {}", e)
            }
        }
    }

    override fun unload() {
    }

    override fun register(item: Module) {
        if (!(item.javaClass.isAnnotationPresent(ModuleMeta::class.java))) {
            throw IllegalArgumentException("Module must with @ModuleMeta annotation")
        }
        modules.computeIfAbsent(item.category) { mutableListOf() }.add(item)
    }

    fun <T : Module> get(clazz: Class<T>): T {
        val module = modules.values.flatten().firstOrNull { clazz.isInstance(it) }
            ?: throw IllegalStateException("Module not registered: ${clazz.name}")

        return clazz.cast(module)
    }

    override fun unregister(item: Module) {
        modules.entries.removeIf { (_, v) ->
            v.contains(item)
        }
    }
}