package com.Lilith.FMusic;

import com.Lilith.FMusic.server.FMusicServer;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.common.event.FMLServerStoppingEvent;

public class CommonProxy {

    /**
     * FMusic 服务端逻辑实例 (原 @Mod fmusic_server)。
     * 通过 commonSetup 将自身注册到 Forge/FML 事件总线。
     */
    protected final FMusicServer fMusicServer = new FMusicServer();

    // preInit "Run before anything else. Read your config, create blocks, items, etc, and register them with the
    // GameRegistry." (Remove if not needed)
    public void preInit(FMLPreInitializationEvent event) {
        Config.synchronizeConfiguration(event.getSuggestedConfigurationFile());

        // FMusic 服务端初始化: 日志/侧实现/配置文件/网络信道/事件注册
        // (客户端集成服务器同样会执行,与原始 FMusicServer 模组行为一致)
        fMusicServer.commonSetup(event);
    }

    // load "Do your mod setup. Build whatever data structures you care about. Register recipes." (Remove if not needed)
    public void init(FMLInitializationEvent event) {}

    // postInit "Handle interaction with other mods, complete your setup based on this." (Remove if not needed)
    public void postInit(FMLPostInitializationEvent event) {}

    // register server commands in this event handler (Remove if not needed)
    public void serverStarting(FMLServerStartingEvent event) {
        // FMusic 服务端: 记录服务器实例并注册 /music 指令
        fMusicServer.onServerStarting(event);
    }

    // fired when the server has started
    public void serverStarted(FMLServerStartedEvent event) {
        fMusicServer.onServerStarted(event);
    }

    // fired when the server is stopping
    public void serverStopping(FMLServerStoppingEvent event) {
        fMusicServer.onServerStopping(event);
    }

    // fired after all mods have finished loading
    public void loadComplete(FMLLoadCompleteEvent event) {}
}
