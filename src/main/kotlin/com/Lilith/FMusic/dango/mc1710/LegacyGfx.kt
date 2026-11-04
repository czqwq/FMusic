package com.Lilith.FMusic.dango.mc1710

import com.Lilith.FMusic.dango.DangoLogger
import com.Lilith.FMusic.dango.font.CjkFont
import com.Lilith.FMusic.dango.gfx.CjkRun
import com.Lilith.FMusic.dango.gfx.Gfx
import com.Lilith.FMusic.dango.gfx.GlowPass
import com.Lilith.FMusic.dango.gfx.GuiCanvas
import com.Lilith.FMusic.dango.gfx.HudCanvas
import com.Lilith.FMusic.dango.gfx.TextCanvas
import com.Lilith.FMusic.dango.gfx.Texture
import com.Lilith.FMusic.dango.gfx.Textures
import com.Lilith.FMusic.dango.gfx.WorldView
import com.Lilith.FMusic.dango.lyrics.DeepGlow
import com.Lilith.FMusic.dango.ui.GlassRenderContext
import com.Lilith.FMusic.dango.utils.render.GlCaps
import com.Lilith.FMusic.dango.utils.render.GlStateSnapshot

import net.minecraft.client.gui.Gui
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.renderer.OpenGlHelper
import net.minecraft.client.renderer.entity.RenderManager
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GL20

import kotlin.math.max

/** 1.8.9 的绘图实现：OpenGL 固定管线 + 原版的 FontRenderer / TextureManager */
object LegacyGfx : Gfx {

    override val textures: Textures = LegacyTextures

    override fun newCanvas(): GuiCanvas = GlassRenderContext()

    override fun uiTextWidth(text: String, size: Float, font: String): Float =
        LegacyGl.font(font).getTextWidth(text, size)

    override fun cjkWidth(text: String, size: Float, style: Int): Float = CjkFont.width(text, size, style)

    override fun cjkPreload(c: Char, style: Int) {
        CjkFont.glyph(c, style)
    }

