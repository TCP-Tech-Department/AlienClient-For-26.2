package dev.luminous.mod.modules.impl.misc;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.TotemEvent;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.core.impl.CommandManager;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.common.ClientboundDisconnectPacket;
import net.minecraft.network.chat.Component;

public class AutoLog extends Module {

    public static boolean loggedOut = false;
    private final BooleanSetting logOnEnable = add(new BooleanSetting("LogOnEnable", false));
    private final BooleanSetting onPop = add(new BooleanSetting("OnPop", true));
    private final BooleanSetting lowArmor = add(new BooleanSetting("LowArmor", true));
    private final BooleanSetting totemLess = add(new BooleanSetting("TotemLess", true).setParent());
    private final SliderSetting totems = add(new SliderSetting("Totems", 2, 0, 20, 1, totemLess::isOpen));
    private final BooleanSetting autoDisable = add(new BooleanSetting("AutoDisable", true));
    private final BooleanSetting showReason = add(new BooleanSetting("ShowReason", false));

    public AutoLog() {
        super("AutoLog", Category.Misc);
        setChinese("自动下线");
    }

    @Override
    public void onEnable() {
        if (logOnEnable.getValue()) {
            disconnect("Enabled");
        }
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (totemLess.getValue()) {
            int totem = InventoryUtil.getItemCount(Items.TOTEM_OF_UNDYING);
            if (totem <= totems.getValue()) {
                disconnect("You have too few totems (" + totem + ").");
                return;
            }
        }
        if (lowArmor.getValue()) {
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD}) {
                ItemStack armor = mc.player.getItemBySlot(slot);
                if (armor.isEmpty()) continue;
                int damage = EntityUtil.getDamagePercent(armor);
                if (damage < 5) {
                    disconnect("Your armor has a durability of less than 5%.");
                    return;
                }
            }
        }
    }

    @EventListener
    public void onPop(TotemEvent event) {
        if (onPop.getValue() && event.getPlayer() == mc.player) {
            disconnect("You poped 1 totem!");
        }
    }

    @Override
    public void onLogout() {
        if (autoDisable.getValue())
            disable();
    }

    private void disconnect(String reason) {
        loggedOut = true;
        CommandManager.sendMessage("§4[AutoLog] " + reason);
        mc.getConnection().send(new ServerboundSetCarriedItemPacket(114514));
        if (showReason.getValue())
            mc.player.connection.handleDisconnect(new ClientboundDisconnectPacket(Component.literal("[AutoLog]" + reason)));
    }
}
