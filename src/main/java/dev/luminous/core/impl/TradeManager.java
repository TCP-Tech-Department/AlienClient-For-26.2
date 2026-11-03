package dev.luminous.core.impl;

import by.radioegor146.nativeobfuscator.Native;
import dev.luminous.core.Manager;
import net.minecraft.world.item.Items;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class TradeManager extends Manager {
    private final ArrayList<String> list = new ArrayList<>();

    public TradeManager() {
        read();
    }

    public ArrayList<String> getList() {
        return list;
    }

    public void clear() {
        list.clear();
    }

    public boolean inWhitelist(String name) {
        return list.contains(name) || list.contains(name.replace("block.minecraft.", "").replace("item.minecraft.", ""));
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

    @Native
    public void read() {
        try {
            File friendFile = getFile("trades.txt");
            if (!friendFile.exists()) {
                add(Items.ENCHANTED_BOOK.getDescriptionId());
                add(Items.DIAMOND_BLOCK.getDescriptionId());
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
            File friendFile = getFile("trades.txt");
            PrintWriter printwriter = new PrintWriter(new OutputStreamWriter(new FileOutputStream(friendFile), StandardCharsets.UTF_8));
            for (String str : list) {
                printwriter.println(str);
            }
            printwriter.close();
        } catch (Exception exception) {
            exception.printStackTrace();
        }
    }
}