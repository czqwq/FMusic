package com.Lilith.FMusic.dango.mc1710

import com.Lilith.FMusic.dango.DangoClient
import com.Lilith.FMusic.dango.DangoMusic
import com.Lilith.FMusic.dango.font.FontManager
import com.Lilith.FMusic.dango.font.UiFont
import com.Lilith.FMusic.dango.shader.ShaderManager
import com.Lilith.FMusic.dango.shader.impls.QuadShader
import com.Lilith.FMusic.dango.shader.impls.ui.FontShader
import com.Lilith.FMusic.dango.shader.impls.ui.GlassBlurShader
import com.Lilith.FMusic.dango.shader.impls.ui.GlassShader
import com.Lilith.FMusic.dango.shader.impls.ui.ImageShader
import com.Lilith.FMusic.dango.shader.impls.ui.ShapeShader

import net.minecraft.client.Minecraft

/** 1.8.9 里 Minecraft 的实例（平台层到处要用） */
lateinit var mc: Minecraft

/** 1.8.9 平台层自己的 GL 资源：着色器和菜单字体（第一次游戏 tick 时初始化） */
object LegacyGl {
    val shaderManager by lazy { ShaderManager(DangoMusic.instance.eventManager) }
    val fontManager = FontManager()

    private val fontCache = mutableMapOf<String, UiFont>()
    fun font(path: String): UiFont = fontCache.getOrPut(path) { fontManager.get(path) }

    private var glReady = false

    /** 第一次游戏 tick 时调用（这时 OpenGL 已经可用）：编译着色器、加载字体 */
    fun init() {
        if (glReady) return
        glReady = true

        shaderManager.register(QuadShader())
        // 上游这里还注册了 GlowDownsample/GlowUpsample/GlowComposite 三个「描边辉光」着色器，
        // 但它们引用 assets/dangomusic/shaders/outline/base.vert，而这个文件在上游 legacy 和
        // modern 两套里都不存在 —— 结果只是每次启动刷 3 条加载失败日志。移植版的实际辉光是
        // lyrics/DeepGlow.kt 里内联的 GLSL，这三条从未被使用，所以不再注册。
        // 以后上游补上 base.vert 并真的用起来时，把这三行加回来即可。
        shaderManager.register(ShapeShader())
        shaderManager.register(FontShader())
        shaderManager.register(ImageShader())
        shaderManager.register(GlassShader())
        shaderManager.register(GlassBlurShader())
        shaderManager.load()

        fontManager.register("Inter-Regular-cut.ttf")         // 第一个注册的是默认字体（找不到指定字体时用它）
        fontManager.register("Inter-Medium-cut.ttf")
        fontManager.register("Inter-SemiBold-cut.ttf")
        fontManager.register("InterDisplay-Bold-cut.ttf")
        fontManager.load()
    }
}

/**
 * 启动入口。原来由上游自己的 `DangoMusicMod`（1.8.9 的 @Mod 类）调用，
 * 移植后由 FMusic 的 `com.Lilith.FMusic.ClientProxy#preInit` 在客户端 preInit 阶段调用。
 */
object LegacyDango {
    @JvmStatic
    fun start() {
        mc = Minecraft.getMinecraft()
        DangoClient.start(LegacyPlatform, LegacyGfx)
    }
}
