package dev.luminous.asm.mixins;

import dev.luminous.api.interfaces.IChatHudHook;
import dev.luminous.api.interfaces.IChatHudLineHook;
import dev.luminous.api.utils.math.FadeUtils;
import dev.luminous.api.utils.render.ChatLineRenderState;
import dev.luminous.asm.accessors.IChatHud;
import dev.luminous.mod.modules.impl.client.ClientSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/*
 * 26.2: ChatHud -> {@link ChatComponent}，且实现整体重写：
 * <ul>
 *   <li>ChatHudLine / ChatHudLine.Visible -> {@link GuiMessage} / {@link GuiMessage.Line}（record）；</li>
 *   <li>messages / visibleMessages -> allMessages / trimmedMessages；</li>
 *   <li>addMessage(Text, ...) -> addMessage(Component, MessageSignature, GuiMessageSource, GuiMessageTag)，
 *       公开入口为 addClientSystemMessage / addServerSystemMessage / addPlayerMessage；</li>
 *   <li>render(...) -> extractRenderState(...)，渲染循环为 {@code forEachLine(AlphaCalculator, LineConsumer)}，
 *       每行回调 {@code LineConsumer#accept(line, lineIndex, alpha)}；
 *       文字绘制下沉到 {@code ChatGraphicsAccess#handleMessage}（由 {@link MixinChatGraphicsAccess} 接管）。</li>
 *   <li>GuiMessage.Line#addedTime() 仍然存在（委托给 {@code GuiMessage#addedTime()} record 组件），
 *       但它已不再在渲染循环里被调用，行下标改为由 LineConsumer#accept 直接给出，
 *       见 {@link MixinChatHudLineConsumer}。</li>
 * </ul>
 */
@Mixin(ChatComponent.class)
public abstract class MixinChatHud implements IChatHudHook {

    @Unique
    private int nextMessageId = 0;
    @Unique
    private boolean nextSync;
    @Final
    @Shadow
    private List<GuiMessage.Line> trimmedMessages; // 26.2: was visibleMessages
    @Shadow
    @Final
    private List<GuiMessage> allMessages; // 26.2: was messages

    /**
     * 26.2: 两个消息列表仍是 private final ArrayList。
     * 26.2 的 {@code addMessage} 会先从网络线程/渲染线程之外的调用方进到 allMessages，
     * 保持 1.21.1 的做法换成并发安全列表（accessor 见 {@link IChatHud}）。
     */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void onInit(Minecraft minecraft, CallbackInfo ci) {
        ((IChatHud) this).setTrimmedMessages(new CopyOnWriteArrayList<>());
        ((IChatHud) this).setAllMessages(new CopyOnWriteArrayList<>());
    }

    /**
     * 26.2: 每帧聊天绘制入口，先清掉上一帧残留的行动画状态。
     * <p>
     * {@code captureClickableText}（点击区域收集）走的是另一条
     * {@code ClickableTextOnlyGraphicsAccess}，它同样会经过匿名 LineConsumer#accept 写入状态，
     * 但不会调用 {@code handleMessage}（因此状态不会被消费）。这里在真正绘制前清一次，
     * 保证随后的「chat.queue」/受限提示文字拿到的一定是本帧的行状态（没有就按原版画）。
     */
    @Inject(
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
            at = @At("HEAD")
    )
    private void alienClient$resetLineAnimation(GuiGraphicsExtractor graphics, Font font, int ticks, int mouseX,
                                                int mouseY, ChatComponent.DisplayMode displayMode,
                                                boolean changeCursorOnInsertions, CallbackInfo ci) {
        ChatLineRenderState.end();
    }

    @Override
    public void alienClient$addMessage(Component message, int id) {
        nextMessageId = id;
        nextSync = true;
        addMessage(message);
        nextSync = false;
        nextMessageId = 0;
    }

    @Override
    public void alienClient$addMessage(Component message) {
        nextSync = true;
        addMessage(message);
        nextSync = false;
    }

    @Override
    public void alienClient$addMessageOutSync(Component message, int id) {
        nextMessageId = id;
        addMessage(message);
        nextMessageId = 0;
    }

    // 26.2: was @Inject(method = "addVisibleMessage", ...) —— 现在拆成 addMessageToDisplayQueue / addMessageToQueue。
    // 26.2 的 addMessageToDisplayQueue 对每条换行后的 Line 逐个 addFirst，这里同样逐行挂上状态
    // （不能放 TAIL：多行消息只有最后一行会被设置，其余行会因为没有 FadeUtils 而按 ease=0 渲染）。
    @Inject(method = "addMessageToDisplayQueue",
            at = @At(value = "INVOKE", target = "Ljava/util/List;addFirst(Ljava/lang/Object;)V", shift = At.Shift.AFTER, remap = false))
    private void onAddMessageAfterNewChatHudLineVisible(GuiMessage message, CallbackInfo ci) {
        if (trimmedMessages.isEmpty()) return;
        IChatHudLineHook line = (IChatHudLineHook) (Object) trimmedMessages.getFirst();
        line.alienClient$setMessageId(nextMessageId);
        line.alienClient$setSync(nextSync);
        line.alienClient$setFade(new FadeUtils(ClientSetting.INSTANCE.animationTime.getValueInt()));
    }

