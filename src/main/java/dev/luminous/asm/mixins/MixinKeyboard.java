package dev.luminous.asm.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.luminous.Alien;
import dev.luminous.api.utils.Wrapper;
import dev.luminous.core.impl.CommandManager;
import dev.luminous.mod.modules.impl.client.ClientSetting;
import net.minecraft.ChatFormatting;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.2: {@code Keyboard} → {@code KeyboardHandler}，{@code onKey(long,int,int,int,int)} →
 * {@code keyPress(long, int action, KeyEvent event)}（26.2 的按键参数打包成 record）。
 * <p>
 * 26.2: 原 {@code NarratorManager#isActive()} 的 Redirect ——
 * {@code NarratorManager} 已删除，旁白由 {@code Minecraft#getNarrator()} 返回的
 * {@code net.minecraft.client.GameNarrator} 承载（它内部再委托给 com.mojang.text2speech.Narrator）。
 * Ctrl+B（key 66）切换旁白状态的前置判据因此变成
 * {@code this.minecraft.getNarrator().isActive()}，这里把 {@code GameNarrator#isActive()}
 * 强制返回 false，等价于旧行为：屏蔽 Ctrl+B 的旁白热键，不会去改 options.narrator()。
 * <p>
 * 用 {@code @WrapOperation} 而不是 {@code @Redirect}（见 CHEATSHEET §3）。
 */
@Mixin(KeyboardHandler.class)
public class MixinKeyboard implements Wrapper {

    @Inject(method = "keyPress", at = @At("HEAD"))
    private void onKey(long windowHandle, int action, KeyEvent event, CallbackInfo ci) {
        try {
            int key = event.key();
            if (action == 1) {
                Alien.MODULE.onKeyPressed(key);
            }
            if (action == 0) {
                Alien.MODULE.onKeyReleased(key);
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (ClientSetting.INSTANCE.debug.getValue())
                CommandManager.sendMessage(ChatFormatting.DARK_RED + "[ERROR] onKey " + e.getMessage());
        }
    }

    /**
     * 26.2: was {@code @Redirect(NarratorManager#isActive)}。
     * 只作用于 keyPress 里的旁白热键判断，恒返回 false = 永不触发 Ctrl+B 旁白切换。
     */
    @WrapOperation(
            method = "keyPress",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/GameNarrator;isActive()Z"
            )
    )
    private boolean alienClient$disableNarratorHotkey(GameNarrator narrator, Operation<Boolean> original) {
        return false;
    }
}
