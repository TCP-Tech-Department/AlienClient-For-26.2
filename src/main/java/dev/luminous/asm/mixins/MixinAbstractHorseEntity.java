package dev.luminous.asm.mixins;

import dev.luminous.mod.modules.impl.movement.EntityControl;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 26.2: {@code AbstractHorse#isSaddled()} 不再由 AbstractHorse 覆写，而是统一声明在
 * {@code Mob#isSaddled()}（装备槽 SADDLE 判定）。Mixin 只能注入「声明该方法的类」，
 * 所以 @Mixin 目标改为 {@link Mob}，并用 instanceof 限定只影响马类实体，
 * 语义与旧版（只改 AbstractHorse）保持一致。
 */
@Mixin(Mob.class)
public abstract class MixinAbstractHorseEntity {

    @Inject(method = "isSaddled", at = @At("HEAD"), cancellable = true)
    public void onIsSaddled(CallbackInfoReturnable<Boolean> cir) {
        if (!(Mob.class.cast(this) instanceof AbstractHorse)) {
            return;
        }
        if (EntityControl.INSTANCE.isOn()) {
            cir.setReturnValue(true);
        }
    }
}
