package com.Lilith.FMusic.server;

import java.util.List;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommand;
import net.minecraft.command.ICommandSender;

import com.Lilith.FMusic.server.core.command.CommandEX;

public class CommandForge extends CommandBase {

    @Override
    public String getCommandName() {
        return "music";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "music help";
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        CommandEX.execute(sender, sender.getCommandSenderName(), args);
    }

    @Override
    public List<String> addTabCompletionOptions(ICommandSender sender, String[] args) {
        return CommandEX.getTabList(sender, sender.getCommandSenderName(), args);
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public int compareTo(ICommand o) {
        return 0;
    }
}
