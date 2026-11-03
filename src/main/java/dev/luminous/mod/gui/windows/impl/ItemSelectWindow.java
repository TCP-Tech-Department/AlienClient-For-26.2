package dev.luminous.mod.gui.windows.impl;

import com.google.common.collect.Lists;
import dev.luminous.api.utils.render.Render2DUtil;
import dev.luminous.core.Manager;
import dev.luminous.core.impl.CleanerManager;
import dev.luminous.core.impl.FontManager;
import dev.luminous.core.impl.TradeManager;
import dev.luminous.core.impl.XrayManager;
import dev.luminous.mod.gui.items.buttons.StringButton;
import dev.luminous.mod.gui.windows.WindowBase;
import net.minecraft.world.level.block.Block;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.language.I18n;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.world.item.Item;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.StringUtil;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.util.ArrayList;
import java.util.Objects;

import static dev.luminous.api.utils.Wrapper.mc;

public class ItemSelectWindow extends WindowBase {
    private final Manager manager;
    private final ArrayList<ItemPlate> itemPlates = new ArrayList<>();
    private final ArrayList<ItemPlate> allItems = new ArrayList<>();

    private boolean allTab = true, listening = false;
    private String search = "Search";

    public ItemSelectWindow(Manager manager) {
        this(mc.getWindow().getGuiScaledWidth() / 2f - 100, mc.getWindow().getGuiScaledHeight() / 2f - 150, 200, 300, manager);
    }

    public ItemSelectWindow(float x, float y, float width, float height, Manager manager) {
        super(x, y, width, height, "Items", null);
        this.manager = manager;
        refreshItemPlates();

        int id1 = 0;
        // 26.2：Registries.BLOCK 只是 key，实际注册表遍历用 BuiltInRegistries.BLOCK
        for (Block block : BuiltInRegistries.BLOCK) {
            allItems.add(new ItemPlate(id1, id1 * 20, block.asItem(), block.getDescriptionId()));
            id1++;
        }

        for (Item item : BuiltInRegistries.ITEM) {
            allItems.add(new ItemPlate(id1, id1 * 20, item, item.getDescriptionId()));
            id1++;
        }
    }

    @Override
    public void render(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        super.render(context, mouseX, mouseY);
        boolean hover1 = Render2DUtil.isHovered(mouseX, mouseY, getX() + getWidth() - 90, getY() + 3, 70, 10);

        Render2DUtil.drawRect(context, getX() + getWidth() - 90, getY() + 3, 70, 10, hover1 ? new Color(0xC5838383, true) : new Color(0xC5575757, true));
        FontManager.small.drawString(context, search, getX() + getWidth() - 86, getY() + 7, new Color(0xD5D5D5).getRGB());

        int tabColor1 = allTab ? new Color(0xD5D5D5).getRGB() : Color.GRAY.getRGB();
        int tabColor2 = allTab ? Color.GRAY.getRGB() : new Color(0xBDBDBD).getRGB();
        // 26.2：Tessellator / DEBUG_LINE_STRIP 已移除，标签栏折线改为逐段 drawLine
        float[] xs = {getX() + 1.5f, getX() + 8, getX() + 8, getX() + 48, getX() + 54, getX() + 52, getX() + 52, getX() + 92, getX() + 100, getX() + getWidth() - 1};
        float[] ys = {getY() + 29, getY() + 29, getY() + 19, getY() + 19, getY() + 29, getY() + 25, getY() + 19, getY() + 19, getY() + 29, getY() + 29};
        int[] cs = {Color.DARK_GRAY.getRGB(), tabColor1, tabColor1, tabColor1, tabColor1, tabColor2, tabColor2, tabColor2, Color.GRAY.getRGB(), Color.DARK_GRAY.getRGB()};
        for (int s = 0; s < xs.length - 1; s++) {
            Render2DUtil.drawLine(context, xs[s], ys[s], xs[s + 1], ys[s + 1], cs[s]);
        }

        FontManager.small.drawString(context, "All", getX() + 25, getY() + 25, tabColor1);
        FontManager.small.drawString(context, "Selected", getX() + 60, getY() + 25, tabColor2);

        if (!allTab && itemPlates.isEmpty()) {
            FontManager.ui.drawCenteredString(context, "It's empty here yet",
                    getX() + getWidth() / 2f, getY() + getHeight() / 2f, new Color(0xBDBDBD).getRGB());
        }
        context.enableScissor((int) getX(), (int) (getY() + 30), (int) (getX() + getWidth()), (int) (getY() + getHeight() - 1));

        for (ItemPlate itemPlate : (allTab ? allItems : itemPlates)) {
            if (itemPlate.offset + getY() + 25 + getScrollOffset() > getY() + getHeight() || itemPlate.offset + getScrollOffset() + getY() + 10 < getY())
                continue;

            // 26.2：Matrix3x2fStack 是 2D，push/pop 改名为 pushMatrix/popMatrix，translate 只接受 x/y
            context.pose().pushMatrix();
            context.pose().translate(getX() + 6, itemPlate.offset + getY() + 32 + getScrollOffset());
            context.item(itemPlate.item().getDefaultInstance(), 0, 0);
            context.pose().popMatrix();

            FontManager.ui.drawString(context, I18n.get(itemPlate.key()), getX() + 26, itemPlate.offset + getY() + 38 + getScrollOffset(), new Color(0xBDBDBD).getRGB());

            boolean hover2 = Render2DUtil.isHovered(mouseX, mouseY, getX() + getWidth() - 20, itemPlate.offset + getY() + 35 + getScrollOffset(), 11, 11);

            Render2DUtil.drawRect(context, getX() + getWidth() - 20, itemPlate.offset + getY() + 35 + getScrollOffset(), 11, 11,
                    hover2 ? new Color(0xC57A7A7A, true) : new Color(0xC5575757, true));

            boolean selected = itemPlates.stream().anyMatch(sI -> Objects.equals(sI.key, itemPlate.key));

            if (allTab && !selected) {
                FontManager.ui.drawString(context, "+", getX() + getWidth() - 17, itemPlate.offset + getY() + 37 + getScrollOffset(), -1);
            } else {
                FontManager.ui.drawString(context, "-", getX() + getWidth() - 16.5, itemPlate.offset + getY() + 37.5 + getScrollOffset(), -1);
            }
        }
        setMaxElementsHeight((allTab ? allItems : itemPlates).size() * 20);
        context.disableScissor();
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);

