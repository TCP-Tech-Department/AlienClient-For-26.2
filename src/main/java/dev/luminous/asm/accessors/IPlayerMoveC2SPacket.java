package dev.luminous.asm.accessors;

import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 26.2: {@link ServerboundMovePlayerPacket} 的字段布局未变
 * （protected final double x/y/z、float yRot/xRot、boolean onGround），
 * 只是由扁平类改为带 Pos/PosRot/Rot/StatusOnly 子类的抽象基类，
 * accessor 挂在基类上对所有子类生效。
 */
@Mixin(ServerboundMovePlayerPacket.class)
public interface IPlayerMoveC2SPacket {
    @Mutable
    @Accessor("onGround")
    void setOnGround(boolean onGround);

    @Mutable
    @Accessor("xRot")
    void setPitch(float pitch);

    @Mutable
    @Accessor("yRot")
    void setYaw(float yaw);

    @Mutable
    @Accessor("x")
    void setX(double x);

    @Mutable
    @Accessor("z")
    void setZ(double z);
}