    override fun drawCjkRuns(runs: List<CjkRun>, scale: Float) {
        if (runs.isEmpty()) return
        val k = scale
        GL20.glUseProgram(0)
        GL11.glMatrixMode(GL11.GL_PROJECTION)
        GL11.glPushMatrix()
        GL11.glLoadIdentity()
        GL11.glOrtho(0.0, mc.displayWidth.toDouble(), mc.displayHeight.toDouble(), 0.0, 1000.0, 3000.0)
        GL11.glMatrixMode(GL11.GL_MODELVIEW)
        GL11.glPushMatrix()
        GL11.glLoadIdentity()
        GL11.glTranslatef(0f, 0f, -2000f)
        GL11.glScalef(k, k, 1f)

        // 全部直接设 GL 状态（不走 MC 的状态缓存，缓存可能和实际状态对不上），画完原样恢复
        val caps = GlCaps.save()
        GL13.glActiveTexture(OpenGlHelper.lightmapTexUnit)
        GL11.glDisable(GL11.GL_TEXTURE_2D)                 // 关掉光照贴图那一层，否则字会被压暗
        GL13.glActiveTexture(OpenGlHelper.defaultTexUnit)
        GL11.glEnable(GL11.GL_TEXTURE_2D)
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE)
        GL11.glDisable(GL11.GL_ALPHA_TEST)
        GL11.glDisable(GL11.GL_DEPTH_TEST)
        GL11.glDisable(GL11.GL_LIGHTING)
        GL11.glDisable(GL11.GL_FOG)
        GL11.glDisable(GL11.GL_CULL_FACE)
        GL11.glEnable(GL11.GL_BLEND)
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA)
        GL14.glBlendEquation(GL14.GL_FUNC_ADD)

        for (r in runs) {
            val c = r.clip
            if (c != null) {
                GL11.glEnable(GL11.GL_SCISSOR_TEST)
                GL11.glScissor(
                    (c.x * k).toInt(),
                    (mc.displayHeight - c.bottom * k).toInt(),
                    max(0, (c.width * k).toInt()),
                    max(0, (c.height * k).toInt()),
                )
            } else {
                GL11.glDisable(GL11.GL_SCISSOR_TEST)
            }
            GL11.glColor4f(r.r, r.g, r.b, r.a.coerceIn(0f, 1f))
            if (r.centered) CjkFont.drawCentered(r.s, r.x, r.y, r.size, r.style)
            else CjkFont.draw(r.s, r.x, r.y, r.size, r.style)
        }

        GL11.glDisable(GL11.GL_SCISSOR_TEST)
        GlCaps.restore(caps)
        resetColorAndTexture()
        GL11.glPopMatrix()
        GL11.glMatrixMode(GL11.GL_PROJECTION)
        GL11.glPopMatrix()
        GL11.glMatrixMode(GL11.GL_MODELVIEW)
    }

    override fun guarded(block: () -> Unit) {
        // 状态快照 / 还原本身也可能失败（Angelica 跑的是 GL 3.3 core profile，某些兼容 profile 专用的
        // 查询在那边不保证有实现）。这类失败只该让"这一帧的状态可能脏一点"，绝不该把渲染管线带崩：
        // 所以两处都兜一层，失败只记一次日志。
        var snap: GlStateSnapshot? = null
        try {
            snap = GlStateSnapshot()
        } catch (t: Throwable) {
            if (snapshotFailureLogged.compareAndSet(false, true)) {
                DangoLogger.warn("Dango state snapshot failed, drawing without state guard: {}", t.toString())
            }
        }
        GL20.glUseProgram(0)
        try {
            block()
        } catch (t: Throwable) {
            if (drawFailureLogged.compareAndSet(false, true)) {
                DangoLogger.warn("Dango draw failed: {}", t.toString())
            }
        } finally {
            try {
                snap?.restore()
            } catch (t: Throwable) {
                if (restoreFailureLogged.compareAndSet(false, true)) {
                    DangoLogger.warn("Dango state restore failed: {}", t.toString())
                }
            }
        }
    }

    private val snapshotFailureLogged = java.util.concurrent.atomic.AtomicBoolean(false)
    private val restoreFailureLogged = java.util.concurrent.atomic.AtomicBoolean(false)
    private val drawFailureLogged = java.util.concurrent.atomic.AtomicBoolean(false)

    override val text: TextCanvas = LegacyText
    override val world: WorldView = LegacyWorld
    override val glow: GlowPass = DeepGlow()
    override val hud: HudCanvas = LegacyHud

    /**
     * 直接用 GL 绑过贴图 / 设过颜色之后恢复成中性状态，免得后面的绘制沿用我们的颜色和贴图。
     *
     * 1.7.10 没有 GlStateManager 那层状态缓存（Angelica 的 GLStateManager 是把 GL11 调用重定向
     * 到它自己的缓存上的），所以这里的 GL 调用就是唯一的状态来源，调一次就同步了。
     */
    internal fun resetColorAndTexture() {
        GL11.glColor4f(1f, 1f, 1f, 1f)
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0)
    }

    /** 画文字前的通用状态（关光照贴图、雾、光照、背面剔除，开混合） */
    internal fun prepareTextState() {
        GL13.glActiveTexture(OpenGlHelper.lightmapTexUnit)
        GL11.glDisable(GL11.GL_TEXTURE_2D)
        GL13.glActiveTexture(OpenGlHelper.defaultTexUnit)
        GL11.glEnable(GL11.GL_TEXTURE_2D)
        GL11.glDisable(GL11.GL_LIGHTING)
        GL11.glDisable(GL11.GL_FOG)
        GL11.glEnable(GL11.GL_BLEND)
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO)
        GL11.glDisable(GL11.GL_CULL_FACE)
    }
}

// ---------------------------------------------------------------- 歌词文字

