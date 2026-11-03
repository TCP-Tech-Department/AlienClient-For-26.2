package dev.luminous.asm.accessors;

import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/*
 * 26.2: ClientboundSetEntityMotionPacket 由 class 改为 record
 *     record ClientboundSetEntityMotionPacket(int id, Vec3 movement)
 * （见 26.2/net/minecraft/network/protocol/game/ClientboundSetEntityMotionPacket.java）。
 * 旧的 velocityX/Y/Z（1/8000 单位的 int）已不存在，速度改为不可变 Vec3 movement。
 * record 不可变 → 只保留只读访问器。
 *
 * 要「改」这个包里的速度，26.2 的正确做法不是在包里改，而是改客户端实体的动量：
 * 26.2 ClientPacketListener#handleSetEntityMotion 只有一句 entity.lerpMotion(packet.movement())
 * （ClientPacketListener.java:623-627），
 * MixinClientPlayNetworkHandler#velocityHook 就是用 @Redirect 拦这个调用、
 * 把事件修正后的 (x,y,z) 重新 lerpMotion 回去的 —— Velocity 模块因此无需重建数据包即可生效。
 *
 * 如果哪天真需要发一个带修正值的包（例如回环/伪造 S2C），按下面这条链路重建：
 *     Vec3 fixed = new Vec3(x, y, z);
 *     ClientboundSetEntityMotionPacket p2 = new ClientboundSetEntityMotionPacket(p.getId(), fixed);
 *     mc.getConnection().send(p2);   // 仅对端是「服务端」时才有意义；客户端不会收到自己发的包
 * 组件顺序 = (id, movement)，已按上面的 record 声明核对。
 */
@Mixin(ClientboundSetEntityMotionPacket.class)
public interface IEntityVelocityUpdateS2CPacket {

    @Accessor("id")
    int getId();

    @Accessor("movement")
    Vec3 getMovement();
}
