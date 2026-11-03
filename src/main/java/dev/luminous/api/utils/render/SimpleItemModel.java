package dev.luminous.api.utils.render;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;

import java.util.List;

/**
 * 26.2: {@code net.minecraft.client.render.model.BakedModel} 整包已移除。
 * <p>
 * 物品模型不再是「可查询 quads 的 BakedModel 对象」，而是在
 * {@code ItemStackRenderState.LayerRenderState#prepareQuadList()} 阶段把四边面烘焙进列表，
 * 渲染时整表提交给 {@code SubmitNodeCollector#submitItem}。
 * <p>
 * 因此本类从「只保留 SOUTH 朝向面的 BakedModel 包装」改为「四边面过滤器」，
 * 语义保持不变：{@code NoRender} 的 2DItem 优化只保留朝南的面，其余面丢弃。
 * <p>
 * 26.2 里「替换整个模型」与「过滤四边面」的可行性（已逐处核实源码）：
 * <ul>
 *   <li>替换模型：{@code ItemModelResolver#updateForTopItem} 会把
 *       {@code ClientItem} 解析出的 {@code ItemModel} 结果写进 {@code ItemStackRenderState}，
 *       要替换就得自己实现 {@link net.minecraft.client.renderer.item.ItemModel} 并包住解析每个物品的模型
 *       （registry 级别、且 {@code ItemModel#update} 的入参不含可替换的 model 句柄），代价远大于收益；</li>
 *   <li>过滤四边面：{@code LayerRenderState#submit} 把最终 quad 列表交给
 *       {@code SubmitNodeCollector#submitItem(PoseStack, ItemDisplayContext, int, int, int, int[], List, FoilType)}，
 *       在这里按 {@link ItemDisplayContext} 过滤是唯一「既不改模型解析、又不动渲染管线」的路径。</li>
 * </ul>
 * 1.21.1 里 {@code BakedModel#hasDepth()} 那道「3D 模型不扁平化」的保险已不需要：
 * 有深度的模型（箱子、床…）与内置渲染器在 26.2 走
 * {@code LayerRenderState#specialRenderer} 分支，根本不会进 {@code submitItem}。
 */
public final class SimpleItemModel {

    /**
     * 扁平化后保留的朝向。2D 物品模型（{@code item/generated}）正反两面是 SOUTH/NORTH，
     * 只留 SOUTH 即可看到正面。
     */
    private static final Direction KEEP_DIRECTION = Direction.SOUTH;

    /**
     * 允许被扁平化的 {@link ItemDisplayContext}。
     * <p>
     * 只有 {@link ItemDisplayContext#GROUND}（掉落物，原 1.21.1 的
     * {@code ModelTransformationMode.GROUND}）—— 与旧实现完全一致。
     * 其余 context 必须保留完整模型，否则会直接看不见东西：
     * <ul>
     *   <li>{@code GUI}：物品栏/HUD 图标，只留一面会变成看不见的薄片；</li>
     *   <li>{@code FIRST_PERSON_*} / {@code THIRD_PERSON_*}：手持物品，只留一面会穿帮；</li>
     *   <li>{@code HEAD}：头盔槽/穿戴展示；</li>
     *   <li>{@code FIXED}：物品展示框，展示框自身还有一层朝向旋转，只留 SOUTH 会随朝向整块消失；</li>
     *   <li>{@code ON_SHELF}：26.2 新增的书架类展示，同上；</li>
     *   <li>{@code NONE}：{@code ItemStackRenderState} 的初始值，语义上是「未指定」，不能猜。</li>
     * </ul>
     * 以后若要给别的 context 开关，只需要改这一个方法（调用方已经按 context 走）。
     */
    public static boolean isFlattenableContext(ItemDisplayContext context) {
        return context == ItemDisplayContext.GROUND;
    }

    /**
     * 按 context 过滤一组物品四边面；context 不允许扁平化时原样返回入参（零分配）。
     *
     * @return 过滤后的**新列表**。绝不能返回复用的缓存列表，原因见 {@link #filter(List)}。
     */
    public static List<BakedQuad> filter(List<BakedQuad> quads, ItemDisplayContext context) {
        if (!isFlattenableContext(context)) {
            return quads;
        }
        return filter(quads);
    }

    /**
     * 过滤一组物品四边面，只保留朝南的面。
     * <p>
     * <b>必须每次都返回新列表</b>：{@code SubmitNodeCollection#submitItem} 是把 quad 列表**按引用**
     * 存进 {@code ItemFeatureRenderer.Submit}（record 的 {@code quads} 字段），要到
     * {@code FeatureRenderDispatcher#prepareFrame} / {@code buildGroup} 阶段才真正遍历；
     * 若复用同一个可变列表，同一帧内所有被扁平化的物品会互相覆盖，
     * 后提交的物品会带着前一个物品的 quad 位置/贴图渲染（或者是空列表直接消失）。
     * 过滤只在「掉落物 + 2DItem 开启」时发生，分配一次 {@code ObjectArrayList} 的代价可以忽略。
     */
    public static List<BakedQuad> filter(List<BakedQuad> quads) {
        List<BakedQuad> kept = new ObjectArrayList<>(quads.size());
        for (BakedQuad quad : quads) {
            // 26.2: BakedQuad#getFace → record 组件 direction()；官方允许 direction 为 null（无面向），
            // 旧实现的 isCorrectDirectionForType(null) 也是返回 false，这里行为一致。
            if (quad.direction() == KEEP_DIRECTION) {
                kept.add(quad);
            }
        }
        return kept;
    }
}
