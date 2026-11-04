package com.Lilith.FMusic.dango.shader

import com.Lilith.FMusic.dango.DangoLogger
import com.Lilith.FMusic.dango.event.EventManager
import com.Lilith.FMusic.dango.events.ShadersLinkedEvent
import com.Lilith.FMusic.dango.service.Registry
import com.Lilith.FMusic.dango.service.Service

class ShaderManager(
    private val eventManager: EventManager,
) : Service, Registry<GlShader> {
    private val store = mutableListOf<GlShader>()

    override fun register(item: GlShader) {
        store.add(item)
    }

    override fun unregister(item: GlShader) {
        store.remove(item)
    }

    inline fun <reified T : GlShader> get(): T {
        return get(T::class.java)
    }

    fun <T : GlShader> get(clazz: Class<T>): T {
        val shader = store.firstOrNull { clazz.isInstance(it) }
            ?: throw IllegalStateException("Shader not registered: ${clazz.name}")

        return clazz.cast(shader)
    }

    override fun load() {
        for (shader in store) {
            try {
                shader.linkProgram()
                DangoLogger.info("Shader linked: ${shader.javaClass.name}")
            }catch (e: Exception) {
                DangoLogger.error("ShaderManager loading error: {}", e)
            }
        }

        eventManager.callEvent(ShadersLinkedEvent)
    }

    override fun unload() {
    }
}