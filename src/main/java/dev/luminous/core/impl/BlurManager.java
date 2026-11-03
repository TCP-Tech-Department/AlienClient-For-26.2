package dev.luminous.core.impl;

import dev.luminous.Alien;
import dev.luminous.api.utils.Wrapper;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI 区域模糊（26.2 真实实现）。
 *
 * <p>26.2 的 GUI 绘制被拆成 extract（收集 render state）和 render（GuiRenderer 统一提交），
 * 因此 {@code Component}/{@code HUD} 在 extract 阶段调用的 {@link #applyBlur} 不可能立刻
 * 对主帧缓冲做 blur。原版为此提供了 {@link GuiGraphicsExtractor#blurBeforeThisStratum()}
 * （每帧只允许一次），由 {@code GuiRenderer#draw} 在两层 GUI 之间调用
 * {@code GameRenderer#processBlurEffect()}。
 *
 * <p>这里沿用同一时机：{@code applyBlur} 只登记本帧所有请求的矩形（屏幕归一化坐标）并
 * 请求一次 blur 槽位；随后 {@code MixinGameRendererBlur} 在
 * {@code GameRenderer#processBlurEffect} 的 HEAD 处用 {@code alien:blurarea} 接管，
 * 只有落在这些矩形内的像素才做两趟可分离模糊。链不可用时返回 false，
 * 自动回退到原版 {@code minecraft:blur}（全屏模糊）。
 */
public class BlurManager implements Wrapper {

    private static final int MAX_REGIONS = ShaderManager.MAX_BLUR_REGIONS;

    /** 本帧请求模糊的矩形（归一化 x0, y0, x1, y1，GL 坐标原点左下）。 */
    private final List<float[]> regions = new ArrayList<>(MAX_REGIONS);

    private GuiGraphicsExtractor currentContext;
    private float radius = 10.0F;
    private boolean blurRequested;

    /**
     * 对当前 GUI 层之前的内容做区域模糊（公开签名保持不变）。
     *
     * @param ctx    当前帧的 {@link GuiGraphicsExtractor}
     * @param radius 模糊半径（采样步数）
     */
    public void applyBlur(GuiGraphicsExtractor ctx, float radius, float startX, float startY, float width, float height) {
        if (ctx == null || width <= 0.0F || height <= 0.0F) {
            return;
        }
        if (ctx != currentContext) {
            // 26.2 每帧都会 new 一个 GuiGraphicsExtractor（Gui#extractRenderState），用它当帧标识
            currentContext = ctx;
            regions.clear();
            blurRequested = false;
        }
        this.radius = Math.max(radius, 1.0F);

        if (!blurRequested) {
            blurRequested = true;
            try {
                ctx.blurBeforeThisStratum();
            } catch (IllegalStateException ignored) {
                // 本帧已经有别的代码（例如原版 Screen）请求过 blur，槽位已被占用
            }
        }

        if (regions.size() >= MAX_REGIONS) {
            return;
        }

        float guiWidth = Math.max(1.0F, mc.getWindow().getGuiScaledWidth());
        float guiHeight = Math.max(1.0F, mc.getWindow().getGuiScaledHeight());
        float x0 = clamp01(startX / guiWidth);
        float x1 = clamp01((startX + width) / guiWidth);
        // GUI 坐标原点在左上，GL 采样坐标原点在左下
        float y0 = clamp01(1.0F - (startY + height) / guiHeight);
        float y1 = clamp01(1.0F - startY / guiHeight);
        if (x1 - x0 <= 0.0F || y1 - y0 <= 0.0F) {
            return;
        }
        regions.add(new float[]{x0, y0, x1, y1});
    }

    /**
     * 由 {@code MixinGameRendererBlur} 在 {@code GameRenderer#processBlurEffect} 里调用。
     *
     * @return true 表示已用 alien:blurarea 处理（调用方 cancel 原版全屏模糊）
     */
    public boolean processRegionBlur() {
        if (regions.isEmpty() || Alien.SHADER == null) {
            return false;
        }
        boolean applied = Alien.SHADER.applyRegionBlur(regions, radius);
        regions.clear();
        return applied;
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
