package com.Lilith.FMusic.server.core.command.sub;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.Lilith.FMusic.server.core.FMusic;
import com.Lilith.FMusic.server.core.command.ACommand;
import com.Lilith.FMusic.server.core.command.CommandEX;
import com.Lilith.FMusic.server.core.music.PlayMusic;
import com.Lilith.FMusic.server.core.utils.HudUtils;

public class CommandStop extends ACommand {

    @Override
    public void execute(Object sender, String name, String[] args) {
        if (args.length == 2 && CommandEX.checkAdmin(sender, name)) {
            name = args[1];
            FMusic.side.sendMessage(sender, "已停止玩家：" + name + "的音乐播放");
        } else {
            FMusic.side.sendMessage(sender, FMusic.getMessage().musicPlay.stopPlaying);
        }
        FMusic.side.sendStop(name);
        HudUtils.sendClearHud(name);
    }

    @Override
    public List<String> tab(Object player, String name, String[] args, int index) {
        if (args.length == index || (args.length == index + 1) && CommandEX.checkAdmin(player, name)) {
            return new ArrayList<>(PlayMusic.getNowPlayPlayer());
        } else {
            return Collections.emptyList();
        }
    }
}
