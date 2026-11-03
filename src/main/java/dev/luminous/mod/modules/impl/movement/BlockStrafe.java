package dev.luminous.mod.modules.impl.movement;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.MoveEvent;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.combat.AutoAnchor;
import dev.luminous.mod.modules.settings.impl.SliderSetting;

public class BlockStrafe extends Module {
    public static BlockStrafe INSTANCE;
    private final SliderSetting speed =
            add(new SliderSetting("Speed", 10, 0, 20, 1).setSuffix("%"));
    private final SliderSetting aSpeed =
            add(new SliderSetting("AnchorSpeed", 3, 0, 20, 1).setSuffix("%"));

    public BlockStrafe() {
        super("BlockStrafe", Category.Movement);
        setChinese("方块灵活移动");
        INSTANCE = this;
    }

    @EventListener
    public void onMove(MoveEvent event) {
        if (!EntityUtil.isInsideBlock()) return;
        /*if (Alien.PLAYER.isInWeb(mc.player)) return;*/
        double speed = AutoAnchor.INSTANCE.currentPos == null ? this.speed.getValue() : aSpeed.getValue();
        double moveSpeed = 0.2873 / 100 * speed;
        // 26.2: movementForward / movementSideways 散装字段已移除，改读 ClientInput#getMoveVector
        double n = mc.player.input.getMoveVector().y;
        double n2 = mc.player.input.getMoveVector().x;
        double n3 = mc.player.getYRot();
        if (n == 0.0 && n2 == 0.0) {
            event.setX(0.0);
            event.setZ(0.0);
            return;
        } else if (n != 0.0 && n2 != 0.0) {
            n *= Math.sin(0.7853981633974483);
            n2 *= Math.cos(0.7853981633974483);
        }
        event.setX((n * moveSpeed * -Math.sin(Math.toRadians(n3)) + n2 * moveSpeed * Math.cos(Math.toRadians(n3))));
        event.setZ((n * moveSpeed * Math.cos(Math.toRadians(n3)) - n2 * moveSpeed * -Math.sin(Math.toRadians(n3))));
    }
}
