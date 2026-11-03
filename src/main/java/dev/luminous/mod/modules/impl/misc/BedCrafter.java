package dev.luminous.mod.modules.impl.misc;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.api.utils.world.BlockUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.world.level.block.Blocks;
// 26.2：Yarn RecipeResultCollection → Mojang RecipeCollection（客户端配方书）
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.item.BedItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class BedCrafter extends Module {
    public static BedCrafter INSTANCE;
    private final BooleanSetting rotate =
            add(new BooleanSetting("Rotate", false));
    private final SliderSetting range = add(new SliderSetting("Range", 5, 0, 8));
    private final BooleanSetting disable =
            add(new BooleanSetting("Disable", true));

    public BedCrafter() {
        super("BedCrafter", Category.Misc);
        setChinese("自动制作床");
        INSTANCE = this;
    }

    public static int getEmptySlots() {
        int emptySlots = 0;
        for (int i = 0; i < 36; ++i) {
            ItemStack itemStack = mc.player.getInventory().getItem(i);
            if (!itemStack.isEmpty()) continue;
            ++emptySlots;
        }
        return emptySlots;
    }

    /**
     * 26.2：Player#closeHandledScreen 已变为 protected 的 closeContainer()，
     * 这里手动发送关闭容器包并关闭当前界面（与旧语义一致）。
     */
    private static void closeHandledScreen() {
        if (mc.player == null) return;
        mc.getConnection().send(new ServerboundContainerClosePacket(mc.player.containerMenu.containerId));
        if (mc.gui.screen() != null) mc.gui.screen().onClose();
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (getEmptySlots() == 0) {
            if (mc.player.containerMenu instanceof CraftingMenu) {
                closeHandledScreen();
            }
            if (disable.getValue()) disable();
            return;
        }
        if (mc.player.containerMenu instanceof CraftingMenu) {
            boolean craft = false;
            // 26.2：配方书改为 RecipeDisplayEntry（RecipeDisplayId + 结果预览），
            // 旧的 RecipeEntry#getResult(RegistryManager) 已移除；结果物品用 resultItems(ContextMap) 解析
            ContextMap context = SlotDisplayContext.fromLevel(mc.level);
            for (RecipeCollection collection : mc.player.getRecipeBook().getCollections()) {
                for (RecipeDisplayEntry entry : collection.getRecipes()) {
                    if (entry.resultItems(context).stream().anyMatch(stack -> stack.getItem() instanceof BedItem)) {
                        for (int i = 0; i < getEmptySlots(); ++i) {
                            craft = true;
                            // 26.2：clickRecipe → handlePlaceRecipe(containerId, RecipeDisplayId, useMaxItems)
                            mc.gameMode.handlePlaceRecipe(mc.player.containerMenu.containerId, entry.id(), false);
                            mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, 0, 1, ContainerInput.QUICK_MOVE, mc.player);
                        }
                        break;
                    }
                }
            }
            if (!craft) {
                if (mc.player.containerMenu instanceof CraftingMenu) {
                    closeHandledScreen();
                }
                if (disable.getValue()) disable();
            }
        } else {
            doPlace();
        }
    }

    private void doPlace() {
        BlockPos bestPos = null;
        double distance = 100;
        boolean place = true;
        for (BlockPos pos : BlockUtil.getSphere(range.getValueFloat())) {
            if (mc.level.getBlockState(pos).getBlock() == Blocks.CRAFTING_TABLE && BlockUtil.getClickSideStrict(pos) != null) {
                place = false;
                bestPos = pos;
                break;
            }
            if (BlockUtil.canPlace(pos)) {
                if (bestPos == null || Mth.sqrt((float) mc.player.distanceToSqr(Vec3.atCenterOf(pos))) < distance) {
                    bestPos = pos;
                    distance = Mth.sqrt((float) mc.player.distanceToSqr(Vec3.atCenterOf(pos)));
                }
            }
        }

        if (bestPos != null) {
            if (!place) {
                BlockUtil.clickBlock(bestPos, BlockUtil.getClickSide(bestPos), rotate.getValue());
            } else {
                if (InventoryUtil.findItem(Items.CRAFTING_TABLE) == -1) return;
                int old = mc.player.getInventory().getSelectedSlot();
                InventoryUtil.switchToSlot(InventoryUtil.findItem(Items.CRAFTING_TABLE));
                BlockUtil.placeBlock(bestPos, rotate.getValue());
                InventoryUtil.switchToSlot(old);
            }
        }
    }
}
