package dev.luminous.asm.mixins;

import dev.luminous.Alien;
import dev.luminous.mod.modules.impl.misc.ShulkerViewer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import net.minecraft.client.input.MouseButtonEvent;

import static dev.luminous.api.utils.Wrapper.mc;

/**
 * 26.2: {@code HandledScreen} → {@code AbstractContainerScreen}；
 * 字段 {@code focusedSlot} → {@code hoveredSlot}，{@code x/y} → {@code leftPos/topPos}，
 * {@code handler} → {@code menu}，{@code playerScreenHandler} → {@code inventoryMenu}，
 * {@code getCursorStack()} → {@code getCarried()}；
 * {@code render(DrawContext,int,int,float)} → {@code extractRenderState(GuiGraphicsExtractor,int,int,float)}；
 * {@code mouseClicked(double,double,int)} → {@code mouseClicked(MouseButtonEvent, boolean)}。
 */
@Mixin(AbstractContainerScreen.class)
public abstract class MixinHandledScreen<T extends AbstractContainerMenu> {

    @Unique
    private static final ItemStack[] ITEMS = new ItemStack[27];
    @Shadow
    @Nullable
    protected Slot hoveredSlot;

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void mouseClicked(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && hoveredSlot != null && !hoveredSlot.getItem().isEmpty()
                && mc.player.containerMenu.getCarried().isEmpty() && ShulkerViewer.INSTANCE.isOn()) {
            ItemStack itemStack = hoveredSlot.getItem();
            if (ShulkerViewer.hasItems(itemStack) || itemStack.getItem() == Items.ENDER_CHEST && Alien.PLAYER.known) {
                cir.setReturnValue(ShulkerViewer.openContainer(hoveredSlot.getItem(), ITEMS, false));
            }
        }
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void onRender(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (ShulkerViewer.INSTANCE.isOn() && ShulkerViewer.INSTANCE.toolTips.getValue()) {
            if (hoveredSlot != null && !hoveredSlot.getItem().isEmpty() && mc.player.containerMenu.getCarried().isEmpty()) {
                if (ShulkerViewer.hasItems(hoveredSlot.getItem()) || hoveredSlot.getItem().getItem() == Items.ENDER_CHEST && Alien.PLAYER.known) {
                    ShulkerViewer.renderShulkerToolTip(context, mouseX, mouseY, hoveredSlot.getItem());
                }
            }
        }
    }
}
