package dev.luminous.asm.accessors;

import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Optional;

/*
 * 26.2: ClientboundExplodePacket 由 class 改为 record，且组件比旧版多：
 *     record ClientboundExplodePacket(
 *         Vec3 center, float radius, int blockCount, Optional<Vec3> playerKnockback,
 *         ParticleOptions explosionParticle, Holder<SoundEvent> explosionSound,
 *         WeightedList<ExplosionParticleInfo> blockParticles)
 * （见 26.2/net/minecraft/network/protocol/game/ClientboundExplodePacket.java）。
 * 旧的 playerVelocityX/Y/Z（float，1/8000 单位）已不存在，击退改为不可变 Optional<Vec3> playerKnockback。
 * record 不可变 → 只保留只读访问器。
 *
 * 爆炸击退（Velocity 模块）在 26.2 的正确改法同样不重建包：
 * ClientPacketListener#handleExplosion 末尾是一句
 *     packet.playerKnockback().ifPresent(this.minecraft.player::addDeltaMovement);
 * （ClientPacketListener.java:1374），
 * MixinClientPlayNetworkHandler#velocityHook2 在 TAIL 把已加上的击退减回去、再加事件修正后的值。
 *
 * 若确需重建一个带修正击退的包，必须补齐全部 7 个组件（照抄原包的其余 5 个）：
 *     ClientboundExplodePacket p2 = new ClientboundExplodePacket(
 *         p.center(), p.radius(), p.blockCount(), Optional.of(fixedKnockback),
 *         p.explosionParticle(), p.explosionSound(), p.blockParticles());
 * 组件顺序已按上面的 record 声明核对。
 */
@Mixin(ClientboundExplodePacket.class)
public interface IExplosionS2CPacket {

    @Accessor("center")
    Vec3 getCenter();

    @Accessor("radius")
    float getRadius();

    @Accessor("playerKnockback")
    Optional<Vec3> getPlayerKnockback();
}
