package dev.luminous.mod.modules.impl.render;

import dev.luminous.Alien;
import dev.luminous.api.utils.math.MathUtil;
import dev.luminous.api.utils.render.ColorUtil;
import dev.luminous.api.utils.render.Render2DUtil;
import dev.luminous.api.utils.render.TextUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.client.TextRadar;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.ColorSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3x2fStack;

import java.awt.*;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 玩家名牌。
 * <p>
 * 旧实现（1.21.1）用 {@code mc.getBufferBuilders().getEntityVertexConsumers()} +
 * {@code Font#drawLayer} + {@code ItemRenderer#renderItem} 在世界空间拼一个朝向相机的
 * 即时模式广告牌（含装备/附魔/耐久/物品名），并且全程 {@code glDepthFunc(GL_ALWAYS)}（穿墙可见）。
 * 26.2 移除了全部即时模式路径，{@code BakedModel}/{@code ModelTransformationMode} 的
 * 手工物品渲染也不再可用。
 * <p>
 * 26.2 可选的两条世界文字路径都核实过：
 * <ul>
 *   <li>{@code OrderedSubmitNodeCollector#submitNameTag(PoseStack, Vec3, int, Component, boolean, int,
 *       CameraRenderState)}：原版名牌用的就是这个，但它只有在实体渲染提交阶段（
 *       {@code EntityRenderer#submitNameTag}）才拿得到 {@code SubmitNodeCollector}，
 *       一个名牌只能是一行 {@link Component}，画不了装备图标、耐久条、附魔缩写；
 *       模块层拿不到那个收集器（{@code LevelRenderer#submitNodeStorage} 是 private）。</li>
 *   <li>{@code TextGizmo}：世界坐标单行文字，同样画不了物品图标，且 depth/排序与原版名牌不一致。</li>
 * </ul>
 * 所以名牌改走 GUI 层（{@code onRender2D}，由 {@code Hud#extractRenderState} 的 TAIL 触发）：
 * <ol>
 *   <li>{@link TextUtil#worldSpaceToScreenSpace(Vec3)}（内部是
 *       {@code GameRenderer#projectPointToScreen}）把玩家头顶世界坐标投影成 GUI 坐标；</li>
 *   <li>用 {@link GuiGraphicsExtractor#pose()}（{@link Matrix3x2fStack}）平移到该点并按
 *       「每世界方块像素数」缩放，复现旧实现随距离变化的缩放（Scale / Factor / Distance / Scaling）；</li>
 *   <li>文字用 {@code ctx.text}，装备用 {@code ctx.fakeItem}（owner 传 null，与原实现一致），
 *       数量/耐久条用 {@code ctx.fill}。</li>
 * </ol>
 * 26.2 里 {@code GuiGraphicsExtractor#text}/{@code #item}/{@code #fill}/{@code #innerFill} 都会把当前
 * {@code pose} 快照进 {@code GuiTextRenderState}/{@code GuiItemRenderState}/
 * {@code ColoredRectangleRenderState}，所以 {@code pose().scale(...)} 对文字、物品图标、矩形一律生效。
 * <p>
 * 与旧实现的差异只有一条且不构成功能退化：GUI 层没有世界深度，名牌始终画在最上层 ——
 * 旧实现全程 {@code GL_ALWAYS}，本来就是穿墙可见的，观感一致。
 */
public class NameTags
        extends Module {
    public static NameTags INSTANCE;
    final ColorSetting colorConfig =
            add(new ColorSetting("Color", new Color(255, 255, 255)));
    final ColorSetting friendConfig =
            add(new ColorSetting("Friend", new Color(155, 155, 255)).injectBoolean(true));
    final ColorSetting invisibleConfig =
            add(new ColorSetting("Invisible", new Color(200, 200, 200)).injectBoolean(true));
    final ColorSetting died =
            add(new ColorSetting("Died", new Color(180, 0, 0)).injectBoolean(true));
    final ColorSetting sneakingConfig =
            add(new ColorSetting("Sneaking", new Color(200, 200, 0)).injectBoolean(true));
    final ColorSetting rectConfig =
            add(new ColorSetting("Rectangle", new Color(0, 0, 0, 100)).injectBoolean(true));
    final BooleanSetting armorConfig = add(new BooleanSetting("Armor", true).setParent());
    final BooleanSetting drawItemConfig = add(new BooleanSetting("DrawItem", true, armorConfig::isOpen));
    final SliderSetting offsetConfig = add(new SliderSetting("Offset", -20, -30, 10, 0.01, armorConfig::isOpen));
    final BooleanSetting enchantmentsConfig = add(new BooleanSetting("Enchantments", true));
    final BooleanSetting durabilityConfig = add(new BooleanSetting("Durability", true).setParent());
    final BooleanSetting forceBarConfig = add(new BooleanSetting("ForceBar", true, durabilityConfig::isOpen));
    final BooleanSetting itemNameConfig = add(new BooleanSetting("ItemName", false));
    final BooleanSetting entityIdConfig = add(new BooleanSetting("EntityId", false));
    final BooleanSetting gamemodeConfig = add(new BooleanSetting("Gamemode", false));
    final BooleanSetting pingConfig = add(new BooleanSetting("Ping", true));
    final BooleanSetting healthConfig = add(new BooleanSetting("Health", true));
    final BooleanSetting totemsConfig = add(new BooleanSetting("Totems", false));
    // 26.2 新增：原实现没有「显示距离」这一项，这里补上（默认关闭，不影响既有显示）
    final BooleanSetting distanceDisplay = add(new BooleanSetting("ShowDistance", false));
    final SliderSetting scaleConfig = add(new SliderSetting("Scale", 1, 0, 3, 0.1));
    final BooleanSetting factorConfig = add(new BooleanSetting("Factor", true).setParent());
    final SliderSetting scalingConfig = add(new SliderSetting("Scaling", 1, 0, 3, 0.1, factorConfig::isOpen));
    final SliderSetting distanceConfig = add(new SliderSetting("Distance", 6, 0, 20, 0.1, factorConfig::isOpen));
    final SliderSetting heightConfig = add(new SliderSetting("Height", 0, -3, 3, 0.01));

    public NameTags() {
        super("NameTags", "Renders info on player NameTags", Category.Render);
        setChinese("名字标签");
        INSTANCE = this;
    }

    @Override
    public void onRender2D(GuiGraphicsExtractor ctx, float tickDelta) {
        if (mc.player == null || mc.level == null || mc.gameRenderer == null || mc.getCameraEntity() == null) {
            return;
        }

        Vec3 cameraPos = mc.gameRenderer.mainCamera().position();
        Vec3 lookDirection = Vec3.directionFromRotation(mc.gameRenderer.mainCamera().xRot(), mc.gameRenderer.mainCamera().yRot());
        int guiWidth = ctx.guiWidth();
        int guiHeight = ctx.guiHeight();

        for (Player player : Alien.THREAD.getPlayers()) {
            if (player == null) {
                continue;
            }
            if ((!died.booleanValue && !player.isAlive())
                    || player == mc.player && mc.options.getCameraType().isFirstPerson()
                    || !invisibleConfig.booleanValue && player.isInvisible()) {
                continue;
            }

            String info = getNametagInfo(player);
            Vec3 renderPosition = MathUtil.getRenderPosition(player, tickDelta);
            // 26.2: Entity#getHeight → getBbHeight
            double eyeY = renderPosition.y + player.getBbHeight() + (player.isShiftKeyDown() ? 0.4 : 0.43) + heightConfig.getValueFloat();
            Vec3 worldPos = new Vec3(renderPosition.x, eyeY, renderPosition.z);

            // 相机背后的点会被透视除法翻到屏幕另一侧，必须先剔除
            if (worldPos.subtract(cameraPos).dot(lookDirection) <= 0.05) {
                continue;
            }
            Vec3 screen = TextUtil.worldSpaceToScreenSpace(worldPos);
            if (screen.z < -1.0 || screen.z > 1.0) {
                continue;
            }
            float margin = 256.0f;
            if (screen.x < -margin || screen.x > guiWidth + margin || screen.y < -margin || screen.y > guiHeight + margin) {
                continue;
            }

            float guiScale = getNameTagScale(worldPos, screen, cameraPos);
            if (guiScale <= 0.0f) {
                continue;
            }
            // 26.2: Font#getWidth → Font#width
            int width = mc.font.width(info);
            renderInfo(ctx, info, width / 2.0f, player, screen.x, screen.y, guiScale);
        }
    }

    /**
     * 把旧实现的「世界空间 0.025 * Scale」换算成 GUI 像素缩放。
     * <p>
     * 1 世界方块在屏幕上的像素数由投影结果反推（比手算 FOV 更稳，且自动跟随 Zoom），
     * 再套用旧实现的远距离附加缩放（Factor / Distance / Scaling）。
     */
    private float getNameTagScale(Vec3 worldPos, Vec3 screen, Vec3 cameraPos) {
        Vec3 oneBlockUp = TextUtil.worldSpaceToScreenSpace(worldPos.add(0, 1, 0));
        double pixelsPerBlock = Math.abs(screen.y - oneBlockUp.y);
        if (!Double.isFinite(pixelsPerBlock) || pixelsPerBlock < 1.0) {
            // 退化时用垂直 FOV 兜底
            double distance = Math.max(cameraPos.distanceTo(worldPos), 0.5);
            double fov = Math.toRadians(mc.gameRenderer.mainCamera().getFov());
            pixelsPerBlock = (mc.getWindow().getGuiScaledHeight() / 2.0) / (distance * Math.tan(fov / 2.0));
        }
        pixelsPerBlock = Math.min(pixelsPerBlock, 512.0);

        float scale = 0.025f * scaleConfig.getValueFloat();
        if (factorConfig.getValue()) {
            double distance = cameraPos.distanceTo(worldPos);
            double farDistance = distanceConfig.getValueFloat();
            if (distance > farDistance) {
                scale += (float) ((distance - farDistance) * 0.0025f * scalingConfig.getValueFloat());
            }
        }
        return (float) (scale * pixelsPerBlock);
    }

    private void renderInfo(GuiGraphicsExtractor ctx, String info, float width, Player entity,
                            double screenX, double screenY, float guiScale) {
        Matrix3x2fStack pose = ctx.pose();
        pose.pushMatrix();
        pose.translate((float) screenX, (float) screenY);
        pose.scale(guiScale, guiScale);

        if (rectConfig.booleanValue) {
            // 26.2: Font#fontHeight → lineHeight；pose 缩放后 fill 会自动跟随
            Render2DUtil.drawRect(ctx, -width - 2.0f, -1.0f, width * 2.0f + 3.0f,
                    mc.font.lineHeight + 1.0f, rectConfig.getValue());
        }
        drawWithShadow(ctx, info, -width, 0.0f, getNametagColor(entity));
        if (armorConfig.getValue()) {
            renderItems(ctx, entity);
        }

        pose.popMatrix();
    }

    /**
     * 26.2: {@code Font#drawLayer}/{@code Font#draw} + VertexConsumerProvider 已移除，
     * 统一走 {@link GuiGraphicsExtractor#text(net.minecraft.client.gui.Font, String, int, int, int, boolean)}；
     * dropShadow=true 同时绘制阴影与本体，等价于旧实现「先画阴影层再画前景层」。
     */
    private void drawWithShadow(GuiGraphicsExtractor ctx, String info, float x, float y, int color) {
        ctx.text(mc.font, info, (int) x, (int) y, color, true);
    }

    private void renderItems(GuiGraphicsExtractor ctx, Player player) {
        List<ItemStack> displayItems = new ArrayList<>();
        if (!player.getOffhandItem().isEmpty()) {
            displayItems.add(player.getOffhandItem());
        }
        // 26.2: Inventory#armor 已移除，护甲改由 EntityEquipment 承载
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack armorStack = player.getItemBySlot(slot);
            if (!armorStack.isEmpty()) {
                displayItems.add(armorStack);
            }
        }
        if (!player.getMainHandItem().isEmpty()) {
            displayItems.add(player.getMainHandItem());
        }
        Collections.reverse(displayItems);
        float x = 0;
        int n11 = 0;
        for (ItemStack stack : displayItems) {
            x -= 8;
            // 26.2: ItemEnchantments#getSize → size
            if (stack.getEnchantments().size() > n11) {
                n11 = stack.getEnchantments().size();
            }
        }
        float y = offsetConfig.getValueFloat();
        Matrix3x2fStack pose = ctx.pose();
        for (ItemStack stack : displayItems) {
            if (drawItemConfig.getValue()) {
                // 26.2: ItemRenderer 的 GUI 物品渲染 → GuiGraphicsExtractor#fakeItem
                // （用 fakeItem 而不是 item：旧实现取模型时 owner 传的是 null，
                //  用 mc.player 当 owner 会让别的玩家的装备套上本地玩家的模型状态）
                // 与旧实现一致：图标比 offsetConfig 低 1px，因为旧实现是 translate(x, y + 1) 后再画 16x16
                ctx.fakeItem(stack, (int) x, (int) (y + 1.0f));
            }
            renderItemOverlay(ctx, stack, x, y + 2.5f);

            // 旧实现把耐久/附魔文字放在 0.5 缩放矩阵里（半尺寸字形），这里用 pose 复现
            if (durabilityConfig.getValue()) {
                pose.pushMatrix();
                pose.translate(x + 2.0f, y + 11.5f);
                pose.scale(0.5f, 0.5f);
                renderDurability(ctx, stack);
                pose.popMatrix();
            }
            if (enchantmentsConfig.getValue()) {
                renderEnchants(ctx, stack, x + 2.0f, y + 7.0f);
            }
            x += 16;
        }

        ItemStack heldItem = player.getMainHandItem();
        if (heldItem.isEmpty()) {
            return;
        }
        if (itemNameConfig.getValue()) {
            pose.pushMatrix();
            pose.translate(0.0f, y - 4.5f + enchantOffset(n11));
            pose.scale(0.5f, 0.5f);
            renderItemName(ctx, heldItem);
            pose.popMatrix();
        }
    }

    private void renderItemOverlay(GuiGraphicsExtractor ctx, ItemStack stack, float x, float y) {
        if (stack.getCount() != 1) {
            String string = String.valueOf(stack.getCount());
            drawWithShadow(ctx, string, x + 17 - mc.font.width(string), y + 9.0f, -1);
        }
        // 26.2: isItemBarVisible → isBarVisible，getItemBarStep → getBarWidth，getItemBarColor → getBarColor
        if (stack.isBarVisible() || (stack.isDamageableItem() && forceBarConfig.getValue())) {
            int i = stack.getBarWidth();
            int j = stack.getBarColor();
            int k = (int) x + 2;
            int l = (int) y + 13;
            // 26.2: Colors.BLACK 移除，直接用不透明黑
            ctx.fill(k, l, k + 13, l + 2, 0xFF000000);
            ctx.fill(k, l, k + i, l + 1, j | 0xFF000000);
        }
    }

    private void renderDurability(GuiGraphicsExtractor ctx, ItemStack itemStack) {
        // 26.2: isDamageable → isDamageableItem，getDamage → getDamageValue
        if (!itemStack.isDamageableItem()) {
            return;
        }
        int n = itemStack.getMaxDamage();
        int n2 = itemStack.getDamageValue();
        int durability = (int) ((n - n2) / ((float) n) * 100.0f);
        drawWithShadow(ctx, durability + "%", 0.0f, 0.0f,
                ColorUtil.hslToColor((float) (n - n2) / (float) n * 120.0f, 100.0f, 50.0f, 1.0f).getRGB());
    }

    private void renderEnchants(GuiGraphicsExtractor ctx, ItemStack itemStack, float x, float y) {
        if (itemStack.getItem() == Items.ENCHANTED_GOLDEN_APPLE) {
            drawScaledText(ctx, "God", x, y, 0xffc34e41);
            return;
        }
        // 26.2: hasEnchantments → isEnchanted
        if (!itemStack.isEnchanted()) {
            return;
        }
        // 26.2: EnchantmentHelper#getEnchantments 移除，直接用 ItemStack#getEnchantments
        ItemEnchantments enchants = itemStack.getEnchantments();

        float n2 = 0;
        // 26.2: RegistryEntry<Enchantment> → Holder<Enchantment>；getEnchantments() → keySet()
        for (Holder<Enchantment> enchantment : enchants.keySet()) {
            int lvl = enchants.getLevel(enchantment);
            StringBuilder enchantString = new StringBuilder();
            // 26.2: Enchantment 的本地化名在 record 组件 description 里
            String translatedName = enchantment.value().description().getString().replace("Enchantment ", "");
            if (translatedName.contains("Vanish")) {
                enchantString.append("§cVan");
            } else if (translatedName.contains("Bind")) {
                enchantString.append("§cBind");
            } else {
                int maxLen = lvl > 1 ? 2 : 3;
                if (translatedName.length() > maxLen) {
                    translatedName = translatedName.substring(0, maxLen);
                }
                enchantString.append(translatedName);
                enchantString.append(lvl);
            }
            drawScaledText(ctx, enchantString.toString(), x, y + n2, -1);
            n2 -= 4.5f;
        }
    }

    /** 半尺寸文字（对应旧实现在 scale(0.5) 矩阵里绘制）。 */
    private void drawScaledText(GuiGraphicsExtractor ctx, String text, float x, float y, int color) {
        Matrix3x2fStack pose = ctx.pose();
        pose.pushMatrix();
        pose.translate(x, y);
        pose.scale(0.5f, 0.5f);
        drawWithShadow(ctx, text, 0.0f, 0.0f, color);
        pose.popMatrix();
    }

    private float enchantOffset(final int n) {
        if (!enchantmentsConfig.getValue() || n <= 2) {
            return 0;
        }
        float value = -2;
        value -= (n - 3) * 4.5f;
        return value;
    }

    private void renderItemName(GuiGraphicsExtractor ctx, ItemStack itemStack) {
        // 26.2: ItemStack#getName → getHoverName
        String itemName = itemStack.getHoverName().getString();
        // 本方法已经在 scale(0.5) 的 pose 里（见 renderItems），字形宽度仍是完整 font 宽度，
        // 因此居中要退半宽；旧实现是 ((0 - width) * 2) with width = fontWidth / 4，等价于 -fontWidth / 2。
        drawWithShadow(ctx, itemName, -mc.font.width(itemName) / 2.0f, 0.0f, -1);
    }

    private String getNametagInfo(Player player) {
        final StringBuilder info = new StringBuilder();
        if (gamemodeConfig.getValue()) {
            if (player.isCreative()) {
                info.append(ChatFormatting.GOLD);
                info.append("[C] ");
            } else if (player.isSpectator()) {
                info.append(ChatFormatting.GRAY);
                info.append("[I] ");
            } else {
                info.append(ChatFormatting.BOLD);
                info.append("[S] ");
            }
        }
        if (pingConfig.getValue()) {
            info.append(getEntityPing(player));
            info.append("ms ");
            info.append(ChatFormatting.RESET);
        }
        info.append(player.getName().getString());
        info.append(" ");
        if (entityIdConfig.getValue()) {
            info.append("ID: ");
            info.append(player.getId());
            info.append(" ");
        }
        if (healthConfig.getValue()) {
            double health = player.getHealth() + player.getAbsorptionAmount();

            ChatFormatting hcolor;
            if (health > 18) {
                hcolor = ChatFormatting.GREEN;
            } else if (health > 16) {
                hcolor = ChatFormatting.DARK_GREEN;
            } else if (health > 12) {
                hcolor = ChatFormatting.YELLOW;
            } else if (health > 8) {
                hcolor = ChatFormatting.GOLD;
            } else if (health > 4) {
                hcolor = ChatFormatting.RED;
            } else {
                hcolor = ChatFormatting.DARK_RED;
            }
            String phealth = df.format(health);
            info.append(hcolor);
            info.append(phealth);
            info.append(" ");
        }
        if (totemsConfig.getValue()) {
            int totems = Alien.POP.getPop(player);
            if (totems > 0) {

                ChatFormatting c = TextRadar.getPopColor(totems);
                info.append(c);
                info.append(-totems);
                info.append(" ");
            }
        }
        if (distanceDisplay.getValue()) {
            // 26.2: Camera#getPos → position()；距离取相机到玩家碰撞箱原点（与方块距离一致）
            double distance = mc.gameRenderer.mainCamera().position().distanceTo(player.position());
            info.append(ChatFormatting.WHITE);
            info.append(df.format(distance));
            info.append("m ");
        }
        return info.toString().trim();
    }

    private String getEntityPing(Player entity) {
        if (mc.getConnection() == null) return "§7-1";

        // 26.2: PlayerListEntry → PlayerInfo；getPlayerListEntry → getPlayerInfo；getUuid → getUUID
        PlayerInfo playerListEntry = mc.getConnection().getPlayerInfo(entity.getUUID());
        if (playerListEntry == null) return "§7-1";

        int ping = playerListEntry.getLatency();
        ChatFormatting color;
        if (ping >= 200) {
            color = ChatFormatting.RED;
        } else if (ping >= 100) {
            color = ChatFormatting.YELLOW;
        } else {
            color = ChatFormatting.GREEN;
        }

        return color.toString() + ping;
    }

    final DecimalFormat df = new DecimalFormat("0.0");

    private int getNametagColor(Player player) {
        if (friendConfig.booleanValue && player.getDisplayName() != null && Alien.FRIEND.isFriend(player)) {
            return friendConfig.getValue().getRGB();
        }
        if (invisibleConfig.booleanValue && player.isInvisible()) {
            return invisibleConfig.getValue().getRGB();
        }
        if (sneakingConfig.booleanValue && player.isShiftKeyDown()) {
            return sneakingConfig.getValue().getRGB();
        }
        if (!player.isAlive()) {
            return died.getValue().getRGB();
        }
        return colorConfig.getValue().getRGB();
    }
}
