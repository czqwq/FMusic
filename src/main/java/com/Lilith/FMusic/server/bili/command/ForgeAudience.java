package com.Lilith.FMusic.server.bili.command;

import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;

/**
 * ICommandSender → CommandAudience 适配 (Forge 服务端)。
 */
public final class ForgeAudience implements CommandAudience {

    private final ICommandSender sender;

    public ForgeAudience(ICommandSender sender) {
        this.sender = sender;
    }

    @Override
    public String name() {
        return sender == null ? "?" : sender.getCommandSenderName();
    }

    @Override
    public boolean hasPermission(String permission) {
        if (sender instanceof MinecraftServer) {
            return true;
        }
        if (sender instanceof EntityPlayerMP) {
            return ((EntityPlayerMP) sender).canCommandSenderUseCommand(2, "bilimusic");
        }
        return false;
    }

    @Override
    public void sendMessage(String legacyMessage) {
        if (sender == null) {
            return;
        }
        if (sender instanceof EntityPlayerMP) {
            ((EntityPlayerMP) sender).addChatMessage(new ChatComponentText(legacyMessage));
        } else {
            MinecraftServer.getServer()
                .logInfo(legacyMessage);
        }
    }
}
