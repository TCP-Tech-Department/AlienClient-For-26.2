package dev.luminous.asm.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.luminous.mod.modules.impl.render.NoRender;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.2: 实体渲染拆成 extract（{@code extractRenderState}）+ submit（{@code submit}）两段，
 * 旧的 {@code render(ItemEntity,...)} 与即时模式绘制已消失。
 * <p>
 * 旧 mixin 会用 {@code getRenderedAmount} + 手写矩阵重画堆叠物品，这层逻辑 26.2 原版
 * 已在 {@code ItemEntityRenderer#submitMultipleFromCount} 里实现（数量由
 * {@code ItemEntityRenderState.count} 提前算好），因此无需再替换绘制流程；
 * 现在只剩「2DItem 关闭阴影」这一项需要在这里处理（阴影在 submit 之后由
 * {@code EntityRenderDispatcher} 提交，清空 shadowPieces 即可）。
 */
@Mixin(ItemEntityRenderer.class)
public class MixinItemEntityRenderer {

    @Inject(method = "submit", at = @At("HEAD"))
    public void submit(ItemEntityRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
                       CameraRenderState camera, CallbackInfo ci) {
        if (!NoRender.INSTANCE.isOn() || !NoRender.INSTANCE.fastItem.getValue()) {
            return;
        }
        if (!NoRender.INSTANCE.castShadow.getValue()) {
            state.shadowPieces.clear();
            state.shadowRadius = 0.0F;
        }
    }
}
