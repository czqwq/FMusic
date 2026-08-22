package com.Lilith.FMusic.server.core.command.sub;

import com.Lilith.FMusic.server.core.FMusic;
import com.Lilith.FMusic.server.core.command.ICommand;
import com.Lilith.FMusic.server.core.saves.BanSave;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CommandUnbanPlayer implements ICommand {

    @Override
    public void execute(Object sender, String name, String[] args) {
        if (args.length != 2) {
            FMusic.side.sendMessage(sender, FMusic.getMessage().command.error);
            return;
        }
        BanSave.removeBanPlayer(args[1]);
        FMusic.side.sendMessage(sender, "<gold>[FMusic]<white>已解封玩家" + args[1] + "点歌");
    }

    @Override
    public List<String> tab(Object player, String name, String[] args, int index) {
        if (args.length == index || (args.length == index + 1)) {
            return new ArrayList<>(BanSave.getBanPlayers());
        }

        return Collections.emptyList();
    }
}
