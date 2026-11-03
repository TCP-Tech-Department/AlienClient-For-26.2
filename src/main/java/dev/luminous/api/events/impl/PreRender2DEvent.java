package dev.luminous.api.events.impl;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public class PreRender2DEvent {
    private static final PreRender2DEvent INSTANCE = new PreRender2DEvent();
    public GuiGraphicsExtractor drawContext;

    public static PreRender2DEvent get(GuiGraphicsExtractor drawContext) {
        INSTANCE.drawContext = drawContext;
        return INSTANCE;
    }
}