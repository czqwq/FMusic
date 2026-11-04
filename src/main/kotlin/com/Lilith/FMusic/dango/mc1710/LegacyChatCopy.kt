package com.Lilith.FMusic.dango.mc1710

import net.minecraft.client.gui.GuiScreen
import net.minecraft.event.ClickEvent
import net.minecraft.event.HoverEvent
import net.minecraft.util.ChatComponentText
import net.minecraft.util.ChatStyle
import net.minecraft.util.EnumChatFormatting
import net.minecraft.util.IChatComponent
import org.lwjgl.input.Mouse
import java.util.IdentityHashMap

/** 1.8.9 没有 COPY_TO_CLIPBOARD；只由本地鼠标事件处理自己创建的链接。 */
object LegacyChatCopy {
    private val copies = IdentityHashMap<ClickEvent, String>()

    fun message(text: String, copyText: String, copyHint: String): IChatComponent {
        val at = text.indexOf(copyText)
        if (copyText.isEmpty() || at < 0) return ChatComponentText(text)
        val click = ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, copyText)
        copies[click] = copyText
        val style = ChatStyle().setColor(EnumChatFormatting.AQUA).setUnderlined(true).setChatClickEvent(click)
        if (copyHint.isNotEmpty()) style.setChatHoverEvent(HoverEvent(HoverEvent.Action.SHOW_TEXT, ChatComponentText(copyHint)))
        return ChatComponentText(text.substring(0, at))
            .appendSibling(ChatComponentText(copyText).setChatStyle(style))
            .appendSibling(ChatComponentText(text.substring(at + copyText.length)))
    }

    // 按事件对象身份匹配，服务器发来的同名 SUGGEST_COMMAND 不会被接管。
    fun copyValue(event: ClickEvent?): String? = copies[event]

    /**
     * 聊天栏里点了我们自己的「点击复制」链接：把内容送进剪贴板并返回 true，
     * 由调用方（`MixinGuiChat#mouseClicked` 的 HEAD 注入）取消原版处理。
     *
     * 上游 1.8.9 是挂在 `GuiScreenEvent.MouseInputEvent.Pre` 上做「复制 + e.isCanceled = true」，
     * 1.7.10 的 Forge 没有这个事件，所以改成 mixin 拦 `GuiChat.mouseClicked`——
     * 行为完全一致：只复制，不会再把同一段文字填进聊天输入框。
     *
     * 调用方要保证只在「按下左键」时进来（原版 mouseClicked 本身就是按下才调）。
     */
    @JvmStatic
    fun handleClick(mouseButton: Int): Boolean {
        if (mouseButton != 0) return false
        // 1.7.10 里查「鼠标下面是哪一段聊天文字」的方法是 GuiNewChat.func_146236_a
        // （1.8.9 是 GuiChat.getChatComponent），坐标同样用原始窗口像素，
        // 和原版 GuiChat.mouseClicked 里的调用方式保持一字不差。
        val chat = mc.ingameGUI.chatGUI
        val component = chat.func_146236_a(Mouse.getX(), Mouse.getY()) ?: return false
        val value = copies[component.chatStyle.chatClickEvent] ?: return false
        GuiScreen.setClipboardString(value)
        return true
    }
}
