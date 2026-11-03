package dev.luminous.mod.modules.impl.misc;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.render.Render2DUtil;
import dev.luminous.core.impl.PlayerManager;
import dev.luminous.mod.gui.PeekScreen;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
// 26.2：BLOCK_ENTITY_DATA 组件类型由 NbtComponent 变为 TypedEntityData<BlockEntityType<?>>
import net.minecraft.world.item.component.TypedEntityData;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.spongepowered.asm.mixin.Unique;

import java.awt.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.UUID;

public class ShulkerViewer extends Module {
    private static final ItemStack[] ITEMS = new ItemStack[27];
    public static ShulkerViewer INSTANCE;
    private static int offset;
    public final BooleanSetting toolTips =
            add(new BooleanSetting("ToolTips", true));
    public final BooleanSetting icon =
            add(new BooleanSetting("Icon", true));
    private final HashMap<UUID, Shulker> map = new HashMap<>();
    private final BooleanSetting peek =
            add(new BooleanSetting("Peek", false).setParent());
    private final SliderSetting renderTime =
            add(new SliderSetting("RenderTime", 10, 0, 100, .1, peek::isOpen).setSuffix("s"));
    private final SliderSetting xOffset = add(new SliderSetting("X", 0, 0, 1500, peek::isOpen));
    private final SliderSetting yOffset = add(new SliderSetting("Y", 120, 0, 1000, peek::isOpen));
    private final SliderSetting space =
            add(new SliderSetting("Space", 78, 0, 200, 1, peek::isOpen));

    public ShulkerViewer() {
        super("ShulkerViewer", Category.Misc);
        setChinese("潜影盒查看");
        INSTANCE = this;
    }

    public static void renderShulkerToolTip(GuiGraphicsExtractor context, int mouseX, int mouseY, ItemStack stack) {
        getItemsInContainerItem(stack, ITEMS);
        draw(context, mouseX, mouseY);
    }

    @Unique
    private static void draw(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        /*
         * 26.2 渲染迁移说明（旧的底层 GL 调用全部删除，且新框架都有等价路径）：
         *  - RenderSystem.disableDepthTest() → GuiGraphicsExtractor 按 stratum 顺序合批，
         *    深度/混合由 RenderPipeline（RenderPipelines.GUI / GUI_TEXTURED）决定，调用方不再碰 GL 状态；
         *  - GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT) → 同上，GUI 层没有需要手动清理的深度缓冲；
         *  - RenderSystem.enableLighting + DiffuseLighting.disableGuiDepthLighting()（GUI 物品光照）
         *    → 由 GuiGraphicsExtractor#item 内部的物品渲染管线处理。
         * 所以这里就是纯 GUI 层绘制：先画半透明底板，再逐格 item() + itemDecorations()。
         */

        mouseX += 8;
        mouseY -= 82;

        // 26.2：Render2DUtil 改为接收 GuiGraphicsExtractor（矩阵由 ctx.pose() 管理）
        Render2DUtil.drawRect(context, mouseX, mouseY, 176, 67, new Color(0, 0, 0, 120));

        int row = 0;
        int i = 0;
        for (ItemStack itemStack : ShulkerViewer.ITEMS) {
            // 26.2：drawItem → item，drawItemInSlot → itemDecorations
            context.item(itemStack, mouseX + 8 + i * 18, mouseY + 7 + row * 18);
            context.itemDecorations(mc.font, itemStack, mouseX + 8 + i * 18, mouseY + 7 + row * 18);
            i++;
            if (i >= 9) {
                i = 0;
                row++;
            }
        }
    }

    public static boolean hasItems(ItemStack itemStack) {
        ItemContainerContents container = itemStack.get(DataComponents.CONTAINER);
        if (container != null && container.nonEmptyItemCopyStream().findAny().isPresent()) return true;

        // 26.2：BLOCK_ENTITY_DATA → TypedEntityData（NbtComponent 已不存在），用 contains() 判键
        TypedEntityData<BlockEntityType<?>> data = itemStack.get(DataComponents.BLOCK_ENTITY_DATA);
        return data != null && data.contains("Items");
    }

