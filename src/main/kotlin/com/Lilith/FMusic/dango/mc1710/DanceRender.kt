package com.Lilith.FMusic.dango.mc1710

import com.Lilith.FMusic.dango.music.Dance
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import org.lwjgl.opengl.GL11

/**
 * 「生物跟着节奏跳」（[Dance]）在 1.7.10 的渲染挂点，同时也是 **YSMU（Yes Steve Model Unofficial）兼容层**。
 *
 * 上游 1.8.9 是在 `RenderLivingEvent.Pre/Post` 里套矩阵 + 改朝向字段的；1.7.10 这边原先照搬，
 * 但只要有“替换渲染器”类模组在场就不成立：
 *
 * - **YSMU 玩家**：它在 `RenderPlayerEvent.Pre` 就 `setCanceled(true)` 并自己画 GeckoLib 模型，
 *   原版 `RenderPlayer#doRender` 直接 return，根本走不到 `super.doRender` 里的 `RenderLivingEvent.Pre`——
 *   姿势套不上；
 * - **YSMU NPC / 载具**：NPC 在 `RenderLivingEvent.Pre` 里取消并当场上屏，我们（LOWEST，最后收到）
 *   只 push 不 pop（Pre 被取消就没有 Post），矩阵栈失衡；载具则是在 `RenderManager` 里被 redirect 掉整个
 *   `render.doRender`，事件同样不会发；
 * - 其它 GeckoLib 模组自带的替换渲染器同理。
 *
 * 所以改成和姐妹项目 FriendSwine 一样的做法：挂点从“生物渲染器内部”移到
 * [net.minecraft.client.renderer.entity.RenderManager] 的实体分发外层 `func_147939_a`——
 * 无论里面是原版渲染器、YSMU 的 GeckoLib 渲染器还是别的 mod 的替换渲染器，都吃同一套变换：
 *
 * - **压扁 / 拉长**：GL 矩阵缩放，以脚下为轴（`y - entity.yOffset`，和原版 `RenderPlayer` /
 *   YSMU `GeoReplacedEntityRenderer` 画模型的原点一致，不然压扁时脚会被抬起来）；
 * - **转圈**：改 `renderYawOffset` / `rotationYawHead`（连同 prev）而不是转矩阵，名字牌是 billboard，
 *   转矩阵会把名字一起转歪；YSMU 的模型朝向读的也是这两个字段
 *   （`GeoReplacedEntityRenderer#resolveBodyYaw` 插值 `prevRenderYawOffset→renderYawOffset`），所以一样生效；
 * - **阴影 / 火焰**：`RenderManager` 里 `render.doRender` 返回后另有 `doRenderShadowAndFire`，画它们之前把
 *   变换撤掉（`pop + push` 换掉当前栈帧），阴影不会被压扁、火焰位置也不会歪；
 * - **只在世界实体渲染里生效**：由 [net.minecraft.client.renderer.RenderGlobal] 的 `renderEntities` 划范围，
 *   物品栏纸娃娃 / GUI 实体预览 / YSMU 预览不会被抖（这些是“看模型”的界面，抖起来只是干扰）。
 *   世界里的实体在开着界面时（背景世界仍在渲染）照常抖。
 *
 * 这些方法只由 `MixinRenderManagerDance` / `MixinRenderGlobalDance` 在客户端渲染线程调用，
 * 每帧每个实体一次，所有状态都是单线程的。
 */
object DanceRender {

    /** 正在 `RenderGlobal.renderEntities` 里（世界实体渲染）：只有这时候才抖 */
    private var worldDepth = 0

    /**
     * 正在渲染的实体嵌套栈（LIFO），每一层 `func_147939_a` 都压一个：
     * 真抖动的是 [Frame]，不抖的用共享的 [NO_FRAME] 占位。
     * 用占位而不是“不压”，是为了同实体嵌套渲染（有的 mod 会在世界渲染里再画同一个实体）时也能对上。
     */
    private val frames = ArrayDeque<Frame>()

