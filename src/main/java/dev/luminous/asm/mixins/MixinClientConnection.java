package dev.luminous.asm.mixins;

import dev.luminous.Alien;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.core.impl.CommandManager;
import dev.luminous.mod.modules.impl.client.ClientSetting;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.TimeoutException;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.SkipPacketEncoderException;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.2: {@code ClientConnection} → {@code net.minecraft.network.Connection}；
 * 旧的静态 {@code handlePacket(Packet, PacketListener)} 被 {@code channelRead0(ChannelHandlerContext, Packet)}
 * 取代（SimpleChannelInboundHandler），{@code PacketEncoderException} → {@code SkipPacketEncoderException}。
 */
@Mixin(Connection.class)
public class MixinClientConnection {

    @Inject(method = "channelRead0", at = @At("HEAD"), cancellable = true)
    private <T extends PacketListener> void onHandlePacket(ChannelHandlerContext context, Packet<T> packet, CallbackInfo ci) {
        PacketEvent.Receive event = new PacketEvent.Receive(packet);
        Alien.EVENT_BUS.post(event);
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void onSendPacketPre(Packet<?> packet, CallbackInfo info) {
        PacketEvent.Send event = new PacketEvent.Send(packet);
        Alien.EVENT_BUS.post(event);
        if (event.isCancelled()) {
            info.cancel();
        }
    }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("RETURN"))
    private void onSendPacketPost(Packet<?> packet, CallbackInfo info) {
        PacketEvent.Sent event = new PacketEvent.Sent(packet);
        Alien.EVENT_BUS.post(event);
    }

    @Inject(method = "exceptionCaught", at = @At("HEAD"), cancellable = true)
    private void exceptionCaught(ChannelHandlerContext context, Throwable throwable, CallbackInfo ci) {
        if (!(throwable instanceof TimeoutException) && !(throwable instanceof SkipPacketEncoderException) && ClientSetting.INSTANCE.caughtException.getValue()) {
            if (ClientSetting.INSTANCE.log.getValue()) {
                CommandManager.sendMessage("§4Caught exception §7" + throwable.getMessage());
            }
            ci.cancel();
        }
    }
}
