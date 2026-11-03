package dev.luminous.mod.modules.impl.combat;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.ClientTickEvent;
import dev.luminous.api.utils.combat.CombatUtil;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.api.utils.world.BlockPosX;
import dev.luminous.api.utils.world.BlockUtil;
import dev.luminous.core.impl.CommandManager;
import dev.luminous.core.impl.RotationManager;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.client.AntiCheat;
import dev.luminous.mod.modules.impl.exploit.Blink;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

import static dev.luminous.api.utils.world.BlockUtil.canReplace;

public class Burrow extends Module {
    public static Burrow INSTANCE;
    private final EnumSetting<RotateMode> rotate = add(new EnumSetting<>("RotateMode", RotateMode.Bypass));
    private final EnumSetting<LagBackMode> lagMode = add(new EnumSetting<>("LagMode", LagBackMode.TrollHack));
    private final EnumSetting<LagBackMode> aboveLagMode = add(new EnumSetting<>("MoveLagMode", LagBackMode.Smart));
    private final List<BlockPos> placePos = new ArrayList<>();
    private final Timer timer = new Timer();
    private final Timer webTimer = new Timer();
    private final BooleanSetting disable = add(new BooleanSetting("Disable", true));
    private final BooleanSetting jumpDisable = add(new BooleanSetting("JumpDisable", true, () -> !disable.getValue()));
    private final SliderSetting delay = add(new SliderSetting("Delay", 500, 0, 1000, () -> !disable.getValue()));
    private final SliderSetting webTime = add(new SliderSetting("WebTime", 0, 0, 500));
    private final BooleanSetting enderChest = add(new BooleanSetting("EnderChest", true));
    private final BooleanSetting antiLag = add(new BooleanSetting("AntiLag", false));
    private final BooleanSetting single = add(new BooleanSetting("SingleBlock", false));
    private final BooleanSetting detectMine = add(new BooleanSetting("DetectMining", false));
    private final BooleanSetting headFill = add(new BooleanSetting("HeadFill", false));
    private final BooleanSetting usingPause = add(new BooleanSetting("UsingPause", false));
    private final BooleanSetting down = add(new BooleanSetting("Down", true));
    private final BooleanSetting noSelfPos = add(new BooleanSetting("NoSelfPos", false));
    private final BooleanSetting packetPlace =
            add(new BooleanSetting("PacketPlace", true));
    private final BooleanSetting sound =
            add(new BooleanSetting("Sound", true));
    private final SliderSetting blocksPer = add(new SliderSetting("BlocksPer", 4, 1, 4, 1));
    private final BooleanSetting breakCrystal = add(new BooleanSetting("Break", true));
    private final BooleanSetting wait = add(new BooleanSetting("Wait", true, disable::getValue));
    private final BooleanSetting fakeMove = add(new BooleanSetting("FakeMove", true).setParent());
    private final BooleanSetting center = add(new BooleanSetting("AllowCenter", true, fakeMove::isOpen));
    private final SliderSetting preCorrect = add(new SliderSetting("PreCorrect", 0.25, 0, 1, 0.001, fakeMove::isOpen));
    private final SliderSetting moveDis = add(new SliderSetting("MoveDis", 0.25, 0, 1, 0.001, fakeMove::isOpen));
    private final SliderSetting moveDis2 = add(new SliderSetting("MoveDis2", 0.25, 0, 1, 0.001, fakeMove::isOpen));
    private final SliderSetting moveCorrect2 = add(new SliderSetting("Correct", 0.25, 0, 1, 0.001, fakeMove::isOpen));
    private final SliderSetting yOffset = add(new SliderSetting("YOffset", 0.01, 0, 1, 0.001, fakeMove::isOpen));
    private final BooleanSetting inventory = add(new BooleanSetting("InventorySwap", true));
    private final SliderSetting smartX = add(new SliderSetting("SmartXZ", 3, 0, 10, 0.1, () -> lagMode.getValue() == LagBackMode.Smart || aboveLagMode.getValue() == LagBackMode.Smart));
    private final SliderSetting smartUp = add(new SliderSetting("SmartUp", 3, 0, 10, 0.1, () -> lagMode.getValue() == LagBackMode.Smart || aboveLagMode.getValue() == LagBackMode.Smart));
    private final SliderSetting smartDown = add(new SliderSetting("SmartDown", 3, 0, 10, 0.1, () -> lagMode.getValue() == LagBackMode.Smart || aboveLagMode.getValue() == LagBackMode.Smart));
    private final SliderSetting smartDistance = add(new SliderSetting("SmartDistance", 2, 0, 10, 0.1, () -> lagMode.getValue() == LagBackMode.Smart || aboveLagMode.getValue() == LagBackMode.Smart));
    private int progress = 0;
    private Vec3 currentPos;

