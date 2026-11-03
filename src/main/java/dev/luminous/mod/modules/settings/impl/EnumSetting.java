package dev.luminous.mod.modules.settings.impl;

import dev.luminous.mod.modules.settings.EnumConverter;
import dev.luminous.mod.modules.settings.Setting;

import java.util.function.BooleanSupplier;

public class EnumSetting<T extends Enum<T>> extends Setting {
    private final T defaultValue;
    private T value;
    private Runnable task = null;
    private boolean injectTask = false;

    public EnumSetting(String name, T defaultValue) {
        super(name);
        this.value = defaultValue;
        this.defaultValue = defaultValue;
    }

    public EnumSetting(String name, T defaultValue, BooleanSupplier visibilityIn) {
        super(name, visibilityIn);
        this.value = defaultValue;
        this.defaultValue = defaultValue;
    }

    public void loadSetting(String enumString) {
        EnumConverter converter = new EnumConverter();
        if (enumString == null) {
            value = defaultValue;
            return;
        }
        Enum<?> value = converter.get(defaultValue, enumString);
        if (value != null) {
            this.value = (T) value;
        } else {
            this.value = defaultValue;
        }
    }

    public void increaseEnum() {
        value = (T) EnumConverter.increaseEnum(value);
        if (injectTask) {
            task.run();
        }
    }

    public void resetValue() {
        this.value = defaultValue;
    }

    public T getValue() {
        return this.value;
    }

    public void setValue(T value) {
        this.value = value;
    }

    public void setEnumValue(String value) {
        for (T e : this.value.getDeclaringClass().getEnumConstants()) {
            if (!e.name().equalsIgnoreCase(value)) continue;
            this.value = e;
            if (injectTask) {
                task.run();
            }
        }
    }

    public EnumSetting<T> injectTask(Runnable task) {
        this.task = task;
        injectTask = true;
        return this;
    }

    public boolean is(T mode) {
        return getValue() == mode;
    }

    public T getDefaultValue() {
        return defaultValue;
    }
}