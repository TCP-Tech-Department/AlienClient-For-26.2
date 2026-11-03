package dev.luminous.mod.commands.impl;

import dev.luminous.Alien;
import dev.luminous.core.impl.PlayerManager;
import dev.luminous.mod.commands.Command;
import dev.luminous.mod.gui.windows.WindowsScreen;
import dev.luminous.mod.gui.windows.impl.ItemSelectWindow;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public class TradeCommand extends Command {

    public TradeCommand() {
        super("trade", "[\"\"/name/reset/clear/list] | [add/remove] [name]");
    }

    @Override
    public void runCommand(String[] parameters) {
        if (parameters.length == 0) {
            PlayerManager.screenToOpen = (new WindowsScreen(new ItemSelectWindow(Alien.TRADE)));
            return;
        }
        switch (parameters[0]) {
            case "reset" -> {
                Alien.TRADE.clear();
                Alien.TRADE.add(Items.ENCHANTED_BOOK.getDescriptionId());
                Alien.TRADE.add(Items.DIAMOND_BLOCK.getDescriptionId());
                sendChatMessage("§fItems list got reset");
                return;
            }
            case "clear" -> {
                Alien.TRADE.clear();
                sendChatMessage("§fItems list got clear");
                return;
            }
            case "list" -> {
                if (Alien.TRADE.getList().isEmpty()) {
                    sendChatMessage("§fItems list is empty");
                    return;
                }

                for (String name : Alien.TRADE.getList()) {
                    sendChatMessage("§a" + name);
                }
                return;
            }
            case "add" -> {
                if (parameters.length == 2) {
                    Alien.TRADE.add(parameters[1]);
                    sendChatMessage("§f" + parameters[1] + (Alien.TRADE.inWhitelist(parameters[1]) ? " §ahas been added" : " §chas been removed"));
                    return;
                }
                sendUsage();
                return;
            }
            case "remove" -> {
                if (parameters.length == 2) {
                    Alien.TRADE.remove(parameters[1]);
                    sendChatMessage("§f" + parameters[1] + (Alien.TRADE.inWhitelist(parameters[1]) ? " §ahas been added" : " §chas been removed"));
                    return;
                }
                sendUsage();
                return;
            }
        }

        if (parameters.length == 1) {
            sendChatMessage("§f" + parameters[0] + (Alien.TRADE.inWhitelist(parameters[0]) ? " §ais in whitelist" : " §cisn't in whitelist"));
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
                if (input.equalsIgnoreCase(Alien.getPrefix() + "trade") || x.toLowerCase().startsWith(input)) {
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
