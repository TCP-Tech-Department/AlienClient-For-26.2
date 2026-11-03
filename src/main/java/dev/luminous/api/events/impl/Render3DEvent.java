package dev.luminous.api.events.impl;

import dev.luminous.api.utils.render.Render3DUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.phys.AABB;

import java.awt.*;

public class Render3DEvent {
    private static final Render3DEvent INSTANCE = new Render3DEvent();
    public PoseStack matrixStack;
    public float tickDelta;

    public static Render3DEvent get(PoseStack matrixStack, float tickDelta) {
        INSTANCE.matrixStack = matrixStack;
        INSTANCE.tickDelta = tickDelta;
        return INSTANCE;
    }

    public void drawBox(AABB box, Color color) {
        Render3DUtil.drawBox(box, color);
    }

    public void drawFill(AABB box, Color color) {
        Render3DUtil.drawFill(box, color);
    }
}

