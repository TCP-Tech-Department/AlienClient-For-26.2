package dev.luminous.core.impl;

import dev.luminous.mod.gui.fonts.FontRenderer;
import dev.luminous.mod.modules.impl.client.Fonts;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Objects;

public class FontManager {
    public static FontRenderer ui;
    public static FontRenderer small;
    public static FontRenderer icon;

    public static void init() {
        try {
            ui = assets(8, "default", Font.PLAIN);
            small = assets(6, "default", Font.PLAIN);
            icon = assetsWithoutOffset(8f, "icon", Font.PLAIN);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static FontRenderer assets(float size, String font, int style, String alternate) throws IOException, FontFormatException {
        return new FontRenderer(Font.createFont(Font.TRUETYPE_FONT, Objects.requireNonNull(FontManager.class.getClassLoader().getResourceAsStream("assets/alienclient/font/" + font + ".ttf"))).deriveFont(style, size), getFont(alternate, style, (int) size), size) {
            @Override
            public void drawString(GuiGraphicsExtractor ctx, String s, float x, float y, float r, float g, float b, float a, boolean shadow) {
                super.drawString(ctx, s, x + Fonts.INSTANCE.translate.getValueInt(), y + Fonts.INSTANCE.shift.getValueInt(), r, g, b, a, shadow);
            }
        };
    }

    public static FontRenderer assetsWithoutOffset(float size, String name, int style) throws IOException, FontFormatException {
        return new FontRenderer(Font.createFont(Font.TRUETYPE_FONT, Objects.requireNonNull(FontManager.class.getClassLoader().getResourceAsStream("assets/alienclient/font/" + name + ".ttf"))).deriveFont(style, size), size);
    }

    public static FontRenderer assets(float size, String name, int style) throws IOException, FontFormatException {
        return new FontRenderer(Font.createFont(Font.TRUETYPE_FONT, Objects.requireNonNull(FontManager.class.getClassLoader().getResourceAsStream("assets/alienclient/font/" + name + ".ttf"))).deriveFont(style, size), size) {
            @Override
            public void drawString(GuiGraphicsExtractor ctx, String s, float x, float y, float r, float g, float b, float a, boolean shadow) {
                super.drawString(ctx, s, x + Fonts.INSTANCE.translate.getValueInt(), y + Fonts.INSTANCE.shift.getValueInt(), r, g, b, a, shadow);
            }
        };
    }

    public static FontRenderer create(int size, String font, int style, String alternate) {
        return new FontRenderer(getFont(font, style, size), getFont(alternate, style, size), size) {
            @Override
            public void drawString(GuiGraphicsExtractor ctx, String s, float x, float y, float r, float g, float b, float a, boolean shadow) {
                super.drawString(ctx, s, x + Fonts.INSTANCE.translate.getValueInt(), y + Fonts.INSTANCE.shift.getValueInt(), r, g, b, a, shadow);
            }
        };
    }

    public static FontRenderer create(int size, String font, int style) {
        return new FontRenderer(getFont(font, style, size), size) {
            @Override
            public void drawString(GuiGraphicsExtractor ctx, String s, float x, float y, float r, float g, float b, float a, boolean shadow) {
                super.drawString(ctx, s, x + Fonts.INSTANCE.translate.getValueInt(), y + Fonts.INSTANCE.shift.getValueInt(), r, g, b, a, shadow);
            }
        };
    }

    private static Font getFont(String font, int style, int size) {
        File fontDir = new File("C:\\Windows\\Fonts");

        try {
            for (File file : fontDir.listFiles()) {
                if (file.getName().replace(".ttf", "").replace(".ttc", "").replace(".otf", "").equalsIgnoreCase(font)) {
                    try {
                        return Font.createFont(Font.TRUETYPE_FONT, file).deriveFont(style, size);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }

            for (File file : fontDir.listFiles()) {
                if (file.getName().startsWith(font)) {
                    try {
                        return Font.createFont(Font.TRUETYPE_FONT, file).deriveFont(style, size);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (Exception ignored) {
        }

        return new Font(null, style, size);
    }
}
