package dev.luminous.core.impl;

import dev.luminous.core.Manager;
import net.minecraft.world.item.Items;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class CleanerManager extends Manager {
    private final ArrayList<String> list = new ArrayList<>();

    public CleanerManager() {
        read();
    }

    public ArrayList<String> getList() {
        return list;
    }

    public boolean inList(String name) {
        return list.contains(name) || list.contains(name.replace("block.minecraft.", "").replace("item.minecraft.", ""));
    }

    public void clear() {
        list.clear();
    }

    public void remove(String name) {
        name = name.replace("block.minecraft.", "").replace("item.minecraft.", "");
        list.remove(name);
    }

    public void add(String name) {
        name = name.replace("block.minecraft.", "").replace("item.minecraft.", "");
        if (!list.contains(name)) {
            list.add(name);
        }
    }

    public void read() {
        try {
            File friendFile = getFile("cleaner.txt");
            if (!friendFile.exists()) {
                add(Items.NETHERITE_SWORD.getDescriptionId());
                add(Items.NETHERITE_PICKAXE.getDescriptionId());
                add(Items.NETHERITE_HELMET.getDescriptionId());
                add(Items.NETHERITE_CHESTPLATE.getDescriptionId());
                add(Items.NETHERITE_LEGGINGS.getDescriptionId());
                add(Items.NETHERITE_BOOTS.getDescriptionId());
                add(Items.OBSIDIAN.getDescriptionId());
                add(Items.ENDER_CHEST.getDescriptionId());
                add(Items.ENDER_PEARL.getDescriptionId());
                add(Items.ENCHANTED_GOLDEN_APPLE.getDescriptionId());
                add(Items.EXPERIENCE_BOTTLE.getDescriptionId());
                add(Items.COBWEB.getDescriptionId());
                add(Items.POTION.getDescriptionId());
                add(Items.SPLASH_POTION.getDescriptionId());
                add(Items.TOTEM_OF_UNDYING.getDescriptionId());
                add(Items.END_CRYSTAL.getDescriptionId());
                add(Items.ELYTRA.getDescriptionId());
                add(Items.FLINT_AND_STEEL.getDescriptionId());
                add(Items.PISTON.getDescriptionId());
                add(Items.STICKY_PISTON.getDescriptionId());
                add(Items.REDSTONE_BLOCK.getDescriptionId());
                add(Items.GLOWSTONE.getDescriptionId());
                add(Items.RESPAWN_ANCHOR.getDescriptionId());
                add(Items.ANVIL.getDescriptionId());
                return;
            }
            List<String> list = IOUtils.readLines(new FileInputStream(friendFile), StandardCharsets.UTF_8);

            for (String s : list) {
                add(s);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void save() {
        try {
            File friendFile = getFile("cleaner.txt");
            PrintWriter printwriter = new PrintWriter(new OutputStreamWriter(new FileOutputStream(friendFile), StandardCharsets.UTF_8));
            for (String str : list) {
                printwriter.println(str);
            }
            printwriter.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}