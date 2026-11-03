package dev.luminous.mod.modules.impl.render;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.HeldItemRendererEvent;
import dev.luminous.asm.accessors.IHeldItemRenderer;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.world.InteractionHand;

public class ViewModel extends Module {
    public static ViewModel INSTANCE;
    public final BooleanSetting mainhandSwap = add(new BooleanSetting("MainhandSwap", true));
    public final BooleanSetting offhandSwap = add(new BooleanSetting("OffhandSwap", true));
    public final SliderSetting scaleMainX = add(new SliderSetting("ScaleMainX", 1f, 0.1f, 5f, 0.01));
    public final SliderSetting scaleMainY = add(new SliderSetting("ScaleMainY", 1f, 0.1f, 5f, 0.01));
    public final SliderSetting scaleMainZ = add(new SliderSetting("ScaleMainZ", 1f, 0.1f, 5f, 0.01));
    public final SliderSetting positionMainX = add(new SliderSetting("PositionMainX", 0f, -3.0f, 3f, 0.01));
    public final SliderSetting positionMainY = add(new SliderSetting("PositionMainY", 0f, -3.0f, 3f, 0.01));
    public final SliderSetting positionMainZ = add(new SliderSetting("PositionMainZ", 0f, -3.0f, 3f, 0.01));
    public final SliderSetting rotationMainX = add(new SliderSetting("RotationMainX", 0f, -180.0f, 180f, 0.01));
    public final SliderSetting rotationMainY = add(new SliderSetting("RotationMainY", 0f, -180.0f, 180f, 0.01));
    public final SliderSetting rotationMainZ = add(new SliderSetting("RotationMainZ", 0f, -180.0f, 180f, 0.01));
    public final SliderSetting scaleOffX = add(new SliderSetting("ScaleOffX", 1f, 0.1f, 5f, 0.01));
    public final SliderSetting scaleOffY = add(new SliderSetting("ScaleOffY", 1f, 0.1f, 5f, 0.01));
    public final SliderSetting scaleOffZ = add(new SliderSetting("ScaleOffZ", 1f, 0.1f, 5f, 0.01));
    public final SliderSetting positionOffX = add(new SliderSetting("PositionOffX", 0f, -3.0f, 3f, 0.01));
    public final SliderSetting positionOffY = add(new SliderSetting("PositionOffY", 0f, -3.0f, 3f, 0.01));
    public final SliderSetting positionOffZ = add(new SliderSetting("PositionOffZ", 0f, -3.0f, 3f, 0.01));
    public final SliderSetting rotationOffX = add(new SliderSetting("RotationOffX", 0f, -180.0f, 180f, 0.01));
    public final SliderSetting rotationOffY = add(new SliderSetting("RotationOffY", 0f, -180.0f, 180f, 0.01));
    public final SliderSetting rotationOffZ = add(new SliderSetting("RotationOffZ", 0f, -180.0f, 180f, 0.01));
    public final BooleanSetting slowAnimation = add(new BooleanSetting("SwingSpeed", true));
    public final SliderSetting slowAnimationVal = add(new SliderSetting("Value", 6, 1, 50));

    public ViewModel() {
        super("ViewModel", Category.Render);
        setChinese("手持模型");
        INSTANCE = this;
    }

    @Override
    public void onRender3D(PoseStack matrixStack) {
        // 26.2: EntityRenderDispatcher#getHeldItemRenderer → getItemInHandRenderer
        if (!mainhandSwap.getValue() && ((IHeldItemRenderer) mc.getEntityRenderDispatcher().getItemInHandRenderer()).getEquippedProgressMainHand() <= 1f) {
            ((IHeldItemRenderer) mc.getEntityRenderDispatcher().getItemInHandRenderer()).setEquippedProgressMainHand(1f);
            ((IHeldItemRenderer) mc.getEntityRenderDispatcher().getItemInHandRenderer()).setItemStackMainHand(mc.player.getMainHandItem());
        }

        if (!offhandSwap.getValue() && ((IHeldItemRenderer) mc.getEntityRenderDispatcher().getItemInHandRenderer()).getEquippedProgressOffHand() <= 1f) {
            ((IHeldItemRenderer) mc.getEntityRenderDispatcher().getItemInHandRenderer()).setEquippedProgressOffHand(1f);
            ((IHeldItemRenderer) mc.getEntityRenderDispatcher().getItemInHandRenderer()).setItemStackOffHand(mc.player.getOffhandItem());
        }
    }

    @EventListener
    private void onHeldItemRender(HeldItemRendererEvent event) {
        // 26.2: 事件里的变换矩阵是 event.getStack()（PoseStack），旧代码误用了 getItem()（ItemStack）；
        // RotationAxis.POSITIVE_X → com.mojang.math.Axis.XP，PoseStack#multiply → mulPose
        if (event.getHand() == InteractionHand.MAIN_HAND) {
            event.getStack().translate(positionMainX.getValueFloat(), positionMainY.getValueFloat(), positionMainZ.getValueFloat());
            event.getStack().scale(scaleMainX.getValueFloat(), scaleMainY.getValueFloat(), scaleMainZ.getValueFloat());
            event.getStack().mulPose(Axis.XP.rotationDegrees(rotationMainX.getValueFloat()));
            event.getStack().mulPose(Axis.YP.rotationDegrees(rotationMainY.getValueFloat()));
            event.getStack().mulPose(Axis.ZP.rotationDegrees(rotationMainZ.getValueFloat()));
        } else {
            event.getStack().translate(positionOffX.getValueFloat(), positionOffY.getValueFloat(), positionOffZ.getValueFloat());
            event.getStack().scale(scaleOffX.getValueFloat(), scaleOffY.getValueFloat(), scaleOffZ.getValueFloat());
            event.getStack().mulPose(Axis.XP.rotationDegrees(rotationOffX.getValueFloat()));
            event.getStack().mulPose(Axis.YP.rotationDegrees(rotationOffY.getValueFloat()));
            event.getStack().mulPose(Axis.ZP.rotationDegrees(rotationOffZ.getValueFloat()));
        }
    }
}
