package dev.luminous.asm.accessors;

import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;

/*
 * 26.2: ClientboundPlayerPositionPacket 由 class 改为 record
 *     record ClientboundPlayerPositionPacket(int id, PositionMoveRotation change, Set<Relative> relatives)
 * （见 26.2/net/minecraft/network/protocol/game/ClientboundPlayerPositionPacket.java）。
 * 旧的 yaw / pitch 字段已不存在，旋转量在 change 里（change.yRot() / change.xRot()）。
 * record 不可变 → 只保留只读访问器。
 *
 * 26.2 的「不改朝向的传送」（AntiPacket 的 S2CRotate / NoRotate）正确做法是重建 record：
 *     PositionMoveRotation fixed = p.change().withRotation(myYaw, myPitch);   // 见 PositionMoveRotation#withRotation
 *     ClientboundPlayerPositionPacket p2 = ClientboundPlayerPositionPacket.of(p.id(), fixed, p.relatives());
 * 或者像现在 MixinClientPlayNetworkHandler#onPlayerPositionLook 那样整体 cancel 掉，
 * 自己按 Relative 标志位把位置应用上去、跳过朝向 —— 两条路都已在 26.2 源码上核对过字段含义：
 *   * change.position()   → 目标位置（旧 getX/getY/getZ）
 *   * change.deltaMovement() → 目标动量（旧 getDeltaX/Y/Z）
 *   * relatives()         → 旧 packet.getFlags()/PositionFlag，现在是 Set<Relative>
 * 组件顺序 = (id, change, relatives)，已按上面的 record 声明核对。
 */
@Mixin(ClientboundPlayerPositionPacket.class)
public interface IPlayerPositionLookS2CPacket {

    @Accessor("id")
    int getId();

    @Accessor("change")
    PositionMoveRotation getChange();

    @Accessor("relatives")
    Set<Relative> getRelatives();
}
