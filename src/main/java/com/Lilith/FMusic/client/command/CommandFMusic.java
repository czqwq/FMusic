package com.Lilith.FMusic.client.command;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;

import com.Lilith.FMusic.Config;
import com.Lilith.FMusic.client.gui.DelayedGuiDisplayTicker;
import com.Lilith.FMusic.client.gui.FMusicHudConfigGui;

/**
 * 客户端指令:
 * - /fmusic pause_at_freeze &lt;true/false&gt; 单人游戏(未开放局域网)中, 按 Esc 暂停时音乐是否随之暂停
 * - /fmusic hudconfig 打开 HUD 可视化配置界面 (拖拽调整各模块位置)
 */
public class CommandFMusic extends CommandBase {

    @Override
    public String getCommandName() {
        return "fmusic";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/fmusic pause_at_freeze <true/false> | /fmusic hudconfig";
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        // 客户端指令, 无需 op 权限; 单人游戏限制在业务逻辑内检查
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("pause_at_freeze")) {
            processPauseAtFreeze(sender, args);
        } else if (args.length >= 1 && args[0].equalsIgnoreCase("hudconfig")) {
            // 打开 HUD 可视化配置界面 (延迟 1 tick, 照 PowerGoggles 模式)
            DelayedGuiDisplayTicker.create(new FMusicHudConfigGui(), 1);
        } else {
            sender.addChatMessage(
                new ChatComponentText("§e[FMusic]§f用法: /fmusic pause_at_freeze <true/false> | /fmusic hudconfig"));
        }
    }

    private void processPauseAtFreeze(ICommandSender sender, String[] args) {
        if (!canUse()) {
            sender.addChatMessage(new ChatComponentText("§e[FMusic]§c该指令仅在单人游戏(未开放局域网)中可用"));
            return;
        }
        if (args.length < 2) {
            sender.addChatMessage(
                new ChatComponentText(
                    "§e[FMusic]§f当前 pause_at_freeze = " + Config.pauseAtFreeze
                        + " (使用 /fmusic pause_at_freeze <true/false> 设置)"));
            return;
        }
        boolean value = Boolean.parseBoolean(args[1]);
        Config.pauseAtFreeze = value;
        Config.save();
        sender.addChatMessage(
            new ChatComponentText("§e[FMusic]§a已设置 pause_at_freeze = " + value + " (重启或 /music reload 后保持)"));
    }

    private boolean canUse() {
        Minecraft mc = Minecraft.getMinecraft();
        if (!mc.isSingleplayer()) {
            return false;
        }
        if (mc.getIntegratedServer() == null) {
            return false;
        }
        return !mc.getIntegratedServer()
            .getPublic();
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        List<String> list = new ArrayList<>();
        if (args.length == 1) {
            if ("pause_at_freeze".startsWith(args[0].toLowerCase())) {
                list.add("pause_at_freeze");
            }
            if ("hudconfig".startsWith(args[0].toLowerCase())) {
                list.add("hudconfig");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("pause_at_freeze")) {
            for (String s : new String[] { "true", "false" }) {
                if (s.startsWith(args[1].toLowerCase())) {
                    list.add(s);
                }
            }
        }
        return list;
    }
}
