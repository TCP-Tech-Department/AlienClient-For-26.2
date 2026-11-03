package dev.luminous.asm.mixins;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.luminous.Alien;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.render.*;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.FogType;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 26.2 {@code GameRenderer} 迁移。
 * <p>
 * 主要变化：
 * <ul>
 *   <li>{@code renderWorld(RenderTickCounter,...)} → {@code renderLevel(DeltaTracker)}；
 *       手部渲染 {@code renderHand(...)} → {@code renderItemInHand(CameraRenderState,float,Matrix4fc)}。</li>
 *   <li>{@code showFloatingItem} → {@code displayItemActivation}。</li>
 *   <li>{@code tiltViewWhenHurt} → {@code bobHurt(CameraRenderState,PoseStack)}。</li>
 *   <li>{@code getNightVisionStrength} → {@code nightVisionScale}。</li>
 *   <li>{@code getFov}/{@code getBasicProjectionMatrix}/{@code getCamera()} 均消失：
 *       FOV 与投影矩阵现在由 {@code Camera} 计算，{@code GameRenderer} 只从
 *       {@code CameraRenderState} 读取。这里改为在 {@code extractCamera} 的 TAIL 改写
 *       {@code cameraState.projectionMatrix / hudFov}，等价于旧的 getFov + getBasicProjectionMatrix。</li>
 *   <li>{@code findCrosshairTarget} 消失（已迁到 {@code LocalPlayer#raycastHitResult}，
 *       见 {@link MixinLocalPlayerCrosshair}）。</li>
 * </ul>
 */
@Mixin(GameRenderer.class)
public class MixinGameRenderer {
    @Shadow
    @Final
    private Minecraft minecraft;
    @Shadow
    @Final
    private GameRenderState gameRenderState;
    @Shadow
    @Final
    private Camera mainCamera;

    @Unique
    private static float alien$clampFov(double fov) {
        return (float) Math.min(Math.max(fov, 1.0), 177.0);
    }

    @Inject(method = "displayItemActivation", at = @At("HEAD"), cancellable = true)
    private void onShowFloatingItem(ItemStack floatingItem, CallbackInfo info) {
        if (floatingItem.getItem() == Items.TOTEM_OF_UNDYING && NoRender.INSTANCE.isOn() && NoRender.INSTANCE.totem.getValue()) {
            info.cancel();
        }
    }

    /**
     * 26.2: 反胃（nausea）不再走 {@code MathHelper.lerp} 的 renderWorld 重定向，
     * 而是 {@code renderLevel} 里 {@code player.getEffectBlendFactor(MobEffects.NAUSEA, ...)}；
     * 返回 0 即等于旧实现「lerp 结果恒为 0」。
     */
    @Redirect(
            method = "renderLevel",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getEffectBlendFactor(Lnet/minecraft/core/Holder;F)F"),
            require = 0
    )
    private float applyCameraTransformationsMathHelperLerpProxy(LivingEntity entity, Holder<MobEffect> effect, float partialTicks) {
        if (NoRender.INSTANCE.isOn() && NoRender.INSTANCE.nausea.getValue()) {
            return 0;
        }
        return entity.getEffectBlendFactor(effect, partialTicks);
    }

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void tiltViewWhenHurtHook(CameraRenderState cameraState, PoseStack matrices, CallbackInfo ci) {
        if (NoRender.INSTANCE.isOn() && NoRender.INSTANCE.hurtCam.getValue()) {
            ci.cancel();
        }
    }

    @Inject(method = "shouldRenderBlockOutline", at = @At("HEAD"), cancellable = true)
    public void hookOutline(CallbackInfoReturnable<Boolean> cir) {
        if (HighLight.INSTANCE.isOn()) {
            cir.setReturnValue(false);
        }
    }

    /**
     * 26.2: 世界几何改用 gizmos（世界坐标），提交时机必须在
     * {@code LevelRenderer#submitFeatures → finalizeGizmoCollection} 之前。
     * {@code GameRenderer#extract} 正好在相机更新之后、关卡 extract 之前，是最合适的等价点
     * （旧实现在 renderWorld 里、renderHand 之前，依赖 RenderSystem 的投影/模型视图矩阵，
     * 这两个矩阵 26.2 已删除并改由 projectPointToScreen 提供）。
     */
    @Inject(method = "extract", at = @At("HEAD"))
    void render3dHook(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        if (Module.nullCheck()) {
            return;
        }
        // 26.2: gizmo 使用世界坐标，不再需要相机旋转矩阵，给模块一个单位栈即可
        PoseStack matrixStack = new PoseStack();
        Alien.FPS.record();
        Alien.MODULE.render3D(matrixStack);
    }

    /**
     * 后处理入口：世界 + 手部画完之后，跑 {@code Alien.SHADER.renderShaders()}。
     *
     * <p><b>require = 1 是刻意的</b>：这是整条着色器链唯一的触发点。
     * 用 {@code require = 0} 时，一旦 26.2 改了 {@code renderLevel} 里的调用点，
     * 注入会**静默失败**，表现为「Shader 模块开了但画面毫无变化、也不报错」——很难查。
     * 已核实 {@code 26.2/net/minecraft/client/renderer/GameRenderer.java:569} 只有一处
     * {@code renderItemInHand(CameraRenderState, float, Matrix4fc)} 调用。
     */
    @Inject(
            method = "renderLevel",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;renderItemInHand(Lnet/minecraft/client/renderer/state/level/CameraRenderState;FLorg/joml/Matrix4fc;)V", shift = At.Shift.AFTER),
            require = 1
    )
    public void postRender3dHook(DeltaTracker deltaTracker, CallbackInfo ci) {
        Alien.SHADER.renderShaders();
    }

    @Inject(method = "extractCamera", at = @At("TAIL"))
    private void alien$applyFovAndAspect(DeltaTracker deltaTracker, float worldPartialTicks, float cameraEntityPartialTicks, CallbackInfo ci) {
        Fov fovModule = Fov.INSTANCE;
        boolean fovOn = fovModule.isOn();
        boolean zoomOn = Zoom.on;
        boolean aspectOn = AspectRatio.INSTANCE.isOn();
        CameraRenderState cameraState = this.gameRenderState.levelRenderState.cameraRenderState;
        if ((!fovOn && !zoomOn && !aspectOn) || cameraState.isPanoramicMode) {
            return;
        }

        // ---- 世界 FOV ----
        // 26.2: 相机最终 fov 已在 Camera#calculateFov 里算好（含疾跑/飞行倍率与死亡、液体修正）
        double worldFov = fovOn ? fovModule.fov.getValue() : this.mainCamera.getFov();
        if (zoomOn) {
            worldFov = alien$clampFov(worldFov - Zoom.INSTANCE.currentFov);
        }

        if (fovOn) {
            // 旧实现在 Fov 模块开启时直接返回设定值，随后仍会应用死亡/液体修正
            if (cameraState.entityRenderState.isDeadOrDying) {
                float duration = Math.min(cameraState.entityRenderState.deathTime, 20.0F);
                worldFov /= (1.0F - 500.0F / (duration + 500.0F)) * 2.0F + 1.0F;
            }
            if (cameraState.fogType == FogType.LAVA || cameraState.fogType == FogType.WATER) {
                worldFov *= Mth.lerp(this.minecraft.options.fovEffectScale().get().floatValue(), 1.0, 0.85714287F);
            }
        }

        // ---- 手部 FOV（旧 getFov(changingFov=false)）----
        if (fovOn) {
            cameraState.hudFov = (float) fovModule.itemFov.getValue();
        }

        // ---- 重算投影矩阵（旧的 getBasicProjectionMatrix + AspectRatio）----
        float aspect = aspectOn
                ? AspectRatio.INSTANCE.ratio.getValueFloat()
                : (float) this.minecraft.getWindow().getWidth() / this.minecraft.getWindow().getHeight();
        Matrix4f projection = cameraState.projectionMatrix;
        projection.identity();
        projection.perspective(
                (float) (worldFov * (Math.PI / 180.0)),
                aspect,
                0.05F,
                cameraState.depthFar,
                RenderSystem.getDevice().getDeviceInfo().isZZeroToOne()
        );
    }

    @Inject(method = "nightVisionScale", at = @At("HEAD"), cancellable = true)
    private static void getNightVisionStrengthHook(LivingEntity entity, float a, CallbackInfoReturnable<Float> cir) {
        MobEffectInstance statusEffectInstance = entity.getEffect(MobEffects.NIGHT_VISION);
        if (statusEffectInstance == null)
            cir.setReturnValue(1f);
    }

    /*
     * 26.2: 原注入点 GameRenderer#findCrosshairTarget(Entity, double, double, float) 已消失。
     * 准星拾取现在是 Minecraft#pick(float) → LocalPlayer#raycastHitResult(float, Entity)
     * （内部 static LocalPlayer#pick 负责 block/entity 各自的距离裁剪），
     * 都不在 GameRenderer 上，因此该 hook 迁到了独立的
     * {@link MixinLocalPlayerCrosshair}（目标 LocalPlayer）：
     *   - Freecam → 直接返回 InteractUtil.getRtxTarget(...)；
     *   - InteractTweaks.isActive → 包裹 Entity#pick（block 射线，支撑 bedrock→air）；
     *   - InteractTweaks.noEntityTrace → 丢掉 entity 命中（含 AttackRange 组件那条捷径）。
     */
}
