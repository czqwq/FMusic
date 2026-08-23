package com.Lilith.FMusic.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;

/**
 * 延迟 N tick 打开 GUI (照 PowerGoggles 的 DelayedGuiDisplayTicker 模式,
 * 避免指令执行与 GUI 打开同 tick 的冲突)
 */
public class DelayedGuiDisplayTicker {

    private final GuiScreen guiScreen;
    private int ticks;

    private DelayedGuiDisplayTicker(GuiScreen guiScreen, int delay) {
        this.guiScreen = guiScreen;
        this.ticks = delay;
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        this.ticks--;

        if (this.ticks < 0) {
            Minecraft.getMinecraft()
                .displayGuiScreen(this.guiScreen);
            FMLCommonHandler.instance()
                .bus()
                .unregister(this);
        }
    }

    public static void create(GuiScreen guiScreen, int delay) {
        FMLCommonHandler.instance()
            .bus()
            .register(new DelayedGuiDisplayTicker(guiScreen, delay));
    }
}
