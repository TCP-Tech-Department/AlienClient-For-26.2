package dev.luminous.asm.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.luminous.mod.modules.impl.render.NameTags;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.2: 实体渲染拆成 extract + submit，旧的
 * {@code renderLabelIfPresent(T, Component, PoseStack, VertexConsumerProvider, int, float)} 消失，
 * 名牌绘制点变成 {@code submitNameDisplay(EntityRenderState, PoseStack, SubmitNodeCollector,
 * CameraRenderState)}。
 * <p>
 * 注意：{@code EntityRenderer#submitNameDisplay} 被 {@link AvatarRenderer}
 * （玩家渲染，负责玩家名牌）覆写，只注入基类对玩家无效，所以直接注入 AvatarRenderer 的实现；
 * {@code AvatarRenderState} 就是玩家名牌状态（Entity 已不在渲染阶段出现）。取消即「不画原版名牌」，
 * NameTags 模块会自绘。
 */
@Mixin(AvatarRenderer.class)
public abstract class MixinEntityRenderer {

    @Inject(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V", at = @At("HEAD"), cancellable = true)
    private void onRenderLabel(AvatarRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                               CameraRenderState camera, CallbackInfo ci) {
        if (NameTags.INSTANCE.isOn()) ci.cancel();
    }
}
