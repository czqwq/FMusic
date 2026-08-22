package com.Lilith.FMusic.server.core.command.sub;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.Lilith.FMusic.codec.HudType;
import com.Lilith.FMusic.server.core.FMusic;
import com.Lilith.FMusic.server.core.command.ACommand;
import com.Lilith.FMusic.server.core.command.ICommand;
import com.Lilith.FMusic.server.core.objs.message.ARG;
import com.Lilith.FMusic.server.core.utils.HudUtils;

public class CommandHud extends ACommand {

    public static final List<String> tf = new ArrayList<String>() {

        {
            this.add("true");
            this.add("false");
        }
    };
    /**
     * Hud的指令
     */
    private static final List<String> hudlist = new ArrayList<String>() {

        {
            this.add("enable");
            this.add("reset");
            this.add("alpha");
            this.add("info");
            this.add("lyric");
            this.add("state");
            this.add("pic");
        }
    };
    private final Map<String, ICommand> commandList = new HashMap<>();

    public CommandHud() {
        commandList.put("enable", new HudEnable());
        commandList.put("reset", new HudReset());
        commandList.put("alpha", new HudAlpha());
        commandList.put("info", new CommandHudSet(HudType.INFO));
        commandList.put("lyric", new CommandHudSet(HudType.LYRIC));
        commandList.put("state", new CommandHudSet(HudType.STATE));
        commandList.put("pic", new CommandHudSet(HudType.PIC));
    }

    @Override
    public void execute(Object sender, String name, String[] args) {
        if (args.length == 1) {
            FMusic.side.sendMessage(sender, FMusic.getMessage().command.error);
        } else {
            ICommand command = commandList.get(args[1]);
            if (command != null) {
                command.execute(sender, name, args);
            } else {
                FMusic.side.sendMessage(sender, FMusic.getMessage().command.error);
            }
        }
    }

    @Override
    public List<String> tab(Object player, String name, String[] args, int index) {
        if (args.length == index + 1) {
            return hudlist;
        } else {
            ICommand command = commandList.get(args[index]);
            if (command != null) {
                return command.tab(player, name, args, index + 1);
            }
        }
        return Collections.emptyList();
    }

    private static class HudAlpha extends ACommand {

        @Override
        public void execute(Object sender, String name, String[] args) {
            if (args.length == 3) {
                try {
                    float temp = HudUtils.setHudAlpha(name, null, args[2]);
                    if (temp == -1) {
                        FMusic.side.sendMessage(sender, FMusic.getMessage().command.error);
                        return;
                    }
                    FMusic.side.sendMessageTask(
                        sender,
                        FMusic.getMessage().hud.state.replace(ARG.value, String.valueOf(temp))
                            .replace(ARG.hud, FMusic.getMessage().hudList.getHud(null)));
                    return;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            FMusic.side.sendMessage(sender, FMusic.getMessage().command.error);
        }
    }

    private static class HudEnable extends ACommand {

        @Override
        public void execute(Object sender, String name, String[] args) {
            if (args.length == 2 || args.length == 3) {
                boolean temp = HudUtils.setHudEnable(name, null, args.length == 3 ? args[2] : null);
                FMusic.side.sendMessageTask(
                    sender,
                    FMusic.getMessage().hud.state
                        .replace(
                            ARG.value,
                            temp ? FMusic.getMessage().hudList.enable : FMusic.getMessage().hudList.disable)
                        .replace(ARG.hud, FMusic.getMessage().hudList.getHud(null)));
                return;
            }
            FMusic.side.sendMessage(sender, FMusic.getMessage().command.error);
        }

        @Override
        public List<String> tab(Object player, String name, String[] args, int index) {
            if (args.length == index + 1) {
                return tf;
            }
            return Collections.emptyList();
        }
    }

    private static class HudReset extends ACommand {

        @Override
        public void execute(Object sender, String name, String[] args) {
            HudUtils.reset(name);
            FMusic.side.sendMessage(
                sender,
                FMusic.getMessage().hud.reset.replace(ARG.hud, FMusic.getMessage().hudList.getHud(null)));
        }
    }
}
