package dev.luminous.asm.accessors;

import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 26.2: {@code user}（private final User）与 {@code rightClickDelay}（private int）字段都存在。
 * 26.2 的 rightClickDelay 仍是 private，InteractTweaks / NoSlow / BlockUtil 需要读写它，
 * 因此同时提供 getter 与 setter。
 */
@Mixin(Minecraft.class)
public interface IMinecraftClient {
    @Mutable
    @Accessor("user")
    void setSession(User session);

    @Accessor("rightClickDelay")
    int getRightClickDelay();

    @Accessor("rightClickDelay")
    void setRightClickDelay(int delay);

    /**
     * 26.2: 旧的 {@code Minecraft#doItemUse()} 已删除，等价逻辑在
     * {@code private void startUseItem()}（{@code Minecraft.java:1677}，内部会置
     * {@code rightClickDelay = 4}、取 {@code hitResult}、实体/方块/物品三种分支、副手重试）。
     * <p>
     * 这是“右键使用手中物品”的完整原版流程，AutoTool 的自动抛竿/收竿必须走它，
     * 只调用 {@code MultiPlayerGameMode#useItem} 会缺少顺序号、动画与冷却处理。
     */
    @Invoker("startUseItem")
    void alienClient$startUseItem();
}
