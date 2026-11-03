package dev.luminous.asm.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.luminous.api.utils.render.ModelPlayer;
import dev.luminous.mod.modules.impl.render.Chams;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.object.crystal.EndCrystalModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.client.renderer.entity.EnderDragonRenderer;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.*;

/**
 * 26.2 {@code EndCrystalEntityRenderer}（现名 {@code EndCrystalRenderer}）迁移。
 * <p>
 * 旧实现在渲染器里直接持有 core/frame/bottom 三个 {@code ModelPart} 与 RenderLayer 常量；
 * 26.2 把这些都收进了 {@code EndCrystalModel}（base / outerGlass / innerGlass / cube 嵌套），
 * 渲染改为 {@code submit(state, PoseStack, SubmitNodeCollector, CameraRenderState)} 提交节点模型，
 * RenderLayer / ItemRenderer#getDirectItemGlintConsumer / RenderSystem 即时模式全部移除。
 * <p>
 * 迁移要点：
 * <ul>
 *   <li>模型姿态由 {@code EndCrystalModel#setupAnim(state)} 生成（自转 + 上下浮动 + 45° 倾角），
 *       不再需要手工堆 Quaternionf，垂直浮动差异用整模平移补偿。</li>
 *   <li>逐部件上色改用 {@code submitModelPart(..., tintedColor, ...)}；
 *       为了保持「外框 / 内框 / 核心」各自颜色，用 {@code ModelPart#skipDraw} 分层提交。</li>
 *   <li>Chams 的 fill/line 叠加层默认仍交给 {@link ModelPlayer}（gizmos），
 *       但 gizmo 用世界坐标，因此单独构造一份平移到实体世界坐标的 PoseStack；
 *       {@code Chams.depth} 打开时改走提交式自定义几何（见 {@code alien$submitChams}）。</li>
 *   <li>Glint 用 26.2 的 {@code RenderTypes.entityGlint()} 再提交一遍模型
 *       （官方 {@code ThrownTridentRenderer}/{@code EquipmentLayerRenderer} 就是这个写法）。</li>
 *   <li>SpinSpeed 用「缩放喂给模型的 ageInTicks」实现（自转角 = ageInTicks * 3 写死在模型里），
 *       浮动量按缩放后的 age 反算补偿，保证自转倍速不影响浮动节奏。</li>
 *   <li>光束原本由 renderer 在自己的 submit 里绘制，取消 submit 后需要自己补上。</li>
 * </ul>
 */
@Mixin(EndCrystalRenderer.class)
public class MixinEndCrystalEntityRenderer {

    @Unique
    private static final Identifier ALIEN$CRYSTAL_TEXTURE =
            Identifier.withDefaultNamespace("textures/entity/end_crystal/end_crystal.png");
    @Unique
    private static final Identifier ALIEN$BLANK_TEXTURE =
            Identifier.withDefaultNamespace("textures/blank.png");

    @Shadow
    @Final
    private EndCrystalModel model;

