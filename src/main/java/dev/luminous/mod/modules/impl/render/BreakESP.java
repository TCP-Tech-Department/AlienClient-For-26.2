package dev.luminous.mod.modules.impl.render;

import dev.luminous.Alien;
import dev.luminous.api.utils.math.Easing;
import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.api.utils.render.ColorUtil;
import dev.luminous.api.utils.render.Render3DUtil;
import dev.luminous.core.impl.BreakManager;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.ColorSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.world.level.block.state.BlockState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AirItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import java.awt.*;
import java.text.DecimalFormat;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.phys.Vec3;

public class BreakESP extends Module {
    public static BreakESP INSTANCE;
    private final BooleanSetting progress = add(new BooleanSetting("Progress", true));
    private final SliderSetting damage = add(new SliderSetting("Damage", 1.0f, 0.0f, 2.0f, 0.01));
    private final ColorSetting box = add(new ColorSetting("AABB", new Color(198, 176, 12, 255)).injectBoolean(true));
    private final ColorSetting fill = add(new ColorSetting("Fill", new Color(198, 176, 12, 78)).injectBoolean(true));
    private final ColorSetting boxFriend = add(new ColorSetting("FriendBox", new Color(30, 45, 169, 255)).injectBoolean(true));
    private final ColorSetting fillFriend = add(new ColorSetting("FriendFill", new Color(30, 45, 169, 78)).injectBoolean(true));
    private final EnumSetting<Easing> ease = add(new EnumSetting<>("Ease", Easing.CubicInOut));

    private final BooleanSetting second = add(new BooleanSetting("Second", true));
    private final ColorSetting secondBox = add(new ColorSetting("SecondBox", new Color(255, 255, 255, 255)).injectBoolean(true));
    private final ColorSetting secondFill = add(new ColorSetting("SecondFill", new Color(255, 255, 255, 100)).injectBoolean(true));
    final DecimalFormat df = new DecimalFormat("0.0");

    public BreakESP() {
        super("BreakESP", Category.Render);
        setChinese("挖掘显示");
        INSTANCE = this;
    }

    final Color startColor = new Color(255, 6, 6);
    final Color endColor = new Color(0, 255, 12);
    final Color doubleColor = new Color(255, 179, 96);

    private Color getFillColor(Player player) {
        return Alien.FRIEND.isFriend(player) ? fillFriend.getValue() : fill.getValue();
    }

    private Color getBoxColor(Player player) {
        return Alien.FRIEND.isFriend(player) ? boxFriend.getValue() : box.getValue();
    }

    @Override
    public void onRender3D(PoseStack matrixStack) {
        for (BreakManager.BreakData breakData : Alien.BREAK.breakMap.values()) {
            if (breakData == null || breakData.getEntity() == null) continue;
            Player player = (Player) breakData.getEntity();
            double size = 0.5 * (1 - breakData.fade.ease(ease.getValue()));
            // 26.2: AABB#shrink → contract / inflate（负 shrink 等于 inflate）
            AABB cbox = new AABB(breakData.pos).contract(size, size, size).inflate(size, size, size);
            if (fill.booleanValue) {
                Render3DUtil.drawFill(cbox, getFillColor(player));
            }
            if (box.booleanValue) {
                Render3DUtil.drawBox(cbox, getBoxColor(player));
            }
            Render3DUtil.drawText3D(player.getName().getString(), Vec3.atCenterOf(breakData.pos).add(0, progress.getValue() ? 0.15 : 0, 0), -1);
            if (progress.getValue()) {
                Vec3 progressPos = Vec3.atCenterOf(breakData.pos).add(0, -0.15, 0);
                Render3DUtil.drawText3D(Component.literal(breakData.failed ? "§4Failed" : (breakData.complete ? "Broke" : df.format(Math.min(1, breakData.timer.getMs() / breakData.breakTime) * 100))), progressPos.x, progressPos.y, progressPos.z, 0, 0, 1, breakData.complete ? (mc.level.getBlockState(breakData.pos).isAir() ? endColor.getRGB() : startColor.getRGB()) : ColorUtil.fadeColor(startColor, endColor, (double) breakData.timer.getMs() / breakData.breakTime).getRGB());
            }
        }
        if (second.getValue())
            for (int i : Alien.BREAK.doubleMap.keySet()) {
                BreakManager.BreakData breakData = Alien.BREAK.doubleMap.get(i);
                if (breakData == null || breakData.getEntity() == null || mc.level.getBlockState(breakData.pos).isAir()) {
                    Alien.BREAK.doubleMap.remove(i);
                    continue;
                }
                BreakManager.BreakData singleBreakData = Alien.BREAK.breakMap.get(i);
                if (singleBreakData != null) {
                    if (singleBreakData.pos.equals(breakData.pos)) continue;
                }
                double size = 0.5 * (1 - breakData.fade.ease(ease.getValue()));
                AABB cbox = new AABB(breakData.pos).contract(size, size, size).inflate(size, size, size);
                if (secondFill.booleanValue) {
                    Render3DUtil.drawFill(cbox, secondFill.getValue());
                }
                if (secondBox.booleanValue) {
                    Render3DUtil.drawBox(cbox, secondBox.getValue());
                }
                Render3DUtil.drawText3D(breakData.getEntity().getName().getString(), Vec3.atCenterOf(breakData.pos).add(0, 0.15, 0), -1);
                Render3DUtil.drawText3D("Double", Vec3.atCenterOf(breakData.pos).add(0, -0.15, 0), doubleColor.getRGB());
            }
    }

