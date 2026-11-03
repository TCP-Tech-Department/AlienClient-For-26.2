package dev.luminous.api.events.impl;

import dev.luminous.api.events.Event;

public class TimerEvent extends Event {
    private TimerEvent() {
    }

    private static final TimerEvent instance = new TimerEvent();
    private float timer;
    private boolean modified;

    public static TimerEvent getEvent() {
        instance.timer = 1f;
        instance.modified = false;
        instance.setCancelled(false);
        return instance;
    }

    public float get() {
        return this.timer;
    }

    public void set(float timer) {
        this.modified = true;
        this.timer = timer;
    }

    public boolean isModified() {
        return this.modified;
    }
}
