package com.Lilith.FMusic.server.core.command.sub;

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
            FMusic.side.sendMessage(sender, "<gold>[FMusic]<white>错误的指令");
            return;
        }

        if (api == null) {
            FMusic.side.sendMessage(sender, FMusic.getMessage().musicPlay.error2);
            return;
        }

        // 直接播放音频链接 (http/https)
        if (musicID.startsWith("http://") || musicID.startsWith("https://")) {
            FMusic.side.sendMessage(sender, "<gold>[FMusic]<white>正在测试播放 " + musicID);
            FMusicServer.LOGGER.debug("[FMusic] [CommandTest] " + name + " 测试播放链接: " + musicID);
            FMusic.side.sendMusic(name, musicID);
            return;
        }

        if (api.checkId(musicID)) {
            FMusic.side.sendMessage(sender, "<gold>[FMusic]<white>正在测试解析 " + musicID);
            try {
                SongInfoObj info = api.getMusic(musicID, "test", false);
                if (info == null) {
                    FMusic.side.sendMessage(sender, "<gold>[FMusic]<white>测试解析失败");
                    return;
                }
                FMusic.side.sendMessage(sender, "<gold>[FMusic]<white>音乐名称 " + info.getName());
                FMusic.side.sendMessage(sender, "<gold>[FMusic]<white>音乐作者 " + info.getAuthor());
                String url = api.getPlayUrl(musicID);
                FMusic.side.sendMessage(sender, "<gold>[FMusic]<white>播放地址 " + url);
            } catch (Exception e) {
                FMusic.side.sendMessage(sender, "<gold>[FMusic]<white>测试解析错误");
                e.printStackTrace();
            }
        } else {
            FMusic.side.sendMessage(sender, "<gold>[FMusic]<white>测试解析失败: 无效的音乐ID或链接");
        }
    }
}
