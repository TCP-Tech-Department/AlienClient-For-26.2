package dev.luminous.api.utils.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.luminous.api.utils.Wrapper;
import net.minecraft.gizmos.CuboidGizmo;
import net.minecraft.gizmos.Gizmo;
import net.minecraft.gizmos.GizmoPrimitives;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.LineGizmo;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.awt.*;

/**
 * 26.2 渲染层。
 * <p>
 * 1.21.1 的即时模式（Tessellator + BufferRenderer.drawWithGlobalProgram + RenderSystem.setShader）
 * 在 26.2 已被整体移除。26.2 改为「提交节点」模型：把几何体提交给
 * {@link net.minecraft.gizmos.GizmoCollector}，由 {@code LevelRenderer} 在下一帧的
 * extract 阶段统一定型并合批绘制。
 * <p>
 * 因此本类保留原有对外签名（19 个调用方零改动），内部改为提交 {@code Gizmo}。
 * 语义差异：
 * <ul>
 *   <li>不再需要相机相对坐标：gizmo 直接使用世界坐标，无需自行减去 camera 位置。</li>
 *   <li>不再需要手工 {@code PoseStack} 变换：原 {@code matrixFrom} 已废弃。</li>
 *   <li>深度测试开关映射为 {@link GizmoProperties#setAlwaysOnTop()}。</li>
 *   <li>提交发生在渲染线程时，最迟延迟 1 帧生效。</li>
 * </ul>
 */
public class Render3DUtil implements Wrapper {

    /**
     * 把 gizmo 提交到当前帧的世界渲染收集器。
     * <p>
     * {@code collectPerFrameRenderThreadGizmos()} 会临时替换线程内的收集器，
     * 并在 close 时恢复外层收集器，因此可在渲染过程中安全嵌套调用。
     */
    private static GizmoProperties submit(Gizmo gizmo) {
        return submit(gizmo, true);
    }

    private static GizmoProperties submit(Gizmo gizmo, boolean alwaysOnTop) {
        try (Gizmos.TemporaryCollection ignored = mc.levelRenderer.collectPerFrameRenderThreadGizmos()) {
            GizmoProperties properties = Gizmos.addGizmo(gizmo);
            if (alwaysOnTop) {
                properties.setAlwaysOnTop();
            }
            return properties;
        }
    }

    // ------------------------------------------------------------------
    // 方框
    // ------------------------------------------------------------------

    public static void drawFill(AABB bb, Color fillColor) {
        draw3DBox(bb, fillColor, new Color(0, 0, 0, 0), false, true);
    }

    public static void drawBox(AABB bb, Color outlineColor) {
        draw3DBox(bb, new Color(0, 0, 0, 0), outlineColor, true, false);
    }

    public static void drawBox(AABB bb, Color outlineColor, float lineWidth) {
        draw3DBox(bb, new Color(0, 0, 0, 0), outlineColor, true, false, lineWidth);
    }

    public static void draw3DBox(AABB box, Color fillColor, Color outlineColor) {
        draw3DBox(box, fillColor, outlineColor, true, true);
    }

    public static void draw3DBox(AABB box, Color fillColor, Color outlineColor, boolean outline, boolean fill) {
        draw3DBox(box, fillColor, outlineColor, outline, fill, 1.5f);
    }

    public static void draw3DBox(AABB box, Color fillColor, Color outlineColor, boolean outline, boolean fill, float lineWidth) {
        // GizmoStyle(stroke, strokeWidth, fill)：stroke/fill 为 0 时对应项不绘制，
        // 与旧的 outline / fill 开关语义一致（旧代码用 new Color(0,0,0,0) 关闭）。
        int stroke = outline ? outlineColor.getRGB() : 0;
        float width = outline ? lineWidth : 0.0f;
        int fillArgb = fill ? fillColor.getRGB() : 0;
        submit(new CuboidGizmo(box, new GizmoStyle(stroke, width, fillArgb), false));
    }

