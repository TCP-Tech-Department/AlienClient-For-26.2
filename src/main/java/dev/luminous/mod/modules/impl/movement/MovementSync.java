package dev.luminous.mod.modules.impl.movement;

import dev.luminous.mod.modules.Module;

public class MovementSync extends Module {
    public static MovementSync INSTANCE;

    public MovementSync() {
        super("MovementSync", Category.Movement);
        setChinese("移动同步");
        INSTANCE = this;
    }
}
