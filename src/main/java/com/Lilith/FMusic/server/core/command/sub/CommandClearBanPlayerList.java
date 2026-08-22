package com.Lilith.FMusic.server.core.command.sub;

import com.Lilith.FMusic.server.core.FMusic;
import com.Lilith.FMusic.server.core.command.ACommand;
import com.Lilith.FMusic.server.core.saves.BanSave;

public class CommandClearBanPlayerList extends ACommand {

    @Override
    public void execute(Object sender, String name, String[] args) {
        BanSave.clearBanPlayer();
        FMusic.side.sendMessage(sender, "<gold>[FMusic]<white>禁止玩家点歌列表已清空");
    }
}
