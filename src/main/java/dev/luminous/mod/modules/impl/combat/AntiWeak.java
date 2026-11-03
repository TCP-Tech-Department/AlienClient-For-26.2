package dev.luminous.mod.modules.impl.combat;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.eventbus.EventPriority;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.tags.ItemTags;


public class AntiWeak extends Module {
    private final EnumSetting<SwapMode> swapMode =
            add(new EnumSetting<>("SwapMode", SwapMode.Inventory));
    private final SliderSetting delay = add(new SliderSetting("Delay", 100, 0, 500).setSuffix("ms"));
    private final BooleanSetting onlyCrystal =
            add(new BooleanSetting("OnlyCrystal", true));
    private final Timer delayTimer = new Timer();
    boolean ignore = false;
    private net.minecraft.network.protocol.Packet<?> lastPacket = null;

    public AntiWeak() {
        super("AntiWeak", Category.Combat);
        setChinese("反虚弱");
    }

    @Override
    public String getInfo() {
        return swapMode.getValue().name();
    }

    @EventListener(priority = EventPriority.HIGHEST)
    public void onPacketSend(PacketEvent.Send event) {
        if (nullCheck()) return;
        if (event.isCancelled()) return;
        if (ignore) return;
        if (mc.player.getEffect(MobEffects.WEAKNESS) == null) return;
        if (mc.player.getMainHandItem().getItem().builtInRegistryHolder().is(ItemTags.SWORDS))
            return;
        if (!delayTimer.passedMs(delay.getValue())) return;
        if (event.getPacket() instanceof net.minecraft.network.protocol.game.ServerboundAttackPacket packet) {

            if (onlyCrystal.getValue() && !(Criticals.getEntity(packet) instanceof EndCrystal))
                return;
            lastPacket = packet;
            delayTimer.reset();
            ignore = true;
            doAnti();
            ignore = false;
            event.cancel();
        }
    }

    private void doAnti() {
        if (lastPacket == null) return;
        int strong;
        if (swapMode.getValue() != SwapMode.Inventory) {
            strong = InventoryUtil.findTag(ItemTags.SWORDS);
        } else {
            strong = InventoryUtil.findTagInventorySlot(ItemTags.SWORDS);
        }
        if (strong == -1) return;
        int old = mc.player.getInventory().getSelectedSlot();
        if (swapMode.getValue() != SwapMode.Inventory) {
            InventoryUtil.switchToSlot(strong);
        } else {
            InventoryUtil.inventorySwap(strong, mc.player.getInventory().getSelectedSlot());
        }
        mc.getConnection().send(lastPacket);
        if (swapMode.getValue() != SwapMode.Inventory) {
            if (swapMode.getValue() != SwapMode.Normal) InventoryUtil.switchToSlot(old);
        } else {
            InventoryUtil.inventorySwap(strong, mc.player.getInventory().getSelectedSlot());
            EntityUtil.syncInventory();
        }
    }

    public enum SwapMode {
        Normal, Silent, Inventory
    }
}
