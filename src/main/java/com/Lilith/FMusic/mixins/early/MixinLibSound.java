package com.Lilith.FMusic.mixins.early;

import java.util.List;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.Lilith.FMusic.client.IGetSound;

import paulscode.sound.Channel;
import paulscode.sound.Library;

@Mixin(value = Library.class, remap = false)
public class MixinLibSound implements IGetSound {

    @Shadow
    protected List<Channel> streamingChannels;

    @Shadow
    protected List<Channel> normalChannels;

    public List<Channel> fMusic_Client$getNormalChannels() {
        return normalChannels;
    }

    public List<Channel> fMusic_Client$getStreamingChannels() {
        return streamingChannels;
    }
}
