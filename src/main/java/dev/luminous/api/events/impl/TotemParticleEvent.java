package dev.luminous.api.events.impl;

import dev.luminous.api.events.Event;

import java.awt.*;

public class TotemParticleEvent extends Event {
    private TotemParticleEvent() {
    }

    private static final TotemParticleEvent instance = new TotemParticleEvent();
    public double velocityX, velocityY, velocityZ;
    public Color color;

    public static TotemParticleEvent get(double velocityX, double velocityY, double velocityZ) {
        instance.velocityX = velocityX;
        instance.velocityY = velocityY;
        instance.velocityZ = velocityZ;
        return instance;
    }
}
