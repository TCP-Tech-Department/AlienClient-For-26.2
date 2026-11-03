package dev.luminous.mod.modules.impl.combat;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.combat.CombatUtil;
import dev.luminous.api.utils.math.PredictUtil;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.api.utils.world.BlockPosX;
import dev.luminous.api.utils.world.BlockUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.exploit.Blink;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ConcretePowderBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;

import static dev.luminous.api.utils.world.BlockUtil.isStrictDirection;

public class AutoTrap
        extends Module {
    public static AutoTrap INSTANCE;
    public final SliderSetting delay =
            add(new SliderSetting("Delay", 100, 0, 500).setSuffix("ms"));
    private final EnumSetting<TargetMode> targetMod =
            add(new EnumSetting<>("TargetMode", TargetMode.Single));
    private final EnumSetting<Mode> headMode = add(new EnumSetting<>("BlockForHead", Mode.Anchor));
    final ArrayList<BlockPos> trapList = new ArrayList<>();
    final ArrayList<BlockPos> placeList = new ArrayList<>();
    private final Timer timer = new Timer();
    private final SliderSetting placeRange =
            add(new SliderSetting("PlaceRange", 4.0f, 1.0f, 6.0f).setSuffix("m"));
    private final SliderSetting blocksPer = add(new SliderSetting("BlocksPer", 1, 1, 8));
    public final SliderSetting predictTicks =
            add(new SliderSetting("PredictTicks", 2, 0.0, 50, 1));
    private final BooleanSetting rotate =
            add(new BooleanSetting("Rotate", true));
    private final BooleanSetting autoDisable =
            add(new BooleanSetting("AutoDisable", true));
    private final SliderSetting range =
            add(new SliderSetting("Range", 5.0f, 1.0f, 8.0f).setSuffix("m"));
    private final BooleanSetting checkMine =
            add(new BooleanSetting("DetectMining", false));
    private final BooleanSetting helper =
            add(new BooleanSetting("Helper", true));
    private final BooleanSetting inventory =
            add(new BooleanSetting("InventorySwap", true));
    private final BooleanSetting onlyCrawling =
            add(new BooleanSetting("OnlyCrawling", false));
    private final BooleanSetting checkElytra =
            add(new BooleanSetting("CheckElytra", false));
    private final BooleanSetting extend =
            add(new BooleanSetting("Extend", true));
    private final BooleanSetting antiStep =
            add(new BooleanSetting("AntiStep", false));
    private final BooleanSetting onlyBreak =
            add(new BooleanSetting("OnlyBreak", false, antiStep::getValue));
    private final BooleanSetting head =
            add(new BooleanSetting("Head", true));
    private final BooleanSetting headExtend =
            add(new BooleanSetting("HeadExtend", true));
    private final BooleanSetting chestUp =
            add(new BooleanSetting("ChestUp", true));
    private final BooleanSetting onlyBreaking =
            add(new BooleanSetting("OnlyBreaking", false, chestUp::getValue));
    private final BooleanSetting chest =
            add(new BooleanSetting("Chest", true));
    private final BooleanSetting onlyGround =
            add(new BooleanSetting("OnlyGround", false, chest::getValue));
    private final BooleanSetting ignoreCrawling =
            add(new BooleanSetting("IgnoreCrawling", false, chest::getValue));
    private final BooleanSetting legs =
            add(new BooleanSetting("Legs", false));
    private final BooleanSetting legAnchor =
            add(new BooleanSetting("LegAnchor", true));
    private final BooleanSetting down =
            add(new BooleanSetting("Down", false));
    private final BooleanSetting onlyHole =
            add(new BooleanSetting("OnlyHole", false));
    private final BooleanSetting breakCrystal =
            add(new BooleanSetting("Break", true));
    private final BooleanSetting usingPause = add(new BooleanSetting("UsingPause", true));
    private final BooleanSetting selfGround = add(new BooleanSetting("SelfGround", true));
    public Player target;
    int progress = 0;

    public AutoTrap() {
        super("AutoTrap", Category.Combat);
        setChinese("自动困住");
        INSTANCE = this;
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        trapList.clear();
        placeList.clear();
        progress = 0;
        target = null;
        if (selfGround.getValue() && !mc.player.onGround()) {
            return;
        }
        if (inventory.getValue() && !EntityUtil.inInventory()) return;
        if (Blink.INSTANCE.isOn() && Blink.INSTANCE.pauseModule.getValue()) return;
        if (usingPause.getValue() && mc.player.isUsingItem()) {
            return;
        }
        if (!timer.passed((long) delay.getValue())) {
            return;
        }
        if (targetMod.getValue() == TargetMode.Single) {
            target = CombatUtil.getClosestEnemy(range.getValue());
            if (target == null) {
                if (autoDisable.getValue()) disable();
                return;
            }
            trapTarget(target);
        } else if (targetMod.getValue() == TargetMode.Multi) {
            boolean found = false;
            for (Player player : CombatUtil.getEnemies(range.getValue())) {
                found = true;
                target = player;
                trapTarget(target);
            }
            if (!found) {
                if (autoDisable.getValue()) disable();
                target = null;
            }
        }
    }

    private void trapTarget(Player target) {
        if (onlyHole.getValue() && !Alien.HOLE.isHole(EntityUtil.getEntityPos(target))) return;
        // 26.2: ElytraItem 已移除，鞘翅改由 GLIDER 组件判定
        if (onlyCrawling.getValue() && !target.isVisuallyCrawling() && (!checkElytra.getValue() || !target.getItemBySlot(EquipmentSlot.CHEST).has(DataComponents.GLIDER) || mc.player.getY() < target.getY() + 1 && !target.isFallFlying()))
            return;
        Vec3 playerPos = predictTicks.getValue() > 0 ? PredictUtil.getPos(target, predictTicks.getValueInt()) : target.position();
        doTrap(target, new BlockPosX(playerPos.x, playerPos.y, playerPos.z));
    }

    private void doTrap(Player player, BlockPos pos) {
        if (pos == null) return;
        if (trapList.contains(pos)) {
            return;
        }
        trapList.add(pos);
        int headOffset = player.isVisuallyCrawling() ? 1 : 2;
        int chestOffset = player.isVisuallyCrawling() ? 0 : 1;
        if (legs.getValue()) {
            for (Direction i : Direction.values()) {
                if (i == Direction.DOWN || i == Direction.UP) continue;
                BlockPos offsetPos = pos.relative(i);
                tryPlaceBlock(offsetPos, legAnchor.getValue(), false, false);
                if (BlockUtil.getPlaceSide(offsetPos) == null && BlockUtil.clientCanPlace(offsetPos, breakCrystal.getValue()) && getHelper(offsetPos) != null)
                    tryPlaceObsidian(getHelper(offsetPos));
            }
        }
        if (headExtend.getValue()) {
            for (int x : new int[]{1, 0, -1}) {
                for (int z : new int[]{1, 0, -1}) {
                    BlockPos offsetPos = pos.offset(z, 0, x);
                    if (checkEntity(new BlockPos(offsetPos)))
                        tryPlaceBlock(offsetPos.above(headOffset), headMode.getValue() == Mode.Anchor, headMode.getValue() == Mode.Concrete, headMode.getValue() == Mode.Web);
                }
            }
        }
        if (head.getValue()) {
            if (BlockUtil.clientCanPlace(pos.above(headOffset), breakCrystal.getValue())) {
                if (BlockUtil.getPlaceSide(pos.above(headOffset)) == null) {
                    boolean trapChest = helper.getValue();
                    if (getHelper(pos.above(headOffset)) != null) {
                        tryPlaceObsidian(getHelper(pos.above(headOffset)));
                        trapChest = false;
                    }
                    if (trapChest) {
                        for (Direction i : Direction.values()) {
                            if (i == Direction.DOWN || i == Direction.UP) continue;
                            BlockPos offsetPos = pos.relative(i).above(chestOffset);
                            if (!isStrictDirection(pos.relative(i).above(), i.getOpposite())) continue;
                            if (BlockUtil.clientCanPlace(offsetPos.above(chestOffset), breakCrystal.getValue())) {
                                if (BlockUtil.canPlace(offsetPos, placeRange.getValue(), breakCrystal.getValue())) {
                                    tryPlaceObsidian(offsetPos);
                                    trapChest = false;
                                    break;
                                }
                            }
                        }
                        if (trapChest) {
                            for (Direction i : Direction.values()) {
                                if (i == Direction.DOWN || i == Direction.UP) continue;
                                BlockPos offsetPos = pos.relative(i).above(chestOffset);
                                if (!isStrictDirection(pos.relative(i).above(), i.getOpposite())) continue;
                                if (BlockUtil.clientCanPlace(offsetPos.above(chestOffset), breakCrystal.getValue())) {
                                    if (BlockUtil.getPlaceSide(offsetPos) == null && BlockUtil.clientCanPlace(offsetPos, breakCrystal.getValue()) && getHelper(offsetPos) != null) {
                                        tryPlaceObsidian(getHelper(offsetPos));
                                        trapChest = false;
                                        break;
                                    }
                                }
                            }
                            if (trapChest) {
                                for (Direction i : Direction.values()) {
                                    if (i == Direction.DOWN || i == Direction.UP) continue;
                                    BlockPos offsetPos = pos.relative(i).above(chestOffset);
                                    if (!isStrictDirection(pos.relative(i).above(), i.getOpposite())) continue;
                                    if (BlockUtil.clientCanPlace(offsetPos.above(chestOffset), breakCrystal.getValue())) {
                                        if (BlockUtil.getPlaceSide(offsetPos) == null && BlockUtil.clientCanPlace(offsetPos, breakCrystal.getValue()) && getHelper(offsetPos) != null) {
                                            if (BlockUtil.getPlaceSide(offsetPos.below()) == null && BlockUtil.clientCanPlace(offsetPos.below(), breakCrystal.getValue()) && getHelper(offsetPos.below()) != null) {
                                                tryPlaceObsidian(getHelper(offsetPos.below()));
                                                break;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                tryPlaceBlock(pos.above(headOffset), headMode.getValue() == Mode.Anchor, headMode.getValue() == Mode.Concrete, headMode.getValue() == Mode.Web);
            }
        }
        if (antiStep.getValue() && (Alien.BREAK.isMining(pos.above(headOffset)) || !onlyBreak.getValue())) {
            if (BlockUtil.getPlaceSide(pos.above(3)) == null && BlockUtil.clientCanPlace(pos.above(3), breakCrystal.getValue())) {
                if (getHelper(pos.above(3), Direction.DOWN) != null) {
                    tryPlaceObsidian(getHelper(pos.above(3)));
                }
            }
            tryPlaceObsidian(pos.above(3));
        }
        if (down.getValue()) {
            BlockPos offsetPos = pos.below();
            tryPlaceObsidian(offsetPos);
            if (BlockUtil.getPlaceSide(offsetPos) == null && BlockUtil.clientCanPlace(offsetPos, breakCrystal.getValue()) && getHelper(offsetPos) != null)
                tryPlaceObsidian(getHelper(offsetPos));
        }
        if (chestUp.getValue()) {
            for (Direction i : Direction.values()) {
                if (i == Direction.DOWN || i == Direction.UP) continue;
                BlockPos offsetPos = pos.relative(i).above(headOffset);
                if (!onlyBreaking.getValue() || Alien.BREAK.isMining(pos.above(headOffset))) {
                    tryPlaceObsidian(offsetPos);
                    if (BlockUtil.getPlaceSide(offsetPos) == null && BlockUtil.clientCanPlace(offsetPos, breakCrystal.getValue())) {
                        if (getHelper(offsetPos) != null) {
                            tryPlaceObsidian(getHelper(offsetPos));
                        } else if (BlockUtil.getPlaceSide(offsetPos.below()) == null && BlockUtil.clientCanPlace(offsetPos.below(), breakCrystal.getValue()) && getHelper(offsetPos.below()) != null) {
                            tryPlaceObsidian(getHelper(offsetPos.below()));
                        }
                    }
                }
            }
        }
        if (chest.getValue() && (!onlyGround.getValue() || target.onGround()) && (!ignoreCrawling.getValue() || !target.isVisuallyCrawling())) {
            for (Direction i : Direction.values()) {
                if (i == Direction.DOWN || i == Direction.UP) continue;
                BlockPos offsetPos = pos.relative(i).above(chestOffset);
                tryPlaceObsidian(offsetPos);
                if (BlockUtil.getPlaceSide(offsetPos) == null && BlockUtil.clientCanPlace(offsetPos, breakCrystal.getValue())) {
                    if (getHelper(offsetPos) != null) {
                        tryPlaceObsidian(getHelper(offsetPos));
                    } else if (BlockUtil.getPlaceSide(offsetPos.below()) == null && BlockUtil.clientCanPlace(offsetPos.below(), breakCrystal.getValue()) && getHelper(offsetPos.below()) != null) {
                        tryPlaceObsidian(getHelper(offsetPos.below()));
                    }
                }
            }
        }
        if (extend.getValue()) {
            for (int x : new int[]{1, 0, -1}) {
                for (int z : new int[]{1, 0, -1}) {
                    BlockPos offsetPos = pos.offset(x, 0, z);
                    if (checkEntity(new BlockPos(offsetPos))) {
                        doTrap(player, offsetPos);
                    }
                }
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

    public BlockPos getHelper(BlockPos pos) {
        if (!helper.getValue()) return null;
        for (Direction i : Direction.values()) {
            if (checkMine.getValue() && Alien.BREAK.isMining(pos.relative(i))) continue;
            if (!BlockUtil.isStrictDirection(pos.relative(i), i.getOpposite()))
                continue;
            if (BlockUtil.canPlace(pos.relative(i), placeRange.getValue(), breakCrystal.getValue())) return pos.relative(i);
        }
        return null;
    }

    public BlockPos getHelper(BlockPos pos, Direction ignore) {
        if (!helper.getValue()) return null;
        for (Direction i : Direction.values()) {
            if (i == ignore) continue;
            if (checkMine.getValue() && Alien.BREAK.isMining(pos.relative(i))) continue;
            if (!BlockUtil.isStrictDirection(pos.relative(i), i.getOpposite())) continue;
            if (BlockUtil.canPlace(pos.relative(i), placeRange.getValue(), breakCrystal.getValue())) return pos.relative(i);
        }
        return null;
    }

    private boolean checkEntity(BlockPos pos) {
        if (mc.player.getBoundingBox().intersects(new AABB(pos))) return false;
        for (Entity entity : Alien.THREAD.getPlayers()) {
            if (entity.getBoundingBox().intersects(new AABB(pos)) && entity.isAlive())
                return true;
        }
        return false;
    }

    private void tryPlaceBlock(BlockPos pos, boolean anchor, boolean sand, boolean web) {
        if (placeList.contains(pos)) return;
        if (Alien.BREAK.isMining(pos)) return;
        if (!BlockUtil.canPlace(pos, 6, breakCrystal.getValue())) return;
        if (!(progress < blocksPer.getValue())) return;
        if (Mth.sqrt((float) mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos))) > placeRange.getValue())
            return;
        int old = mc.player.getInventory().getSelectedSlot();
        int block = sand ? getConcrete() : (web ? (getWeb() != -1 ? getWeb() : getBlock()) : (anchor && getAnchor() != -1 ? getAnchor() : getBlock()));
        if (block == -1) return;
        placeList.add(pos);
        CombatUtil.attackCrystal(pos, rotate.getValue(), usingPause.getValue());
        doSwap(block);
        BlockUtil.placeBlock(pos, rotate.getValue());
        if (inventory.getValue()) {
            doSwap(block);
            EntityUtil.syncInventory();
        } else {
            doSwap(old);
        }
        timer.reset();
        progress++;
    }

    private void tryPlaceObsidian(BlockPos pos) {
        if (pos == null) return;
        if (placeList.contains(pos)) return;
        if (Alien.BREAK.isMining(pos)) return;
        if (!BlockUtil.canPlace(pos, 6, breakCrystal.getValue())) return;
        if (!(progress < blocksPer.getValue())) return;
        if (Mth.sqrt((float) mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos))) > placeRange.getValue())
            return;
        int old = mc.player.getInventory().getSelectedSlot();
        int block = getBlock();
        if (block == -1) return;
        BlockUtil.placedPos.add(pos);
        placeList.add(pos);
        CombatUtil.attackCrystal(pos, rotate.getValue(), usingPause.getValue());
        doSwap(block);
        BlockUtil.placeBlock(pos, rotate.getValue());
        if (inventory.getValue()) {
            doSwap(block);
            EntityUtil.syncInventory();
        } else {
            doSwap(old);
        }
        timer.reset();
        progress++;
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

    private int getConcrete() {
        if (inventory.getValue()) {
            return InventoryUtil.findClassInventorySlot(ConcretePowderBlock.class);
        } else {
            return InventoryUtil.findClass(ConcretePowderBlock.class);
        }
    }

    private int getWeb() {
        if (inventory.getValue()) {
            return InventoryUtil.findBlockInventorySlot(Blocks.COBWEB);
        } else {
            return InventoryUtil.findBlock(Blocks.COBWEB);
        }
    }

    private int getAnchor() {
        if (inventory.getValue()) {
            return InventoryUtil.findBlockInventorySlot(Blocks.RESPAWN_ANCHOR);
        } else {
            return InventoryUtil.findBlock(Blocks.RESPAWN_ANCHOR);
        }
    }

    public enum TargetMode {
        Single, Multi
    }

    private enum Mode {
        Obsidian,
        Anchor,
        Web,
        Concrete
    }
}
