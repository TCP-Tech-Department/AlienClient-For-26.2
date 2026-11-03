package dev.luminous.api.events.impl;

import dev.luminous.api.events.Event;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

public class ClickBlockEvent extends Event {
    private ClickBlockEvent() {
    }

    private BlockPos pos;
    private Direction direction;

    public Direction getDirection() {
        return direction;
    }

    public BlockPos getPos() {
        return pos;
    }

    private static final ClickBlockEvent INSTANCE = new ClickBlockEvent();

    public static ClickBlockEvent get(BlockPos pos, Direction direction) {
        INSTANCE.pos = pos;
        INSTANCE.direction = direction;
        INSTANCE.setCancelled(false);
        return INSTANCE;
    }
}

