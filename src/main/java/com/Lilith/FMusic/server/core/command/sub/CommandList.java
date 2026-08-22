package com.Lilith.FMusic.server.core.command.sub;

import com.Lilith.FMusic.server.core.FMusic;
import com.Lilith.FMusic.server.core.command.ACommand;
import com.Lilith.FMusic.server.core.music.PlayMusic;
import com.Lilith.FMusic.server.core.objs.message.ARG;

public class CommandList extends ACommand {

    @Override
    public void execute(Object sender, String name, String[] args) {
        if (PlayMusic.nowPlayMusic == null || PlayMusic.nowPlayMusic.isNull()) {
            FMusic.side.sendMessage(sender, FMusic.getMessage().musicPlay.emptyPlayingMusic);
        } else {
            String info = FMusic.getMessage().musicPlay.nowPlay;
            info = info.replace(ARG.musicName, PlayMusic.nowPlayMusic.getName())
                .replace(ARG.musicAuthor, PlayMusic.nowPlayMusic.getAuthor())
                .replace(ARG.musicAl, PlayMusic.nowPlayMusic.getAl())
                .replace(ARG.musicAlia, PlayMusic.nowPlayMusic.getAlia())
                .replace(ARG.player, PlayMusic.nowPlayMusic.getCall());
            FMusic.side.sendMessage(sender, info);
        }
        if (PlayMusic.getListSize() == 0) {
            FMusic.side.sendMessage(sender, FMusic.getMessage().musicPlay.emptyPlay);
        } else {
            FMusic.side.sendMessage(
                sender,
                FMusic.getMessage().musicPlay.listMusic.head.replace(ARG.count, "" + PlayMusic.getListSize()));
            FMusic.side.sendMessage(sender, PlayMusic.getAllList());
        }
    }
}
