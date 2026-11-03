package dev.luminous.api.utils.path;

import dev.luminous.api.utils.Wrapper;
import dev.luminous.mod.modules.impl.client.BaritoneModule;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.BlockPos;

public class BaritoneUtil implements Wrapper {
    public static boolean loaded;

    static {
        Package[] packages = Package.getPackages();
        for (Package pkg : packages) {
            if (pkg.getName().contains("baritone.api")) {
                loaded = true;
                break;
            }
        }
    }

    public static void gotoPos(BlockPos pos) {
        if (loaded) {
            BaritoneModule.gotoPos(pos);
        }
    }

    public static void forward() {
        if (loaded) {
            BaritoneModule.forward();
        }
    }

    public static void mine(Block block) {
        if (loaded) {
            BaritoneModule.mine(block);
        }
    }

    public static boolean isPathing() {
        if (loaded) {
            return BaritoneModule.isPathing();
        }
        return false;
    }

    public static void cancelEverything() {
        if (loaded) {
            BaritoneModule.cancelEverything();
        }
    }

    public static boolean isActive() {
        if (loaded) {
            return BaritoneModule.isActive();
        }
        return false;
    }
}
