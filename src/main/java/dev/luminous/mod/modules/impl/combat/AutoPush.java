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
import dev.luminous.core.impl.RotationManager;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.client.ClientSetting;
import dev.luminous.mod.modules.impl.exploit.Blink;
import dev.luminous.mod.modules.impl.player.PacketMine;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.world.level.block.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;

public class AutoPush extends Module {
    public static AutoPush INSTANCE;
    private final BooleanSetting torch = add(new BooleanSetting("Torch", false));
    private final BooleanSetting rotate = add(new BooleanSetting("Rotation", true));
    private final BooleanSetting yawDeceive = add(new BooleanSetting("YawDeceive", true));
    private final BooleanSetting pistonPacket = add(new BooleanSetting("PistonPacket", false));
    private final BooleanSetting powerPacket = add(new BooleanSetting("PowerPacket", true));
    private final BooleanSetting noEating = add(new BooleanSetting("EatingPause", true));
    private final BooleanSetting mine = add(new BooleanSetting("Mine", true));
    private final BooleanSetting allowWeb = add(new BooleanSetting("AllowWeb", true));
    private final SliderSetting updateDelay = add(new SliderSetting("Delay", 100, 0, 1000));
    private final BooleanSetting selfGround = add(new BooleanSetting("SelfGround", true));
    private final BooleanSetting onlyGround = add(new BooleanSetting("OnlyGround", false));
    private final BooleanSetting autoDisable = add(new BooleanSetting("AutoDisable", true));
    private final SliderSetting range = add(new SliderSetting("Range", 5.0, 0.0, 6.0));
    private final SliderSetting placeRange = add(new SliderSetting("PlaceRange", 5.0, 0.0, 6.0));
    private final SliderSetting surroundCheck = add(new SliderSetting("SurroundCheck", 2, 0, 4));
    private final BooleanSetting inventory =
            add(new BooleanSetting("InventorySwap", true));
    private final Timer timer = new Timer();

    public AutoPush() {
        super("AutoPush", Category.Combat);
        setChinese("活塞推人");
        INSTANCE = this;
    }

    public static void pistonFacing(Direction i) {
        if (i == Direction.EAST) {
            Alien.ROTATION.snapAt(-90.0f, 5.0f);
        } else if (i == Direction.WEST) {
            Alien.ROTATION.snapAt(90.0f, 5.0f);
        } else if (i == Direction.NORTH) {
            Alien.ROTATION.snapAt(180.0f, 5.0f);
        } else if (i == Direction.SOUTH) {
            Alien.ROTATION.snapAt(0.0f, 5.0f);
        }
    }

    @Override
    public void onEnable() {
        AutoCrystal.INSTANCE.lastBreakTimer.reset();
        //CrystalAura.INSTANCE.faceTimer.reset();
    }