    // 26.2: was @Inject(method = "addMessage(Lnet/minecraft/client/gui/hud/ChatHudLine;)V", ...)
    @Inject(method = "addMessageToQueue", at = @At("TAIL"))
    private void onAddMessageAfterNewChatHudLine(GuiMessage message, CallbackInfo ci) {
        if (allMessages.isEmpty()) return;
        IChatHudLineHook line = (IChatHudLineHook) (Object) allMessages.getFirst();
        line.alienClient$setMessageId(nextMessageId);
        line.alienClient$setSync(nextSync);
        line.alienClient$setFade(new FadeUtils(ClientSetting.INSTANCE.animationTime.getValueInt()));
    }

    // 26.2: was addMessage(Text, MessageSignatureData, MessageIndicator)
    @Inject(at = @At("HEAD"), method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V")
    private void onAddMessage(Component message, MessageSignature signature, GuiMessageSource source, GuiMessageTag tag, CallbackInfo ci) {
        if (nextMessageId != 0) {
            trimmedMessages.removeIf(msg -> ((IChatHudLineHook) (Object) msg).alienClient$getMessageId() == nextMessageId);
            allMessages.removeIf(msg -> ((IChatHudLineHook) (Object) msg).alienClient$getMessageId() == nextMessageId);
        }
    }

    // 26.2: was @Redirect(method = "addVisibleMessage", List#size()I, ordinal = 2)
    // addMessageToDisplayQueue 里有 3 处 List.size()：0 = lines.size()（循环条件），
    // 1 = lines.size()（endOfEntry 判断），2 = trimmedMessages.size()（裁剪循环）。
    // 只有第 3 处要改成「永不裁剪」以实现 InfiniteChat。
    @Redirect(method = {"addMessageToDisplayQueue"},
            at = @At(value = "INVOKE", target = "Ljava/util/List;size()I", ordinal = 2, remap = false), require = 0)
    public int chatLinesSize(List<GuiMessage.Line> list) {
        return ClientSetting.INSTANCE.isOn() && ClientSetting.INSTANCE.infiniteChat.getValue() ? Integer.MIN_VALUE : list.size();
    }

    // 26.2: addMessageToQueue 里的 allMessages.size()
    @Redirect(method = {"addMessageToQueue"},
            at = @At(value = "INVOKE", target = "Ljava/util/List;size()I", ordinal = 0, remap = false), require = 0)
    public int allMessagesSize(List<GuiMessage> list) {
        return ClientSetting.INSTANCE.isOn() && ClientSetting.INSTANCE.infiniteChat.getValue() ? Integer.MIN_VALUE : list.size();
    }

    /*
     * 26.2: 取代 1.21.1 的两个注入点：
     *   - @Redirect(ChatHud#render -> DrawContext#drawTextWithShadow) —— 聊天文字淡入/位移/同步染色；
     *   - @Inject(ChatHud#render, INVOKE ChatHudLine$Visible#addedTime) —— 抓当前绘制行下标。
     * 26.2 的渲染循环是 ChatComponent#forEachLine，它逐行回调
     * LineConsumer#accept(line, lineIndex, alpha)：这里既给出行下标（= 旧 chatLineIndex），
     * 也给出行对象（内含 Alien 附加状态：消息 id / 同步标记 / FadeUtils）。
     * 由于 forEachLine 的两个参数类型都是 ChatComponent 的 private 嵌套类型、
     * 无法出现在 mixin 方法签名里，抓取动作放在 MixinChatHudLineConsumer
     * （目标为那个匿名 LineConsumer 实现类），真正的文字绘制动画放在
     * MixinChatGraphicsAccess（目标为 ChatGraphicsAccess 的两个绘制实现）。
     */

    /**
     * 26.2: 取代 {@code @ModifyVariable(method = "render", at = STORE)} 去掉 MessageIndicator。
     * 26.2 的指示条与指示图标都由 {@code GuiMessageTag} 驱动
     * （{@code ChatGraphicsAccess#handleTag} 画色条、{@code handleTagIcon} 画图标），
     * 而 tag 只在 {@code GuiMessage} 构造时写入（{@code addMessage} 的最后一个参数）。
     * 因此在这里把 tag 抹成 null，等于同时关掉色条 + 图标 + 悬停提示，与 1.21.1 抹掉
     * MessageIndicator 的效果一致；消息内容、签名、删除队列都不受影响。
     */
    @ModifyVariable(
            method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
            at = @At("HEAD"), argsOnly = true, index = 4)
    private GuiMessageTag alienClient$removeMessageIndicator(GuiMessageTag tag) {
        if (ClientSetting.INSTANCE.hideIndicator.getValue()) {
            return null;
        }
        return tag;
    }

    // 26.2: addMessage(Text) -> addClientSystemMessage(Component)
    @Shadow
    public abstract void addClientSystemMessage(Component message);

    private void addMessage(Component message) {
        this.addClientSystemMessage(message);
    }
}
