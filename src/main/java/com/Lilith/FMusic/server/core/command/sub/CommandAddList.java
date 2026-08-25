package com.Lilith.FMusic.server.core.command.sub;
import net.minecraft.util.StatCollector;

import com.Lilith.FMusic.server.core.FMusic;
import com.Lilith.FMusic.server.core.IMusicApi;
import com.Lilith.FMusic.server.core.command.ACommand;

public class CommandAddList extends ACommand {

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

        musicID = api.getMusicId(musicID);

        if (api.checkId(musicID)) {
            api.setList(musicID, sender);
            FMusic.side.sendMessage(sender, StatCollector.translateToLocalFormatted("fmusic.cmd.addlist_ok", musicID));
        } else {
            FMusic.side.sendMessage(sender, StatCollector.translateToLocal("fmusic.cmd.addlist_invalid"));
        }
    }
}
