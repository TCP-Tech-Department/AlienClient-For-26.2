package dev.luminous.mod.modules.impl.client;

import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class ItemsCounter extends Module {
    public static ItemsCounter INSTANCE;
    private final BooleanSetting hideEmpty = add(new BooleanSetting("HideEmpty", true));
    private final BooleanSetting crystal = add(new BooleanSetting("Crystal", true));
    private final BooleanSetting xp = add(new BooleanSetting("XP", true));
    private final BooleanSetting pearl = add(new BooleanSetting("Pearl", true));
    private final BooleanSetting obsidian = add(new BooleanSetting("Obsidian", true));
    private final BooleanSetting egApple = add(new BooleanSetting("E-GApple", true));
    private final BooleanSetting gApple = add(new BooleanSetting("GApple", true));
    private final BooleanSetting totem = add(new BooleanSetting("Totem", true));
    private final BooleanSetting web = add(new BooleanSetting("Web", true));
    private final BooleanSetting anchor = add(new BooleanSetting("Anchor", true));
    private final BooleanSetting glowstone = add(new BooleanSetting("Glowstone", true));
    private final BooleanSetting piston = add(new BooleanSetting("Piston", true));
    private final BooleanSetting redstone = add(new BooleanSetting("RedStone", true));
    private final BooleanSetting enderChest = add(new BooleanSetting("EnderChest", true));
    private final BooleanSetting firework = add(new BooleanSetting("Firework", true));
    private final SliderSetting xOffset = add(new SliderSetting("X", 100, 0, 1500));
    private final SliderSetting yOffset = add(new SliderSetting("Y", 100, 0, 1000));
    private final SliderSetting offset = add(new SliderSetting("Offset", 18, 0, 30));

    // 26.2: ItemStack 构造会读取已绑定的数据组件（Holder.Reference#components），
    // 而模块在 onInitialize 阶段就被 new 出来，此时组件还没绑定，直接在字段初始化里 new 会抛
    // "NullPointerException: Components not bound yet"。所以改成首次渲染时惰性创建。
    private ItemStack crystalStack;
    private ItemStack xpStack;
    private ItemStack pearlStack;
    private ItemStack obsidianStack;
    private ItemStack eGappleStack;
    private ItemStack gappleStack;
    private ItemStack totemStack;
    private ItemStack webStack;
    private ItemStack anchorStack;
    private ItemStack glowstoneStack;
    private ItemStack pistonStack;
    private ItemStack redstoneStack;
    private ItemStack enderChestStack;
    private ItemStack fireworkStack;
    int x, y;
    GuiGraphicsExtractor drawContext;

    private void ensureStacks() {
        if (crystalStack != null) return;
        crystalStack = new ItemStack(Items.END_CRYSTAL);
        xpStack = new ItemStack(Items.EXPERIENCE_BOTTLE);
        pearlStack = new ItemStack(Items.ENDER_PEARL);
        obsidianStack = new ItemStack(Items.OBSIDIAN);
        eGappleStack = new ItemStack(Items.ENCHANTED_GOLDEN_APPLE);
        gappleStack = new ItemStack(Items.GOLDEN_APPLE);
        totemStack = new ItemStack(Items.TOTEM_OF_UNDYING);
        webStack = new ItemStack(Items.COBWEB);
        anchorStack = new ItemStack(Items.RESPAWN_ANCHOR);
        glowstoneStack = new ItemStack(Items.GLOWSTONE);
        pistonStack = new ItemStack(Items.PISTON);
        redstoneStack = new ItemStack(Items.REDSTONE_BLOCK);
        enderChestStack = new ItemStack(Items.ENDER_CHEST);
        fireworkStack = new ItemStack(Items.FIREWORK_ROCKET);
    }

    public ItemsCounter() {
        super("Items", Category.Client);
        setChinese("物品数量");
        INSTANCE = this;
    }

    @Override
    public void onRender2D(GuiGraphicsExtractor drawContext, float tickDelta) {
        ensureStacks();
        this.drawContext = drawContext;
        x = this.xOffset.getValueInt() - offset.getValueInt();
        y = this.yOffset.getValueInt();
        if (crystal.getValue()) {
            crystalStack.setCount(getItemCount(Items.END_CRYSTAL));
            drawItem(crystalStack);
        }
        if (xp.getValue()) {
            xpStack.setCount(getItemCount(Items.EXPERIENCE_BOTTLE));
            drawItem(xpStack);
        }
        if (pearl.getValue()) {
            pearlStack.setCount(getItemCount(Items.ENDER_PEARL));
            drawItem(pearlStack);
        }
        if (obsidian.getValue()) {
            obsidianStack.setCount(getItemCount(Items.OBSIDIAN));
            drawItem(obsidianStack);
        }
        if (egApple.getValue()) {
            eGappleStack.setCount(getItemCount(Items.ENCHANTED_GOLDEN_APPLE));
            drawItem(eGappleStack);
        }
        if (gApple.getValue()) {
            gappleStack.setCount(getItemCount(Items.GOLDEN_APPLE));
            drawItem(gappleStack);
        }
        if (totem.getValue()) {
            totemStack.setCount(getItemCount(Items.TOTEM_OF_UNDYING));
            drawItem(totemStack);
        }
        if (web.getValue()) {
            webStack.setCount(getItemCount(Items.COBWEB));
            drawItem(webStack);
        }
        if (anchor.getValue()) {
            anchorStack.setCount(getItemCount(Items.RESPAWN_ANCHOR));
            drawItem(anchorStack);
        }
        if (glowstone.getValue()) {
            glowstoneStack.setCount(getItemCount(Items.GLOWSTONE));
            drawItem(glowstoneStack);
        }
        if (piston.getValue()) {
            int pistonCount = InventoryUtil.getItemCount(PistonBaseBlock.class);
            if (pistonCount > 0 || !hideEmpty.getValue()) {
                x += offset.getValueInt();
                pistonStack.setCount(Math.max(1, pistonCount));
                drawItem(pistonStack);
            }
        }
        if (redstone.getValue()) {
            redstoneStack.setCount(getItemCount(Items.REDSTONE_BLOCK));
            drawItem(redstoneStack);
        }
        if (enderChest.getValue()) {
            enderChestStack.setCount(getItemCount(Items.ENDER_CHEST));
            drawItem(enderChestStack);
        }
        if (firework.getValue()) {
            fireworkStack.setCount(getItemCount(Items.FIREWORK_ROCKET));
            drawItem(fireworkStack);
        }
    }

    private int getItemCount(Item item) {
        int i = InventoryUtil.getItemCount(item);
        if (hideEmpty.getValue() && i == 0) {
            return 0;
        } else {
            x += offset.getValueInt();
            return Math.max(i, 1);
        }
    }

    private void drawItem(ItemStack itemStack) {
        drawContext.item(itemStack, x, y);
        drawContext.itemDecorations(mc.font, itemStack, x, y);
    }
}
