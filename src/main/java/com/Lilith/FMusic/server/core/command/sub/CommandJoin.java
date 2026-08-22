package com.Lilith.FMusic.server.core.command.sub;

import com.Lilith.FMusic.server.core.FMusic;
import com.Lilith.FMusic.server.core.command.ACommand;

public class CommandJoin extends ACommand {

    @Override
    public void execute(Object sender, String name, String[] args) {
        FMusic.joinPlayNow(name);
    }
}
