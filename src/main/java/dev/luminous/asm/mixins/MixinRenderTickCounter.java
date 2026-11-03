package dev.luminous.asm.mixins;

import dev.luminous.Alien;
import dev.luminous.api.events.impl.TimerEvent;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 26.2: {@code RenderTickCounter.Dynamic} → {@code DeltaTracker.Timer}；
 * {@code beginRenderTick(J)I} → {@code advanceGameTime(J)I}；
 * 字段 {@code lastFrameDuration/tickDelta/prevTimeMillis/tickTime} →
 * {@code deltaTicks/deltaTickResidual/lastMs/msPerTick}（语义一一对应）。
 */
@Mixin(DeltaTracker.Timer.class)
public class MixinRenderTickCounter {
    @Shadow
    private float deltaTicks;
    @Shadow
    private float deltaTickResidual;
    @Shadow
    private long lastMs;
    @Final
    @Shadow
    private float msPerTick;

    @Inject(method = "advanceGameTime", at = @At("HEAD"), cancellable = true)
    private void beginRenderTickHook(long timeMillis, CallbackInfoReturnable<Integer> cir) {
        TimerEvent event = TimerEvent.getEvent();
        Alien.EVENT_BUS.post(event);
        if (!event.isCancelled()) {
            float timer;
            if (event.isModified()) {
                timer = event.get();
            } else {
                timer = Alien.TIMER.get();
            }
            if (timer == 1) return;
            this.deltaTicks = ((timeMillis - this.lastMs) / this.msPerTick) * timer;
            this.lastMs = timeMillis;
            this.deltaTickResidual += this.deltaTicks;
            int i = (int) this.deltaTickResidual;
            this.deltaTickResidual -= i;
            cir.setReturnValue(i);
        }
    }
}
