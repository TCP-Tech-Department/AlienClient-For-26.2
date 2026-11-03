package dev.luminous.api.events.impl;

import dev.luminous.api.events.Event;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class FireworkShooterRotationEvent extends Event {
    private FireworkShooterRotationEvent() {
    }

    private static final FireworkShooterRotationEvent instance = new FireworkShooterRotationEvent();
    public LivingEntity shooter;
    public float pitch;
    public float yaw;

    public static FireworkShooterRotationEvent get(LivingEntity shooter, float yaw, float pitch) {
        instance.shooter = shooter;
        instance.yaw = yaw;
        instance.pitch = pitch;
        instance.setCancelled(false);
        return instance;
    }

    public final Vec3 getRotationVector() {
        float f = pitch * (float) (Math.PI / 180.0);
        float g = -yaw * (float) (Math.PI / 180.0);
        float h = Mth.cos(g);
        float i = Mth.sin(g);
        float j = Mth.cos(f);
        float k = Mth.sin(f);
        return new Vec3(i * j, -k, h * j);
    }
}
