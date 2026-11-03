package dev.luminous.asm.mixins;

import dev.luminous.mod.modules.impl.render.NoRender;
import net.minecraft.client.renderer.entity.PaintingRenderer;
import net.minecraft.client.renderer.entity.state.PaintingRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;

/**
 * 26.2: {@code PaintingEntityRenderer} → {@code net.minecraft.client.renderer.entity.PaintingRenderer}
 * （注意不是 EntityRenderer 的 Paint*），渲染入口 {@code render(Painting,float,float,PoseStack,
 * VertexConsumerProvider,int)} → {@code submit(PaintingRenderState, PoseStack, SubmitNodeCollector,
 * CameraRenderState)}。
 */
@Mixin(value = {PaintingRenderer.class})
public class MixinPaintingEntityRenderer {
    @Inject(at = {@At(value = "HEAD")}, method = {"submit"}, cancellable = true)
    public void render(PaintingRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
                       CameraRenderState camera, CallbackInfo ci) {
        if (NoRender.INSTANCE.isOn() && NoRender.INSTANCE.lightsUpdate.getValue()) {
            ci.cancel();
        }
    }
}
