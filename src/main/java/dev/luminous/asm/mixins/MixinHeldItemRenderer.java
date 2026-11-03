package dev.luminous.asm.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import dev.luminous.Alien;
import dev.luminous.api.events.impl.HeldItemRendererEvent;
import dev.luminous.mod.modules.impl.render.Chams;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.OutputTarget;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 26.2: {@code HeldItemRenderer} → {@code ItemInHandRenderer}（net.minecraft.client.renderer）。
 * <p>
 * {@code renderFirstPersonItem(AbstractClientPlayer,float,float,InteractionHand,float,ItemStack,float,
 * PoseStack,VertexConsumerProvider,int)} 与 {@code renderItem(...)} 都已不存在（26.2 第一人称物件
 * 走 {@code submitArmWithItems} → 提交到 {@code SubmitNodeCollector}）。最接近且保留同样上下文的
 * 位置是 {@code submitArmWithItem(AbstractClientPlayer, float, float, InteractionHand, float, ItemStack,
 * float, PoseStack, SubmitNodeCollector, int)}：手上物品/手/装备进度/矩阵齐备，在此 HEAD 派发事件。
 * <p>
 * <b>Chams.hand（手持物/手臂染色）在 26.2 的实现</b>：
 * 1.21.1 用的是 {@code RenderSystem.setShaderColor(...)}（全局 {@code ColorModulator}）。
 * 26.2 已经<b>没有</b>任何全局着色颜色：
 * <ul>
 *   <li>{@code RenderSystem} 里没有 color 相关成员（只剩 fog / global settings / output override）；</li>
 *   <li>每条 {@code RenderType} 的 {@code ColorModulator} 由 {@code RenderType#writeTransform} 用常量
 *       {@code DynamicUniforms.WHITE} 写入（{@code RenderType.java:73}、{@code DynamicUniforms.java:37}）；</li>
 *   <li>{@code ItemInHandRenderer#renderItem} 内部 {@code new ItemStackRenderState()} 是局部变量
 *       （{@code ItemInHandRenderer.java:139}），外部拿不到，也没有可 join 的「手持物染色」RenderType。</li>
 * </ul>
 * 所以颜色只能落到顶点/模型颜色上。这里在 {@code submitArmWithItem} 入口把
 * {@code SubmitNodeCollector} 换成 {@link HandTintCollector} 代理，两类提交分别处理：
 * <ul>
 *   <li>{@code submitModel(...)}（手臂：{@code AvatarRenderer#renderHand} →
 *       {@code submitModelPart(arm, ..., entityTranslucent(skin))} → 默认 {@code tintedColor = -1}）：
 *       把 {@code tintedColor} 乘以 Hand 颜色（{@code ARGB.multiply}）；</li>
 *   <li>{@code submitItem(...)}（手持物）：每层四边面是一组 {@code BakedQuad} + 每层的
 *       {@code tintLayers}，原版在 {@code ItemFeatureRenderer#prepareMainSubmit} 里用
 *       {@code QuadInstance#setColor(tint)} 写顶点色。这里改成同样用
 *       {@code submitCustomGeometry} + {@code VertexConsumer#putBakedQuad(pose, quad, QuadInstance)}，
 *       {@code QuadInstance#setColor} 传「原 tint × Hand 颜色」，于是整件物品都被染色
 *       （不依赖 tintIndex，普通贴图面也会被乘色）。附魔光效按原版
 *       {@code ItemFeatureRenderer#prepareFoilSubmit} 的思路再补一遍 glint 提交。</li>
 * </ul>
 * 未开启 {@code Chams.hand} 时完全不包装，行为与原版一致；默认颜色 {@code -1}（白）乘上去是恒等。
 */
@Mixin(ItemInHandRenderer.class)
public abstract class MixinHeldItemRenderer {

    @Inject(method = "submitArmWithItem", at = @At("HEAD"))
    private void onRenderItem(AbstractClientPlayer player, float frameInterp, float xRot, InteractionHand hand,
                              float attack, ItemStack itemStack, float inverseArmHeight, PoseStack poseStack,
                              SubmitNodeCollector submitNodeCollector, int lightCoords, CallbackInfo ci) {
        HeldItemRendererEvent event = HeldItemRendererEvent.get(hand, itemStack, inverseArmHeight, poseStack);
        Alien.EVENT_BUS.post(event);
    }

    /**
     * 把本方法体内（以及它调用的 {@code renderPlayerArm} / {@code renderItem} / 地图/弓等分支）
     * 拿到的 collector 换成染色代理。{@code argsOnly = true} + 只按类型匹配，
     * 参数表里只有一个 {@code SubmitNodeCollector}，无歧义。
     */
    @ModifyVariable(method = "submitArmWithItem", at = @At("HEAD"), argsOnly = true)
    private SubmitNodeCollector alien$tintHandCollector(SubmitNodeCollector collector) {
        Chams chams = Chams.INSTANCE;
        if (chams == null || !chams.isOn() || !chams.hand.booleanValue || collector == null) {
            return collector;
        }
        int argb = chams.hand.getValue().getRGB();
        // 纯白且不透明时乘法是恒等（Hand 的默认值就是 -1），
        // 这种情况不包装：继续走原版 ItemFeatureRenderer 提交路径，默认配置下零风险。
        if ((argb & 0xFFFFFF) == 0xFFFFFF && (argb >>> 24) == 0xFF) {
            return collector;
        }
        return new HandTintCollector(collector, argb);
    }

    /** 26.2: {@code ItemFeatureRenderer#useTransparentGlint} 的等价判断（fabulous 时 glint 也要走半透明管线）。 */
    private static RenderType alien$glintType(RenderType itemRenderType) {
        boolean transparentGlint = itemRenderType != null
                && Minecraft.getInstance().gameRenderer.gameRenderState().useShaderTransparency()
                && itemRenderType.outputTarget() == OutputTarget.ITEM_ENTITY_TARGET;
        return transparentGlint ? RenderTypes.glintTranslucent() : RenderTypes.glint();
    }

    /**
     * {@code SubmitNodeCollector} 的染色代理：只改写 {@code submitModel}（手臂 tintedColor）与
     * {@code submitItem}（手持物顶点色），其余提交原样转发。
     * <p>
     * 实现 {@code SubmitNodeCollector} 后同时也满足 {@code OrderedSubmitNodeCollector}，
     * 所以 {@link #order(int)} 直接返回同类型代理即可。
     */
    private static final class HandTintCollector implements SubmitNodeCollector {
        private final SubmitNodeCollector delegate;
        private final int color;

        private HandTintCollector(SubmitNodeCollector delegate, int color) {
            this.delegate = delegate;
            this.color = color;
        }

        /**
         * {@code OrderedSubmitNodeCollector} 本身没有 {@code order(int)}（它是
         * {@code SubmitNodeCollector} 才有的方法），拿到的分栏 collector 无法再包成同类型代理；
         * 第一人称手部路径（{@code ItemInHandRenderer} 全文）不会调用 {@code order()}，
         * 所以这里原样转发，不做染色。
         */
        @Override
        public OrderedSubmitNodeCollector order(int order) {
            return this.delegate.order(order);
        }

        @Override
        public void submitShadow(PoseStack poseStack, float radius, List<EntityRenderState.ShadowPiece> pieces) {
            this.delegate.submitShadow(poseStack, radius, pieces);
        }

        @Override
        public void submitNameTag(PoseStack poseStack, Vec3 nameTagAttachment, int offset, Component name,
                                  boolean seeThrough, int lightCoords, CameraRenderState camera) {
            this.delegate.submitNameTag(poseStack, nameTagAttachment, offset, name, seeThrough, lightCoords, camera);
        }

        @Override
        public void submitText(PoseStack poseStack, float x, float y, FormattedCharSequence string, boolean dropShadow,
                               Font.DisplayMode displayMode, int lightCoords, int color, int backgroundColor,
                               int outlineColor) {
            this.delegate.submitText(poseStack, x, y, string, dropShadow, displayMode, lightCoords, color,
                    backgroundColor, outlineColor);
        }

        @Override
        public void submitFlame(PoseStack poseStack, EntityRenderState renderState, Quaternionf rotation) {
            this.delegate.submitFlame(poseStack, renderState, rotation);
        }

        @Override
        public void submitLeash(PoseStack poseStack, EntityRenderState.LeashState leashState) {
            this.delegate.submitLeash(poseStack, leashState);
        }

        @Override
        public <S> void submitModel(Model<? super S> model, S state, PoseStack poseStack, RenderType renderType,
                                    int lightCoords, int overlayCoords, int tintedColor, TextureAtlasSprite sprite,
                                    int outlineColor, ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
            // 手臂（以及任何走 submitModel 的手部模型）就是靠 tintedColor 上色的：
            // 默认 -1（白）→ 乘以 Hand 颜色后整体染色。
            this.delegate.submitModel(model, state, poseStack, renderType, lightCoords, overlayCoords,
                    ARGB.multiply(tintedColor, this.color), sprite, outlineColor, crumblingOverlay);
        }

        @Override
        public void submitMovingBlock(PoseStack poseStack, MovingBlockRenderState movingBlockRenderState, int outlineColor) {
            this.delegate.submitMovingBlock(poseStack, movingBlockRenderState, outlineColor);
        }

        @Override
        public void submitBlockModel(PoseStack poseStack, RenderType renderType, List<BlockStateModelPart> parts,
                                     int[] tintLayers, int lightCoords, int overlayCoords, int outlineColor) {
            this.delegate.submitBlockModel(poseStack, renderType, parts, tintLayers, lightCoords, overlayCoords, outlineColor);
        }

        @Override
        public void submitBreakingBlockModel(PoseStack poseStack, List<BlockStateModelPart> parts, int progress) {
            this.delegate.submitBreakingBlockModel(poseStack, parts, progress);
        }

        @Override
        public void submitShapeOutline(PoseStack poseStack, VoxelShape shape, RenderType renderType, int color,
                                       float width, boolean afterTerrain) {
            this.delegate.submitShapeOutline(poseStack, shape, renderType, color, width, afterTerrain);
        }

        @Override
        public void submitItem(PoseStack poseStack, ItemDisplayContext displayContext, int lightCoords, int overlayCoords,
                               int outlineColor, int[] tintLayers, List<BakedQuad> quads,
                               ItemStackRenderState.FoilType foilType) {
            // 物品的「发光描边」提交（第一人称几乎不会出现）不染色，原样转发，避免破坏原版行为。
            if (outlineColor != 0) {
                this.delegate.submitItem(poseStack, displayContext, lightCoords, overlayCoords, outlineColor,
                        tintLayers, quads, foilType);
                return;
            }

            // 每层四边面：原版 tint = material.isTinted() ? tintLayers[tintIndex] : -1（白）。
            // 这里再乘 Hand 颜色，未染色的面也会被乘上，所以整件物品都会变色。
            for (BakedQuad quad : quads) {
                BakedQuad.MaterialInfo material = quad.materialInfo();
                int tint = -1;
                if (material.isTinted() && material.tintIndex() >= 0 && material.tintIndex() < tintLayers.length) {
                    tint = tintLayers[material.tintIndex()];
                }
                int quadColor = ARGB.multiply(tint, this.color);
                this.delegate.submitCustomGeometry(poseStack, material.itemRenderType(), (pose, buffer) -> {
                    QuadInstance instance = new QuadInstance();
                    instance.setLightCoords(lightCoords);
                    instance.setOverlayCoords(overlayCoords);
                    instance.setColor(quadColor);
                    buffer.putBakedQuad(pose, quad, instance);
                });
            }

            // 附魔光效：原版由 ItemFeatureRenderer#prepareFoilSubmit 单独提交一遍 glint 管线，
            // 这里绕过了 submitItem，所以要自己补，否则附魔手持物会丢失光效。
            // （SPECIAL 的 decal 变换未复刻，仅影响少数特殊模型的附魔光斑位置。）
            if (foilType != ItemStackRenderState.FoilType.NONE) {
                RenderType glint = alien$glintType(quads.isEmpty() ? null : quads.get(0).materialInfo().itemRenderType());
                this.delegate.submitCustomGeometry(poseStack, glint, (pose, buffer) -> {
                    QuadInstance instance = new QuadInstance();
                    instance.setLightCoords(lightCoords);
                    instance.setOverlayCoords(overlayCoords);
                    instance.setColor(-1);
                    for (BakedQuad quad : quads) {
                        buffer.putBakedQuad(pose, quad, instance);
                    }
                });
            }
        }

        @Override
        public void submitCustomGeometry(PoseStack poseStack, RenderType renderType,
                                         SubmitNodeCollector.CustomGeometryRenderer customGeometryRenderer) {
            // 地图 / 特殊模型等直接走自定义几何的路径不染色（原版就是这样提交的），保持原样。
            this.delegate.submitCustomGeometry(poseStack, renderType, customGeometryRenderer);
        }

        @Override
        public void submitQuadParticleGroup(QuadParticleRenderState particles) {
            this.delegate.submitQuadParticleGroup(particles);
        }

        @Override
        public void submitGizmoPrimitives(DrawableGizmoPrimitives.Group group, CameraRenderState camera, boolean onTop) {
            this.delegate.submitGizmoPrimitives(group, camera, onTop);
        }
    }
}
