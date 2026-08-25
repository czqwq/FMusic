package com.Lilith.FMusic.server.core.command.sub;

import net.minecraft.util.StatCollector;

import com.Lilith.FMusic.server.core.FMusic;
import com.Lilith.FMusic.server.core.command.ACommand;
import com.Lilith.FMusic.server.core.music.PlayMusic;

public class CommandClearList extends ACommand {

    @Override
    public void execute(Object sender, String name, String[] args) {
        PlayMusic.clearIdleList();
        FMusic.side.sendMessage(sender, StatCollector.translateToLocal("fmusic.cmd.clearlist_ok"));
    }
}
