package com.Lilith.FMusic.server.core.command.sub;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.Lilith.FMusic.server.core.FMusic;
import com.Lilith.FMusic.server.core.command.ICommand;
import com.Lilith.FMusic.server.core.music.PlayMusic;
import com.Lilith.FMusic.server.core.objs.message.ARG;
import com.Lilith.FMusic.server.core.objs.music.SongInfoObj;

public class CommandCancel implements ICommand {

    @Override
    public void execute(Object sender, String name, String[] args) {
        if (args.length == 1) {
            SongInfoObj id = PlayMusic.findPlayerMusic(name);
            if (id == null) {
                FMusic.side.sendMessage(sender, FMusic.getMessage().cancel.err1);
                return;
            }
            if (!id.getCall()
                .equalsIgnoreCase(name)) {
                FMusic.side.sendMessage(
                    sender,
                    FMusic.getMessage().cancel.err2.replace(ARG.musicName, id.getName())
                        .replace(ARG.musicAuthor, id.getAuthor()));
                return;
            }
            PlayMusic.remove(id);
            FMusic.side.sendMessage(sender, FMusic.getMessage().cancel.done);
        } else if (args.length == 2) {
            try {
                int index = Integer.parseInt(args[1]);
                SongInfoObj id = PlayMusic.findMusicIndex(index);
                if (id == null) {
                    FMusic.side.sendMessage(sender, FMusic.getMessage().cancel.err3.replace(ARG.index, args[1]));
                    return;
                }
                if (!id.getCall()
                    .equalsIgnoreCase(name)) {
                    FMusic.side.sendMessage(
                        sender,
                        FMusic.getMessage().cancel.err2.replace(ARG.musicName, id.getName())
                            .replace(ARG.musicAuthor, id.getAuthor()));
                    return;
                }

                FMusic.side.sendMessage(sender, FMusic.getMessage().cancel.done);
            } catch (Exception e) {
                FMusic.side.sendMessage(sender, FMusic.getMessage().cancel.err4);
            }
        } else {
            FMusic.side.sendMessage(sender, FMusic.getMessage().command.error);
        }
    }

    @Override
    public List<String> tab(Object player, String name, String[] args, int index) {
        if (args.length == 1 || (args.length == 2 && args[1].isEmpty())) {
            List<String> list = new ArrayList<>();
            List<SongInfoObj> list1 = PlayMusic.getList();
            if (list1.size() > 1) {
                for (int a = 1; a < list1.size(); a++) {
                    SongInfoObj item = list1.get(a);
                    if (item.getCall()
                        .equalsIgnoreCase(name)) {
                        list.add(String.valueOf(a));
                    }
                }
            }
            return list;
        }

        return Collections.emptyList();
    }
}