object LegacyText : TextCanvas {
    override fun push() = GL11.glPushMatrix()
    override fun pop() = GL11.glPopMatrix()
    override fun translate(x: Float, y: Float) = GL11.glTranslatef(x, y, 0f)
    override fun rotate(degrees: Float) = GL11.glRotatef(degrees, 0f, 0f, 1f)
    override fun scale(x: Float, y: Float) = GL11.glScalef(x, y, 1f)

    override fun color(r: Float, g: Float, b: Float, a: Float) = GL11.glColor4f(r, g, b, a)

    override fun lyricFont(file: java.io.File?) = CjkFont.setLyricFont(file)

    override fun smoothText(text: String, x: Float, baseline: Float, size: Float, style: Int) =
        CjkFont.draw(text, x, baseline, size, style)

    override fun smoothWidth(text: String, size: Float, style: Int): Float = CjkFont.width(text, size, style)

    override fun beginSmooth() {
        // 平滑字体走固定管线，直接设 GL 状态
        GL11.glDisable(GL11.GL_ALPHA_TEST)
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE)
    }

    override fun endSmooth() = LegacyGfx.resetColorAndTexture()

    override fun pixelText(text: String, x: Float, y: Float, argb: Int, shadow: Boolean) {
        // 1.7.10 的 FontRenderer 只有 int 坐标的重载
        mc.fontRenderer.drawString(text, x.toInt(), y.toInt(), argb, shadow)
    }

    override fun pixelWidth(text: String): Float = mc.fontRenderer.getStringWidth(text).toFloat()

    override fun pixelCharWidth(c: Char): Float = mc.fontRenderer.getCharWidth(c).toFloat()

    private var screenCaps: GlCaps.Saved? = null

    override fun beginScreen() {
        screenCaps = GlCaps.save()
        GL20.glUseProgram(0)
        GL11.glMatrixMode(GL11.GL_PROJECTION)
        GL11.glPushMatrix()
        GL11.glLoadIdentity()
        GL11.glOrtho(0.0, mc.displayWidth.toDouble(), mc.displayHeight.toDouble(), 0.0, 1000.0, 3000.0)
        GL11.glMatrixMode(GL11.GL_MODELVIEW)
        GL11.glPushMatrix()
        GL11.glLoadIdentity()
        GL11.glTranslatef(0f, 0f, -2000f)

        GL13.glActiveTexture(OpenGlHelper.lightmapTexUnit)
        GL11.glDisable(GL11.GL_TEXTURE_2D)
        GL13.glActiveTexture(OpenGlHelper.defaultTexUnit)
        GL11.glEnable(GL11.GL_TEXTURE_2D)
        GL11.glTexEnvi(GL11.GL_TEXTURE_ENV, GL11.GL_TEXTURE_ENV_MODE, GL11.GL_MODULATE)
        GL11.glDisable(GL11.GL_ALPHA_TEST)
        GL11.glDisable(GL11.GL_DEPTH_TEST)
        GL11.glDisable(GL11.GL_LIGHTING)
        GL11.glDisable(GL11.GL_FOG)
        GL11.glDisable(GL11.GL_CULL_FACE)
        GL11.glEnable(GL11.GL_BLEND)
        GL14.glBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA)
        GL14.glBlendEquation(GL14.GL_FUNC_ADD)
    }

    override fun endScreen() {
        screenCaps?.let { GlCaps.restore(it) }
        screenCaps = null
        LegacyGfx.resetColorAndTexture()
        GL11.glMatrixMode(GL11.GL_PROJECTION)
        GL11.glPopMatrix()
        GL11.glMatrixMode(GL11.GL_MODELVIEW)
        GL11.glPopMatrix()
    }
}

// ---------------------------------------------------------------- 世界里的歌词

object LegacyWorld : WorldView {
    override val cameraX: Double get() = RenderManager.instance.viewerPosX
    override val cameraY: Double get() = RenderManager.instance.viewerPosY
    override val cameraZ: Double get() = RenderManager.instance.viewerPosZ

    private val viewMatrix = org.lwjgl.BufferUtils.createFloatBuffer(16)

