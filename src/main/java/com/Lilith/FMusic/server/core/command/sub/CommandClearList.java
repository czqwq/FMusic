package com.Lilith.FMusic.server.core.command.sub;

import com.Lilith.FMusic.server.core.FMusic;
import com.Lilith.FMusic.server.core.command.ACommand;
import com.Lilith.FMusic.server.core.music.PlayMusic;

public class CommandClearList extends ACommand {

    @Override
    public void execute(Object sender, String name, String[] args) {
        PlayMusic.clearIdleList();
        FMusic.side.sendMessage(sender, "<gold>[FMusic]<white>空闲音乐列表已清空");
    }
}
