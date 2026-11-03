package dev.luminous.asm.mixins;

import dev.luminous.Alien;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.2 GUI 背景模糊接管。
 *
 * <p>26.2 的 GUI blur 只在 {@code GuiRenderer#draw} 的两个 draw range 之间调用一次
 * {@code GameRenderer#processBlurEffect()}，内部固定使用 {@code minecraft:blur}（全屏、
 * 半径来自 {@code Globals.MenuBlurRadius}）。Alien 需要「只模糊指定矩形」，
 * 所以在 HEAD 处用 {@code alien:blurarea} 接管：
 * {@code BlurManager} 在 extract 阶段记录矩形并请求一次 blur 槽位，
 * 这里若成功跑了区域模糊就 cancel 原版全屏模糊；否则保留原版行为。
 */
@Mixin(GameRenderer.class)
public class MixinGameRendererBlur {

    @Inject(method = "processBlurEffect", at = @At("HEAD"), cancellable = true)
    private void alien$processRegionBlur(CallbackInfo ci) {
        if (Alien.BLUR != null && Alien.BLUR.processRegionBlur()) {
            ci.cancel();
        }
    }
}
