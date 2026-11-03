package dev.luminous.asm.mixins;

import dev.luminous.mod.modules.impl.misc.ShulkerViewer;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

/**
 * 26.2: 原注入点 {@code ShulkerBoxBlock#appendTooltip(ItemStack, Item.TooltipContext, List<Component>, TooltipType)}
 * 已消失 —— 潜影盒的内容提示改由数据组件 {@code DataComponents.CONTAINER}
 * （{@code ItemContainerContents} 实现 {@code TooltipProvider}）在
 * {@code ItemStack#addDetailsToTooltip} 里统一生成，{@code ShulkerBoxBlock} 的父类链上已无 tooltip 方法。
 * <p>
 * 直接在 {@code ItemContainerContents#addToTooltip} 上注入拿不到「当前 stack 是不是潜影盒」的上下文
 * （会影响收纳袋等所有容器组件），所以挂到 {@code ItemStack#addToTooltip(DataComponentType, ...)}：
 * 它是唯一能同时拿到 {@code this}（当前 ItemStack）与组件类型的地方，只拦截
 * {@code DataComponents.CONTAINER} + 潜影盒方块物品，语义与旧的
 * 「ShulkerViewer.toolTips 开启时取消原版 appendTooltip」完全等价，其它容器（箱子/收纳袋/木桶）不受影响。
 * <p>
 * 类名沿用 {@code MixinShulkerBoxBlock}（保持 alien.mixins.json 的登记项不变），目标类已改为 {@link ItemStack}。
 */
@Mixin(ItemStack.class)
public class MixinShulkerBoxBlock {

    @Inject(
            method = "addToTooltip(Lnet/minecraft/core/component/DataComponentType;Lnet/minecraft/world/item/Item$TooltipContext;Lnet/minecraft/world/item/component/TooltipDisplay;Ljava/util/function/Consumer;Lnet/minecraft/world/item/TooltipFlag;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onAppendTooltip(DataComponentType<?> type, Item.TooltipContext context, TooltipDisplay display,
                                 Consumer<Component> consumer, TooltipFlag flag, CallbackInfo ci) {
        if (type != DataComponents.CONTAINER) {
            return;
        }
        if (!(ShulkerViewer.INSTANCE.isOn() && ShulkerViewer.INSTANCE.toolTips.getValue())) {
            return;
        }
        ItemStack stack = (ItemStack) (Object) this;
        if (stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock) {
            ci.cancel();
        }
    }
}
