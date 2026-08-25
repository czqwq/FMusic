package com.Lilith.FMusic.server.core.command.sub;
import net.minecraft.util.StatCollector;

import com.Lilith.FMusic.server.core.FMusic;
import com.Lilith.FMusic.server.core.command.ACommand;
import com.Lilith.FMusic.server.core.saves.BanSave;

public class CommandClearBanList extends ACommand {

    @Override
    public void execute(Object sender, String name, String[] args) {
        BanSave.clearBan();
        FMusic.side.sendMessage(sender, StatCollector.translateToLocal("fmusic.cmd.clearban_ok"));
    }
}
