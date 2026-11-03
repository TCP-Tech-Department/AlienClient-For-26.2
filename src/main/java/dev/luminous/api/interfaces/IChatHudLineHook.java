package dev.luminous.api.interfaces;

import dev.luminous.api.utils.math.FadeUtils;

public interface IChatHudLineHook {
    int alienClient$getMessageId();

    void alienClient$setMessageId(int id);

    boolean alienClient$getSync();

    void alienClient$setSync(boolean sync);

    FadeUtils alienClient$getFade();

    void alienClient$setFade(FadeUtils fade);
}
