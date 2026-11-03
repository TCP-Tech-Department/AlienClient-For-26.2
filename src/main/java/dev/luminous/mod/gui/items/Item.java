package dev.luminous.mod.gui.items;

import dev.luminous.core.impl.FontManager;
import dev.luminous.api.utils.render.ColorUtil;
import dev.luminous.mod.Mod;
import dev.luminous.mod.modules.impl.client.ClickGui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.awt.*;

public class Item
        extends Mod {
    public static GuiGraphicsExtractor context;
    protected float x;
    protected float y;
    protected int width;
    protected int height;
    private boolean hidden;

    public Item(String name) {
        super(name);
    }

    public void setLocation(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public static void sound() {
        if (ClickGui.getInstance().sound.getValue())
            // 26.2：PositionedSoundInstance.master 已被 SimpleSoundInstance.forUI 取代
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, ClickGui.getInstance().soundPitch.getValueFloat()));
    }

    public void drawScreen(GuiGraphicsExtractor context, int mouseX, int mouseY, float partialTicks) {
    }

    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
    }

    public void mouseReleased(int mouseX, int mouseY, int releaseButton) {
    }

    public void update() {
    }

    public void onKeyTyped(char typedChar, int keyCode) {
    }

    public void onKeyPressed(int key) {
    }

    public float getX() {
        return this.x;
    }

    public float getY() {
        return this.y;
    }

    public int getWidth() {
        return this.width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return this.height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public boolean isHidden() {
        return this.hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    protected void drawString(String text, double x, double y, Color color) {
        drawString(text, x, y, color.hashCode());
    }

    protected void drawString(String text, double x, double y, int color) {
        color = alpha(color);
        if (ClickGui.getInstance().font.getValue()) {
            FontManager.ui.drawString(context, text, (int) x, (int) y, color, ClickGui.getInstance().shadow.getValue());
        } else {
            // 26.2：DrawContext.drawText → GuiGraphicsExtractor.text，textRenderer → font
            context.text(mc.font, text, (int) x, (int) y, color, ClickGui.getInstance().shadow.getValue());
        }
    }

    /**
     * 26.2：旧的整屏染色 {@code RenderSystem.setShaderColor(1, 1, 1, alphaValue)} 已被移除，
     * 26.2 没有等价的全局着色器颜色。等价做法是把 {@code ClickGui.alphaValue}
     * （0..1 的淡出进度，由 {@code ClickGui.FadeOut} 驱动）乘进每个颜色自己的 alpha 通道。
     * <p>
     * 这里放在 Item 上，是因为 ClickGui 的所有取色最终都会经过它（文字走 {@link #drawString}，
     * 填充走 {@code Render2DUtil} 并应在调用处套一层 {@code Item.alpha(...)}）。
     */
    public static int alpha(int color) {
        double value = ClickGui.getInstance().alphaValue;
        if (value >= 1.0) {
            return color;
        }
        if (value <= 0.0) {
            return color & 0x00FFFFFF;
        }
        int sourceAlpha = (color >>> 24) & 0xFF;
        return ColorUtil.injectAlpha(color, (int) Math.round(sourceAlpha * value));
    }

    protected int getFontHeight() {
        if (ClickGui.getInstance().font.getValue()) {
            return (int) FontManager.ui.getFontHeight();
        }
        return mc.font.lineHeight;
    }

    protected int getWidth(String s) {
        if (ClickGui.getInstance().font.getValue()) {
            return (int) FontManager.ui.getWidth(s);
        }
        return mc.font.width(s);
    }
}