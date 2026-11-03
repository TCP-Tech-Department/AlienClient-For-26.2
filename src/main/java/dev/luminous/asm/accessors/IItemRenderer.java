package dev.luminous.asm.accessors;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/**
 * 26.2: 1.21.1 的 {@code ItemRenderer} / {@code BuiltinModelItemRenderer} 整体被
 * {@code ItemStackRenderState} + {@code ItemModel} + {@code SpecialModelRenderer} 体系取代。
 * <p>
 * 旧接口提供两项能力：
 * <ul>
 *   <li>{@code hookGetBuiltinModelItemRenderer()} → {@link #hookGetSpecialRenderer()}
 *       （内置/特殊模型渲染器，字段改名并搬到 {@code LayerRenderState}）；</li>
 *   <li>{@code hookRenderBakedItemModel(model, stack, light, overlay, matrices, vertices)}
 *       → 用 {@link #hookGetQuads()} + {@link #hookApplyTransform(PoseStack.Pose)}
 *       + {@link #hookSubmit(PoseStack, SubmitNodeCollector, int, int, int)} 组合还原：
 *       先改 quad 列表，再套上该层的 {@code ItemTransform}，最后把这一层提交给渲染收集器。
 *       26.2 的 {@code ItemStackRenderState.LayerRenderState#submit} 做的就是这三件事，
 *       而 {@code renderBakedItemModel} 在 1.21.1 做的也正是「把一份 BakedModel 的 quad
 *       按变换提交」，语义一致。</li>
 * </ul>
 * 已按 26.2 源码核实（{@code 26.2/net/minecraft/client/renderer/item/ItemStackRenderState.java}）：
 * <pre>
 * private final List&lt;BakedQuad&gt; quads = new ArrayList&lt;&gt;();                       // L147
 * private @Nullable SpecialModelRenderer&lt;Object&gt; specialRenderer;               // L154
 * public List&lt;BakedQuad&gt; prepareQuadList()                                    // L174（public，不用 accessor）
 * private void submit(PoseStack, SubmitNodeCollector, int, int, int)           // L219（private）
 * private void applyTransform(PoseStack.Pose)                                  // L245（private）
 * </pre>
 * 本接口目前没有调用方（全库 grep 过），纯属「保留自定义物品渲染的能力」。
 */
@Mixin(ItemStackRenderState.LayerRenderState.class)
public interface IItemRenderer {

    /** 26.2: 特殊/内置模型渲染器（原 {@code builtinModelItemRenderer} 的等价物）。 */
    @Accessor("specialRenderer")
    SpecialModelRenderer<Object> hookGetSpecialRenderer();

    /**
     * 直接拿到本层待提交的四边面列表（可变）。
     * <p>
     * 与 public 的 {@code prepareQuadList()} 指向同一个 {@code ArrayList}，用哪个都行；
     * 这里保留 accessor 是为了「过滤脚本不改模型解析流程」这一类旧调用方的可移植性。
     * 注意：本列表会被 {@code submit} 按引用交出，改完之后要调用一次 {@link #hookSubmit}。
     */
    @Accessor("quads")
    List<BakedQuad> hookGetQuads();

    /** 应用本层的 {@code ItemTransform}（含左右手判定与 {@code localTransform}）。 */
    @Invoker("applyTransform")
    void hookApplyTransform(PoseStack.Pose localPose);

    /**
     * 26.2 的「渲染这一层」：把当前 quad 列表提交给收集器（原 {@code renderBakedItemModel} 的等价能力）。
     * <p>
     * 调用方需要先 {@code poseStack.pushPose()} 与 {@link #hookApplyTransform}，
     * 与 {@code LayerRenderState#submit} 内部流程一致；否则变换会不对。
     */
    @Invoker("submit")
    void hookSubmit(PoseStack poseStack, SubmitNodeCollector collector, int lightCoords, int overlayCoords, int outlineColor);
}
