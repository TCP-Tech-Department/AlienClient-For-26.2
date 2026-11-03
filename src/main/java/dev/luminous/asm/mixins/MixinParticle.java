package dev.luminous.asm.mixins;

import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * 26.2: {@code Particle} 的速度字段从 {@code velocityX/Y/Z} 改名为 {@code xd/yd/zd}；
 * 颜色相关方法（{@code setColor}/{@code setAlpha}）不在 {@code Particle} 上，
 * 而在子类 {@code SingleQuadParticle} —— 见 {@link MixinTotemParticle}。
 */
@Mixin(Particle.class)
public abstract class MixinParticle {

    @Shadow
    protected double xd;
    @Shadow
    protected double yd;
    @Shadow
    protected double zd;
}
