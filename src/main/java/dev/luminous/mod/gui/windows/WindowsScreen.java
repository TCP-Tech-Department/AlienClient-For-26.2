package dev.luminous.mod.gui.windows;

import dev.luminous.api.utils.render.Render2DUtil;
import dev.luminous.mod.gui.ClickGuiScreen;
import dev.luminous.mod.modules.Module;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static dev.luminous.api.utils.Wrapper.mc;

public class WindowsScreen extends Screen {
    public static WindowBase lastClickedWindow;
    public static WindowBase draggingWindow;
    private List<WindowBase> windows = new ArrayList<>();

    public WindowsScreen(WindowBase... windows) {
        super(Component.literal("CustomWindows"));
        this.windows.clear();
        lastClickedWindow = null;
        this.windows = Arrays.stream(windows).toList();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        windows.stream().filter(WindowBase::isVisible).forEach(w -> {
            if (w != lastClickedWindow)
                w.render(context, mouseX, mouseY);
        });

        if (lastClickedWindow != null && lastClickedWindow.isVisible())
            lastClickedWindow.render(context, mouseX, mouseY);
    }

    /**
     * 26.2：{@code renderBackground} 已由框架自动调用，改名为 {@code extractBackground}。
     * 原实现在「世界已加载」时不画背景，这里保留同样的条件。
     */
    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        if (Module.nullCheck())
            super.extractBackground(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        windows.forEach(w -> w.mouseReleased(event.x(), event.y(), event.button()));
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        windows.stream().filter(WindowBase::isVisible).forEach(w -> w.mouseClicked(mouseX, mouseY, event.button()));

        int i = mc.getWindow().getGuiScaledWidth() / 2;
        float offset = (windows.size() * 20f) / -2f - 23;

        if (Render2DUtil.isHovered(mouseX, mouseY, (i + offset) + 1, mc.getWindow().getGuiScaledHeight() - 23, 15, 15))
            mc.setScreenAndShow(ClickGuiScreen.getInstance());

        offset += 23;
        for (WindowBase w : windows) {
            if (Render2DUtil.isHovered(mouseX, mouseY, i + offset, mc.getWindow().getGuiScaledHeight() - 24, 17, 17))
                w.setVisible(!w.isVisible());
            offset += 20f;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        windows.stream().filter(WindowBase::isVisible).forEach(w -> w.keyPressed(event));
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        windows.stream().filter(WindowBase::isVisible).forEach(w -> w.charTyped(event));
        return super.charTyped(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        windows.stream().filter(WindowBase::isVisible).forEach(w -> w.mouseScrolled((int) (verticalAmount * 5D)));
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
}
