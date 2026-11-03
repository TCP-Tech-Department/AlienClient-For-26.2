package dev.luminous.api.utils.player;

import dev.luminous.api.utils.Wrapper;
import dev.luminous.mod.modules.impl.movement.HoleSnap;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

public class MovementUtil implements Wrapper {
    public static boolean isMoving() {
        return mc.player.input.getMoveVector().y != 0.0 || mc.player.input.getMoveVector().x != 0.0 || HoleSnap.INSTANCE.isOn();
    }

    public static boolean isStatic() {
        return mc.player.getDeltaMovement().x == 0 && mc.player.onGround() && mc.player.getDeltaMovement().z == 0;
    }

    public static boolean isJumping() {
        return mc.player.input.keyPresses.jump();
    }

    public static double getDistance2D() {
        double xDist = mc.player.getX() - mc.player.xo;
        double zDist = mc.player.getZ() - mc.player.zo;
        return Math.sqrt(xDist * xDist + zDist * zDist);
    }

    public static double getJumpSpeed() {
        double defaultSpeed = 0.0;

        if (mc.player.hasEffect(MobEffects.JUMP_BOOST)) {
            int amplifier = mc.player.getEffect(MobEffects.JUMP_BOOST).getAmplifier();
            defaultSpeed += (amplifier + 1) * 0.1;
        }

        return defaultSpeed;
    }

    public static double[] directionSpeed(double speed) {
        float forward = mc.player.input.getMoveVector().y;
        float side = mc.player.input.getMoveVector().x;
        return directionSpeed(speed, forward, side);
    }

    private static double[] directionSpeed(double speed, float forward, float side) {
        float yaw = mc.player.yRotO + (mc.player.getYRot() - mc.player.yRotO) * mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        if (forward != 0.0f) {
            if (side > 0.0f) {
                yaw += ((forward > 0.0f) ? -45 : 45);
            } else if (side < 0.0f) {
                yaw += ((forward > 0.0f) ? 45 : -45);
            }
            side = 0.0f;
            if (forward > 0.0f) {
                forward = 1.0f;
            } else if (forward < 0.0f) {
                forward = -1.0f;
            }
        }
        final double sin = Math.sin(Math.toRadians(yaw + 90.0f));
        final double cos = Math.cos(Math.toRadians(yaw + 90.0f));
        final double posX = forward * speed * cos + side * speed * sin;
        final double posZ = forward * speed * sin - side * speed * cos;
        return new double[]{posX, posZ};
    }

    public static double getMotionX() {
        return mc.player.getDeltaMovement().x;
    }

    public static void setMotionX(double x) {
        Vec3 motion = mc.player.getDeltaMovement();
        mc.player.setDeltaMovement(x, motion.y, motion.z);
    }

    public static double getMotionY() {
        return mc.player.getDeltaMovement().y;
    }

    public static void setMotionY(double y) {
        Vec3 motion = mc.player.getDeltaMovement();
        mc.player.setDeltaMovement(motion.x, y, motion.z);
    }

    public static double getMotionZ() {
        return mc.player.getDeltaMovement().z;
    }

    public static void setMotionZ(double z) {
        Vec3 motion = mc.player.getDeltaMovement();
        mc.player.setDeltaMovement(motion.x, motion.y, z);
    }

    public static double getSpeed(boolean slowness) {
        double defaultSpeed = 0.2873;
        return getSpeed(slowness, defaultSpeed);
    }

    public static double getSpeed(boolean slowness, double defaultSpeed) {
        if (mc.player.hasEffect(MobEffects.SPEED)) {
            int amplifier = mc.player.getEffect(MobEffects.SPEED)
                    .getAmplifier();

            defaultSpeed *= 1.0 + 0.2 * (amplifier + 1);
        }

        if (slowness && mc.player.hasEffect(MobEffects.SLOWNESS)) {
            int amplifier = mc.player.getEffect(MobEffects.SLOWNESS)
                    .getAmplifier();

            defaultSpeed /= 1.0 + 0.2 * (amplifier + 1);
        }

        if (mc.player.isShiftKeyDown()) defaultSpeed /= 5;
        return defaultSpeed;
    }
}
