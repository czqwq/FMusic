package com.Lilith.FMusic.server.core.command.sub;

import com.Lilith.FMusic.server.core.FMusic;
import com.Lilith.FMusic.server.core.command.ACommand;
import com.Lilith.FMusic.server.core.command.CommandEX;
import com.Lilith.FMusic.server.core.command.PermissionList;
import com.Lilith.FMusic.server.core.music.MusicSearch;
import com.Lilith.FMusic.server.core.objs.message.ARG;
import com.Lilith.FMusic.server.core.objs.music.SearchPageObj;
import com.Lilith.FMusic.server.core.saves.SaveTask;
import com.Lilith.FMusic.server.core.utils.Function;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CommandSelect extends ACommand {

    @Override
    public void execute(Object sender, String name, String[] args) {
        if (FMusic.getConfig().needPermission
            && !FMusic.side.checkPermission(sender, PermissionList.PERMISSION_SEARCH)) {
            FMusic.side.sendMessage(sender, FMusic.getMessage().search.noPer);
            return;
        }
        SearchPageObj obj = MusicSearch.getSearch(name);
        if (obj == null) {
            FMusic.side.sendMessage(sender, FMusic.getMessage().search.emptySearch);
        } else if (!args[1].isEmpty() && Function.isInteger(args[1])) {
            int a = Integer.parseInt(args[1]);
            if (a == 0) {
                FMusic.side.sendMessage(sender, FMusic.getMessage().search.errorNum);
                return;
            }
            String id = obj.getSong((obj.getPage() * 10) + (a - 1));
            FMusic.side.sendMessage(sender, FMusic.getMessage().search.choice.replace(ARG.index, "" + a));
            SaveTask.task(() -> CommandEX.addMusic(sender, name, obj.getApi(), id));
            MusicSearch.removeSearch(name);
        } else {
            FMusic.side.sendMessage(sender, FMusic.getMessage().search.errorNum);
        }
    }

    @Override
    public List<String> tab(Object player, String name, String[] args, int index) {
        if (args.length == 1 || (args.length == 2)) {
            List<String> list = new ArrayList<>();
            SearchPageObj obj = MusicSearch.getSearch(name);
            if (obj != null) {
                for (int a = 0; a < obj.getIndex(); a++) {
                    list.add(String.valueOf(a + 1));
                }
            }
            return list;
        }
        return Collections.emptyList();
    }
}
