package com.Lilith.FMusic.server.core.command.sub;

import net.minecraft.util.StatCollector;

import com.Lilith.FMusic.Config;
import com.Lilith.FMusic.server.core.FMusic;
import com.Lilith.FMusic.server.core.command.ACommand;

public class CommandReload extends ACommand {

    @Override
    public void execute(Object sender, String name, String[] args) {
        FMusic.side.reload();
        // 刷新 config/FMusic.cfg (pause_at_freeze 等)
        Config.reload();
        // B站点歌配置 (fmusic_server/bili/config.json)
        com.Lilith.FMusic.server.bili.BiliMusicBridge.reload();
        FMusic.side.sendMessage(sender, StatCollector.translateToLocal("fmusic.cmd.reload_ok"));
    }
}
