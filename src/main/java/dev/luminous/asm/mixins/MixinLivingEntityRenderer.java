package dev.luminous.asm.mixins;

import dev.luminous.api.utils.math.MathUtil;
import dev.luminous.core.impl.RotationManager;
import dev.luminous.mod.modules.impl.client.ClientSetting;
import dev.luminous.mod.modules.impl.render.NoRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.awt.*;

import static dev.luminous.api.utils.Wrapper.mc;

/**
 * 26.2: 实体渲染拆成 extract + submit 两段。
 * <ul>
 *   <li>旧的 {@code render(T,float,float,PoseStack,VertexConsumerProvider,int)} 注入点消失，
 *       模型动画改由 {@code LivingEntityRenderState} 驱动，而该状态在
 *       {@code extractRenderState(T,S,float)} 里从实体读取。所以「临时改写实体旋转再还原」
 *       这招仍然成立，只是注入点从 render 换成 extractRenderState。</li>
 *   <li>旧的 {@code @ModifyArgs EntityModel#render(...)}（改第 5 个参数的颜色/透明度）消失，
 *       26.2 的模型颜色走 {@code LivingEntityRenderer#getModelTint(S)}，改为在那里改写返回值。</li>
 * </ul>
 */
@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRenderer {
    @Unique
    private LivingEntity lastEntity;
    @Unique
    private float originalYaw;
    @Unique
    private float originalHeadYaw;
    @Unique
    private float originalBodyYaw;
    @Unique
    private float originalPitch;

    @Unique
    private float originalPrevYaw;
    @Unique
    private float originalPrevHeadYaw;
    @Unique
    private float originalPrevBodyYaw;
    @Unique
    private float originalPrevPitch;

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    public void onRenderPre(LivingEntity livingEntity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
        if (Minecraft.getInstance().player != null && livingEntity == Minecraft.getInstance().player && ClientSetting.INSTANCE.rotations.getValue()) {
            originalYaw = livingEntity.getYRot();
            originalHeadYaw = livingEntity.yHeadRot;
            originalBodyYaw = livingEntity.yBodyRot;
            originalPitch = livingEntity.getXRot();
            originalPrevYaw = livingEntity.yRotO;
            originalPrevHeadYaw = livingEntity.yHeadRotO;
            originalPrevBodyYaw = livingEntity.yBodyRotO;
            originalPrevPitch = livingEntity.xRotO;

            livingEntity.setYRot(RotationManager.getRenderYawOffset());
            livingEntity.yHeadRot = RotationManager.getRotationYawHead();
            livingEntity.yBodyRot = RotationManager.getRenderYawOffset();
            livingEntity.setXRot(RotationManager.getRenderPitch());
            livingEntity.yRotO = RotationManager.getPrevRenderYawOffset();
            livingEntity.yHeadRotO = RotationManager.getPrevRotationYawHead();
            livingEntity.yBodyRotO = RotationManager.getPrevRenderYawOffset();
            livingEntity.xRotO = RotationManager.getPrevRenderPitch();
        }
        lastEntity = livingEntity;
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    public void onRenderPost(LivingEntity livingEntity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
        if (Minecraft.getInstance().player != null && livingEntity == Minecraft.getInstance().player && ClientSetting.INSTANCE.rotations.getValue()) {
            livingEntity.setYRot(originalYaw);
            livingEntity.yHeadRot = originalHeadYaw;
            livingEntity.yBodyRot = originalBodyYaw;
            livingEntity.setXRot(originalPitch);
            livingEntity.yRotO = originalPrevYaw;
            livingEntity.yHeadRotO = originalPrevHeadYaw;
            livingEntity.yBodyRotO = originalPrevBodyYaw;
            livingEntity.xRotO = originalPrevPitch;
        }
    }

    /**
     * 26.2: 模型颜色由 {@code getModelTint(S)} 决定（submit 时与基础色相乘），
     * 因此把旧的「改 EntityModel#render 的 color 参数」改为在这里返回带 alpha 的白色。
     * <p>
     * 26.2 的 render state 不再持有实体引用，无法直接比较 {@code entity != mc.player}，
     * 改用「同为 Player + 位置与本地玩家重合」来排除自己（与原语义等价：旧的
     * {@code lastEntity != mc.player}）。
     */
    @Inject(method = "getModelTint", at = @At("RETURN"), cancellable = true)
    protected void onGetModelTint(LivingEntityRenderState state, CallbackInfoReturnable<Integer> cir) {
        if (!NoRender.INSTANCE.isOn() || !NoRender.INSTANCE.antiPlayerCollision.getValue()) {
            return;
        }
        if (mc.player == null || state.entityType != EntityTypes.PLAYER || state.isInvisible) {
            return;
        }
        double distanceSq = mc.player.position().distanceToSqr(state.x, state.y, state.z);
        if (distanceSq < 1.0E-4) {
            // 就是本地玩家自己
            return;
        }
        float alpha = MathUtil.clamp((float) (distanceSq / 3f) + 0.2f, 0f, 1f);
        cir.setReturnValue(applyOpacity(0x26FFFFFF, alpha));
    }

    @Unique
    int applyOpacity(int color_int, float opacity) {
        opacity = Math.min(1, Math.max(0, opacity));
        Color color = new Color(color_int);
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), (int) (color.getAlpha() * opacity)).getRGB();
    }
}
