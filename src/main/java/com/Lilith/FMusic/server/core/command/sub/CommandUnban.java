package com.Lilith.FMusic.server.core.command.sub;

import com.Lilith.FMusic.server.core.FMusic;
import com.Lilith.FMusic.server.core.IMusicApi;
import com.Lilith.FMusic.server.core.command.ACommand;
import com.Lilith.FMusic.server.core.saves.BanSave;

public class CommandUnban extends ACommand {

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

        musicID = api.getMusicId(musicID);

        if (api.checkId(musicID)) {
            api.setList(musicID, sender);
            BanSave.removeBanMusic(args[1], api.getId());
            FMusic.side.sendMessage(sender, "<gold>[FMusic]<white>音乐API " + api.getId() + "已解封点歌" + musicID);
        } else {
            FMusic.side.sendMessage(sender, "<gold>[FMusic]<white>请输入有效的ID");
        }
    }
}
