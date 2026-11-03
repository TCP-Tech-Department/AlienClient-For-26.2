package dev.luminous.api.events.impl;

import dev.luminous.api.events.Event;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;

public class InventoryS2CPacketEvent extends Event {
    private InventoryS2CPacketEvent() {
    }

    private static final InventoryS2CPacketEvent INSTANCE = new InventoryS2CPacketEvent();
    public ClientboundContainerSetContentPacket packet;

    public static InventoryS2CPacketEvent get(ClientboundContainerSetContentPacket hand) {
        INSTANCE.packet = hand;
        INSTANCE.setCancelled(false);
        return INSTANCE;
    }
}
