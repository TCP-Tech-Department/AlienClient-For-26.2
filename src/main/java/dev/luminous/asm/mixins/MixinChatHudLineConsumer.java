package dev.luminous.asm.mixins;

import dev.luminous.api.interfaces.IChatHudLineHook;
import dev.luminous.api.utils.math.FadeUtils;
import dev.luminous.api.utils.render.ChatLineRenderState;
import dev.luminous.mod.modules.impl.client.ClientSetting;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.2 聊天行渲染回调。
 * <p>
 * 1.21.1 里「当前行下标」是靠 {@code @Inject(ChatHud#render, INVOKE ChatHudLine$Visible#addedTime)}
 * 抓局部变量得到的；26.2 的 {@code ChatHudLine.Visible#addedTime()} 虽然还在
 * （{@code GuiMessage.Line#addedTime()} 委托给 {@code GuiMessage#addedTime()} record 组件），
 * 但渲染循环里已不再调用它，无法再作为注入锚点。
 * <p>
 * 26.2 的渲染循环 {@code ChatComponent#forEachLine} 用匿名 {@code LineConsumer} 逐行回调
 * {@code accept(GuiMessage.Line line, int lineIndex, float alpha)}：行下标、行对象、透明度都在这里。
 * 而 {@code forEachLine(AlphaCalculator, LineConsumer)} 的两个参数类型都是 {@code ChatComponent}
 * 的 private 嵌套类型，无法在 mixin 方法签名里引用，所以直接以这个匿名实现类
 * （{@code ChatComponent$1}，源码里 {@code extractRenderState} 中唯一的匿名 LineConsumer）为注入目标。
 * 它只有 {@code accept} 一个方法，且三个参数类型全部是 public。
 * <p>
 * 这里只负责把「本行的动画参数」写入 {@link ChatLineRenderState}，
 * 真正的位移/透明度/染色由 {@link MixinChatGraphicsAccess} 在紧接着的
 * {@code handleMessage} 调用里应用（两次调用一一对应，故状态一定被消费掉，
 * 不会污染循环之后队列提示 / 受限提示的文字）。
 * <p>
 * 注意：底框那一趟 forEachLine 用的是 ChatComponent 里的 lambda，不走这个匿名类，
 * 因此聊天底框不会跟着淡入位移动画移动（与 1.21.1 一致）。
 */
@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent$1")
public class MixinChatHudLineConsumer {

    @Inject(method = "accept", at = @At("HEAD"))
    private void alienClient$beginLineAnimation(GuiMessage.Line line, int lineIndex, float alpha, CallbackInfo ci) {
        IChatHudLineHook hook = (IChatHudLineHook) (Object) line;
        FadeUtils fadeUtils = hook.alienClient$getFade();
        // 1.21.1 在 fadeUtils == null 时按 ease = 0 处理（整行几乎透明）。
        // 现在每一行在 addMessageToDisplayQueue 里都会挂上 FadeUtils，null 只可能出现在
        // 异常路径（例如未来新增的 Line 构造点），这里按「动画已结束」处理，
        // 避免整段聊天文字变成 alpha≈10/255 的隐形文本。
        float ease = fadeUtils == null ? 1.0F : (float) fadeUtils.ease(ClientSetting.INSTANCE.ease.getValue());
        ChatLineRenderState.begin(ease, (1.0F - ease) * ClientSetting.INSTANCE.animateOffset.getValueFloat(),
                hook.alienClient$getSync());
    }
}
