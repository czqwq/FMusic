package com.Lilith.FMusic.server.core.command.sub;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.Lilith.FMusic.server.core.FMusic;
import com.Lilith.FMusic.server.core.command.ACommand;
import com.Lilith.FMusic.server.core.command.PermissionList;
import com.Lilith.FMusic.server.core.music.PlayMusic;
import com.Lilith.FMusic.server.core.music.PlayRuntime;
import com.Lilith.FMusic.server.core.objs.message.ARG;
import com.Lilith.FMusic.server.core.objs.music.SongInfoObj;
import com.Lilith.FMusic.server.core.saves.BanSave;

public class CommandPush extends ACommand {

    @Override
    public void execute(Object sender, String name, String[] args) {
        if (FMusic.getConfig().needPermission && !FMusic.side.checkPermission(sender, PermissionList.PERMISSION_PUSH)) {
            FMusic.side.sendMessage(sender, FMusic.side.miniMessage(FMusic.getMessage().push.noPermission));
            return;
        }
        if (PlayMusic.getListSize() == 0 && PlayMusic.getIdleListSize() == 0) {
            FMusic.side.sendMessage(sender, FMusic.side.miniMessage(FMusic.getMessage().musicPlay.emptyPlay));
        }
        SongInfoObj music = null;
        if (args.length == 1) {
            music = PlayMusic.findPlayerMusic(name);
            if (music == null) {
                FMusic.side.sendMessage(sender, FMusic.getMessage().push.noId);
                return;
            }
            SongInfoObj id1 = PlayMusic.findMusicIndex(1);
            if (id1 != null && id1.getID()
                .equalsIgnoreCase(music.getID())) {
                FMusic.side.sendMessage(sender, FMusic.getMessage().push.pushErr);
                return;
            }
        } else if (args.length == 2) {
            if (args[1].equalsIgnoreCase("cancel")) {
                if (!name.equalsIgnoreCase(PlayMusic.getPushSender())) {
                    FMusic.side.sendMessage(sender, FMusic.getMessage().push.err1);
                    return;
                }
                if (PlayMusic.getPushTime() <= 0) {
                    FMusic.side.sendMessage(sender, FMusic.getMessage().push.err2);
                    return;
                }
                PlayMusic.clearPush();
                FMusic.side.broadcast(FMusic.getMessage().push.cancel);
                return;
            } else {
                try {
                    int index = Integer.parseInt(args[1]);
                    music = PlayMusic.findMusicIndex(index);
                } catch (Exception e) {
                    FMusic.side.sendMessage(sender, FMusic.getMessage().push.noId);
                    return;
                }
                if (music == null) {
                    FMusic.side.sendMessage(sender, FMusic.getMessage().push.noId1.replace(ARG.index, args[1]));
                    return;
                }
            }
        } else if (args.length > 2) {
            FMusic.side.sendMessage(sender, FMusic.getMessage().command.error);
            return;
        }
        if (PlayMusic.getPushTime() <= 0) {
            if (music == null) {
                return;
            }
            PlayMusic.startPush(name, music);
            FMusic.side.sendMessage(sender, FMusic.getMessage().push.doVote);
            String data = FMusic.getMessage().push.bq;
            data = data.replace(ARG.player, name)
                .replace(ARG.time, String.valueOf(FMusic.getConfig().voteTime))
                .replace(ARG.musicName, music.getName())
                .replace(ARG.musicAuthor, music.getAuthor())
                .replace(ARG.countAll, String.valueOf(PlayRuntime.getMiniVote()));
            FMusic.side.broadcast(data);
            FMusic.side.broadcast(
                FMusic.side.miniMessage(FMusic.getMessage().push.bq1)
                    .append(FMusic.side.miniMessageRun(FMusic.getMessage().push.bq2, "/music push")));
        } else {
            if (music != null) {
                FMusic.side.sendMessage(sender, FMusic.getMessage().push.err3);
                return;
            }
            if (!PlayMusic.containPush(name)) {
                PlayMusic.addPush(name);
                FMusic.side.sendMessage(sender, FMusic.getMessage().push.agree);
                String data = FMusic.getMessage().push.bqAgree;
                data = data.replace(ARG.player, name)
                    .replace(ARG.count, String.valueOf(PlayMusic.getVoteCount()))
                    .replace(ARG.countAll, String.valueOf(PlayRuntime.getMiniVote()));
                FMusic.side.broadcast(data);
            } else {
                FMusic.side.sendMessage(sender, FMusic.getMessage().push.arAgree);
            }
        }
        BanSave.removeMutePlayer(name);
    }

    @Override
    public List<String> tab(Object player, String name, String[] args, int index) {
        if (args.length == 1 || (args.length == 2 && args[1].isEmpty())) {
            List<String> list = new ArrayList<>();
            List<SongInfoObj> list1 = PlayMusic.getList();
            for (int a = 1; a < list1.size(); a++) {
                SongInfoObj item = list1.get(a);
                if (item.getCall()
                    .equalsIgnoreCase(name)) {
                    list.add(String.valueOf(a));
                }
            }

            return list;
        }
        return Collections.emptyList();
    }
}
