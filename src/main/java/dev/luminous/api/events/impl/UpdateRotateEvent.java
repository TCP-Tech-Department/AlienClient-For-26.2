package dev.luminous.api.events.impl;

public class UpdateRotateEvent {
    private UpdateRotateEvent() {
    }

    private static final UpdateRotateEvent instance = new UpdateRotateEvent();
    private float yaw;
    private float pitch;
    private boolean modified;

    public static UpdateRotateEvent get(float yaw, float pitch) {
        instance.yaw = yaw;
        instance.pitch = pitch;
        instance.modified = false;
        return instance;
    }

    public float getYaw() {
        return this.yaw;
    }

    public void setYaw(float yaw) {
        modified = true;
        setYawWithoutSync(yaw);
    }

    public float getPitch() {
        return this.pitch;
    }

    public void setPitch(float pitch) {
        modified = true;
        setPitchWithoutSync(pitch);
    }

    public boolean isModified() {
        return modified;
    }

    public void setRotation(final float yaw, final float pitch) {
        this.setYaw(yaw);
        this.setPitch(pitch);
    }

    public void setYawWithoutSync(float yaw) {
        this.yaw = yaw;
    }

    public void setPitchWithoutSync(float pitch) {
        this.pitch = pitch;
    }
}
