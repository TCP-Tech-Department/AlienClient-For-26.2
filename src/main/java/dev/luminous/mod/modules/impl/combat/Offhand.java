package dev.luminous.mod.modules.impl.combat;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.ClientTickEvent;
import dev.luminous.api.events.impl.TotemEvent;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.enums.Timing;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;

public class Offhand extends Module {
    private final EnumSetting<OffhandItem> item = add(new EnumSetting<>("Item", OffhandItem.Totem));
    private final BooleanSetting safe = add(new BooleanSetting("Safe", true).setParent());
    private final SliderSetting safeHealth = add(new SliderSetting("Health", 16.0f, 0.0f, 36.0f, 0.1, safe::isOpen));
    private final BooleanSetting lethalCrystal = add(new BooleanSetting("LethalCrystal", true, safe::isOpen));
    private final BooleanSetting gapSwitch = add(new BooleanSetting("GapSwitch", true).setParent());
    private final BooleanSetting always = add(new BooleanSetting("Always", false, gapSwitch::isOpen));
    private final BooleanSetting gapOnTotem = add(new BooleanSetting("Gap-Totem", false, gapSwitch::isOpen));
    private final BooleanSetting gapOnSword = add(new BooleanSetting("Gap-Sword", true, gapSwitch::isOpen));
    private final BooleanSetting gapOnPick = add(new BooleanSetting("Gap-Pickaxe", false, gapSwitch::isOpen));
    private final BooleanSetting mainHandTotem = add(new BooleanSetting("MainHandTotem", false).setParent());
    private final SliderSetting slot = add(new SliderSetting("Slot", 1, 1, 9, 1, mainHandTotem::isOpen));
    private final BooleanSetting forceUpdate = add(new BooleanSetting("ForceUpdate", false, mainHandTotem::isOpen));
    private final BooleanSetting withOffhand = add(new BooleanSetting("WithOffhand", false, mainHandTotem::isOpen));
    private final EnumSetting<SwapMode> swapMode = add(new EnumSetting<>("SwapMode", SwapMode.OffhandSwap));
    private final SliderSetting delay = add(new SliderSetting("Delay", 50.0, 0.0, 500.0, 1));
    private final EnumSetting<Timing> timing =
            add(new EnumSetting<>("Timing", Timing.All));
    private final Timer timer = new Timer();

    public Offhand() {
        super("Offhand", Category.Combat);
        setChinese("副手物品");
    }

    @EventListener
    public void totem(TotemEvent event) {
        if (event.getPlayer() == mc.player) {
            if (mc.player.getMainHandItem().getItem() == Items.TOTEM_OF_UNDYING) {
                mc.player.getInventory().removeFromSelected(false);
            } else if (mc.player.getOffhandItem().getItem() == Items.TOTEM_OF_UNDYING) {
                mc.player.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            }
        }
    }

    private boolean lethalCrystal() {
        if (!lethalCrystal.getValue()) {
            return false;
        }
        for (final Entity entity : Alien.THREAD.getEntities()) {
            if (entity instanceof EndCrystal && mc.player.distanceTo(entity) <= 12.0f && AutoCrystal.INSTANCE.calculateDamage(new Vec3(entity.getX(), entity.getY(), entity.getZ()), mc.player, mc.player) >= EntityUtil.getHealth(mc.player)) {
                return true;
            }
        }
        return false;
    }

