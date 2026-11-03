package dev.luminous.api.events.impl;

import dev.luminous.api.events.Event;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;

public class RemoveFireworkEvent extends Event {
    private RemoveFireworkEvent() {
    }

    public static final RemoveFireworkEvent instance = new RemoveFireworkEvent();
    private FireworkRocketEntity entity;

    public static RemoveFireworkEvent get(FireworkRocketEntity entity) {
        instance.entity = entity;
        instance.setCancelled(false);
        return instance;
    }

    public FireworkRocketEntity getRocketEntity() {
        return entity;
    }
}