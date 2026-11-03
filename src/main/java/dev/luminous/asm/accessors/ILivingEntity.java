package dev.luminous.asm.accessors;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 26.2: 字段名全部未变（attackStrengthTicker 为 protected，noJumpDelay / swimAmount / swimAmountO
 * 为 private），无需改名。
 */
@Mixin(LivingEntity.class)
public interface ILivingEntity {
    @Accessor("attackStrengthTicker")
    int getLastAttackedTicks();

    @Accessor("noJumpDelay")
    int getLastJumpCooldown();

    @Accessor("noJumpDelay")
    void setLastJumpCooldown(int val);

    @Accessor("swimAmount")
    float getLeaningPitch();

    @Accessor("swimAmount")
    void setLeaningPitch(float val);

    @Accessor("swimAmountO")
    void setLastLeaningPitch(float val);

    /**
     * 26.2: {@code LivingEntity#tryUseTotem(DamageSource)} 改名为
     * {@code private boolean checkTotemDeathProtection(DamageSource)}
     * （LivingEntity.java:1386，内部按 {@code DataComponents.DEATH_PROTECTION} 找护身符，
     * 消耗一个、置血量为 1、套用效果并广播 entity event 35）。
     * private 方法用 @Invoker 即可调用，FakePlayer 的假伤害结算需要它。
     */
    @Invoker("checkTotemDeathProtection")
    boolean alienClient$checkTotemDeathProtection(DamageSource source);
}
