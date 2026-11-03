package dev.luminous.asm.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.luminous.api.utils.render.ChatLineRenderState;
import dev.luminous.mod.modules.impl.client.ClientSetting;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 26.2 聊天文字绘制入口。
 * <p>
 * 26.2 把聊天行的文字绘制从 {@code ChatComponent#render} 下沉到
 * {@code ChatComponent.ChatGraphicsAccess} 的实现类：
 * <ul>
 *   <li>{@code DrawingBackgroundGraphicsAccess} —— 聊天未聚焦（HUD 上的聊天栏）；</li>
 *   <li>{@code DrawingFocusedGraphicsAccess} —— 聊天已聚焦（ChatScreen 打开）。</li>
 * </ul>
 * 两者的 {@code handleMessage(int textTop, float opacity, FormattedCharSequence message)} 都只有一处
 * {@code textRenderer.accept(TextAlignment.LEFT, 0, textTop, parameters.withOpacity(opacity), message)}，
 * 这就是 1.21.1 里被 {@code @Redirect(drawTextWithShadow)} 拦下的那次调用。
 * <p>
 * 这里把旧实现的两件事原样搬过来（参数由 {@link MixinChatHudLineConsumer} 写在 {@link ChatLineRenderState} 里）：
 * <ol>
 *   <li>淡入位移：{@code anchorX += (int)((1-ease) * animateOffset)}；</li>
 *   <li>淡入透明度：{@code opacity = max(10/255, opacity * ease)}（ClientSetting.fade 关闭时不改）。</li>
 * </ol>
 * <p>
 * 注意：1.21.1 里还有第三件事「把玩家自己发的消息整行染成 ClientSetting.color」，
 * 按用户要求**已移除**——聊天文字一律保持原版颜色与 {@code §} 格式码，不再被主题色覆盖。
 * 因此 {@link ChatLineRenderState#sync()} 现在没有消费方，仅保留以兼容旧的写入方。
 * <p>
 * 队列提示 / 受限提示也走 handleMessage，但它们前面没有聊天行回调，
 * {@link ChatLineRenderState#pending()} 为 false，因此保持原版绘制。
 */
@Mixin(targets = {
        "net.minecraft.client.gui.components.ChatComponent$DrawingBackgroundGraphicsAccess",
        "net.minecraft.client.gui.components.ChatComponent$DrawingFocusedGraphicsAccess"
})
public class MixinChatGraphicsAccess {

    @WrapOperation(
            method = "handleMessage",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/ActiveTextCollector;accept(Lnet/minecraft/client/gui/TextAlignment;IILnet/minecraft/client/gui/ActiveTextCollector$Parameters;Lnet/minecraft/util/FormattedCharSequence;)V"
            )
    )
    private void alienClient$applyChatLineAnimation(ActiveTextCollector collector, TextAlignment alignment, int anchorX,
                                                    int y, ActiveTextCollector.Parameters parameters,
                                                    FormattedCharSequence text, Operation<Void> original) {
        if (!ChatLineRenderState.pending()) {
            original.call(collector, alignment, anchorX, y, parameters, text);
            return;
        }

        float ease = ChatLineRenderState.ease();
        int offset = (int) ChatLineRenderState.offsetX();
        ChatLineRenderState.consume();

        float opacity = parameters.opacity();
        if (ClientSetting.INSTANCE.fade.getValue()) {
            // 旧实现是在 0..255 的 alpha 字节上取 max(10, a*ease)
            opacity = Math.max(10.0F / 255.0F, opacity * ease);
        }
        original.call(collector, alignment, anchorX + offset, y, parameters.withOpacity(opacity), text);
    }
}
