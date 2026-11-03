package dev.luminous.mod.commands.impl;

import dev.luminous.mod.commands.Command;
import net.minecraft.world.level.GameType;

import java.util.List;

public class GamemodeCommand extends Command {

    public GamemodeCommand() {
        super("gamemode", "[gamemode]");
    }

    @Override
    public void runCommand(String[] parameters) {
        if (parameters.length == 0) {
            sendUsage();
            return;
        }
        String moduleName = parameters[0];
        if (moduleName.equalsIgnoreCase("survival")) {
            mc.gameMode.setLocalMode(GameType.SURVIVAL);
        } else if (moduleName.equalsIgnoreCase("creative")) {
            mc.gameMode.setLocalMode(GameType.CREATIVE);
        } else if (moduleName.equalsIgnoreCase("adventure")) {
            mc.gameMode.setLocalMode(GameType.ADVENTURE);
        } else if (moduleName.equalsIgnoreCase("spectator")) {
            mc.gameMode.setLocalMode(GameType.SPECTATOR);
        }
    }

    @Override
    public String[] getAutocorrect(int count, List<String> seperated) {
        if (count == 1) {
            return new String[]{"survival", "creative", "adventure", "spectator"};
        }
        return null;
    }
}
