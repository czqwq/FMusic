package com.Lilith.FMusic.server.core.command.sub;

import com.Lilith.FMusic.server.core.FMusic;
import com.Lilith.FMusic.server.core.command.ACommand;
import com.Lilith.FMusic.server.core.command.CommandEX;
import com.Lilith.FMusic.server.core.command.PermissionList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CommandSearchApi extends ACommand {

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
            FMusic.side.sendMessage(sender, FMusic.getMessage().musicPlay.error2);
            return;
        }
        if (args.length < 3) {
            FMusic.side.sendMessage(sender, FMusic.getMessage().search.emptySearch);
            return;
        }

        String[] newArgs = new String[args.length - 1];
        System.arraycopy(args, 1, newArgs, 0, newArgs.length);

        FMusic.side.sendMessage(sender, FMusic.getMessage().search.startSearch);
        CommandEX.searchMusicApi(sender, name, newArgs, false);
    }

    @Override
    public List<String> tab(Object player, String name, String[] args, int index) {
        if (index == 1 && args.length <= index + 1) {
            return new ArrayList<>(FMusic.MUSIC_APIS.keySet());
        }
        return Collections.emptyList();
    }
}
