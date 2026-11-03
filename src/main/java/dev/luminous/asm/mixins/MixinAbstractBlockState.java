package dev.luminous.asm.mixins;

import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.render.Ambience;
import net.minecraft.world.level.block.state.BlockBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 26.2: {@code BlockBehaviour.AbstractBlockState} 改名为 {@code BlockBehaviour.BlockStateBase}；
 * Yarn 的 {@code getLuminance()} 对应 Mojang 的 {@code getLightEmission()}（返回 int）。
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public class MixinAbstractBlockState {
    @Inject(method = "getLightEmission", at = @At("HEAD"), cancellable = true)
    public void getLuminanceHook(CallbackInfoReturnable<Integer> cir) {
        if (Module.nullCheck()) return;
        if (Ambience.INSTANCE.customLuminance.getValue()) {
            cir.setReturnValue(Ambience.INSTANCE.luminance.getValueInt());
        }
    }
}
