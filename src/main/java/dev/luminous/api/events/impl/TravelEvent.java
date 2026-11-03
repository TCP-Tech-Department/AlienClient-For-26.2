package dev.luminous.api.events.impl;

import dev.luminous.api.events.Event;
import net.minecraft.world.entity.player.Player;

public class TravelEvent extends Event {
    private TravelEvent() {
    }

    private static final TravelEvent INSTANCE = new TravelEvent();
    private Player entity;

    public static TravelEvent get(Stage stage, Player entity) {
        INSTANCE.entity = entity;
        INSTANCE.stage = stage;
        INSTANCE.setCancelled(false);
        return INSTANCE;
    }

    public Player getEntity() {
        return entity;
    }
}