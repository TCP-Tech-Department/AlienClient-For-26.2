package dev.luminous.api.utils.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.luminous.api.utils.math.MathUtil;
import dev.luminous.mod.modules.settings.impl.ColorSetting;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.gizmos.Gizmo;
import net.minecraft.gizmos.GizmoPrimitives;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static dev.luminous.api.utils.Wrapper.mc;

/**
 * 26.2 模型渲染器。
 * <p>
 * 使用 Gizmo 体系替代旧的 BufferBuilder 即时模式。
 * 通过 {@link ModelPart#visit} 遍历模型部件，把每个 Cube 的顶点提前烘焙进 Gizmo，
 * 再由 {@code LevelRenderer} 在 extract 阶段统一定型并合批绘制。
 * <p>
 * 与 1.21.1 的语义差异：
 * <ul>
 *   <li>gizmo 使用<b>世界坐标</b>，不再减去相机位置（旧实现传的是相机相对坐标）。</li>
 *   <li>{@link ModelPart#visit} 不检查 {@code visible}，因此这里用部件名查表补上可见性判断，
 *       否则 PlayerModel 里被隐藏的 jacket / sleeve / pants 会被画出来。</li>
 *   <li>PoseStack 只在提交时有效（gizmo 是延迟绘制），所以矩阵在提交时快照，不能持有活的 PoseStack。</li>
 * </ul>
 */
public class ModelPlayer {
    public final Player player;
    private final PlayerModel model;

    public ModelPlayer(Player player) {
        this.player = player;
        ModelPart root = mc.getEntityModels().bakeLayer(net.minecraft.client.model.geom.ModelLayers.PLAYER);
        this.model = new PlayerModel(root, false);

        model.leftPants.visible = false;
        model.rightPants.visible = false;
        model.leftSleeve.visible = false;
        model.rightSleeve.visible = false;
        model.jacket.visible = false;
        model.hat.visible = false;

        model.head.xScale = 0.95f;
        model.head.yScale = 0.95f;
        model.head.zScale = 0.95f;
    }

    /**
     * 26.2: 深度开关不再是 {@code RenderSystem.disableDepthTest()}，而是「提交到哪条 gizmo 阶段」。
     *
     * <p><b>为什么是 {@code alwaysOnTop} 而不是 {@code depthTest}</b>：
     * {@link GizmoProperties} 只有 3 个方法（{@code setAlwaysOnTop()} /
     * {@code persistForMillis(int)} / {@code fadeOut()}），没有 depth 相关 API；
     * 深度由「是否 alwaysOnTop」隐含决定：
     * <ul>
     *   <li>{@code SimpleGizmoCollector.GizmoInstance.isAlwaysOnTop} 默认 <b>false</b>；</li>
     *   <li>{@code LevelRenderer#finalizeGizmoCollection} 按该标志把 gizmo 分进
     *       {@code standardPrimitives} / {@code alwaysOnTopPrimitives}；</li>
     *   <li>standard → {@code SubmitNodeCollection#gizmos} 阶段，在
     *       {@code FeatureRenderDispatcher.PreparedFrame#executeTranslucent()} 里、主 pass 内执行，
     *       管线为 {@code RenderTypes.debugFilledBox()}（{@code DEBUG_FILLED_BOX}，
     *       {@code DepthStencilState(GREATER_THAN_OR_EQUAL, depthWrite=false)}）与
     *       {@code RenderTypes.lines()} —— <b>深度测试开</b>，会被地形遮挡；</li>
     *   <li>alwaysOnTop → {@code SubmitNodeCollection#alwaysOnTop} 阶段，由
     *       {@code LevelRenderer#addAlwaysOnTopPass} 执行，该 pass 先
     *       {@code clearDepthTexture}（{@code LevelRenderer.java:501}），所以穿透地形。</li>
     * </ul>
     * 也就是说 gizmo 管线<b>本来就支持</b>「不画在最上层」（默认值就是 false），
     * 之前这里无条件 {@code setAlwaysOnTop()} 才是把 1.21.1 {@code disableDepthTest} 的语义
     * 硬编码到唯一路径上。旧重载保持 true（等价 1.21.1 的 {@code ModelPlayer} 内部
     * {@code RenderSystem.disableDepthTest()}），需要真实深度的调用方走带参重载。
     */
    public void render(PoseStack matrices, ColorSetting fill, ColorSetting line) {
        render(matrices, fill, line, 1, 0, 1, 0, false, false, true);
    }

