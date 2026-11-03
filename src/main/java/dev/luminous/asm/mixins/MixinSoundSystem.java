package dev.luminous.asm.mixins;

import dev.luminous.Alien;
import dev.luminous.api.events.impl.PlaySoundEvent;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 26.2: {@code SoundSystem} → {@code net.minecraft.client.sounds.SoundEngine}；
 * {@code play(SoundInstance)} 现在返回 {@code SoundEngine.PlayResult}，
 * 因此取消播放改为 HEAD 处返回 {@code NOT_STARTED}。
 */
@Mixin(SoundEngine.class)
public class MixinSoundSystem {
    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void onPlay(SoundInstance soundInstance, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        PlaySoundEvent event = PlaySoundEvent.get(soundInstance);
        Alien.EVENT_BUS.post(event);
        if (event.isCancelled()) cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
    }
}
