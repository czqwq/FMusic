package com.Lilith.FMusic.dango

import com.Lilith.FMusic.dango.config.ConfigManager
import com.Lilith.FMusic.dango.event.Event
import com.Lilith.FMusic.dango.event.EventManager
import com.Lilith.FMusic.dango.module.Module
import com.Lilith.FMusic.dango.module.ModuleManager
import com.Lilith.FMusic.dango.platform.Platform

import java.nio.file.Path

/**
 * Dango Music 的核心：事件系统 + 模块 + 设置。所有 Minecraft 版本共用这一份。
 * 和游戏打交道的地方都通过 [Platform]，由各版本的平台层在启动时传进来。
 */
class DangoMusic private constructor(val platform: Platform) {
    companion object {
        /** 配置目录名（原来是 dangomusic，移植后挂到 FMusic 名下：config/fmusic/dango） */
        const val MOD_ID = "fmusic_dango"
        const val NAME = "Dango Music"

        lateinit var instance: DangoMusic
            private set

        /** 平台层在 mod 初始化时调用：注册模块、读取设置 */
        @JvmStatic
        fun start(platform: Platform, modules: List<Module>) {
            instance = DangoMusic(platform)
            instance.init(modules)
        }

        @JvmStatic
        fun callEvent(event: Event) {
            if (::instance.isInitialized) instance.eventManager.callEvent(event)
        }
    }

    /** 配置、登录凭证、缓存都放在 .minecraft/config/fmusic/dango/ */
    val clientDir: Path = platform.configDir

    val eventManager = EventManager()
    val moduleManager = ModuleManager(eventManager)
    val configManager = ConfigManager(moduleManager, clientDir.resolve("config.json"))

    private fun init(modules: List<Module>) {
        clientDir.toFile().mkdirs()

        modules.forEach { moduleManager.register(it) }
        moduleManager.load()

        configManager.load()
        eventManager.addListener(configManager)
        configManager.installShutdownHook()
    }
}