    public static void getItemsInContainerItem(ItemStack itemStack, ItemStack[] items) {
        if (itemStack.getItem() == Items.ENDER_CHEST) {
            for (int i = 0; i < Alien.PLAYER.ENDERCHEST_ITEM.size(); i++) {
                items[i] = Alien.PLAYER.ENDERCHEST_ITEM.get(i);
            }
            return;
        }

        Arrays.fill(items, ItemStack.EMPTY);
        DataComponentMap components = itemStack.getComponents();

        if (components.has(DataComponents.CONTAINER)) {
            // 26.2：ContainerComponent 的内部列表不再需要 accessor，ItemContainerContents#copyInto 公开可读
            ItemContainerContents container = components.get(DataComponents.CONTAINER);
            if (container != null) {
                NonNullList<ItemStack> stacks = NonNullList.withSize(items.length, ItemStack.EMPTY);
                container.copyInto(stacks);
                for (int i = 0; i < stacks.size() && i < items.length; i++) {
                    if (!stacks.get(i).isEmpty()) items[i] = stacks.get(i);
                }
            }
        } else if (components.has(DataComponents.BLOCK_ENTITY_DATA)) {
            // 26.2：NbtComponent → TypedEntityData，NbtCompound → CompoundTag，NbtList → ListTag
            TypedEntityData<BlockEntityType<?>> data = components.get(DataComponents.BLOCK_ENTITY_DATA);

            if (data != null) {
                CompoundTag tag = data.copyTagWithoutId();
                ListTag nbt3 = tag.getListOrEmpty("Items");

                for (int i = 0; i < nbt3.size(); i++) {
                    CompoundTag entry = nbt3.getCompoundOrEmpty(i);
                    int slot = entry.getByteOr("Slot", (byte) 0); // Apparently shulker boxes can store more than 27 items, good job Mojang
                    // now NPEs when mc.level == null
                    if (slot >= 0 && slot < items.length)
                        items[slot] = parseItemStack(entry);
                }
            }
        }
    }

    /**
     * 26.2：ItemStack#fromNbtOrEmpty(RegistryManager, NbtCompound) 已移除，
     * 改为用 ItemStack.CODEC 在带注册表上下文的 NbtOps 上解析。
     */
    private static ItemStack parseItemStack(CompoundTag tag) {
        if (mc.level == null) return ItemStack.EMPTY;
        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, mc.level.registryAccess());
        return ItemStack.CODEC.parse(ops, tag).result().orElse(ItemStack.EMPTY);
    }

    public static boolean openContainer(ItemStack itemStack, ItemStack[] contents, boolean pause) {
        if (hasItems(itemStack) || itemStack.getItem() == Items.ENDER_CHEST) {
            getItemsInContainerItem(itemStack, contents);
            if (pause)
                PlayerManager.screenToOpen = new PeekScreen(itemStack, contents);
            else
                // 26.2：mc.setScreen → mc.setScreenAndShow
                mc.setScreenAndShow(new PeekScreen(itemStack, contents));
            return true;
        }
        return false;
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (peek.getValue()) {
            for (AbstractClientPlayer player : Alien.THREAD.getPlayers()) {
                ItemStack stack = player.getMainHandItem();
                if (!(stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock)) {
                    stack = player.getOffhandItem();
                }
                if (stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock) {
                    // 26.2：authlib GameProfile 仍是普通类，用 getId()/getName()
                    map.put(player.getGameProfile().id(), new Shulker(stack, player.getGameProfile().name()));
                }
            }
        }
    }

    @Override
    public void onRender2D(GuiGraphicsExtractor drawContext, float tickDelta) {
        if (peek.getValue()) {
            offset = 0;
            map.values().removeIf(shulker -> shulker.draw(drawContext));
        }
    }

    class Shulker {
        final ItemStack itemStack;
        final String name;
        private final Timer timer;

        public Shulker(ItemStack itemStack, String name) {
            this.itemStack = itemStack;
            this.timer = new Timer();
            this.name = name;
        }

        public boolean draw(GuiGraphicsExtractor context) {
            if (timer.passedS(renderTime.getValue())) return true;
            renderShulkerToolTip(context, xOffset.getValueInt() - 8, yOffset.getValueInt() + offset, itemStack);
            context.text(mc.font, name, xOffset.getValueInt(), yOffset.getValueInt() + offset - mc.font.lineHeight - 82, -1, true);
            offset += space.getValueInt();
            return false;
        }
    }
}
