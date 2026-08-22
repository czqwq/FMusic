package com.Lilith.FMusic.server.core.command.sub;

import com.Lilith.FMusic.server.core.FMusic;
import com.Lilith.FMusic.server.core.command.ACommand;
import com.Lilith.FMusic.server.core.music.PlayMusic;
import com.Lilith.FMusic.server.core.saves.BanSave;

public class CommandNext extends ACommand {

    @Override
    public void execute(Object sender, String name, String[] args) {
        PlayMusic.musicLessTime = 10;
        FMusic.side.sendMessage(sender, "<gold>[FMusic]<white>已强制切歌");
        BanSave.removeMutePlayer(name);
    }
}
