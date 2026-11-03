package dev.luminous.mod.modules.impl.render;

import dev.luminous.api.utils.render.Render2DUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.ColorSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class Crosshair extends Module {
    public static Crosshair INSTANCE;
    public final SliderSetting length =
            add(new SliderSetting("Length", 5, 0.0, 20.0, 0.1));
    public final SliderSetting thickness =
            add(new SliderSetting("Thickness", 2, 0.0, 20.0, 0.1));
    public final SliderSetting interval =
            add(new SliderSetting("Interval", 2, 0.0, 20.0, 0.1));
    private final ColorSetting color = add(new ColorSetting("Color"));

    public Crosshair() {
        super("Crosshair", Category.Render);
        setChinese("准星");
        INSTANCE = this;
    }

    public void draw(GuiGraphicsExtractor context) {
        // 26.2: 2D 绘制不再走 PoseStack，Render2DUtil 直接接收 GuiGraphicsExtractor
        float centerX = mc.getWindow().getGuiScaledWidth() / 2f;
        float centerY = mc.getWindow().getGuiScaledHeight() / 2f;

        //Up
        Render2DUtil.drawRect(context, centerX - thickness.getValueFloat() / 2f, centerY - length.getValueFloat() - interval.getValueFloat(), thickness.getValueFloat(), length.getValueFloat(), color.getValue());

        //Down
        Render2DUtil.drawRect(context, centerX - thickness.getValueFloat() / 2f, centerY + interval.getValueFloat(), thickness.getValueFloat(), length.getValueFloat(), color.getValue());

        //Right
        Render2DUtil.drawRect(context, centerX + interval.getValueFloat(), centerY - thickness.getValueFloat() / 2f, length.getValueFloat(), thickness.getValueFloat(), color.getValue());

        //Left
        Render2DUtil.drawRect(context, centerX - interval.getValueFloat() - length.getValueFloat(), centerY - thickness.getValueFloat() / 2f, length.getValueFloat(), thickness.getValueFloat(), color.getValue());
    }
}
