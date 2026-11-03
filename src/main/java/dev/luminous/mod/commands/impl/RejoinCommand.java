package dev.luminous.mod.commands.impl;

import dev.luminous.mod.commands.Command;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.chat.Component;

import java.util.List;

public class RejoinCommand extends Command {

    public RejoinCommand() {
        super("rejoin", "");
    }

    @Override
    public void runCommand(String[] parameters) {
        mc.execute(() -> {
            if (mc.level != null && mc.getCurrentServer() != null) {
                ServerData lastestServerEntry = mc.getCurrentServer();
                new ClientboundDisconnectPacket(Component.literal("Self kick")).handle(mc.getConnection());
                ConnectScreen.startConnecting(new TitleScreen(), mc, ServerAddress.parseString(lastestServerEntry.ip), lastestServerEntry, false, null);
            }
        });
    }

    @Override
    public String[] getAutocorrect(int count, List<String> seperated) {
        return null;
    }
}
