package com.Lilith.FMusic.dango.utils.render

import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11
import org.lwjgl.opengl.GL13
import org.lwjgl.opengl.GL14
import org.lwjgl.opengl.GL15
import org.lwjgl.opengl.GL20

/**
 * 记下当前的 OpenGL 状态，restore() 时原样还原。
 *
 * 1.8.9 有 GlStateManager 那层状态缓存，所以原来要"通过 GlStateManager 和原生 GL 各设一遍"；
 * 1.7.10 没有这层缓存（Angelica 的 GLStateManager 是把 GL11 调用重定向到自己的缓存上），
 * 直接调 GL11 就是唯一的状态来源，缓存也跟着一起更新，因此这里只做原生 GL 调用。
 *
 * 除了开关和混合之外，还保存了裁剪状态（scissor）和绑定的 VBO：Angelica 跑的是 core profile，
 * 我们直接用 GL 画完之后如果这两项和进入时不一样，它的状态缓存就会和真实状态对不上。
 */
class GlStateSnapshot {
    private var program = 0
    private var activeTex = GL13.GL_TEXTURE0
    private val tex2d = BooleanArray(2)
    private val binding = IntArray(2)
    private val lightmapCoord = FloatArray(4)
    private var blend = false
    private var depthTest = false
    private var cull = false
    private var alphaTest = false
    private var fog = false
    private var lighting = false
    private var depthMask = true
    private var srcRgb = GL11.GL_ONE
    private var dstRgb = GL11.GL_ZERO
    private var srcAlpha = GL11.GL_ONE
    private var dstAlpha = GL11.GL_ZERO
    private var shadeModel = GL11.GL_SMOOTH
    private var matrixMode = GL11.GL_MODELVIEW
    private val color = FloatArray(4)
    private var scissorTest = false
    private val scissorBox = IntArray(4)
    private var arrayBuffer = 0
    private var elementArrayBuffer = 0

    init {
        val buf = BufferUtils.createFloatBuffer(16)
        program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM)
        activeTex = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE)
        for (i in 0..1) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + i)
            tex2d[i] = GL11.glIsEnabled(GL11.GL_TEXTURE_2D)
            binding[i] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D)
            if (i == 1) {
                // 1 号单元的"当前纹理坐标"就是光照贴图坐标，原版还会缓存它
                buf.clear()
                GL11.glGetFloat(GL11.GL_CURRENT_TEXTURE_COORDS, buf)
                for (k in 0..3) lightmapCoord[k] = buf.get(k)
            }
        }
        GL13.glActiveTexture(activeTex)

        blend = GL11.glIsEnabled(GL11.GL_BLEND)
        depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST)
        cull = GL11.glIsEnabled(GL11.GL_CULL_FACE)
        alphaTest = GL11.glIsEnabled(GL11.GL_ALPHA_TEST)
        fog = GL11.glIsEnabled(GL11.GL_FOG)
        lighting = GL11.glIsEnabled(GL11.GL_LIGHTING)
        depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK)
        srcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB)
        dstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB)
        srcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA)
        dstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA)
        shadeModel = GL11.glGetInteger(GL11.GL_SHADE_MODEL)
        matrixMode = GL11.glGetInteger(GL11.GL_MATRIX_MODE)

        scissorTest = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)
        val box = BufferUtils.createIntBuffer(4)
        GL11.glGetInteger(GL11.GL_SCISSOR_BOX, box)
        for (k in 0..3) scissorBox[k] = box.get(k)
        arrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING)
        elementArrayBuffer = GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING)

        buf.clear()
        GL11.glGetFloat(GL11.GL_CURRENT_COLOR, buf)
        for (k in 0..3) color[k] = buf.get(k)
    }

    private fun set(cap: Int, on: Boolean) {
        if (on) GL11.glEnable(cap) else GL11.glDisable(cap)
    }

    fun restore() {
        GL20.glUseProgram(program)

        for (i in 1 downTo 0) {
            val unit = GL13.GL_TEXTURE0 + i
            GL13.glActiveTexture(unit)
            set(GL11.GL_TEXTURE_2D, tex2d[i])
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, binding[i])
            if (i == 1) {
                // 1 号单元的"当前纹理坐标"就是光照贴图坐标。必须用 2f 而不是上游原来的 4f：
                //   - Angelica 的重定向表只映射了 glMultiTexCoord2f/2d/2s，没有 4f 变体；
                //   - 它跑的是 GL 3.3 core profile，而 glMultiTexCoord4f 是兼容 profile 的固定管线函数，
                //     在 core 上下文里没有对应入口，直接调用会让 LWJGL 在原生层 abort 掉整个 JVM
                //     （实测崩溃：No context is current or a function that is not available）。
                // 语义不变：原版自己也是用 glMultiTexCoord2f(lightmapTexUnit, u, v) 设这一对坐标的，
                // 剩下的 z/w 保持默认 0/1，正是 2f 调用的结果。
                GL13.glMultiTexCoord2f(unit, lightmapCoord[0], lightmapCoord[1])
            }
        }
        GL13.glActiveTexture(activeTex)

        set(GL11.GL_BLEND, blend)
        set(GL11.GL_DEPTH_TEST, depthTest)
        set(GL11.GL_CULL_FACE, cull)
        set(GL11.GL_ALPHA_TEST, alphaTest)
        set(GL11.GL_FOG, fog)
        set(GL11.GL_LIGHTING, lighting)

        GL11.glDepthMask(depthMask)
        GL14.glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha)
        GL11.glShadeModel(shadeModel)
        GL11.glColor4f(color[0], color[1], color[2], color[3])
        GL11.glMatrixMode(matrixMode)

        GL11.glScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3])
        set(GL11.GL_SCISSOR_TEST, scissorTest)
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBuffer)
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, elementArrayBuffer)
    }
}
