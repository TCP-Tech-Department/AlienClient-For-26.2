package dev.luminous.mod.modules.impl.movement;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

public class VClip extends Module {
    private final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", Mode.Jump));

    public VClip() {
        super("VClip", Category.Movement);
        setChinese("纵向穿墙");
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        disable();
        switch (mode.getValue()) {
            case Teleport -> {
                // 26.2: setPosition → setPos
                mc.player.setPos(mc.player.getX(), mc.player.getY() + 3, mc.player.getZ());
                mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY(), mc.player.getZ(), true, false));
            }
            case Jump -> {
                // 26.2: PositionAndOnGround → Pos(x,y,z,onGround,horizontalCollision)
                mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY() + 0.4199999868869781, mc.player.getZ(), false, false));
                mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY() + 0.7531999805212017, mc.player.getZ(), false, false));
                mc.player.setPos(mc.player.getX(), mc.player.getY() + 1, mc.player.getZ());
                mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY(), mc.player.getZ(), true, false));
            }
            case Glitch -> {
                double posX = mc.player.getX();
                double posY = Math.round(mc.player.getY());
                double posZ = mc.player.getZ();
                boolean onGround = mc.player.onGround();

                mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(posX,
                        posY,
                        posZ,
                        onGround,
                        false));

                double halfY = 2 / 400.0;
                posY -= halfY;

                mc.player.setPos(posX, posY, posZ);
                mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(posX,
                        posY,
                        posZ,
                        onGround,
                        false));

                posY -= halfY * 300.0;
                mc.player.setPos(posX, posY, posZ);
                mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(posX,
                        posY,
                        posZ,
                        onGround,
                        false));
            }
        }
    }

    public enum Mode {
        Glitch,
        Teleport,
        Jump
    }
}
