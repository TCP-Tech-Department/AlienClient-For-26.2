package dev.luminous.asm.accessors;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/*
 * 26.2: 字段名未变（private final blockStatePredictionHandler）。
 *
 * 为什么 accessor 方法不叫 getBlockStatePredictionHandler：
 * 26.2 的 ClientLevel 自己已经声明了一个同签名的 package-private 方法
 * {@code BlockStatePredictionHandler getBlockStatePredictionHandler()}。
 * 若接口里用同名方法（无论是 @Accessor 生成还是 default 方法），JVM 在 invokeinterface 时
 * 会选中 ClientLevel 那个 package-private 方法并抛
 * IllegalAccessError: 'BlockStatePredictionHandler net.minecraft.client.multiplayer.ClientLevel.getBlockStatePredictionHandler()'
 * （已用最小用例实测确认），所以公开入口改用 getPredictionHandler()。
 */
@Mixin(ClientLevel.class)
public interface IClientLevel {

    /**
     * 26.2: 旧名 getBlockStatePredictionHandler()（1.21.1 Yarn 无同名方法，可直接用）。
     */
    @Accessor("blockStatePredictionHandler")
    BlockStatePredictionHandler getPredictionHandler();
}
