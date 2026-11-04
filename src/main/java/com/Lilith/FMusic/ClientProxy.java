package com.Lilith.FMusic;

import net.minecraftforge.client.ClientCommandHandler;

import com.Lilith.FMusic.client.FMusic;
import com.Lilith.FMusic.client.command.CommandFMusic;
import com.Lilith.FMusic.dango.mc1710.ForgeBridge;
import com.Lilith.FMusic.dango.mc1710.LegacyDango;
import com.Lilith.FMusic.dango.mc1710.LegacyKeyBindings;

import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {

    // Override CommonProxy methods here, if you want a different behaviour on the client (e.g. registering renders).
    // Don't forget to call the super methods as well.

    /**
     * FMusic 客户端逻辑实例 (原 @Mod fmusic_client)。
     * 通过 preload 将自身注册到 Forge/FML 事件总线与网络信道。
     */
    private final FMusic fMusicClient = new FMusic();

    @Override
    public void preInit(FMLPreInitializationEvent event) {
        super.preInit(event);

        // FMusic 客户端初始化: 注册渲染/声音/数据包/网络信道等事件
        fMusicClient.preload(event);

        // 客户端指令: /fmusic pause_at_freeze <true/false>
        ClientCommandHandler.instance.registerCommand(new CommandFMusic());

        // 内置客户端播放器 (移植自 DangoMusic)。它和服务端推送的点歌系统是两套独立播放系统,
        // 同时只允许一个出声 (见 DangoAudioArbiter), 由 ForgeBridge 每 tick 仲裁。
        // 顺序：先注册按键 -> 启动核心 (注册模块 + 读回配置) -> 挂事件桥。
        // 着色器与菜单字体的实际创建推迟到第一次客户端 tick (那时 OpenGL 才可用), 见 LegacyGl.init()。
        LegacyKeyBindings.register();
        LegacyDango.start();
        ForgeBridge.register();
    }

    @Override
    public void loadComplete(FMLLoadCompleteEvent event) {
        // 所有模组加载完成后,声音系统已就绪,初始化 FMusic 核心
        fMusicClient.test(event);
    }
}