    override fun cameraAngles(): com.Lilith.FMusic.dango.gfx.ViewAngles {
        // 在任何歌词变换和发光绘制之前只读一次，保留睡觉时原版附加的镜头旋转。
        viewMatrix.clear()
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, viewMatrix)
        return com.Lilith.FMusic.dango.gfx.ViewAngles.fromViewBack(
            viewMatrix.get(2).toDouble(), viewMatrix.get(6).toDouble(), viewMatrix.get(10).toDouble(),
        ) ?: com.Lilith.FMusic.dango.gfx.ViewAngles(RenderManager.instance.playerViewY, RenderManager.instance.playerViewX)
    }

    override fun begin() {
        LegacyGfx.prepareTextState()
        GL11.glDisable(GL11.GL_DEPTH_TEST)
        GL11.glDepthMask(false)
    }

    override fun push(rx: Double, ry: Double, rz: Double, yaw: Float, pitch: Float, scale: Float) {
        GL11.glPushMatrix()
        GL11.glTranslated(rx, ry, rz)
        GL11.glRotatef(-yaw, 0f, 1f, 0f)
        GL11.glRotatef(pitch, 1f, 0f, 0f)
        GL11.glScalef(-scale, -scale, scale)
    }

    override fun pop() = GL11.glPopMatrix()

    override fun resync() {
        // 发光是直接用 GL 画的，恢复了中性状态再重新设一遍文字状态
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0)
        GL11.glColor4f(1f, 1f, 1f, 1f)
        LegacyGfx.prepareTextState()
    }
}

// ---------------------------------------------------------------- 左上角 HUD

object LegacyHud : HudCanvas {
    override fun begin() {
        // 1.7.10 的 ScaledResolution 要显式传窗口像素
        val sr = ScaledResolution(mc, mc.displayWidth, mc.displayHeight)
        GL11.glMatrixMode(GL11.GL_PROJECTION)
        GL11.glPushMatrix()
        GL11.glLoadIdentity()
        GL11.glOrtho(0.0, sr.scaledWidth_double, sr.scaledHeight_double, 0.0, 1000.0, 3000.0)
        GL11.glMatrixMode(GL11.GL_MODELVIEW)
        GL11.glPushMatrix()
        GL11.glLoadIdentity()
        GL11.glTranslatef(0f, 0f, -2000f)
        LegacyGfx.prepareTextState()
        GL11.glDisable(GL11.GL_DEPTH_TEST)
    }

    override fun end() {
        GL11.glMatrixMode(GL11.GL_PROJECTION)
        GL11.glPopMatrix()
        GL11.glMatrixMode(GL11.GL_MODELVIEW)
        GL11.glPopMatrix()
    }

    override fun rect(x1: Int, y1: Int, x2: Int, y2: Int, argb: Int) = Gui.drawRect(x1, y1, x2, y2, argb)

    override fun texture(texture: Texture, x: Int, y: Int, width: Int, height: Int, alpha: Float) {
        GL11.glEnable(GL11.GL_TEXTURE_2D)
        GL11.glEnable(GL11.GL_BLEND)
        mc.textureManager.bindTexture((texture as LegacyTexture).location)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR)
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR)
        GL11.glColor4f(1f, 1f, 1f, alpha)
        // 1.8.9 的 drawModalRectWithCustomSizedTexture 在 1.7.10 就是 func_146110_a
        Gui.func_146110_a(x, y, 0f, 0f, width, height, width.toFloat(), height.toFloat())
        GL11.glColor4f(1f, 1f, 1f, 1f)
    }

    override fun text(text: String, x: Float, y: Float, argb: Int, shadow: Boolean) {
        // Gui.drawRect 画完会关掉混合，文字前重新打开
        GL11.glEnable(GL11.GL_TEXTURE_2D)
        GL11.glEnable(GL11.GL_BLEND)
        mc.fontRenderer.drawString(text, x.toInt(), y.toInt(), argb, shadow)
    }
}
