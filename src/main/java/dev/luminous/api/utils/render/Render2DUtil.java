package dev.luminous.api.utils.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2fStack;

import java.awt.*;

/**
 * 2D 绘制工具（26.2）。
 * <p>
 * 26.2 移除了 {@code Tessellator} / {@code BufferBuilder} / {@code RenderSystem.setShader}
 * 的即时绘制路径，GUI 统一走 {@link GuiGraphicsExtractor}：
 * 所有绘制以「渲染状态」形式提交到 {@code GuiRenderState}，由 GUI 渲染阶段合批。
 * <p>
 * 因此本类不再接收矩阵栈，改为直接接收绘制上下文；坐标变换由调用方通过
 * {@code ctx.pose()}（{@link Matrix3x2fStack}）完成，{@code fill} 会自动应用当前 pose。
 * <p>
 * 与旧实现的差异（均为 26.2 API 限制，不是功能删减）：
 * <ul>
 *   <li>坐标是整数像素：{@code fill} 只接受 int，浮点坐标会被截断。</li>
 *   <li>{@code fillGradient} 只支持纵向渐变，横向渐变通过临时旋转坐标系实现。</li>
 *   <li>斜线用最小外接矩形近似（{@code fill} 无法画任意方向线段）。</li>
 * </ul>
 */
public class Render2DUtil {

    // ------------------------------------------------------------------
    // 基础矩形
    // ------------------------------------------------------------------

    public static void rect(GuiGraphicsExtractor ctx, float x1, float y1, float x2, float y2, int color) {
        rectFilled(ctx, x1, y1, x2, y2, color);
    }

    public static void rectFilled(GuiGraphicsExtractor ctx, float x1, float y1, float x2, float y2, int color) {
        if ((color >>> 24) <= 2) {
            return;
        }
        ctx.fill(floor(x1), floor(y1), ceil(x2), ceil(y2), color);
    }

    public static void drawRect(GuiGraphicsExtractor ctx, float x, float y, float width, float height, int c) {
        rectFilled(ctx, x, y, x + width, y + height, c);
    }

    public static void drawRect(GuiGraphicsExtractor ctx, float x, float y, float width, float height, Color c) {
        drawRect(ctx, x, y, width, height, c.getRGB());
    }

    public static void drawRectWithOutline(GuiGraphicsExtractor ctx, float x, float y, float width, float height, Color fill, Color outline) {
        rectFilled(ctx, x, y, x + width, y + height, fill.getRGB());
        outline(ctx, x, y, width, height, outline.getRGB());
    }

    public static void outline(GuiGraphicsExtractor ctx, float x, float y, float width, float height, int color) {
        if ((color >>> 24) <= 2) {
            return;
        }
        int x0 = floor(x);
        int y0 = floor(y);
        int x1 = ceil(x + width);
        int y1 = ceil(y + height);
        ctx.fill(x0, y0, x1, y0 + 1, color);
        ctx.fill(x0, y1 - 1, x1, y1, color);
        ctx.fill(x0, y0 + 1, x0 + 1, y1 - 1, color);
        ctx.fill(x1 - 1, y0 + 1, x1, y1 - 1, color);
    }

    public static void arrow(GuiGraphicsExtractor ctx, float x, float y, Color color) {
        drawRectWithOutline(ctx, x - 1f, y - 1f, 2, 2, color, Color.BLACK);
    }

    // ------------------------------------------------------------------
    // 线条
    // ------------------------------------------------------------------

    /**
     * 两点之间画线。
     * <p>
     * {@code fill} 只能画轴对齐矩形，这里用最小外接矩形近似；
     * 对水平/垂直线是精确的，对斜线会退化为细长矩形块（厚度 1px）。
     */
    public static void drawLine(GuiGraphicsExtractor ctx, float x, float y, float x1, float y1, int color) {
        if ((color >>> 24) <= 2) {
            return;
        }
        int x0 = floor(Math.min(x, x1));
        int y0 = floor(Math.min(y, y1));
        int xEnd = ceil(Math.max(x, x1));
        int yEnd = ceil(Math.max(y, y1));
        ctx.fill(x0, y0, Math.max(xEnd, x0 + 1), Math.max(yEnd, y0 + 1), color);
    }

    // ------------------------------------------------------------------
    // 渐变
    // ------------------------------------------------------------------

    public static void horizontalGradient(GuiGraphicsExtractor ctx, float x1, float y1, float x2, float y2, Color startColor, Color endColor) {
        horizontalGradient(ctx, x1, y1, x2, y2, startColor.getRGB(), endColor.getRGB());
    }

    /**
     * 横向渐变。
     * <p>
     * 26.2 的 {@code fillGradient} 只支持纵向（上→下），这里临时把坐标系旋转 90°，
     * 让纵向渐变在屏幕上呈现为横向。
     */
    public static void horizontalGradient(GuiGraphicsExtractor ctx, float x1, float y1, float x2, float y2, int startColor, int endColor) {
        float left = Math.min(x1, x2);
        float right = Math.max(x1, x2);
        float top = Math.min(y1, y2);
        float bottom = Math.max(y1, y2);

        Matrix3x2fStack pose = ctx.pose();
        pose.pushMatrix();
        // 绕矩形中心逆时针旋转 90°：旋转系的 +y 指向原 +x，
        // 于是 fillGradient 的「上→下」正好变成「左→右」
        pose.translate((left + right) * 0.5f, (top + bottom) * 0.5f);
        pose.rotate((float) (-Math.PI / 2));
        float halfWidth = (right - left) * 0.5f;
        float halfHeight = (bottom - top) * 0.5f;
        ctx.fillGradient(
                floor(-halfHeight), floor(-halfWidth),
                ceil(halfHeight), ceil(halfWidth),
                startColor, endColor
        );
        pose.popMatrix();
    }

    // ------------------------------------------------------------------
    // 光晕
    // ------------------------------------------------------------------

    /**
     * 中心亮、四周淡出的光晕。
     * <p>
     * 旧实现用 4 个三角形拼渐变；26.2 的 GUI 只能画轴对齐矩形，
     * 这里改用多层同心矩形逐层降低 alpha 来模拟。
     */
    public static void drawGlow(GuiGraphicsExtractor ctx, float x, float y, float width, float height, int color) {
        int layers = 6;
        int baseAlpha = color >>> 24;
        if (baseAlpha <= 2) {
            return;
        }
        for (int i = layers; i >= 1; i--) {
            float grow = i * 0.5f;
            int alpha = (int) (baseAlpha * (1.0f - (float) i / (layers + 1)) * 0.35f);
            if (alpha <= 1) {
                continue;
            }
            rectFilled(ctx, x - grow, y - grow, x + width + grow, y + height + grow,
                    ColorUtil.injectAlpha(color, alpha));
        }
    }

    // ------------------------------------------------------------------
    // 命中检测
    // ------------------------------------------------------------------

    public static boolean isHovered(double mouseX, double mouseY, double x, double y, double width, double height) {
        return mouseX >= x && mouseX - width <= x && mouseY >= y && mouseY - height <= y;
    }

    // ------------------------------------------------------------------
    // 坐标取整
    // ------------------------------------------------------------------

    private static int floor(float value) {
        return (int) Math.floor(value);
    }

    private static int ceil(float value) {
        return (int) Math.ceil(value);
    }
}
