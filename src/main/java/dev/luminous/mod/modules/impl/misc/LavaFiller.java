package dev.luminous.mod.modules.impl.misc;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.api.utils.world.BlockUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.LavaFluid;
import net.minecraft.core.BlockPos;

public class LavaFiller extends Module {
    public final SliderSetting placeDelay =
            add(new SliderSetting("PlaceDelay", 50, 0, 500).setSuffix("ms"));
    private final BooleanSetting inventory =
            add(new BooleanSetting("InventorySwap", true));
    private final SliderSetting range =
            add(new SliderSetting("Range", 5, 0, 8, .1));
    private final SliderSetting blocksPer =
            add(new SliderSetting("BlocksPer", 1, 1, 8));
    private final BooleanSetting detectMining =
            add(new BooleanSetting("DetectMining", false));
    private final BooleanSetting usingPause =
            add(new BooleanSetting("UsingPause", true));
    private final BooleanSetting rotate =
            add(new BooleanSetting("Rotate", true));
    private final BooleanSetting packetPlace =
            add(new BooleanSetting("PacketPlace", true));
    private final Timer timer = new Timer();

    int progress = 0;

    public LavaFiller() {
        super("LavaFiller", Category.Misc);
        setChinese("自动填岩浆");
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (!timer.passed((long) placeDelay.getValue())) return;
        if (inventory.getValue() && !EntityUtil.inInventory()) return;
        progress = 0;

        if (getBlock() == -1) {
            return;
        }
        if (usingPause.getValue() && mc.player.isUsingItem()) {
            return;
        }
        for (BlockPos pos : BlockUtil.getSphere(range.getValueFloat())) {
            if (mc.level.getBlockState(pos).getBlock() == Blocks.LAVA) {
                // 26.2：Yarn FluidState#getFluid → Mojang FluidState#getType；LavaFluid.Still → LavaFluid.Source
                if (mc.level.getBlockState(pos).getFluidState().getType() instanceof LavaFluid.Source) {
                    tryPlaceBlock(pos);
                }
            }
        }
    }

    private void tryPlaceBlock(BlockPos pos) {
        if (pos == null) return;
        if (detectMining.getValue() && Alien.BREAK.isMining(pos)) return;
        if (!(progress < blocksPer.getValue())) return;
        int block = getBlock();
        if (block == -1) return;

        if (!BlockUtil.canPlace(pos, range.getValue(), false)) return;
        int old = mc.player.getInventory().getSelectedSlot();
        doSwap(block);
        BlockUtil.placeBlock(pos, rotate.getValue(), packetPlace.getValue());
        if (inventory.getValue()) {
            doSwap(block);
            EntityUtil.syncInventory();
        } else {
            doSwap(old);
        }
        progress++;
        timer.reset();
    }

    private void doSwap(int slot) {
        if (inventory.getValue()) {
            InventoryUtil.inventorySwap(slot, mc.player.getInventory().getSelectedSlot());
        } else {
            InventoryUtil.switchToSlot(slot);
        }
    }

    private int getBlock() {
        if (inventory.getValue()) {
            return InventoryUtil.findBlockInventorySlot(Blocks.OBSIDIAN);
        } else {
            return InventoryUtil.findBlock(Blocks.OBSIDIAN);
        }
    }
}