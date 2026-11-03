package dev.luminous.mod.modules.impl.combat;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.api.utils.world.BlockUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.player.PacketMine;
import dev.luminous.mod.modules.settings.impl.BindSetting;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.client.gui.screens.inventory.ShulkerBoxScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.inventory.Slot;

public class AutoRegear extends Module {
    public static AutoRegear INSTANCE;
    public final BooleanSetting rotate = add(new BooleanSetting("Rotate", true));
    public final Timer timeoutTimer = new Timer();
    final int[] stealCountList = new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    private final BooleanSetting autoDisable = add(new BooleanSetting("AutoDisable", true));
    private final SliderSetting disableTime =
            add(new SliderSetting("DisableTime", 500, 0, 1000));
    private final BooleanSetting place = add(new BooleanSetting("Place", true));
    private final BooleanSetting inventory = add(new BooleanSetting("InventorySwap", true));
    private final BooleanSetting preferOpen = add(new BooleanSetting("PerferOpen", true));
    private final BooleanSetting open = add(new BooleanSetting("Open", true));
    private final SliderSetting range = add(new SliderSetting("MaxRange", 4.0f, 0.0f, 6f, .1));
    private final SliderSetting minRange = add(new SliderSetting("MinRange", 1.0f, 0.0f, 3f, .1));
    private final BooleanSetting mine = add(new BooleanSetting("Mine", true));
    private final BooleanSetting take = add(new BooleanSetting("Take", true));
    private final BooleanSetting smart = add(new BooleanSetting("Smart", true, take::getValue).setParent());
    private final BooleanSetting forceMove = add(new BooleanSetting("ForceQuickMove", true, () -> take.getValue() && smart.isOpen()));
    private final SliderSetting crystal = add(new SliderSetting("Crystal", 256, 0, 512, () -> take.getValue() && smart.isOpen()));
    private final SliderSetting exp = add(new SliderSetting("Exp", 256, 0, 512, () -> take.getValue() && smart.isOpen()));
    private final SliderSetting totem = add(new SliderSetting("Totem", 6, 0, 36, () -> take.getValue() && smart.isOpen()));
    private final SliderSetting gapple = add(new SliderSetting("Gapple", 128, 0, 512, () -> take.getValue() && smart.isOpen()));
    private final SliderSetting obsidian = add(new SliderSetting("Obsidian", 64, 0, 512, () -> take.getValue() && smart.isOpen()));
    private final SliderSetting web = add(new SliderSetting("Web", 64, 0, 512, () -> take.getValue() && smart.isOpen()));
    private final SliderSetting glowstone = add(new SliderSetting("Glowstone", 128, 0, 512, () -> take.getValue() && smart.isOpen()));
    private final SliderSetting anchor = add(new SliderSetting("Anchor", 128, 0, 512, () -> take.getValue() && smart.isOpen()));
    private final SliderSetting pearl = add(new SliderSetting("Pearl", 16, 0, 64, () -> take.getValue() && smart.isOpen()));
    private final SliderSetting piston = add(new SliderSetting("Piston", 64, 0, 512, () -> take.getValue() && smart.isOpen()));
    private final SliderSetting redstone = add(new SliderSetting("RedStone", 64, 0, 512, () -> take.getValue() && smart.isOpen()));
    private final SliderSetting bed = add(new SliderSetting("Bed", 256, 0, 512, () -> take.getValue() && smart.isOpen()));
    private final SliderSetting speed = add(new SliderSetting("Speed", 1, 0, 8, () -> take.getValue() && smart.isOpen()));
    private final SliderSetting resistance = add(new SliderSetting("Resistance", 1, 0, 8, () -> take.getValue() && smart.isOpen()));
    private final SliderSetting strength = add(new SliderSetting("Strength", 1, 0, 8, () -> take.getValue() && smart.isOpen()));
    private final BindSetting placeKey = add(new BindSetting("PlaceKey", -1));
    private final Timer timer = new Timer();
    private final List<BlockPos> openList = new ArrayList<>();
    public BlockPos placePos = null;
    private BlockPos openPos;
    private boolean opend = false;
    private boolean on = false;

    public AutoRegear() {
        super("AutoRegear", Category.Combat);
        setChinese("自动补给");
        INSTANCE = this;
    }

    public int findShulker() {
        if (inventory.getValue()) {
            for (int i = 0; i < 36; ++i) {
                ItemStack stack = mc.player.getInventory().getItem(i);
                if (stack.isEmpty()) continue;
                if ((stack.getItem() instanceof BlockItem blockItem) && blockItem.getBlock() instanceof ShulkerBoxBlock) {
                    return i < 9 ? i + 36 : i;
                }
            }
            return -1;
        }
        return InventoryUtil.findClass(ShulkerBoxBlock.class);
    }

