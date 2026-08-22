package com.Lilith.FMusic.mixins.early;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.Lilith.FMusic.client.FMusic;
import com.Lilith.FMusic.client.IGetSoundHandler;

import paulscode.sound.Library;
import paulscode.sound.SoundSystem;

@Mixin(value = SoundSystem.class, remap = false)
public class MixinSound implements IGetSoundHandler {

    @Shadow
    protected Library soundLibrary;

    public Library fMusic_Client$getSoundLibrary() {
        return soundLibrary;
    }

    @Inject(method = "<init>()V", at = @At("RETURN"))
    public void create(CallbackInfo ci) {
        if (FMusic.sound == null) {
            FMusic.sound = (SoundSystem) (Object) this;
        }
    }
}
