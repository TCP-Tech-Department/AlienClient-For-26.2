package dev.luminous.api.events.impl;

import dev.luminous.api.events.Event;
import net.minecraft.world.entity.Entity;

public class RenderEntityEvent extends Event {
    private RenderEntityEvent() {
    }

    private static final RenderEntityEvent INSTANCE = new RenderEntityEvent();
    private Entity entity;

    public static RenderEntityEvent get(Entity entity) {
        INSTANCE.entity = entity;
        INSTANCE.setCancelled(false);
        return INSTANCE;
    }

    public Entity getEntity() {
        return this.entity;
    }
}