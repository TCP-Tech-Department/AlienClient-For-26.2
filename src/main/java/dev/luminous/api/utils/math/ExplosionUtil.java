package dev.luminous.api.utils.math;

import dev.luminous.api.utils.Wrapper;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public class ExplosionUtil implements Wrapper {

    public static float anchorDamage(BlockPos pos, LivingEntity target, LivingEntity predict) {
        return DamageUtils.anchorDamage(target, predict, Vec3.atCenterOf(pos));
    }

    public static float calculateDamage(Vec3 pos, LivingEntity entity, LivingEntity predict, float power) {
        return DamageUtils.explosionDamage(entity, predict, pos, power * 2);
    }
}
