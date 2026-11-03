package dev.luminous.asm.mixins;

import dev.luminous.Alien;
import dev.luminous.api.interfaces.IMouseHook;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping; // 26.2: was KeyBinding
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler; // 26.2: was Mouse
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class MixinMouse implements IMouseHook {
    @Shadow
    private boolean mouseGrabbed; // 26.2: was cursorLocked
    @Final
    @Shadow
    private Minecraft minecraft; // 26.2: was client
    @Shadow
    private double xpos; // 26.2: was x
    @Shadow
    private double ypos; // 26.2: was y

    // 26.2: was onMouseButton(long, int button, int action, int mods)
    @Inject(method = "onButton", at = @At("HEAD"))
    private void onMouse(long handle, MouseButtonInfo buttonInfo, int action, CallbackInfo ci) {
        int key = -(buttonInfo.button() + 2);
        if (action == InputConstants.PRESS) {
            Alien.MODULE.onKeyPressed(key);
        }
        if (action == InputConstants.RELEASE) {
            Alien.MODULE.onKeyReleased(key);
        }
    }

    @Override
    public void alienClient$lock() {
        if (this.minecraft.getWindow().isFocused()) {
            if (!this.mouseGrabbed) {
                // 26.2: was KeyBinding.updatePressedStates()（macOS 特判已随 Minecraft.IS_SYSTEM_MAC 移除而取消）
                KeyMapping.setAll();

                this.mouseGrabbed = true;
                this.xpos = this.minecraft.getWindow().getWidth() / 2d;
                this.ypos = this.minecraft.getWindow().getHeight() / 2d;
                // 26.2: was InputConstants.setCursorParameters(window, GLFW_CURSOR_DISABLED, x, y)
                InputConstants.grabOrReleaseMouse(this.minecraft.getWindow(), InputConstants.CURSOR_DISABLED, this.xpos, this.ypos);
            }
        }
    }
}
