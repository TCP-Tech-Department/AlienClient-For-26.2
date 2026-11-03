package dev.luminous.asm.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.luminous.api.utils.render.SimpleItemModel;
import dev.luminous.mod.modules.impl.render.NoRender;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/**
 * 26.2: {@code ItemRenderer} / {@code BakedModel} / {@code ModelTransformationMode} 全部移除。
 * <p>
 * 物品现在由 {@code ItemModelResolver} 提前烘焙成 {@code ItemStackRenderState}（每层一份四边面列表），
 * 渲染时 {@code LayerRenderState#submit} 把整表交给 {@code SubmitNodeCollector#submitItem}。
 * 旧 mixin 通过「包装 BakedModel、只保留 SOUTH 面」实现 2DItem 优化，
 * 这里改为在提交前过滤四边面列表，等价且更靠后（不依赖模型解析流程）。
 * <p>
 * 已按 26.2 字节码核实（{@code 26.2/net/minecraft/client/renderer/item/ItemStackRenderState.java:237}
 * 与 minecraft-client.jar 里的 {@code ItemStackRenderState$LayerRenderState#submit}）：
 * <pre>
 * invokeinterface net/minecraft/client/renderer/SubmitNodeCollector.submitItem
 *     (Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;III[I
 *      Ljava/util/List;Lnet/minecraft/client/renderer/item/ItemStackRenderState$FoilType;)V
 * </pre>
 * 注意 owner 是 {@code SubmitNodeCollector}（而不是声明该方法的父接口
 * {@code OrderedSubmitNodeCollector}），{@code @At#target} 必须照这个 owner 写，
 * 否则 {@code defaultRequire = 1} 会在启动时直接 injection failure。
 * 参数类型也一字不差：{@code int,int,int,int[],List<BakedQuad>,FoilType}。
 * <p>
 * 这里必须用 MixinExtras 的 {@code @WrapOperation} 而不是 {@code @Redirect}。
 * fabric-renderer-api-v1 自己也在同一个调用点上做了 {@code @Redirect}（submitItemProxy），
 * 两个 Redirect 会互相冲突，Mixin 会跳过其中一个，导致 fabric 那边的 mixin 注入失败并崩游戏
 * （实测：{@code @Redirect conflict. Skipping fabric-renderer-api-v1...}）。
 * WrapOperation 可以与其它 Redirect 共存。
 * <p>
 * {@code displayContext} 不会为 null：{@code ItemStackRenderState#displayContext} 字段初值是
 * {@code ItemDisplayContext.NONE}，{@code clear()} 也写 NONE，只有
 * {@code ItemModelResolver#updateForTopItem} 会覆盖它，而官方所有调用点都传具体枚举。
 * 即便如此，这里的判断用 {@code ==}（null 安全）而不是 {@code .equals}/{@code .ordinal()}。
 */
@Mixin(ItemStackRenderState.LayerRenderState.class)
public class MixinItemRenderer {

    @WrapOperation(
            method = "submit",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitItem(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/item/ItemDisplayContext;III[ILjava/util/List;Lnet/minecraft/client/renderer/item/ItemStackRenderState$FoilType;)V"
            )
    )
    private void wrapSubmitItem(SubmitNodeCollector collector, PoseStack poseStack, ItemDisplayContext displayContext,
                                int lightCoords, int overlayCoords, int outlineColor, int[] tintLayers,
                                List<BakedQuad> quads, ItemStackRenderState.FoilType foilType, Operation<Void> original) {
        // 26.2: 只有掉落物（GROUND）才做扁平化，GUI/手持/展示框等 context 保持完整模型。
        // context 白名单集中在 SimpleItemModel#isFlattenableContext，这里不再硬编码单个枚举。
        if (NoRender.INSTANCE.isOn()
                && NoRender.INSTANCE.fastItem.getValue()
                && !NoRender.INSTANCE.renderSidesOfItems.getValue()
                && SimpleItemModel.isFlattenableContext(displayContext)
                && !quads.isEmpty()) {
            // filter 每次返回新列表：submitItem 会按引用保存该列表，延迟到本帧稍后才遍历。
            original.call(collector, poseStack, displayContext, lightCoords, overlayCoords, outlineColor, tintLayers,
                    SimpleItemModel.filter(quads, displayContext), foilType);
        } else {
            original.call(collector, poseStack, displayContext, lightCoords, overlayCoords, outlineColor, tintLayers,
                    quads, foilType);
        }
    }
}
