package dev.luminous.api.events.impl;

import net.minecraft.world.level.block.Block;
import net.minecraft.core.BlockPos;

public class PlaceBlockEvent {
    private PlaceBlockEvent() {
    }

    private static final PlaceBlockEvent INSTANCE = new PlaceBlockEvent();

    public BlockPos blockPos;
    public Block block;

    public static PlaceBlockEvent get(BlockPos blockPos, Block block) {
        INSTANCE.blockPos = blockPos;
        INSTANCE.block = block;
        return INSTANCE;
    }
}