package dev.luminous.asm.mixins;

import dev.luminous.mod.modules.impl.render.Xray;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 26.2: fabric-renderer-indigo 的 {@code AbstractBlockRenderContext} / {@code MutableQuadViewImpl} /
 * {@code BlockRenderInfo} 已被上游删除（indigo 在 26.2 重写为 {@code AltModelBlockRendererImpl} +
 * {@code ExtendedBlockModelFeatureRenderer}），因此原「在 bufferQuad 前丢弃 quad」的注入点不存在。
 * <p>
 * 等价位置是 26.2 原版方块几何生成器 {@code ModelBlockRenderer}：它的
 * {@code tesselateAmbientOcclusion} / {@code tesselateFlat} 才是真正逐 quad 输出的地方，
 * Xray 屏蔽某个方块时直接跳过这两个方法即可（与旧 quad 级行为一致）。
 */
@Mixin(ModelBlockRenderer.class)
public abstract class MixinAbstractBlockRenderContext {

    @Inject(method = {"tesselateAmbientOcclusion", "tesselateFlat"}, at = @At("HEAD"), cancellable = true)
    private void onBufferQuad(BlockQuadOutput output, float x, float y, float z, List<BlockStateModelPart> parts,
                              BlockAndTintGetter level, BlockState state, BlockPos pos, CallbackInfo ci) {
        if (Xray.shouldBlock(state)) ci.cancel();
    }
}
