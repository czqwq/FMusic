package com.Lilith.FMusic.dango.mc1710

import com.Lilith.FMusic.dango.DangoClient
import com.Lilith.FMusic.dango.DangoLogger
import com.Lilith.FMusic.dango.DangoMusic
import com.Lilith.FMusic.dango.events.HudRenderEvent
import com.Lilith.FMusic.dango.events.TickEvent
import com.Lilith.FMusic.dango.events.WorldRenderEndEvent

import com.Lilith.FMusic.dango.music.Dance
import cpw.mods.fml.common.FMLCommonHandler
import cpw.mods.fml.common.eventhandler.EventPriority
import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.common.gameevent.TickEvent as ForgeTickEvent
import net.minecraft.entity.EntityLivingBase
import net.minecraftforge.client.event.GuiScreenEvent
import net.minecraftforge.client.event.RenderGameOverlayEvent
import net.minecraftforge.client.event.RenderLivingEvent
import net.minecraftforge.client.event.RenderWorldLastEvent
import net.minecraftforge.common.MinecraftForge
import org.lwjgl.opengl.GL11

/**
 * 把 Forge 的事件转成 mod 自己的事件：
 *   ClientTickEvent(START) → TickEvent（对应原来注入在 Minecraft.runTick 开头）
 *   RenderWorldLastEvent   → WorldRenderEndEvent（对应原来注入在 renderWorldPass 末尾、画手之前）
 *   画南瓜头遮罩之前（暗角刚画完）→ HudRenderEvent（Song card、灵动岛、Now playing，暗角压不到它们）
 */
class ForgeBridge {

    companion object {
        /**
         * mod 主类调用一次。1.8.9 起 Tick 事件和画面事件都在 MinecraftForge.EVENT_BUS 上，
         * 1.7.10 的 TickEvent 还是走 FML 自己的那条总线（FMLCommonHandler.instance().bus()），所以两边都要注册。
         */
        @JvmStatic
        fun register() {
            val bridge = ForgeBridge()
            MinecraftForge.EVENT_BUS.register(bridge)
            if (FMLCommonHandler.instance().bus() !== MinecraftForge.EVENT_BUS) {
                FMLCommonHandler.instance().bus().register(bridge)
            }
        }
    }

    @SubscribeEvent
    fun onMenuDraw(e: GuiScreenEvent.DrawScreenEvent.Post) {
        if (LegacyPlatform.inMainMenu) {
            DangoClient.renderMainMenuNowPlaying()
        }
    }

    @SubscribeEvent
    fun onClientTick(e: ForgeTickEvent.ClientTickEvent) {
        if (e.phase != ForgeTickEvent.Phase.START) return
        try {
            LegacyGl.init()
        } catch (t: Throwable) {
            DangoLogger.error("Dango Music GL init failed: {}", t)
        }
        // 和 FMusic 那边的播放器互让：轮询本播放器是否刚开播（见 DangoAudioArbiter 的说明）
        DangoAudioArbiter.tick()
        DangoMusic.callEvent(TickEvent)
    }

    @SubscribeEvent
    fun onRenderWorldLast(e: RenderWorldLastEvent) {
        DangoMusic.callEvent(WorldRenderEndEvent(e.partialTicks))
    }

    @SubscribeEvent
    fun onOverlay(e: RenderGameOverlayEvent.Pre) {
        if (e.type == RenderGameOverlayEvent.ElementType.HELMET) DangoMusic.callEvent(HudRenderEvent)
    }

    // ---------------------------------------------------------------- 生物跟着节奏跳

    /** 这只生物画之前套上了跳舞的姿势（Post 里撤掉），以及给它的身体 / 头多转了多少度 */
    private var dancing: EntityLivingBase? = null
    private var danceSpin = 0f

    /**
     * 画生物之前：压扁 / 拉长套在矩阵上（以脚下为原点）；转圈改的是身体和头的朝向，
     * 不直接转矩阵，免得头顶的名字跟着转。被别的 mod 取消的就不管（最低优先级，最后一个收到）。
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onRenderLivingPre(e: RenderLivingEvent.Pre) {
        val pose = Dance.pose() ?: return
        val entity = e.entity
        danceSpin = pose[0]
        entity.renderYawOffset += danceSpin
        entity.prevRenderYawOffset += danceSpin
        entity.rotationYawHead += danceSpin
        entity.prevRotationYawHead += danceSpin
        val squash = pose[1]
        val wide = 1f / Math.sqrt(squash.toDouble()).toFloat()
        // 1.7.10 没有 GlStateManager，用原生 GL 矩阵栈（e.x/y/z 是 double）
        GL11.glPushMatrix()
        GL11.glTranslated(e.x, e.y, e.z)
        GL11.glScalef(wide, squash, wide)
        GL11.glTranslated(-e.x, -e.y, -e.z)
        dancing = entity
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    fun onRenderLivingPost(e: RenderLivingEvent.Post) {
        val entity = dancing ?: return
        if (entity !== e.entity) return
        dancing = null
        entity.renderYawOffset -= danceSpin
        entity.prevRenderYawOffset -= danceSpin
        entity.rotationYawHead -= danceSpin
        entity.prevRotationYawHead -= danceSpin
        GL11.glPopMatrix()
    }
}
