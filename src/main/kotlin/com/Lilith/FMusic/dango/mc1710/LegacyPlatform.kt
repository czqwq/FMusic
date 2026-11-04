package com.Lilith.FMusic.dango.mc1710

import com.Lilith.FMusic.dango.platform.Platform
import com.Lilith.FMusic.dango.platform.PlayerPose
import com.Lilith.FMusic.dango.platform.PlayerKeyBinding

import net.minecraft.client.gui.GuiChat
import net.minecraft.client.gui.GuiMainMenu
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.block.Block
import net.minecraft.block.BlockLeaves
import net.minecraft.block.material.Material
import net.minecraft.util.MathHelper
import net.minecraft.world.World
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse

import java.nio.file.Path

/** 1.7.10 的游戏接口。键码本来就是 LWJGL 2 的，不用转换 */
object LegacyPlatform : Platform {

    // 配置、登录凭证、缓存放 config/fmusic/dango/（和老模组的 config/fmusic_client.json、fmusic_server/ 并列，不冲突）
    override val configDir: Path
        get() = mc.mcDataDir.toPath().resolve("config").resolve("fmusic").resolve("dango")

    // 1.7.10 的 Session 只暴露 getUsername()，Kotlin 里就是 .username；拿不到时退回 "Player"。
    override val playerName: String get() = mc.session?.username ?: "Player"

    override val languageCode: String? get() = mc.gameSettings?.language

    override val windowWidth: Int get() = mc.displayWidth
    override val windowHeight: Int get() = mc.displayHeight

    override val mouseX: Int get() = Mouse.getX()
    override val mouseY: Int get() = mc.displayHeight - Mouse.getY() - 1

    override fun isMouseDown(button: Int): Boolean = Mouse.isButtonDown(button)

    override fun isKeyDown(key: Int): Boolean = try {
        if (key in -100..-1) Mouse.isButtonDown(key + 100) else Keyboard.isKeyDown(key)
    } catch (_: Exception) {
        false
    }

    override fun syncKeyBindings(bindings: List<PlayerKeyBinding>) = LegacyKeyBindings.sync(bindings)

    override val ctrlDown: Boolean get() = GuiScreen.isCtrlKeyDown()

    override var clipboard: String
        get() = GuiScreen.getClipboardString()
        set(value) = GuiScreen.setClipboardString(value)

    override val inGame: Boolean get() = mc.currentScreen == null && mc.theWorld != null

    override val inMainMenu: Boolean get() = mc.currentScreen is GuiMainMenu

    override val chatOpen: Boolean get() = mc.currentScreen is GuiChat

    // 1.7.10 的 ScaledResolution 构造需要自己传窗口尺寸（1.8.9 是只传 Minecraft）
    override val guiScale: Int get() = ScaledResolution(mc, mc.displayWidth, mc.displayHeight).scaleFactor

    // 1.7.10 没有 Minecraft.getDebugFPS()（那是 1.8 才有的），debugFPS 只是 Minecraft 的私有静态字段，
    // 而且正式环境里 MC 的字段名是 SRG（field_xxx），按字符串反射在生产环境必然拿不到，
    // 所以这里用"两次取值的间隔"估算帧率：HUD 每帧取一次 fps，间隔就是帧时间。
    // 这个值只用于显示，间隔过长（HUD 隐藏过）时直接跳过，不给离谱的瞬时值。
    private var lastFpsNanos = 0L
    private var smoothedFps = 0.0

    override val fps: Int
        get() {
            val now = System.nanoTime()
            val prev = lastFpsNanos
            if (prev == 0L) {
                lastFpsNanos = now
            } else {
                val dt = (now - prev) / 1_000_000_000.0
                if (dt >= 1.0e-3) {
                    lastFpsNanos = now
                    if (dt <= 0.5) {
                        val instant = 1.0 / dt
                        smoothedFps = if (smoothedFps <= 0.0) instant else smoothedFps * 0.9 + instant * 0.1
                    }
                }
            }
            return smoothedFps.toInt()
        }

    override val fov: Float get() = mc.gameSettings.fovSetting

    // 1.7.10 没有 NetHandlerPlayClient.getPlayerInfo(uuid).responseTime（那是 1.8 才有的），
    // 单人也不该有延迟；这个值只用来显示，取不到就一直是 0。
    override val pingMs: Int get() = 0

    override val skyDarken: Int? get() = mc.theWorld?.skylightSubtracted

    // 旧版没有方块标签，同时识别树叶类和树叶材质，避免树冠产生室内回声。
    private fun isAcousticLeaves(block: Block): Boolean =
        block is BlockLeaves || block.material == Material.leaves