    boolean isTargetHere(BlockPos pos, Entity target) {
        return new AABB(pos).intersects(target.getBoundingBox());
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (!timer.passedMs(updateDelay.getValue())) return;
        if (selfGround.getValue() && !mc.player.onGround()) {
            return;
        }
        if (findBlock(getBlockType()) == -1 || findClass(PistonBaseBlock.class) == -1) {
            if (autoDisable.getValue()) disable();
            return;
        }
        if (noEating.getValue() && mc.player.isUsingItem())
            return;
        if (Blink.INSTANCE.isOn() && Blink.INSTANCE.pauseModule.getValue()) return;
        for (Player player : CombatUtil.getEnemies(range.getValue())) {
            if (!canPush(player)) continue;

            float[] offset = new float[]{-0.25f, 0f, 0.25f};
            for (float x : offset) {
                for (float z : offset) {
                    BlockPosX playerPos = new BlockPosX(player.getX() + x, player.getY() + 0.5, player.getZ() + z);
                    for (Direction i : Direction.values()) {
                        if (i == Direction.UP || i == Direction.DOWN) continue;
                        BlockPos pos = playerPos.relative(i);
                        if (isTargetHere(pos, player)) {
                            if (!mc.level.noCollision(player, new AABB(pos))) {
                                if (tryPush(playerPos.relative(i.getOpposite()), i)) {
                                    timer.reset();
                                    return;
                                }
                                if (tryPush(playerPos.relative(i.getOpposite()).above(), i)) {
                                    timer.reset();
                                    return;
                                }
                            }
                        }
                    }
                }
            }

            if (mc.level.noCollision(player, new AABB(new BlockPosX(player.getX(), player.getY() + 2.5, player.getZ())))) {
                for (Direction i : Direction.values()) {
                    if (i == Direction.UP || i == Direction.DOWN) continue;
                    BlockPos pos = EntityUtil.getEntityPos(player).relative(i);
                    AABB box = player.getBoundingBox().move(new Vec3(i.getStepX(), i.getStepY(), i.getStepZ()));
                    if (getBlock(pos.above()) != Blocks.PISTON_HEAD && mc.level.noCollision(player, box.move(0, 1, 0)) && !isTargetHere(pos, player)) {
                        if (tryPush(EntityUtil.getEntityPos(player).relative(i.getOpposite()).above(), i)) {
                            timer.reset();
                            return;
                        }
                        if (tryPush(EntityUtil.getEntityPos(player).relative(i.getOpposite()), i)) {
                            timer.reset();
                            return;
                        }
                    }
                }
            }

            for (float x : offset) {
                for (float z : offset) {
                    BlockPosX playerPos = new BlockPosX(player.getX() + x, player.getY() + 0.5, player.getZ() + z);
                    for (Direction i : Direction.values()) {
                        if (i == Direction.UP || i == Direction.DOWN) continue;
                        BlockPos pos = playerPos.relative(i);
                        if (isTargetHere(pos, player)) {
                            if (tryPush(playerPos.relative(i.getOpposite()).above(), i)) {
                                timer.reset();
                                return;
                            }
                            if (tryPush(playerPos.relative(i.getOpposite()), i)) {
                                timer.reset();
                                return;
                            }
                        }
                    }
                }
            }
        }

    }

