package dev.luminous.api.utils.player;

import dev.luminous.api.utils.Wrapper;
import dev.luminous.api.utils.world.BlockUtil;
import dev.luminous.mod.modules.impl.client.AntiCheat;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.HashMap;
import java.util.Map;

public class InventoryUtil implements Wrapper {

    static int lastSlot = -1;
    static int lastSelect = -1;

    /*
     * ===== 26.2 背包搬运原语（替代 1.21.1 的 PickFromInventoryC2SPacket）=====
     *
     * 1.21.1 的 PickFromInventoryC2SPacket(slot) 在 26.2 已删除。它在服务端执行的是
     * PlayerInventory#swapSlotWithHotbar(slot)（26.2 里对应 Inventory#pickSlot），
     * 语义 =「把背包该槽的物品与当前选中的快捷栏槽互换」。
     *
     * 26.2 唯一的等价入口是 ServerboundContainerClickPacket + ContainerInput.SWAP，
     * 见 26.2/net/minecraft/world/inventory/AbstractContainerMenu.java 的 doClick()：
     *     else if (containerInput == ContainerInput.SWAP && (buttonNum >= 0 && buttonNum < 9 || buttonNum == 40)) {
     *        ItemStack source = inventory.getItem(buttonNum);      // buttonNum 是 Inventory 索引
     *        Slot target = this.slots.get(slotIndex);              // slotIndex 是容器槽位
     * 即：slotNum = 容器槽位，buttonNum = 目标 Inventory 索引（0..8 快捷栏 / 40 副手）。
     * Inventory.SLOT_OFFHAND == 40 已确认（26.2/net/minecraft/world/entity/player/Inventory.java:33）。
     *
     * 服务端处理见 ServerGamePacketListenerImpl#handleContainerClick → menu.clicked(...)。
     */

    /** Inventory 索引 → inventoryMenu 槽位（快捷栏 0..8 在界面里是 36..44，其余不变）。 */
    public static int toMenuSlot(int inventorySlot) {
        return inventorySlot < 9 ? inventorySlot + 36 : inventorySlot;
    }

