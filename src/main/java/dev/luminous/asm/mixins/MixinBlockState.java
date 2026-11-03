package dev.luminous.asm.mixins;

import dev.luminous.Alien;
import dev.luminous.api.events.impl.BlockActivateEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/*
 * 26.2：BlockBehaviour.AbstractBlockState 改名为 BlockBehaviour.BlockStateBase，
 * 且原来的 {@code onUse(World, PlayerEntity, BlockHitResult)} 拆成
 * {@code useItemOn(ItemStack, Level, Player, InteractionHand, BlockHitResult)}
 * 与 {@code useWithoutItem(Level, Player, BlockHitResult)}。
 * 这里改为在 BlockStateBase#useItemOn 的 HEAD 发事件（右键点方块的第一入口，
 * 与原 onUse 的调用时机一致），不再用“继承父类 + @Override”的写法（26.2 构造器签名已变）。
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public class MixinBlockState {

    // 26.2: was @Override onUse(World, PlayerEntity, BlockHitResult) on BlockState
    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void onUse(ItemStack itemStack, Level level, Player player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> info) {
        Alien.EVENT_BUS.post(BlockActivateEvent.get((BlockState) (Object) this));
    }
}
