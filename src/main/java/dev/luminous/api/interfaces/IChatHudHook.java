package dev.luminous.api.interfaces;

import net.minecraft.network.chat.Component;

public interface IChatHudHook {
    void alienClient$addMessage(Component message, int id);

    void alienClient$addMessage(Component message);

    void alienClient$addMessageOutSync(Component message, int id);
}
