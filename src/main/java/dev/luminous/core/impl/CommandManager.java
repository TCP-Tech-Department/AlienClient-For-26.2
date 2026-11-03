package dev.luminous.core.impl;

import by.radioegor146.nativeobfuscator.Native;
import dev.luminous.Alien;
import dev.luminous.api.interfaces.IChatHudHook;
import dev.luminous.api.utils.Wrapper;
import dev.luminous.mod.commands.Command;
import dev.luminous.mod.commands.impl.*;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.client.ClientSetting;
import net.minecraft.network.chat.Component;

import java.util.HashMap;

public class CommandManager implements Wrapper {
    private final HashMap<String, Command> commands = new HashMap<>();

    public CommandManager() {
        init();
    }

    @Native
    public void init() {
        registerCommand(new AimCommand());
        registerCommand(new BindCommand());
        registerCommand(new BindsCommand());
        registerCommand(new CleanerCommand());
        registerCommand(new ClipCommand());
        // EsuCommand 已移除（外连 QQ API）
        registerCommand(new FakePlayerCommand());
        registerCommand(new FriendCommand());
        registerCommand(new XrayCommand());
        registerCommand(new GamemodeCommand());
        registerCommand(new KitCommand());
        registerCommand(new LoadCommand());
        registerCommand(new PingCommand());
        registerCommand(new PrefixCommand());
        registerCommand(new RejoinCommand());
        registerCommand(new ReloadCommand());
        registerCommand(new SaveCommand());
        registerCommand(new TeleportCommand());
        registerCommand(new TCommand());
        registerCommand(new ToggleCommand());
        registerCommand(new TradeCommand());
        registerCommand(new WatermarkCommand());
        registerCommand(new PeekCommand());
        registerCommand(new GcCommand());
    }

    public static void sendMessage(String message) {
        mc.execute(() -> {
            if (Module.nullCheck()) return;
            if (ClientSetting.INSTANCE.messageStyle.getValue() == ClientSetting.Style.Earth) {
                // 26.2: ChatComponent.addMessage(Component) → addClientSystemMessage(Component)
                mc.gui.hud.getChat().addClientSystemMessage(Component.literal(message));
                return;
            }
            if (ClientSetting.INSTANCE.messageStyle.getValue() == ClientSetting.Style.Moon) {
                mc.gui.hud.getChat().addClientSystemMessage(Component.literal("§f[§b" + ClientSetting.INSTANCE.hackName.getValue() + "§f] " + message));
                return;
            }
            ((IChatHudHook) mc.gui.hud.getChat()).alienClient$addMessage(Component.literal(ClientSetting.INSTANCE.hackName.getValue() + "§f " + message));
        });
    }

    public static void sendMessageId(String message, int id) {
        mc.execute(() -> {
            if (Module.nullCheck()) return;
            if (ClientSetting.INSTANCE.messageStyle.getValue() == ClientSetting.Style.Earth) {
                ((IChatHudHook) mc.gui.hud.getChat()).alienClient$addMessage(Component.literal(message), id);
                return;
            }
            if (ClientSetting.INSTANCE.messageStyle.getValue() == ClientSetting.Style.Moon) {
                ((IChatHudHook) mc.gui.hud.getChat()).alienClient$addMessage(Component.literal("§f[§b" + ClientSetting.INSTANCE.hackName.getValue() + "§f] " + message), id);
                return;
            }
            ((IChatHudHook) mc.gui.hud.getChat()).alienClient$addMessage(Component.literal(ClientSetting.INSTANCE.hackName.getValue() + "§f " + message), id);
        });
    }

    public static void sendChatMessageWidthIdNoSync(String message, int id) {
        mc.execute(() -> {
            if (Module.nullCheck()) return;
            ((IChatHudHook) mc.gui.hud.getChat()).alienClient$addMessageOutSync(Component.literal("§f" + message), id);
        });
    }

    private void registerCommand(Command command) {
        commands.put(command.getName(), command);
    }

    public Command getCommandBySyntax(String string) {
        return this.commands.get(string);
    }

    public HashMap<String, Command> getCommands() {
        return this.commands;
    }

    public void command(String[] commandIn) {
        Command command = commands.get(commandIn[0].substring(Alien.getPrefix().length()).toLowerCase());

        if (command == null) {
            sendMessage("§4Invalid Command!");
        } else {
            String[] parameterList = new String[commandIn.length - 1];
            System.arraycopy(commandIn, 1, parameterList, 0, commandIn.length - 1);
            if (parameterList.length == 1 && parameterList[0].equals("help")) {
                command.sendUsage();
                return;
            }
            command.runCommand(parameterList);
        }
    }
}