    /**
     * 上下渐变填充立方体。
     * <p>
     * gizmo 的 addQuad 只接受单色，因此把四个侧面各拆成上下两段来近似原渐变效果。
     */
    public static void drawFadeFill(AABB box, Color bottom, Color top) {
        submit(new GradientCuboidGizmo(box, bottom.getRGB(), top.getRGB()));
    }

    // ------------------------------------------------------------------
    // 线条
    // ------------------------------------------------------------------

    public static void drawLine(Vec3 start, Vec3 end, Color color) {
        drawLine(start.x, start.y, start.z, end.x, end.y, end.z, color, 1.0f);
    }

    public static void drawLine(double x1, double y1, double z1, double x2, double y2, double z2, Color color, float width) {
        submit(new LineGizmo(new Vec3(x1, y1, z1), new Vec3(x2, y2, z2), color.getRGB(), width));
    }

    // ------------------------------------------------------------------
    // 文字
    // ------------------------------------------------------------------

    public static void drawText3D(String text, Vec3 vec3d, Color color) {
        drawText3D(Component.literal(text), vec3d.x, vec3d.y, vec3d.z, 0, 0, 1, color.getRGB());
    }

    public static void drawText3D(String text, Vec3 vec3d, int color) {
        drawText3D(Component.literal(text), vec3d.x, vec3d.y, vec3d.z, 0, 0, 1, color);
    }

    public static void drawText3D(Component text, Vec3 vec3d, double offX, double offY, double scale, Color color) {
        drawText3D(text, vec3d.x, vec3d.y, vec3d.z, offX, offY, scale, color.getRGB());
    }

    public static void drawText3D(Component text, double x, double y, double z, double offX, double offY, double scale, int color) {
        // 旧实现按 -0.025 * scale 缩放字形；26.2 TextGizmo 的世界缩放基准是 0.32，
        // 这里按同等视觉比例换算，运行时若偏大/偏小只需调 TEXT_SCALE_BASE。
        float worldScale = (float) (scale * TEXT_SCALE_BASE);
        submit(new TextGizmo(
                new Vec3(x + offX, y + offY, z),
                text.getString(),
                TextGizmo.Style.forColorAndCentered(color).withScale(worldScale)
        ));
    }

    /** 26.2 TextGizmo 的世界缩放基准，对应旧版 -0.025 * scale 的视觉大小。 */
    private static final double TEXT_SCALE_BASE = 0.32;

    /**
     * 动画相位基准时间。
     * <p>
     * 旧实现读 {@code Alien.initTime}，这使渲染层反向依赖 Mod 入口；
     * 改为渲染层自持的类加载时间，行为等价且让渲染层保持零外部依赖。
     */
    private static final long ANIMATION_EPOCH = System.currentTimeMillis();

    // ------------------------------------------------------------------
    // 目标光环 ESP（ThunderHack 风格）
    // ------------------------------------------------------------------

    /**
     * 三层螺旋光环。
     * <p>
     * {@code stack} 参数仅为保持旧签名（Aura / AutoCrystal 仍在传），26.2 下不再使用：
     * gizmo 使用世界坐标，不需要手工矩阵。
     */
    public static void drawTargetEsp(PoseStack stack, Entity target, Color color) {
        float delta = getTickDelta();
        double x = target.xo + (target.getX() - target.xo) * delta;
        double y = target.yo + (target.getY() - target.yo) * delta;
        double z = target.zo + (target.getZ() - target.zo) * delta;

        double radius = Math.max(target.getBbWidth(), 0.6f) * 0.5;
        float height = target.getBbHeight();
        float phase = ((System.currentTimeMillis() - ANIMATION_EPOCH) / 5.0f) % 360.0f;

        Vec3 center = new Vec3(x, y, z);
        int argb = color.getRGB();

        submit(new RingGizmo(center, radius, height, 0.004f, argb, phase));
        submit(new RingGizmo(center, radius, height, 0.004f, argb, phase + 120.0f));
        submit(new RingGizmo(center, radius, height, 0.004f, argb, phase + 240.0f));
    }

    // ------------------------------------------------------------------
    // 时间
    // ------------------------------------------------------------------

    public static float getTickDelta() {
        return mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
    }

