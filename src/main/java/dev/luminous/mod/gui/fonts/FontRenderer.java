package dev.luminous.mod.gui.fonts;

import dev.luminous.api.utils.Wrapper;
import it.unimi.dsi.fastutil.chars.Char2IntArrayMap;
import it.unimi.dsi.fastutil.chars.Char2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;

import java.awt.*;
import java.io.Closeable;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 自绘位图字体。
 * <p>
 * 26.2 迁移要点：
 * <ul>
 *   <li>绘制入口由 {@code MatrixStack + Tessellator + RenderSystem.setShader} 改为
 *       {@link GuiGraphicsExtractor}；每个字形用 {@code blit} 提交到 GUI 渲染状态。</li>
 *   <li>GUI 矩阵类型是 {@link Matrix3x2fStack}（2D），不再是 3D 的 {@code PoseStack}。</li>
 *   <li>因此所有 {@code drawString} 系列的第一个参数由矩阵栈改为绘制上下文。</li>
 *   <li>{@code Window.getScaleFactor()} 改名为 {@code getGuiScale()}。</li>
 * </ul>
 */
public class FontRenderer implements Closeable, Wrapper {
    private static final Char2IntArrayMap colorCodes = new Char2IntArrayMap() {{
        put('0', 0x000000);
        put('1', 0x0000AA);
        put('2', 0x00AA00);
        put('3', 0x00AAAA);
        put('4', 0xAA0000);
        put('5', 0xAA00AA);
        put('6', 0xFFAA00);
        put('7', 0xAAAAAA);
        put('8', 0x555555);
        put('9', 0x5555FF);
        put('A', 0x55FF55);
        put('B', 0x55FFFF);
        put('C', 0xFF5555);
        put('D', 0xFF55FF);
        put('E', 0xFFFF55);
        put('F', 0xFFFFFF);
    }};

    private static final ExecutorService ASYNC_WORKER = Executors.newCachedThreadPool();
    private final Object2ObjectMap<Identifier, ObjectList<DrawEntry>> GLYPH_PAGE_CACHE = new Object2ObjectOpenHashMap<>();
    private final float originalSize;
    private final ObjectList<GlyphMap> maps = new ObjectArrayList<>();
    private final Char2ObjectArrayMap<Glyph> allGlyphs = new Char2ObjectArrayMap<>();
    private final int charsPerPage;
    private final int padding;
    private final String prebakeGlyphs;
    private int scaleMul = 0;
    private Font font;
    private Font secondFont;
    private int previousGameScale = -1;
    private Future<Void> prebakeGlyphsFuture;
    private boolean initialized;

    public FontRenderer(Font font, float sizePx, int charactersPerPage, int paddingBetweenCharacters, @Nullable String prebakeCharacters) {
        this.originalSize = sizePx;
        this.charsPerPage = charactersPerPage;
        this.padding = paddingBetweenCharacters;
        this.prebakeGlyphs = prebakeCharacters;
        init(font, sizePx);
    }

    public FontRenderer(Font font, Font secondFont, float sizePx, int charactersPerPage, int paddingBetweenCharacters, @Nullable String prebakeCharacters) {
        this(font, sizePx, charactersPerPage, paddingBetweenCharacters, prebakeCharacters);
        this.secondFont = secondFont.deriveFont(sizePx * this.scaleMul);
    }

    public FontRenderer(Font font, float sizePx) {
        this(font, sizePx, 256, 5, null);
    }

    public FontRenderer(Font font, Font secondFont, float sizePx) {
        this(font, secondFont, sizePx, 256, 5, null);
    }

    private static int floorNearestMulN(int x, int n) {
        return n * (int) Math.floor((double) x / (double) n);
    }

