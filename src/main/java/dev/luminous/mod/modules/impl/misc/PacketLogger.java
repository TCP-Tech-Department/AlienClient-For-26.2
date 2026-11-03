package dev.luminous.mod.modules.impl.misc;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.EntityVelocityUpdateEvent;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.core.impl.CommandManager;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.combat.Criticals;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.phys.BlockHitResult;

public class PacketLogger extends Module {

    public static PacketLogger INSTANCE;
    private final BooleanSetting moveFull = add(new BooleanSetting("MoveFull", true));
    private final BooleanSetting movePos = add(new BooleanSetting("MovePosition", true));
    private final BooleanSetting moveLook = add(new BooleanSetting("MoveLook", true));
    private final BooleanSetting moveGround = add(new BooleanSetting("MoveGround", true));
    private final BooleanSetting vehicleMove = add(new BooleanSetting("VehicleMove", true));
    private final BooleanSetting playerAction = add(new BooleanSetting("PlayerAction", true));
    private final BooleanSetting updateSlot = add(new BooleanSetting("UpdateSlot", true));
    private final BooleanSetting handSwing = add(new BooleanSetting("HandSwing", true));
    private final BooleanSetting pong = add(new BooleanSetting("Pong", true));
    private final BooleanSetting interactEntity = add(new BooleanSetting("InteractEntity", true));
    private final BooleanSetting interactBlock = add(new BooleanSetting("InteractBlock", true));
    private final BooleanSetting interactItem = add(new BooleanSetting("InteractItem", true));
    private final BooleanSetting closeScreen = add(new BooleanSetting("CloseScreen", true));
    private final BooleanSetting command = add(new BooleanSetting("ClientCommand", true));
    private final BooleanSetting status = add(new BooleanSetting("ClientStatus", true));
    private final BooleanSetting clickSlot = add(new BooleanSetting("ClickSlot", true));
    private final BooleanSetting pickInventory = add(new BooleanSetting("PickInventory", true));
    private final BooleanSetting teleportConfirm = add(new BooleanSetting("TeleportConfirm", true));

    private final BooleanSetting s2cVelocity = add(new BooleanSetting("S2cVelocity", true));

    public PacketLogger() {
        super("PacketLogger", Category.Misc);
        setChinese("数据包记录");
        INSTANCE = this;
    }

    private void logPacket(String msg, Object... args) {
        String s = String.format(msg, args);
        CommandManager.sendMessage(s);
    }

    @EventListener(priority = 999999)
    public void velocity(EntityVelocityUpdateEvent event) {
        if (s2cVelocity.getValue() && event.getEntity() == mc.player) {
            logPacket("S2C Velocity, x: %s, y: %s, z: %s, isExplosion: %s", event.getX(), event.getY(), event.getZ(), event.isExplosion());
        }
    }

