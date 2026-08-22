package com.Lilith.FMusic.server.core.command.sub;

import com.Lilith.FMusic.server.core.FMusic;
import com.Lilith.FMusic.server.core.IMusicApi;
import com.Lilith.FMusic.server.core.command.ACommand;
import com.Lilith.FMusic.server.core.music.PlayMusic;
import com.Lilith.FMusic.server.core.objs.music.SongInfoObj;
import com.Lilith.FMusic.server.core.saves.BanSave;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
            FMusic.side.sendMessage(sender, "<gold>[FMusic]<white>错误的指令");
        }

        if (api == null) {
            FMusic.side.sendMessage(sender, FMusic.getMessage().musicPlay.error2);
            return;
        }

        if (api.checkId(musicID)) {
            BanSave.addBanMusic(musicID, api.getId());
            FMusic.side
                .sendMessage(sender, "<gold>[FMusic]<white>音乐API " + api.getId() + " 已禁止点歌" + musicID);
        } else {
            FMusic.side.sendMessage(sender, "<gold>[FMusic]<white>请输入有效的ID");
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
