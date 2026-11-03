package dev.luminous.asm.accessors;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/*
 * 26.2: ChatHud -> {@link ChatComponent}，ChatHudLine -> {@link GuiMessage}，
 * ChatHudLine.Visible -> {@link GuiMessage.Line}；
 * 字段 messages -> allMessages，visibleMessages -> trimmedMessages。
 * 两个列表在 26.2 仍是 private final（ArrayList/Lists.newArrayList），替换列表本身需要 @Mutable。
 * 供 MixinChatHud 在 <init> 里换成并发安全列表（若需要）以及外部清空聊天记录使用。
 */
@Mixin(ChatComponent.class)
public interface IChatHud {

    @Mutable
    @Accessor("trimmedMessages")
    void setTrimmedMessages(List<GuiMessage.Line> trimmedMessages);

    @Mutable
    @Accessor("allMessages")
    void setAllMessages(List<GuiMessage> allMessages);
}
