package dev.luminous.mod.modules.impl.player;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.api.utils.player.MovementUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.movement.ElytraFly;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.InteractionHand;

import java.util.HashMap;
import java.util.Map;

public class AutoArmor extends Module {
    public static AutoArmor INSTANCE;
    private final EnumSetting<EnchantPriority> head = add(new EnumSetting<>("Head", EnchantPriority.Protection));
    private final EnumSetting<EnchantPriority> body = add(new EnumSetting<>("Body", EnchantPriority.Protection));
    private final EnumSetting<EnchantPriority> tights = add(new EnumSetting<>("Tights", EnchantPriority.Protection));
    private final EnumSetting<EnchantPriority> feet = add(new EnumSetting<>("Feet", EnchantPriority.Protection));
    private final BooleanSetting ignoreCurse = add(new BooleanSetting("IgnoreCurse", true));
    private final BooleanSetting noMove =
            add(new BooleanSetting("NoMove", false));
    private final SliderSetting delay =
            add(new SliderSetting("Delay", 3, 0, 10, 1));
    private final BooleanSetting autoElytra =
            add(new BooleanSetting("AutoElytra", true));
    private final EnumSetting<HotbarSwapMode> hotbarSwap = add(new EnumSetting<>("HotbarSwap", HotbarSwapMode.Swap));
    private final EnumSetting<InventorySwapMode> inventorySwap = add(new EnumSetting<>("InventorySwap", InventorySwapMode.ClickSlot));

    private int tickDelay = 0;

    public AutoArmor() {
        super("AutoArmor", Category.Player);
        setChinese("自动穿甲");
        INSTANCE = this;
    }