    public static double getBreakTime(BlockPos pos, boolean extraBreak) {
        int slot = getTool(pos);
        if (slot == -1) {
            slot = mc.player.getInventory().getSelectedSlot();
        }
        return getBreakTime(pos, slot, extraBreak ? 1 : INSTANCE.damage.getValue());
    }

    static int getTool(BlockPos pos) {

        AtomicInteger slot = new AtomicInteger();
        slot.set(-1);
        float CurrentFastest = 1.0f;
        for (Map.Entry<Integer, ItemStack> entry : InventoryUtil.getInventoryAndHotbarSlots().entrySet()) {
            if (!(entry.getValue().getItem() instanceof AirItem)) {
                // 26.2: EnchantmentHelper#getLevel → getItemEnchantmentLevel(Holder, ItemStack)
                float digSpeed = EnchantmentHelper.getItemEnchantmentLevel(mc.level.registryAccess().getOrThrow(Enchantments.EFFICIENCY), entry.getValue());
                // 26.2: ItemStack#getMiningSpeedMultiplier → getDestroySpeed
                float destroySpeed = entry.getValue().getDestroySpeed(mc.level.getBlockState(pos));
                if (digSpeed + destroySpeed > CurrentFastest) {
                    CurrentFastest = digSpeed + destroySpeed;
                    slot.set(entry.getKey());
                }
            }
        }
        return slot.get();
    }

    static double getBreakTime(BlockPos pos, int slot, double damage) {
        return (1 / getBlockStrength(pos, mc.player.getInventory().getItem(slot)) / 20 * 1000 * damage);
    }

    static float getBlockStrength(BlockPos position, ItemStack itemStack) {
        BlockState state = mc.level.getBlockState(position);
        // 26.2: BlockState#getHardness → getDestroySpeed
        float hardness = state.getDestroySpeed(mc.level, position);
        if (hardness < 0) {
            return 0;
        }
        // 26.2: isToolRequired → requiresCorrectToolForDrops；isSuitableFor → isCorrectToolForDrops
        float i = !state.requiresCorrectToolForDrops() || itemStack.isCorrectToolForDrops(state) ? 30 : 100;
        return getDigSpeed(state, itemStack) / hardness / i;
    }

    static float getDigSpeed(BlockState state, ItemStack itemStack) {
        float digSpeed = getDestroySpeed(state, itemStack);
        if (digSpeed > 1) {
            int efficiencyModifier = EnchantmentHelper.getItemEnchantmentLevel(mc.level.registryAccess().getOrThrow(Enchantments.EFFICIENCY), itemStack);
            if (efficiencyModifier > 0 && !itemStack.isEmpty()) {
                digSpeed += (float) (StrictMath.pow(efficiencyModifier, 2) + 1);
            }
        }
        return (digSpeed < 0 ? 0 : digSpeed);
    }

    static float getDestroySpeed(BlockState state, ItemStack itemStack) {
        float destroySpeed = 1;
        if (itemStack != null && !itemStack.isEmpty()) {
            destroySpeed *= itemStack.getDestroySpeed(state);
        }
        return destroySpeed;
    }
}
