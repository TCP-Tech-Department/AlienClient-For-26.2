package dev.luminous.asm.mixins;

import dev.luminous.Alien;
import dev.luminous.api.events.impl.ServerConnectBeginEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.2: {@code ConnectScreen.connect(Screen, Minecraft, ServerAddress, ServerInfo, boolean, CookieStorage)}
 * 拆成公开的 {@code startConnecting(Screen, Minecraft, ServerAddress, ServerData, boolean, TransferState)}
 * 与私有的 {@code connect(Minecraft, ServerAddress, ServerData, TransferState)}。
 * Yarn {@code ServerInfo} → {@code ServerData}，{@code CookieStorage} → {@code TransferState}。
 */
@Mixin(ConnectScreen.class)
public abstract class MixinConnectScreen {
    @Inject(method = "startConnecting", at = @At("HEAD"))
    private static void tryConnectEvent(Screen screen, Minecraft minecraft, ServerAddress address, ServerData data,
                                        boolean quickPlay, TransferState transferState, CallbackInfo ci) {
        Alien.EVENT_BUS.post(ServerConnectBeginEvent.get(address, data));
    }

    @Inject(method = "connect", at = @At("HEAD"))
    private void tryConnectEvent(Minecraft minecraft, ServerAddress address, ServerData data,
                                 TransferState transferState, CallbackInfo ci) {
        Alien.EVENT_BUS.post(ServerConnectBeginEvent.get(address, data));
    }
}
