package dev.luminous.asm.mixins;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.luminous.mod.modules.impl.render.Xray;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.2: {@code BlockModelRenderer#renderSmooth/renderFlat(BlockRenderView, BakedModel, ...)} 整条
 * 即时模式管道被删除，官方等价类是 {@code ModelBlockRenderer}：
 * <ul>
 *   <li>{@code tesselateBlock} —— 方块几何入口（旧 renderSmooth/renderFlat 的位置）；</li>
 *   <li>{@code shouldRenderFace(BlockAndTintGetter, BlockState, Direction, BlockPos)} —— 旧的
 *       {@code Block#shouldDrawSide} 判定，Xray 的「强制绘制被遮挡面」移到此处。</li>
 * </ul>
 */
@Mixin(ModelBlockRenderer.class)
public abstract class MixinBlockModelRenderer {

    @Inject(method = "tesselateBlock", at = @At("HEAD"), cancellable = true)
    private void onTesselateBlock(BlockQuadOutput output, float x, float y, float z, BlockAndTintGetter level,
                                  BlockPos pos, BlockState state, BlockStateModel model, long seed, CallbackInfo ci) {
        if (Xray.shouldBlock(state)) ci.cancel();
    }

    /**
     * 26.2: 原 {@code Block#shouldDrawSide(BlockState, BlockGetter, BlockPos, Direction, BlockPos)} 的
     * 搬迁点。这里第 4 个参数是「邻居坐标」，Xray#modifyDrawSide 需要的是「当前方块坐标」，
     * 所以反向偏移一次。
     */
    @ModifyReturnValue(method = "shouldRenderFace", at = @At("RETURN"))
    private boolean onShouldDrawSide(boolean original, BlockAndTintGetter level, BlockState state,
                                     Direction direction, BlockPos neighborPos) {
        Xray xray = Xray.INSTANCE;
        if (xray.isOn()) {
            return xray.modifyDrawSide(state, level, neighborPos.relative(direction.getOpposite()), direction, original);
        }
        return original;
    }
}
