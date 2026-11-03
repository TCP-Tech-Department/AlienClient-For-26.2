package dev.luminous.mod.modules.impl.player;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.PlaySoundEvent;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.math.MathUtil;
import dev.luminous.asm.accessors.IMinecraftClient;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;

import java.awt.event.KeyEvent;

public class AutoTool extends Module {

    public AutoTool() {
        super("AutoTool", Category.Player);
        setChinese("自动工具");
    }

    private final BooleanSetting mine = add(new BooleanSetting("Mine", true));
    private final BooleanSetting fish = add(new BooleanSetting("Fish", true).setParent());
    private final BooleanSetting autoCast = add(new BooleanSetting("AutoCast", true, fish::isOpen));
    private final SliderSetting ticksAutoCast = add(new SliderSetting("TicksAutoCast", 10, 0, 60, fish::isOpen));
    private final SliderSetting ticksCatch = add(new SliderSetting("TicksCatch", 6, 0, 60, fish::isOpen));
    private final SliderSetting ticksThrow = add(new SliderSetting("TicksThrow", 14, 0, 60, fish::isOpen));
    private final BooleanSetting splashDetection = add(new BooleanSetting("SplashDetection", false, fish::isOpen));
    private final SliderSetting splashDetectionRange = add(new SliderSetting("DetectionRange", 10, 0, 60, fish::isOpen));

    @EventListener
    public void onUpdate(UpdateEvent event) {
        autoFish();
        autoTool();
    }

    public void autoTool() {
        if (!mine.getValue()) return;
        if (!(mc.hitResult instanceof BlockHitResult result)) return;
        BlockPos pos = result.getBlockPos();
        // 26.2: Level 无 isAir，改用 BlockState#isAir
        if (mc.level.getBlockState(pos).isAir())
            return;
        int tool = getTool(pos);
        if (tool != -1 && mc.options.keyAttack.isDown()) {
            mc.player.getInventory().setSelectedSlot(tool);
        }
    }

    private boolean ticksEnabled;
    private int ticksToRightClick;
    private int ticksData;

    private int autoCastTimer;
    private boolean autoCastEnabled;

    private int autoCastCheckTimer;

    @Override
    public void onEnable() {
        ticksEnabled = false;
        autoCastEnabled = false;
        autoCastCheckTimer = 0;
    }

    @EventListener
    private void onPlaySound(PlaySoundEvent event) {
        if (nullCheck()) return;
        if (!fish.getValue()) return;
        SoundInstance p = event.sound;
        // 26.2: Player#fishHook → Player#fishing
        FishingHook b = mc.player.fishing;
        if (b == null) return;
        // 26.2: SoundInstance#getId() → getIdentifier()
        if (p.getIdentifier().getPath().equals("entity.fishing_bobber.splash")) {
            if (!splashDetection.getValue() || MathUtil.distance(b.getX(), b.getY(), b.getZ(), p.getX(), p.getY(), p.getZ()) <= splashDetectionRange.getValue()) {
                ticksEnabled = true;
                ticksToRightClick = ticksCatch.getValueInt();
                ticksData = 0;
            }
        }
    }

    public void autoFish() {
        if (!fish.getValue()) return;
        // Auto cast
        if (autoCastCheckTimer <= 0) {
            autoCastCheckTimer = 30;

            if (autoCast.getValue() && !ticksEnabled && !autoCastEnabled && mc.player.fishing == null && mc.player.getMainHandItem().getItem() == Items.FISHING_ROD) {
                autoCastTimer = 0;
                autoCastEnabled = true;
            }
        } else {
            autoCastCheckTimer--;
        }

        // Check for auto cast timer
        if (autoCastEnabled) {
            autoCastTimer++;

            if (autoCastTimer > ticksAutoCast.getValue()) {
                autoCastEnabled = false;
                useItem();
            }
        }

        // Handle logic
        if (ticksEnabled && ticksToRightClick <= 0) {
            if (ticksData == 0) {
                useItem();
                ticksToRightClick = ticksThrow.getValueInt();
                ticksData = 1;
            } else if (ticksData == 1) {
                useItem();
                ticksEnabled = false;
            }
        }

        ticksToRightClick--;
    }

    /**
     * 26.2: Minecraft#doItemUse() 已移除，等价逻辑是 private 的 Minecraft#startUseItem()
     * （Minecraft.java:1677），通过 {@link IMinecraftClient#alienClient$startUseItem()} 暴露后直接调用，
     * 保留完整原版右键流程（冷却、命中结果、副手重试）。
     */
    private void useItem() {
        ((IMinecraftClient) mc).alienClient$startUseItem();
    }

    @EventListener
    private void onKey(KeyEvent event) {
        if (mc.options.keyUse.isDown()) ticksEnabled = false;
    }

    public static int getTool(final BlockPos pos) {
        int index = -1;
        float CurrentFastest = 1.0f;
        for (int i = 0; i < 9; ++i) {
            final ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack != ItemStack.EMPTY) {
                final float digSpeed = EnchantmentHelper.getItemEnchantmentLevel(mc.level.registryAccess().getOrThrow(Enchantments.EFFICIENCY), stack);
                final float destroySpeed = stack.getDestroySpeed(mc.level.getBlockState(pos));

                if (mc.level.getBlockState(pos).getBlock() instanceof AirBlock) return -1;
                if (digSpeed + destroySpeed > CurrentFastest) {
                    CurrentFastest = digSpeed + destroySpeed;
                    index = i;
                }
            }
        }
        return index;
    }
}
