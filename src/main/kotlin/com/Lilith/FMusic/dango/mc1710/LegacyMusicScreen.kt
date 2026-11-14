package com.Lilith.FMusic.dango.mc1710

import com.Lilith.FMusic.dango.DangoClient
import com.Lilith.FMusic.dango.platform.Keys

import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.gui.GuiMainMenu
import org.lwjgl.input.Keyboard
import org.lwjgl.input.Mouse
import org.lwjgl.opengl.GL11

/** 内置播放器在 1.7.10 里的外壳：把原版界面的事件转给共用的 [com.Lilith.FMusic.dango.music.MusicScreen] */
class LegacyMusicScreen(
    /** 关闭后回到哪个界面（null = 回到游戏） */
    private val parent: GuiScreen? = null,
) : GuiScreen() {

    private val screen = DangoClient.newMusicScreen { mc.displayGuiScreen(parent) }

    // 复用已初始化的主菜单，仅调用它的全景绘制，不画按钮、标题或 Realms。
    private val panoramaMethod by lazy {
        GuiMainMenu::class.java.declaredMethods.first {
            it.name == "renderSkybox" || it.name == "func_73971_c"
        }.apply { isAccessible = true }
    }

    /**
     * 主菜单界面：原版 `GuiMainMenu`，或装了 Custom Main Menu 时它的 `GuiCustom`（可选兼容层）。
     * 只有没有世界时才算；世界里的其它界面不接管播放器打开键，也不需要画菜单背景。
     */
    private fun menuParent(): GuiScreen? {
        if (mc.theWorld != null) return null
        val current = parent ?: return null
        return if (current is GuiMainMenu || CustomMainMenuCompat.isMainMenuScreen(current)) current else null
    }

    override fun initGui() {
        Keyboard.enableRepeatEvents(true)
        // 切界面时 lwjgl3ify 会把 SDL 文本输入的计数清零（GuiOpenEvent → resetTextInput），我们跟着复位
        LegacyTextInput.reset()
        // 输入法（lwjgl3ify 走 SDL 文本输入）：组字结果只会以 TextEvent 送进来，
        // 一个字一个字地喂给界面，和本地按键走同一个入口，所以焦点、光标这些逻辑不用分两套。
        LegacyTextInput.subscribe { text ->
            if (mc.currentScreen === this) text.forEach { screen.keyTyped(it, Keys.NONE) }
        }
        menuParent()?.let {
            it.width = width
            it.height = height
        }
        screen.opened()
    }

    override fun onGuiClosed() {
        Keyboard.enableRepeatEvents(false)
        LegacyTextInput.reset()
        LegacyTextInput.unsubscribe()
    }

    override fun doesGuiPauseGame() = false

    override fun updateScreen() {
        // 主菜单自己要按帧推进（原版全景 / CMM 的全景 timer、幻灯片），播放器只是叠在它上面
        menuParent()?.updateScreen()
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        // 每帧同步"有没有输入框在打字"：lwjgl3ify 下据此开关 SDL 文本输入，
        // 输入法的组字结果（中文 / 日文 / 韩文）才会进得来；界面里没有输入框在用时立刻关掉，
        // 免得影响别的界面（SDL 文本输入是窗口级的，开着会让别处的字符重复）。
        LegacyTextInput.setFocused(screen.textInputActive)
        val menu = menuParent()
        var menuBackdrop = menu != null
        if (menu is GuiMainMenu) {
            // 1.7.10 没有 GlStateManager，直接用 GL 开关（等价于 GlStateManager.disableAlpha/enableAlpha）
            GL11.glDisable(GL11.GL_ALPHA_TEST)
            panoramaMethod.invoke(menu, mouseX, mouseY, partialTicks)
            GL11.glEnable(GL11.GL_ALPHA_TEST)
            // 与 GuiMainMenu 的两层渐变一致，避免开关时背景亮度变化。
            drawGradientRect(0, 0, width, height, -2130706433, 16777215)
            drawGradientRect(0, 0, width, height, 0, Int.MIN_VALUE)
        } else if (menu != null) {
            // 可选兼容：装了 Custom Main Menu 时让它的 GuiCustom 自己画一遍主菜单当背景。
            // 没装/版本对不上时返回 false，退回下面没有背景的深色底路径（不会黑屏或自采样）。
            menuBackdrop = CustomMainMenuCompat.renderBackdrop(menu, mouseX, mouseY, partialTicks)
        }
        screen.render(menuBackdrop)
    }

    override fun mouseClicked(mouseX: Int, mouseY: Int, mouseButton: Int) {
        screen.mouseClicked(mouseButton)
    }

    override fun mouseClickMove(mouseX: Int, mouseY: Int, clickedMouseButton: Int, timeSinceLastClick: Long) {
        screen.mouseDragged()
    }

    // 1.7.10 没有 mouseReleased(mouseX, mouseY, state)：移动和抬起都走 mouseMovedOrUp，state == -1 表示只是移动。
    override fun mouseMovedOrUp(mouseX: Int, mouseY: Int, state: Int) {
        super.mouseMovedOrUp(mouseX, mouseY, state)
        if (state != -1) screen.mouseReleased()
    }

    override fun handleMouseInput() {
        super.handleMouseInput()
        val wheel = Mouse.getEventDWheel()
        if (wheel != 0) screen.scroll(wheel, Mouse.getEventX(), mc.displayHeight - Mouse.getEventY() - 1)
    }

    override fun keyTyped(typedChar: Char, keyCode: Int) {
        // SDL 文本输入开着的时候，可打印字符会同时从"按键事件"和"TextEvent"两条路进来：
        // 只认 TextEvent（ModularUI2 的输入框也是这么处理的），否则英文会输入两遍。
        // 回车 / Esc / 退格 / Ctrl 组合这类控制键不受影响，照旧按 keyCode 走。
        if (LegacyTextInput.textEventsActive && Keys.isAllowedChar(typedChar)) return
        screen.keyTyped(typedChar, keyCode)
    }

    override fun handleKeyboardInput() {
        if (!Keyboard.getEventKeyState()) {
            screen.keyReleased(Keyboard.getEventKey())
            // 纯 LWJGL 2（没装 lwjgl3ify）时，输入法组字结果只有这一条出口：
            // keyCode = 0 + keyState = false 的纯字符事件 —— 原版见到 keyState 是 false 就整条丢掉了。
            // lwjgl3ify 下这条永远是 0（组字结果已经在 TextEvent 里处理过），不会重复。
            if (LegacyTextInput.focused) {
                val ime = LegacyTextInput.imeCharOrNul()
                if (ime != '\u0000') screen.keyTyped(ime, Keys.NONE)
            }
        }
        super.handleKeyboardInput()
    }
}
