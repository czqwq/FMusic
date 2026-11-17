package com.Lilith.FMusic.dango.mc1710

import com.Lilith.FMusic.dango.DangoClient
import com.Lilith.FMusic.dango.DangoLogger
import com.Lilith.FMusic.dango.DangoMusic
import com.Lilith.FMusic.dango.events.HudRenderEvent
import com.Lilith.FMusic.dango.events.TickEvent
import com.Lilith.FMusic.dango.events.WorldRenderEndEvent

import com.Lilith.FMusic.dango.platform.VanillaMusicHold
import cpw.mods.fml.common.FMLCommonHandler
import cpw.mods.fml.common.eventhandler.SubscribeEvent
import cpw.mods.fml.common.gameevent.TickEvent as ForgeTickEvent
import net.minecraft.client.audio.SoundCategory
import net.minecraftforge.client.event.GuiScreenEvent
import net.minecraftforge.client.event.RenderGameOverlayEvent
import net.minecraftforge.client.event.RenderWorldLastEvent
import net.minecraftforge.client.event.sound.PlaySoundEvent17
import net.minecraftforge.common.MinecraftForge

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

    // ---------------------------------------------------------------- 原版背景音乐让路

    /**
     * 原版音乐让路：放歌时拦掉原版要开始放的背景音乐（音乐盒是 RECORDS，不受影响）。
     *
     * 上游 1.8.9 挂在 `PlaySoundEvent` 上；1.7.10 这个 Forge 对应的是 [PlaySoundEvent17]
     * （`ForgeHooksClient.playSound` 会 post 它，并把 `e.result` 当作最终要播的声音返回，
     * 置 null 就等于拦掉这首）。[SoundCategory] 也就是上游同名的那个枚举。
     *
     * 只拦 [SoundCategory.MUSIC]：RECORDS 是唱片机 / 音乐盒，必须继续能放
     * （FMusic 自己的 `client/FMusic.java#onSound` 会连 RECORDS 一起停，那是它那边的老行为，
     * 这边不跟它学）。MUSIC 之外的一切（WEATHER / BLOCKS / MOBS …）都不动。
     *
     * [VanillaMusicHold.active] 由平台层每 tick 按「播放器是否正在出声」更新。
     */
    @SubscribeEvent
    fun onPlaySound(e: PlaySoundEvent17) {
        if (VanillaMusicHold.active && e.category == SoundCategory.MUSIC) e.result = null
    }

    // 生物跟着节奏跳（Dance）的渲染挂点不走这里：原先的 RenderLivingEvent.Pre/Post 在 YSMU
    // 等替换渲染器在场时套不上姿势（玩家走 RenderPlayerEvent.Pre 就被取消、NPC 被取消后没有 Post，
    // 只 push 不 pop）。现在改成在 RenderManager 实体分发外层做，见 mc1710/DanceRender.kt 与
    // mixins/early/MixinRenderManagerDance.java、MixinRenderGlobalDance.java。
}
