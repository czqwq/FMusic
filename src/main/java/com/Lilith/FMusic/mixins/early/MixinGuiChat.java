package com.Lilith.FMusic.mixins.early;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.Lilith.FMusic.client.command.ClientCommandHint;

/**
 * 在聊天输入框上方实时绘制 /music 用法提示 (输入即提示)。
 *
 * <p>
 * 只读输入框文本 + 画一行字, 不修改原版任何状态。inputField 声明在 GuiChat 上, 可以直接
 * {@code @Shadow} (reobf 后为 field_146415_a, 见构建产物); mc/width/height 声明在父类
 * GuiScreen 上, 不能 @Shadow, 用转型访问。所有异常都被吞掉: 提示只是锦上添花。
 */
@Mixin(GuiChat.class)
public class MixinGuiChat {

    @Shadow
    protected GuiTextField inputField;

    @Inject(method = "drawScreen", at = @At("TAIL"))
    private void fmusic$drawCommandHint(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        try {
            String hint = ClientCommandHint.hintFor(inputField.getText());
            if (hint == null || hint.isEmpty()) {
                return;
            }

            GuiScreen screen = (GuiScreen) (Object) this;
            Minecraft mc = screen.mc;
            FontRenderer font = mc == null ? null : mc.fontRenderer;
            if (font == null) {
                return;
            }

            // 输入框位于 height - 12, 提示画在其正上方一行
            int y = screen.height - 24;
            int maxWidth = screen.width - 8;
            String text = hint;
            while (text.length() > 4 && font.getStringWidth(text) > maxWidth) {
                text = text.substring(0, text.length() - 2);
            }

            int textWidth = font.getStringWidth(text);
            int x = Math.max(2, (screen.width - textWidth) / 2);
            Gui.drawRect(x - 3, y - 2, x + textWidth + 3, y + 9, 0x90000000);
            font.drawStringWithShadow(text, x, y, 0xFFFFFF);
        } catch (Throwable ignored) {
            // 任何失败都不影响聊天界面
        }
    }
}
