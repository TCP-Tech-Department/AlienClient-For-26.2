package dev.luminous.mod.commands.impl;

import dev.luminous.core.impl.RotationManager;
import dev.luminous.mod.commands.Command;
import net.minecraft.world.phys.Vec3;

import java.text.DecimalFormat;
import java.util.List;

public class AimCommand extends Command {

    public AimCommand() {
        super("aim", "[x] [y] [z]");
    }

    @Override
    public void runCommand(String[] parameters) {
        if (parameters.length != 3) {
            sendUsage();
            return;
        }
        double x;
        double y;
        double z;
        if (isNumeric(parameters[0])) {
            x = Double.parseDouble(parameters[0]);
        } else if (parameters[0].startsWith("~")) {
            if (isNumeric(parameters[0].replace("~", ""))) {
                x = mc.player.getX() + Double.parseDouble(parameters[0].replace("~", ""));
            } else if (parameters[0].replace("~", "").isEmpty()) {
                x = mc.player.getX();
            } else {
                sendUsage();
                return;
            }
        } else {
            sendUsage();
            return;
        }

        if (isNumeric(parameters[1])) {
            y = Double.parseDouble(parameters[1]);
        } else if (parameters[1].startsWith("~")) {
            if (isNumeric(parameters[1].replace("~", ""))) {
                y = mc.player.getY() + Double.parseDouble(parameters[1].replace("~", ""));
            } else if (parameters[1].replace("~", "").isEmpty()) {
                y = mc.player.getY();
            } else {
                sendUsage();
                return;
            }
        } else {
            sendUsage();
            return;
        }

        if (isNumeric(parameters[2])) {
            z = Double.parseDouble(parameters[2]);
        } else if (parameters[2].startsWith("~")) {
            if (isNumeric(parameters[2].replace("~", ""))) {
                z = mc.player.getZ() + Double.parseDouble(parameters[2].replace("~", ""));
            } else if (parameters[2].replace("~", "").isEmpty()) {
                z = mc.player.getZ();
            } else {
                sendUsage();
                return;
            }
        } else {
            sendUsage();
            return;
        }
        float[] angle = RotationManager.getRotation(new Vec3(x, y, z));
        mc.player.setYRot(angle[0]);
        mc.player.setXRot(angle[1]);
        DecimalFormat df = new DecimalFormat("0.0");
        sendChatMessage("§fAim to §eX:" + df.format(x) + " Y:" + df.format(y) + " Z:" + df.format(z));
    }

    private boolean isNumeric(String str) {
        return str.matches("-?\\d+(\\.\\d+)?");
    }

    @Override
    public String[] getAutocorrect(int count, List<String> seperated) {
        return new String[]{"~ "};
    }
}
