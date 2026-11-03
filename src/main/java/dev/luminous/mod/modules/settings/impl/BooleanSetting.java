package dev.luminous.mod.modules.settings.impl;

import dev.luminous.mod.modules.settings.Setting;

import java.util.function.BooleanSupplier;

public class BooleanSetting extends Setting {
    private final boolean defaultValue;
    private boolean value;
    private boolean hasParent = false;
    private boolean open = false;
    private Runnable task = null;
    private boolean injectTask = false;

    public BooleanSetting(String name, boolean defaultValue) {
        super(name);
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public BooleanSetting(String name, boolean defaultValue, BooleanSupplier visibilityIn) {
        super(name, visibilityIn);
        this.defaultValue = defaultValue;
        this.value = defaultValue;
    }

    public boolean getValue() {
        return this.value;
    }

    public void setValue(boolean value) {
        this.value = value;
        if (injectTask) {
            task.run();
        }
    }

    public void setValueWithoutTask(boolean value) {
        this.value = value;
    }

    public boolean isOpen() {
        if (hasParent) {
            return open;
        } else {
            return true;
        }
    }

    public void setOpen(boolean open) {
        this.open = open;
    }

    public boolean hasParent() {
        return hasParent;
    }

    public BooleanSetting setParent() {
        hasParent = true;
        return this;
    }

    public BooleanSetting injectTask(Runnable task) {
        this.task = task;
        injectTask = true;
        return this;
    }

    public boolean getDefaultValue() {
        return defaultValue;
    }
}
