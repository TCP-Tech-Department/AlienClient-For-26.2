package dev.luminous.core.impl;

import dev.luminous.Alien;
import dev.luminous.api.utils.Wrapper;
import dev.luminous.api.utils.world.BlockUtil;
import dev.luminous.mod.modules.impl.combat.Breaker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

public class HoleManager implements Wrapper {
    public boolean isHole(BlockPos pos) {
        return isHole(pos, true, false, false);
    }

    public boolean isHole(BlockPos pos, boolean canStand, boolean checkTrap, boolean anyBlock) {
        int blockProgress = 0;
        for (Direction i : Direction.values()) {
            if (i == Direction.UP || i == Direction.DOWN) continue;
            if (anyBlock && !mc.level.getBlockState(pos.relative(i)).isAir() || Alien.HOLE.isHard(pos.relative(i)))
                blockProgress++;
        }
        return (!checkTrap || (mc.level.getBlockState(pos).isAir()
                && mc.level.getBlockState(pos.above()).isAir()
                && mc.level.getBlockState(pos.above(1)).isAir()
                && mc.level.getBlockState(pos.above(2)).isAir()
                && (mc.player.blockPosition().getY() - 1 <= pos.getY() || mc.level.getBlockState(pos.above(3)).isAir())
                && (mc.player.blockPosition().getY() - 2 <= pos.getY() || mc.level.getBlockState(pos.above(4)).isAir())))
                && blockProgress > 3
                && (!canStand || BlockUtil.canCollide(new AABB(pos.offset(0, -1, 0))));
    }

    public BlockPos getHole(float range, boolean doubleHole, boolean any, boolean up) {
        BlockPos bestPos = null;
        double bestDistance = range + 1;
        for (BlockPos pos : BlockUtil.getSphere(range, mc.player.position())) {
            if (pos.getX() != mc.player.blockPosition().getX() || pos.getZ() != mc.player.blockPosition().getZ()) {
                if (!up && pos.getY() + 1 > mc.player.getY()) continue;
            }
            if (Alien.HOLE.isHole(pos, true, true, any) || doubleHole && isDoubleHole(pos)) {
                if (pos.getY() - mc.player.blockPosition().getY() > 1) continue;
                double distance = Mth.sqrt((float) mc.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
                if (bestPos == null || distance < bestDistance) {
                    bestPos = pos;
                    bestDistance = distance;
                }
            }
        }
        return bestPos;
    }

    public boolean isDoubleHole(BlockPos pos) {
        Direction unHardFacing = is3Block(pos);
        if (unHardFacing != null) {
            pos = pos.relative(unHardFacing);
            unHardFacing = is3Block(pos);
            return unHardFacing != null;
        }
        return false;
    }

    public Direction is3Block(BlockPos pos) {
        if (!isHard(pos.below())) {
            return null;
        }
        if (!mc.level.getBlockState(pos).isAir() || !mc.level.getBlockState(pos.above()).isAir() || !mc.level.getBlockState(pos.above(2)).isAir()) {
            return null;
        }
        int progress = 0;
        Direction unHardFacing = null;
        for (Direction facing : Direction.values()) {
            if (facing == Direction.UP || facing == Direction.DOWN) continue;
            if (isHard(pos.relative(facing))) {
                progress++;
                continue;
            }
            int progress2 = 0;
            for (Direction facing2 : Direction.values()) {
                if (facing2 == Direction.DOWN || facing2 == facing.getOpposite()) {
                    continue;
                }
                if (isHard(pos.relative(facing).relative(facing2))) {
                    progress2++;
                }
            }
            if (progress2 == 4) {
                progress++;
                continue;
            }
            unHardFacing = facing;
        }
        if (progress == 3) {
            return unHardFacing;
        }
        return null;
    }

    public boolean isHard(BlockPos pos) {
        Block block = mc.level.getBlockState(pos).getBlock();
        return isHard(block);
    }

    public boolean isHard(Block block) {
        return block == Blocks.BEDROCK || Breaker.hard.contains(block);
    }
}
