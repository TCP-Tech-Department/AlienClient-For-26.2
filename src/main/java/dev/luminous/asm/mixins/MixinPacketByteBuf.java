package dev.luminous.asm.mixins;

import dev.luminous.mod.modules.impl.exploit.AntiPacket;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.network.FriendlyByteBuf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * 26.2: {@code PacketByteBuf} → {@code FriendlyByteBuf}，{@code NbtSizeTracker} → {@code NbtAccounter}
 * （{@code ofUnlimitedBytes()} → {@code unlimitedHeap()}）。
 * 注入点仍是 {@code readNbt(ByteBuf)} 内部转向 {@code readNbt(ByteBuf, NbtAccounter)} 的那次调用，
 * 只改第二个实参（配额），即「放大 NBT 解码上限」。
 */
@Mixin(FriendlyByteBuf.class)
public abstract class MixinPacketByteBuf {
    @ModifyArg(method = "readNbt(Lio/netty/buffer/ByteBuf;)Lnet/minecraft/nbt/CompoundTag;", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/FriendlyByteBuf;readNbt(Lio/netty/buffer/ByteBuf;Lnet/minecraft/nbt/NbtAccounter;)Lnet/minecraft/nbt/Tag;"))
    private static NbtAccounter xlPackets(NbtAccounter accounter) {
        return AntiPacket.INSTANCE.isOn() && AntiPacket.INSTANCE.decode.getValue() ? NbtAccounter.unlimitedHeap() : accounter;
    }
}
