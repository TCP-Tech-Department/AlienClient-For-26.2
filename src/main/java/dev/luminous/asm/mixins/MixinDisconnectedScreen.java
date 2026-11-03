package dev.luminous.asm.mixins;

import dev.luminous.mod.modules.impl.misc.AutoReconnect;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static dev.luminous.api.utils.Wrapper.mc;

/**
 * 26.2: {@code DirectionalLayoutWidget grid} → {@code LinearLayout layout}，
 * {@code ButtonWidget.Builder} → {@code Button.builder(...).width(w).build()}，
 * {@code grid.refreshPositions()} → {@code layout.arrangeElements()}，
 * {@code ConnectScreen.connect(...)} → {@code ConnectScreen.startConnecting(...)}（ServerData/TransferState）。
 */
@Mixin(DisconnectedScreen.class)
public abstract class MixinDisconnectedScreen {
    @Shadow
    @Final
    private LinearLayout layout;
    @Unique
    private Button reconnectBtn;
    @Unique
    private double time = AutoReconnect.INSTANCE.delay.getValue() * 20;

    @Inject(method = "init", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/layouts/LinearLayout;arrangeElements()V", shift = At.Shift.BEFORE))
    private void addButtons(CallbackInfo ci) {
        if (AutoReconnect.INSTANCE.lastServerConnection != null) {
            reconnectBtn = Button.builder(Component.literal(getText()), button -> tryConnecting()).width(200).build();
            layout.addChild(reconnectBtn);
        }
    }

    public void tick() {
        if (!AutoReconnect.INSTANCE.rejoin() || AutoReconnect.INSTANCE.lastServerConnection == null) return;

        if (time <= 0) {
            tryConnecting();
        } else {
            time--;
            if (reconnectBtn != null) reconnectBtn.setMessage(Component.literal(getText()));
        }
    }

    @Unique
    private String getText() {
        String reconnectText = "Reconnect";
        if (AutoReconnect.INSTANCE.rejoin()) reconnectText += " " + String.format("(%.1f)", time / 20);
        return reconnectText;
    }

    @Unique
    private void tryConnecting() {
        var lastServer = AutoReconnect.INSTANCE.lastServerConnection;
        ConnectScreen.startConnecting(new TitleScreen(), mc, lastServer.left(), lastServer.right(), false, null);
    }
}
