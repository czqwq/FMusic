package com.Lilith.FMusic.server.core.command.sub;

import com.Lilith.FMusic.server.core.FMusic;
import com.Lilith.FMusic.server.core.command.ACommand;
import com.Lilith.FMusic.server.core.command.PermissionList;
import com.Lilith.FMusic.server.core.music.MusicSearch;
import com.Lilith.FMusic.server.core.objs.music.SearchPageObj;

public class CommandLastPage extends ACommand {

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
        } else if (obj.lastPage()) {
            MusicSearch.showSearch(sender, obj);
        } else {
            FMusic.side.sendMessage(sender, FMusic.getMessage().search.cantLast);
        }
    }
}
