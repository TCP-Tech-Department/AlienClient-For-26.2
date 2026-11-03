package dev.luminous.mod.modules.impl.movement;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.eventbus.EventPriority;
import dev.luminous.api.events.impl.KeyboardInputEvent;
import dev.luminous.api.events.impl.MoveEvent;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.world.BlockUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.AABB;

public class SafeWalk extends Module {
    public static SafeWalk INSTANCE;
    private final BooleanSetting legit = add(new BooleanSetting("Legit", true));
    private final BooleanSetting onlyInsideBlock = add(new BooleanSetting("OnlyInsideBlock", false));

    public SafeWalk() {
        super("SafeWalk", Category.Movement);
        setChinese("边缘行走");
        INSTANCE = this;
    }

    @EventListener(priority = EventPriority.LOWEST)
    public void keyboard(KeyboardInputEvent event) {
        if (mc.player.onGround() && legit.getValue() && shouldSafeWalk()) {
            // 26.2: input.sneaking 字段已移除，改成 Input record 的 shift 位
            boolean sneak = this.isOffsetBBEmpty(0.3, -1.0, 0.0)
                    || this.isOffsetBBEmpty(0.0, -1.0, 0.3)
                    || this.isOffsetBBEmpty(0.3, -1.0, 0.3)
                    || this.isOffsetBBEmpty(-0.3, -1.0, 0.0)
                    || this.isOffsetBBEmpty(0.0, -1.0, -0.3)
                    || this.isOffsetBBEmpty(-0.3, -1.0, -0.3);
            if (sneak) {
                Input keys = mc.player.input.keyPresses;
                mc.player.input.keyPresses = new Input(keys.forward(), keys.backward(), keys.left(), keys.right(), keys.jump(), true, keys.sprint());
            }
        }
    }

    @EventListener(priority = EventPriority.LOW)
    public void onMove(MoveEvent event) {
        if (mc.player.onGround() && !legit.getValue() && shouldSafeWalk()) {
            double x = event.getX();
            double y = event.getY();
            double z = event.getZ();
            double increment = 0.05;
            while (x != 0.0 && this.isOffsetBBEmpty(x, -1.0, 0.0)) {
                if (x < increment && x >= -increment) {
                    x = 0.0;
                    continue;
                }
                if (x > 0.0) {
                    x -= increment;
                    continue;
                }
                x += increment;
            }
            while (z != 0.0 && this.isOffsetBBEmpty(0.0, -1.0, z)) {
                if (z < increment && z >= -increment) {
                    z = 0.0;
                    continue;
                }
                if (z > 0.0) {
                    z -= increment;
                    continue;
                }
                z += increment;
            }
            while (x != 0.0 && z != 0.0 && this.isOffsetBBEmpty(x, -1.0, z)) {
                x = x < increment && x >= -increment ? 0.0 : (x > 0.0 ? x - increment : x + increment);
                if (z < increment && z >= -increment) {
                    z = 0.0;
                    continue;
                }
                if (z > 0.0) {
                    z -= increment;
                    continue;
                }
                z += increment;
            }
            event.setX(x);
            event.setY(y);
            event.setZ(z);
        }
    }

    public boolean shouldSafeWalk() {
        return !onlyInsideBlock.getValue() || EntityUtil.isInsideBlock();
    }

    public boolean isOffsetBBEmpty(double offsetX, double offsetY, double offsetZ) {
        AABB playerBox = mc.player.getBoundingBox();
        AABB box = new AABB(playerBox.minX, playerBox.minY, playerBox.maxZ, playerBox.maxX, playerBox.minY + 0.5, playerBox.maxZ);
        return !BlockUtil.canCollide(mc.player, box.move(offsetX, offsetY, offsetZ));
    }
}
