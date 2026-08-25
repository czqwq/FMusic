package com.Lilith.FMusic.server.bili.command;

import java.util.Collections;
import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.StatCollector;

import com.Lilith.FMusic.server.bili.BiliMusicBridge;

/**
 * /bilimusic 命令 (status / reload / reconnect / request / help)。
 */
public class BiliCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "bilimusic";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "bilimusic help";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        BiliMusicBridge bridge = BiliMusicBridge.instanceForCommand();
        if (bridge == null) {
            sender.addChatMessage(
                new net.minecraft.util.ChatComponentText(StatCollector.translateToLocal("bili.cmd.not_enabled")));
            return;
        }
        new BiliMusicCommand(bridge).execute(new ForgeAudience(sender), getCommandName(), args);
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        BiliMusicBridge bridge = BiliMusicBridge.instanceForCommand();
        if (bridge == null) {
            return Collections.emptyList();
        }
        return new BiliMusicCommand(bridge).suggest(new ForgeAudience(sender), args);
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public int compareTo(Object o) {
        return 0;
    }
}