    @Override
    public void onEnable() {
        opend = false;
        openPos = null;
        timeoutTimer.reset();
        placePos = null;
        if (nullCheck()) {
            return;
        }
        if (!this.place.getValue()) {
            return;
        }
        doPlace();
    }

    private void doPlace() {
        int oldSlot = mc.player.getInventory().getSelectedSlot();
        double distance = 100;
        BlockPos bestPos = null;
        for (BlockPos pos : BlockUtil.getSphere((float) range.getValue())) {
            if (!mc.level.getBlockState(pos.above()).isAir()) continue;
            if (preferOpen.getValue() && mc.level.getBlockState(pos).getBlock() instanceof ShulkerBoxBlock) return;
            if (Mth.sqrt((float) mc.player.distanceToSqr(Vec3.atCenterOf(pos))) < minRange.getValue()) continue;
            if (!BlockUtil.clientCanPlace(pos, false)
                    || !BlockUtil.isStrictDirection(pos.relative(Direction.DOWN), Direction.UP)
                    || !BlockUtil.canClick(pos.relative(Direction.DOWN))
            ) continue;
            if (bestPos == null || Mth.sqrt((float) mc.player.distanceToSqr(Vec3.atCenterOf(pos))) < distance) {
                distance = Mth.sqrt((float) mc.player.distanceToSqr(Vec3.atCenterOf(pos)));
                bestPos = pos;
            }
        }
        if (bestPos != null) {
            if (this.findShulker() == -1) {
                sendMessage("§4No shulkerbox found.");
                return;
            }
            if (inventory.getValue()) {
                int slot = findShulker();
                InventoryUtil.inventorySwap(slot, oldSlot);
                placeBlock(bestPos);
                placePos = bestPos;
                InventoryUtil.inventorySwap(slot, oldSlot);
            } else {
                InventoryUtil.switchToSlot(this.findShulker());
                placeBlock(bestPos);
                placePos = bestPos;
                InventoryUtil.switchToSlot(oldSlot);
            }
            timer.reset();
        } else {
            sendMessage("§4No place position found.");
        }
    }

    private void update() {
        this.stealCountList[0] = (int) (this.crystal.getValue() - InventoryUtil.getItemCount(Items.END_CRYSTAL));
        this.stealCountList[1] = (int) (this.exp.getValue() - InventoryUtil.getItemCount(Items.EXPERIENCE_BOTTLE));
        this.stealCountList[2] = (int) (this.totem.getValue() - InventoryUtil.getItemCount(Items.TOTEM_OF_UNDYING));
        this.stealCountList[3] = (int) (this.gapple.getValue() - InventoryUtil.getItemCount(Items.ENCHANTED_GOLDEN_APPLE));
        this.stealCountList[4] = (int) (this.obsidian.getValue() - InventoryUtil.getItemCount(Blocks.OBSIDIAN.asItem()));
        this.stealCountList[5] = (int) (this.web.getValue() - InventoryUtil.getItemCount(Blocks.COBWEB.asItem()));
        this.stealCountList[6] = (int) (this.glowstone.getValue() - InventoryUtil.getItemCount(Blocks.GLOWSTONE.asItem()));
        this.stealCountList[7] = (int) (this.anchor.getValue() - InventoryUtil.getItemCount(Blocks.RESPAWN_ANCHOR.asItem()));
        this.stealCountList[8] = (int) (this.pearl.getValue() - InventoryUtil.getItemCount(Items.ENDER_PEARL));
        this.stealCountList[9] = (int) (this.piston.getValue() - InventoryUtil.getItemCount(Blocks.PISTON.asItem()) - InventoryUtil.getItemCount(Blocks.STICKY_PISTON.asItem()));
        this.stealCountList[10] = (int) (this.redstone.getValue() - InventoryUtil.getItemCount(Blocks.REDSTONE_BLOCK.asItem()));
        this.stealCountList[11] = (int) (this.bed.getValue() - InventoryUtil.getItemCount(BedBlock.class));
        this.stealCountList[12] = (int) (this.speed.getValue() - InventoryUtil.getPotionCount(MobEffects.SPEED.value()));
        this.stealCountList[13] = (int) (this.resistance.getValue() - InventoryUtil.getPotionCount(MobEffects.RESISTANCE.value()));
        this.stealCountList[14] = (int) (this.strength.getValue() - InventoryUtil.getPotionCount(MobEffects.STRENGTH.value()));
    }

