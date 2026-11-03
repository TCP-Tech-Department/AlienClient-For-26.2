package dev.luminous.asm.mixins;

import dev.luminous.Alien;
import dev.luminous.api.events.impl.LookDirectionEvent;
import dev.luminous.api.events.impl.SprintEvent;
import dev.luminous.mod.modules.impl.movement.Velocity;
import dev.luminous.mod.modules.impl.render.NoRender;
import dev.luminous.mod.modules.impl.render.ShaderModule;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import static dev.luminous.api.utils.Wrapper.mc;

/**
 * 26.2 改名对照：
 * <pre>
 * changeLookDirection(double, double) → turn(double, double)
 * isGlowing()                        → isCurrentlyGlowing()
 * pushAwayFrom(Entity)               → push(Entity)（内部 addVelocity(DDD) → push(DDD)）
 * setFlag(int, boolean)              → setSharedFlag(int, boolean)，冲刺 flag = 3
 * </pre>
 */
@Mixin(Entity.class)
public abstract class MixinEntity {

    /** 26.2: {@code Entity.FLAG_SPRINTING}（private static final int = 3）。 */
    @Unique
    private static final int SPRINTING_FLAG_INDEX = 3;

    /** 26.2: was {@code changeLookDirection}。 */
    @Inject(method = "turn(DD)V", at = @At(value = "HEAD"), cancellable = true)
    private void hookChangeLookDirection(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        if (Entity.class.cast(this) == mc.player) {
            LookDirectionEvent lookDirectionEvent = LookDirectionEvent.get(
                    Entity.class.cast(this), cursorDeltaX, cursorDeltaY);
            Alien.EVENT_BUS.post(lookDirectionEvent);
            if (lookDirectionEvent.isCancelled()) {
                ci.cancel();
            }
        }
    }

    @Inject(at = {@At("HEAD")}, method = "isInvisibleTo(Lnet/minecraft/world/entity/player/Player;)Z", cancellable = true)
    private void onIsInvisibleCheck(Player message, CallbackInfoReturnable<Boolean> cir) {
        if (NoRender.INSTANCE.isOn() && NoRender.INSTANCE.invisible.getValue()) {
            cir.setReturnValue(false);
        }
    }

    /** 26.2: was {@code isGlowing}。 */
    @Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true)
    void isGlowingHook(CallbackInfoReturnable<Boolean> cir) {
        if (ShaderModule.INSTANCE.isOn()) {
            cir.setReturnValue(ShaderModule.INSTANCE.shouldRender(Entity.class.cast(this)));
        }
    }

    @ModifyArgs(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;push(DDD)V"))
    private void pushAwayFromHook(Args args) {
        if (Entity.class.cast(this) == Minecraft.getInstance().player) {
            if (Velocity.INSTANCE.isOn() && Velocity.INSTANCE.entityPush.getValue()) {
                args.set(0, 0d);
                args.set(1, 0d);
                args.set(2, 0d);
            }
        }
    }

    @Inject(method = "isOnFire", at = @At("HEAD"), cancellable = true)
    void isOnFireHook(CallbackInfoReturnable<Boolean> cir) {
        if (NoRender.INSTANCE.isOn() && NoRender.INSTANCE.fireEntity.getValue()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = {"setSprinting"}, at = {@At("HEAD")}, cancellable = true)
    public void setSprintingHook(boolean sprinting, CallbackInfo ci) {
        if (Entity.class.cast(this) == Minecraft.getInstance().player) {
            SprintEvent event = SprintEvent.get();
            Alien.EVENT_BUS.post(event);
            if (event.isCancelled()) {
                ci.cancel();
                sprinting = event.isSprint();
                this.setSharedFlag(SPRINTING_FLAG_INDEX, sprinting);
            }
        }
    }

    @Shadow
    protected abstract void setSharedFlag(int flag, boolean value);
}
