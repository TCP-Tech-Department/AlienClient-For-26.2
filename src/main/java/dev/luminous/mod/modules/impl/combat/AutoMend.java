package dev.luminous.mod.modules.impl.combat;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.eventbus.EventPriority;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.events.impl.UpdateRotateEvent;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.enums.SwingSide;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.core.NonNullList;

public class AutoMend extends Module {

    public static AutoMend INSTANCE;
    public final BooleanSetting rotation =
            add(new BooleanSetting("Rotation", true).setParent());
    private final BooleanSetting instant =
            add(new BooleanSetting("Instant", false, rotation::isOpen));
    public final BooleanSetting onlyBroken =
            add(new BooleanSetting("OnlyBroken", true));
    private final SliderSetting delay =
            add(new SliderSetting("Delay", 3, 0, 5));
    private final BooleanSetting inventory =
            add(new BooleanSetting("InventorySwap", true));
    public final EnumSetting<SwingSide> interactSwing = add(new EnumSetting<>("InteractSwing", SwingSide.All));
    private final BooleanSetting usingPause =
            add(new BooleanSetting("UsingPause", true));
    private final BooleanSetting onlyGround =
            add(new BooleanSetting("OnlyGround", true));
    public final BooleanSetting autoDisable =
            add(new BooleanSetting("AutoDisable", true));
    private final Timer delayTimer = new Timer();
    boolean lookDown = false;
    int exp = 0;
    private boolean throwing = false;

    public AutoMend() {
        super("AutoMend", Category.Combat);
        setChinese("自动经验瓶");
        INSTANCE = this;
    }

    @Override
    public void onDisable() {
        throwing = false;
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        throwing = checkThrow();
        if (inventory.getValue() && !EntityUtil.inInventory()) return;
        if (lookDown && isThrow() && delayTimer.passed(delay.getValueInt() * 20L) && (!onlyGround.getValue() || mc.player.onGround())) {
            exp = InventoryUtil.getItemCount(Items.EXPERIENCE_BOTTLE) - 1;
            if (rotation.getValue() && instant.getValue()) {
                Alien.ROTATION.snapAt(Alien.ROTATION.rotationYaw, 88);
            }
            throwExp();
            if (rotation.getValue() && instant.getValue()) {
                Alien.ROTATION.snapBack();
            }
        }
        if (autoDisable.getValue()) {
            if (!isThrow()) {
                disable();
            }
        }
    }

    @Override
    public void onEnable() {
        lookDown = !rotation.getValue() || instant.getValue();
        if (nullCheck()) {
            disable();
            return;
        }
        exp = InventoryUtil.getItemCount(Items.EXPERIENCE_BOTTLE);
    }

    @Override
    public String getInfo() {
        return String.valueOf(exp);
    }

    public void throwExp() {
        int oldSlot = mc.player.getInventory().getSelectedSlot();
        int newSlot;
        if (inventory.getValue() && (newSlot = InventoryUtil.findItemInventorySlotFromZero(Items.EXPERIENCE_BOTTLE)) != -1) {
            InventoryUtil.inventorySwap(newSlot, mc.player.getInventory().getSelectedSlot());
            sendSequencedPacket(id -> new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, id, Alien.ROTATION.getLastYaw(), Alien.ROTATION.getLastPitch()));
            EntityUtil.swingHand(InteractionHand.MAIN_HAND, interactSwing.getValue());
            InventoryUtil.inventorySwap(newSlot, mc.player.getInventory().getSelectedSlot());
            EntityUtil.syncInventory();
            delayTimer.reset();
        } else if ((newSlot = InventoryUtil.findItem(Items.EXPERIENCE_BOTTLE)) != -1) {
            InventoryUtil.switchToSlot(newSlot);
            sendSequencedPacket(id -> new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, id, Alien.ROTATION.getLastYaw(), Alien.ROTATION.getLastPitch()));
            EntityUtil.swingHand(InteractionHand.MAIN_HAND, interactSwing.getValue());
            InventoryUtil.switchToSlot(oldSlot);
            delayTimer.reset();
        }
    }

    @EventListener(priority = EventPriority.LOWEST)
    public void RotateEvent(UpdateRotateEvent event) {
        if (!rotation.getValue() || instant.getValue()) return;
        if (isThrow()) {
            event.setPitch(88);
            lookDown = true;
        }
    }

    public boolean isThrow() {
        return throwing;
    }

    public boolean checkThrow() {
        if (isOff()) return false;
        if (mc.gui.screen() != null) return false;
        if (usingPause.getValue() && mc.player.isUsingItem()) {
            return false;
        }
        if (InventoryUtil.findItem(Items.EXPERIENCE_BOTTLE) == -1 && (!inventory.getValue() || InventoryUtil.findItemInventorySlotFromZero(Items.EXPERIENCE_BOTTLE) == -1))
            return false;
        if (onlyBroken.getValue()) {
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack armor = mc.player.getItemBySlot(slot);
                if (armor.isEmpty()) continue;
                if (EntityUtil.getDamagePercent(armor) >= 100) continue;
                if (EnchantmentHelper.getItemEnchantmentLevel(mc.player.registryAccess().getOrThrow(Enchantments.MENDING), armor) > 0) return true;
            }
        } else {
            return true;
        }
        return false;
    }
}
