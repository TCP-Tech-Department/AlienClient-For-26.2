package dev.luminous.mod.commands.impl;

import dev.luminous.Alien;
import dev.luminous.core.impl.PlayerManager;
import dev.luminous.mod.commands.Command;
import dev.luminous.mod.gui.windows.WindowsScreen;
import dev.luminous.mod.gui.windows.impl.ItemSelectWindow;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.List;

public class XrayCommand extends Command {

    public XrayCommand() {
        super("xray", "[\"\"/name/reset/clear/list] | [add/remove] [name]");
    }

    @Override
    public void runCommand(String[] parameters) {
        if (parameters.length == 0) {
            PlayerManager.screenToOpen = (new WindowsScreen(new ItemSelectWindow(Alien.XRAY)));
            //sendUsage();
            return;
        }
        switch (parameters[0]) {
            case "reset" -> {
                Alien.XRAY.clear();
                Alien.XRAY.add(Blocks.DIAMOND_ORE.getDescriptionId());
                Alien.XRAY.add(Blocks.DEEPSLATE_DIAMOND_ORE.getDescriptionId());
                Alien.XRAY.add(Blocks.GOLD_ORE.getDescriptionId());
                Alien.XRAY.add(Blocks.NETHER_GOLD_ORE.getDescriptionId());
                Alien.XRAY.add(Blocks.IRON_ORE.getDescriptionId());
                Alien.XRAY.add(Blocks.DEEPSLATE_IRON_ORE.getDescriptionId());
                Alien.XRAY.add(Blocks.REDSTONE_ORE.getDescriptionId());
                Alien.XRAY.add(Blocks.EMERALD_ORE.getDescriptionId());
                Alien.XRAY.add(Blocks.DEEPSLATE_EMERALD_ORE.getDescriptionId());
                Alien.XRAY.add(Blocks.DEEPSLATE_REDSTONE_ORE.getDescriptionId());
                Alien.XRAY.add(Blocks.COAL_ORE.getDescriptionId());
                Alien.XRAY.add(Blocks.DEEPSLATE_COAL_ORE.getDescriptionId());
                Alien.XRAY.add(Blocks.ANCIENT_DEBRIS.getDescriptionId());
                Alien.XRAY.add(Blocks.NETHER_QUARTZ_ORE.getDescriptionId());
                Alien.XRAY.add(Blocks.LAPIS_ORE.getDescriptionId());
                Alien.XRAY.add(Blocks.DEEPSLATE_LAPIS_ORE.getDescriptionId());
                sendChatMessage("§fBlocks list got reset");
                return;
            }
            case "clear" -> {
                Alien.XRAY.clear();
                sendChatMessage("§fBlocks list got clear");
                return;
            }
            case "list" -> {
                if (Alien.XRAY.getList().isEmpty()) {
                    sendChatMessage("§fBlocks list is empty");
                    return;
                }

                for (String name : Alien.XRAY.getList()) {
                    sendChatMessage("§a" + name);
                }
                return;
            }
            case "add" -> {
                if (parameters.length == 2) {
                    Alien.XRAY.add(parameters[1]);
                    sendChatMessage("§f" + parameters[1] + (Alien.XRAY.inWhitelist(parameters[1]) ? " §ahas been added" : " §chas been removed"));
                    return;
                }
                sendUsage();
                return;
            }
            case "remove" -> {
                if (parameters.length == 2) {
                    Alien.XRAY.remove(parameters[1]);
                    sendChatMessage("§f" + parameters[1] + (Alien.XRAY.inWhitelist(parameters[1]) ? " §ahas been added" : " §chas been removed"));
                    return;
                }
                sendUsage();
                return;
            }
        }

        if (parameters.length == 1) {
            sendChatMessage("§f" + parameters[0] + (Alien.XRAY.inWhitelist(parameters[0]) ? " §ais in whitelist" : " §cisn't in whitelist"));
            return;
        }

        sendUsage();
    }

    @Override
    public String[] getAutocorrect(int count, List<String> seperated) {
        if (count == 1) {
            String input = seperated.getLast().toLowerCase();
            List<String> correct = new ArrayList<>();
            List<String> list = List.of("add", "remove", "list", "reset", "clear");
            for (String x : list) {
                if (input.equalsIgnoreCase(Alien.getPrefix() + "xray") || x.toLowerCase().startsWith(input)) {
                    correct.add(x);
                }
            }
            int numCmds = correct.size();
            String[] commands = new String[numCmds];

            int i = 0;
            for (String x : correct) {
                commands[i++] = x;
            }

            return commands;
        }
        return null;
    }
}
