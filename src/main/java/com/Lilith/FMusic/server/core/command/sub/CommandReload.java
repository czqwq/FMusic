package com.Lilith.FMusic.server.core.command.sub;

import com.Lilith.FMusic.Config;
import com.Lilith.FMusic.server.core.FMusic;
import com.Lilith.FMusic.server.core.command.ACommand;

public class CommandReload extends ACommand {

    @Override
    public void execute(Object sender, String name, String[] args) {
        FMusic.side.reload();
        // 刷新 config/FMusic.cfg (pause_at_freeze 等)
        Config.reload();
        FMusic.side.sendMessage(sender, "<gold>[FMusic]<white>已重读配置文件");
    }
}
