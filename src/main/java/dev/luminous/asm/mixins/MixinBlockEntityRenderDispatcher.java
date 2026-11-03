package dev.luminous.asm.mixins;

import dev.luminous.Alien;
import dev.luminous.api.events.impl.RenderBlockEntityEvent;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 26.2: 方块实体渲染拆成 extract（{@code tryExtractRenderState}）+ submit（{@code submit}），
 * 旧的 {@code render(BlockEntity,float,PoseStack,VertexConsumerProvider)} 消失，且 submit 阶段
 * 只有 {@code BlockEntityRenderState}、拿不到 BlockEntity 实例。
 * <p>
 * 因此「取消渲染」改为在 extract 阶段返回 {@code null}（该返回值本身就是 @Nullable，
 * 调用方 {@code BlockEntityRenderDispatcher#tryExtractRenderState} 的调用者会跳过 null），
 * 这样 Xray 屏蔽箱子/告示牌等方块实体的效果保持不变。
 */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class MixinBlockEntityRenderDispatcher {

    @Inject(method = "tryExtractRenderState", at = @At("HEAD"), cancellable = true)
    private void onRenderEntity(BlockEntity blockEntity, float partialTicks,
                                ModelFeatureRenderer.CrumblingOverlay breakProgress, boolean isGloballyRendered,
                                CallbackInfoReturnable<BlockEntityRenderState> cir) {
        RenderBlockEntityEvent event = Alien.EVENT_BUS.post(RenderBlockEntityEvent.get(blockEntity));
        if (event.isCancelled()) cir.setReturnValue(null);
    }
}
