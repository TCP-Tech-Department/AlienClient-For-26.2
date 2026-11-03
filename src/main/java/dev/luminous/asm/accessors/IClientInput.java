package dev.luminous.asm.accessors;

import net.minecraft.client.player.ClientInput;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/*
 * 26.2: 输入状态改为 record 体系（Input keyPresses + Vec2 moveVector），
 * 旧的 pressingForward / movementForward / jumping / sneaking 散装字段全部移除。
 * moveVector 在 26.2 是 protected（非 final），直接生成 setter 即可。
 */
@Mixin(ClientInput.class)
public interface IClientInput {
    @Accessor("moveVector")
    void setMoveVector(Vec2 moveVector);
}