    @EventListener
    public void onTick(ClientTickEvent event) {
        if (nullCheck()) return;
        if (timing.is(Timing.Pre) && event.isPost() || timing.is(Timing.Post) && event.isPre()) return;

        if (!timer.passed(delay.getValueInt())) {
            return;
        }
        if (!EntityUtil.inInventory()) {
            return;
        }
        boolean switchMainHandTotem = mc.player.getOffhandItem().getItem() != Items.TOTEM_OF_UNDYING || withOffhand.getValue();
        boolean unsafe = EntityUtil.getHealth(mc.player) < safeHealth.getValue() || lethalCrystal();
        if (mainHandTotem.getValue()) {
            int totemSlot = InventoryUtil.findItemInventorySlot(Items.TOTEM_OF_UNDYING);
            if (totemSlot != -1 && mc.player.getInventory().getItem(slot.getValueInt() - 1).getItem() != Items.TOTEM_OF_UNDYING) {
                switch (swapMode.getValue()) {
                    case ClickSlot -> {
                        mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, totemSlot, 0, ContainerInput.PICKUP, mc.player);
                        mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, slot.getValueInt() - 1 + 36, 0, ContainerInput.PICKUP, mc.player);
                        mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, totemSlot, 0, ContainerInput.PICKUP, mc.player);
                        EntityUtil.syncInventory();
                    }
                    case OffhandSwap -> {
                        InventoryUtil.swapWithHotbarSlot(totemSlot, slot.getValueInt() - 1);
                        EntityUtil.syncInventory();
                    }
                    case Pick -> {
                        int old = mc.player.getInventory().getSelectedSlot();
                        InventoryUtil.switchToSlot(slot.getValueInt() - 1);
                        // 26.2: PickFromInventoryC2SPacket 已删除。它的服务端语义是
                        // PlayerInventory#swapSlotWithHotbar(slot)，即「把该槽与当前选中的快捷栏槽互换」，
                        // 26.2 等价实现是 ContainerInput.SWAP 到当前选中的快捷栏槽。
                        InventoryUtil.pickFromInventory(totemSlot);
                        InventoryUtil.switchToSlot(old);
                    }
                }
                if (switchMainHandTotem) {
                    if (!safe.getValue() || unsafe) {
                        if (slot.getValueInt() - 1 != mc.player.getInventory().getSelectedSlot() || forceUpdate.getValue()) {
                            InventoryUtil.switchToSlot(slot.getValueInt() - 1);
                        }
                    }
                }
                timer.reset();
                return;
            }
        }
        if (safe.getValue()) {
            if (unsafe) {
                if (mainHandTotem.getValue() && switchMainHandTotem) {
                    int hotBarSlot = InventoryUtil.findItem(Items.TOTEM_OF_UNDYING);
                    if (hotBarSlot != -1) {
                        if (hotBarSlot != mc.player.getInventory().getSelectedSlot() || forceUpdate.getValue()) {
                            InventoryUtil.switchToSlot(hotBarSlot);
                        }
                    }
                } else {
                    swap(Items.TOTEM_OF_UNDYING);
                    timer.reset();
                    return;
                }
            }
        } else if (mainHandTotem.getValue() && (switchMainHandTotem)) {
            int hotBarSlot = InventoryUtil.findItem(Items.TOTEM_OF_UNDYING);
            if (hotBarSlot != -1) {
                if (hotBarSlot != mc.player.getInventory().getSelectedSlot() || forceUpdate.getValue()) {
                    InventoryUtil.switchToSlot(hotBarSlot);
                }
            }
        }
        if (((gapOnSword.getValue() && mc.player.getMainHandItem().getItem() .builtInRegistryHolder().is(ItemTags.SWORDS)) || always.getValue() && (mc.player.getMainHandItem().getItem() != Items.GOLDEN_APPLE && mc.player.getMainHandItem().getItem() != Items.ENCHANTED_GOLDEN_APPLE) || (gapOnPick.getValue() && mc.player.getMainHandItem().getItem() .builtInRegistryHolder().is(ItemTags.PICKAXES)) || (gapOnTotem.getValue() && mc.player.getMainHandItem().getItem() == Items.TOTEM_OF_UNDYING)) && mc.options.keyUse.isDown() && gapSwitch.getValue()) {
            swap(Items.GOLDEN_APPLE);
            timer.reset();
            return;
        }
        EnumSetting<OffhandItem> item = this.item;
        OffhandItem i = item.getValue();
        if (i == OffhandItem.Shield) {
            swap(Items.SHIELD);
            timer.reset();
            return;
        }
        if (i == OffhandItem.Chorus) {
            swap(Items.CHORUS_FRUIT);
            timer.reset();
            return;
        }
        if (i == OffhandItem.Crystal) {
            swap(Items.END_CRYSTAL);
            timer.reset();
            return;
        }
        if (i == OffhandItem.Totem) {
            swap(Items.TOTEM_OF_UNDYING);
            timer.reset();
            return;
        }
        if (i == OffhandItem.Gapple) {
            swap(Items.GOLDEN_APPLE);
            timer.reset();
        }
    }

    private void swap(Item item) {
        int itemSlot = item == Items.GOLDEN_APPLE ? getGAppleSlot() : findItemInventorySlot(item);
        if (itemSlot == -1) return;

        switch (swapMode.getValue()) {
            case OffhandSwap -> {
                InventoryUtil.swapToOffhand(itemSlot);
                EntityUtil.syncInventory();
            }
            case Pick -> {
                // 26.2: PickFromInventoryC2SPacket 已删除；本模式的目的就是把物品弄到副手，
                // 因此直接用 ContainerInput.SWAP 到 Inventory.SLOT_OFFHAND(40)，等价旧 OffhandSwap 的包。
                InventoryUtil.swapToOffhand(itemSlot);
                EntityUtil.syncInventory();
            }
            case ClickSlot -> {
                mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, itemSlot, 0, ContainerInput.PICKUP, mc.player);
                mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, 45, 0, ContainerInput.PICKUP, mc.player);
                mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, itemSlot, 0, ContainerInput.PICKUP, mc.player);
                EntityUtil.syncInventory();
            }
        }
    }

    private int getGAppleSlot() {
        return findItemInventorySlot(Items.ENCHANTED_GOLDEN_APPLE) != -1 ? findItemInventorySlot(Items.ENCHANTED_GOLDEN_APPLE) : findItemInventorySlot(Items.GOLDEN_APPLE);
    }

    @Override
    public String getInfo() {
        return item.getValue().name();
    }

    public int findItemInventorySlot(Item item) {
        if (mc.player.getOffhandItem().getItem() == Items.GOLDEN_APPLE && item == Items.GOLDEN_APPLE) {
            return -1;
        }
        if (mc.player.getOffhandItem().getItem() == Items.ENCHANTED_GOLDEN_APPLE && (item == Items.GOLDEN_APPLE || item == Items.ENCHANTED_GOLDEN_APPLE)) {
            return -1;
        }
        if (item == mc.player.getOffhandItem().getItem()) {
            return -1;
        }
        switch (swapMode.getValue()) {
            case Pick -> {
                for (int i = 9; i < mc.player.getInventory().getContainerSize() + 1; i++) {
                    ItemStack s = mc.player.getInventory().getItem(i);
                    if (s.getItem() != item) continue;
                    return i;
                }

                for (int i = 0; i < 9; i++) {
                    ItemStack s = mc.player.getInventory().getItem(i);
                    if (s.getItem() != item) continue;
                    return i;
                }
            }
            case OffhandSwap, ClickSlot -> {
                for (int i = 44; i >= 0; --i) {
                    ItemStack stack = mc.player.getInventory().getItem(i);
                    if (stack.getItem() == item) return i < 9 ? i + 36 : i;
                }
            }
        }
        return -1;
    }

    public enum OffhandItem {
        None,
        Totem,
        Crystal,
        Gapple,
        Shield,
        Chorus
    }

    public enum SwapMode {
        ClickSlot,
        OffhandSwap,
        Pick,
    }
}
