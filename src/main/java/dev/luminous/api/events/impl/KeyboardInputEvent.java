package dev.luminous.api.events.impl;

import dev.luminous.api.events.Event;

public class KeyboardInputEvent extends Event {
    private KeyboardInputEvent() {
    }

    private static final KeyboardInputEvent INSTANCE = new KeyboardInputEvent();

    public static KeyboardInputEvent get() {
        INSTANCE.setCancelled(false);
        return INSTANCE;
    }
}