        if (Render2DUtil.isHovered(mouseX, mouseY, getX() + 8, getY() + 19, 52, 19)) {
            allTab = true;
            resetScroll();
        }

        if (Render2DUtil.isHovered(mouseX, mouseY, getX() + 54, getY() + 19, 70, 19)) {
            allTab = false;
            resetScroll();
        }

        if (Render2DUtil.isHovered(mouseX, mouseY, getX() + getWidth() - 90, getY() + 3, 70, 10)) {
            listening = true;
            search = "";
        }

        ArrayList<ItemPlate> copy = Lists.newArrayList(allTab ? allItems : itemPlates);
        for (ItemPlate itemPlate : copy) {
            if ((int) (itemPlate.offset + getY() + 50) + getScrollOffset() > getY() + getHeight())
                continue;

            String name = itemPlate.key().replace("item.minecraft.", "").replace("block.minecraft.", "");

            if (Render2DUtil.isHovered(mouseX, mouseY, getX() + getWidth() - 20, itemPlate.offset + getY() + 35 + getScrollOffset(), 10, 10)) {
                boolean selected = itemPlates.stream().anyMatch(sI -> Objects.equals(sI.key(), itemPlate.key));

                if (allTab && !selected) {
                    if (manager instanceof TradeManager m) {
                        if (!m.inWhitelist(name)) {
                            m.add(name);
                            refreshItemPlates();
                        }
                    } else if (manager instanceof CleanerManager m) {
                        if (!m.inList(name)) {
                            m.add(name);
                            refreshItemPlates();
                        }
                    } else if (manager instanceof XrayManager m) {
                        if (!m.inWhitelist(name)) {
                            m.add(name);
                            refreshItemPlates();
                        }
                    }
                } else {
                    if (manager instanceof TradeManager m) {
                        m.remove(name);
                    } else if (manager instanceof CleanerManager m) {
                        m.remove(name);
                    } else if (manager instanceof XrayManager m) {
                        m.remove(name);
                    }
                    refreshItemPlates();
                }
            }
        }
    }

    @Override
    public void keyPressed(KeyEvent event) {
        int keyCode = event.key();
        if (keyCode == GLFW.GLFW_KEY_F && (InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL) || InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_RIGHT_CONTROL))) {
            listening = !listening;
            return;
        }

        if (listening) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_ESCAPE -> {
                    listening = false;
                    search = "Search";
                    refreshAllItems();
                }

                case GLFW.GLFW_KEY_BACKSPACE -> {
                    search = StringButton.removeLastChar(search);
                    refreshAllItems();

                    if (Objects.equals(search, "")) {
                        listening = false;
                        search = "Search";
                    }
                }

                case GLFW.GLFW_KEY_SPACE -> search = search + " ";
            }
        }
    }

    @Override
    public void charTyped(CharacterEvent event) {
        // 26.2：charTyped(char,int) → charTyped(CharacterEvent)，字符校验用 StringUtil.isAllowedChatCharacter
        if (StringUtil.isAllowedChatCharacter(event.codepoint()) && listening) {
            search = search + event.codepointAsString();
            refreshAllItems();
        }
    }

    private void refreshItemPlates() {
        itemPlates.clear();

        int id = 0;

        for (Item item : BuiltInRegistries.ITEM)
            if (manager instanceof TradeManager m) {
                if (m.inWhitelist(item.getDescriptionId())) {
                    itemPlates.add(new ItemPlate(id, id * 20, item.asItem(), item.getDescriptionId()));
                    id++;
                }
            } else if (manager instanceof CleanerManager m) {
                if (m.inList(item.getDescriptionId())) {
                    itemPlates.add(new ItemPlate(id, id * 20, item.asItem(), item.getDescriptionId()));
                    id++;
                }
            } else if (manager instanceof XrayManager m) {
                if (m.inWhitelist(item.getDescriptionId())) {
                    itemPlates.add(new ItemPlate(id, id * 20, item.asItem(), item.getDescriptionId()));
                    id++;
                }
            }
    }

    private void refreshAllItems() {
        allItems.clear();
        resetScroll();
        int id1 = 0;

        for (Item item : BuiltInRegistries.ITEM) {
            // 26.2：Item.getName() 需要 ItemStack，改用它自带的默认实例取显示名
            if (search.equals("Search") || search.isEmpty() || item.getDescriptionId().contains(search) || item.getName(item.getDefaultInstance()).getString().toLowerCase().contains(search.toLowerCase())) {
                allItems.add(new ItemPlate(id1, id1 * 20, item, item.getDescriptionId()));
                id1++;
            }
        }
    }

    private record ItemPlate(float id, float offset, Item item, String key) {
    }
}