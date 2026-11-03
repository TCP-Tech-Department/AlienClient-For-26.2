package dev.luminous.asm.mixins;

import dev.luminous.Alien;
import dev.luminous.api.events.impl.FireworkShooterRotationEvent;
import dev.luminous.api.events.impl.RemoveFireworkEvent;
import dev.luminous.api.utils.Wrapper;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FireworkRocketEntity.class)
public class MixinFireworkRocketEntity implements Wrapper {

    @Shadow
    private int life;

    /**
     * 26.2: {@code Entity#updateRotation()} 已上移到 {@code Projectile#updateRotation()}，
     * 而且 tick 内部调用顺序被重排，直接按 INVOKE 定位不稳。这里改用「tick 里第一次读 life 字段」
     * 作为锚点 —— 它在 updateRotation() 之后、发射音效/寿命自增之前，正好是旧注入点的位置。
     * {@code getRotationVector()} 改名 {@code getLookAngle()}（下方 Redirect）。
     */
    @Inject(method = "tick", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/entity/projectile/FireworkRocketEntity;life:I",
            ordinal = 0, shift = At.Shift.BEFORE), cancellable = true)
    private void hookTickPre(CallbackInfo ci) {
        FireworkRocketEntity rocketEntity = (FireworkRocketEntity) (Object) this;
        RemoveFireworkEvent removeFireworkEvent = RemoveFireworkEvent.get(rocketEntity);
        Alien.EVENT_BUS.post(removeFireworkEvent);
        if (removeFireworkEvent.isCancelled()) {
            ci.cancel();
            // 26.2: level.isClient → level.isClientSide()；level.random 是 protected，
            // 改用 Entity#getRandom()。另外 26.2 的 Level 只有
            // playSound(Entity, BlockPos, SoundEvent, SoundSource, float, float) 收 SoundEvent
            // （double 版本收 Holder<SoundEvent>），所以这里改用 BlockPos 重载。
            if (life == 0 && !rocketEntity.isSilent()) {
                mc.level.playSound(null, rocketEntity.blockPosition(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.AMBIENT, 3.0f, 1.0f);
            }
            ++life;
            if (mc.level.isClientSide()) {
                mc.level.addParticle(ParticleTypes.FIREWORK, rocketEntity.getX(), rocketEntity.getY(), rocketEntity.getZ(),
                        rocketEntity.getRandom().nextGaussian() * 0.05, -rocketEntity.getDeltaMovement().y * 0.5,
                        rocketEntity.getRandom().nextGaussian() * 0.05);
            }
        }
    }

    /** 26.2: was {@code LivingEntity#getRotationVector()}，官方名 {@code Entity#getLookAngle()}。 */
    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/Entity;getLookAngle()Lnet/minecraft/world/phys/Vec3;"), require = 0)
    public Vec3 hook(Entity instance) {
        if (!(instance instanceof LivingEntity livingEntity)) {
            return instance.getLookAngle();
        }
        FireworkShooterRotationEvent event = FireworkShooterRotationEvent.get(livingEntity, livingEntity.getYRot(), livingEntity.getXRot());
        Alien.EVENT_BUS.post(event);
        if (event.isCancelled()) {
            return event.getRotationVector();
        }
        return livingEntity.getLookAngle();
    }
}
