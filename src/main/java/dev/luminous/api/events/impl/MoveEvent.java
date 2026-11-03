package dev.luminous.api.events.impl;

import dev.luminous.api.events.Event;

public class MoveEvent extends Event {
    private MoveEvent() {
    }

    private static final MoveEvent instance = new MoveEvent();
    public boolean modify;
    private double x;
    private double y;
    private double z;

    public static MoveEvent get(double x, double y, double z) {
        instance.modify = false;
        instance.x = x;
        instance.y = y;
        instance.z = z;
        instance.setCancelled(false);
        return instance;
    }

    public double getX() {
        return this.x;
    }

    public void setX(double x) {
        modify = true;
        this.x = x;
    }

    public double getY() {
        return this.y;
    }

    public void setY(double y) {
        modify = true;
        this.y = y;
    }

    public double getZ() {
        return this.z;
    }

    public void setZ(double z) {
        modify = true;
        this.z = z;
    }
}
