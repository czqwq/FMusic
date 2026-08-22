package com.Lilith.FMusic.server.core.command.sub;

import com.Lilith.FMusic.server.core.FMusic;
import com.Lilith.FMusic.server.core.command.ACommand;
import com.Lilith.FMusic.server.core.command.CommandEX;
import com.Lilith.FMusic.server.core.command.PermissionList;

public class CommandSearch extends ACommand {

    @Override
    public void execute(Object sender, String name, String[] args) {
        if (FMusic.getConfig().needPermission
            && !FMusic.side.checkPermission(sender, PermissionList.PERMISSION_SEARCH)) {
            FMusic.side.sendMessage(sender, FMusic.getMessage().search.noPer);
            return;
        }
        if (CommandEX.checkMoney(sender, name, FMusic.getConfig().cost.searchCost)) {
            return;
        }
        if (CommandEX.cost(sender, name, FMusic.getConfig().cost.searchCost, FMusic.getMessage().cost.search)) {
            return;
        }

        if (args.length < 2) {
            FMusic.side.sendMessage(sender, FMusic.getMessage().search.emptySearch);
            return;
        }

        FMusic.side.sendMessage(sender, FMusic.getMessage().search.startSearch);
        CommandEX.searchMusic(sender, name, args, false);
    }
}
