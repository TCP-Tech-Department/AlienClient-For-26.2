package dev.luminous.mod.modules.impl.player;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.eventbus.EventPriority;
import dev.luminous.api.events.impl.KeyboardInputEvent;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.events.impl.UpdateRotateEvent;
import dev.luminous.api.utils.math.MathUtil;
import dev.luminous.api.utils.path.BaritoneUtil;
import dev.luminous.api.utils.player.MovementUtil;
import dev.luminous.core.impl.RotationManager;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.luminous.asm.accessors.IClientInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;

public class Freecam extends Module {
    public static Freecam INSTANCE;
    private final SliderSetting speed = add(new SliderSetting("HSpeed", 1, 0.0, 3));
    private final SliderSetting hspeed = add(new SliderSetting("VSpeed", 0.42, 0.0, 3));
    private final BooleanSetting rotate =
            add(new BooleanSetting("Rotate", true));
    private float fakeYaw;
    private float fakePitch;
    private float prevFakeYaw;
    private float prevFakePitch;

    private double fakeX;
    private double fakeY;
    private double fakeZ;
    private double prevFakeX;
    private double prevFakeY;
    private double prevFakeZ;

    private float playerYaw;
    private float playerPitch;

    public Freecam() {
        super("Freecam", Category.Player);
        setChinese("自由相机");
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        if (nullCheck()) {
            disable();
            return;
        }
        playerYaw = getYaw();
        playerPitch = getPitch();

        fakePitch = getPitch();
        fakeYaw = getYaw();

        prevFakePitch = fakePitch;
        prevFakeYaw = fakeYaw;

        fakeX = mc.player.getX();
        fakeY = mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose());
        fakeZ = mc.player.getZ();

        prevFakeX = fakeX;
        prevFakeY = fakeY;
        prevFakeZ = fakeZ;
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (rotate.getValue() && mc.hitResult != null && mc.hitResult.getLocation() != null) {
            float[] angle = RotationManager.getRotation(mc.hitResult.getLocation());
            playerYaw = angle[0];
            playerPitch = angle[1];
        }
        if (BaritoneUtil.isPathing()) {
            double[] motion = MovementUtil.directionSpeed(speed.getValue());

            prevFakeX = fakeX;
            prevFakeY = fakeY;
            prevFakeZ = fakeZ;

            fakeX += motion[0];
            fakeZ += motion[1];

            // 26.2: jumpKey → keyJump / sneakKey → keyShift，isPressed → isDown
            if (mc.options.keyJump.isDown())
                fakeY += hspeed.getValue();

            if (mc.options.keyShift.isDown())
                fakeY -= hspeed.getValue();
        }
    }

    @EventListener(priority = EventPriority.HIGHEST)
    public void onRotate(UpdateRotateEvent event) {
        if (BaritoneUtil.isPathing()) return;
        if (event.isModified()) return;
        event.setYawWithoutSync(playerYaw);
        event.setPitchWithoutSync(playerPitch);
    }

    @Override
    public void onRender3D(PoseStack matrixStack) {
        prevFakeYaw = fakeYaw;
        prevFakePitch = fakePitch;

        fakeYaw = getYaw();
        fakePitch = getPitch();
    }

    private float getYaw() {
        return mc.player.getYRot();
    }

    private float getPitch() {
        return mc.player.getXRot();
    }

    @EventListener
    public void onKeyboardInput(KeyboardInputEvent event) {
        if (mc.player == null) return;

        double[] motion = MovementUtil.directionSpeed(speed.getValue());

        prevFakeX = fakeX;
        prevFakeY = fakeY;
        prevFakeZ = fakeZ;

        fakeX += motion[0];
        fakeZ += motion[1];

        if (mc.options.keyJump.isDown())
            fakeY += hspeed.getValue();

        if (mc.options.keyShift.isDown())
            fakeY -= hspeed.getValue();

        // 26.2: Input 变为 record，散装字段（movementForward/movementSideways/jumping/sneaking）已移除
        mc.player.input.keyPresses = Input.EMPTY;
        ((IClientInput) mc.player.input).setMoveVector(Vec2.ZERO);
    }

    public float getFakeYaw() {
        return MathUtil.interpolate(prevFakeYaw, fakeYaw, mc.getDeltaTracker().getGameTimeDeltaPartialTick(true));
    }

    public float getFakePitch() {
        return MathUtil.interpolate(prevFakePitch, fakePitch, mc.getDeltaTracker().getGameTimeDeltaPartialTick(true));
    }

    public double getFakeX() {
        return MathUtil.interpolate(prevFakeX, fakeX, mc.getDeltaTracker().getGameTimeDeltaPartialTick(true));
    }

    public double getFakeY() {
        return MathUtil.interpolate(prevFakeY, fakeY, mc.getDeltaTracker().getGameTimeDeltaPartialTick(true));
    }

    public double getFakeZ() {
        return MathUtil.interpolate(prevFakeZ, fakeZ, mc.getDeltaTracker().getGameTimeDeltaPartialTick(true));
    }
}
