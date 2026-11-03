package dev.luminous.asm.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.luminous.mod.modules.impl.render.Ambience;
import dev.luminous.mod.modules.impl.render.NoRender;
import dev.luminous.mod.modules.impl.render.Xray;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.fog.environment.BlindnessFogEnvironment;
import net.minecraft.client.renderer.fog.environment.FogEnvironment;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.awt.*;

/**
 * 26.2 雾效重写（已按 {@code 26.2/net/minecraft/client/renderer/fog/} 全包核实）。
 * <p>
 * 1.21.1 的 {@code BackgroundRenderer#render/applyFog} + {@code RenderSystem.setShaderFogColor/Start/End}
 * 全部消失。26.2 的唯一雾数据载体是 {@link FogData}：
 * <pre>
 * FogRenderer#setupFog(Camera, int renderDistanceInChunks, DeltaTracker, float darkenWorldAmount, ClientLevel)
 *     → 内部 computeFogColor(...) 写 fog.color，再由 FOG_ENVIRONMENTS 列表写各段距离
 *     → 返回值交给 FogRenderer#updateBuffer(FogData) 打包成 "Fog" UBO
 * </pre>
 * 因此 <b>{@code setupFog} 的 {@code RETURN} 就是 26.2 雾色/雾距离的最终计算点</b>：此处改 {@link FogData}
 * 即等价于旧的 {@code setShaderFogColor / setShaderFogStart / setShaderFogEnd}。
 * <p>
 * 注：{@code Ambience.dimensionColor} 的雾色覆写已由 {@code MixinDimensionEffects}
 * 挂在 {@code FogRenderer#computeFogColor} 的 TAIL（更靠前的同一计算链），这里不再重复写 color，
 * 只保留 {@code Ambience.fog} / {@code fogDistance} 与 {@code NoRender.fog} / Xray 的语义。
 * <p>
 * 旧 {@code getFogModifier(Entity, float)} 返回 {@code StatusEffectFogModifier} 的注入点也不存在了：
 * 26.2 改由 {@code FOG_ENVIRONMENTS}（Lava / PowderedSnow / Blindness / Darkness / Water / Atmospheric）
 * 的 {@code isApplicable(FogType, Entity)} 决定谁生效。所以 {@code NoRender.blindness}
 * 改为拦截 {@code isApplicable}：让 {@link BlindnessFogEnvironment} 永不生效，
 * 等价于旧实现「返回 null 表示没有失明雾修正」。
 */
@Mixin(FogRenderer.class)
public class MixinBackgroundRenderer {

    /**
     * 26.2: 原 {@code @Redirect} on {@code LivingEntity#hasStatusEffect} in {@code BackgroundRenderer#render}。
     * 26.2 里同一段夜视判定搬到了私有方法 {@code FogRenderer#computeFogColor}
     * （{@code livingEntity.hasEffect(MobEffects.NIGHT_VISION) && !livingEntity.hasEffect(MobEffects.DARKNESS)}），
     * 这里按 {@code ordinal = 0} 命中 NIGHT_VISION 那次调用。
     * <p>
     * 按 brief §3 使用 MixinExtras {@code @WrapOperation}（{@code @Redirect} 会与 fabric 自己的 redirect 冲突）。
     */
    @WrapOperation(method = "computeFogColor",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;hasEffect(Lnet/minecraft/core/Holder;)Z", ordinal = 0),
            require = 0)
    private boolean alien$nightVisionHook(LivingEntity instance, Holder<MobEffect> effect, Operation<Boolean> original) {
        if (Ambience.INSTANCE.isOn() && Ambience.INSTANCE.fullBright.getValue()) return true;
        return original.call(instance, effect);
    }

    /**
     * 26.2: {@code NoRender.blindness} 的新落点。
     * <p>
     * {@code setupFog} 与 {@code computeFogColor} 都遍历同一个 {@code FOG_ENVIRONMENTS} 列表并调用
     * {@code FogEnvironment#isApplicable(FogType, Entity)}；在这里让失明环境恒为 false，
     * 失明就不会再压近雾距离、也不会再把雾色压黑（{@code getModifiedDarkness} 同样不会被选中）。
     * 其它环境（水/岩浆/细雪/黑暗）不受影响。
     */
    @WrapOperation(method = {"setupFog", "computeFogColor"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/fog/environment/FogEnvironment;isApplicable(Lnet/minecraft/world/level/material/FogType;Lnet/minecraft/world/entity/Entity;)Z"),
            require = 0)
    private boolean alien$noBlindnessFog(FogEnvironment environment, FogType fogType, Entity entity, Operation<Boolean> original) {
        if (NoRender.INSTANCE.isOn() && NoRender.INSTANCE.blindness.getValue()
                && environment instanceof BlindnessFogEnvironment) {
            return false;
        }
        return original.call(environment, fogType, entity);
    }

    /**
     * 26.2: 雾色 / 雾距离的最终覆写点（{@code FogData} 就是旧 {@code RenderSystem.setShaderFog*} 的全部状态）。
     * <p>
     * 合并了 1.21.1 的三个注入点：
     * <ul>
     *   <li>{@code applyFog} TAIL → {@code Ambience.fog} / {@code Ambience.fogDistance}；
     *   <li>{@code applyFog} TAIL 的 {@code FOG_TERRAIN} 分支 → {@code NoRender.fog} / {@code Xray} 拉远雾；
     *   <li>{@code render} HEAD 的 {@code RenderSystem.clearColor} → 由 {@code MixinDimensionEffects} 处理。
     * </ul>
     * 顺序：先距离（NoRender/Xray 最远）再 Ambience 显式覆盖，最后才是颜色（Ambience.fog 优先于维度雾色，
     * 与 1.21.1 一致：{@code setShaderFogColor} 在 {@code applyFog} TAIL 执行，晚于维度雾色写入）。
     */
    @Inject(method = "setupFog", at = @At("RETURN"))
    private void alien$applyFogOverrides(Camera camera, int renderDistanceInChunks, DeltaTracker deltaTracker,
                                         float darkenWorldAmount, ClientLevel level,
                                         CallbackInfoReturnable<FogData> info) {
        FogData fog = info.getReturnValue();
        if (fog == null) return;

        // 旧实现只把 FOG_TERRAIN 拉远，水里/岩浆里不动；26.2 里「地形雾」等价于镜头不在任何流体中
        boolean terrainFog = camera.getFluidInCamera() == FogType.NONE;
        if (terrainFog && ((NoRender.INSTANCE.isOn() && NoRender.INSTANCE.fog.getValue()) || Xray.INSTANCE.isOn())) {
            float viewDistance = renderDistanceInChunks * 16f;
            fog.environmentalStart = viewDistance * 4f;
            fog.environmentalEnd = viewDistance * 4.25f;
        }

        if (Ambience.INSTANCE.isOn() && Ambience.INSTANCE.fogDistance.getValue()) {
            fog.environmentalStart = Ambience.INSTANCE.fogStart.getValueFloat();
            fog.environmentalEnd = Ambience.INSTANCE.fogEnd.getValueFloat();
        }

        if (Ambience.INSTANCE.isOn() && Ambience.INSTANCE.fog.booleanValue) {
            Color color = Ambience.INSTANCE.fog.getValue();
            fog.color.set(color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, color.getAlpha() / 255f);
        }
    }
}