    @Unique
    private float yOffset(float age, Chams module) {
        float f = age * module.floatValue.getValueFloat();
        float g = Mth.sin(f * 0.2F) / 2.0F + 0.5F;
        g = (g * g + g) * 0.4F * module.bounceHeight.getValueFloat();
        return g - 1.4F + module.floatOffset.getValueFloat();
    }

    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    public void submit(EndCrystalRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
                       CameraRenderState camera, CallbackInfo ci) {
        Chams module = Chams.INSTANCE;
        if (!module.customCrystal()) {
            return;
        }
        ci.cancel();

        float ageInTicks = module.spinSync.getValue()
                ? module.age + (state.ageInTicks - Mth.floor(state.ageInTicks))
                : state.ageInTicks;

        // 26.2: 自转角在 EndCrystalModel#setupAnim 里写死为 ageInTicks * 3.0F，
        // 所以「自转倍速」只能通过缩放喂进模型的 ageInTicks 实现（旧的 * spinValue 直接乘在角度上）。
        // setupAnim 同时用同一个 ageInTicks 算浮动 getY(age)，因此浮动量要按缩放后的 age 反算补偿，
        // 否则改自转速度会连带改上下浮动节奏。
        float spinAge = ageInTicks * module.spinValue.getValueFloat();
        float deltaY = yOffset(ageInTicks, module) - EndCrystalRenderer.getY(spinAge);

        // 26.2: 模型姿态（自转 / 浮动 / 45° 倾角）统一由模型自己算
        float previousAge = state.ageInTicks;
        state.ageInTicks = spinAge;
        model.setupAnim(state);
        state.ageInTicks = previousAge;

        RenderType renderType = RenderTypes.entityTranslucent(
                module.texture.getValue() ? ALIEN$CRYSTAL_TEXTURE : ALIEN$BLANK_TEXTURE);
        int light = state.lightCoords;
        int overlay = OverlayTexture.NO_OVERLAY;
        boolean showsBottom = state.showsBottom;

        poseStack.pushPose();
        poseStack.scale(2.0F * module.scale.getValueFloat(), 2.0F * module.scale.getValueFloat(), 2.0F * module.scale.getValueFloat());
        poseStack.translate(0.0F, -0.5F, 0.0F);
        poseStack.translate(0.0F, deltaY, 0.0F);

        // 底坐 + 外框：只画 base / outerGlass 自己的 cube
        model.base.visible = showsBottom;
        model.outerGlass.visible = module.outerFrame.booleanValue;
        model.outerGlass.skipDraw = !module.outerFrame.booleanValue;
        model.innerGlass.visible = false;
        model.innerGlass.skipDraw = false;
        model.cube.visible = false;
        submitPart(submitNodeCollector, poseStack, renderType, light, overlay, module.outerFrame.getValue().getRGB());

        // 内框：外框仅作为变换链保留（skipDraw），只画 innerGlass
        model.base.visible = false;
        model.outerGlass.visible = true;
        model.outerGlass.skipDraw = true;
        model.innerGlass.visible = module.innerFrame.booleanValue;
        model.innerGlass.skipDraw = !module.innerFrame.booleanValue;
        submitPart(submitNodeCollector, poseStack, renderType, light, overlay, module.innerFrame.getValue().getRGB());

        // 核心：外框/内框仅保留变换链，只画 cube
        model.innerGlass.visible = true;
        model.innerGlass.skipDraw = true;
        model.cube.visible = module.core.booleanValue;
        submitPart(submitNodeCollector, poseStack, renderType, light, overlay, module.core.getValue().getRGB());

        // Chams 自定义叠加层（fill / line）。两条路径互斥：
        //  - depth=false（默认，旧的 disableDepthTest）：gizmo + setAlwaysOnTop（alwaysOnTop 阶段会 clearDepth，故穿墙）
        //  - depth=true（旧的 enableDepthTest）：提交式自定义几何，管线 DEBUG_FILLED_BOX / LINES
        //    （DepthStencilState(GREATER_THAN_OR_EQUAL, depthWrite=false)），与 gizmo 同一条管线，只是留在正常深度阶段
        if (module.custom.getValue()) {
            if (module.depth.getValue()) {
                alien$submitChams(submitNodeCollector, poseStack, module);
            }
        }

        // 还原，避免影响原版提交路径
        model.base.visible = showsBottom;
        model.outerGlass.visible = true;
        model.outerGlass.skipDraw = false;
        model.innerGlass.visible = true;
        model.innerGlass.skipDraw = false;
        model.cube.visible = true;

        // Glint：旧 ItemRenderer#getDirectItemGlintConsumer(..., glint) 在 26.2 的等价物是
        // 用 RenderTypes.entityGlint() 再把同一份模型提交一遍（官方 ThrownTridentRenderer 写法）。
        // 该管线是 CompareOp.EQUAL + 不写深度，正好叠在刚画好的模型上。
        if (module.glint.getValue()) {
            submitNodeCollector.submitModelPart(model.root(), poseStack, RenderTypes.entityGlint(), light, overlay, null);
        }

        poseStack.popPose();

        // Chams 自定义 fill / line 叠加层（gizmos，世界坐标，所以单独构造平移到实体位置的栈）
        if (module.custom.getValue() && !module.depth.getValue()) {
            PoseStack gizmoStack = new PoseStack();
            gizmoStack.translate(state.x, state.y, state.z);
            gizmoStack.scale(2.0F * module.scale.getValueFloat(), 2.0F * module.scale.getValueFloat(), 2.0F * module.scale.getValueFloat());
            gizmoStack.translate(0.0F, -0.5F, 0.0F);
            gizmoStack.translate(0.0F, deltaY, 0.0F);
            if (module.outerFrame.booleanValue) {
                ModelPlayer.render(gizmoStack, model.outerGlass, module.fill, module.line, 1, module.chamsTexture.getValue());
            }
            if (module.innerFrame.booleanValue) {
                ModelPlayer.render(gizmoStack, model.innerGlass, module.fill, module.line, 1, module.chamsTexture.getValue());
            }
            if (module.core.booleanValue) {
                ModelPlayer.render(gizmoStack, model.cube, module.fill, module.line, 1, module.chamsTexture.getValue());
            }
        }

        // 光束：原实现由 renderer 在模型之后提交，取消 submit 后要自己补
        Vec3 beamOffset = state.beamOffset;
        if (beamOffset != null) {
            poseStack.pushPose();
            poseStack.translate(beamOffset);
            EnderDragonRenderer.submitCrystalBeams(
                    (float) -beamOffset.x,
                    // 光束要接在「实际画出来的」水晶顶部：浮动已被 yOffset/BounceHeight 改写，
                    // 与 1.21.1 原实现一致（旧代码传的也是 yOffset 的结果，而不是原版 getY）
                    (float) -beamOffset.y + yOffset(ageInTicks, module),
                    (float) -beamOffset.z,
                    ageInTicks,
                    poseStack,
                    submitNodeCollector,
                    state.lightCoords
            );
            poseStack.popPose();
        }
    }

