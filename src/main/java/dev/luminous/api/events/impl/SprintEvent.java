package dev.luminous.api.events.impl;

import dev.luminous.api.events.Event;

public class SprintEvent extends Event {
    private SprintEvent() {
    }

    private static final SprintEvent instance = new SprintEvent();

    public static SprintEvent get() {
        instance.sprint = false;
        instance.setCancelled(false);
        return instance;
    }

    private boolean sprint;

    public boolean isSprint() {
        return sprint;
    }

    public void setSprint(boolean sprint) {
        this.sprint = sprint;
    }
}
