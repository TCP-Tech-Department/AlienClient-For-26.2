package dev.luminous.mod.gui;

import dev.luminous.mod.modules.impl.misc.ShulkerViewer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.awt.*;

import static dev.luminous.api.utils.Wrapper.mc;

public class PeekScreen extends ShulkerBoxScreen {
    // 26.2：Identifier.of 已改名为 withDefaultNamespace
    private final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/shulker_box.png");
    private final ItemStack[] contents;
    private final ItemStack storageBlock;

    public PeekScreen(ItemStack storageBlock, ItemStack[] contents) {
        // 26.2：SimpleInventory → SimpleContainer；ItemStack.getName → getHoverName
        super(new ShulkerBoxMenu(0, mc.player.getInventory(), new SimpleContainer(contents)), mc.player.getInventory(), storageBlock.getHoverName());
        this.contents = contents;
        this.storageBlock = storageBlock;
    }

    public static Color getShulkerColor(ItemStack shulkerItem) {
        if (shulkerItem.getItem() instanceof BlockItem blockItem) {
            Block block = blockItem.getBlock();
            if (block instanceof ShulkerBoxBlock shulkerBlock) {
                DyeColor dye = shulkerBlock.getColor();
                if (dye == null) return Color.WHITE;
                // 26.2：DyeColor.getEntityColor 改名为 getTextureDiffuseColor
                final int color = dye.getTextureDiffuseColor();
                return new Color(color);
            }
        }
        return Color.WHITE;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // 26.2：focusedSlot → hoveredSlot；getCursorStack → getCarried；setScreen → setScreenAndShow
        if (event.button() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && hoveredSlot != null && !hoveredSlot.getItem().isEmpty() && mc.player.containerMenu.getCarried().isEmpty()) {
            ItemStack itemStack = hoveredSlot.getItem();
            if (ShulkerViewer.hasItems(itemStack) || itemStack.getItem() == Items.ENDER_CHEST) {
                return ShulkerViewer.openContainer(hoveredSlot.getItem(), contents, false);
            } else if (itemStack.get(DataComponents.WRITTEN_BOOK_CONTENT) != null || itemStack.get(DataComponents.WRITABLE_BOOK_CONTENT) != null) {
                onClose();
                // 26.2：BookScreen 改名为 BookViewScreen，其内容容器 Contents.create 改为 BookAccess.fromItem（可能为 null）
                BookViewScreen.BookAccess access = BookViewScreen.BookAccess.fromItem(itemStack);
                if (access != null) {
                    mc.setScreenAndShow(new BookViewScreen(access));
                }
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        // 26.2：options.inventoryKey 改为 options.keyInventory，matchesKey 改为 matches(KeyEvent)
        if (event.key() == GLFW.GLFW_KEY_ESCAPE || mc.options.keyInventory.matches(event)) {
            onClose();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return false;
    }

    /**
     * 26.2：{@code drawBackground(DrawContext, float, int, int)} 改为
     * {@code extractBackground(GuiGraphicsExtractor, int, int, float)}。
     * {@code RenderSystem.setShaderColor} 已移除，改用 {@code blit} 的颜色参数给箱子贴图染色。
     */
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        // 先走父类（Screen 背景 + ShulkerBoxScreen 的原色贴图），再覆盖一层染色的贴图
        super.extractBackground(graphics, mouseX, mouseY, a);

        Color color = getShulkerColor(storageBlock);
        int i = (width - imageWidth) / 2;
        int j = (height - imageHeight) / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, i, j, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256, color.getRGB());
    }
}
