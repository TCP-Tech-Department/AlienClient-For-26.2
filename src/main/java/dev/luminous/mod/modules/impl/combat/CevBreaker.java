package dev.luminous.mod.modules.impl.combat;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.combat.CombatUtil;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.api.utils.world.BlockUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.client.ClientSetting;
import dev.luminous.mod.modules.impl.player.PacketMine;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

public class CevBreaker extends Module {
    public static CevBreaker INSTANCE;
    private final SliderSetting targetRange =
            add(new SliderSetting("TargetRange", 5, 0, 8, .1));
    private final SliderSetting breakRange =
            add(new SliderSetting("BreakRange", 5, 0, 8, .1));
    private final SliderSetting delay =
            add(new SliderSetting("Delay", 100, 0, 500).setSuffix("ms"));
    private final BooleanSetting rotate =
            add(new BooleanSetting("Rotate", true));
    private final BooleanSetting ground =
            add(new BooleanSetting("Ground", true));
    private final BooleanSetting inventory =
            add(new BooleanSetting("InventorySwap", true));
    private final BooleanSetting top =
            add(new BooleanSetting("Top", false));
    private final BooleanSetting bevel =
            add(new BooleanSetting("Bevel", true));
    private final Timer timer = new Timer();
    private Player target = null;

    public CevBreaker() {
        super("CevBreaker", Category.Combat);
        setChinese("自动炸头");
        INSTANCE = this;
    }

    public static boolean canPlaceCrystal(BlockPos pos) {
        return mc.level.getBlockState(pos).isAir()
                && BlockUtil.noEntityBlockCrystal(pos, false)
                && BlockUtil.noEntityBlockCrystal(pos.above(), false)
                && (!ClientSetting.INSTANCE.lowVersion.getValue() || mc.level.getBlockState(pos.above()).isAir());
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (inventory.getValue() && !EntityUtil.inInventory()) return;
        if (ground.getValue() && !mc.player.onGround()) return;
        PacketMine.INSTANCE.crystal.setValue(true);
        if ((target = CombatUtil.getClosestEnemy(targetRange.getValue())) != null) {
            BlockPos targetPos = EntityUtil.getEntityPos(target);
            if (PacketMine.getBreakPos() != null) {
                for (Direction facing : Direction.values()) {
                    if (facing == Direction.DOWN) continue;
                    if (facing == Direction.UP) {
                        if (!top.getValue()) continue;
                    } else if (!bevel.getValue()) continue;
                    BlockPos pos = targetPos.above(1).relative(facing);
                    if (Vec3.atCenterOf(pos.above()).distanceTo(mc.player.position()) > breakRange.getValue()) continue;
                    if (PacketMine.getBreakPos().equals(targetPos.above(1).relative(facing))) {
                        if (canPlaceCrystal(targetPos.above(2).relative(facing))) {
                            if (mc.level.getBlockState(pos).isAir()) {
                                if (!BlockUtil.canPlace(pos)) continue;
                                if (!timer.passedMs(delay.getValue())) {
                                    return;
                                }
                                placeBlock(pos);
                                timer.reset();
                                return;
                            } else if (getBlock(pos) == Blocks.OBSIDIAN) {
                                PacketMine.INSTANCE.mine(pos);
                                timer.reset();
                                return;
                            }
                        } else if (BlockUtil.hasCrystal(targetPos.above(2).relative(facing))) {
                            if (mc.level.getBlockState(pos).isAir()) {
                                return;
                            } else if (getBlock(pos) == Blocks.OBSIDIAN) {
                                PacketMine.INSTANCE.mine(pos);
                                timer.reset();
                                return;
                            }
                        }
                    }
                }
            }
            for (Direction facing : Direction.values()) {
                if (facing == Direction.DOWN) continue;
                if (facing == Direction.UP) {
                    if (!top.getValue()) continue;
                } else if (!bevel.getValue()) continue;
                BlockPos pos = targetPos.above(1).relative(facing);
                if (Vec3.atCenterOf(pos.above()).distanceTo(mc.player.position()) > breakRange.getValue()) continue;
                if (canPlaceCrystal(targetPos.above(2).relative(facing))) {
                    if (mc.level.getBlockState(pos).isAir()) {
                        if (!BlockUtil.canPlace(pos)) continue;
                        if (!timer.passedMs(delay.getValue())) {
                            return;
                        }
                        placeBlock(pos);
                        timer.reset();
                        break;
                    } else if (getBlock(pos) == Blocks.OBSIDIAN) {
                        PacketMine.INSTANCE.mine(pos);
                        timer.reset();
                        break;
                    }
                } else if (BlockUtil.hasCrystal(targetPos.above(2).relative(facing))) {
                    if (mc.level.getBlockState(pos).isAir()) {
                        break;
                    } else if (getBlock(pos) == Blocks.OBSIDIAN) {
                        PacketMine.INSTANCE.mine(pos);
                        timer.reset();
                        break;
                    }
                }
            }
        }
    }

    private void doSwap(int slot) {
        if (inventory.getValue()) {
            InventoryUtil.inventorySwap(slot, mc.player.getInventory().getSelectedSlot());
        } else {
            InventoryUtil.switchToSlot(slot);
        }
    }

    private int getBlock() {
        if (inventory.getValue()) {
            return InventoryUtil.findBlockInventorySlot(Blocks.OBSIDIAN);
        } else {
            return InventoryUtil.findBlock(Blocks.OBSIDIAN);
        }
    }

    private void placeBlock(BlockPos pos) {
        int block;
        if ((block = getBlock()) == -1) {
            return;
        }
        int oldSlot = mc.player.getInventory().getSelectedSlot();
        if (BlockUtil.canPlace(pos)) {

            if (BlockUtil.allowAirPlace()) {
                doSwap(block);
                BlockUtil.placedPos.add(pos);
                BlockUtil.airPlace(pos, rotate.getValue());
                if (inventory.getValue()) {
                    doSwap(block);
                    EntityUtil.syncInventory();
                } else {
                    doSwap(oldSlot);
                }
                return;
            }
            Direction side = BlockUtil.getPlaceSide(pos);
            if (side == null) return;
            doSwap(block);
            BlockUtil.placedPos.add(pos);
            BlockUtil.clickBlock(pos.relative(side), side.getOpposite(), rotate.getValue());
            if (inventory.getValue()) {
                doSwap(block);
                EntityUtil.syncInventory();
            } else {
                doSwap(oldSlot);
            }
        }
    }

    @Override
    public String getInfo() {
        if (target != null) {
            return target.getName().getString();
        }
        return null;
    }

    private Block getBlock(BlockPos pos) {
        return mc.level.getBlockState(pos).getBlock();
    }
}
