package dev.luminous.asm.mixins;

import dev.luminous.mod.modules.impl.render.Ambience;
import dev.luminous.mod.modules.impl.render.NoRender;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.*;

/**
 * 26.2 lightmap 迁移（已按 {@code 26.2/net/minecraft/client/renderer/Lightmap.java}、
 * {@code LightmapRenderStateExtractor.java}、{@code state/LightmapRenderState.java}、
 * {@code assets/minecraft/shaders/core/lightmap.fsh} 核实）。
 * <p>
 * 1.21.1 的 {@code LightmapTextureManager} 在 CPU 上逐像素写 16x16 的 {@code NativeImage}。
 * 26.2 改为 GPU 侧生成：{@code LightmapRenderStateExtractor#extract} 只算出 10 个标量/向量，
 * 由 {@code Lightmap#render} 打包进 {@code LightmapInfo} UBO，再由
 * {@code RenderPipelines.LIGHTMAP} + {@code core/lightmap.fsh} 画一张 16x16 纹理。
 * 因此「逐像素改颜色」不再可行，但 <b>{@link LightmapRenderState} 就是最终的逐像素公式的输入</b>，
 * 改它就等于改整张 lightmap，且比覆盖 {@code lightmap.fsh} 安全得多（不需要资源覆盖、不影响未开启模块的玩家）：
 * <pre>
 * block_brightness = get_brightness(block_level) * BlockFactor
 * sky_brightness   = get_brightness(sky_level)   * SkyFactor
 * color = max(AmbientColor, NightVisionColor * NightVisionFactor)
 *       + SkyLightColor * sky_brightness
 *       + mix(BlockLightTint, white, 0.9 * parabolic(block_level)) * block_brightness
 * color = mix(color, color * (0.7,0.6,0.6), BossOverlayWorldDarkeningFactor)
 * color = color - DarknessScale
 * color = mix(clamp(color), notGamma(color), BrightnessFactor)
 * </pre>
 * 逐项映射：
 * <ul>
 *   <li>{@code Ambience.fullBright} → 夜视强度拉满 + 夜视色置纯白
 *       （{@code color = max(ambient, white) = 1}，任何光照等级都到顶），同时把
 *       {@code BrightnessFactor} 拉到 1、{@code DarknessScale} 归零；</li>
 *   <li>{@code Ambience.worldColor} → 旧实现「亮度 &lt; 1 的像素整块写成 worldColor」，
 *       现在等价地写成「环境光下限 = worldColor，并把天空光/方块光/夜视色按 worldColor 相乘」，
 *       白色时即旧默认的整体提亮，彩色时得到整体染色；</li>
 *   <li>{@code NoRender.darkness} → {@code DarknessScale = 0}（旧实现是 {@code getDarknessFactor} 恒 0）。</li>
 * </ul>
 */
@Mixin(LightmapRenderStateExtractor.class)
public class MixinLightmapTextureManager {

    // 26.2: was @Redirect on LocalPlayer#hasStatusEffect in "update"（fullBright）+
    //      @Inject on "update" HEAD（worldColor）+ getDarknessFactor 注入（darkness）
    @Inject(method = "extract", at = @At("TAIL"))
    private void updateHook(LightmapRenderState renderState, float partialTicks, CallbackInfo ci) {
        if (!renderState.needsUpdate) return;

        // fullBright：等价于强制拥有夜视效果 + 伽马拉满
        if (Ambience.INSTANCE.isOn() && Ambience.INSTANCE.fullBright.getValue()) {
            renderState.nightVisionEffectIntensity = 1.0F;
            renderState.nightVisionColor = LightmapRenderStateExtractor.WHITE;
            renderState.brightness = 1.0F;
            renderState.darknessEffectScale = 0.0F;
        }

        // NoRender.darkness：原来把 getDarknessFactor 置 0，现在把 lightmap 的黑暗扣减量置 0
        if (NoRender.INSTANCE.isOn() && NoRender.INSTANCE.darkness.getValue()) {
            renderState.darknessEffectScale = 0.0F;
        }

        if (Ambience.INSTANCE.isOn() && Ambience.INSTANCE.worldColor.booleanValue) {
            Color color = Ambience.INSTANCE.worldColor.getValue();
            // 26.2 没有逐像素写入口，用 alpha 在「原色」与「white（无效果）」之间插值，
            // 这样 worldColor 的 alpha 语义（0 = 不染色）与原 GUI 调色一致。
            float a = color.getAlpha() / 255f;
            Vector3f tint = new Vector3f(
                    lerp(1.0f, color.getRed() / 255f, a),
                    lerp(1.0f, color.getGreen() / 255f, a),
                    lerp(1.0f, color.getBlue() / 255f, a)
            );
            // 环境光下限：暗部整体抬到 worldColor（旧的「亮度 < 1 的像素整块填色」）
            renderState.ambientColor = tint;
            // 受光部分整体染色：三路光色乘以 tint（tint 为白时是恒等变换）
            renderState.skyLightColor = mul(renderState.skyLightColor, tint);
            renderState.blockLightTint = mul(renderState.blockLightTint, tint);
            renderState.nightVisionColor = mul(renderState.nightVisionColor, tint);
        }
    }

    @Unique
    private static float lerp(float delta, float start, float end) {
        return start + delta * (end - start);
    }

    @Unique
    private static Vector3f mul(Vector3fc value, Vector3f factor) {
        return new Vector3f(value.x() * factor.x, value.y() * factor.y, value.z() * factor.z);
    }
}
