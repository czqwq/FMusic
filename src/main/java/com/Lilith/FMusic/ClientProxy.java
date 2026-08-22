package com.Lilith.FMusic;

import com.Lilith.FMusic.client.FMusic;

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
    }

    @Override
    public void loadComplete(FMLLoadCompleteEvent event) {
        // 所有模组加载完成后,声音系统已就绪,初始化 FMusic 核心
        fMusicClient.test(event);
    }
}
