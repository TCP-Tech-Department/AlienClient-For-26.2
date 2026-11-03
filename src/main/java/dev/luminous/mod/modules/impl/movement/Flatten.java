package dev.luminous.mod.modules.impl.movement;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.combat.CombatUtil;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.api.utils.world.BlockPosX;
import dev.luminous.api.utils.world.BlockUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.exploit.Blink;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.projectile.arrow.Arrow;
// 26.2: ExperienceBottleEntity → ThrownExperienceBottle（包路径也变了）
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.Direction;

public class Flatten extends Module {
    public static Flatten INSTANCE;
    private final BooleanSetting rotate =
            add(new BooleanSetting("Rotate", true));
    private final BooleanSetting checkMine =
            add(new BooleanSetting("DetectMining", true));
    private final BooleanSetting inventory =
            add(new BooleanSetting("InventorySwap", true));
    private final BooleanSetting usingPause =
            add(new BooleanSetting("UsingPause", true));
    private final BooleanSetting cover =
            add(new BooleanSetting("Cover", false));
    private final SliderSetting blocksPer =
            add(new SliderSetting("BlocksPer", 2, 1, 8));
    private final SliderSetting delay =
            add(new SliderSetting("Delay", 100, 0, 1000));
    private final Timer timer = new Timer();
    int progress = 0;

    public Flatten() {
        super("Flatten", Category.Movement);
        setChinese("填平脚下");
        INSTANCE = this;
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        progress = 0;
        if (inventory.getValue() && !EntityUtil.inInventory()) return;
        if (Blink.INSTANCE.isOn() && Blink.INSTANCE.pauseModule.getValue()) return;
        if (usingPause.getValue() && mc.player.isUsingItem()) {
            return;
        }
        if (!mc.player.onGround()) {
            return;
        }
        if (!timer.passed(delay.getValueInt())) return;
        int oldSlot = mc.player.getInventory().getSelectedSlot();
        int block;
        if ((block = getBlock()) == -1) {
            return;
        }
        if (!EntityUtil.isInsideBlock()) return;

        BlockPos pos1 = new BlockPosX(mc.player.getX() + 0.5, mc.player.getY() + 0.5, mc.player.getZ() + 0.5).below();
        BlockPos pos2 = new BlockPosX(mc.player.getX() - 0.5, mc.player.getY() + 0.5, mc.player.getZ() + 0.5).below();
        BlockPos pos3 = new BlockPosX(mc.player.getX() + 0.5, mc.player.getY() + 0.5, mc.player.getZ() - 0.5).below();
        BlockPos pos4 = new BlockPosX(mc.player.getX() - 0.5, mc.player.getY() + 0.5, mc.player.getZ() - 0.5).below();

        if (!canPlace(pos1) && !canPlace(pos2) && !canPlace(pos3) && !canPlace(pos4)) {
            return;
        }

        CombatUtil.attackCrystal(pos1, rotate.getValue(), usingPause.getValue());
        CombatUtil.attackCrystal(pos2, rotate.getValue(), usingPause.getValue());
        CombatUtil.attackCrystal(pos3, rotate.getValue(), usingPause.getValue());
        CombatUtil.attackCrystal(pos4, rotate.getValue(), usingPause.getValue());

        doSwap(block);
        tryPlaceObsidian(pos1, rotate.getValue());
        tryPlaceObsidian(pos2, rotate.getValue());
        tryPlaceObsidian(pos3, rotate.getValue());
        tryPlaceObsidian(pos4, rotate.getValue());

        if (inventory.getValue()) {
            doSwap(block);
            EntityUtil.syncInventory();
        } else {
            doSwap(oldSlot);
        }
    }

    private void tryPlaceObsidian(BlockPos pos, boolean rotate) {
        if (canPlace(pos)) {
            if (!(progress < blocksPer.getValue())) return;
            if (BlockUtil.allowAirPlace()) {
                BlockUtil.placedPos.add(pos);
                BlockUtil.airPlace(pos, rotate);
                timer.reset();
                progress++;
                return;
            }
            Direction side = BlockUtil.getPlaceSide(pos);
            if (side == null) return;
            progress++;
            BlockUtil.placedPos.add(pos);
            BlockUtil.clickBlock(pos.relative(side), side.getOpposite(), rotate);
            timer.reset();
        }
    }

    private void doSwap(int slot) {
        if (inventory.getValue()) {
            InventoryUtil.inventorySwap(slot, mc.player.getInventory().getSelectedSlot());
        } else {
            InventoryUtil.switchToSlot(slot);
        }
    }

    private boolean canPlace(BlockPos pos) {
        if (checkMine.getValue() && Alien.BREAK.isMining(pos)) {
            return false;
        }
        // 26.2: Level 没有 isAir，改查 BlockState
        if (cover.getValue() && mc.level.getBlockState(pos.above()).isAir()) {
            return false;
        }
        if (BlockUtil.getPlaceSide(pos) == null) {
            return false;
        }
        if (!BlockUtil.canReplace(pos)) {
            return false;
        }
        return !hasEntity(pos);
    }

    private boolean hasEntity(BlockPos pos) {
        for (Entity entity : BlockUtil.getEntities(new AABB(pos))) {
            if (entity == mc.player) continue;
            if (!entity.isAlive() || entity instanceof ItemEntity || entity instanceof ExperienceOrb || entity instanceof ThrownExperienceBottle || entity instanceof Arrow || entity instanceof EndCrystal)
                continue;
            return true;
        }
        return false;
    }

    private int getBlock() {
        if (inventory.getValue()) {
            return InventoryUtil.findBlockInventorySlot(Blocks.OBSIDIAN);
        } else {
            return InventoryUtil.findBlock(Blocks.OBSIDIAN);
        }
    }
}