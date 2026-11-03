package dev.luminous.asm.mixins;

import dev.luminous.Alien;
import dev.luminous.api.events.impl.AmbientOcclusionEvent;
import dev.luminous.mod.modules.impl.player.PacketMine;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.class)
public abstract class MixinAbstractBlock {

    /**
     * 26.2: was {@code AbstractBlock#getAmbientOcclusionLightLevel} —— 官方名叫 {@code getShadeBrightness}，
     * 签名 (BlockState, BlockGetter, BlockPos)F，语义一致（返回值即 AO 亮度）。
     */
    @Inject(method = "getShadeBrightness", at = @At("HEAD"), cancellable = true)
    private void onGetAmbientOcclusionLightLevel(BlockState state, BlockGetter level, BlockPos pos, CallbackInfoReturnable<Float> cir) {
        AmbientOcclusionEvent event = Alien.EVENT_BUS.post(AmbientOcclusionEvent.get());

        if (event.lightLevel != -1) cir.setReturnValue(event.lightLevel);
    }

    /**
     * 26.2: {@code ShapeContext} → {@code CollisionContext}（net.minecraft.world.phys.shapes）；
     * {@code VoxelShapes.empty()} → {@code Shapes.empty()}。
     */
    @Inject(method = "getCollisionShape", at = @At("HEAD"), cancellable = true)
    private void onComputeNextCollisionBox(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (PacketMine.INSTANCE != null && pos.equals(PacketMine.getBreakPos()) && PacketMine.INSTANCE.noCollide.getValue() && PacketMine.ghost) {
            cir.setReturnValue(Shapes.empty());
        }
    }
}
