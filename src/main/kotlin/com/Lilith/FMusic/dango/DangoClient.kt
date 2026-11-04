package com.Lilith.FMusic.dango

import com.Lilith.FMusic.dango.gfx.Gfx
import com.Lilith.FMusic.dango.i18n.I18n
import com.Lilith.FMusic.dango.module.impls.music.DynamicIslandModule
import com.Lilith.FMusic.dango.module.impls.music.LyricsModule
import com.Lilith.FMusic.dango.module.impls.music.NeteaseMusicModule
import com.Lilith.FMusic.dango.module.impls.music.SongCardModule
import com.Lilith.FMusic.dango.music.MusicScreen
import com.Lilith.FMusic.dango.platform.Platform

/**
 * 客户端入口：平台层在 mod 初始化时调用 [start]，把游戏接口（[Platform]）和绘图接口（[Gfx]）交进来。
 */
object DangoClient {

    lateinit var gfx: Gfx
        private set

    val platform: Platform get() = DangoMusic.instance.platform

    @JvmStatic
    fun start(platform: Platform, gfx: Gfx) {
        this.gfx = gfx
        I18n.language = I18n.ENGLISH // 未显示 UI 的启动阶段占位；不写入语言设置。
        val musicModule = NeteaseMusicModule()
        DangoMusic.start(
            platform,
            listOf(
                musicModule,
                LyricsModule(),
                SongCardModule(),
                DynamicIslandModule(),
            ),
        )
        musicModule.applyLanguage() // ConfigManager 已读回手动选择，优先于首次默认值。
    }

    /** 平台层打开播放器界面时用：新建一个播放器界面，[close] 由平台层提供（关掉这个界面） */
    @JvmStatic
    fun newMusicScreen(close: () -> Unit): MusicScreen =
        MusicScreen(DangoMusic.instance.moduleManager.get(NeteaseMusicModule::class.java), close)

    @JvmStatic
    fun renderMainMenuNowPlaying() {
        if (platform.inMainMenu) {
            DangoMusic.instance.moduleManager.get(LyricsModule::class.java).renderMainMenu()
        }
    }
}
