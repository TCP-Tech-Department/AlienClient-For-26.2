package dev.luminous.api.events.impl;

import dev.luminous.api.events.Event;
import net.minecraft.world.InteractionHand;

public class InteractItemEvent extends Event {
    private InteractItemEvent() {
    }

    private static final InteractItemEvent INSTANCE = new InteractItemEvent();
    public InteractionHand hand;

    public static InteractItemEvent getPre(InteractionHand hand) {
        INSTANCE.hand = hand;
        INSTANCE.stage = Stage.Pre;
        INSTANCE.setCancelled(false);
        return INSTANCE;
    }

    public static InteractItemEvent getPost(InteractionHand hand) {
        INSTANCE.hand = hand;
        INSTANCE.stage = Stage.Post;
        return INSTANCE;
    }
}
