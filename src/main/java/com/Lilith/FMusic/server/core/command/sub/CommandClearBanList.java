package com.Lilith.FMusic.server.core.command.sub;

import com.Lilith.FMusic.server.core.FMusic;
import com.Lilith.FMusic.server.core.command.ACommand;
import com.Lilith.FMusic.server.core.saves.BanSave;

public class CommandClearBanList extends ACommand {

    @Override
    public void execute(Object sender, String name, String[] args) {
        BanSave.clearBan();
        FMusic.side.sendMessage(sender, "<gold>[FMusic]<white>禁止点歌列表已清空");
    }
}
