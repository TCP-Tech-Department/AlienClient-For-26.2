package dev.luminous.asm.mixins;

import dev.luminous.Alien;
import dev.luminous.api.events.impl.PlaceBlockEvent;
import dev.luminous.mod.modules.Module;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockItem.class)
public class MixinBlockItem {

    /**
     * 26.2: was {@code place(ItemPlacementContext, BlockState)Z} →
     * 官方签名 {@code place(BlockPlaceContext)InteractionResult}（BlockState 参数已移除，
     * 放置后的状态直接从世界读）。{@code ItemPlacementContext} → {@code BlockPlaceContext}，
     * {@code context.level()/blockPosition()} → {@code context.getLevel()/getClickedPos()}，
     * {@code level.isClient} → {@code level.isClientSide()}。
     */
    @Inject(method = "place(Lnet/minecraft/world/item/context/BlockPlaceContext;)Lnet/minecraft/world/InteractionResult;", at = @At("RETURN"))
    private void onPlace(@NotNull BlockPlaceContext context, CallbackInfoReturnable<InteractionResult> info) {
        if (Module.nullCheck()) return;
        if (context.getLevel().isClientSide()) {
            Alien.EVENT_BUS.post(PlaceBlockEvent.get(context.getClickedPos(),
                    context.getLevel().getBlockState(context.getClickedPos()).getBlock()));
        }
    }
}
