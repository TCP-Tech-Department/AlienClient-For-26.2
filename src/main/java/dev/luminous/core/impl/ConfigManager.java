package dev.luminous.core.impl;

import by.radioegor146.nativeobfuscator.Native;
import com.google.common.base.Splitter;
import dev.luminous.Alien;
import dev.luminous.core.Manager;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.client.HUD;
import dev.luminous.mod.modules.settings.Setting;
import dev.luminous.mod.modules.settings.impl.*;
import org.apache.commons.io.IOUtils;

import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

public class ConfigManager extends Manager {
    public static File options = getFile("options.txt");
    private final Hashtable<String, String> settings = new Hashtable<>();

    public ConfigManager() {
        read();
    }

    @Native
    public void load() {
        for (Module module : Alien.MODULE.getModules()) {
            for (Setting setting : module.getSettings()) {
                String line = module.getName() + "_" + setting.getName();
                switch (setting) {
                    case BooleanSetting s ->
                            s.setValueWithoutTask(Alien.CONFIG.getBoolean(line, s.getDefaultValue()));
                    case SliderSetting s ->
                            s.setValue(Alien.CONFIG.getFloat(line, (float) s.getDefaultValue()));
                    case BindSetting s -> {
                        s.setValue(Alien.CONFIG.getInt(line, s.getDefaultValue()));
                        s.setHoldEnable(Alien.CONFIG.getBoolean(line + "_hold"));
                    }
                    case EnumSetting<?> s -> s.loadSetting(Alien.CONFIG.getString(line));
                    case ColorSetting s -> {
                        s.setValue(new Color(Alien.CONFIG.getInt(line, s.getDefaultValue().getRGB()), true));
                        s.setSync(Alien.CONFIG.getBoolean(line + "Sync", s.getDefaultSync()));
                        if (s.injectBoolean) {
                            s.booleanValue = Alien.CONFIG.getBoolean(line + "Boolean", s.getDefaultBooleanValue());
                        }
                    }
                    case StringSetting s ->
                            s.setValue(Alien.CONFIG.getString(line, s.getDefaultValue()));
                    default -> {
                    }
                }
            }
            module.setState(Alien.CONFIG.getBoolean(module.getName() + "_state", module instanceof HUD));
        }
    }

    public void save() {
        PrintWriter printwriter = null;
        try {
            printwriter = new PrintWriter(new OutputStreamWriter(new FileOutputStream(options), StandardCharsets.UTF_8));

            for (Module module : Alien.MODULE.getModules()) {
                for (Setting setting : module.getSettings()) {
                    String line = module.getName() + "_" + setting.getName();
                    switch (setting) {
                        case BooleanSetting s -> printwriter.println(line + ":" + s.getValue());
                        case SliderSetting s -> printwriter.println(line + ":" + s.getValue());
                        case BindSetting s -> {
                            printwriter.println(line + ":" + s.getValue());
                            printwriter.println(line + "_hold" + ":" + s.isHoldEnable());
                        }
                        case EnumSetting<?> s -> printwriter.println(line + ":" + s.getValue().name());
                        case ColorSetting s -> {
                            printwriter.println(line + ":" + s.getValue().getRGB());
                            printwriter.println(line + "Sync:" + s.sync);
                            if (s.injectBoolean) {
                                printwriter.println(line + "Boolean:" + s.booleanValue);
                            }
                        }
                        case StringSetting s -> printwriter.println(line + ":" + s.getValue());
                        default -> {
                        }
                    }
                }
                printwriter.println(module.getName() + "_state:" + module.isOn());
            }
        } catch (Exception exception) {
            exception.printStackTrace();
            System.out.println("[" + Alien.NAME + "] Failed to save settings");
        } finally {
            IOUtils.closeQuietly(printwriter);
        }
    }

    @Native
    public void read() {
        try {
            final Splitter COLON_SPLITTER = Splitter.on(':');
            if (!options.exists()) {
                return;
            }
            List<String> list = IOUtils.readLines(new FileInputStream(options), StandardCharsets.UTF_8);
            for (String s : list) {
                try {
                    Iterator<String> iterator = COLON_SPLITTER.limit(2).split(s).iterator();
                    settings.put(iterator.next(), iterator.next());
                } catch (Exception var10) {
                    System.out.println("Skipping bad option: " + s);
                }
            }
        } catch (Exception exception) {
            exception.printStackTrace();
            System.out.println("[" + Alien.NAME + "] Failed to load settings");
        }
    }

    public int getInt(String setting, int defaultValue) {
        String s = settings.get(setting);
        if (s == null || !isInteger(s)) return defaultValue;
        return Integer.parseInt(s);
    }

    public float getFloat(String setting, float defaultValue) {
        String s = settings.get(setting);
        if (s == null || !isFloat(s)) return defaultValue;
        return Float.parseFloat(s);
    }

    public boolean getBoolean(String setting) {
        String s = settings.get(setting);
        return Boolean.parseBoolean(s);
    }

    public boolean getBoolean(String setting, boolean defaultValue) {
        if (settings.get(setting) != null) {
            String s = settings.get(setting);
            return Boolean.parseBoolean(s);
        } else {
            return defaultValue;
        }
    }

    public String getString(String setting) {
        return settings.get(setting);
    }

    public String getString(String setting, String defaultValue) {
        if (settings.get(setting) == null) {
            return defaultValue;
        }
        return settings.get(setting);
    }

    public boolean isInteger(final String str) {
        final Pattern pattern = Pattern.compile("^[-+]?[\\d]*$");
        return pattern.matcher(str).matches();
    }

    public boolean isFloat(String str) {
        String pattern = "^[-+]?[0-9]*\\.?[0-9]+([eE][-+]?[0-9]+)?$";
        return str.matches(pattern);
    }
}