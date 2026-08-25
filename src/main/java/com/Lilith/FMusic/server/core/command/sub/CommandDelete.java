package com.Lilith.FMusic.server.core.command.sub;
import net.minecraft.util.StatCollector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.Lilith.FMusic.server.core.FMusic;
import com.Lilith.FMusic.server.core.command.ACommand;
import com.Lilith.FMusic.server.core.music.PlayMusic;
import com.Lilith.FMusic.server.core.utils.Function;

public class CommandDelete extends ACommand {

    @Override
    public void execute(Object sender, String name, String[] args) {
        if (args.length != 2) {
            FMusic.side.sendMessage(sender, FMusic.getMessage().command.error);
            return;
        }
        if (!args[1].isEmpty() && Function.isInteger(args[1])) {
            int music = Integer.parseInt(args[1]);
            if (music == 0) {
                FMusic.side.sendMessage(sender, StatCollector.translateToLocal("fmusic.cmd.delete_invalid"));
                return;
            }
            if (music > PlayMusic.getListSize()) {
                FMusic.side.sendMessage(sender, StatCollector.translateToLocal("fmusic.cmd.delete_too_big"));
                return;
            }
            PlayMusic.remove(music - 1);
            FMusic.side.sendMessage(sender, StatCollector.translateToLocalFormatted("fmusic.cmd.delete_ok", music));
        } else {
            FMusic.side.sendMessage(sender, StatCollector.translateToLocal("fmusic.cmd.delete_invalid"));
        }
    }

    @Override
    public List<String> tab(Object player, String name, String[] args, int index) {
        if (args.length == 1 || (args.length == 2 && args[1].isEmpty())) {
            List<String> list = new ArrayList<>();
            for (int a = 0; a < PlayMusic.getListSize(); a++) {
                list.add(String.valueOf(a + 1));
            }
            return list;
        }

        return Collections.emptyList();
    }
}
