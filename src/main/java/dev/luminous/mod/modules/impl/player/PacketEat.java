package dev.luminous.mod.modules.impl.player;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;

public class PacketEat extends Module {
    public static PacketEat INSTANCE;
    private final BooleanSetting deSync =
            add(new BooleanSetting("DeSync", false));
    private final BooleanSetting noRelease =
            add(new BooleanSetting("NoRelease", true));

    public PacketEat() {
        super("PacketEat", Category.Player);
        setChinese("发包进食");
        INSTANCE = this;
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        // 26.2: Item.components().contains() → ItemStack.has(DataComponents)；getActiveHand() → getUsedItemHand()
        if (deSync.getValue() && mc.player.isUsingItem() && mc.player.getActiveItem().has(DataComponents.FOOD)) {
            Module.sendSequencedPacket(id -> new ServerboundUseItemPacket(mc.player.getUsedItemHand(), id, Alien.ROTATION.getLastYaw(), Alien.ROTATION.getLastPitch()));
        }
    }

    @EventListener
    public void onPacket(PacketEvent.Send event) {
        if (noRelease.getValue() && event.getPacket() instanceof ServerboundPlayerActionPacket packet && packet.getAction() == ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM && mc.player.getActiveItem().has(DataComponents.FOOD)) {
            event.cancel();
        }
    }
}
