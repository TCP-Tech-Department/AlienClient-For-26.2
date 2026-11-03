package dev.luminous.asm.mixins;

import dev.luminous.mod.modules.impl.render.Ambience;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.*;

/**
 * 26.2: 原目标类 {@code net.minecraft.client.render.DimensionEffects}（DimensionSpecialEffects）
 * 在 26.2 已被整体删除 —— 维度视觉差异改由 {@code DimensionType} 的 {@code EnvironmentAttributes}
 * + {@code FogEnvironment} 驱动。
 * <p>
 * 最接近的覆写点：{@code FogRenderer#computeFogColor(Camera, float, ClientLevel, int, float, Vector4f)}
 * （26.2 唯一的雾色计算，输出写到 {@code dest}）。原 {@code getFogColorOverride} 的「用 dimensionColor
 * 覆盖雾色」逻辑在此保留：TAIL 直接改写 dest。
 */
@Mixin(FogRenderer.class)
public class MixinDimensionEffects {

    @Inject(method = "computeFogColor", at = @At("TAIL"))
    private void hookGetFogColorOverride(Camera camera, float partialTicks, ClientLevel level, int renderDistance,
                                         float darkenWorldAmount, Vector4f dest, CallbackInfo ci) {
        if (Ambience.INSTANCE.isOn() && Ambience.INSTANCE.dimensionColor.booleanValue) {
            Color color = Ambience.INSTANCE.dimensionColor.getValue();
            dest.set((float) color.getRed() / 255.0f, (float) color.getGreen() / 255.0f,
                    (float) color.getBlue() / 255.0f, 1.0f);
        }
    }
}
