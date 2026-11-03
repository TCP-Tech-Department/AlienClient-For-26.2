package dev.luminous.api.events.impl;

public class AmbientOcclusionEvent {
    private AmbientOcclusionEvent() {
    }

    private static final AmbientOcclusionEvent INSTANCE = new AmbientOcclusionEvent();

    public float lightLevel = -1;

    public static AmbientOcclusionEvent get() {
        INSTANCE.lightLevel = -1;
        return INSTANCE;
    }
}
