package dev.luminous.asm.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.luminous.mod.modules.impl.render.NoRender;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 26.2: {@code ArmorFeatureRenderer}（Yarn）被 {@code HumanoidArmorLayer} + {@code EquipmentLayerRenderer} 取代。
 * <p>
 * 盔甲本体、附魔光效、盔甲纹饰现在都在 {@code EquipmentLayerRenderer#renderLayers} 里
 * 通过 {@code OrderedSubmitNodeCollector#submitModel} 提交，原来的
 * {@code renderArmorParts} / {@code renderTrim} / {@code renderGlint} 三个方法均已消失。
 * 这里在 {@code renderLayers} 的三处提交点分别拦截，保持 NoRender 的三个开关语义：
 * <ul>
 *   <li>ordinal 0 —— 盔甲本体（armorCutoutNoCull）</li>
 *   <li>ordinal 1 —— 附魔光效（armorEntityGlint）</li>
 *   <li>ordinal 2 —— 盔甲纹饰（带 sprite 的 submitModel）</li>
 * </ul>
 * 用的是 {@code require = 0}：万一某个提交点被 26.2 后续版本调整，只会退化为「该开关不生效」，
 * 而不会因为 {@code defaultRequire = 1} 让游戏启动失败。
 */
@Mixin(EquipmentLayerRenderer.class)
public class MixinArmorFeatureRenderer {

    private static final String SUBMIT_MODEL =
            "Lnet/minecraft/client/renderer/OrderedSubmitNodeCollector;submitModel("
                    + "Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/rendertype/RenderType;III"
                    + "Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;I"
                    + "Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V";

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Redirect(method = "renderLayers", at = @At(value = "INVOKE", target = SUBMIT_MODEL, ordinal = 0), require = 0)
    private void onRenderArmorParts(OrderedSubmitNodeCollector collector, Model model, Object state, PoseStack poseStack,
                                    RenderType renderType, int lightCoords, int overlayCoords, int color,
                                    TextureAtlasSprite sprite, int outlineColor, ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        if (NoRender.INSTANCE.isOn() && NoRender.INSTANCE.armorParts.getValue()) {
            return;
        }
        collector.submitModel(model, state, poseStack, renderType, lightCoords, overlayCoords, color, sprite, outlineColor, crumblingOverlay);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Redirect(method = "renderLayers", at = @At(value = "INVOKE", target = SUBMIT_MODEL, ordinal = 1), require = 0)
    private void onRenderArmorGlint(OrderedSubmitNodeCollector collector, Model model, Object state, PoseStack poseStack,
                                    RenderType renderType, int lightCoords, int overlayCoords, int color,
                                    TextureAtlasSprite sprite, int outlineColor, ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        if (NoRender.INSTANCE.isOn() && NoRender.INSTANCE.armorGlint.getValue()) {
            return;
        }
        collector.submitModel(model, state, poseStack, renderType, lightCoords, overlayCoords, color, sprite, outlineColor, crumblingOverlay);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Redirect(method = "renderLayers", at = @At(value = "INVOKE", target = SUBMIT_MODEL, ordinal = 2), require = 0)
    private void onRenderArmorTrim(OrderedSubmitNodeCollector collector, Model model, Object state, PoseStack poseStack,
                                   RenderType renderType, int lightCoords, int overlayCoords, int color,
                                   TextureAtlasSprite sprite, int outlineColor, ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        if (NoRender.INSTANCE.isOn() && NoRender.INSTANCE.armorTrim.getValue()) {
            return;
        }
        collector.submitModel(model, state, poseStack, renderType, lightCoords, overlayCoords, color, sprite, outlineColor, crumblingOverlay);
    }
}
