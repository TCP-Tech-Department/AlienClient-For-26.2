package dev.luminous.asm.mixins;

import dev.luminous.mod.modules.impl.movement.Velocity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 26.2: {@code FlowableFluid} → {@code FlowingFluid}，{@code getVelocity(...)} → {@code getFlow(...)}。
 * 旧实现 Redirect 了 {@code Iterator#hasNext}（1.21.1 用 while 迭代 Direction），26.2 改成
 * for-each 遍历 {@code Direction.Plane.HORIZONTAL}，注入点消失；改为在 getFlow 返回时直接归零，
 * 效果等价（水流不推动玩家）。
 */
@Mixin(FlowingFluid.class)
public class MixinFlowableFluid {

    @Inject(method = "getFlow", at = @At("RETURN"), cancellable = true)
    private void getVelocity_hasNext(BlockGetter level, BlockPos pos, FluidState fluidState, CallbackInfoReturnable<Vec3> cir) {
        if (Velocity.INSTANCE.isOn() && Velocity.INSTANCE.waterPush.getValue()) {
            cir.setReturnValue(Vec3.ZERO);
        }
    }
}
