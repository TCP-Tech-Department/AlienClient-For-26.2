package dev.luminous.asm.mixins;

import dev.luminous.mod.modules.impl.misc.ShulkerViewer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.2: {@code DrawContext#drawItemInSlot(Font,ItemStack,IILjava/lang/String;)} 改名
 * {@code GuiGraphicsExtractor#itemDecorations(...)}（同一个方法：绘制数量/耐久装饰）；
 * {@code drawItem(...)} → {@code item(ItemStack,int,int)}；堆叠矩阵从
 * {@code PoseStack matrices} 变成 2D 的 {@code Matrix3x2fStack pose()}。
 */
@Mixin(GuiGraphicsExtractor.class)
public abstract class MixinDrawContext {

    @Unique
    private static final ItemStack[] ITEMS = new ItemStack[27];

    @Inject(method = "itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V", at = @At("TAIL"))
    public void onDrawItem(Font font, ItemStack stack, int x, int y, String countOverride, CallbackInfo ci) {
        if (ShulkerViewer.INSTANCE.isOn() && ShulkerViewer.INSTANCE.icon.getValue() && stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock) {
            ShulkerViewer.getItemsInContainerItem(stack, ITEMS);
            for (ItemStack itemStack : ITEMS) {
                if (!itemStack.isEmpty()) {
                    Matrix3x2fStack pose = this.pose();
                    pose.pushMatrix();
                    pose.scale(0.5f, 0.5f);
                    this.item(itemStack, x * 2 + 20, y * 2 + 20);
                    pose.popMatrix();
                    return;
                }
            }
        }
    }

    @Shadow
    public abstract Matrix3x2fStack pose();

    @Shadow
    public abstract void item(ItemStack itemStack, int x, int y);
}
