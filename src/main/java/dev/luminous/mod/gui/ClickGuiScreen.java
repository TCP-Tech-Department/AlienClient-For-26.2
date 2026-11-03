package dev.luminous.mod.gui;

import dev.luminous.Alien;
import dev.luminous.api.utils.Wrapper;
import dev.luminous.mod.Mod;
import dev.luminous.mod.gui.items.Component;
import dev.luminous.mod.gui.items.Item;
import dev.luminous.mod.gui.items.buttons.ModuleButton;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.client.ClickGui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;

public class ClickGuiScreen extends Screen {
    private static ClickGuiScreen INSTANCE;

    static {
        INSTANCE = new ClickGuiScreen();
    }

    private final ArrayList<Component> components = new ArrayList<>();

    public ClickGuiScreen() {
        // 26.2：Component 在本类中指的是 GUI 组件（dev.luminous...），标题用全限定名
        super(net.minecraft.network.chat.Component.literal("Alien"));
        setInstance();
        load();
    }

    public static ClickGuiScreen getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ClickGuiScreen();
        }
        return INSTANCE;
    }

    private void setInstance() {
        INSTANCE = this;
    }

    private void load() {
        int x = -84;
        for (final Module.Category category : Module.Category.values()) {
            this.components.add(new Component(category.toString(), category, x += 94, 4, true) {

                @Override
                public void setupItems() {
                    for (Module module : Alien.MODULE.getModules()) {
                        if (module.getCategory().equals(category)) {
                            this.addButton(new ModuleButton(module));
                        }
                    }
                }
            });
        }
        this.components.forEach(components -> components.getItems().sort(Comparator.comparing(Mod::getName)));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        // 26.2：RenderSystem.setShaderColor 已移除，无法对整屏 GUI 做全局 alpha 染色。
        // 现状：文字颜色由 Item#drawString 乘 ClickGui.alphaValue；控件填充色仍是原值
        // （曾尝试给 Render2DUtil 加全局倍率，按用户要求连同 GUI 布局改动一起回退了）。
        Item.context = context;
        // 26.2：背景由框架自动调用 extractBackground，不再手动 renderBackground
        this.components.forEach(components -> components.drawScreen(context, mouseX, mouseY, delta));
    }

    /**
     * 手动绘制入口（供 {@code ClickGui.FadeOut} 在屏幕未打开时叠加一次 GUI 使用）。
     * <p>
     * 26.2 把 {@code Screen.render} 改名成 {@code extractRenderState}，这里保留旧名做兼容转发。
     */
    public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        extractRenderState(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();
        this.components.forEach(components -> components.mouseClicked(mouseX, mouseY, event.button()));
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();
        this.components.forEach(components -> components.mouseReleased(mouseX, mouseY, event.button()));
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // 26.2：InputConstants.isKeyPressed → isKeyDown，Window.getHandle → handle()
        if (InputConstants.isKeyDown(Wrapper.mc.getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT)) {
            if (verticalAmount < 0) {
                this.components.forEach(component -> component.setX(component.getX() - 15));
            } else if (verticalAmount > 0) {
                this.components.forEach(component -> component.setX(component.getX() + 15));
            }
        } else {
            if (verticalAmount < 0) {
                this.components.forEach(component -> component.setY(component.getY() - 15));
            } else if (verticalAmount > 0) {
                this.components.forEach(component -> component.setY(component.getY() + 15));
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        this.components.forEach(component -> component.onKeyPressed(event.key()));
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        String typed = event.codepointAsString();
        char chr = typed.isEmpty() ? 0 : typed.charAt(0);
        this.components.forEach(component -> component.onKeyTyped(chr, 0));
        return super.charTyped(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public final ArrayList<Component> getComponents() {
        return this.components;
    }

    public int getTextOffset() {
        return -ClickGui.getInstance().textOffset.getValueInt() - 6;
    }
}
