package dev.luminous.asm.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.luminous.mod.modules.impl.render.NoRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.2: {@code InGameOverlayRenderer} 整体删除，火焰/水下/方块贴合三种屏幕覆盖层都搬进
 * {@code net.minecraft.client.renderer.ScreenEffectRenderer}，并且原来的三个入口方法
 * （renderFireOverlay / renderUnderwaterOverlay / renderInWallOverlay）变成三个 private static
 * 提交辅助方法：{@code submitFire} / {@code submitWater} / {@code submitBlockSprite}。
 * 逐个取消即可，粒度与旧版一致。
 */
@Mixin(ScreenEffectRenderer.class)
public class MixinInGameOverlayRenderer {

    /** 26.2: was InGameOverlayRenderer#renderFireOverlay。 */
    @Inject(method = "submitFire", at = @At("HEAD"), cancellable = true)
    private static void onRenderFireOverlay(PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
                                           TextureAtlasSprite sprite, CallbackInfo ci) {
        if (NoRender.INSTANCE.isOn() && NoRender.INSTANCE.fireOverlay.getValue()) {
            ci.cancel();
        }
    }

    /** 26.2: was InGameOverlayRenderer#renderUnderwaterOverlay。 */
    @Inject(method = "submitWater", at = @At("HEAD"), cancellable = true)
    private static void onRenderUnderwaterOverlay(Minecraft minecraft, PoseStack poseStack,
                                                  SubmitNodeCollector submitNodeCollector, CallbackInfo ci) {
        if (NoRender.INSTANCE.isOn() && NoRender.INSTANCE.waterOverlay.getValue()) {
            ci.cancel();
        }
    }

    /** 26.2: was InGameOverlayRenderer#renderInWallOverlay（方块贴在镜头上的覆盖层）。 */
    @Inject(method = "submitBlockSprite", at = @At("HEAD"), cancellable = true)
    private static void onrenderInWallOverlay(TextureAtlasSprite sprite, PoseStack poseStack,
                                              SubmitNodeCollector submitNodeCollector, int color, CallbackInfo ci) {
        if (NoRender.INSTANCE.isOn() && NoRender.INSTANCE.blockOverlay.getValue()) {
            ci.cancel();
        }
    }
}
