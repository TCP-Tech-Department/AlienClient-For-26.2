package dev.luminous.asm.mixins;

import dev.luminous.Alien;
import dev.luminous.api.events.impl.KeyboardInputEvent;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.2: KeyboardInput 移到 {@code net.minecraft.client.player}，且
 * tick() 不再有参数、也没有 {@code sneaking} 字段（改成写 {@code Input keyPresses} 与 {@code moveVector}）。
 * 旧注入点是在 tick 末尾写 sneaking 之后；这里改成 {@code @At("TAIL")}，效果相同：
 * 事件在原生按键状态计算完毕之后发出，监听器（NoSlow / FastSwim / Fly / SafeWalk / HoleSnap /
 * Freecam / RotationManager）可以覆写 keyPresses / moveVector。
 */
@Mixin(KeyboardInput.class)
public class MixinKeyboardInput {
    @Inject(method = "tick", at = @At("TAIL"), cancellable = true)
    private void keyInput(CallbackInfo ci) {
        KeyboardInputEvent event = KeyboardInputEvent.get();
        Alien.EVENT_BUS.post(event);
        if (event.isCancelled()) ci.cancel();
    }
}
