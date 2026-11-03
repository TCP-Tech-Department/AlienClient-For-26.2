package dev.luminous.mod.modules.settings.impl;

import dev.luminous.mod.modules.settings.Setting;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.util.function.BooleanSupplier;

public class BindSetting extends Setting {
    private final int defaultValue;
    public boolean holding = false;
    private int value;
    private boolean pressed = false;
    private boolean holdEnable = false;

    public BindSetting(String name, int value) {
        super(name);
        defaultValue = value;
        this.value = value;
    }

    public BindSetting(String name, int value, BooleanSupplier visibilityIn) {
        super(name, visibilityIn);
        defaultValue = value;
        this.value = value;
    }

    public int getValue() {
        return this.value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public String getKeyString() {
        if (value == -1) return "None";
        if (value < -1) {
            return "Mouse" + (Math.abs(value) - 1);
        }
        String kn = this.value > 0 ? GLFW.glfwGetKeyName(this.value, GLFW.glfwGetKeyScancode(this.value)) : "None";
        if (kn == null) {
            try {
                for (Field declaredField : GLFW.class.getDeclaredFields()) {
                    if (declaredField.getName().startsWith("GLFW_KEY_")) {
                        int a = (int) declaredField.get(null);
                        if (a == this.value) {
                            String nb = declaredField.getName().substring("GLFW_KEY_".length());
                            kn = nb.substring(0, 1).toUpperCase() + nb.substring(1).toLowerCase();
                        }
                    }
                }
            } catch (Exception ignored) {
                kn = "None";
            }
        }
        if (kn == null) {
            return "Unknown " + value;
        }
        return kn.toUpperCase();
    }

    public boolean isPressed() {
        return pressed;
    }

    public void setPressed(boolean pressed) {
        this.pressed = pressed;
    }

    public boolean isHoldEnable() {
        return holdEnable;
    }

    public void setHoldEnable(boolean holdEnable) {
        this.holdEnable = holdEnable;
    }

    public int getDefaultValue() {
        return defaultValue;
    }
}