    /** 26.2: ArmorItem 已移除，护甲由 EQUIPPABLE 组件的槽位判定 */
    private static EquipmentSlot getArmorSlot(ItemStack stack) {
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable == null) return null;
        EquipmentSlot slot = equippable.slot();
        return switch (slot) {
            case HEAD, CHEST, LEGS, FEET -> slot;
            default -> null;
        };
    }

    /** 26.2: ElytraItem 已移除，鞘翅由 GLIDER 组件判定 */
    private static boolean isElytra(ItemStack stack) {
        return stack.has(DataComponents.GLIDER);
    }

    /** 等价 1.21.1 的 ElytraItem.isUsable（未损坏到只剩 1 点耐久） */
    private static boolean isElytraUsable(ItemStack stack) {
        return isElytra(stack) && !stack.nextDamageWillBreak();
    }

    /** 26.2: ArmorItem#getProtection() 已移除，改为读取 ARMOR 属性修饰符 */
    private static int getArmorDefense(ItemStack stack, EquipmentSlot slot) {
        if (slot == null || slot == EquipmentSlot.BODY) return 0;
        ItemAttributeModifiers modifiers = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        return (int) Math.round(modifiers.compute(Attributes.ARMOR, 0.0, slot));
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (!EntityUtil.inInventory()) {
            return;
        }

        // 26.2: playerScreenHandler → inventoryMenu
        if (mc.player.inventoryMenu != mc.player.containerMenu)
            return;

        if (MovementUtil.isMoving() && noMove.getValue()) return;

        if (tickDelay > 0) {
            tickDelay--;
            return;
        }

        tickDelay = delay.getValueInt();

        Map<EquipmentSlot, int[]> armorMap = new HashMap<>(4);
        armorMap.put(EquipmentSlot.FEET, new int[]{36, getProtection(mc.player.getInventory().getItem(36)), -1, -1});
        armorMap.put(EquipmentSlot.LEGS, new int[]{37, getProtection(mc.player.getInventory().getItem(37)), -1, -1});
        armorMap.put(EquipmentSlot.CHEST, new int[]{38, getProtection(mc.player.getInventory().getItem(38)), -1, -1});
        armorMap.put(EquipmentSlot.HEAD, new int[]{39, getProtection(mc.player.getInventory().getItem(39)), -1, -1});
        for (int s = 0; s < 36; s++) {
            ItemStack stack = mc.player.getInventory().getItem(s);
            if (getArmorSlot(stack) == null && !isElytra(stack))
                continue;
            if (isElytra(stack)) {
                if (ElytraFly.INSTANCE.isOff() && autoElytra.getValue()) continue;
                if (ElytraFly.INSTANCE.isOn() && ElytraFly.INSTANCE.packet.getValue()) continue;
            }
            int protection = getProtection(stack);
            EquipmentSlot slot = isElytra(stack) ? EquipmentSlot.CHEST : getArmorSlot(stack);
            for (Map.Entry<EquipmentSlot, int[]> e : armorMap.entrySet()) {
                if (autoElytra.getValue() && (ElytraFly.INSTANCE.isOn()) && e.getKey() == EquipmentSlot.CHEST) {
                    if (!mc.player.getInventory().getItem(38).isEmpty() && isElytra(mc.player.getInventory().getItem(38)) && isElytraUsable(mc.player.getInventory().getItem(38))) {
                        continue;
                    }
                    if (e.getValue()[2] != -1 && !mc.player.getInventory().getItem(e.getValue()[2]).isEmpty() && isElytra(mc.player.getInventory().getItem(e.getValue()[2])) && isElytraUsable(mc.player.getInventory().getItem(e.getValue()[2]))) {
                        continue;
                    }
                    if (!stack.isEmpty() && isElytra(stack) && isElytraUsable(stack)) {
                        e.getValue()[2] = s;
                    }
                    continue;
                }
                if (protection > 0) {
                    if (e.getKey() == slot) {
                        if (protection > e.getValue()[1] && protection > e.getValue()[3]) {
                            e.getValue()[2] = s;
                            e.getValue()[3] = protection;
                        }
                    }
                }
            }
        }

        for (Map.Entry<EquipmentSlot, int[]> equipmentSlotEntry : armorMap.entrySet()) {
            if (equipmentSlotEntry.getValue()[2] != -1) {
                if (equipmentSlotEntry.getValue()[2] < 9) {
                    switch (hotbarSwap.getValue()) {
                        case Swap -> {
                            int armorSlot = 44 - equipmentSlotEntry.getValue()[0];
                            // 26.2: ContainerInput.SWAP，buttonNum = 目标 Inventory 索引（0..8 快捷栏）
                            InventoryUtil.swapWithHotbarSlot(armorSlot, equipmentSlotEntry.getValue()[2]);
                            EntityUtil.syncInventory();
                        }
                        case Switch -> {
                            int old = mc.player.getInventory().getSelectedSlot();
                            InventoryUtil.switchToSlot(equipmentSlotEntry.getValue()[2]);
                            sendSequencedPacket(id -> new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, id, Alien.ROTATION.getLastYaw(), Alien.ROTATION.getLastPitch()));
                            InventoryUtil.switchToSlot(old);
                        }
                    }
                } else if (mc.player.inventoryMenu == mc.player.containerMenu) {
                    int armorSlot = 44 - equipmentSlotEntry.getValue()[0];
                    int newArmorSlot = equipmentSlotEntry.getValue()[2];

                    switch (inventorySwap.getValue()) {
                        case Pick -> {
                            // 26.2: PickFromInventoryC2SPacket 已删除。原流程是
                            //   ① 把该槽换到当前选中的快捷栏槽（swapSlotWithHotbar）
                            //   ② 右键「使用」把手里/选中的护甲穿上
                            //   ③ 再换回去
                            // ①③ 的等价实现是 ContainerInput.SWAP 到当前选中的快捷栏槽（InventoryUtil.pickFromInventory）。
                            // 注：本分支只在 newArmorSlot >= 9 时进入，此时 Inventory 索引 == 容器槽位，无需换算。
                            InventoryUtil.pickFromInventory(newArmorSlot);
                            sendSequencedPacket(id -> new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, id, Alien.ROTATION.getLastYaw(), Alien.ROTATION.getLastPitch()));
                            InventoryUtil.pickFromInventory(newArmorSlot);
                        }
                        case ClickSlot -> {
                            mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, newArmorSlot, 0, ContainerInput.PICKUP, mc.player);
                            mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, armorSlot, 0, ContainerInput.PICKUP, mc.player);
                            if (equipmentSlotEntry.getValue()[1] != -1)
                                mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, newArmorSlot, 0, ContainerInput.PICKUP, mc.player);
                        }
                    }

                    EntityUtil.syncInventory();
                }
                return;
            }
        }
    }

    private int getProtection(ItemStack is) {
        if (getArmorSlot(is) != null || isElytra(is)) {
            int prot = 0;
            // 原实现：鞘翅按 BODY 槽的分组设置计算，护甲按自身槽位
            EquipmentSlot slot = isElytra(is) ? EquipmentSlot.BODY : getArmorSlot(is);

            if (isElytra(is)) {
                if (!isElytraUsable(is))
                    return 0;
                prot = 1;
            }

            int blastMultiplier = 1;
            int protectionMultiplier = 1;

            switch (slot) {
                case HEAD -> {
                    if (head.is(EnchantPriority.Protection)) protectionMultiplier *= 2;
                    else blastMultiplier *= 2;
                }
                case BODY -> {
                    if (body.is(EnchantPriority.Protection)) protectionMultiplier *= 2;
                    else blastMultiplier *= 2;
                }
                case LEGS -> {
                    if (tights.is(EnchantPriority.Protection)) protectionMultiplier *= 2;
                    else blastMultiplier *= 2;
                }
                case FEET -> {
                    if (feet.is(EnchantPriority.Protection)) protectionMultiplier *= 2;
                    else blastMultiplier *= 2;
                }
                default -> {
                }
            }

            if (EnchantmentHelper.hasAnyEnchantments(is)) {
                Holder<Enchantment> protection = mc.level.registryAccess().getOrThrow(Enchantments.PROTECTION);
                Holder<Enchantment> blastProtection = mc.level.registryAccess().getOrThrow(Enchantments.BLAST_PROTECTION);
                Holder<Enchantment> bindingCurse = mc.level.registryAccess().getOrThrow(Enchantments.BINDING_CURSE);

                prot += EnchantmentHelper.getItemEnchantmentLevel(protection, is) * protectionMultiplier;
                prot += EnchantmentHelper.getItemEnchantmentLevel(blastProtection, is) * blastMultiplier;

                if (EnchantmentHelper.getItemEnchantmentLevel(bindingCurse, is) > 0 && ignoreCurse.getValue())
                    prot = -999;
            }
            return getArmorDefense(is, slot) + prot;
        } else if (!is.isEmpty()) {
            return 0;
        }

        return -1;
    }

    private enum EnchantPriority {
        Blast, Protection
    }

    public enum HotbarSwapMode {
        Swap,
        Switch,
    }

    public enum InventorySwapMode {
        ClickSlot,
        Pick,
    }
}
