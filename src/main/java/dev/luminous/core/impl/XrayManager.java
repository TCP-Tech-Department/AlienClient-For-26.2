package dev.luminous.core.impl;

import dev.luminous.Alien;
import dev.luminous.core.Manager;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.render.Xray;
import net.minecraft.world.level.block.Blocks;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class XrayManager extends Manager {
    private final ArrayList<String> list = new ArrayList<>();

    public XrayManager() {
        read();
    }

    public ArrayList<String> getList() {
        return list;
    }

    public boolean inWhitelist(String name) {
        return list.contains(name) || list.contains(name.replace("block.minecraft.", "").replace("item.minecraft.", ""));
    }

    public void clear() {
        list.clear();
    }

    public void remove(String name) {
        name = name.replace("block.minecraft.", "").replace("item.minecraft.", "");
        if (list.remove(name)) {
            if (!Module.nullCheck() && Xray.INSTANCE.isOn()) {
                mc.levelExtractor.allChanged();
            }
        }
    }

    public void add(String name) {
        name = name.replace("block.minecraft.", "").replace("item.minecraft.", "");
        if (!list.contains(name)) {
            list.add(name);
            if (!Module.nullCheck() && Xray.INSTANCE.isOn()) {
                mc.levelExtractor.allChanged();
            }
        }
    }

    public void read() {
        try {
            File friendFile = getFile("xrays.txt");
            if (!friendFile.exists()) {
                add(Blocks.DIAMOND_ORE.getDescriptionId());
                add(Blocks.DEEPSLATE_DIAMOND_ORE.getDescriptionId());
                add(Blocks.GOLD_ORE.getDescriptionId());
                add(Blocks.NETHER_GOLD_ORE.getDescriptionId());
                add(Blocks.IRON_ORE.getDescriptionId());
                add(Blocks.DEEPSLATE_IRON_ORE.getDescriptionId());
                add(Blocks.REDSTONE_ORE.getDescriptionId());
                add(Blocks.EMERALD_ORE.getDescriptionId());
                add(Blocks.DEEPSLATE_EMERALD_ORE.getDescriptionId());
                add(Blocks.DEEPSLATE_REDSTONE_ORE.getDescriptionId());
                add(Blocks.COAL_ORE.getDescriptionId());
                add(Blocks.DEEPSLATE_COAL_ORE.getDescriptionId());
                add(Blocks.ANCIENT_DEBRIS.getDescriptionId());
                add(Blocks.NETHER_QUARTZ_ORE.getDescriptionId());
                add(Blocks.LAPIS_ORE.getDescriptionId());
                add(Blocks.DEEPSLATE_LAPIS_ORE.getDescriptionId());
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
            File friendFile = getFile("xrays.txt");
            PrintWriter printwriter = new PrintWriter(new OutputStreamWriter(new FileOutputStream(friendFile), StandardCharsets.UTF_8));
            for (String str : list) {
                printwriter.println(str);
            }
            printwriter.close();
        } catch (Exception exception) {
            System.out.println("[" + Alien.NAME + "] Failed to save xrays");
        }
    }
}