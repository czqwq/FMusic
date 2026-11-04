package com.Lilith.FMusic.dango.mc1710

import com.Lilith.FMusic.dango.DangoClient

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

    override fun initGui() {
        Keyboard.enableRepeatEvents(true)
        if (parent is GuiMainMenu) {
            parent.width = width
            parent.height = height
        }
        screen.opened()
    }

    override fun onGuiClosed() {
        Keyboard.enableRepeatEvents(false)
    }

    override fun doesGuiPauseGame() = false

    override fun updateScreen() {
        if (mc.theWorld == null && parent is GuiMainMenu) parent.updateScreen()
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        val menuBackdrop = mc.theWorld == null && parent is GuiMainMenu
        if (menuBackdrop) {
            // 1.7.10 没有 GlStateManager，直接用 GL 开关（等价于 GlStateManager.disableAlpha/enableAlpha）
            GL11.glDisable(GL11.GL_ALPHA_TEST)
            panoramaMethod.invoke(parent, mouseX, mouseY, partialTicks)
            GL11.glEnable(GL11.GL_ALPHA_TEST)
            // 与 GuiMainMenu 的两层渐变一致，避免开关时背景亮度变化。
            drawGradientRect(0, 0, width, height, -2130706433, 16777215)
            drawGradientRect(0, 0, width, height, 0, Int.MIN_VALUE)
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
        screen.keyTyped(typedChar, keyCode)
    }

    override fun handleKeyboardInput() {
        if (!Keyboard.getEventKeyState()) screen.keyReleased(Keyboard.getEventKey())
        super.handleKeyboardInput()
    }
}