    private val absorptionCache = java.util.IdentityHashMap<Block, Float>()

    override fun soundAbsorption(x: Int, y: Int, z: Int): Float {
        val world = mc.theWorld ?: return -1f
        val block = world.getBlock(x, y, z)
        return absorptionCache.getOrPut(block) {
            val material = block.material
            if (isAcousticLeaves(block) || material.isLiquid || !material.blocksMovement()) -1f else when (block.stepSound) {
                Block.soundTypeStone, Block.soundTypePiston -> 0.03f
                Block.soundTypeMetal, Block.soundTypeAnvil -> 0.04f
                Block.soundTypeGlass -> 0.05f
                Block.soundTypeWood, Block.soundTypeLadder -> 0.12f
                Block.soundTypeGrass -> 0.3f
                Block.soundTypeGravel, Block.soundTypeSand -> 0.35f
                Block.soundTypeSnow -> 0.5f
                Block.soundTypeCloth -> 0.7f
                else -> 0.1f
            }
        }
    }

    // 1.7.10 没有 World.isRainingAt(x,y,z)，最接近的原版方法是 canLightningStrikeAt：
    // 世界在下雨 + 头顶看得见天 + 这一格高于降水高度 + 生物群系真的有雨（雪原按原版算没雨）。
    private fun isRainingAt(world: World, x: Int, y: Int, z: Int): Boolean = world.canLightningStrikeAt(x, y, z)

    override val environment: com.Lilith.FMusic.dango.platform.ListenerEnvironment?
        get() {
            val p = mc.thePlayer ?: return null
            val world = mc.theWorld ?: return null
            val eyeY = p.posY + p.getEyeHeight()
            val eyeX = MathHelper.floor_double(p.posX)
            val eyeBlockY = MathHelper.floor_double(eyeY)
            val eyeZ = MathHelper.floor_double(p.posZ)
            // 闭塞程度：往 14 个方向各看 6 格，碰到实心方块（不算空气和液体）的比例
            var hits = 0
            val d = 0.57735
            val dirs = arrayOf(
                doubleArrayOf(1.0, 0.0, 0.0), doubleArrayOf(-1.0, 0.0, 0.0), doubleArrayOf(0.0, 1.0, 0.0),
                doubleArrayOf(0.0, -1.0, 0.0), doubleArrayOf(0.0, 0.0, 1.0), doubleArrayOf(0.0, 0.0, -1.0),
                doubleArrayOf(d, d, d), doubleArrayOf(d, d, -d), doubleArrayOf(-d, d, d), doubleArrayOf(-d, d, -d),
                doubleArrayOf(d, -d, d), doubleArrayOf(d, -d, -d), doubleArrayOf(-d, -d, d), doubleArrayOf(-d, -d, -d),
            )
            for (dir in dirs) {
                for (step in 1..6) {
                    val bx = MathHelper.floor_double(p.posX + dir[0] * step)
                    val by = MathHelper.floor_double(eyeY + dir[1] * step)
                    val bz = MathHelper.floor_double(p.posZ + dir[2] * step)
                    val block = world.getBlock(bx, by, bz)
                    if (!world.isAirBlock(bx, by, bz) && !block.material.isLiquid && !isAcousticLeaves(block)) {
                        hits++
                        break
                    }
                }
            }
            val rain = when {
                !isRainingAt(world, eyeX, eyeBlockY, eyeZ) -> 0f
                world.isThundering -> 1f
                else -> 0.5f
            }
            return com.Lilith.FMusic.dango.platform.ListenerEnvironment(
                p.isInsideOfMaterial(Material.water), world.canBlockSeeTheSky(eyeX, eyeBlockY, eyeZ), hits / dirs.size.toFloat(), rain)
        }

    override val player: PlayerPose?
        get() {
            val p = mc.thePlayer ?: return null
            return PlayerPose(p.posX, p.posY + p.getEyeHeight(), p.posZ, p.rotationYaw, p.rotationPitch)
        }

    override fun showChatMessage(message: String, copyText: String, copyHint: String): Boolean {
        val player = mc.thePlayer ?: return false
        // 1.7.10 里客户端玩家加聊天行的方法是 addChatComponentMessage（EntityPlayerSP 会转发给 GuiNewChat）
        player.addChatComponentMessage(LegacyChatCopy.message(message, copyText, copyHint))
        return true
    }

    override fun openMusicScreen() {
        mc.displayGuiScreen(LegacyMusicScreen(mc.currentScreen))
    }
}
