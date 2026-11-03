package dev.luminous.mod.modules.impl.player;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.asm.accessors.IPlayerMoveC2SPacket;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.exploit.BowBomb;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Items;

public class NoFall extends Module {
    private final EnumSetting<NoFallMode> mode = add(new EnumSetting<>("Mode", NoFallMode.Packet));
    private final SliderSetting distance =
            add(new SliderSetting("Distance", 3.0f, 0.0f, 8.0f, 0.1));

    public NoFall() {
        super("NoFall", "Prevents fall damage.", Category.Player);
        setChinese("没有摔落伤害");
    }

    @Override
    public String getInfo() {
        return mode.getValue().name();
    }

    public enum NoFallMode {
        Packet,
        Grim
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (nullCheck()) {
            return;
        }
        if (mode.is(NoFallMode.Grim) && checkFalling()) {
            // 26.2: PlayerMoveC2SPacket.Full → ServerboundMovePlayerPacket.PosRot（新增 horizontalCollision 参数）
            mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(mc.player.getX(), mc.player.getY() + 1.0e-9,
                    mc.player.getZ(), mc.player.getYRot(), mc.player.getXRot(), false, false));
            // 26.2: onLanding() → resetFallDistance()
            mc.player.resetFallDistance();
        }
    }

    private boolean checkFalling() {
        // 26.2: getSafeFallDistance() 移除，等价于 SAFE_FALL_DISTANCE 属性
        return mc.player.fallDistance > (float) mc.player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE)
                && !mc.player.onGround()
                && !mc.player.isFallFlying();
    }

    @EventListener
    public void onPacketSend(PacketEvent.Send event) {
        if (nullCheck()) {
            return;
        }
        // 26.2: getArmorItems() 移除，改为遍历四个护甲槽
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST,
                EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (mc.player.getItemBySlot(slot).getItem() == Items.ELYTRA) {
                return;
            }
        }
        if (!mode.is(NoFallMode.Packet)) {
            return;
        }
        if (event.getPacket() instanceof ServerboundMovePlayerPacket packet) {
            if (mc.player.fallDistance >= (float) this.distance.getValue() && !BowBomb.send) {
                ((IPlayerMoveC2SPacket) packet).setOnGround(true);
            }
        }
    }
}
