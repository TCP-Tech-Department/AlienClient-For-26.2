package dev.luminous.asm.mixins;

import dev.luminous.api.interfaces.IChatHudLineHook;
import dev.luminous.api.utils.math.FadeUtils;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * 26.2: ChatHudLine -> {@link GuiMessage}（record）。
 * 每行的 Alien 附加状态（消息 id / 同步标记 / 淡入动画）挂在这个 record 上，
 * MixinChatHud 通过 IChatHudLineHook 强转访问。
 */
@Mixin(value = GuiMessage.class)
public class MixinChatHudLine implements IChatHudLineHook {
    @Unique
    private int id = 0;
    @Unique
    private boolean sync = false;
    @Unique
    private FadeUtils fade;

    @Override
    public int alienClient$getMessageId() {
        return id;
    }

    @Override
    public void alienClient$setMessageId(int id) {
        this.id = id;
    }

    @Override
    public boolean alienClient$getSync() {
        return sync;
    }

    @Override
    public void alienClient$setSync(boolean sync) {
        this.sync = sync;
    }

    @Override
    public FadeUtils alienClient$getFade() {
        return fade;
    }

    @Override
    public void alienClient$setFade(FadeUtils fade) {
        this.fade = fade;
    }
}
