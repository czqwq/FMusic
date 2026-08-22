package com.Lilith.FMusic.server.event;

import net.minecraft.command.ICommandSender;

import com.Lilith.FMusic.server.core.objs.music.PlayerAddMusicObj;

import cpw.mods.fml.common.eventhandler.Cancelable;
import cpw.mods.fml.common.eventhandler.Event;

@Cancelable
public class MusicAddEvent extends Event {

    /**
     * 添加的音乐
     */
    private final PlayerAddMusicObj music;
    /**
     * 添加者
     */
    private final ICommandSender player;

    public MusicAddEvent(PlayerAddMusicObj id, ICommandSender player) {
        this.music = id;
        this.player = player;
    }

    public ICommandSender getPlayer() {
        return player;
    }

    public PlayerAddMusicObj getMusic() {
        return music;
    }
}