    @Override
    public void onDisable() {
        opend = false;
        if (mine.getValue()) {
            if (placePos != null) {
                PacketMine.INSTANCE.mine(placePos);
            }
        }
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (smart.getValue()) update();
        if (placeKey.isPressed() && mc.gui.screen() == null) {
            if (!on) {
                opend = false;
                openPos = null;
                timeoutTimer.reset();
                placePos = null;
                doPlace();
            }
            on = true;
        } else {
            on = false;
        }
        openList.removeIf(pos -> !(mc.level.getBlockState(pos).getBlock() instanceof ShulkerBoxBlock));
        if (!(mc.gui.screen() instanceof ShulkerBoxScreen)) {
            if (opend) {
                opend = false;
                if (autoDisable.getValue()) timeoutToDisable();
                if (mine.getValue()) {
                    if (openPos != null) {
                        if (mc.level.getBlockState(openPos).getBlock() instanceof ShulkerBoxBlock) {
                            PacketMine.INSTANCE.mine(openPos);
                        } else {
                            openPos = null;
                        }
                    }
                }
                return;
            }
            if (open.getValue()) {
                if (placePos != null && Mth.sqrt((float) mc.player.distanceToSqr(Vec3.atCenterOf(placePos))) <= range.getValue() && mc.level.getBlockState(placePos.above()).isAir() && (!timer.passed(500) || mc.level.getBlockState(placePos).getBlock() instanceof ShulkerBoxBlock)) {
                    if (mc.level.getBlockState(placePos).getBlock() instanceof ShulkerBoxBlock) {
                        openPos = placePos;
                        BlockUtil.clickBlock(placePos, BlockUtil.getClickSide(placePos), rotate.getValue());
                    }
                } else {
                    boolean found = false;
                    for (BlockPos pos : BlockUtil.getSphere((float) range.getValue())) {
                        if (openList.contains(pos)) {
                            continue;
                        }
                        if (!mc.level.getBlockState(pos.above()).isAir() && !BlockUtil.canReplace(pos.above())) continue;
                        if (mc.level.getBlockState(pos).getBlock() instanceof ShulkerBoxBlock) {
                            openPos = pos;
                            BlockUtil.clickBlock(pos, BlockUtil.getClickSide(pos), rotate.getValue());
                            found = true;
                            break;
                        }
                    }
                    if (!found && autoDisable.getValue()) this.timeoutToDisable();
                }
            } else if (!this.take.getValue()) {
                if (autoDisable.getValue()) this.timeoutToDisable();
            }
            return;
        }
        opend = true;
        if (openPos != null) {
            openList.add(openPos);
        }
        if (!this.take.getValue()) {
            if (autoDisable.getValue()) this.timeoutToDisable();
            return;
        }
        boolean take = false;
        if (mc.player.containerMenu instanceof ShulkerBoxMenu shulker) {
            for (Slot slot : shulker.slots) {
                if (slot.index < 27 && !slot.getItem().isEmpty()) {
                    Type type = needSteal(slot.getItem());
                    if (!smart.getValue() || type == Type.QuickMove || type == Type.Stack && forceMove.getValue()) {
                        mc.gameMode.handleContainerInput(shulker.containerId, slot.index, 0, ContainerInput.QUICK_MOVE, mc.player);
                        take = true;
                    } else if (type == Type.Stack) {
                        //CommandManager.sendMessage(slot.index + " stack");
                        for (int slot1 = 0; slot1 < 36; ++slot1) {
                            ItemStack stack = mc.player.getInventory().getItem(slot1);
                            if (stack.isEmpty()) continue;
                            if (!stack.isStackable()) continue;
                            if (stack.getItem() != slot.getItem().getItem()) continue;
                            //CommandManager.sendMessage(slot1 + " same item" + " max:" + stack.getMaxCount() + " count:" + stack.getCount());
                            if (stack.getCount() < stack.getMaxStackSize()) {
                                int i = (slot1 < 9 ? slot1 + 36 : slot1) + 18;
                                //CommandManager.sendMessage(slot.index + " to" + i);
                                mc.gameMode.handleContainerInput(shulker.containerId, slot.index, 0, ContainerInput.PICKUP, mc.player);
                                mc.gameMode.handleContainerInput(shulker.containerId, i, 0, ContainerInput.PICKUP, mc.player);
                                mc.gameMode.handleContainerInput(shulker.containerId, slot.index, 0, ContainerInput.PICKUP, mc.player);
                                take = true;
                                break;
                            }
                        }
                    }
                }
            }
        }
        if (autoDisable.getValue() && !take) this.timeoutToDisable();
    }

    private void timeoutToDisable() {
        if (timeoutTimer.passed(disableTime.getValueInt()))
            disable();
    }

