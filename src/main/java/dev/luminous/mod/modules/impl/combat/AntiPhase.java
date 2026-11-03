package dev.luminous.mod.modules.impl.combat;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.combat.CombatUtil;
import dev.luminous.api.utils.math.MathUtil;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.api.utils.world.BlockPosX;
import dev.luminous.api.utils.world.BlockUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.client.AntiCheat;
import dev.luminous.mod.modules.impl.exploit.Blink;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class AntiPhase extends Module {
    public AntiPhase() {
        super("AntiPhase", Category.Combat);
        setChinese("反穿墙");
    }

    private final SliderSetting placeRange = add(new SliderSetting("PlaceRange", 4, 0, 8));
    private final BooleanSetting ladder = add(new BooleanSetting("Ladder", true).setParent());
    private final BooleanSetting onlyHard = add(new BooleanSetting("OnlyHard", true, ladder::isOpen));
    private final BooleanSetting itemFrame = add(new BooleanSetting("ItemFrame", true).setParent());
    private final BooleanSetting fill = add(new BooleanSetting("Fill", false, itemFrame::isOpen));
    private final BooleanSetting scaffolding = add(new BooleanSetting("Scaffolding", true));
    private final BooleanSetting rotate = add(new BooleanSetting("Rotate", true));
    private final BooleanSetting eatingPause = add(new BooleanSetting("EatingPause", true));
    private final BooleanSetting collideSkip = add(new BooleanSetting("CollideSkip", true));
    private final BooleanSetting crawlingSkip = add(new BooleanSetting("CrawlingSkip", true));
    private final BooleanSetting onlyGround = add(new BooleanSetting("InAirSkip", false));
    private final SliderSetting targetRange = add(new SliderSetting("TargetRange", 5, 0, 7, .1));
    private final SliderSetting delay = add(new SliderSetting("Delay", 100, 0, 2000, 1));
    private final BooleanSetting inventory = add(new BooleanSetting("InventorySwap", true));

    private final Timer timer = new Timer();

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (eatingPause.getValue() && mc.player.isUsingItem()) return;
        if (Blink.INSTANCE.isOn() && Blink.INSTANCE.pauseModule.getValue()) return;
        for (Entity target : CombatUtil.getEnemies(targetRange.getValue())) {
            if (crawlingSkip.getValue() && target.isVisuallyCrawling())
                continue;
            if (onlyGround.getValue() && !target.onGround())
                continue;
            if (collideSkip.getValue() && BlockUtil.canCollide(target, target.getBoundingBox()))
                continue;
            if (!timer.passed(delay.getValueInt())) continue;

            if (scaffolding.getValue()) {
                if (BlockUtil.canReplace(target.blockPosition())) {
                    int block = getScaffolding();
                    if (block != -1) {
                        BlockPos bp = target.blockPosition();
                        Direction downSide = null;
                        Direction placeSide = BlockUtil.getPlaceSide(bp, 6.0);
                        if (placeSide != null || (downSide = getSideIgnore(bp.below())) != null && BlockUtil.getBlock(bp.below()) instanceof ScaffoldingBlock && !mc.player.isShiftKeyDown() || mc.player.isShiftKeyDown() && (downSide = getSideOnly(bp.below())) != null && BlockUtil.getBlock(bp.below()) instanceof ScaffoldingBlock) {
                            Vec3 targetPos = placeSide != null ? Vec3.atCenterOf(bp.relative(placeSide)).add(placeSide.getOpposite().getStepX() * 0.5, placeSide.getOpposite().getStepY() * 0.5, placeSide.getOpposite().getStepZ() * 0.5) : Vec3.atCenterOf(bp.below()).add(downSide.getStepX() * 0.5, downSide.getStepY() * 0.5, downSide.getStepZ() * 0.5);
                            double distance = mc.player.getEyePosition().distanceTo(targetPos);
                            if (distance <= placeRange.getValue()) {
                                int old = mc.player.getInventory().getSelectedSlot();
                                doSwap(block);
                                if (BlockUtil.getBlock(bp.below()) instanceof ScaffoldingBlock && downSide != null) {
                                    BlockUtil.clickBlock(bp.below(), downSide, rotate.getValue());
                                } else {
                                    BlockUtil.placeBlock(bp, rotate.getValue());
                                }
                                timer.reset();
                                if (inventory.getValue()) {
                                    doSwap(block);
                                    EntityUtil.syncInventory();
                                } else {
                                    doSwap(old);
                                }
                            }
                        }
                    }
                }
            }

            if (itemFrame.getValue()) {
                if (mc.level.getBlockState(target.blockPosition()).isAir()) {
                    ItemFrame itemFrameEntity = hasItemFrame(new AABB(target.blockPosition()));
                    if (itemFrameEntity == null) {
                        int block = getItemFrame();
                        if (block != -1) {
                            BlockPos bp = target.blockPosition().below();
                            double distance = mc.player.getEyePosition().distanceTo(Vec3.atBottomCenterOf(bp).add(0, 1, 0));
                            if (distance <= placeRange.getValue()) {
                                if (BlockUtil.isStrictDirection(bp, Direction.UP) && !BlockUtil.canReplace(bp) && BlockUtil.canClick(bp)) {
                                    int old = mc.player.getInventory().getSelectedSlot();
                                    doSwap(block);
                                    BlockUtil.clickBlock(bp, Direction.UP, rotate.getValue());
                                    timer.reset();
                                    if (inventory.getValue()) {
                                        doSwap(block);
                                        EntityUtil.syncInventory();
                                    } else {
                                        doSwap(old);
                                    }
                                    //return;
                                }
                            }
                        }
                    }
                    if (fill.getValue() && itemFrameEntity != null && itemFrameEntity.getItem().isEmpty()) {
                        Vec3 hitVec = MathUtil.getClosestPointToBox(mc.player.getEyePosition(), itemFrameEntity.getBoundingBox());
                        if (mc.player.getEyePosition().distanceTo(hitVec) <= AntiCheat.INSTANCE.ieRange.getValue()) {
                            int block = getObsidian();
                            if (block != -1) {
                                int old = mc.player.getInventory().getSelectedSlot();
                                doSwap(block);
                                if (rotate.getValue()) {
                                    Alien.ROTATION.snapAt(hitVec);
                                }
                                mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundInteractPacket(itemFrameEntity.getId(), net.minecraft.world.InteractionHand.MAIN_HAND, itemFrameEntity.position(), false));
                                timer.reset();
                                if (inventory.getValue()) {
                                    doSwap(block);
                                    EntityUtil.syncInventory();
                                } else {
                                    doSwap(old);
                                }
                                if (rotate.getValue()) {
                                    Alien.ROTATION.snapBack();
                                }
                            }
                        }
                    }
                }
            }

            if (ladder.getValue()) {
                int block = getLadder();
                if (block != -1) {
                    if (BlockUtil.canReplace(target.blockPosition())) {
                        Direction facing = targetFacing(target.position());
                        if (facing != null) {
                            BlockPos bp = target.blockPosition().relative(facing);
                            double distance = mc.player.getEyePosition().distanceTo(Vec3.atCenterOf(bp).add(facing.getOpposite().getStepX() * 0.5, facing.getOpposite().getStepY() * 0.5, facing.getOpposite().getStepZ() * 0.5));
                            if (distance <= placeRange.getValue()) {
                                BlockUtil.placedPos.add(target.blockPosition());
                                int old = mc.player.getInventory().getSelectedSlot();
                                doSwap(block);
                                BlockUtil.clickBlock(bp, facing.getOpposite(), rotate.getValue());
                                timer.reset();
                                if (inventory.getValue()) {
                                    doSwap(block);
                                    EntityUtil.syncInventory();
                                } else {
                                    doSwap(old);
                                }
                                //return;
                            }
                        }
                    }
                }
            }
        }
    }

    private Direction getSideOnly(BlockPos pos) {
        if (BlockUtil.isStrictDirection(pos, Direction.UP))
            return Direction.UP;
        return null;
    }

    private Direction getSideIgnore(BlockPos pos) {
        for (Direction i : Direction.values()) {
            if (i == Direction.UP) continue;
            if (!BlockUtil.isStrictDirection(pos, i)) continue;
            return i;
        }
        return null;
    }

    private ItemFrame hasItemFrame(AABB box) {
        for (Entity entity : BlockUtil.getEntities(box)) {
            if (entity instanceof ItemFrame itemFrameEntity && ((ItemFrame) entity).getDirection() == Direction.UP) {
                return itemFrameEntity;
            }
        }
        return null;
    }

    private static AABB getAABB(Direction facing, BlockPos bp) {
        AABB box = null;
        double wide = 0.1875;
        double x = facing.getStepX() * 0.5 + bp.getX() + 0.5;
        double y = bp.getY();
        double z = facing.getStepZ() * 0.5 + bp.getZ() + 0.5;
        switch (facing) {
            case WEST -> box = new AABB(x, y, z, x + wide, y + 1, z + 1);
            case EAST -> box = new AABB(x, y, z, x - wide, y + 1, z + 1);
            case NORTH -> box = new AABB(x, y, z, x + 1, y + 1, z + wide);
            case SOUTH -> box = new AABB(x, y, z, x + 1, y + 1, z - wide);
        }
        return box;
    }

    private void doSwap(int slot) {
        if (inventory.getValue()) {
            InventoryUtil.inventorySwap(slot, mc.player.getInventory().getSelectedSlot());
        } else {
            InventoryUtil.switchToSlot(slot);
        }
    }

    private int getFlintAndSteel() {
        if (inventory.getValue()) {
            return InventoryUtil.findItemInventorySlot(Items.FLINT_AND_STEEL);
        } else {
            return InventoryUtil.findItem(Items.FLINT_AND_STEEL);
        }
    }

    private int getObsidian() {
        if (inventory.getValue()) {
            return InventoryUtil.findItemInventorySlot(Items.OBSIDIAN);
        } else {
            return InventoryUtil.findItem(Items.OBSIDIAN);
        }
    }

    private int getItemFrame() {
        if (inventory.getValue()) {
            return InventoryUtil.findItemInventorySlot(Items.ITEM_FRAME);
        } else {
            return InventoryUtil.findItem(Items.ITEM_FRAME);
        }
    }

    private int getLadder() {
        if (inventory.getValue()) {
            return InventoryUtil.findBlockInventorySlot(Blocks.LADDER);
        } else {
            return InventoryUtil.findBlock(Blocks.LADDER);
        }
    }

    private int getScaffolding() {
        if (inventory.getValue()) {
            return InventoryUtil.findBlockInventorySlot(Blocks.SCAFFOLDING);
        } else {
            return InventoryUtil.findBlock(Blocks.SCAFFOLDING);
        }
    }

    private Direction targetFacing(Vec3 vec3d) {
        BlockPos blockPos = new BlockPosX(vec3d);
        Vec3 centerPos = Vec3.atBottomCenterOf(blockPos);

        float factorValue = 0.4f;

        double minDistance = Double.MAX_VALUE;
        Direction facing = null;
        for (Direction direction : Direction.values()) {
            if (direction == Direction.UP || direction == Direction.DOWN) continue;
            BlockPos bp = blockPos.relative(direction);
            if (!BlockUtil.isStrictDirection(bp, direction.getOpposite()))
                continue;
            if (onlyHard.getValue()) {
                if (!Alien.HOLE.isHard(bp)) continue;
            } else {
                if (BlockUtil.canReplace(bp)) continue;
                if (!BlockUtil.canClick(bp)) continue;
            }
            AABB box = getAABB(direction, blockPos);
            if (box == null) continue;
            if (hasEntity(box)) continue;

            Vec3 tempPos = centerPos.add(direction.getStepX() * factorValue, 0, direction.getStepZ() * factorValue);

            double distance = tempPos.distanceTo(vec3d);
            if (distance < minDistance) {
                minDistance = distance;
                facing = direction;
            }
        }

        return facing;
    }

    public static boolean hasEntity(AABB box) {
        for (Entity entity : BlockUtil.getEntities(box)) {
            if (!entity.isAlive() || entity instanceof ItemEntity || entity instanceof ExperienceOrb || entity instanceof ThrownExperienceBottle || entity instanceof Arrow || entity instanceof ItemFrame)
                continue;
            return true;
        }
        return false;
    }
}