    public static String stripControlCodes(String text) {
        char[] chars = text.toCharArray();
        StringBuilder f = new StringBuilder();
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (c == '§') {
                i++;
                continue;
            }
            f.append(c);
        }
        return f.toString();
    }

    @Contract(value = "-> new", pure = true)
    public static @NotNull Identifier randomIdentifier() {
        // 26.2：Identifier.of 已改名为 fromNamespaceAndPath
        return Identifier.fromNamespaceAndPath("alienclient", "temp/" + randomString());
    }

    private static String randomString() {
        return IntStream.range(0, 32).mapToObj(operand -> String.valueOf((char) new Random().nextInt('a', 'z' + 1))).collect(Collectors.joining());
    }

    @Contract(value = "_ -> new", pure = true)
    public static int @NotNull [] RGBIntToRGB(int in) {
        int red = in >> 8 * 2 & 0xFF;
        int green = in >> 8 & 0xFF;
        int blue = in & 0xFF;
        return new int[]{red, green, blue};
    }

    public static double roundToDecimal(double n, int point) {
        if (point == 0) {
            return Math.floor(n);
        }
        double factor = Math.pow(10, point);
        return Math.round(n * factor) / factor;
    }

    private void sizeCheck() {
        // 26.2：Window.getScaleFactor() 改名为 getGuiScale()
        int gs = mc.getWindow().getGuiScale();
        if (gs != this.previousGameScale) {
            close();
            init(this.font, this.originalSize);
            if (secondFont != null) {
                this.secondFont = secondFont.deriveFont(originalSize * this.scaleMul);
            }
        }
    }

    private void init(Font font, float sizePx) {
        if (initialized) throw new IllegalStateException("Double call to init()");
        initialized = true;
        this.previousGameScale = mc.getWindow().getGuiScale();
        this.scaleMul = this.previousGameScale;
        this.font = font.deriveFont(sizePx * this.scaleMul);
        if (prebakeGlyphs != null && !prebakeGlyphs.isEmpty()) {
            prebakeGlyphsFuture = this.prebake();
        }
    }

    private Future<Void> prebake() {
        return ASYNC_WORKER.submit(() -> {
            for (char c : prebakeGlyphs.toCharArray()) {
                if (Thread.interrupted()) break;
                locateGlyph1(c);
            }
            return null;
        });
    }

    private GlyphMap generateMap(char from, char to) {
        GlyphMap gm;
        if (secondFont != null) {
            gm = new GlyphMap(from, to, this.font, this.secondFont, randomIdentifier(), padding);
        } else {
            gm = new GlyphMap(from, to, this.font, randomIdentifier(), padding);
        }
        maps.add(gm);
        return gm;
    }

    private Glyph locateGlyph0(char glyph) {
        for (GlyphMap map : maps) {
            if (map.contains(glyph)) {
                return map.getGlyph(glyph);
            }
        }
        int base = floorNearestMulN(glyph, charsPerPage);
        GlyphMap glyphMap = generateMap((char) base, (char) (base + charsPerPage));
        return glyphMap.getGlyph(glyph);
    }

    @Nullable
    private Glyph locateGlyph1(char glyph) {
        return allGlyphs.computeIfAbsent(glyph, this::locateGlyph0);
    }

    // ------------------------------------------------------------------
    // 绘制入口
    // ------------------------------------------------------------------

    public void drawString(GuiGraphicsExtractor ctx, String s, double x, double y, int color) {
        float r = ((color >> 16) & 0xff) / 255f;
        float g = ((color >> 8) & 0xff) / 255f;
        float b = ((color) & 0xff) / 255f;
        float a = ((color >> 24) & 0xff) / 255f;
        drawString(ctx, s, (float) x, (float) y, r, g, b, a);
    }

    public void drawString(GuiGraphicsExtractor ctx, String s, double x, double y, Color color) {
        drawString(ctx, s, (float) x, (float) y, color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, color.getAlpha() / 255f);
    }

    public void drawString(GuiGraphicsExtractor ctx, String s, float x, float y, float r, float g, float b, float a) {
        drawString(ctx, s, x, y, r, g, b, a, false);
    }

    public void drawString(GuiGraphicsExtractor ctx, String s, double x, double y, int color, boolean shadow) {
        float r = ((color >> 16) & 0xff) / 255f;
        float g = ((color >> 8) & 0xff) / 255f;
        float b = ((color) & 0xff) / 255f;
        float a = ((color >> 24) & 0xff) / 255f;
        drawString(ctx, s, (float) x, (float) y, r, g, b, a, shadow);
    }

    public void drawString(GuiGraphicsExtractor ctx, String s, double x, double y, Color color, boolean shadow) {
        drawString(ctx, s, (float) x, (float) y, color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, color.getAlpha() / 255f, shadow);
    }

    public void drawStringWithShadow(GuiGraphicsExtractor ctx, String s, double x, double y, int color) {
        float r = ((color >> 16) & 0xff) / 255f;
        float g = ((color >> 8) & 0xff) / 255f;
        float b = ((color) & 0xff) / 255f;
        float a = ((color >> 24) & 0xff) / 255f;
        drawStringWithShadow(ctx, s, (float) x, (float) y, r, g, b, a);
    }

    public void drawStringWithShadow(GuiGraphicsExtractor ctx, String s, double x, double y, Color color) {
        drawStringWithShadow(ctx, s, (float) x, (float) y, color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, color.getAlpha() / 255f);
    }

    public void drawStringWithShadow(GuiGraphicsExtractor ctx, String s, float x, float y, float r, float g, float b, float a) {
        drawString(ctx, s, x, y, r, g, b, a, true);
    }

    /**
     * 核心绘制。
     * <p>
     * 与旧实现的差异：不再手工 {@code Tessellator.begin(QUADS)} + 绑定纹理 + 全局程序，
     * 而是把每个字形作为一次 {@code blit} 提交；坐标由 GUI 的 {@link Matrix3x2fStack} 变换，
     * 因此必须保留 {@code 1 / scaleMul} 的缩放，才能让字形在 GUI 缩放下保持原尺寸。
     */
    public void drawString(GuiGraphicsExtractor ctx, String s, float x, float y, float r, float g, float b, float a, boolean shadow) {
        if (prebakeGlyphsFuture != null && !prebakeGlyphsFuture.isDone()) {
            try {
                prebakeGlyphsFuture.get();
            } catch (InterruptedException | ExecutionException ignored) {
            }
        }

        sizeCheck();
        float r2 = r, g2 = g, b2 = b;

        Matrix3x2fStack pose = ctx.pose();
        pose.pushMatrix();
        pose.translate((float) roundToDecimal(x, 1), (float) roundToDecimal(y - 3f, 1));
        pose.scale(1f / this.scaleMul, 1f / this.scaleMul);

        char[] chars = s.toCharArray();
        float xOffset = 0;
        float yOffset = 0;
        boolean inSel = false;
        int lineStart = 0;

        synchronized (GLYPH_PAGE_CACHE) {
            for (int i = 0; i < chars.length; i++) {
                char c = chars[i];
                if (inSel) {
                    inSel = false;
                    char c1 = Character.toUpperCase(c);
                    if (colorCodes.containsKey(c1)) {
                        int ii = colorCodes.get(c1);
                        int[] col = RGBIntToRGB(ii);
                        r2 = col[0] / 255f;
                        g2 = col[1] / 255f;
                        b2 = col[2] / 255f;
                    } else if (c1 == 'R') {
                        r2 = r;
                        g2 = g;
                        b2 = b;
                    }
                    continue;
                }

                if (c == '§') {
                    inSel = true;
                    continue;
                } else if (c == '\n') {
                    yOffset += getStringHeight(s.substring(lineStart, i)) * scaleMul;
                    xOffset = 0;
                    lineStart = i + 1;
                    continue;
                }
                Glyph glyph = locateGlyph1(c);
                if (glyph != null) {
                    if (glyph.value() != ' ') {
                        Identifier i1 = glyph.owner().bindToTexture;
                        if (shadow) {
                            DrawEntry shadowEntry = new DrawEntry(xOffset + 1, yOffset + 1, 0, 0, 0, glyph);
                            GLYPH_PAGE_CACHE.computeIfAbsent(i1, integer -> new ObjectArrayList<>()).add(shadowEntry);
                        }
                        DrawEntry entry = new DrawEntry(xOffset, yOffset, r2, g2, b2, glyph);
                        GLYPH_PAGE_CACHE.computeIfAbsent(i1, integer -> new ObjectArrayList<>()).add(entry);
                    }
                    xOffset += glyph.width();
                }
            }

            for (Identifier identifier : GLYPH_PAGE_CACHE.keySet()) {
                List<DrawEntry> objects = GLYPH_PAGE_CACHE.get(identifier);
                for (DrawEntry object : objects) {
                    float xo = object.atX;
                    float yo = object.atY;
                    Glyph glyph = object.toDraw;
                    GlyphMap owner = glyph.owner();
                    int argb = ((int) (a * 255) << 24)
                            | ((int) (object.r * 255) << 16)
                            | ((int) (object.g * 255) << 8)
                            | (int) (object.b * 255);

                    // 26.2：blit 的 u/v 是纹理像素坐标（不是归一化 UV），
                    // 纹理尺寸由 textureWidth/textureHeight 给出。
                    ctx.blit(
                            RenderPipelines.GUI_TEXTURED,
                            identifier,
                            (int) xo, (int) yo,
                            glyph.u(), glyph.v(),
                            (int) glyph.width(), (int) glyph.height(),
                            owner.width, owner.height,
                            argb
                    );
                }
            }

            GLYPH_PAGE_CACHE.clear();
        }
        pose.popMatrix();
    }

    public void drawCenteredString(GuiGraphicsExtractor ctx, String s, double x, double y, int color) {
        float r = ((color >> 16) & 0xff) / 255f;
        float g = ((color >> 8) & 0xff) / 255f;
        float b = ((color) & 0xff) / 255f;
        float a = ((color >> 24) & 0xff) / 255f;
        drawStringWithShadow(ctx, s, (float) (x - getWidth(s) / 2f), (float) y, r, g, b, a);
    }

    public void drawCenteredString(GuiGraphicsExtractor ctx, String s, double x, double y, Color color) {
        drawStringWithShadow(ctx, s, (float) (x - getWidth(s) / 2f), (float) y, color.getRed() / 255f, color.getGreen() / 255f, color.getBlue() / 255f, color.getAlpha() / 255f);
    }

    public void drawCenteredString(GuiGraphicsExtractor ctx, String s, float x, float y, float r, float g, float b, float a) {
        drawStringWithShadow(ctx, s, x - getWidth(s) / 2f, y, r, g, b, a);
    }

    public void drawGradientString(GuiGraphicsExtractor ctx, String s, float x, float y) {
        drawString(ctx, s, x, y, 255, 255, 255, 255);
    }

    public void drawGradientCenteredString(GuiGraphicsExtractor ctx, String s, float x, float y) {
        drawGradientString(ctx, s, x - getWidth(s) / 2f, y);
    }

    // ------------------------------------------------------------------
    // 度量
    // ------------------------------------------------------------------

    public float getWidth(String text) {
        char[] c = stripControlCodes(text).toCharArray();
        float currentLine = 0;
        float maxPreviousLines = 0;
        for (char c1 : c) {
            if (c1 == '\n') {
                maxPreviousLines = Math.max(currentLine, maxPreviousLines);
                currentLine = 0;
                continue;
            }
            Glyph glyph = locateGlyph1(c1);
            currentLine += glyph == null ? 0 : (glyph.width() / (float) this.scaleMul);
        }
        return Math.max(currentLine, maxPreviousLines);
    }

    public float getStringHeight(String text) {
        char[] c = stripControlCodes(text).toCharArray();
        if (c.length == 0) {
            c = new char[]{' '};
        }
        float currentLine = 0;
        float previous = 0;
        for (char c1 : c) {
            if (c1 == '\n') {
                if (currentLine == 0) {
                    currentLine = (locateGlyph1(' ') == null ? 0 : (Objects.requireNonNull(locateGlyph1(' ')).height() / (float) this.scaleMul));
                }
                previous += currentLine;
                currentLine = 0;
                continue;
            }
            Glyph glyph = locateGlyph1(c1);
            currentLine = Math.max(
                    glyph == null ? 0 : (glyph.height() / (float) this.scaleMul)
                    , currentLine);
        }
        return currentLine + previous;
    }

    public float getFontHeight(String str) {
        return getStringHeight(str);
    }

    public float getFontHeight() {
        return getStringHeight("A");
    }

    @Override
    public void close() {
        try {
            if (prebakeGlyphsFuture != null && !prebakeGlyphsFuture.isDone() && !prebakeGlyphsFuture.isCancelled()) {
                prebakeGlyphsFuture.cancel(true);
                prebakeGlyphsFuture.get();
                prebakeGlyphsFuture = null;
            }
            for (GlyphMap map : maps) {
                map.destroy();
            }
            maps.clear();
            allGlyphs.clear();
            initialized = false;
        } catch (Exception ignored) {
        }
    }

    record DrawEntry(float atX, float atY, float r, float g, float b, Glyph toDraw) {
    }
}
