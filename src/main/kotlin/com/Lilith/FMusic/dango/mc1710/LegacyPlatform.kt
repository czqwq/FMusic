package com.Lilith.FMusic.dango.mc1710

import com.Lilith.FMusic.dango.DangoLogger
import com.Lilith.FMusic.dango.music.AudioOutput
import com.Lilith.FMusic.dango.platform.Platform
import com.Lilith.FMusic.dango.platform.PlayerPose
import com.Lilith.FMusic.dango.platform.PlayerKeyBinding
import com.Lilith.FMusic.dango.platform.VanillaMusicHold

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
import org.lwjgl.openal.AL
import org.lwjgl.openal.AL10
import org.lwjgl.openal.AL11
import org.lwjgl.openal.ALC10
import org.lwjgl.openal.ALCcontext

import java.nio.ByteBuffer
import java.nio.file.Path

/** 1.7.10 的游戏接口。键码本来就是 LWJGL 2 的，不用转换 */
object LegacyPlatform : Platform {

    override val gameDir: Path get() = mc.mcDataDir.toPath()

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

    // 装了 Custom Main Menu 时主菜单是它的 GuiCustom 而不是 GuiMainMenu，由可选兼容层认出来
    override val inMainMenu: Boolean
        get() = mc.currentScreen is GuiMainMenu || CustomMainMenuCompat.isMainMenuScreen(mc.currentScreen)

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

    // ---------------------------------------------------------------- 声音

    /**
     * 用游戏自己的 OpenAL 放歌（对应上游 modern 的 `GameAudioOutput`，这里用 1.7.10 的 LWJGL 2 绑定）。
     * 打不开（游戏声音关着、设备不支持、绑不上环境……）一律返回 null，调用方会退回 Java Sound。
     */
    override fun openAudioOutput(sampleRate: Int, channels: Int): AudioOutput? = try {
        LegacyOpenAlOutput.open(sampleRate, channels)
    } catch (t: Throwable) {
        DangoLogger.warn("Game OpenAL output unavailable: {}", t.toString())
        null
    }

    /** 本模组把 1.7.10 的 OpenAL 输出做出来了，设置里可以选（上游 1.8.9 平台没实现，那里是隐藏的） */
    override val hasGameAudio: Boolean get() = true

    private var holdingMusic = false

    /**
     * 原版背景音乐让路。
     *
     * 1.7.10 的 `MusicTicker` 把正在放的那首 `ISound` 存在**私有字段**里（生产环境还是 SRG 名，反射拿不到），
     * 既没有 1.8.9 的 `func_181557_a()`，也没有高版本的 `musicManager.stopPlaying()`：
     * `SoundHandler` 里能按类别停音乐的口子只有 `setSoundLevel(category, vol)`，而它内部
     * （`SoundManager#setSoundCategoryVolume`）对非 MASTER 类别并不用传进去的音量，而是重新读
     * `gameSettings` 里玩家自己的滑块 —— 也就是拿它静音没用。`stopSound(ISound)` 又只认对象身份。
     *
     * 所以这里只做两件靠谱的事：
     *   1. 让路开始时停一次正在放的声音（`stopSounds()`），原版音乐随之停掉。代价是那一瞬间
     *      别的短音效也会被掐掉 —— 本模组点歌系统的 `FMusic#stopPlayMusic()` 用的就是同一条路；
     *   2. 之后靠 [VanillaMusicHold.active] 拦住**新**的原版音乐（由 `ForgeBridge` 里的
     *      PlaySoundEvent17 钩子消费）。
     * 不做 SRG 反射：那种写法在生产环境必然拿不到，宁可少掐一首（它最多自然放完，几秒到几分钟）。
     */
    override fun holdVanillaMusic(hold: Boolean) {
        VanillaMusicHold.active = hold
        if (hold && !holdingMusic) {
            try {
                mc.soundHandler?.stopSounds()
            } catch (t: Throwable) {
                DangoLogger.warn("Stopping vanilla music failed: {}", t.toString())
            }
        }
        holdingMusic = hold
    }
}

/**
 * 用游戏自己的 OpenAL 流式放歌（1.7.10 / LWJGL 2）。
 *
 * 直接用游戏已经打开的声音环境和设备，只建一个自己的音源和几块缓冲，**不切换 / 不重建环境**：
 * 安卓启动器把 Java Sound 转到 OpenAL 时会另建环境，游戏音效就全部出错、变成杂音。
 *
 * 数据攒成约 50 ms 一块排队给音源，最多 [BUFFERS] 块（约 0.25 秒）；排满了就等最早一块放完。
 * 逻辑与上游 modern 的 `GameAudioOutput` 一致，只是调用换成 LWJGL 2 的 `org.lwjgl.openal.*`。
 *
 * 两条 1.7.10 特有的注意点：
 *   - OpenAL 的"当前环境"是按线程记的（OpenAL Soft 老版本是进程级、新版按线程，两种都可能）：
 *     解码线程从没碰过声音系统，所以每个线程第一次调 AL 之前先 `alcMakeContextCurrent` 一下，
 *     把游戏自己的环境设成当前（用的是同一个环境对象，不影响游戏那条线程 / 原版自己的流式音乐）；
 *   - 游戏重启声音系统（F3+T、换输出设备）会 `AL.destroy()` + `AL.create()`：老的音源名失效，
 *     [alive] 一看就知道，[lost] 变 true，播放器会重新开一个输出接着放。
 */
