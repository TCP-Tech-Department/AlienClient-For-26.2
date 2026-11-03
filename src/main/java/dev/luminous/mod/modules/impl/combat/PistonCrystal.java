package dev.luminous.mod.modules.impl.combat;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.combat.CombatUtil;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.api.utils.player.MovementUtil;
import dev.luminous.api.utils.world.BlockUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.client.ClientSetting;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public class PistonCrystal extends Module {
    public static PistonCrystal INSTANCE;
    private final BooleanSetting rotate =
            add(new BooleanSetting("Rotate", false));
    private final BooleanSetting pistonPacket =
            add(new BooleanSetting("PistonPacket", false));
    private final BooleanSetting noEating = add(new BooleanSetting("NoEating", true));
    private final BooleanSetting eatingBreak = add(new BooleanSetting("EatingBreak", false));
    private final SliderSetting placeRange =
            add(new SliderSetting("PlaceRange", 5.0f, 1.0f, 8.0f));
    private final SliderSetting range =
            add(new SliderSetting("Range", 4.0f, 1.0f, 8.0f));
    private final BooleanSetting fire =
            add(new BooleanSetting("Fire", true));
    private final BooleanSetting switchPos =
            add(new BooleanSetting("Switch", false));
    private final BooleanSetting onlyGround =
            add(new BooleanSetting("SelfGround", true));
    private final BooleanSetting onlyStatic =
            add(new BooleanSetting("MovingPause", true));
    private final SliderSetting updateDelay =
            add(new SliderSetting("PlaceDelay", 100, 0, 500));
    private final SliderSetting posUpdateDelay =
            add(new SliderSetting("PosUpdateDelay", 500, 0, 1000));
    private final SliderSetting stageSetting =
            add(new SliderSetting("Stage", 4, 1, 10));
    private final SliderSetting pistonStage =
            add(new SliderSetting("PistonStage", 1, 1, 10));
    private final SliderSetting pistonMaxStage =
            add(new SliderSetting("PistonMaxStage", 1, 1, 10));
    private final SliderSetting powerStage =
            add(new SliderSetting("PowerStage", 3, 1, 10));
    private final SliderSetting powerMaxStage =
            add(new SliderSetting("PowerMaxStage", 3, 1, 10));
    private final SliderSetting crystalStage =
            add(new SliderSetting("CrystalStage", 4, 1, 10));
    private final SliderSetting crystalMaxStage =
            add(new SliderSetting("CrystalMaxStage", 4, 1, 10));
    private final SliderSetting fireStage =
            add(new SliderSetting("FireStage", 2, 1, 10));
    private final SliderSetting fireMaxStage =
            add(new SliderSetting("FireMaxStage", 2, 1, 10));
    private final BooleanSetting inventory =
            add(new BooleanSetting("InventorySwap", true));
    private final BooleanSetting debug =
            add(new BooleanSetting("Debug", false));
    private final Timer timer = new Timer();
    private final Timer crystalTimer = new Timer();
    public BlockPos bestPos = null;
    public BlockPos bestOPos = null;
    public Direction bestFacing = null;
    public double distance = 100;
    public boolean getPos = false;
    public int stage = 1;
    private Player target = null;
    private boolean isPiston = false;
    public PistonCrystal() {
        super("PistonCrystal", Category.Combat);
        setChinese("活塞水晶");
        INSTANCE = this;
    }

    private static boolean canFire(BlockPos pos) {
        if (BlockUtil.canReplace(pos.below())) return false;
        if (!mc.level.getBlockState(pos).isAir()) return false;
        if (!BlockUtil.canClick(pos.relative(Direction.DOWN))) return false;
        return BlockUtil.isStrictDirection(pos.below(), Direction.UP);
    }

    public void onTick() {
        if (pistonStage.getValue() > stageSetting.getValue()) {
            pistonStage.setValue(stageSetting.getValue());
        }
        if (fireStage.getValue() > stageSetting.getValue()) {
            fireStage.setValue(stageSetting.getValue());
        }
        if (powerStage.getValue() > stageSetting.getValue()) {
            powerStage.setValue(stageSetting.getValue());
        }
        if (crystalStage.getValue() > stageSetting.getValue()) {
            crystalStage.setValue(stageSetting.getValue());
        }

        if (pistonMaxStage.getValue() > stageSetting.getValue()) {
            pistonMaxStage.setValue(stageSetting.getValue());
        }
        if (fireMaxStage.getValue() > stageSetting.getValue()) {
            fireMaxStage.setValue(stageSetting.getValue());
        }
        if (powerMaxStage.getValue() > stageSetting.getValue()) {
            powerMaxStage.setValue(stageSetting.getValue());
        }
        if (crystalMaxStage.getValue() > stageSetting.getValue()) {
            crystalMaxStage.setValue(stageSetting.getValue());
        }

        if (crystalMaxStage.getValue() < crystalStage.getValue()) {
            crystalStage.setValue(crystalMaxStage.getValue());
        }
        if (powerMaxStage.getValue() < powerStage.getValue()) {
            powerStage.setValue(powerMaxStage.getValue());
        }
        if (pistonMaxStage.getValue() < pistonStage.getValue()) {
            pistonStage.setValue(pistonMaxStage.getValue());
        }
        if (fireMaxStage.getValue() < fireStage.getValue()) {
            fireStage.setValue(fireMaxStage.getValue());
        }
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        onTick();
        target = CombatUtil.getClosestEnemy(range.getValue());
        if (target == null) {
            return;
        }
        if (noEating.getValue() && mc.player.isUsingItem())
            return;
        if (check(onlyStatic.getValue(), !mc.player.onGround(), onlyGround.getValue())) return;
        BlockPos pos = EntityUtil.getEntityPos(target, true);
        if (!mc.player.isUsingItem() || eatingBreak.getValue()) {
            if (checkCrystal(pos.above(0))) {
                CombatUtil.attackCrystal(pos.above(0), rotate.getValue(), true);
            }
            if (checkCrystal(pos.above(1))) {
                CombatUtil.attackCrystal(pos.above(1), rotate.getValue(), true);
            }
            if (checkCrystal(pos.above(2))) {
                CombatUtil.attackCrystal(pos.above(2), rotate.getValue(), true);
            }
        }
        if (bestPos != null && mc.level.getBlockState(bestPos).getBlock() instanceof PistonBaseBlock) {
            isPiston = true;
        } else if (isPiston) {
            isPiston = false;
            crystalTimer.reset();
            bestPos = null;
        }
        if (crystalTimer.passedMs(posUpdateDelay.getValueInt())) {
            stage = 0;
            distance = 100;
            getPos = false;
            getBestPos(pos.above(2));
            getBestPos(pos.above());
        }
        if (!timer.passedMs(updateDelay.getValueInt())) return;
        if (getPos && bestPos != null) {
            timer.reset();
            if (debug.getValue()) {
                sendMessage("[Debug] PistonPos:" + bestPos + " Facing:" + bestFacing + " CrystalPos:" + bestOPos.relative(bestFacing));
            }
            doPistonAura(bestPos, bestFacing, bestOPos);
        }
    }

    public boolean check(boolean onlyStatic, boolean onGround, boolean onlyGround) {
        if (MovementUtil.isMoving() && onlyStatic) return true;
        if (onGround && onlyGround) return true;
        if (findBlock(Blocks.REDSTONE_BLOCK) == -1) return true;
        if (findClass(PistonBaseBlock.class) == -1) return true;
        return findItem(Items.END_CRYSTAL) == -1;
    }

    private boolean checkCrystal(BlockPos pos) {
        for (Entity entity : BlockUtil.getEntities(new AABB(pos))) {
            if (entity instanceof EndCrystal) {
                float damage = AutoCrystal.INSTANCE.calculateDamage(entity.position(), target, target);
                if (damage > 7) return true;
            }
        }
        return false;
    }

    private boolean checkCrystal2(BlockPos pos) {
        for (Entity entity : BlockUtil.getEntities(new AABB(pos))) {
            if (entity instanceof EndCrystal && EntityUtil.getEntityPos(entity).equals(pos)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String getInfo() {
        if (target != null) return target.getName().getString();
        return null;
    }

    private void getBestPos(BlockPos pos) {
        for (Direction i : Direction.values()) {
            if (i == Direction.DOWN || i == Direction.UP) continue;
            getPos(pos, i);
        }
    }

    private void getPos(BlockPos pos, Direction i) {
        if (!BlockUtil.canPlaceCrystal(pos.relative(i)) && !checkCrystal2(pos.relative(i))) return;
        getPos(pos.relative(i, 3), i, pos);
        getPos(pos.relative(i, 3).above(), i, pos);
        int offsetX = pos.relative(i).getX() - pos.getX();
        int offsetZ = pos.relative(i).getZ() - pos.getZ();
        getPos(pos.relative(i, 3).offset(offsetZ, 0, offsetX), i, pos);
        getPos(pos.relative(i, 3).offset(-offsetZ, 0, -offsetX), i, pos);
        getPos(pos.relative(i, 3).offset(offsetZ, 1, offsetX), i, pos);
        getPos(pos.relative(i, 3).offset(-offsetZ, 1, -offsetX), i, pos);

        getPos(pos.relative(i, 2), i, pos);
        getPos(pos.relative(i, 2).above(), i, pos);

        getPos(pos.relative(i, 2).offset(offsetZ, 0, offsetX), i, pos);
        getPos(pos.relative(i, 2).offset(-offsetZ, 0, -offsetX), i, pos);
        getPos(pos.relative(i, 2).offset(offsetZ, 1, offsetX), i, pos);
        getPos(pos.relative(i, 2).offset(-offsetZ, 1, -offsetX), i, pos);
    }

    private void getPos(BlockPos pos, Direction facing, BlockPos oPos) {
        if (switchPos.getValue() && bestPos != null && bestPos.equals(pos) && mc.level.getBlockState(bestPos).isAir()) {
            return;
        }
        if (!BlockUtil.canPlace(pos, placeRange.getValue()) && !(getBlock(pos) instanceof PistonBaseBlock)) return;
        if (findClass(PistonBaseBlock.class) == -1) return;
        if (ClientSetting.INSTANCE.lowVersion.getValue() && !(getBlock(pos) instanceof PistonBaseBlock) && (mc.player.getY() - pos.getY() <= -2.0 || mc.player.getY() - pos.getY() >= 3.0) && BlockUtil.distanceToXZ(pos.getX() + 0.5, pos.getZ() + 0.5) < 2.6) {
            return;
        }
        if (!mc.level.getBlockState(pos.relative(facing, -1)).isAir() || mc.level.getBlockState(pos.relative(facing, -1)).getBlock() == Blocks.FIRE || getBlock(pos.relative(facing.getOpposite())) == Blocks.MOVING_PISTON && !checkCrystal2(pos.relative(facing.getOpposite()))) {
            return;
        }
        if (!BlockUtil.canPlace(pos, placeRange.getValue()) && !isPiston(pos, facing)) {
            return;
        }
        if (!(Mth.sqrt((float) mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos))) < distance || bestPos == null)) {
            return;
        }
        bestPos = pos;
        bestOPos = oPos;
        bestFacing = facing;
        distance = Mth.sqrt((float) mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)));
        getPos = true;
        crystalTimer.reset();
    }

    private void doPistonAura(BlockPos pos, Direction facing, BlockPos oPos) {
        if (stage >= stageSetting.getValue()) {
            stage = 0;
        }
        stage++;
        if (mc.level.getBlockState(pos).isAir()) {
            if (BlockUtil.canPlace(pos)) {
                if (stage >= pistonStage.getValue() && stage <= pistonMaxStage.getValue()) {
                    Direction side = BlockUtil.getPlaceSide(pos);
                    if (side == null) {
                        return;
                    }
                    int old = mc.player.getInventory().getSelectedSlot();
                    AutoPush.pistonFacing(facing);
                    int piston = findClass(PistonBaseBlock.class);
                    doSwap(piston);
                    BlockUtil.placeBlock(pos, false, pistonPacket.getValue());
                    if (inventory.getValue()) {
                        doSwap(piston);
                        EntityUtil.syncInventory();
                    } else {
                        doSwap(old);
                    }
                    BlockPos neighbour = pos.relative(side);
                    Direction opposite = side.getOpposite();
                    if (rotate.getValue()) {
                        Alien.ROTATION.lookAt(neighbour, opposite);
                    }
                }
            } else {
                return;
            }
        }
        if (stage >= powerStage.getValue() && stage <= powerMaxStage.getValue()) {
            doRedStone(pos, facing, oPos.relative(facing));
        }
        if (stage >= crystalStage.getValue() && stage <= crystalMaxStage.getValue()) {
            placeCrystal(oPos, facing);
        }
        if (stage >= fireStage.getValue() && stage <= fireMaxStage.getValue()) {
            doFire(oPos, facing);
        }
    }

    private void placeCrystal(BlockPos pos, Direction facing) {
        if (!BlockUtil.canPlaceCrystal(pos.relative(facing))) return;
        int crystal = findItem(Items.END_CRYSTAL);
        if (crystal == -1) return;
        int old = mc.player.getInventory().getSelectedSlot();
        doSwap(crystal);
        BlockUtil.placeCrystal(pos.relative(facing), true);
        if (inventory.getValue()) {
            doSwap(crystal);
            EntityUtil.syncInventory();
        } else {
            doSwap(old);
        }
    }

    private boolean isPiston(BlockPos pos, Direction facing) {
        if (!(mc.level.getBlockState(pos).getBlock() instanceof PistonBaseBlock)) return false;
        if (mc.level.getBlockState(pos).getValue(DirectionalBlock.FACING).getOpposite() != facing) return false;
        return mc.level.getBlockState(pos.relative(facing, -1)).isAir() || getBlock(pos.relative(facing, -1)) == Blocks.FIRE || getBlock(pos.relative(facing.getOpposite())) == Blocks.MOVING_PISTON;
    }

    private void doFire(BlockPos pos, Direction facing) {
        if (!fire.getValue()) return;
        int fire = findItem(Items.FLINT_AND_STEEL);
        if (fire == -1) return;
        int old = mc.player.getInventory().getSelectedSlot();

        int[] xOffset = {0, facing.getStepZ(), -facing.getStepZ()};
        int[] yOffset = {0, 1};
        int[] zOffset = {0, facing.getStepX(), -facing.getStepX()};
        for (int x : xOffset) {
            for (int y : yOffset) {
                for (int z : zOffset) {
                    if (getBlock(pos.offset(x, y, z)) == Blocks.FIRE) {
                        return;
                    }
                }
            }
        }
        for (int x : xOffset) {
            for (int y : yOffset) {
                for (int z : zOffset) {
                    if (canFire(pos.offset(x, y, z))) {
                        doSwap(fire);
                        placeFire(pos.offset(x, y, z));
                        if (inventory.getValue()) {
                            doSwap(fire);
                            EntityUtil.syncInventory();
                        } else {
                            doSwap(old);
                        }
                        return;
                    }
                }
            }
        }
    }

    public void placeFire(BlockPos pos) {
        BlockPos neighbour = pos.relative(Direction.DOWN);
        BlockUtil.clickBlock(neighbour, Direction.UP, this.rotate.getValue());
    }

    private void doRedStone(BlockPos pos, Direction facing, BlockPos crystalPos) {
        if (!mc.level.getBlockState(pos.relative(facing, -1)).isAir() && getBlock(pos.relative(facing, -1)) != Blocks.FIRE && getBlock(pos.relative(facing.getOpposite())) != Blocks.MOVING_PISTON)
            return;
        for (Direction i : Direction.values()) {
            if (getBlock(pos.relative(i)) == Blocks.REDSTONE_BLOCK) return;
        }
        int power = findBlock(Blocks.REDSTONE_BLOCK);
        if (power == -1) return;
        int old = mc.player.getInventory().getSelectedSlot();
        Direction bestNeighboring = BlockUtil.getBestNeighboring(pos, facing);
        if (bestNeighboring != null && bestNeighboring != facing.getOpposite() && BlockUtil.canPlace(pos.relative(bestNeighboring), placeRange.getValue()) && !pos.relative(bestNeighboring).equals(crystalPos)) {
            doSwap(power);
            BlockUtil.placeBlock(pos.relative(bestNeighboring), rotate.getValue());
            if (inventory.getValue()) {
                doSwap(power);
                EntityUtil.syncInventory();
            } else {
                doSwap(old);
            }
            return;
        }
        for (Direction i : Direction.values()) {
            if (!BlockUtil.canPlace(pos.relative(i), placeRange.getValue()) || pos.relative(i).equals(crystalPos) || i == facing.getOpposite())
                continue;
            doSwap(power);
            BlockUtil.placeBlock(pos.relative(i), rotate.getValue());
            if (inventory.getValue()) {
                doSwap(power);
                EntityUtil.syncInventory();
            } else {
                doSwap(old);
            }
            return;
        }
    }

    private void doSwap(int slot) {
        if (inventory.getValue()) {
            InventoryUtil.inventorySwap(slot, mc.player.getInventory().getSelectedSlot());
        } else {
            InventoryUtil.switchToSlot(slot);
        }
    }

    public int findItem(Item itemIn) {
        if (inventory.getValue()) {
            return InventoryUtil.findItemInventorySlot(itemIn);
        } else {
            return InventoryUtil.findItem(itemIn);
        }
    }

    public int findBlock(Block blockIn) {
        if (inventory.getValue()) {
            return InventoryUtil.findBlockInventorySlot(blockIn);
        } else {
            return InventoryUtil.findBlock(blockIn);
        }
    }

    public int findClass(Class<?> clazz) {
        if (inventory.getValue()) {
            return InventoryUtil.findClassInventorySlot(clazz);
        } else {
            return InventoryUtil.findClass(clazz);
        }
    }

    private Block getBlock(BlockPos pos) {
        return mc.level.getBlockState(pos).getBlock();
    }
}
