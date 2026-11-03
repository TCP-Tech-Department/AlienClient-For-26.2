package dev.luminous.api.utils.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.luminous.api.utils.Wrapper;
import net.minecraft.gizmos.Gizmo;
import net.minecraft.gizmos.GizmoPrimitives;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.awt.*;

/**
 * 实体底部的「果冻」波浪光环。
 * <p>
 * 26.2 已移除 Tessellator / BufferRenderer 的即时绘制路径，
 * 这里改为向 {@code GizmoCollector} 提交一个自定义 gizmo（相邻扇区拼成渐变带）。
 * {@code matrix} 参数保留以兼容旧签名，gizmo 直接使用世界坐标。
 */
public class JelloUtil implements Wrapper {
    private static float prevCircleStep;
    private static float circleStep;

    private static final int SEGMENTS = 30;

    public static void drawJello(PoseStack matrix, Entity target, Color color) {
        float delta = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        double cs = prevCircleStep + (circleStep - prevCircleStep) * delta;
        double prevSinAnim = absSinAnimation(cs - 0.45f);
        double sinAnim = absSinAnimation(cs);

        AABB box = target.getBoundingBox();
        double x = target.xo + (target.getX() - target.xo) * delta;
        double y = target.yo + (target.getY() - target.yo) * delta;
        double z = target.zo + (target.getZ() - target.zo) * delta;

        double baseY = y + prevSinAnim * target.getBbHeight();
        double nextY = y + sinAnim * target.getBbHeight();
        double radius = ((box.maxX - box.minX) + (box.maxZ - box.minZ)) * 0.5;

        try (Gizmos.TemporaryCollection ignored = mc.levelRenderer.collectPerFrameRenderThreadGizmos()) {
            Gizmos.addGizmo(new JelloGizmo(new Vec3(x, baseY, z), new Vec3(x, nextY, z), radius, color.getRGB()))
                    .setAlwaysOnTop();
        }
    }

    public static void updateJello() {
        prevCircleStep = circleStep;
        circleStep += 0.15f;
    }

    private static double absSinAnimation(double input) {
        return Math.abs(1 + Math.sin(input)) / 2;
    }

    /**
     * 环形渐变带：底圈使用实体色，顶圈透明，形成柔和的光晕。
     */
    private record JelloGizmo(Vec3 bottomCenter, Vec3 topCenter, double radius, int color) implements Gizmo {
        @Override
        public void emit(GizmoPrimitives primitives, float alphaMultiplier) {
            // 单色 quad 无法逐顶点渐变：把环带拆成上下两段，上段用半透明色近似淡出
            int midColor = ColorUtil.injectAlpha(color, (color >>> 24) / 2);
            double midY = (bottomCenter.y + topCenter.y) * 0.5;

            for (int i = 0; i < SEGMENTS; i++) {
                double a0 = i * 2.0 * Math.PI / SEGMENTS;
                double a1 = (i + 1) * 2.0 * Math.PI / SEGMENTS;

                Vec3 bottomA = new Vec3(bottomCenter.x + Math.cos(a0) * radius, bottomCenter.y, bottomCenter.z + Math.sin(a0) * radius);
                Vec3 bottomB = new Vec3(bottomCenter.x + Math.cos(a1) * radius, bottomCenter.y, bottomCenter.z + Math.sin(a1) * radius);
                Vec3 midA = new Vec3(topCenter.x + Math.cos(a0) * radius, midY, topCenter.z + Math.sin(a0) * radius);
                Vec3 midB = new Vec3(topCenter.x + Math.cos(a1) * radius, midY, topCenter.z + Math.sin(a1) * radius);
                Vec3 topA = new Vec3(topCenter.x + Math.cos(a0) * radius, topCenter.y, topCenter.z + Math.sin(a0) * radius);
                Vec3 topB = new Vec3(topCenter.x + Math.cos(a1) * radius, topCenter.y, topCenter.z + Math.sin(a1) * radius);

                primitives.addQuad(bottomA, midA, midB, bottomB, color);
                primitives.addQuad(midA, topA, topB, midB, midColor);
            }
        }
    }
}
