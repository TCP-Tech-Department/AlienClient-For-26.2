package dev.luminous.asm.mixins;

import dev.luminous.mod.modules.impl.movement.NoSlow;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Block.class)
public abstract class MixinBlock {

    /**
     * 26.2: {@code getVelocityMultiplier()} 已改名 {@code getFriction()}（Yarn 的 velocity multiplier
     * 在官方名里就是方块的摩擦系数，具体值由 {@code BlockBehaviour.Properties#friction} 设定）。
     * 注入点从 HEAD 改为 RETURN —— 旧写法在 HEAD 读 {@code getReturnValueF()} 恒为 0，
     * RETURN 才能真正判断「原值 < 1」。
     */
    @Inject(method = "getFriction", at = @At("RETURN"), cancellable = true)
    private void onGetVelocityMultiplier(CallbackInfoReturnable<Float> cir) {
        if (NoSlow.INSTANCE.soulSand()) {
            if (cir.getReturnValueF() < 1.0f)
                cir.setReturnValue(1F);
        }
    }

    /*
     * 26.2: 原静态注入点 {@code Block#shouldDrawSide(BlockState, BlockGetter, BlockPos, Direction, BlockPos)Z}
     * 改名为 {@code Block#shouldRenderFace(BlockState, BlockState, Direction)Z}，且不再接收 BlockGetter/BlockPos，
     * Xray#modifyDrawSide 需要的世界上下文在这里拿不到。
     * 该逻辑已迁移到 {@link MixinBlockModelRenderer#onShouldDrawSide}（ModelBlockRenderer#shouldRenderFace
     * 仍带 BlockAndTintGetter + BlockPos），此处保留说明，不再重复注入。
     */
}
