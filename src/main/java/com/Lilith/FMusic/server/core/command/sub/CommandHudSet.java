package com.Lilith.FMusic.server.core.command.sub;

import com.Lilith.FMusic.codec.HudBasePosObj;
import com.Lilith.FMusic.codec.HudPosType;
import com.Lilith.FMusic.codec.HudType;
import com.Lilith.FMusic.server.core.FMusic;
import com.Lilith.FMusic.server.core.command.ACommand;
import com.Lilith.FMusic.server.core.command.AHudCommand;
import com.Lilith.FMusic.server.core.command.ICommand;
import com.Lilith.FMusic.server.core.objs.message.ARG;
import com.Lilith.FMusic.server.core.utils.HudUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommandHudSet extends AHudCommand {

    /**
     * Hud的指令
     */
    private static final List<String> hud = new ArrayList<String>() {

        {
            this.add("enable");
            this.add("pos");
            this.add("dir");
            this.add("alpha");
            this.add("reset");
        }
    };
    private static final List<String> pic = new ArrayList<String>() {

        {
            this.add("size");
            this.add("rotate");
            this.add("speed");
        }
    };
    private static final List<String> info = new ArrayList<String>() {

        {
            this.add("color");
            this.add("shadow");
            this.add("loop");
            this.add("gap");
            this.add("maxwidth");
        }
    };
    private static final List<String> state = new ArrayList<String>() {

        {
            this.add("color");
            this.add("shadow");
            this.add("gap");
        }
    };
    private static final List<String> lyric = new ArrayList<String>() {

        {
            this.add("color");
            this.add("shadow");
            this.add("gap");
            this.add("maxwidth");
        }
    };

    private final Map<String, ICommand> commandList = new HashMap<>();

    public CommandHudSet(HudType type) {
        super(type);
        commandList.put("enable", new HudEnable(type));
        commandList.put("reset", new HudReset(type));
        commandList.put("pos", new HudPos(type));
        commandList.put("alpha", new HudAlpha(type));
        commandList.put("dir", new HudDir(type));
        if (type == HudType.PIC) {
            commandList.put("size", new PicSize());
            commandList.put("rotate", new PicRotate());
            commandList.put("speed", new PicRotateSpeed());
        } else if (type == HudType.INFO) {
            commandList.put("color", new HudColor(type));
            commandList.put("shadow", new HudShadow(type));
            commandList.put("loop", new HudLoop(type));
            commandList.put("gap", new HudGap(type));
            commandList.put("maxwidth", new HudMaxWidth(type));
        } else if (type == HudType.STATE) {
            commandList.put("color", new HudColor(type));
            commandList.put("shadow", new HudShadow(type));
            commandList.put("gap", new HudGap(type));
        } else if (type == HudType.LYRIC) {
            commandList.put("color", new HudColor(type));
            commandList.put("shadow", new HudShadow(type));
            commandList.put("gap", new HudGap(type));
            commandList.put("maxwidth", new HudMaxWidth(type));
        }
    }

    @Override
    public void execute(Object sender, String name, String[] args) {
        if (args.length == 2) {
            FMusic.side.sendMessage(sender, FMusic.getMessage().command.error);
        } else {
            ICommand command = commandList.get(args[2]);
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
            List<String> list = new ArrayList<>(hud);
            if (type == HudType.PIC) {
                list.addAll(pic);
            } else if (type == HudType.INFO) {
                list.addAll(info);
            } else if (type == HudType.STATE) {
                list.addAll(state);
            } else if (type == HudType.LYRIC) {
                list.addAll(lyric);
            }

            return list;
        } else {
            ICommand command = commandList.get(args[index]);
            if (command != null) {
                return command.tab(player, name, args, index + 1);
            }
        }
        return Collections.emptyList();
    }

    private static class HudEnable extends AHudCommand {

        public HudEnable(HudType type) {
            super(type);
        }

        @Override
        public void execute(Object sender, String name, String[] args) {
            if (args.length == 3 || args.length == 4) {
                boolean temp = HudUtils.setHudEnable(name, type, args.length == 4 ? args[3] : null);
                FMusic.side.sendMessageTask(
                    sender,
                    FMusic.getMessage().hud.state
                        .replace(
                            ARG.value,
                            temp ? FMusic.getMessage().hudList.enable : FMusic.getMessage().hudList.disable)
                        .replace(ARG.hud, FMusic.getMessage().hudList.getHud(type)));
                return;
            }
            FMusic.side.sendMessage(sender, FMusic.getMessage().command.error);
        }

        @Override
        public List<String> tab(Object player, String name, String[] args, int index) {
            if (args.length == index + 1) {
                return CommandHud.tf;
            }
            return Collections.emptyList();
        }
    }

    private static class HudReset extends AHudCommand {

        public HudReset(HudType type) {
            super(type);
        }

        @Override
        public void execute(Object sender, String name, String[] args) {
            HudUtils.reset(name, type);
            FMusic.side.sendMessage(
                sender,
                FMusic.getMessage().hud.reset.replace(ARG.hud, FMusic.getMessage().hudList.getHud(type)));
        }
    }

    private static class HudPos extends AHudCommand {

        public HudPos(HudType type) {
            super(type);
        }

        @Override
        public void execute(Object sender, String name, String[] args) {
            try {
                if (args.length != 5) {
                    FMusic.side.sendMessage(sender, FMusic.getMessage().command.error);
                    return;
                }
                HudBasePosObj obj = HudUtils.setHudPos(name, type, args[3], args[4]);
                if (obj == null) {
                    FMusic.side.sendMessageTask(sender, FMusic.getMessage().command.error);
                    return;
                }

                FMusic.side.sendMessageTask(
                    sender,
                    FMusic.getMessage().hud.set.replace(ARG.hud, FMusic.getMessage().hudList.getHud(type))
                        .replace(ARG.x, String.valueOf(obj.x))
                        .replace(ARG.y, String.valueOf(obj.y)));
            } catch (Exception e) {
                FMusic.side.sendMessage(sender, FMusic.getMessage().command.error);
            }
        }
    }

    private static class HudAlpha extends AHudCommand {

        public HudAlpha(HudType type) {
            super(type);
        }

        @Override
        public void execute(Object sender, String name, String[] args) {
            try {
                if (args.length != 4) {
                    FMusic.side.sendMessage(sender, FMusic.getMessage().command.error);
                    return;
                }
                float obj = HudUtils.setHudAlpha(name, type, args[3]);
                if (obj == -1) {
                    FMusic.side.sendMessageTask(sender, FMusic.getMessage().command.error);
                    return;
                }

                FMusic.side.sendMessageTask(
                    sender,
                    FMusic.getMessage().hud.set4.replace(ARG.hud, FMusic.getMessage().hudList.getHud(type))
                        .replace(ARG.value, String.valueOf(obj)));
            } catch (Exception e) {
                FMusic.side.sendMessage(sender, FMusic.getMessage().command.error);
            }
        }
    }

    private static class HudMaxWidth extends AHudCommand {

        public HudMaxWidth(HudType type) {
            super(type);
        }

        @Override
        public void execute(Object sender, String name, String[] args) {
            try {
                if (args.length != 4) {
                    FMusic.side.sendMessage(sender, FMusic.getMessage().command.error);
                    return;
                }
                int width = HudUtils.setHudHudMaxWidth(name, type, args[3]);
                if (width == -1) {
                    FMusic.side.sendMessageTask(sender, FMusic.getMessage().command.error);
                    return;
                }

                FMusic.side.sendMessageTask(
                    sender,
                    FMusic.getMessage().hud.set6.replace(ARG.hud, FMusic.getMessage().hudList.getHud(type))
                        .replace(ARG.value, String.valueOf(width)));
            } catch (Exception e) {
                FMusic.side.sendMessage(sender, FMusic.getMessage().command.error);
            }
        }
    }

    private static class HudDir extends AHudCommand {

        private static final List<String> dir = new ArrayList<String>() {

            {
                for (HudPosType type : HudPosType.values()) {
                    this.add(type.name());
                }
            }
        };

        public HudDir(HudType type) {
            super(type);
        }

        @Override
        public void execute(Object sender, String name, String[] args) {
            if (args.length != 4) {
                FMusic.side.sendMessage(sender, FMusic.getMessage().command.error);
                return;
            }
            HudPosType type1 = HudUtils.setPos(name, type, args[3]);
            if (type1 == null) {
                FMusic.side.sendMessage(sender, FMusic.getMessage().command.error);
                return;
            }
            FMusic.side.sendMessage(
                sender,
                FMusic.getMessage().hud.set1.replace(ARG.hud, FMusic.getMessage().hudList.getHud(type))
                    .replace(ARG.value, FMusic.getMessage().hudList.getDir(type1)));
        }

        @Override
        public List<String> tab(Object player, String name, String[] args, int index) {
            if (args.length == index + 1) {
                return dir;
            }
            return Collections.emptyList();
        }
    }

    private static class HudColor extends AHudCommand {

        public HudColor(HudType type) {
            super(type);
        }

        @Override
        public void execute(Object sender, String name, String[] args) {
            if (args.length != 4) {
                FMusic.side.sendMessage(sender, FMusic.getMessage().command.error);
                return;
            }
            int color = HudUtils.setColor(name, type, args[3]);
            if (color == -1) {
                FMusic.side.sendMessage(sender, FMusic.getMessage().command.error);
                return;
            }
            FMusic.side.sendMessage(
                sender,
                FMusic.getMessage().hud.set2.replace(ARG.hud, FMusic.getMessage().hudList.getHud(type))
                    .replace(ARG.value, String.format("%06X", color & 0xFFFFFF)));
        }
    }

    private static class HudShadow extends AHudCommand {

        public HudShadow(HudType type) {
            super(type);
        }

        @Override
        public void execute(Object sender, String name, String[] args) {
            if (args.length == 3 || args.length == 4) {
                FMusic.side.sendMessage(
                    sender,
                    FMusic.getMessage().hud.set3.replace(ARG.hud, FMusic.getMessage().hudList.getHud(type))
                        .replace(
                            ARG.value,
                            HudUtils.setShadow(name, type, args.length == 4 ? args[3] : null)
                                ? FMusic.getMessage().hudList.enable
                                : FMusic.getMessage().hudList.disable));
                return;
            }
            FMusic.side.sendMessage(sender, FMusic.getMessage().command.error);
        }

        @Override
        public List<String> tab(Object player, String name, String[] args, int index) {
            if (args.length == index + 1) {
                return CommandHud.tf;
            }
            return Collections.emptyList();
        }
    }

    private static class HudLoop extends AHudCommand {

        public HudLoop(HudType type) {
            super(type);
        }

        @Override
        public void execute(Object sender, String name, String[] args) {
            if (args.length == 3 || args.length == 4) {
                FMusic.side.sendMessage(
                    sender,
                    FMusic.getMessage().hud.set5.replace(ARG.hud, FMusic.getMessage().hudList.getHud(type))
                        .replace(
                            ARG.value,
                            HudUtils.setLoop(name, type, args.length == 4 ? args[3] : null)
                                ? FMusic.getMessage().hudList.enable
                                : FMusic.getMessage().hudList.disable));
                return;
            }
            FMusic.side.sendMessage(sender, FMusic.getMessage().command.error);
        }

        @Override
        public List<String> tab(Object player, String name, String[] args, int index) {
            if (args.length == index + 1) {
                return CommandHud.tf;
            }
            return Collections.emptyList();
        }
    }

    private static class HudGap extends AHudCommand {

        public HudGap(HudType type) {
            super(type);
        }

        @Override
        public void execute(Object sender, String name, String[] args) {
            if (args.length != 4) {
                FMusic.side.sendMessage(sender, FMusic.getMessage().command.error);
                return;
            }
            int loop = HudUtils.setGap(name, type, args[3]);
            if (loop == -1) {
                FMusic.side.sendMessage(sender, FMusic.getMessage().command.error);
                return;
            }
            FMusic.side.sendMessage(
                sender,
                FMusic.getMessage().hud.set7.replace(ARG.hud, FMusic.getMessage().hudList.getHud(type))
                    .replace(ARG.value, String.valueOf(loop)));
        }

        @Override
        public List<String> tab(Object player, String name, String[] args, int index) {
            if (args.length == index + 1) {
                return CommandHud.tf;
            }
            return Collections.emptyList();
        }
    }

    private static class PicSize extends ACommand {

        @Override
        public void execute(Object sender, String name, String[] args) {
            if (args.length != 4 || !HudUtils.setPicSize(name, args[3])) {
                FMusic.side.sendMessage(sender, FMusic.getMessage().command.error);
                return;
            }
            FMusic.side.sendMessage(sender, FMusic.getMessage().hud.picSize.replace(ARG.value, args[2]));
        }
    }

    private static class PicRotate extends ACommand {

        @Override
        public void execute(Object sender, String name, String[] args) {
            if (args.length == 3 || args.length == 4) {
                FMusic.side.sendMessage(
                    sender,
                    FMusic.getMessage().hud.picRotate.replace(
                        ARG.value,
                        HudUtils.setPicRotate(name, args.length == 4 ? args[3] : null)
                            ? FMusic.getMessage().hudList.enable
                            : FMusic.getMessage().hudList.disable));
                return;
            }
            FMusic.side.sendMessage(sender, FMusic.getMessage().command.error);
        }

        @Override
        public List<String> tab(Object player, String name, String[] args, int index) {
            if (args.length == index + 1) {
                return CommandHud.tf;
            }
            return Collections.emptyList();
        }
    }

    private static class PicRotateSpeed extends ACommand {

        @Override
        public void execute(Object sender, String name, String[] args) {
            if (args.length != 4 || !HudUtils.setPicRotateSpeed(name, args[3])) {
                FMusic.side.sendMessage(sender, FMusic.getMessage().command.error);
                return;
            }
            FMusic.side.sendMessage(sender, FMusic.getMessage().hud.picSpeed.replace(ARG.value, args[3]));
        }
    }
}
