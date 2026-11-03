package dev.luminous.api.events.impl;

import dev.luminous.api.events.Event;

public class ChunkOcclusionEvent extends Event {
    private ChunkOcclusionEvent() {
    }

    private static final ChunkOcclusionEvent INSTANCE = new ChunkOcclusionEvent();

    public static ChunkOcclusionEvent get() {
        INSTANCE.setCancelled(false);
        return INSTANCE;
    }
}