    @Unique
    private void submitPart(SubmitNodeCollector collector, PoseStack poseStack, RenderType renderType,
                            int light, int overlay, int color) {
        collector.submitModelPart(model.root(), poseStack, renderType, light, overlay, null, color, null, 0);
    }

    /**
     * {@code Chams.depth = true} 的叠加层：与 gizmo 版同样的几何体，改用提交式自定义几何 +
     * 26.2 里带深度测试的彩色几何管线。
     * <p>
     * 26.2 没有 {@code RenderSystem.enableDepthTest/depthMask}，也没有「无深度测试」的实体 RenderType；
     * 深度开关已经完全下沉到 {@code RenderPipeline}：
     * <ul>
     *   <li>{@code RenderPipelines.DEBUG_FILLED_BOX} / {@code LINES}：深度测试开、深度写关 —— 与 gizmo
     *       内部使用的管线完全相同，因此视觉与默认路径一致，只是不做 alwaysOnTop；</li>
     *   <li>alwaysOnTop 的穿墙效果来自 {@code LevelRenderer#addAlwaysOnTopPass} 里的
     *       {@code clearDepthTexture}，而不是管线本身。</li>
     * </ul>
     * 顶点变换与 {@code ModelFeatureRenderer} 一致：把提交时捕获的 {@code PoseStack.Pose} 拷进一个新的
     * PoseStack，再用 {@code ModelPart#visit} 拿到每个 cube 的最终 pose。
     */
    @Unique
    private void alien$submitChams(SubmitNodeCollector collector, PoseStack poseStack, Chams module) {
        if (module.outerFrame.booleanValue) alien$submitChamsPart(collector, poseStack, model.outerGlass, module);
        if (module.innerFrame.booleanValue) alien$submitChamsPart(collector, poseStack, model.innerGlass, module);
        if (module.core.booleanValue) alien$submitChamsPart(collector, poseStack, model.cube, module);
    }

    @Unique
    private void alien$submitChamsPart(SubmitNodeCollector collector, PoseStack poseStack, ModelPart part, Chams module) {
        if (!part.visible) return;

        if (module.fill.booleanValue) {
            Color color = module.fill.getValue();
            int argb = color.getRGB();
            collector.submitCustomGeometry(poseStack, RenderTypes.debugFilledBox(), (pose, buffer) -> {
                PoseStack local = new PoseStack();
                local.last().set(pose);
                part.visit(local, (cubePose, path, index, cube) -> {
                    for (ModelPart.Polygon polygon : cube.polygons) {
                        ModelPart.Vertex[] vertices = polygon.vertices();
                        if (vertices.length < 4) continue;
                        for (ModelPart.Vertex vertex : vertices) {
                            buffer.addVertex(cubePose, vertex.worldX(), vertex.worldY(), vertex.worldZ()).setColor(argb);
                        }
                    }
                });
            });
        }

        if (module.line.booleanValue) {
            Color color = module.line.getValue();
            int argb = color.getRGB();
            float width = 1.5f;
            collector.submitCustomGeometry(poseStack, RenderTypes.lines(), (pose, buffer) -> {
                PoseStack local = new PoseStack();
                local.last().set(pose);
                part.visit(local, (cubePose, path, index, cube) -> {
                    for (ModelPart.Polygon polygon : cube.polygons) {
                        ModelPart.Vertex[] vertices = polygon.vertices();
                        if (vertices.length < 2) continue;
                        for (int i = 0; i < vertices.length; i++) {
                            ModelPart.Vertex from = vertices[i];
                            ModelPart.Vertex to = vertices[(i + 1) % vertices.length];
                            float dx = to.worldX() - from.worldX();
                            float dy = to.worldY() - from.worldY();
                            float dz = to.worldZ() - from.worldZ();
                            buffer.addVertex(cubePose, from.worldX(), from.worldY(), from.worldZ())
                                    .setColor(argb).setNormal(cubePose, dx, dy, dz).setLineWidth(width);
                            buffer.addVertex(cubePose, to.worldX(), to.worldY(), to.worldZ())
                                    .setColor(argb).setNormal(cubePose, dx, dy, dz).setLineWidth(width);
                        }
                    }
                });
            });
        }
    }
}