    /** 兼容重载：保持 1.21.1 的「不测深度」行为（{@code RenderSystem.disableDepthTest()}）。 */
    public void render(PoseStack matrices, ColorSetting fill, ColorSetting line, double alpha, double yOffset, double scale, double yaw, boolean noLimb, boolean forceSneaking) {
        render(matrices, fill, line, alpha, yOffset, scale, yaw, noLimb, forceSneaking, true);
    }

    /**
     * @param alwaysOnTop true = 提交到 {@code always_on_top} 阶段（穿墙，等价旧 {@code disableDepthTest}）；
     *                    false = 留在普通深度阶段（{@code DEBUG_FILLED_BOX} / {@code LINES}，会被方块遮挡）。
     */
    public void render(PoseStack matrices, ColorSetting fill, ColorSetting line, double alpha, double yOffset, double scale, double yaw, boolean noLimb, boolean forceSneaking, boolean alwaysOnTop) {
        // 26.2: gizmo 用世界坐标，直接用实体坐标，不再做 camera 相对偏移。
        double x = player.getX();
        double y = player.getY() + yOffset;
        double z = player.getZ();

        matrices.pushPose();
        matrices.translate(x, y, z);
        matrices.mulPose(new org.joml.Quaternionf().rotationY(MathUtil.rad(180 - player.yBodyRot + (float) yaw)));

        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);

        model.head.xRot = player.getXRot() * Mth.DEG_TO_RAD;
        model.head.yRot = (player.yHeadRot - player.yBodyRot) * Mth.DEG_TO_RAD;

        float swingProgress = player.getAttackAnim(partialTick);
        if (swingProgress > 0) {
            float f = Mth.sin(swingProgress * (float) Math.PI);
            model.rightArm.xRot -= f * 0.5f;
        }

        matrices.scale(-1.0F, -1.0F, 1.0F);
        matrices.translate(0.0F, -1.401F, 0.0F);
        matrices.scale((float) scale * .93f, (float) scale * .93f, (float) scale * .93f);

        renderPart(matrices, model.root(), fill, line, alpha, alwaysOnTop);