    private boolean tryPush(BlockPos piston, Direction direction) {
        if (!mc.level.getBlockState(piston.relative(direction)).isAir()) return false;
        if (isTrueFacing(piston, direction) && facingCheck(piston)) {
            if (BlockUtil.clientCanPlace(piston, false)) {
                boolean canPower = false;
                if (BlockUtil.getPlaceSide(piston, placeRange.getValue()) != null) {
                    CombatUtil.modifyPos = piston;
                    CombatUtil.modifyBlockState = Blocks.PISTON.defaultBlockState();
                    for (Direction i : Direction.values()) {
                        if (getBlock(piston.relative(i)) == getBlockType()) {
                            canPower = true;
                            break;
                        }
                    }
                    for (Direction i : Direction.values()) {
                        if (canPower) break;
                        if (BlockUtil.canPlace(piston.relative(i), placeRange.getValue())) {
                            canPower = true;
                        }
                    }
                    CombatUtil.modifyPos = null;

                    if (canPower) {
                        int pistonSlot = findClass(PistonBaseBlock.class);
                        Direction side = BlockUtil.getPlaceSide(piston);
                        if (side != null) {
                            if (rotate.getValue()) Alien.ROTATION.lookAt(piston.relative(side), side.getOpposite());
                            if (yawDeceive.getValue()) pistonFacing(direction.getOpposite());
                            int old = mc.player.getInventory().getSelectedSlot();
                            doSwap(pistonSlot);
                            BlockUtil.placeBlock(piston, false, pistonPacket.getValue());
                            if (inventory.getValue()) {
                                doSwap(pistonSlot);
                                EntityUtil.syncInventory();
                            } else {
                                doSwap(old);
                            }
                            if (rotate.getValue() && yawDeceive.getValue())
                                Alien.ROTATION.lookAt(piston.relative(side), side.getOpposite());
                            if (rotate.getValue()) {
                                Alien.ROTATION.snapBack();
                            }
                            for (Direction i : Direction.values()) {
                                if (getBlock(piston.relative(i)) == getBlockType()) {
                                    if (mine.getValue()) {
                                        PacketMine.INSTANCE.mine(piston.relative(i));
                                    }
                                    if (autoDisable.getValue()) {
                                        disable();
                                    }
                                    return true;
                                }
                            }
                            for (Direction i : Direction.values()) {
                                if (i == Direction.UP && torch.getValue()) continue;
                                if (BlockUtil.canPlace(piston.relative(i), placeRange.getValue())) {
                                    int oldSlot = mc.player.getInventory().getSelectedSlot();
                                    int powerSlot = findBlock(getBlockType());
                                    doSwap(powerSlot);
                                    BlockUtil.placeBlock(piston.relative(i), rotate.getValue(), powerPacket.getValue());
                                    if (inventory.getValue()) {
                                        doSwap(powerSlot);
                                        EntityUtil.syncInventory();
                                    } else {
                                        doSwap(oldSlot);
                                    }
                                    if (mine.getValue()) {
                                        PacketMine.INSTANCE.mine(piston.relative(i));
                                    }
                                    return true;
                                }
                            }

                            return true;
                        }
                    }
                } else {
                    Direction powerFacing = null;
                    for (Direction i : Direction.values()) {
                        if (i == Direction.UP && torch.getValue()) continue;
                        if (powerFacing != null) break;
                        CombatUtil.modifyPos = piston.relative(i);
                        CombatUtil.modifyBlockState = getBlockType().defaultBlockState();
                        if (BlockUtil.getPlaceSide(piston) != null) {
                            powerFacing = i;
                        }
                        CombatUtil.modifyPos = null;
                        if (powerFacing != null && !BlockUtil.canPlace(piston.relative(powerFacing))) {
                            powerFacing = null;
                        }
                    }
                    if (powerFacing != null) {
                        int oldSlot = mc.player.getInventory().getSelectedSlot();
                        int powerSlot = findBlock(getBlockType());
                        doSwap(powerSlot);
                        BlockUtil.placeBlock(piston.relative(powerFacing), rotate.getValue(), powerPacket.getValue());
                        if (inventory.getValue()) {
                            doSwap(powerSlot);
                            EntityUtil.syncInventory();
                        } else {
                            doSwap(oldSlot);
                        }

                        CombatUtil.modifyPos = piston.relative(powerFacing);
                        CombatUtil.modifyBlockState = getBlockType().defaultBlockState();
                        int pistonSlot = findClass(PistonBaseBlock.class);
                        Direction side = BlockUtil.getPlaceSide(piston);
                        if (side != null) {
                            if (rotate.getValue()) Alien.ROTATION.lookAt(piston.relative(side), side.getOpposite());
                            if (yawDeceive.getValue()) pistonFacing(direction.getOpposite());
                            int old = mc.player.getInventory().getSelectedSlot();
                            doSwap(pistonSlot);
                            BlockUtil.placeBlock(piston, false, pistonPacket.getValue());
                            if (inventory.getValue()) {
                                doSwap(pistonSlot);
                                EntityUtil.syncInventory();
                            } else {
                                doSwap(old);
                            }
                            if (rotate.getValue() && yawDeceive.getValue())
                                Alien.ROTATION.lookAt(piston.relative(side), side.getOpposite());
                            if (rotate.getValue()) {
                                Alien.ROTATION.snapBack();
                            }
                        }
                        CombatUtil.modifyPos = null;
                        return true;
                    }
                }
            }
        }
        BlockState state = mc.level.getBlockState(piston);
        if (state.getBlock() instanceof PistonBaseBlock && getBlockState(piston).getValue(DirectionalBlock.FACING) == direction) {
            for (Direction i : Direction.values()) {
                if (getBlock(piston.relative(i)) == getBlockType()) {
                    if (autoDisable.getValue()) {
                        disable();
                        return true;
                    }
                    return false;
                }
            }
            for (Direction i : Direction.values()) {
                if (i == Direction.UP && torch.getValue()) continue;
                if (BlockUtil.canPlace(piston.relative(i), placeRange.getValue())) {
                    int oldSlot = mc.player.getInventory().getSelectedSlot();
                    int powerSlot = findBlock(getBlockType());
                    doSwap(powerSlot);
                    BlockUtil.placeBlock(piston.relative(i), rotate.getValue(), powerPacket.getValue());
                    if (inventory.getValue()) {
                        doSwap(powerSlot);
                        EntityUtil.syncInventory();
                    } else {
                        doSwap(oldSlot);
                    }
                    return true;
                }
            }
        }
        return false;
    }

