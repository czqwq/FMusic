package com.Lilith.FMusic.server.core.command.sub;
import net.minecraft.util.StatCollector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.Lilith.FMusic.server.core.FMusic;
import com.Lilith.FMusic.server.core.IMusicApi;
import com.Lilith.FMusic.server.core.command.ACommand;
import com.Lilith.FMusic.server.core.music.PlayMusic;
import com.Lilith.FMusic.server.core.objs.music.SongInfoObj;
import com.Lilith.FMusic.server.core.saves.BanSave;

public class CommandBan extends ACommand {

    @Override
    public void execute(Object sender, String name, String[] args) {
        if (args.length < 2) {
            FMusic.side.sendMessage(sender, FMusic.getMessage().command.error);
            return;
        }

        String musicID = null;
        IMusicApi api = null;

        if (args.length == 2) {
            api = FMusic.MUSIC_APIS.get(FMusic.getConfig().defaultApi);
            musicID = args[1];
        } else if (args.length == 3) {
            api = FMusic.MUSIC_APIS.get(args[1]);
            musicID = args[2];
        } else {
            FMusic.side.sendMessage(sender, StatCollector.translateToLocal("fmusic.cmd.wrong_cmd"));
        }

        if (api == null) {
            FMusic.side.sendMessage(sender, FMusic.getMessage().musicPlay.error2);
            return;
        }

        if (api.checkId(musicID)) {
            BanSave.addBanMusic(musicID, api.getId());
            FMusic.side.sendMessage(sender, StatCollector.translateToLocalFormatted("fmusic.cmd.ban_ok", api.getId(), musicID));
        } else {
            FMusic.side.sendMessage(sender, StatCollector.translateToLocal("fmusic.cmd.invalid_id"));
        }
    }

    @Override
    public List<String> tab(Object player, String name, String[] args, int index) {
        if (args.length == index || (args.length == index + 1)) {
            List<String> list = new ArrayList<>();
            if (PlayMusic.nowPlayMusic != null) {
                list.add(PlayMusic.nowPlayMusic.getID());
            }
            for (SongInfoObj item : PlayMusic.getList()) {
                list.add(item.getID());
            }

            return list;
        }

        return Collections.emptyList();
    }
}