    /** 一只已经套上姿势的生物：记下原朝向，撤姿势时精确还原 */
    private class Frame(
        val entity: EntityLivingBase?,
        val spin: Float,
        val renderYawOffset: Float,
        val prevRenderYawOffset: Float,
        val rotationYawHead: Float,
        val prevRotationYawHead: Float,
    ) {
        /** 画阴影 / 火焰前已经撤掉（此时栈里剩的是换过的干净帧） */
        var restored = false
    }

    /** 这一层没套姿势（不抖 / 不在世界渲染里）：只占位，不碰矩阵 */
    private val NO_FRAME = Frame(null, 0f, 0f, 0f, 0f, 0f)

    /** `RenderGlobal.renderEntities` 开始 / 结束（世界实体渲染的范围） */
    @JvmStatic
    fun beginWorld() {
        worldDepth++
    }

    @JvmStatic
    fun endWorld() {
        if (worldDepth > 0) worldDepth--
    }

    /** `RenderManager` 开始画一只实体：套上这一帧的姿势（没在抖就只压一个占位） */
    @JvmStatic
    fun begin(entity: Entity, x: Double, y: Double, z: Double) {
        frames.addLast(poseFrame(entity, x, y, z) ?: NO_FRAME)
    }

    /**
     * `render.doRender` 之后、`doRenderShadowAndFire`（阴影 / 火焰）之前：
     * 用 `pop + push` 把当前矩阵栈帧换成没套姿势的干净帧，并还原朝向。
     */
    @JvmStatic
    fun beforeOverlays(entity: Entity) {
        val frame = frames.lastOrNull() ?: return
        if (frame === NO_FRAME || frame.restored || frame.entity !== entity) return
        GL11.glPopMatrix()
        GL11.glPushMatrix()
        restore(frame)
    }

    /** `RenderManager` 画完这只实体：收尾（没走到 [beforeOverlays] 的在这里撤姿势并弹矩阵） */
    @JvmStatic
    fun end() {
        val frame = frames.lastOrNull() ?: return
        frames.removeLast()
        if (frame === NO_FRAME) return
        if (!frame.restored) restore(frame)
        // begin 里 push 过一次：没走 beforeOverlays 时栈顶是套了姿势的帧，走过时是换成干净帧的帧，都弹一次
        GL11.glPopMatrix()
    }

    /** 这一帧确实要抖就套姿势并返回帧；否则返回 null（不碰矩阵 / 朝向） */
    private fun poseFrame(entity: Entity, x: Double, y: Double, z: Double): Frame? {
        if (worldDepth <= 0 || entity !is EntityLivingBase) return null
        val pose = Dance.pose() ?: return null
        // 以脚下为轴：1.7.10 的 RenderPlayer 和 YSMU 都会先减掉 yOffset 再画模型
        val feetY = y - entity.yOffset
        val squash = pose[1]
        val wide = 1f / Math.sqrt(squash.toDouble()).toFloat()
        GL11.glPushMatrix()
        GL11.glTranslated(x, feetY, z)
        GL11.glScalef(wide, squash, wide)
        GL11.glTranslated(-x, -feetY, -z)
        val spin = pose[0]
        val frame = Frame(
            entity, spin,
            entity.renderYawOffset, entity.prevRenderYawOffset,
            entity.rotationYawHead, entity.prevRotationYawHead,
        )
        if (spin != 0f) {
            entity.renderYawOffset += spin
            entity.prevRenderYawOffset += spin
            entity.rotationYawHead += spin
            entity.prevRotationYawHead += spin
        }
        return frame
    }

    /** 还原朝向字段（转圈用的），并标记这一帧已经撤过 */
    private fun restore(frame: Frame) {
        frame.restored = true
        if (frame.spin == 0f) return
        val entity = frame.entity ?: return
        entity.renderYawOffset = frame.renderYawOffset
        entity.prevRenderYawOffset = frame.prevRenderYawOffset
        entity.rotationYawHead = frame.rotationYawHead
        entity.prevRotationYawHead = frame.prevRotationYawHead
    }
}
