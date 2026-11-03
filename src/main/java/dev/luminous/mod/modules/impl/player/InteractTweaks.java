package dev.luminous.mod.modules.impl.player;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.asm.accessors.IMinecraftClient;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;

public class InteractTweaks extends Module {

    public static InteractTweaks INSTANCE;
    public final BooleanSetting noEntityTrace =
            add(new BooleanSetting("NoEntityTrace", true).setParent());
    public final BooleanSetting onlyPickaxe =
            add(new BooleanSetting("OnlyPickaxe", true, noEntityTrace::isOpen));
    public final BooleanSetting multiTask =
            add(new BooleanSetting("MultiTask", true));
    public final BooleanSetting respawn =
            add(new BooleanSetting("Respawn", true));
    public final BooleanSetting ghostHand =
            add(new BooleanSetting("IgnoreBedrock", false));
    private final BooleanSetting noAbort =
            add(new BooleanSetting("NoMineAbort", false));
    private final BooleanSetting noReset =
            add(new BooleanSetting("NoMineReset", false));
    private final BooleanSetting noDelay =
            add(new BooleanSetting("NoMineDelay", false));
    private final BooleanSetting noInteract =
            add(new BooleanSetting("NoInteract", false));
    private final BooleanSetting pickaxeSwitch =
            add(new BooleanSetting("SwitchEat", false).setParent());
    private final BooleanSetting allowSword =
            add(new BooleanSetting("Sword", true, pickaxeSwitch::isOpen));
    private final BooleanSetting allowPickaxe =
            add(new BooleanSetting("Pickaxe", true, pickaxeSwitch::isOpen));
    private final BooleanSetting reach =
            add(new BooleanSetting("Reach", false));
    public final SliderSetting blockRange = add(new SliderSetting("BlockRange", 5, 0, 15, 0.1, reach::getValue));
    public final SliderSetting entityRange = add(new SliderSetting("EntityRange", 5, 0, 15, 0.1, reach::getValue));

    private final SliderSetting delay = add(new SliderSetting("UseDelay", 4, 0, 4, 1));
    public boolean isActive;
    boolean swapped = false;
    int lastSlot = 0;

    public InteractTweaks() {
        super("InteractTweaks", Category.Player);
        setChinese("交互调整");
        INSTANCE = this;
    }

    // 26.2: PickaxeItem / SwordItem 已从 26.2 移除，物品改由标签区分
    private boolean isPickaxe() {
        return mc.player.getMainHandItem().is(ItemTags.PICKAXES);
    }

    private boolean isSword() {
        return mc.player.getMainHandItem().is(ItemTags.SWORDS);
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (respawn.getValue() && mc.gui.screen() instanceof DeathScreen) {
            // 26.2: requestRespawn() → respawn()；setScreen → gui.setScreen
            mc.player.respawn();
            mc.gui.setScreen(null);
        }
        // 26.2: Minecraft#itemUseCooldown → private int rightClickDelay（getter/setter 见 IMinecraftClient）。
        // 原版语义恢复：冷却剩余 <= 4 - UseDelay 时直接清零，实现“物品使用不卡冷却”。
        IMinecraftClient accessor = (IMinecraftClient) mc;
        if (accessor.getRightClickDelay() <= 4 - delay.getValueInt()) {
            accessor.setRightClickDelay(0);
        }
        if (pickaxeSwitch.getValue()) {
            if (!(isPickaxe() && allowPickaxe.getValue()) && (!isSword() || !allowSword.getValue()) && mc.player.getMainHandItem().getItem() != Items.ENCHANTED_GOLDEN_APPLE && mc.player.getMainHandItem().getItem() != Items.GOLDEN_APPLE) {
                swapped = false;
                return;
            }
            int gapple = InventoryUtil.findItem(Items.ENCHANTED_GOLDEN_APPLE);
            if (gapple == -1) {
                gapple = InventoryUtil.findItem(Items.GOLDEN_APPLE);
            }
            if (gapple == -1) {
                if (swapped) {
                    InventoryUtil.switchToSlot(lastSlot);
                    swapped = false;
                }
                return;
            }
            if (mc.options.keyUse.isDown()) {
                if ((isPickaxe() && allowPickaxe.getValue() || (isSword() && allowSword.getValue())) && mc.player.getOffhandItem().getItem() != Items.ENCHANTED_GOLDEN_APPLE && mc.player.getMainHandItem().getItem() != Items.GOLDEN_APPLE) {
                    lastSlot = mc.player.getInventory().getSelectedSlot();
                    InventoryUtil.switchToSlot(gapple);
                    swapped = true;
                }
            } else if (swapped) {
                InventoryUtil.switchToSlot(lastSlot);
                swapped = false;
            }
        }
    }

    @EventListener
    public void onPacket(PacketEvent.Send event) {
        if (nullCheck() || !noInteract.getValue() || !(event.getPacket() instanceof ServerboundUseItemOnPacket packet)) {
            return;
        }
        // 26.2: getBlockHitResult() → getHitResult()
        Block block = mc.level.getBlockState(packet.getHitResult().getBlockPos()).getBlock();
        if (!mc.player.isShiftKeyDown()) {
            if (block instanceof ChestBlock || block instanceof EnderChestBlock || block instanceof AnvilBlock) {
                event.cancel();
            }
        }
    }

    @Override
    public void onDisable() {
        isActive = false;
    }

    public boolean reach() {
        return isOn() && reach.getValue();
    }

    public boolean noAbort() {
        return isOn() && noAbort.getValue() && !mc.options.keyUse.isDown();
    }

    public boolean noReset() {
        return isOn() && noReset.getValue();
    }

    public boolean noDelay() {
        return isOn() && noDelay.getValue();
    }

    public boolean multiTask() {
        return isOn() && multiTask.getValue();
    }

    public boolean noEntityTrace() {
        if (isOff() || !noEntityTrace.getValue()) return false;

        if (onlyPickaxe.getValue()) {
            return isPickaxe() || mc.player.isUsingItem() && !isSword();
        }
        return true;
    }

    public boolean ghostHand() {
        return isOn() && ghostHand.getValue() && !mc.options.keyUse.isDown() && !mc.options.keyShift.isDown();
    }
}