    public Burrow() {
        super("Burrow", Category.Combat);
        setChinese("卡黑曜石");
        INSTANCE = this;
    }

    @EventListener
    public void onUpdate(ClientTickEvent event) {
        if (inventory.getValue() && !EntityUtil.inInventory()) return;
        if (event.isPost()) {
            if (!disable.getValue() && jumpDisable.getValue() && mc.player.input.keyPresses.jump()) {
                disable();
                return;
            }
            if (Alien.PLAYER.isInWeb(mc.player)) {
                webTimer.reset();
                return;
            }
            if (usingPause.getValue() && mc.player.isUsingItem()) {
                return;
            }
            if (!webTimer.passedMs(webTime.getValue())) {
                return;
            }
            if (!disable.getValue() && !timer.passedMs(delay.getValue())) {
                return;
            }
            if (!mc.player.onGround()) {
                return;
            }
            if (antiLag.getValue()) {
                if (!BlockUtil.canCollide(mc.player, new AABB(EntityUtil.getPlayerPos(true).below()))) return;
            }
            if (single.getValue() && EntityUtil.isInsideBlock()) {
                if (disable.getValue()) {
                    disable();
                }
                return;
            }
            if (Blink.INSTANCE.isOn() && Blink.INSTANCE.pauseModule.getValue()) return;
            int oldSlot = mc.player.getInventory().getSelectedSlot();
            int block;
            if ((block = getBlock()) == -1) {
                CommandManager.sendMessageId("§4No block found.", hashCode() - 1);
                disable();
                return;
            }
            progress = 0;
            placePos.clear();
            double offset = single.getValue() ? 0 : AntiCheat.getOffset();
            BlockPos pos1 = new BlockPosX(mc.player.getX() + offset, mc.player.getY() + 0.5, mc.player.getZ() + offset);
            BlockPos pos2 = new BlockPosX(mc.player.getX() - offset, mc.player.getY() + 0.5, mc.player.getZ() + offset);
            BlockPos pos3 = new BlockPosX(mc.player.getX() + offset, mc.player.getY() + 0.5, mc.player.getZ() - offset);
            BlockPos pos4 = new BlockPosX(mc.player.getX() - offset, mc.player.getY() + 0.5, mc.player.getZ() - offset);
            BlockPos pos5 = new BlockPosX(mc.player.getX() + offset, mc.player.getY() + 1.5, mc.player.getZ() + offset);
            BlockPos pos6 = new BlockPosX(mc.player.getX() - offset, mc.player.getY() + 1.5, mc.player.getZ() + offset);
            BlockPos pos7 = new BlockPosX(mc.player.getX() + offset, mc.player.getY() + 1.5, mc.player.getZ() - offset);
            BlockPos pos8 = new BlockPosX(mc.player.getX() - offset, mc.player.getY() + 1.5, mc.player.getZ() - offset);
            BlockPos pos9 = new BlockPosX(mc.player.getX() + offset, mc.player.getY() - 1, mc.player.getZ() + offset);
            BlockPos pos10 = new BlockPosX(mc.player.getX() - offset, mc.player.getY() - 1, mc.player.getZ() + offset);
            BlockPos pos11 = new BlockPosX(mc.player.getX() + offset, mc.player.getY() - 1, mc.player.getZ() - offset);
            BlockPos pos12 = new BlockPosX(mc.player.getX() - offset, mc.player.getY() - 1, mc.player.getZ() - offset);
            BlockPos playerPos = EntityUtil.getPlayerPos(true);
            boolean headFill = false;
            if (!canPlace(pos1) && !canPlace(pos2) && !canPlace(pos3) && !canPlace(pos4)) {
                boolean cantHeadFill = !this.headFill.getValue() || !canPlace(pos5) && !canPlace(pos6) && !canPlace(pos7) && !canPlace(pos8);
                boolean cantDown = !down.getValue() || !canPlace(pos9) && !canPlace(pos10) && !canPlace(pos11) && !canPlace(pos12);
                if (cantHeadFill) {
                    if (cantDown) {
                        if (!wait.getValue() && disable.getValue()) {
                            disable();
                        }
                        return;
                    }
                } else {
                    headFill = true;
                }
            }
            boolean above = false;
            BlockPos headPos = EntityUtil.getPlayerPos(true).above(2);
            boolean rotate = this.rotate.getValue() == RotateMode.Normal;
            CombatUtil.attackCrystal(pos1, rotate, false);
            CombatUtil.attackCrystal(pos2, rotate, false);
            CombatUtil.attackCrystal(pos3, rotate, false);
            CombatUtil.attackCrystal(pos4, rotate, false);
            if (headFill || mc.player.isVisuallyCrawling() || trapped(headPos) || trapped(headPos.offset(1, 0, 0)) || trapped(headPos.offset(-1, 0, 0)) || trapped(headPos.offset(0, 0, 1)) || trapped(headPos.offset(0, 0, -1)) || trapped(headPos.offset(1, 0, -1)) || trapped(headPos.offset(-1, 0, -1)) || trapped(headPos.offset(1, 0, 1)) || trapped(headPos.offset(-1, 0, 1))) {
                above = true;
                if (!fakeMove.getValue()) {
                    if (!wait.getValue() && disable.getValue()) disable();
                    return;
                }
                if (!fakeMove()) return;
            } else {
                mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY() + 0.4199999868869781, mc.player.getZ(), false, false));
                mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY() + 0.7531999805212017, mc.player.getZ(), false, false));
                mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY() + 0.9999957640154541, mc.player.getZ(), false, false));
                mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY() + 1.1661092609382138, mc.player.getZ(), false, false));
                currentPos = new Vec3(mc.player.getX(), mc.player.getY() + 1.1661092609382138, mc.player.getZ());
            }
            timer.reset();
            doSwap(block);
            if (this.rotate.getValue() == RotateMode.Bypass) {
                if (above) {
                    float[] angle = RotationManager.getRotation(currentPos.add(0, mc.player.getEyeHeight(mc.player.getPose()), 0), mc.player.position());
                    Alien.ROTATION.snapAt(angle[0], angle[1]);
                } else {
                    Alien.ROTATION.snapAt(Alien.ROTATION.rotationYaw, 90);
                }
            }
            placeBlock(playerPos, rotate);
            placeBlock(pos1, rotate);
            placeBlock(pos2, rotate);
            placeBlock(pos3, rotate);
            placeBlock(pos4, rotate);
            if (down.getValue()) {
                placeBlock(pos9, rotate);
                placeBlock(pos10, rotate);
                placeBlock(pos11, rotate);
                placeBlock(pos12, rotate);
            }
            if (this.headFill.getValue() && above) {
                placeBlock(pos5, rotate);
                placeBlock(pos6, rotate);
                placeBlock(pos7, rotate);
                placeBlock(pos8, rotate);
            }
            if (inventory.getValue()) {
                doSwap(block);
                EntityUtil.syncInventory();
            } else {
                doSwap(oldSlot);
            }
            switch (above ? aboveLagMode.getValue() : lagMode.getValue()) {
                case Smart -> {
                    ArrayList<BlockPos> list = new ArrayList<>();
                    for (double x = mc.player.position().x - smartX.getValue(); x < mc.player.position().x + smartX.getValue(); ++x) {
                        for (double z = mc.player.position().z - smartX.getValue(); z < mc.player.position().z + smartX.getValue(); ++z) {
                            for (double y = mc.player.position().y - smartDown.getValue(); y < mc.player.position().y + smartUp.getValue(); ++y) {
                                list.add(new BlockPosX(x, y, z));
                            }
                        }
                    }

                    double distance = 0;
                    BlockPos bestPos = null;
                    for (BlockPos pos : list) {
                        if (!canMove(pos)) continue;
                        if (Mth.sqrt((float) mc.player.distanceToSqr(Vec3.atCenterOf(pos).add(0, -0.5, 0))) < smartDistance.getValue())
                            continue;
                        if (bestPos == null || mc.player.distanceToSqr(Vec3.atCenterOf(pos)) < distance) {
                            bestPos = pos;
                            distance = mc.player.distanceToSqr(Vec3.atCenterOf(pos));
                        }
                    }
                    if (bestPos != null) {
                        mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(bestPos.getX() + 0.5, bestPos.getY(), bestPos.getZ() + 0.5, false, false));
                    }
                }
                case Invalid -> {
                    for (int i = 0; i < 20; i++)
                        mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY() + 1337, mc.player.getZ(), false, false));
                }
                case Fly -> {
                    mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY() + 1.16610926093821, mc.player.getZ(), false, false));
                    mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY() + 1.170005801788139, mc.player.getZ(), false, false));
                    mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY() + 1.2426308013947485, mc.player.getZ(), false, false));
                    mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY() + 2.3400880035762786, mc.player.getZ(), false, false));
                    mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY() + 2.6400880035762786, mc.player.getZ(), false, false));
                }
                case Glide -> {
                    mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY() + 1.1001, mc.player.getZ(), false, false));
                    mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY() + 1.0605, mc.player.getZ(), false, false));
                    mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY() + 1.0802, mc.player.getZ(), false, false));
                    mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY() + 1.1127, mc.player.getZ(), false, false));
                }
                case TrollHack ->
                        mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY() + 2.3400880035762786, mc.player.getZ(), false, false));
                case Normal ->
                        mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY() + 1.9, mc.player.getZ(), false, false));
                case ToVoid ->
                        mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), -70, mc.player.getZ(), false, false));
                case ToVoid2 ->
                        mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), -7, mc.player.getZ(), false, false));
                case Rotation -> {
                    mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(-180, -90, false, false));
                    mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(180, 90, false, false));
                }
            }
            if (disable.getValue()) disable();
        }
    }

    private boolean fakeMove() {

        double[] offsets = new double[]{1, 0, -1};
        List<BlockPos> offList = new ArrayList<>();
        BlockPos playerPos = mc.player.blockPosition();
        for (double x : offsets) {
            for (double z : offsets) {
                offList.add(new BlockPosX(mc.player.getX() + x, mc.player.getY(), mc.player.getZ() + z));
            }
        }

        for (BlockPos offPos : offList) {
            if (checkSelf(offPos) && !canReplace(offPos) && (!this.headFill.getValue() || !canReplace(offPos.above()))) {
                gotoPos(offPos);
                return true;
            }
        }

        List<BlockPos> pos = new ArrayList<>();
        for (BlockPos offPos : offList) {
            if (!playerPos.equals(offPos) && checkSelf(offPos) && canMove(offPos)) {
                pos.add(offPos);
            }
        }

        if (!pos.isEmpty()) {
            double dis = 10;
            BlockPos target = null;
            for (BlockPos p : pos) {
                double distance = mc.player.position().distanceTo(Vec3.atCenterOf(p).add(0, -0.5, 0));
                if (distance < dis || target == null) {
                    target = p;
                    dis = distance;
                }
            }
            gotoPos(target);
            return true;
        }

        for (BlockPos offPos : offList) {
            if (!playerPos.equals(offPos) && checkSelf(offPos)) {
                pos.add(offPos);
            }
        }

        if (!pos.isEmpty()) {
            double dis = 10;
            BlockPos target = null;
            for (BlockPos p : pos) {
                double distance = mc.player.position().distanceTo(Vec3.atCenterOf(p).add(0, -0.5, 0));
                if (distance < dis || target == null) {
                    target = p;
                    dis = distance;
                }
            }
            gotoPos(target);
            return true;
        }
        if (!center.getValue()) {
            return false;
        }

        offList.clear();
        offList.add(new BlockPosX(mc.player.getX() + 1, mc.player.getY(), mc.player.getZ()));
        offList.add(new BlockPosX(mc.player.getX() - 1, mc.player.getY(), mc.player.getZ()));
        offList.add(new BlockPosX(mc.player.getX(), mc.player.getY(), mc.player.getZ() - 1));
        offList.add(new BlockPosX(mc.player.getX(), mc.player.getY(), mc.player.getZ() + 1));

        for (BlockPos offPos : offList) {
            if (canMove(offPos)) {
                gotoPos(offPos);
                return true;
            }
        }

        if (!wait.getValue() && disable.getValue()) disable();
        return false;
    }

    private void placeBlock(BlockPos pos, boolean rotate) {
        if (canPlace(pos) && !placePos.contains(pos) && progress < blocksPer.getValueInt()) {
            placePos.add(pos);
            if (BlockUtil.allowAirPlace()) {
                progress++;
                BlockUtil.placedPos.add(pos);
                if (sound.getValue())
                    mc.level.playSound(mc.player, pos, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1.0F, 0.8F);
                clickBlock(pos, Direction.DOWN, rotate, InteractionHand.MAIN_HAND, packetPlace.getValue());
            }
            Direction side;
            if ((side = BlockUtil.getPlaceSide(pos)) == null) return;
            progress++;
            BlockUtil.placedPos.add(pos);
            if (sound.getValue())
                mc.level.playSound(mc.player, pos, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1.0F, 0.8F);
            clickBlock(pos.relative(side), side.getOpposite(), rotate, InteractionHand.MAIN_HAND, packetPlace.getValue());
        }
    }

    public void clickBlock(BlockPos pos, Direction side, boolean rotate, InteractionHand hand, boolean packet) {
        Vec3 directionVec = new Vec3(pos.getX() + 0.5 + side.getStepX() * 0.5, pos.getY() + 0.5 + side.getStepY() * 0.5, pos.getZ() + 0.5 + side.getStepZ() * 0.5);
        if (rotate) {
            float[] angle = RotationManager.getRotation(currentPos.add(0, mc.player.getEyeHeight(mc.player.getPose()), 0), directionVec);
            Alien.ROTATION.snapAt(angle[0], angle[1]);
        }
        EntityUtil.swingHand(hand, AntiCheat.INSTANCE.interactSwing.getValue());
        BlockHitResult result = new BlockHitResult(directionVec, side, pos, false);
        if (packet) {
            Module.sendSequencedPacket(id -> new ServerboundUseItemOnPacket(hand, result, id));
        } else {
            mc.gameMode.useItemOn(mc.player, hand, result);
        }
        if (rotate) {
            Alien.ROTATION.snapBack();
        }
    }

    private void doSwap(int slot) {
        if (inventory.getValue()) {
            InventoryUtil.inventorySwap(slot, mc.player.getInventory().getSelectedSlot());
        } else {
            InventoryUtil.switchToSlot(slot);
        }
    }

    private void gotoPos(BlockPos offPos) {
        double targetX = offPos.getX() + 0.5;
        double targetZ = offPos.getZ() + 0.5;

        double x = mc.player.getX();
        double z = mc.player.getZ();

        double y = mc.player.getY() + yOffset.getValue();

        double xDiff = Math.abs(x - targetX);
        double zDiff = Math.abs(z - targetZ);

        double moveDis = preCorrect.getValue();
        if (moveDis > 0) {
            if (xDiff >= moveDis) {
                if (x > targetX) {
                    x -= moveDis;
                } else {
                    x += moveDis;
                }
                mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(x, y, z, false, false));
            }
            if (zDiff >= moveDis) {
                if (z > targetZ) {
                    z -= moveDis;
                } else {
                    z += moveDis;
                }
                mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(x, y, z, false, false));
            }
        }

        xDiff = Math.abs(x - targetX);
        zDiff = Math.abs(z - targetZ);
        moveDis = this.moveDis.getValue();
        if (moveDis > 0) {
            while (xDiff > moveDis) {
                if (x > targetX) {
                    x -= moveDis;
                } else {
                    x += moveDis;
                }
                mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(x, y, z, false, false));
                xDiff = Math.abs(x - targetX);
            }
            while (zDiff > moveDis) {
                if (z > targetZ) {
                    z -= moveDis;
                } else {
                    z += moveDis;
                }
                mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(x, y, z, false, false));
                zDiff = Math.abs(z - targetZ);
            }
        }

        moveDis = this.moveDis2.getValue();
        if (moveDis > 0) {
            while (xDiff > moveDis) {
                if (x > targetX) {
                    x -= moveDis;
                } else {
                    x += moveDis;
                }
                mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(x, y, z, false, false));
                xDiff = Math.abs(x - targetX);
            }
            while (zDiff > moveDis) {
                if (z > targetZ) {
                    z -= moveDis;
                } else {
                    z += moveDis;
                }
                mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(x, y, z, false, false));
                zDiff = Math.abs(z - targetZ);
            }
        }

        moveDis = moveCorrect2.getValue();
        if (moveDis > 0) {
            if (xDiff >= moveDis) {
                if (x > targetX) {
                    x -= moveDis;
                } else {
                    x += moveDis;
                }
                mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(x, y, z, false, false));
            }
            if (zDiff >= moveDis) {
                if (z > targetZ) {
                    z -= moveDis;
                } else {
                    z += moveDis;
                }
                mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(x, y, z, false, false));
            }
        }
        currentPos = new Vec3(x, y, z);
    }

    private boolean canMove(BlockPos pos) {
        return mc.level.getBlockState(pos).isAir() && mc.level.getBlockState(pos.above()).isAir();
    }

    private boolean canPlace(BlockPos pos) {
        if (noSelfPos.getValue() && pos.equals(EntityUtil.getPlayerPos(true))) {
            return false;
        }
        if (!BlockUtil.allowAirPlace() && BlockUtil.getPlaceSide(pos) == null) {
            return false;
        }
        if (!BlockUtil.canReplace(pos)) {
            return false;
        }
        if (detectMine.getValue() && Alien.BREAK.isMining(pos)) {
            return false;
        }
        return !hasEntity(pos);
    }

    private boolean hasEntity(BlockPos pos) {
        for (Entity entity : BlockUtil.getEntities(new AABB(pos))) {
            if (entity == mc.player) continue;
            if (!entity.isAlive() || entity instanceof ItemEntity || entity instanceof ExperienceOrb || entity instanceof ThrownExperienceBottle || entity instanceof Arrow || entity instanceof EndCrystal && breakCrystal.getValue())
                continue;
            return true;
        }
        return false;
    }

    private boolean checkSelf(BlockPos pos) {
        return mc.player.getBoundingBox().intersects(new AABB(pos));
    }

    private boolean trapped(BlockPos pos) {
        return (BlockUtil.canCollide(mc.player, new AABB(pos)) || BlockUtil.getBlock(pos) == Blocks.COBWEB) && checkSelf(pos.below(2));
    }

    private int getBlock() {
        if (inventory.getValue()) {
            if (InventoryUtil.findBlockInventorySlot(Blocks.OBSIDIAN) != -1 || !enderChest.getValue()) {
                return InventoryUtil.findBlockInventorySlot(Blocks.OBSIDIAN);
            }
            return InventoryUtil.findBlockInventorySlot(Blocks.ENDER_CHEST);
        } else {
            if (InventoryUtil.findBlock(Blocks.OBSIDIAN) != -1 || !enderChest.getValue()) {
                return InventoryUtil.findBlock(Blocks.OBSIDIAN);
            }
            return InventoryUtil.findBlock(Blocks.ENDER_CHEST);
        }
    }

    private enum RotateMode {Bypass, Normal, None}

    private enum LagBackMode {
        Smart, Invalid, TrollHack, ToVoid, ToVoid2, Normal, Rotation, Fly, Glide
    }
}