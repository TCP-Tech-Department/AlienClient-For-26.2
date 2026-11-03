package dev.luminous.api.utils.render;

/**
 * 26.2 聊天行渲染的中转状态。
 * <p>
 * 1.21.1 里「取当前行」和「真正绘制文字」都在 {@code ChatHud#render} 同一个方法内，
 * 所以一个 {@code @Redirect(drawTextWithShadow)} 就能同时拿到行下标（通过 {@code addedTime()} 注入点）
 * 和绘制参数。26.2 把渲染循环（{@code ChatComponent#forEachLine}，给出行下标与行对象）和
 * 文字绘制（{@code ChatComponent.Drawing*GraphicsAccess#handleMessage}，给出 x/y/透明度/文本）
 * 拆成了两个私有内部类，无法在同一个 Mixin 里完成闭环，因此用这个类在同一渲染线程内传递：
 * <ul>
 *   <li>{@code MixinChatHud} 在 {@code LineConsumer#accept} 里 {@link #begin}；</li>
 *   <li>{@code MixinChatGraphicsAccess} 在下一次 {@code handleMessage} 里 {@link #consume} 并取用；</li>
 *   <li>渲染循环结束时 {@link #end} 清空，避免队列提示 / 受限提示等「非聊天行」的文字被误加上动画。</li>
 * </ul>
 * 状态只由渲染线程读写，不需要同步。
 */
public final class ChatLineRenderState {

    /** 是否有待消费的聊天行动画状态。 */
    private static boolean pending;
    /** 淡入进度（0 = 刚出现，1 = 动画结束，与旧 FadeUtils#ease 一致）。 */
    private static float ease;
    /** 水平位移（像素，未取整，与旧 (1-ease)*animateOffset 一致）。 */
    private static float offsetX;
    /** 该行是否为客户端自己发的（同步）消息，需要染色。 */
    private static boolean sync;

    private ChatLineRenderState() {
    }

    public static void begin(float ease, float offsetX, boolean sync) {
        pending = true;
        ChatLineRenderState.ease = ease;
        ChatLineRenderState.offsetX = offsetX;
        ChatLineRenderState.sync = sync;
    }

    /** 文字已绘制，状态作废（之后的 handleMessage 都是非聊天行的文字）。 */
    public static void consume() {
        pending = false;
    }

    /** 渲染循环结束，丢弃状态。 */
    public static void end() {
        pending = false;
    }

    public static boolean pending() {
        return pending;
    }

    public static float ease() {
        return ease;
    }

    public static float offsetX() {
        return offsetX;
    }

    public static boolean sync() {
        return sync;
    }
}
