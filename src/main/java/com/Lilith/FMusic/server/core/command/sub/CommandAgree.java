package com.Lilith.FMusic.server.core.command.sub;

import com.Lilith.FMusic.server.core.FMusic;
import com.Lilith.FMusic.server.core.command.ACommand;
import com.Lilith.FMusic.server.core.command.PermissionList;
import com.Lilith.FMusic.server.core.music.PlayMusic;
import com.Lilith.FMusic.server.core.music.PlayRuntime;
import com.Lilith.FMusic.server.core.music.VoteItem;
import com.Lilith.FMusic.server.core.objs.message.ARG;

/**
 * /music agree 同意当前投票 (移植自 AllMusic 4.2.0 投票序列)
 */
public class CommandAgree extends ACommand {

    @Override
    public void execute(Object sender, String name, String[] args) {
        if (FMusic.getConfig().needPermission && !FMusic.side.checkPermission(sender, PermissionList.PERMISSION_VOTE)) {
            FMusic.side.sendMessage(sender, FMusic.getMessage().vote.noPermission);
            return;
        }

        VoteItem vote = PlayMusic.getVote();

        if (vote == null) {
            FMusic.side.sendMessage(name, FMusic.getMessage().vote.err4);
            return;
        }
        if (vote.votePlayer.contains(name)) {
            FMusic.side.sendMessage(name, FMusic.getMessage().vote.err5);
            return;
        }

        FMusic.side.sendMessage(name, FMusic.getMessage().vote.agree);
        String data = FMusic.getMessage().vote.bqAgree;
        data = data.replace(ARG.player, name)
            .replace(ARG.count, String.valueOf(vote.votePlayer.size()))
            .replace(ARG.countAll, String.valueOf(PlayRuntime.getMiniVote()));
        FMusic.side.broadcast(data);

        PlayMusic.addVote(name);
    }
}
