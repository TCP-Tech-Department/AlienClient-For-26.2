package dev.luminous.asm.mixins;

import dev.luminous.mod.modules.impl.render.Xray;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.2: {@code FluidRenderer#render(BlockRenderView, BlockPos, VertexConsumer, BlockState, FluidState)}
 * 改名 {@code tesselate(BlockAndTintGetter, BlockPos, FluidRenderer.Output, BlockState, FluidState)}，
 * {@code BlockRenderView} → {@code net.minecraft.client.renderer.block.BlockAndTintGetter}。
 */
@Mixin(FluidRenderer.class)
public abstract class MixinFluidRenderer {

    @Inject(method = "tesselate", at = @At("HEAD"), cancellable = true)
    private void onRender(BlockAndTintGetter level, BlockPos pos, FluidRenderer.Output output,
                          BlockState blockState, FluidState fluidState, CallbackInfo info) {
        if (Xray.shouldBlock(blockState)) info.cancel();
    }
}
