package dev.luminous.asm.accessors;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 26.2: LocalPlayer 中 positionReminder / yRotLast / xRotLast 字段名与类型均未变，无需改名。
 */
@Mixin(LocalPlayer.class)
public interface IClientPlayerEntity {
    @Accessor("positionReminder")
    void setTicksSinceLastPositionPacketSent(int ticks);

    @Accessor("yRotLast")
    float getLastYaw();

    @Accessor("yRotLast")
    void setLastYaw(float yaw);

    @Accessor("xRotLast")
    float getLastPitch();

    @Accessor("xRotLast")
    void setLastPitch(float pitch);
}
