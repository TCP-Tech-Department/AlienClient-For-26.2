package dev.luminous.api.utils.render;

import dev.luminous.api.utils.Wrapper;
import dev.luminous.core.impl.FontManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2fStack;

import java.awt.*;

/**
 * 文本绘制工具（26.2）。
 * <p>
 * 迁移要点：
 * <ul>
 *   <li>{@code DrawContext} → {@link GuiGraphicsExtractor}，矩阵由 3D {@code PoseStack}
 *       改为 2D {@link Matrix3x2fStack}（{@code ctx.pose()}）。</li>
 *   <li>{@code drawContext.drawText(...)} → {@code ctx.text(font, ...)}。</li>
 *   <li>{@code mc.font} → {@code mc.font}，{@code fontHeight} → {@code lineHeight}。</li>
 *   <li>世界坐标转屏幕：不再自己抓投影/视图矩阵（26.2 的 {@code RenderSystem.getProjectionMatrix()}
 *       与 {@code getModelViewMatrix()} 已移除），改用官方的
 *       {@code GameRenderer.projectPointToScreen(Vec3)}，它直接返回 NDC 坐标。</li>
 * </ul>
 */
public class TextUtil implements Wrapper {

    public static float getWidth(String s) {
        return mc.font.width(s);
    }

    public static float getHeight() {
        return mc.font.lineHeight;
    }

    public static void drawStringWithScale(GuiGraphicsExtractor ctx, String text, float x, float y, int color, float scale) {
        Matrix3x2fStack pose = ctx.pose();
        if (scale != 1) {
            pose.pushMatrix();
            pose.scale(scale, scale);
            if (scale > 1.0f) {
                pose.translate(-x / scale, -y / scale);
            } else {
                pose.translate((x / scale) / 2, (y / scale) / 2);
            }
        }

        drawString(ctx, text, x, y, color);
        if (scale != 1) {
            pose.popMatrix();
        }
    }

    public static void drawStringScale(GuiGraphicsExtractor ctx, String text, float x, float y, int color, float scale, boolean shadow) {
        Matrix3x2fStack pose = ctx.pose();
        if (scale != 1) {
            pose.pushMatrix();
            pose.scale(scale, scale);
            if (scale > 1.0f) {
                pose.translate(-x / scale, -y / scale);
            } else {
                pose.translate((x / scale) / 2, (y / scale) / 2);
            }
        }

        ctx.text(mc.font, text, (int) x, (int) y, color, shadow);
        if (scale != 1) {
            pose.popMatrix();
        }
    }

    public static void drawString(GuiGraphicsExtractor ctx, String text, double x, double y, int color) {
        drawString(ctx, text, x, y, color, false);
    }

    public static void drawString(GuiGraphicsExtractor ctx, String text, double x, double y, int color, boolean customFont) {
        drawString(ctx, text, x, y, color, customFont, true);
    }

    public static void drawString(GuiGraphicsExtractor ctx, String text, double x, double y, int color, boolean customFont, boolean shadow) {
        if (customFont) {
            FontManager.ui.drawString(ctx, text, (int) x, (int) y, color, shadow);
        } else {
            ctx.text(mc.font, text, (int) x, (int) y, color, shadow);
        }
    }

    public static void drawStringPulse(GuiGraphicsExtractor ctx, String text, double x, double y, Color startColor, Color endColor, double speed, int counter, boolean customFont) {
        drawStringPulse(ctx, text, x, y, startColor, endColor, speed, counter, customFont, true);
    }

    public static void drawStringPulse(GuiGraphicsExtractor ctx, String text, double x, double y, Color startColor, Color endColor, double speed, int counter, boolean customFont, boolean shadow) {
        char[] stringToCharArray = text.toCharArray();
        int index = 0;
        boolean color = false;
        String s = null;
        for (char c : stringToCharArray) {
            if (c == '§') {
                color = true;
                continue;
            }
            if (color) {
                if (c == 'r') {
                    s = null;
                } else {
                    s = "§" + c;
                }
                color = false;
                continue;
            }
            index++;
            if (s != null) {
                drawString(ctx, s + c, x,
                        y, startColor.getRGB(), customFont, shadow);
            } else {
                drawString(ctx, String.valueOf(c), x,
                        y, ColorUtil.pulseColor(startColor, endColor, index, counter, speed).getRGB(), customFont, shadow);
            }
            x += customFont ? FontManager.ui.getWidth(String.valueOf(c)) : mc.font.width(String.valueOf(c));
        }
    }

    /**
     * 世界坐标 → GUI 屏幕坐标。
     * <p>
     * 返回的 {@code z} 是 NDC 深度（越小越近）。旧实现返回的是裁剪空间 z，
     * 语义相近但数值范围不同；调用方只用于远近判断。
     */
    public static Vec3 worldSpaceToScreenSpace(Vec3 pos) {
        Vec3 ndc = mc.gameRenderer.projectPointToScreen(pos);
        double screenX = (ndc.x + 1.0) * 0.5 * mc.getWindow().getGuiScaledWidth();
        double screenY = (1.0 - ndc.y) * 0.5 * mc.getWindow().getGuiScaledHeight();
        return new Vec3(screenX, screenY, ndc.z);
    }
}
