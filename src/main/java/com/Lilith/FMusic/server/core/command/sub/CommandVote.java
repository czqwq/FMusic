package com.Lilith.FMusic.server.core.command.sub;

import com.Lilith.FMusic.server.core.FMusic;
import com.Lilith.FMusic.server.core.command.ACommand;
import com.Lilith.FMusic.server.core.command.PermissionList;
import com.Lilith.FMusic.server.core.music.PlayMusic;
import com.Lilith.FMusic.server.core.music.PlayRuntime;
import com.Lilith.FMusic.server.core.objs.message.ARG;
import com.Lilith.FMusic.server.core.saves.BanSave;

public class CommandVote extends ACommand {

    @Override
    public void execute(Object sender, String name, String[] args) {
        if (FMusic.getConfig().needPermission && !FMusic.side.checkPermission(sender, PermissionList.PERMISSION_VOTE)) {
            FMusic.side.sendMessage(sender, FMusic.getMessage().vote.noPermission);
            return;
        } else if (PlayMusic.getListSize() == 0 && PlayMusic.getIdleListSize() == 0) {
            FMusic.side.sendMessage(sender, FMusic.getMessage().musicPlay.emptyPlay);
        } else if (PlayMusic.nowPlayMusic == null) {
            FMusic.side.sendMessage(sender, FMusic.getMessage().musicPlay.emptyPlayingMusic);
        } else if (args.length == 2) {
            if (args[1].equalsIgnoreCase("cancel")) {
                if (!name.equalsIgnoreCase(PlayMusic.getVoteSender())) {
                    FMusic.side.sendMessage(sender, FMusic.getMessage().vote.err1);
                    return;
                } else if (PlayMusic.getVoteTime() <= 0) {
                    FMusic.side.sendMessage(sender, FMusic.getMessage().vote.err2);
                    return;
                }
                FMusic.side.broadcast(FMusic.getMessage().vote.cancel);
                PlayMusic.clearVote();
            } else {
                FMusic.side.sendMessage(sender, FMusic.getMessage().command.error);
            }
            return;
        } else if (args.length > 2) {
            FMusic.side.sendMessage(sender, FMusic.getMessage().command.error);
            return;
        } else if (PlayMusic.getVoteTime() <= 0) {
            PlayMusic.startVote(name);
            FMusic.side.sendMessage(sender, FMusic.getMessage().vote.doVote);
            String data = FMusic.getMessage().vote.bq;
            data = data.replace(ARG.player, name)
                .replace(ARG.time, String.valueOf(FMusic.getConfig().voteTime))
                .replace(ARG.countAll, String.valueOf(PlayRuntime.getMiniVote()));
            FMusic.side.broadcast(data);
            FMusic.side.broadcast(
                FMusic.side.miniMessage(FMusic.getMessage().vote.bq1)
                    .append(FMusic.side.miniMessageRun(FMusic.getMessage().vote.bq2, "/music vote")));
        } else {
            if (!PlayMusic.containVote(name)) {
                PlayMusic.addVote(name);
                FMusic.side.sendMessage(sender, FMusic.getMessage().vote.agree);
                String data = FMusic.getMessage().vote.bqAgree;
                data = data.replace(ARG.player, name)
                    .replace(ARG.count, String.valueOf(PlayMusic.getVoteCount()))
                    .replace(ARG.countAll, String.valueOf(PlayRuntime.getMiniVote()));
                FMusic.side.broadcast(data);
            } else {
                FMusic.side.sendMessage(sender, FMusic.getMessage().vote.arAgree);
            }
        }
        BanSave.removeMutePlayer(name);
    }
}
