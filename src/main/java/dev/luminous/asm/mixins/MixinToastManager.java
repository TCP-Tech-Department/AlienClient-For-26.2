package dev.luminous.asm.mixins;

import dev.luminous.mod.modules.impl.render.NoRender;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.2: {@code ToastManager#draw(DrawContext)} → {@code ToastManager#extractRenderState(GuiGraphicsExtractor)}
 * （包路径 net.minecraft.client.gui.components.toasts 不变）。
 */
@Mixin(ToastManager.class)
public class MixinToastManager {

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V", at = @At(value = "HEAD"), cancellable = true)
    private void hookDraw(GuiGraphicsExtractor context, CallbackInfo ci) {
        if (NoRender.INSTANCE.guiToast.getValue()) {
            ci.cancel();
        }
    }
}