        matrices.popPose();
    }

    /**
     * 静态渲染：用于末影水晶等传入单个 ModelPart 的场景。
     * <p>
     * 26.2: 调用方需要保证传入的 {@code matrices} 已经把几何体带到世界坐标
     * （gizmo 不做相机相对处理），详见 {@code MixinEndCrystalEntityRenderer}。
     * 旧重载默认 {@code alwaysOnTop = true}，与该调用点当前的 {@code Chams.depth=false} 分支一致；
     * {@code Chams.depth=true} 时 {@code MixinEndCrystalEntityRenderer} 走
     * {@code submitCustomGeometry} 的带深度路径，不经过这里。
     */
    public static void render(PoseStack matrices, ModelPart part, ColorSetting fill, ColorSetting line, double alpha, boolean texture) {
        render(matrices, part, fill, line, alpha, texture, true);
    }

    /** 与 {@link #render(PoseStack, ColorSetting, ColorSetting, double, double, double, double, boolean, boolean, boolean)} 的 {@code alwaysOnTop} 同义。 */
    public static void render(PoseStack matrices, ModelPart part, ColorSetting fill, ColorSetting line, double alpha, boolean texture, boolean alwaysOnTop) {
        if (!part.visible) return;
        renderPart(matrices, part, fill, line, alpha, alwaysOnTop);
    }

    /**
     * 核心渲染：把部件的几何体烘焙成 Gizmo 再提交。
     * <p>
     * 26.2 的 gizmo 是延迟提交的，因此这里必须在提交时就把矩阵快照下来，
     * 否则 {@code matrices} 在 emit 之前就被 pop 掉了。
     */
    private static void renderPart(PoseStack matrices, ModelPart part, ColorSetting fill, ColorSetting line, double alpha, boolean alwaysOnTop) {
        List<BakedCube> cubes = new ArrayList<>();
        Function<String, ModelPart> lookup = part.createPartLookup();
        part.visit(matrices, (pose, partPath, cubeIndex, cube) -> {
            String name = partPath.isEmpty() ? "root" : partPath.substring(partPath.lastIndexOf('/') + 1);
            ModelPart owner = lookup.apply(name);
            // visit() 不判断 visible，这里补上（PlayerModel 隐藏的 jacket/sleeve/pants 等）
            if (owner != null && !owner.visible) return;
            cubes.add(new BakedCube(new Matrix4f(pose.pose()), cube));
        });
        if (cubes.isEmpty()) return;

        try (Gizmos.TemporaryCollection ignored = mc.levelRenderer.collectPerFrameRenderThreadGizmos()) {
            GizmoProperties props = Gizmos.addGizmo(new PartGizmo(cubes, fill, line, alpha));
            // 26.2: GizmoProperties 没有任何深度 API，深度只由「进 standard 还是 always_on_top 阶段」决定，
            // 所以只能在需要时调用 setAlwaysOnTop()；不调用即留在带深度测试的普通阶段。
            if (alwaysOnTop) {
                props.setAlwaysOnTop();
            }
        }
    }

    /** 提交时快照下来的单个立方体。 */
    private record BakedCube(Matrix4f matrix, ModelPart.Cube cube) {
    }

    private record PartGizmo(List<BakedCube> cubes, ColorSetting fill, ColorSetting line, double alpha) implements Gizmo {
        @Override
        public void emit(GizmoPrimitives primitives, float alphaMultiplier) {
            if (!fill.booleanValue && !line.booleanValue) return;

            for (BakedCube baked : cubes) {
                for (ModelPart.Polygon polygon : baked.cube().polygons) {
                    ModelPart.Vertex[] verts = polygon.vertices();
                    if (verts.length < 4) continue;

                    Vec3 v1 = transformVertex(baked.matrix(), verts[0]);
                    Vec3 v2 = transformVertex(baked.matrix(), verts[1]);
                    Vec3 v3 = transformVertex(baked.matrix(), verts[2]);
                    Vec3 v4 = transformVertex(baked.matrix(), verts[3]);

                    if (fill.booleanValue) {
                        Color color = fill.getValue();
                        int argb = ColorUtil.injectAlpha(color.getRGB(), (int) (color.getAlpha() * alpha * alphaMultiplier));
                        primitives.addQuad(v1, v2, v3, v4, argb);
                    }

                    if (line.booleanValue) {
                        Color color = line.getValue();
                        int argb = ColorUtil.injectAlpha(color.getRGB(), (int) (color.getAlpha() * alpha * alphaMultiplier));
                        float width = 1.5f;
                        primitives.addLine(v1, v2, argb, width);
                        primitives.addLine(v2, v3, argb, width);
                        primitives.addLine(v3, v4, argb, width);
                        primitives.addLine(v4, v1, argb, width);
                    }
                }
            }
        }

        private static Vec3 transformVertex(Matrix4f matrix, ModelPart.Vertex vertex) {
            Vector4f pos = new Vector4f(vertex.worldX(), vertex.worldY(), vertex.worldZ(), 1.0f);
            pos.mul(matrix);
            return new Vec3(pos.x, pos.y, pos.z);
        }
    }
}
