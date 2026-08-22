package com.Lilith.FMusic.server;

import net.kyori.adventure.text.Component;
import net.minecraft.util.IChatComponent;

import com.Lilith.FMusic.server.core.side.IFMusicLogger;

public class LogForge implements IFMusicLogger {

    @Override
    public void data(Component data) {
        IChatComponent textComponent = FMusicServer.parse(data);
        // 服务器实例在 FMLServerStartingEvent 才可用;
        // preInit 阶段(如 FMusic.init 的启动日志)回退到控制台日志,避免 NPE
        if (FMusicServer.server != null) {
            FMusicServer.server.addChatMessage(textComponent);
        } else {
            FMusicServer.LOGGER.info(textComponent.getUnformattedText());
        }
    }
}
