package dev.luminous.asm.mixins;

import dev.luminous.Alien;
import dev.luminous.api.events.impl.ParticleEvent;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.2: {@code ParticleManager} → {@code net.minecraft.client.particle.ParticleEngine}（同包），
 * {@code addParticle(Particle)} → {@code add(Particle)}。
 */
@Mixin(ParticleEngine.class)
public class MixinParticleManager {
    @Inject(at = @At("HEAD"), method = "add", cancellable = true)
    public void onAddParticle(Particle particle, CallbackInfo ci) {
        ParticleEvent event = ParticleEvent.get(particle);
        Alien.EVENT_BUS.post(event);
        if (event.isCancelled()) {
            ci.cancel();
        }
    }
}
