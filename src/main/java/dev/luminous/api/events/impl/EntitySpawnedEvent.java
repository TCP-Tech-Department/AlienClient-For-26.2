package dev.luminous.api.events.impl;

import net.minecraft.world.entity.Entity;

public class EntitySpawnedEvent {
    private EntitySpawnedEvent() {
    }

    private static final EntitySpawnedEvent INSTANCE = new EntitySpawnedEvent();
    private Entity entity;

    public static EntitySpawnedEvent get(Entity player) {
        INSTANCE.entity = player;
        return INSTANCE;
    }

    public Entity getEntity() {
        return entity;
    }
}