    private boolean facingCheck(BlockPos pos) {
        if (ClientSetting.INSTANCE.lowVersion.getValue()) {
            Direction direction = MathUtil.getDirectionFromEntityLiving(pos, mc.player);
            return direction != Direction.UP && direction != Direction.DOWN;
        }
        return true;
    }

    private boolean isTrueFacing(BlockPos pos, Direction facing) {
        if (yawDeceive.getValue()) return true;
        Direction side = BlockUtil.getPlaceSide(pos);
        if (side == null) return false;
        Vec3 directionVec = new Vec3(pos.getX() + 0.5 + side.getUnitVec3().x * 0.5, pos.getY() + 0.5 + side.getUnitVec3().y * 0.5, pos.getZ() + 0.5 + side.getUnitVec3().z * 0.5);
        float[] rotation =  RotationManager.getRotation(directionVec);
        return MathUtil.getFacingOrder(rotation[0], rotation[1]).getOpposite() == facing;
    }

    private void doSwap(int slot) {
        if (inventory.getValue()) {
            InventoryUtil.inventorySwap(slot, mc.player.getInventory().getSelectedSlot());
        } else {
            InventoryUtil.switchToSlot(slot);
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

    private Boolean canPush(Player player) {
        if (onlyGround.getValue() && !player.onGround()) return false;
        if (!allowWeb.getValue() && Alien.PLAYER.isInWeb(player)) return false;
        float[] offset = new float[]{-0.25f, 0f, 0.25f};

        int progress = 0;

        if (!mc.level.noCollision(player, new AABB(new BlockPosX(player.getX() + 1, player.getY() + 0.5, player.getZ()))))
            progress++;
        if (!mc.level.noCollision(player, new AABB(new BlockPosX(player.getX() - 1, player.getY() + 0.5, player.getZ()))))
            progress++;
        if (!mc.level.noCollision(player, new AABB(new BlockPosX(player.getX(), player.getY() + 0.5, player.getZ() + 1))))
            progress++;
        if (!mc.level.noCollision(player, new AABB(new BlockPosX(player.getX(), player.getY() + 0.5, player.getZ() - 1))))
            progress++;

        for (float x : offset) {
            for (float z : offset) {
                BlockPosX playerPos = new BlockPosX(player.getX() + x, player.getY() + 0.5, player.getZ() + z);
                for (Direction i : Direction.values()) {
                    if (i == Direction.UP || i == Direction.DOWN) continue;
                    BlockPos pos = playerPos.relative(i);
                    if (isTargetHere(pos, player)) {
                        if (!mc.level.noCollision(player, new AABB(pos))) {
                            return true;
                        }
                        if (progress > surroundCheck.getValue() - 1) {
                            return true;
                        }
                    }
                }
            }
        }

        if (mc.level.noCollision(player, new AABB(new BlockPosX(player.getX(), player.getY() + 2.5, player.getZ())))) {
            for (Direction i : Direction.values()) {
                if (i == Direction.UP || i == Direction.DOWN) continue;
                BlockPos pos = EntityUtil.getEntityPos(player).relative(i);
                AABB box = player.getBoundingBox().move(new Vec3(i.getStepX(), i.getStepY(), i.getStepZ()));
                if (getBlock(pos.above()) != Blocks.PISTON_HEAD && mc.level.noCollision(player, box.move(0, 1, 0)) && !isTargetHere(pos, player)) {
                    if (!mc.level.noCollision(player, new AABB(new BlockPosX(player.getX(), player.getY() + 0.5, player.getZ())))) {
                        return true;
                    }
                }
            }
        }

        return progress > surroundCheck.getValue() - 1 || Alien.HOLE.isHard(new BlockPosX(player.getX(), player.getY() + 0.5, player.getZ()));
    }

    private Block getBlock(BlockPos pos) {
        return mc.level.getBlockState(pos).getBlock();
    }

    private Block getBlockType() {
        if (torch.getValue()) {
            return Blocks.REDSTONE_TORCH;
        }
        return Blocks.REDSTONE_BLOCK;
    }

    private BlockState getBlockState(BlockPos pos) {
        return mc.level.getBlockState(pos);
    }
}