    /**
     * 把容器槽位 menuSlot 的物品与 Inventory 索引 target 互换。
     *
     * @param menuSlot 容器槽位（inventoryMenu：9..35 主背包、36..44 快捷栏、45 副手）
     * @param target   Inventory 索引：0..8 快捷栏槽，40 副手槽
     */
    public static void swapWithHotbarSlot(int menuSlot, int target) {
        mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, menuSlot, target, ContainerInput.SWAP, mc.player);
    }

    /** 把容器槽位 menuSlot 的物品换到副手槽（Inventory 索引 40）。 */
    public static void swapToOffhand(int menuSlot) {
        swapWithHotbarSlot(menuSlot, 40);
    }

    /** 26.2 等价于旧 PickFromInventoryC2SPacket(menuSlot)：与该槽物品和「当前选中的快捷栏槽」互换。 */
    public static void pickFromInventory(int menuSlot) {
        swapWithHotbarSlot(menuSlot, mc.player.getInventory().getSelectedSlot());
    }

    public static void inventorySwap(int slot, int selectedSlot) {
        if (slot == lastSlot) {
            switchToSlot(lastSelect);
            lastSlot = -1;
            lastSelect = -1;
            return;
        }
        if (slot - 36 == selectedSlot) return;
        if (!EntityUtil.inInventory()) return;
        if (AntiCheat.INSTANCE.invSwapBypass.getValue()) {
            if (slot - 36 >= 0) {
                lastSlot = slot;
                lastSelect = selectedSlot;
                switchToSlot(slot - 36);
                return;
            }
            // 26.2: 旧 PickFromInventoryC2SPacket → 等价「换到当前选中的快捷栏槽」的容器 SWAP。
            pickFromInventory(slot);
        } else {
            swapWithHotbarSlot(slot, selectedSlot);
            mc.player.getInventory().setChanged();
        }
    }

    public static void switchToSlot(int slot) {
        mc.player.getInventory().setSelectedSlot(slot);
        mc.getConnection().send(new ServerboundSetCarriedItemPacket(slot));
    }

    public static int findTag(net.minecraft.tags.TagKey<Item> tag) {
        for (int i = 0; i < 9; ++i) {
            if (mc.player.getInventory().getItem(i).getItem().builtInRegistryHolder().is(tag)) return i;
        }
        return -1;
    }

    public static int findTagInventorySlot(net.minecraft.tags.TagKey<Item> tag) {
        if (AntiCheat.INSTANCE.priorHotbar.getValue()) {
            for (int i = 0; i < 36; ++i) {
                ItemStack stack = mc.player.getInventory().getItem(i);
                if (stack.getItem().builtInRegistryHolder().is(tag)) return i < 9 ? i + 36 : i;
            }
        } else {
            for (int i = 35; i >= 0; --i) {
                ItemStack stack = mc.player.getInventory().getItem(i);
                if (stack.getItem().builtInRegistryHolder().is(tag)) return i < 9 ? i + 36 : i;
            }
        }
        return -1;
    }

    public static int findItem(Item input) {
        for (int i = 0; i < 9; ++i) {
            Item item = mc.player.getInventory().getItem(i).getItem();
            if (Item.getId(item) != Item.getId(input)) continue;
            return i;
        }
        return -1;
    }

    public static int getFood() {
        for (int i = 0; i < 9; ++i) {
            if (mc.player.getInventory().getItem(i).has(DataComponents.FOOD)) return i;
        }
        return -1;
    }

    public static int getPotionCount(MobEffect targetEffect) {
        int count = 0;
        for (int i = 35; i >= 0; --i) {
            ItemStack itemStack = mc.player.getInventory().getItem(i);
            if (Item.getId(itemStack.getItem()) != Item.getId(Items.SPLASH_POTION)) continue;
            PotionContents potionContentsComponent = itemStack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
            for (MobEffectInstance effect : potionContentsComponent.getAllEffects()) {
                if (effect.getEffect().value() == targetEffect) {
                    count = count + itemStack.getCount();
                }
            }
        }
        return count;
    }

    public static int getItemCount(Class<?> clazz) {
        int count = 0;
        for (Map.Entry<Integer, ItemStack> entry : InventoryUtil.getInventoryAndHotbarSlots().entrySet()) {
            if (entry.getValue().getItem() instanceof BlockItem && clazz.isInstance(((BlockItem) entry.getValue().getItem()).getBlock())) {
                count = count + entry.getValue().getCount();
            }
        }
        return count;
    }

    public static int getItemCount(Item item) {
        int count = 0;
        for (Map.Entry<Integer, ItemStack> entry : InventoryUtil.getInventoryAndHotbarSlots().entrySet()) {
            if (entry.getValue().getItem() != item) continue;
            count = count + entry.getValue().getCount();
        }
        if (mc.player.getOffhandItem().getItem() == item) {
            count += mc.player.getOffhandItem().getCount();
        }
        return count;
    }

    public static int findClass(Class<?> clazz) {
        for (int i = 0; i < 9; ++i) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack == ItemStack.EMPTY) continue;
            if (clazz.isInstance(stack.getItem())) {
                return i;
            }
            if (!(stack.getItem() instanceof BlockItem) || !clazz.isInstance(((BlockItem) stack.getItem()).getBlock()))
                continue;
            return i;
        }
        return -1;
    }

    public static int findClassInventorySlot(Class<?> clazz) {
        if (AntiCheat.INSTANCE.priorHotbar.getValue()) {
            for (int i = 0; i < 36; ++i) {
                ItemStack stack = mc.player.getInventory().getItem(i);
                if (stack == ItemStack.EMPTY) continue;
                if (clazz.isInstance(stack.getItem())) {
                    return i < 9 ? i + 36 : i;
                }
                if (!(stack.getItem() instanceof BlockItem) || !clazz.isInstance(((BlockItem) stack.getItem()).getBlock()))
                    continue;
                return i < 9 ? i + 36 : i;
            }
        } else {
            for (int i = 35; i >= 0; --i) {
                ItemStack stack = mc.player.getInventory().getItem(i);
                if (stack == ItemStack.EMPTY) continue;
                if (clazz.isInstance(stack.getItem())) {
                    return i < 9 ? i + 36 : i;
                }
                if (!(stack.getItem() instanceof BlockItem) || !clazz.isInstance(((BlockItem) stack.getItem()).getBlock()))
                    continue;
                return i < 9 ? i + 36 : i;
            }
        }
        return -1;
    }

    public static int findBlock(Block blockIn) {
        for (int i = 0; i < 9; ++i) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack == ItemStack.EMPTY || !(stack.getItem() instanceof BlockItem) || ((BlockItem) stack.getItem()).getBlock() != blockIn)
                continue;
            return i;
        }
        return -1;
    }

    public static int findUnBlock() {
        for (int i = 0; i < 9; ++i) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() instanceof BlockItem)
                continue;
            return i;
        }
        return -1;
    }

    public static int findBlock() {
        for (int i = 0; i < 9; ++i) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() instanceof BlockItem && !BlockUtil.isClickable(Block.byItem(stack.getItem())) && ((BlockItem) stack.getItem()).getBlock() != Blocks.COBWEB)
                return i;
        }
        return -1;
    }

    public static int findBlockInventorySlot(Block block) {
        return findItemInventorySlot(block.asItem());
    }

    public static int findItemInventorySlot(Item item) {
        if (AntiCheat.INSTANCE.priorHotbar.getValue()) {
            return findItemInventorySlotFromZero(item.asItem());
        } else {
            for (int i = 35; i >= 0; --i) {
                ItemStack stack = mc.player.getInventory().getItem(i);
                if (stack.getItem() == item) return i < 9 ? i + 36 : i;
            }
        }
        return -1;
    }

    public static int findItemInventorySlotFromZero(Item item) {
        for (int i = 0; i < 36; ++i) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() == item) return i < 9 ? i + 36 : i;
        }
        return -1;
    }

    public static Map<Integer, ItemStack> getInventoryAndHotbarSlots() {
        HashMap<Integer, ItemStack> fullInventorySlots = new HashMap<>();

        for (int current = 0; current <= 35; ++current) {
            fullInventorySlots.put(current, mc.player.getInventory().getItem(current));
        }

        return fullInventorySlots;
    }
}
