package com.Lilith.FMusic.server.core.command.sub;

import com.Lilith.FMusic.server.core.FMusic;
import com.Lilith.FMusic.server.core.command.ACommand;

public class CommandReload extends ACommand {

    @Override
    public void execute(Object sender, String name, String[] args) {
        FMusic.side.reload();
        FMusic.side.sendMessage(sender, "<gold>[FMusic]<white>已重读配置文件");
    }
}
