package dev.luminous.mod.gui.windows;

import dev.luminous.api.utils.math.AnimateUtil;
import dev.luminous.api.utils.math.MathUtil;
import dev.luminous.api.utils.render.ColorUtil;
import dev.luminous.api.utils.render.Render2DUtil;
import dev.luminous.core.impl.FontManager;
import dev.luminous.mod.modules.impl.client.ColorsModule;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.resources.Identifier;

import java.awt.*;

public class WindowBase {

    private final String name;
    private final Identifier icon;
    private float x;
    private float y;
    private float width;
    private float height;
    private float dragX;
    private float dragY;
    private float scrollOffset;
    private float prevScrollOffset;
    private float maxElementsHeight;
    private boolean dragging, hoveringWindow, scaling, scrolling;
    private boolean visible = true;

    protected WindowBase(float x, float y, float width, float height, String name, Identifier icon) {
        setX(x);
        setY(y);
        setWidth(width);
        setHeight(height);
        this.name = name;
        this.icon = icon;
    }

    protected void render(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        prevScrollOffset = AnimateUtil.fast(prevScrollOffset, scrollOffset, 12);
        Color color2 = new Color(0xC55B5B5B, true);

        // 26.2：RenderSystem.enableBlend/disableBlend 已移除，GUI 渲染状态自带混合
        Render2DUtil.drawRect(context, x, y, width + 10, height, -1072689136);
        Render2DUtil.drawRect(context, x + 0.5f, y, width + 9, 16, new Color(0x5F000000, true));
        Render2DUtil.horizontalGradient(context, x + 2, y + 16f, x + 2 + width / 2f - 2, y + 16.5f, ColorUtil.injectAlpha(ColorsModule.INSTANCE.clientColor.getValue(), 0), ColorsModule.INSTANCE.clientColor.getValue());
        Render2DUtil.horizontalGradient(context, x + 2 + width / 2f - 2, y + 16f, x + 2 + width - 4, y + 16.5f, ColorsModule.INSTANCE.clientColor.getValue(), ColorUtil.injectAlpha(ColorsModule.INSTANCE.clientColor.getValue(), 0));
        FontManager.ui.drawString(context, name, x + 4, y + 5.5f, -1);
        boolean hover1 = Render2DUtil.isHovered(mouseX, mouseY, x + width - 4, y + 3, 10, 10);
        Render2DUtil.drawRectWithOutline(context, x + width - 4, y + 3, 10, 10, hover1 ? new Color(0xC5777777, true) : new Color(0xC5575757, true), color2);

        float ratio = (getHeight() - 35) / maxElementsHeight;

        boolean hover2 = Render2DUtil.isHovered(mouseX, mouseY, x + width, y + 19, 6, getHeight() - 34);
        Render2DUtil.drawRectWithOutline(context, x + width, y + 19, 6, getHeight() - 34, hover2 ? new Color(0x5F131313, true) : new Color(0x5F000000, true), color2);
        Render2DUtil.drawRect(context, x + width, Math.max(y + 19 - (scrollOffset * ratio), y + 19), 6, Math.min((getHeight() - 34) * ratio, getHeight() - 34), new Color(0xA1313131, true));

        // 26.2：net.minecraft.util.Colors 已移除，白色直接用 0xFFFFFFFF
        Render2DUtil.drawLine(context, x + width - 2, y + 5, x + width + 4, y + 11, -1);
        Render2DUtil.drawLine(context, x + width - 2, y + 11, x + width + 4, y + 5, -1);

        if (scrolling) {
            float diff = ((float) mouseY - y - 19) / (getHeight() - 34);
            scrollOffset = -(diff * maxElementsHeight);
            scrollOffset = MathUtil.clamp(scrollOffset, -maxElementsHeight + (getHeight() - 40), 0);
        }

        hoveringWindow = Render2DUtil.isHovered(mouseX, mouseY, getX(), getY(), getWidth(), getHeight());

        Render2DUtil.drawLine(context, getX() + getWidth(), getY() + getHeight() - 3, getX() + getWidth() + 7, getY() + getHeight() - 10, color2.getRGB());
        Render2DUtil.drawLine(context, getX() + getWidth() + 5, getY() + getHeight() - 3, getX() + getWidth() + 7, getY() + getHeight() - 5, color2.getRGB());
    }

    protected void mouseClicked(double mouseX, double mouseY, int button) {

        if (Render2DUtil.isHovered(mouseX, mouseY, x + width - 4, y + 3, 10, 10)) {
            setVisible(false);
            return;
        }

        if (Render2DUtil.isHovered(mouseX, mouseY, x, y, width, 10)) {
            if (WindowsScreen.draggingWindow == null)
                dragging = true;

            if (WindowsScreen.draggingWindow == null)
                WindowsScreen.draggingWindow = this;

            WindowsScreen.lastClickedWindow = this;
            dragX = (int) (mouseX - getX());
            dragY = (int) (mouseY - getY());
            return;
        }

        if (Render2DUtil.isHovered(mouseX, mouseY, x + width, y + height - 10, 10, 10)) {
            WindowsScreen.lastClickedWindow = this;
            dragX = (int) (mouseX - getWidth());
            dragY = (int) (mouseY - getHeight());
            scaling = true;
            return;
        }

        if (Render2DUtil.isHovered(mouseX, mouseY, x + width, y + 19, 6, getHeight() - 34)) {
            WindowsScreen.lastClickedWindow = this;
            dragX = (int) (mouseX - getWidth());
            dragY = (int) (mouseY - getHeight());
            scrolling = true;
        }
    }

    // 26.2：键盘事件改为 KeyEvent / CharacterEvent record
    protected void keyPressed(KeyEvent event) {
    }

    protected void charTyped(CharacterEvent event) {
    }

    protected void mouseScrolled(int i) {
        if (hoveringWindow) {
            scrollOffset += i * 2;
            scrollOffset = MathUtil.clamp(scrollOffset, -maxElementsHeight + (getHeight() - 40), 0);
        }
    }

    public void mouseReleased(double mouseX, double mouseY, int button) {
        dragging = false;
        scaling = false;
        scrolling = false;
        WindowsScreen.draggingWindow = null;
    }

    protected float getX() {
        return x;
    }

    protected void setX(float x) {
        this.x = x;
    }

    protected float getY() {
        return y;
    }

    protected void setY(float y) {
        this.y = y;
    }

    protected float getWidth() {
        return width;
    }

    protected void setWidth(float width) {
        this.width = width;
    }

    protected float getHeight() {
        return height;
    }

    protected void setHeight(float height) {
        this.height = height;
    }

    protected float getScrollOffset() {
        return prevScrollOffset;
    }

    protected void resetScroll() {
        prevScrollOffset = 0;
        scrollOffset = 0;
    }

    protected void setMaxElementsHeight(float maxElementsHeight) {
        this.maxElementsHeight = maxElementsHeight;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public Identifier getIcon() {
        return icon;
    }
}
