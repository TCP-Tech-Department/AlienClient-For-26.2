package dev.luminous.api.events.impl;

import net.minecraft.world.level.block.state.BlockState;

public class BlockActivateEvent {
    private BlockActivateEvent() {
    }

    private static final BlockActivateEvent INSTANCE = new BlockActivateEvent();

    public BlockState blockState;

    public static BlockActivateEvent get(BlockState blockState) {
        INSTANCE.blockState = blockState;
        return INSTANCE;
    }
}