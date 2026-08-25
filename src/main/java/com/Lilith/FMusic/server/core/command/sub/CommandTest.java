package com.Lilith.FMusic.server.core.command.sub;

import net.minecraft.util.StatCollector;

import com.Lilith.FMusic.server.FMusicServer;
import com.Lilith.FMusic.server.core.FMusic;
import com.Lilith.FMusic.server.core.IMusicApi;
import com.Lilith.FMusic.server.core.command.ACommand;
import com.Lilith.FMusic.server.core.objs.music.SongInfoObj;

public class CommandTest extends ACommand {

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
            return;
        }

        if (api == null) {
            FMusic.side.sendMessage(sender, FMusic.getMessage().musicPlay.error2);
            return;
        }

        // 直接播放音频链接 (http/https)
        if (musicID.startsWith("http://") || musicID.startsWith("https://")) {
            FMusic.side
                .sendMessage(sender, StatCollector.translateToLocalFormatted("fmusic.cmd.test_playing", musicID));
            FMusicServer.LOGGER
                .debug(StatCollector.translateToLocalFormatted("fmusic.log.server.test_url", name, musicID));
            FMusic.side.sendMusic(name, musicID);
            return;
        }

        if (api.checkId(musicID)) {
            FMusic.side
                .sendMessage(sender, StatCollector.translateToLocalFormatted("fmusic.cmd.test_parsing", musicID));
            try {
                SongInfoObj info = api.getMusic(musicID, "test", false);
                if (info == null) {
                    FMusic.side.sendMessage(sender, StatCollector.translateToLocal("fmusic.cmd.test_parse_fail"));
                    return;
                }
                FMusic.side.sendMessage(
                    sender,
                    StatCollector.translateToLocalFormatted("fmusic.cmd.test_song_name", info.getName()));
                FMusic.side.sendMessage(
                    sender,
                    StatCollector.translateToLocalFormatted("fmusic.cmd.test_song_author", info.getAuthor()));
                String url = api.getPlayUrl(musicID);
                FMusic.side
                    .sendMessage(sender, StatCollector.translateToLocalFormatted("fmusic.cmd.test_play_url", url));
            } catch (Exception e) {
                FMusic.side.sendMessage(sender, StatCollector.translateToLocal("fmusic.cmd.test_parse_err"));
                e.printStackTrace();
            }
        } else {
            FMusic.side.sendMessage(sender, StatCollector.translateToLocal("fmusic.cmd.test_invalid"));
        }
    }
}
