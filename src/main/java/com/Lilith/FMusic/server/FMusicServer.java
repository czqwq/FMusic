package com.Lilith.FMusic.server;

import java.io.File;

import net.kyori.adventure.text.Component;
import net.minecraft.command.ServerCommandManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.common.MinecraftForge;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.Lilith.FMusic.server.core.FMusic;
import com.Lilith.FMusic.server.core.music.PlayMusic;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLEventChannel;
import cpw.mods.fml.common.network.NetworkRegistry;

public class FMusicServer {

    public static final Logger LOGGER = LogManager.getLogger("FMusic Server");
    public static MinecraftServer server;
    public static FMLEventChannel channel;

    public static IChatComponent parse(Component input) {
        // adventure 4.17+ 的 GsonComponentSerializer 输出新版 JSON 格式
        // (click_event/command), MC 1.7.10 只识别旧格式 (clickEvent/value),
        // 因此使用自写的 1.7.10 兼容序列化器
        String json = ChatComponentSerializer.serialize(input);
        return IChatComponent.Serializer.func_150699_a(json);
    }

    public void commonSetup(final FMLPreInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance()
            .bus()
            .register(this);
        channel = NetworkRegistry.INSTANCE.newEventDrivenChannel("fmusic:channel");

        FMusic.log = new LogForge();
        FMusic.side = new SideForge();

        new FMusic().init(new File(FMusic.SERVER_DIR));
    }

    public void onServerStarted(FMLServerStartedEvent event) {
        FMusic.start();
    }

    public void onServerStarting(FMLServerStartingEvent event) {
        server = event.getServer();

        ServerCommandManager commandManager = (ServerCommandManager) server.getCommandManager();
        commandManager.registerCommand(new CommandForge());
    }

    public void onServerStopping(FMLServerStoppingEvent event) {
        FMusic.stop();
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PlayMusic.removeNowPlayPlayer(event.player.getCommandSenderName());
    }

    @SubscribeEvent
    public void onServerTickEvent(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Tasks.tick();
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        FMusic.joinPlay(event.player.getCommandSenderName());
    }
}