    @EventListener(priority = -999999)
    public void onPacketSend(PacketEvent.Send event) {
        // 26.2：移动包扁平化为 ServerboundMovePlayerPacket 的 Pos / PosRot / Rot / StatusOnly 子类，
        // 判断字段改为 hasPosition()/hasRotation()，读取用 getX(fallback) 等
        if (event.getPacket() instanceof ServerboundMovePlayerPacket.PosRot packet && moveFull.getValue()) {
            StringBuilder builder = new StringBuilder();
            builder.append("PlayerMove Full - ");
            if (packet.hasPosition()) {
                builder.append("x: ").append(packet.getX(0.0)).append(", y: ").append(packet.getY(0.0)).append(", z: ").append(packet.getZ(0.0)).append(" ");
            }
            if (packet.hasRotation()) {
                builder.append("yaw: ").append(packet.getYRot(0.0f)).append(", pitch: ").append(packet.getXRot(0.0f)).append(" ");
            }
            builder.append(" onground: ").append(packet.isOnGround());
            logPacket(builder.toString());
        }
        if (event.getPacket() instanceof ServerboundMovePlayerPacket.Pos packet && movePos.getValue()) {
            StringBuilder builder = new StringBuilder();
            builder.append("PlayerMove PosGround - ");
            if (packet.hasPosition()) {
                builder.append("x: ").append(packet.getX(0.0)).append(", y: ").append(packet.getY(0.0)).append(", z: ").append(packet.getZ(0.0)).append(" ");
            }
            builder.append(" onground: ").append(packet.isOnGround());
            logPacket(builder.toString());
        }
        if (event.getPacket() instanceof ServerboundMovePlayerPacket.Rot packet && moveLook.getValue()) {
            StringBuilder builder = new StringBuilder();
            builder.append("PlayerMove LookGround - ");
            if (packet.hasRotation()) {
                builder.append("yaw: ").append(packet.getYRot(0.0f)).append(", pitch: ").append(packet.getXRot(0.0f)).append(" ");
            }
            builder.append(" onground: ").append(packet.isOnGround());
            logPacket(builder.toString());
        }
        if (event.getPacket() instanceof ServerboundMovePlayerPacket.StatusOnly packet && moveGround.getValue()) {
            String s = "PlayerMove Ground - onground: " + packet.isOnGround();
            logPacket(s);
        }
        if (event.getPacket() instanceof ServerboundMoveVehiclePacket packet && vehicleMove.getValue()) {
            logPacket("VehicleMove - x: %s, y: %s, z: %s, yaw: %s, pitch: %s", packet.position().x, packet.position().y, packet.position().z, packet.yRot(), packet.xRot());
        }
        if (event.getPacket() instanceof ServerboundPlayerActionPacket packet && playerAction.getValue()) {
            if (packet.getDirection() != null) {
                logPacket("PlayerAction - action: %s, direction: %s, pos: %s", packet.getAction().name(), packet.getDirection().name(), packet.getPos().toShortString());
            }
        }
        if (event.getPacket() instanceof ServerboundSetCarriedItemPacket packet && updateSlot.getValue()) {
            logPacket("UpdateSlot - slot: %d", packet.getSlot());
        }
        if (event.getPacket() instanceof ServerboundSwingPacket packet && handSwing.getValue()) {
            logPacket("HandSwing - hand: %s", packet.getHand().name());
        }
        if (event.getPacket() instanceof ServerboundPongPacket packet && pong.getValue()) {
            logPacket("Pong - %d", packet.getId());
        }
        if (event.getPacket() instanceof ServerboundAttackPacket packet && interactEntity.getValue()) {
            logPacket("InteractEntity - Entity: %s, id: %s", Criticals.getEntity(packet).getName().getString(), Criticals.getEntity(packet).getId());
        }
        if (event.getPacket() instanceof ServerboundInteractPacket packet && interactEntity.getValue()) {
            logPacket("InteractEntity - Entity: %s, id: %s", Criticals.getEntity(packet).getName().getString(), Criticals.getEntity(packet).getId());
        }
        if (event.getPacket() instanceof ServerboundUseItemOnPacket packet && interactBlock.getValue()) {
            BlockHitResult blockHitResult = packet.getHitResult();
            // 26.2：HitResult#blockPosition → BlockHitResult#getBlockPos，getSide → getDirection
            logPacket("InteractBlock - pos: %s, dir: %s, hand: %s", blockHitResult.getBlockPos().toShortString(), blockHitResult.getDirection().name(), packet.getHand().name());
        }
        if (event.getPacket() instanceof ServerboundUseItemPacket packet && interactItem.getValue()) {
            logPacket("InteractItem - hand: %s", packet.getHand().name());
        }
        if (event.getPacket() instanceof ServerboundContainerClosePacket packet && closeScreen.getValue()) {
            logPacket("CloseScreen - id: %s", packet.getContainerId());
        }
        if (event.getPacket() instanceof ServerboundPlayerCommandPacket packet && command.getValue()) {
            logPacket("ClientCommand - mode: %s", packet.getAction().name());
        }
        if (event.getPacket() instanceof ServerboundClientCommandPacket packet && status.getValue()) {
            logPacket("ClientStatus - mode: %s", packet.getAction().name());
        }
        if (event.getPacket() instanceof ServerboundContainerClickPacket packet && clickSlot.getValue()) {
            logPacket("ClickSlot - type: %s, slot: %s, button: %s, id: %s", packet.containerInput().name(), packet.slotNum(), packet.buttonNum(), packet.containerId());
        }
        // 26.2: PickFromInventoryC2SPacket 已删除，等价语义变为「容器 SWAP 到快捷栏槽(0-8)/副手槽 40」，
        // 因此 PickInventory 开关改为记录这类 SWAP 点击（见 InventoryUtil#swapWithHotbarSlot）。
        if (event.getPacket() instanceof ServerboundContainerClickPacket packet
                && packet.containerInput() == ContainerInput.SWAP && pickInventory.getValue()) {
            logPacket("PickFromInventory - slot: %s, to hotbar/offhand: %s", packet.slotNum(), packet.buttonNum());
        }
        if (event.getPacket() instanceof ServerboundAcceptTeleportationPacket packet && teleportConfirm.getValue()) {
            logPacket("TeleportConfirm - id: %s", packet.getId());
        }
    }
}