    // ------------------------------------------------------------------
    // 自定义 Gizmo 实现
    // ------------------------------------------------------------------

    /** 沿圆周螺旋下降的亮带，对应旧版 TRIANGLE_STRIP 光环。 */
    private record RingGizmo(Vec3 center, double radius, float startHeight, float heightStep,
                             int baseColor, float phase) implements Gizmo {
        private static final int SEGMENTS = 360;

        @Override
        public void emit(GizmoPrimitives primitives, float alphaMultiplier) {
            Vec3 prev = null;
            for (int i = 0; i <= SEGMENTS; i++) {
                double angle = Math.toRadians(i);
                double height = startHeight - i * (double) heightStep;
                Vec3 cur = new Vec3(
                        center.x + Math.cos(angle) * radius,
                        center.y + height,
                        center.z + Math.sin(angle) * radius
                );
                if (prev != null) {
                    // 与旧实现一致：每 60 段为一个亮带周期，随时间平移
                    float alpha = 1.0f - ((i + phase) % 360.0f) / 60.0f;
                    alpha = Math.max(0.0f, Math.min(1.0f, alpha)) * alphaMultiplier;
                    primitives.addLine(prev, cur, ColorUtil.injectAlpha(baseColor, (int) (alpha * 255.0f)), 2.0f);
                }
                prev = cur;
            }
        }
    }

    /** 上下渐变的立方体填充：四个侧面各拆成两段，用单色 quad 近似渐变。 */
    private record GradientCuboidGizmo(AABB box, int bottomColor, int topColor) implements Gizmo {
        @Override
        public void emit(GizmoPrimitives primitives, float alphaMultiplier) {
            double x0 = box.minX, y0 = box.minY, z0 = box.minZ;
            double x1 = box.maxX, y1 = box.maxY, z1 = box.maxZ;
            double ym = (y0 + y1) * 0.5;

            int bottom = ColorUtil.injectAlpha(bottomColor, (int) ((bottomColor >>> 24) * alphaMultiplier));
            int top = ColorUtil.injectAlpha(topColor, (int) ((topColor >>> 24) * alphaMultiplier));

            // 侧面下段
            primitives.addQuad(new Vec3(x0, y0, z0), new Vec3(x0, ym, z0), new Vec3(x1, ym, z0), new Vec3(x1, y0, z0), bottom);
            primitives.addQuad(new Vec3(x1, y0, z0), new Vec3(x1, ym, z0), new Vec3(x1, ym, z1), new Vec3(x1, y0, z1), bottom);
            primitives.addQuad(new Vec3(x1, y0, z1), new Vec3(x1, ym, z1), new Vec3(x0, ym, z1), new Vec3(x0, y0, z1), bottom);
            primitives.addQuad(new Vec3(x0, y0, z1), new Vec3(x0, ym, z1), new Vec3(x0, ym, z0), new Vec3(x0, y0, z0), bottom);
            // 侧面上段
            primitives.addQuad(new Vec3(x0, ym, z0), new Vec3(x0, y1, z0), new Vec3(x1, y1, z0), new Vec3(x1, ym, z0), top);
            primitives.addQuad(new Vec3(x1, ym, z0), new Vec3(x1, y1, z0), new Vec3(x1, y1, z1), new Vec3(x1, ym, z1), top);
            primitives.addQuad(new Vec3(x1, ym, z1), new Vec3(x1, y1, z1), new Vec3(x0, y1, z1), new Vec3(x0, ym, z1), top);
            primitives.addQuad(new Vec3(x0, ym, z1), new Vec3(x0, y1, z1), new Vec3(x0, y1, z0), new Vec3(x0, ym, z0), top);
            // 底面 / 顶面
            primitives.addQuad(new Vec3(x0, y0, z0), new Vec3(x1, y0, z0), new Vec3(x1, y0, z1), new Vec3(x0, y0, z1), bottom);
            primitives.addQuad(new Vec3(x0, y1, z0), new Vec3(x0, y1, z1), new Vec3(x1, y1, z1), new Vec3(x1, y1, z0), top);
        }
    }
}
