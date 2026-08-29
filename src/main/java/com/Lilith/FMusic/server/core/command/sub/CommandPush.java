package com.Lilith.FMusic.server.core.command.sub;

import com.Lilith.FMusic.server.core.FMusic;
import com.Lilith.FMusic.server.core.command.ACommand;
import com.Lilith.FMusic.server.core.command.PermissionList;
import com.Lilith.FMusic.server.core.music.PlayMusic;
import com.Lilith.FMusic.server.core.music.VoteItem;
import com.Lilith.FMusic.server.core.objs.message.ARG;
import com.Lilith.FMusic.server.core.objs.music.SongInfoObj;
import com.Lilith.FMusic.server.core.saves.BanSave;

public class CommandPush extends ACommand {

    @Override
    public void execute(Object sender, String name, String[] args) {
        if (FMusic.getConfig().needPermission && !FMusic.side.checkPermission(sender, PermissionList.PERMISSION_PUSH)) {
            FMusic.side.sendMessage(sender, FMusic.getMessage().push.noPermission);
            return;
        }
        SongInfoObj music = null;
        if (PlayMusic.nowPlayMusic == null) {
            FMusic.side.sendMessage(sender, FMusic.getMessage().musicPlay.emptyPlayingMusic);
            return;
        }
        if (args.length == 1) {
            music = PlayMusic.nowPlayMusic;
            SongInfoObj id1 = PlayMusic.findMusicIndex(1);
            if (id1 != null && id1.getId()
                .equalsIgnoreCase(music.getId())) {
                FMusic.side.sendMessage(sender, FMusic.getMessage().push.pushErr);
                return;
            }

            VoteItem item = new VoteItem(music.getApi(), music.getId(), name, VoteItem.VoteType.PUSH);
            if (PlayMusic.startVote(item)) {
                FMusic.side.sendMessage(sender, FMusic.getMessage().push.doVote);
            } else {
                FMusic.side.sendMessage(sender, FMusic.getMessage().push.err3);
            }
        } else if (args.length == 2) {
            if (args[1].equalsIgnoreCase("cancel")) {
                if (!PlayMusic.haveVote(name, VoteItem.VoteType.PUSH)) {
                    FMusic.side.sendMessage(sender, FMusic.getMessage().push.err1);
                    return;
                }
                PlayMusic.removeVote(name, VoteItem.VoteType.PUSH);
                FMusic.side.sendMessage(name, FMusic.getMessage().push.cancel1);
                return;
            } else {
                try {
                    music = PlayMusic.findMusicIndex(Integer.parseInt(args[1]));
                } catch (Exception e) {
                    FMusic.side.sendMessage(sender, FMusic.getMessage().push.noId1.replace(ARG.index, args[1]));
                    return;
                }
                if (music == null) {
                    FMusic.side.sendMessage(sender, FMusic.getMessage().push.noId1.replace(ARG.index, args[1]));
                    return;
                }

                VoteItem item = new VoteItem(music.getApi(), music.getId(), name, VoteItem.VoteType.PUSH);
                if (PlayMusic.startVote(item)) {
                    FMusic.side.sendMessage(sender, FMusic.getMessage().push.doVote);
                } else {
                    FMusic.side.sendMessage(sender, FMusic.getMessage().push.err3);
                }
            }
        } else if (args.length > 2) {
            FMusic.side.sendMessage(sender, FMusic.getMessage().command.error);
            return;
        }
        BanSave.removeMutePlayer(name);
    }
}
