package dev.luminous.core;

import dev.luminous.Alien;
import net.minecraft.client.Minecraft;

import java.io.File;

public class Manager {
    public static final Minecraft mc = Minecraft.getInstance();

    public static File getFile(String s) {
        File folder = getFolder();
        return new File(folder, s);
    }

    public static File getFolder() {
        File folder = new File(mc.gameDirectory.getPath() + File.separator + Alien.NAME.toLowerCase());
        if (!folder.exists()) {
            folder.mkdirs();
        }
        return folder;
    }
}
