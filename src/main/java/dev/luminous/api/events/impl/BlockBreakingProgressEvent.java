package dev.luminous.api.events.impl;

import net.minecraft.core.BlockPos;

public class BlockBreakingProgressEvent {
    private BlockBreakingProgressEvent() {
    }

    private static final BlockBreakingProgressEvent INSTANCE = new BlockBreakingProgressEvent();

    private BlockPos pos;
    private int breakerId;
    private int progress;

    public static BlockBreakingProgressEvent get(BlockPos pos, int breakerId, int progress) {
        INSTANCE.pos = pos;
        INSTANCE.breakerId = breakerId;
        INSTANCE.progress = progress;
        return INSTANCE;
    }

    public BlockPos getPosition() {
        return pos;
    }

    public int getBreakerId() {
        return breakerId;
    }

    public int getProgress() {
        return progress;
    }
}
