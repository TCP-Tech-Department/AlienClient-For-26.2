package dev.luminous.asm.mixins;

import dev.luminous.Alien;
import dev.luminous.api.events.impl.OpenScreenEvent;
import dev.luminous.mod.modules.impl.client.ClickGui;
import dev.luminous.mod.modules.impl.client.ClientSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public class MixinScreen {
    @Shadow
    public int width;
    @Shadow
    public int height;
    @Shadow
    protected Minecraft minecraft; // 26.2: was client

    // 26.2: was renderBackground(DrawContext, int, int, float)；框架现在自动调用 extractBackground
    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    public void renderInGameBackgroundHook(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        ci.cancel();
        if (this.minecraft.level == null) { // 26.2: was client.world
            this.extractPanorama(graphics, delta); // 26.2: was renderPanoramaBackground
        }

        if (ClientSetting.INSTANCE.darkening.getValue()) {
            // 26.2: was renderDarkening(context)，等价于内置的半透明黑色渐变
            this.extractTransparentBackground(graphics);
        }
        if (this.minecraft.level != null) {
            if (ClickGui.getInstance().tint.booleanValue) {
                graphics.fillGradient(0, 0, this.width, this.height, ClickGui.getInstance().tint.getValue().getRGB(), ClickGui.getInstance().endColor.getValue().getRGB());
            }
        }
    }

/*    @Inject(method = "init()V", at = @At("TAIL"))
    public void onInit(CallbackInfo ci) {
        if ((Screen) (Object) this instanceof DownloadingTerrainScreen || (Screen) (Object) this instanceof ProgressScreen) {
            DirectionalLayoutWidget grid = DirectionalLayoutWidget.vertical();
            grid.getMainPositioner().alignHorizontalCenter().margin(10);
            grid.add(new ButtonWidget.Builder(Text.literal("Close This Screen"), button -> close()).build());

            grid.refreshPositions();
            grid.forEachChild(this::addDrawableChild);
            SimplePositioningWidget.setPos(grid, this.getNavigationFocus());
        }
    }*/

    // 26.2: renderPanoramaBackground -> extractPanorama
    @Shadow
    protected void extractPanorama(GuiGraphicsExtractor graphics, float a) {
    }

    // 26.2: renderDarkening -> extractTransparentBackground（26.2 无单参数的 renderDarkening）
    @Shadow
    public void extractTransparentBackground(GuiGraphicsExtractor graphics) {
    }

    /**
     * 26.2: was @Inject(method = "setScreen", HEAD, cancellable) on MinecraftClient。
     * 26.2 里 Minecraft 不再提供 setScreen，屏幕统一由 Gui#setScreen 管理，
     * 而 Gui#setScreen 会对新屏幕调用一次 {@link Screen#added()}，因此挂到 added() 上，
     * 语义仍是「每次打开屏幕触发一次」。26.2 没有「取消打开」的入口，OpenScreenEvent 的 cancel 不再生效。
     */
    @Inject(method = "added", at = @At("HEAD"))
    private void onAdded(CallbackInfo ci) {
        Alien.EVENT_BUS.post(OpenScreenEvent.get((Screen) (Object) this));
    }
}
