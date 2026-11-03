package dev.luminous.mod.modules.impl.render;

import dev.luminous.api.utils.math.Easing;
import dev.luminous.api.utils.math.FadeUtils;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.client.CameraType;
import com.mojang.blaze3d.vertex.PoseStack;

public class CameraClip extends Module {
    public static CameraClip INSTANCE;
    public final SliderSetting distance = add(new SliderSetting("Distance", 4f, 1f, 20f));
    public final SliderSetting animateTime = add(new SliderSetting("AnimationTime", 200, 0, 1000));
    private final EnumSetting<Easing> ease = add(new EnumSetting<>("Ease", Easing.CubicInOut));
    final FadeUtils animation = new FadeUtils(300);
    private final BooleanSetting noFront = add(new BooleanSetting("NoFront", false));
    boolean first = false;

    public CameraClip() {
        super("CameraClip", Category.Render);
        setChinese("摄像机穿墙");
        INSTANCE = this;
    }

    @Override
    public void onRender3D(PoseStack matrixStack) {
        // 26.2: Perspective → CameraType，Options.getPerspective/setPerspective → getCameraType/setCameraType
        if (mc.options.getCameraType() == CameraType.THIRD_PERSON_FRONT && noFront.getValue())
            mc.options.setCameraType(CameraType.FIRST_PERSON);
        animation.setLength(animateTime.getValueInt());
        if (mc.options.getCameraType() == CameraType.FIRST_PERSON) {
            if (!first) {
                first = true;
                animation.reset();
            }
        } else {
            if (first) {
                first = false;
                animation.reset();
            }
        }
    }

    public double getDistance() {
        double quad = mc.options.getCameraType() == CameraType.FIRST_PERSON ? 1 - animation.ease(ease.getValue()) : animation.ease(ease.getValue());
        return (distance.getValue() * quad - 1) + 1;
    }
}
