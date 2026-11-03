package dev.luminous.api.events.impl;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;

public class HeldItemRendererEvent {
    private HeldItemRendererEvent() {
    }

    private static final HeldItemRendererEvent INSTANCE = new HeldItemRendererEvent();
    private InteractionHand hand;
    private ItemStack item;
    private float ep;
    private PoseStack stack;

    public static HeldItemRendererEvent get(InteractionHand hand, ItemStack item, float equipProgress, PoseStack stack) {
        INSTANCE.hand = hand;
        INSTANCE.item = item;
        INSTANCE.ep = equipProgress;
        INSTANCE.stack = stack;
        return INSTANCE;
    }

    public InteractionHand getHand() {
        return hand;
    }

    public ItemStack getItem() {
        return item;
    }

    public float getEp() {
        return ep;
    }

    public PoseStack getStack() {
        return stack;
    }
}