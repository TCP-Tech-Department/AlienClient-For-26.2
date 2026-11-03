package dev.luminous.mod.modules.settings.impl;

import dev.luminous.api.utils.math.Timer;
import dev.luminous.mod.modules.impl.client.ColorsModule;
import dev.luminous.mod.modules.settings.Setting;

import java.awt.*;
import java.util.function.BooleanSupplier;

public class ColorSetting extends Setting {
    public static final Timer timer = new Timer();
    public static final float effectSpeed = 4;
    private final Color defaultValue;
    public boolean sync = false;
    public boolean injectBoolean = false;
    public boolean booleanValue = false;
    private Color value;
    private boolean defaultSync = false;
    private boolean defaultBooleanValue = false;
    private boolean allowClientColor = true;

    public ColorSetting(String name) {
        this(name, new Color(255, 255, 255));
        defaultSync = true;
    }

    public ColorSetting(String name, BooleanSupplier visibilityIn) {
        super(name, visibilityIn);
        this.value = new Color(255, 255, 255);
        this.defaultValue = this.value;
        defaultSync = true;
    }

    public ColorSetting(String name, Color defaultValue) {
        super(name);
        this.value = defaultValue;
        this.defaultValue = defaultValue;
    }

    public ColorSetting(String name, Color defaultValue, BooleanSupplier visibilityIn) {
        super(name, visibilityIn);
        this.value = defaultValue;
        this.defaultValue = defaultValue;
    }

    public ColorSetting(String name, int defaultValue) {
        this(name, new Color(defaultValue, true));
    }

    public ColorSetting(String name, int defaultValue, BooleanSupplier visibilityIn) {
        this(name, new Color(defaultValue, true), visibilityIn);
    }

    public Color getValue() {
        if (sync) {
            if (allowClientColor) {
                Color preColor = ColorsModule.INSTANCE.clientColor.getValue();
                setValue(new Color(preColor.getRed(), preColor.getGreen(), preColor.getBlue(), value.getAlpha()));
            } else {
                float[] HSB = Color.RGBtoHSB(value.getRed(), value.getGreen(), value.getBlue(), null);
                Color preColor = Color.getHSBColor(((float) timer.getMs() * 0.36f * effectSpeed / 20f) % 361 / 360, HSB[1], HSB[2]);
                setValue(new Color(preColor.getRed(), preColor.getGreen(), preColor.getBlue(), value.getAlpha()));
            }
        }
        return this.value;
    }

    public void setValue(Color value) {
        this.value = value;
    }

    public void setValue(int value) {
        this.value = new Color(value, true);
    }

    public void setSync(boolean sync) {
        this.sync = sync;
    }

    public ColorSetting injectBoolean(boolean value) {
        injectBoolean = true;
        defaultBooleanValue = value;
        booleanValue = value;
        return this;
    }

    public ColorSetting allowClientColor(boolean value) {
        allowClientColor = value;
        return this;
    }

    public Color getDefaultValue() {
        return defaultValue;
    }

    public boolean getDefaultBooleanValue() {
        return defaultBooleanValue;
    }

    public boolean getDefaultSync() {
        return defaultSync;
    }
}
