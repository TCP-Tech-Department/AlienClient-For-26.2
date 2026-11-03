package dev.luminous.api.events.impl;

import net.minecraft.world.entity.Entity;

public class RemoveEntityEvent {
    private RemoveEntityEvent() {
    }

    public static final RemoveEntityEvent instance = new RemoveEntityEvent();
    private Entity entity;
    private Entity.RemovalReason removalReason;

    public static RemoveEntityEvent get(Entity entity, Entity.RemovalReason removalReason) {
        instance.entity = entity;
        instance.removalReason = removalReason;
        return instance;
    }

    public Entity getEntity() {
        return entity;
    }

    public Entity.RemovalReason getRemovalReason() {
        return removalReason;
    }
}
