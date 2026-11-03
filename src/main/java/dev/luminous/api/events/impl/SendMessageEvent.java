package dev.luminous.api.events.impl;

import dev.luminous.api.events.Event;

public class SendMessageEvent extends Event {
    private SendMessageEvent() {
    }

    private static final SendMessageEvent INSTANCE = new SendMessageEvent();
    public String defaultMessage;
    public String message;

    public static SendMessageEvent get(String message) {
        INSTANCE.defaultMessage = message;
        INSTANCE.message = message;
        INSTANCE.setCancelled(false);
        return INSTANCE;
    }
}