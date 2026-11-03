package dev.luminous.asm.mixins;

import dev.luminous.Alien;
import dev.luminous.api.events.impl.ClickBlockEvent;
import dev.luminous.api.events.impl.InteractBlockEvent;
import dev.luminous.api.events.impl.InteractItemEvent;
import dev.luminous.mod.modules.impl.player.InteractTweaks;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 26.2 改名对照（ClientPlayerInteractionManager → MultiPlayerGameMode）：
 * <pre>
 * isCurrentlyBreaking(BlockPos)      → sameDestroyTarget(BlockPos)
 * selectedStack（字段）              → destroyingItem
 * updateBlockBreakingProgress(...)   → continueDestroyBlock(BlockPos, Direction)
 * interactItem(Player, Hand)         → useItem(Player, InteractionHand)
 * interactBlock(Player, Hand, Hit)   → useItemOn(LocalPlayer, InteractionHand, BlockHitResult)
 * cancelBlockBreaking()              → stopDestroyBlock()
 * attackBlock(BlockPos, Direction)   → startDestroyBlock(BlockPos, Direction)
 * </pre>
 */
@Mixin(MultiPlayerGameMode.class)
public class MixinClientPlayerInteractionManager {

    @Shadow
    private ItemStack destroyingItem;

    @ModifyVariable(method = "sameDestroyTarget", at = @At("STORE"))
    private ItemStack stack(ItemStack stack) {
        return InteractTweaks.INSTANCE.noReset() ? this.destroyingItem : stack;
    }

    @ModifyConstant(method = "continueDestroyBlock", constant = @Constant(intValue = 5))
    private int MiningCooldownFix(int value) {
        return InteractTweaks.INSTANCE.noDelay() ? 0 : value;
    }

    @Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
    private void hookInteractItem(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        InteractItemEvent event = InteractItemEvent.getPre(hand);
        Alien.EVENT_BUS.post(event);
        if (event.isCancelled()) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }

    @Inject(method = "useItem", at = @At("RETURN"))
    private void hookInteractItemReturn(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        Alien.EVENT_BUS.post(InteractItemEvent.getPost(hand));
    }

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void hookInteractBlock(LocalPlayer player, InteractionHand hand, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        InteractBlockEvent event = InteractBlockEvent.getPre(hand);
        Alien.EVENT_BUS.post(event);
        if (event.isCancelled()) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }

    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void hookInteractBlockReturn(LocalPlayer player, InteractionHand hand, BlockHitResult hitResult, CallbackInfoReturnable<InteractionResult> cir) {
        Alien.EVENT_BUS.post(InteractBlockEvent.getPost(hand));
    }

    @Inject(method = "stopDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void hookCancelBlockBreaking(CallbackInfo callbackInfo) {
        if (InteractTweaks.INSTANCE.noAbort())
            callbackInfo.cancel();
    }

    @Inject(method = "startDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void onAttackBlock(BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir) {
        ClickBlockEvent event = ClickBlockEvent.get(pos, direction);
        Alien.EVENT_BUS.post(event);
        if (event.isCancelled()) {
            cir.setReturnValue(false);
        }
    }
}
