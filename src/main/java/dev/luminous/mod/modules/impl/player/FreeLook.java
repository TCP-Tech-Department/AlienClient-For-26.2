package dev.luminous.mod.modules.impl.player;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.LookDirectionEvent;
import dev.luminous.api.utils.math.MathUtil;
import dev.luminous.mod.modules.Module;
import com.mojang.blaze3d.vertex.PoseStack;

public class FreeLook extends Module {
    public static FreeLook INSTANCE;

    public FreeLook() {
        super("FreeLook", Category.Player);
        setChinese("自由视角");
        INSTANCE = this;
    }

    private float fakeYaw;
    private float fakePitch;
    private float prevFakeYaw;
    private float prevFakePitch;

    @Override
    public void onEnable() {
        if (nullCheck()) {
            disable();
            return;
        }
        fakePitch = mc.player.getXRot();
        fakeYaw = mc.player.getYRot();

        prevFakePitch = fakePitch;
        prevFakeYaw = fakeYaw;
    }

    @EventListener
    public void onLookDirection(LookDirectionEvent event) {
        fakeYaw += (float) event.getCursorDeltaX() * 0.15f;
        fakePitch += (float) event.getCursorDeltaY() * 0.15f;
        event.cancel();
    }

    @Override
    public void onRender3D(PoseStack matrixStack) {
        prevFakeYaw = fakeYaw;
        prevFakePitch = fakePitch;
    }

    public float getFakeYaw() {
        return MathUtil.interpolate(prevFakeYaw, fakeYaw, mc.getDeltaTracker().getGameTimeDeltaPartialTick(true));
    }

    public float getFakePitch() {
        return MathUtil.interpolate(prevFakePitch, fakePitch, mc.getDeltaTracker().getGameTimeDeltaPartialTick(true));
    }
}
