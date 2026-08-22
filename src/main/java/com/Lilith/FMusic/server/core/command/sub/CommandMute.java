package com.Lilith.FMusic.server.core.command.sub;

import com.Lilith.FMusic.server.core.FMusic;
import com.Lilith.FMusic.server.core.command.ACommand;
import com.Lilith.FMusic.server.core.command.CommandEX;
import com.Lilith.FMusic.server.core.music.PlayMusic;
import com.Lilith.FMusic.server.core.saves.BanSave;

import java.util.ArrayList;
import java.util.List;

public class CommandMute extends ACommand {

    @Override
    public void execute(Object sender, String name, String[] args) {
        if (args.length == 2) {
            if (args[1].equalsIgnoreCase("list")) {
                if (BanSave.checkMuteListPlayer(name)) {
                    BanSave.removeMuteListPlayer(name);
                    FMusic.side.sendMessage(sender, FMusic.getMessage().musicPlay.mute2);
                } else {
                    BanSave.addMuteListPlayer(name);
                    FMusic.side.sendMessage(sender, FMusic.getMessage().musicPlay.mute1);
                    if (PlayMusic.nowPlayMusic != null && PlayMusic.nowPlayMusic.isList()) {
                        FMusic.side.sendStop(name);
                        FMusic.side.clearHud(name);
                    }
                }
            } else if (CommandEX.checkAdmin(sender, name)) {
                String finalName = args[1];
                if (BanSave.checkMutePlayer(finalName)) {
                    BanSave.removeMutePlayer(finalName);
                    FMusic.side.sendMessage(sender, "已取消玩家：" + finalName + "的静音");
                } else {
                    BanSave.addMutePlayer(finalName);
                    FMusic.side.sendStop(finalName);
                    FMusic.side.clearHud(finalName);
                    FMusic.side.sendMessage(sender, "已设置玩家：" + finalName + "的静音");
                }
            }
        } else {
            if (BanSave.checkMutePlayer(name)) {
                BanSave.removeMutePlayer(name);
                FMusic.side.sendMessage(sender, FMusic.getMessage().musicPlay.mute3);
            } else {
                BanSave.addMutePlayer(name);
                FMusic.side.sendStop(name);
                FMusic.side.clearHud(name);
                FMusic.side.sendMessage(sender, FMusic.getMessage().musicPlay.mute);
            }
        }
    }

    @Override
    public List<String> tab(Object player, String name, String[] args, int index) {
        if (args.length == index || (args.length == index + 1)) {
            if (CommandEX.checkAdmin(player, name)) {
                List<String> players = new ArrayList<>();
                for (Object item : FMusic.side.getPlayers()) {
                    players.add(FMusic.side.getPlayerName(item));
                }
                return players;
            }
            return new ArrayList<String>() {

                {
                    add("list");
                }
            };
        } else {
            return super.tab(player, name, args, index);
        }
    }
}