private class LegacyOpenAlOutput private constructor(
    /**
     * 游戏自己的声音环境。某些安卓启动器的 LWJGL 里没有 `AL.getContext()`，这时是 null：
     * 那就干脆不绑，直接试（老版本 OpenAL 的"当前环境"是进程级的，不绑也能用；真用不了会在建音源那一步失败）
     */
    private val context: ALCcontext?,
    private val sampleRate: Int,
    private val channels: Int,
) : AudioOutput {

    private val format = if (channels >= 2) AL10.AL_FORMAT_STEREO16 else AL10.AL_FORMAT_MONO16
    private val frameBytes = 2 * channels.coerceIn(1, 2)
    private val chunkBytes = (sampleRate / 20).coerceAtLeast(256) * frameBytes
    private val lock = Object()

    private val source: Int
    private val freeBuffers = ArrayDeque<Int>()
    /** 排在音源里的每一块有多少帧（和音源里的顺序一样） */
    private val queuedFrames = ArrayDeque<Int>()
    private var createdBuffers = 0
    /** 已经放完、从音源里取出来的帧数 */
    private var playedFrames = 0L

    private val pending: ByteBuffer = ByteBuffer.allocateDirect(chunkBytes)

    @Volatile private var running = false
    @Volatile private var closed = false
    /** 声音系统重启前最后一次读到的播放位置 */
    private var lastPositionMicros = 0L

    /** 这条线程有没有把游戏的声音环境设成"当前" */
    private val bound = ThreadLocal<Boolean>()

    init {
        // 先把游戏的声音环境绑到当前线程，再建音源：OpenAL 的"当前环境"可能是按线程算的，
        // 解码线程从没碰过声音系统，不先绑的话 alGenSources 直接失败（拿不到音源就退回 Java Sound）。
        bind()
        source = AL10.alGenSources()
        check(AL10.alGetError() == AL10.AL_NO_ERROR) { "OpenAL source unavailable" }
    }

    /** 当前线程还没绑过的话，把游戏自己的环境设成当前（同一个环境对象，不动游戏的设置） */
    fun bind() {
        if (bound.get() == java.lang.Boolean.TRUE) return
        val ctx = context
        if (ctx != null) {
            try {
                ALC10.alcMakeContextCurrent(ctx)
            } catch (_: Throwable) {
            }
        }
        bound.set(java.lang.Boolean.TRUE)
    }

    /** 游戏的声音系统还在、我们这个音源也还在 */
    private fun alive(): Boolean = try {
        if (!AL.isCreated()) false else AL10.alIsSource(source)
    } catch (_: Throwable) {
        // 启动器精简过 LWJGL（连 AL.isCreated 都没有）：那就只看音源还在不在
        try {
            AL10.alIsSource(source)
        } catch (_: Throwable) {
            false
        }
    }

    override val positionMicros: Long
        get() = synchronized(lock) {
            if (closed) return 0L
            if (!alive()) return lastPositionMicros
            val offset = try {
                bind()
                AL10.alGetSourcei(source, AL11.AL_SAMPLE_OFFSET).toLong()
            } catch (_: Throwable) {
                return lastPositionMicros
            }
            ((playedFrames + offset) * 1_000_000L / sampleRate).also { lastPositionMicros = it }
        }

    override val lost: Boolean get() = !closed && !alive()

    override val isRunning: Boolean get() = running

    override fun start() = synchronized(lock) {
        running = true
        if (!closed && alive() && queuedFrames.isNotEmpty()) {
            try {
                bind()
                AL10.alSourcePlay(source)
            } catch (_: Throwable) {
            }
        }
    }

    override fun stop() = synchronized(lock) {
        running = false
        if (!closed && alive()) {
            try {
                bind()
                AL10.alSourcePause(source)
            } catch (_: Throwable) {
            }
        }
    }

    override fun write(data: ByteArray, offset: Int, length: Int) {
        var off = offset
        var left = length - length % frameBytes
        while (left > 0) {
            // 切歌 / 拖进度时旧的解码线程还会写一下：已经关了就直接丢掉（和 Java Sound 一样）
            if (closed) return
            val n = minOf(left, pending.remaining())
            pending.put(data, off, n)
            off += n
            left -= n
            if (!pending.hasRemaining()) submit()
        }
    }

    /** 把攒好的一块交给音源；缓冲都在用就等最早一块放完 */
    private fun submit() {
        val buffer = takeBuffer() ?: return
        synchronized(lock) {
            if (closed) return
            checkContext()
            pending.flip()
            val frames = pending.remaining() / frameBytes
            AL10.alBufferData(buffer, format, pending, sampleRate)
            pending.clear()
            AL10.alSourceQueueBuffers(source, buffer)
            queuedFrames.addLast(frames)
            // 刚开始或者断过流（数据没跟上）：音源停了就重新开始放
            if (running && AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) AL10.alSourcePlay(source)
            check(AL10.alGetError() == AL10.AL_NO_ERROR) { "OpenAL playback error" }
        }
    }

    private fun takeBuffer(): Int? {
        while (true) {
            synchronized(lock) {
                if (closed) return null
                checkContext()
                reclaim()
                freeBuffers.removeFirstOrNull()?.let { return it }
                if (createdBuffers < BUFFERS) {
                    createdBuffers++
                    return AL10.alGenBuffers()
                }
            }
            Thread.sleep(5)
        }
    }

    /** 放完的缓冲取回来，记到已经放出去的时长里 */
    private fun reclaim() {
        var done = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED)
        while (done-- > 0) {
            freeBuffers.addLast(AL10.alSourceUnqueueBuffers(source))
            playedFrames += queuedFrames.removeFirstOrNull() ?: 0
        }
    }

    /** 游戏重启了声音系统（F3+T、换了输出设备）：原来的音源已经没了，抛出去让播放器换一个新的输出接着放 */
    private fun checkContext() {
        bind()
        check(alive()) { "Game audio was restarted" }
    }

    override fun drain() {
        if (pending.position() > 0) {
            // 最后不满一块的也送进去
            submit()
        }
        while (true) {
            synchronized(lock) {
                if (closed) return
                checkContext()
                reclaim()
                if (queuedFrames.isEmpty()) return
                if (running && AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) AL10.alSourcePlay(source)
            }
            Thread.sleep(10)
        }
    }

    override fun flush() {
        synchronized(lock) {
            if (closed) return
            pending.clear()
            if (!alive()) return
            try {
                bind()
                AL10.alSourceStop(source)
                reclaim()
            } catch (_: Throwable) {
            }
        }
    }

    override fun close() = synchronized(lock) {
        if (closed) return
        closed = true
        running = false
        try {
            if (alive()) {
                bind()
                // 停下以后排着的缓冲都算放完了：全部取回来，和音源一起删掉
                AL10.alSourceStop(source)
                reclaim()
                AL10.alSourcei(source, AL10.AL_BUFFER, 0)
                AL10.alDeleteSources(source)
                for (b in freeBuffers) AL10.alDeleteBuffers(b)
                freeBuffers.clear()
                AL10.alGetError()
            }
        } catch (_: Throwable) {
        }
    }

    override fun setVolume(volume: Float) = synchronized(lock) {
        if (!closed && alive()) {
            try {
                bind()
                AL10.alSourcef(source, AL10.AL_GAIN, volume.coerceIn(0f, 1f))
            } catch (_: Throwable) {
            }
        }
    }

    companion object {
        private const val BUFFERS = 5

        /** 游戏的声音系统开着就能用；没开（声音关掉 / 设备打不开等）返回 null */
        fun open(sampleRate: Int, channels: Int): LegacyOpenAlOutput? {
            if (sampleRate <= 0 || channels <= 0) return null
            var context: ALCcontext? = null
            val usable = try {
                if (AL.isCreated()) {
                    context = AL.getContext()
                    true
                } else {
                    false   // 游戏没开声音系统：没有环境可用，交给 Java Sound
                }
            } catch (_: Throwable) {
                // 启动器精简过 LWJGL（没有 isCreated / getContext）：当作"不知道"，不绑、照样试一次，
                // 建不出音源时就会退回 Java Sound
                true
            }
            if (!usable) return null
            AL10.alGetError()
            val out = LegacyOpenAlOutput(context, sampleRate, channels.coerceIn(1, 2))
            out.bind()
            check(out.source != 0 && AL10.alGetError() == AL10.AL_NO_ERROR) { "OpenAL source unavailable" }
            AL10.alSourcei(out.source, AL10.AL_SOURCE_RELATIVE, AL10.AL_TRUE)
            AL10.alSource3f(out.source, AL10.AL_POSITION, 0f, 0f, 0f)
            AL10.alSourcef(out.source, AL10.AL_ROLLOFF_FACTOR, 0f)
            check(AL10.alGetError() == AL10.AL_NO_ERROR) { "OpenAL source setup failed" }
            return out
        }
    }
}
