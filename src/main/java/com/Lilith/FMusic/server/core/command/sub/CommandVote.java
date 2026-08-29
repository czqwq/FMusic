package com.Lilith.FMusic.server.core.command.sub;

import java.util.Locale;

import com.Lilith.FMusic.server.core.FMusic;
import com.Lilith.FMusic.server.core.command.ACommand;
import com.Lilith.FMusic.server.core.command.PermissionList;
import com.Lilith.FMusic.server.core.music.PlayMusic;
import com.Lilith.FMusic.server.core.music.VoteItem;
import com.Lilith.FMusic.server.core.saves.BanSave;

public class CommandVote extends ACommand {

    @Override
    public void execute(Object sender, String name, String[] args) {
        if (FMusic.getConfig().needPermission && !FMusic.side.checkPermission(sender, PermissionList.PERMISSION_VOTE)) {
            FMusic.side.sendMessage(sender, FMusic.getMessage().vote.noPermission);
            return;
        }
        BanSave.removeMutePlayer(name);
        if (PlayMusic.nowPlayMusic == null) {
            FMusic.side.sendMessage(sender, FMusic.getMessage().musicPlay.emptyPlayingMusic);
        } else if (args.length == 2) {
            if (args[1].equalsIgnoreCase("cancel")) {
                VoteItem vote = PlayMusic.getVote();
                if (vote == null) {
                    FMusic.side.sendMessage(sender, FMusic.getMessage().vote.err4);
                    return;
                }
                if (!PlayMusic.haveVote(name, VoteItem.VoteType.NEXT)) {
                    FMusic.side.sendMessage(sender, FMusic.getMessage().vote.err1);
                    return;
                }
                PlayMusic.removeVote(name, VoteItem.VoteType.NEXT);
                FMusic.side.sendMessage(name, FMusic.getMessage().push.cancel1);
            } else {
                FMusic.side.sendMessage(sender, FMusic.getMessage().command.error);
            }
        } else if (args.length > 2) {
            FMusic.side.sendMessage(sender, FMusic.getMessage().command.error);
            return;
        } else {
            String player = name.toLowerCase(Locale.ROOT);
            VoteItem item = new VoteItem(
                PlayMusic.nowPlayMusic.getApi(),
                PlayMusic.nowPlayMusic.getId(),
                player,
                VoteItem.VoteType.NEXT);
            item.votePlayer.add(player);

            if (PlayMusic.startVote(item)) {
                FMusic.side.sendMessage(sender, FMusic.getMessage().vote.doVote);
            } else {
                FMusic.side.sendMessage(sender, FMusic.getMessage().vote.err3);
            }
        }
    }
}
