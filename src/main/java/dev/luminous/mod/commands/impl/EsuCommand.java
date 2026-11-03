package dev.luminous.mod.commands.impl;

import dev.luminous.mod.commands.Command;
import java.util.List;

public class EsuCommand extends Command {
    public EsuCommand() {
        super("esu", "");
    }

    @Override
    public void runCommand(String[] parameters) {
        // 已禁用外连 QQ API
    }

    @Override
    public String[] getAutocorrect(int count, List<String> seperated) {
        return null;
    }
}