    private Type needSteal(final ItemStack i) {
        if (i.getItem().equals(Items.END_CRYSTAL) && this.stealCountList[0] > 0) {
            stealCountList[0] = stealCountList[0] - i.getCount();
            if (stealCountList[0] < 0) {
                return Type.Stack;
            }
            return Type.QuickMove;
        }
        if (i.getItem().equals(Items.EXPERIENCE_BOTTLE) && this.stealCountList[1] > 0) {
            stealCountList[1] = stealCountList[1] - i.getCount();
            if (stealCountList[1] < 0) {
                return Type.Stack;
            }
            return Type.QuickMove;
        }
        if (i.getItem().equals(Items.TOTEM_OF_UNDYING) && this.stealCountList[2] > 0) {
            stealCountList[2] = stealCountList[2] - i.getCount();
            if (stealCountList[2] < 0) {
                return Type.Stack;
            }
            return Type.QuickMove;
        }
        if (i.getItem().equals(Items.ENCHANTED_GOLDEN_APPLE) && this.stealCountList[3] > 0) {
            stealCountList[3] = stealCountList[3] - i.getCount();
            if (stealCountList[3] < 0) {
                return Type.Stack;
            }
            return Type.QuickMove;
        }
        if (i.getItem().equals(Blocks.OBSIDIAN.asItem()) && this.stealCountList[4] > 0) {
            stealCountList[4] = stealCountList[4] - i.getCount();
            if (stealCountList[4] < 0) {
                return Type.Stack;
            }
            return Type.QuickMove;
        }
        if (i.getItem().equals(Blocks.COBWEB.asItem()) && this.stealCountList[5] > 0) {
            stealCountList[5] = stealCountList[5] - i.getCount();
            if (stealCountList[5] < 0) {
                return Type.Stack;
            }
            return Type.QuickMove;
        }
        if (i.getItem().equals(Blocks.GLOWSTONE.asItem()) && this.stealCountList[6] > 0) {
            stealCountList[6] = stealCountList[6] - i.getCount();
            if (stealCountList[6] < 0) {
                return Type.Stack;
            }
            return Type.QuickMove;
        }
        if (i.getItem().equals(Blocks.RESPAWN_ANCHOR.asItem()) && this.stealCountList[7] > 0) {
            stealCountList[7] = stealCountList[7] - i.getCount();
            if (stealCountList[7] < 0) {
                return Type.Stack;
            }
            return Type.QuickMove;
        }
        if (i.getItem().equals(Items.ENDER_PEARL) && this.stealCountList[8] > 0) {
            stealCountList[8] = stealCountList[8] - i.getCount();
            if (stealCountList[8] < 0) {
                return Type.Stack;
            }
            return Type.QuickMove;
        }
        if (i.getItem() instanceof BlockItem && ((BlockItem) i.getItem()).getBlock() instanceof PistonBaseBlock) {
            if (this.stealCountList[9] > 0) {
                stealCountList[9] = stealCountList[9] - i.getCount();
                if (stealCountList[9] < 0) {
                    return Type.Stack;
                }
                return Type.QuickMove;
            }
        }
        if (i.getItem().equals(Blocks.REDSTONE_BLOCK.asItem()) && this.stealCountList[10] > 0) {
            stealCountList[10] = stealCountList[10] - i.getCount();
            if (stealCountList[10] < 0) {
                return Type.Stack;
            }
            return Type.QuickMove;
        }
        if (i.getItem() instanceof BlockItem && ((BlockItem) i.getItem()).getBlock() instanceof BedBlock) {
            if (this.stealCountList[11] > 0) {
                stealCountList[11] = stealCountList[11] - i.getCount();
                if (stealCountList[11] < 0) {
                    return Type.Stack;
                }
                return Type.QuickMove;
            }
        }
        if (i.is(Items.SPLASH_POTION)) {
            PotionContents potionContentsComponent = i.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
            for (MobEffectInstance effect : potionContentsComponent.getAllEffects()) {
                if (effect.getEffect().value() == MobEffects.SPEED.value()) {
                    if (this.stealCountList[12] > 0) {
                        stealCountList[12] = stealCountList[12] - i.getCount();
                        if (stealCountList[12] < 0) {
                            return Type.Stack;
                        }
                        return Type.QuickMove;
                    }
                } else if (effect.getEffect().value() == MobEffects.RESISTANCE.value()) {
                    if (this.stealCountList[13] > 0) {
                        stealCountList[13] = stealCountList[13] - i.getCount();
                        if (stealCountList[13] < 0) {
                            return Type.Stack;
                        }
                        return Type.QuickMove;
                    }
                } else if (effect.getEffect().value() == MobEffects.STRENGTH.value()) {
                    if (this.stealCountList[14] > 0) {
                        stealCountList[14] = stealCountList[14] - i.getCount();
                        if (stealCountList[14] < 0) {
                            return Type.Stack;
                        }
                        return Type.QuickMove;
                    }
                }
            }
        }

        return Type.None;
    }

    private void placeBlock(BlockPos pos) {
        AntiRegear.INSTANCE.safe.add(pos);
        BlockUtil.clickBlock(pos.relative(Direction.DOWN), Direction.UP, rotate.getValue());
    }

    private enum Type {
        None,
        Stack,
        QuickMove
    }
}