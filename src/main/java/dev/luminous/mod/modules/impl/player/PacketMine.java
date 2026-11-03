package dev.luminous.mod.modules.impl.player;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.eventbus.EventPriority;
import dev.luminous.api.events.impl.ClickBlockEvent;
import dev.luminous.api.events.impl.ClientTickEvent;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.api.events.impl.RotationEvent;
import dev.luminous.api.utils.combat.CombatUtil;
import dev.luminous.api.utils.math.Easing;
import dev.luminous.api.utils.math.FadeUtils;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.api.utils.render.Render3DUtil;
import dev.luminous.api.utils.world.BlockUtil;
import dev.luminous.asm.accessors.IPlayerMoveC2SPacket;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.client.AntiCheat;
import dev.luminous.mod.modules.impl.client.ClientSetting;
import dev.luminous.mod.modules.impl.combat.AutoAnchor;
import dev.luminous.mod.modules.impl.combat.AutoCrystal;
import dev.luminous.mod.modules.impl.combat.CevBreaker;
import dev.luminous.mod.modules.impl.combat.Criticals;
import dev.luminous.mod.modules.impl.exploit.Blink;
import dev.luminous.mod.modules.impl.movement.ElytraFly;
import dev.luminous.mod.modules.impl.movement.Velocity;
import dev.luminous.mod.modules.settings.enums.SwingSide;
import dev.luminous.mod.modules.settings.enums.Timing;
import dev.luminous.mod.modules.settings.impl.*;
import net.minecraft.world.level.block.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.*;
import net.minecraft.world.phys.*;
import net.minecraft.util.Mth;

import java.awt.*;
import java.text.DecimalFormat;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static dev.luminous.api.utils.world.BlockUtil.getBlock;
import net.minecraft.world.level.block.state.BlockState;

public class PacketMine extends Module {
    public static PacketMine INSTANCE;
    public static BlockPos secondPos;
    public static double progress = 0;
    private final FadeUtils animationTime = new FadeUtils(1000);
    private final FadeUtils secondAnim = new FadeUtils(1000);
    private final DecimalFormat df = new DecimalFormat("0.0");
    private final EnumSetting<Page> page = add(new EnumSetting<>("Page", Page.General));

    private final SliderSetting stopDelay = add(new SliderSetting("StopDelay", 50, 0, 500, 1, () -> page.is(Page.General)));
    private final SliderSetting startDelay = add(new SliderSetting("StartDelay", 200, 0, 500, 1, () -> page.is(Page.General)));
    private final SliderSetting damage = add(new SliderSetting("Damage", 0.7f, 0.0f, 2.0f, 0.01, () -> page.is(Page.General)));
    private final SliderSetting maxBreak = add(new SliderSetting("MaxBreak", 3, 0, 20, 1, () -> page.is(Page.General)));
    public final BooleanSetting noGhostHand = add(new BooleanSetting("1.21", false, () -> page.is(Page.General)));
    public final BooleanSetting noCollide = add(new BooleanSetting("NoCollide", true, () -> page.is(Page.General)));
    private final EnumSetting<Timing> timing = add(new EnumSetting<>("Timing", Timing.All, () -> page.getValue() == Page.General));
    private final BooleanSetting grimDisabler = add(new BooleanSetting("GrimDisabler", false, () -> page.is(Page.General)));
    private final BooleanSetting instant = add(new BooleanSetting("Instant", false, () -> page.is(Page.General)));
    private final BooleanSetting wait = add(new BooleanSetting("Wait", true, () -> !instant.getValue() && page.is(Page.General)));
    private final BooleanSetting mineAir = add(new BooleanSetting("MineAir", true, () -> wait.getValue() && !instant.getValue() && page.is(Page.General)));
    private final BooleanSetting hotBar = add(new BooleanSetting("HotbarSwap", false, () -> page.is(Page.General)));
    private final BooleanSetting doubleBreak = add(new BooleanSetting("DoubleBreak", true, () -> page.is(Page.General))).setParent();
    public final BooleanSetting autoSwitch = add(new BooleanSetting("AutoSwitch", true, () -> page.is(Page.General) && doubleBreak.isOpen()));
    private final SliderSetting start = add(new SliderSetting("Start", 0.9f, 0.0f, 2.0f, 0.01, () -> page.is(Page.General) && doubleBreak.isOpen()));
    private final SliderSetting timeOut = add(new SliderSetting("TimeOut", 1.2f, 0.0f, 2.0f, 0.01, () -> page.is(Page.General) && doubleBreak.isOpen()));
    private final BooleanSetting setAir = add(new BooleanSetting("SetAir", false, () -> page.is(Page.General)));
    private final BooleanSetting swing = add(new BooleanSetting("Swing", true, () -> page.is(Page.General)));
    private final BooleanSetting endSwing = add(new BooleanSetting("EndSwing", false, () -> page.is(Page.General)));
    public final SliderSetting range = add(new SliderSetting("Range", 6f, 3.0f, 10.0f, 0.1, () -> page.is(Page.General)));
    private final EnumSetting<SwingSide> swingMode = add(new EnumSetting<>("SwingSide", SwingSide.All, () -> page.is(Page.General)));

    private final BooleanSetting unbreakableCancel = add(new BooleanSetting("UnbreakableCancel", true, () -> page.is(Page.Check)));
    private final BooleanSetting switchReset = add(new BooleanSetting("SwitchReset", false, () -> page.is(Page.Check)));
    private final BooleanSetting preferWeb = add(new BooleanSetting("PreferWeb", true, () -> page.is(Page.Check)));
    private final BooleanSetting preferHead = add(new BooleanSetting("PreferHead", true, () -> page.is(Page.Check)));
    private final BooleanSetting farCancel = add(new BooleanSetting("FarCancel", false, () -> page.is(Page.Check)));
    private final BooleanSetting onlyGround = add(new BooleanSetting("OnlyGround", true, () -> page.is(Page.Check)));
    private final BooleanSetting checkWeb = add(new BooleanSetting("CheckWeb", true, () -> page.is(Page.Check)));
    private final BooleanSetting checkGround = add(new BooleanSetting("CheckGround", true, () -> page.is(Page.Check)));
    private final BooleanSetting smart = add(new BooleanSetting("Smart", true, () -> page.is(Page.Check) && checkGround.getValue()));
    private final BooleanSetting usingPause = add(new BooleanSetting("UsingPause", false, () -> page.is(Page.Check)).setParent());
    private final BooleanSetting allowOffhand = add(new BooleanSetting("AllowOffhand", true, () -> page.is(Page.Check) && usingPause.isOpen()));
    private final BooleanSetting bypassGround = add(new BooleanSetting("BypassGround", true, () -> page.is(Page.Check)));
    private final SliderSetting bypassTime = add(new SliderSetting("BypassTime", 400, 0, 2000, () -> bypassGround.getValue() && page.is(Page.Check)));
    private final BindSetting pause = add(new BindSetting("Pause", -1, () -> page.is(Page.Check)));

    private final BooleanSetting rotate = add(new BooleanSetting("StartRotate", true, () -> page.is(Page.Rotation)));
    private final BooleanSetting endRotate = add(new BooleanSetting("EndRotate", false, () -> page.is(Page.Rotation)));
    private final SliderSetting syncTime = add(new SliderSetting("Sync", 300, 0, 1000, () -> page.is(Page.Rotation)));
    private final BooleanSetting yawStep = add(new BooleanSetting("YawStep", false, () -> page.is(Page.Rotation)).setParent());
    private final BooleanSetting whenElytra = add(new BooleanSetting("FallFlying", true, () -> page.is(Page.Rotation) && yawStep.isOpen()));
    private final SliderSetting steps = add(new SliderSetting("Steps", 0.05, 0, 1, 0.01, () -> page.is(Page.Rotation) && yawStep.isOpen()));
    private final BooleanSetting checkFov = add(new BooleanSetting("OnlyLooking", true, () -> page.is(Page.Rotation) && yawStep.isOpen()));
    private final SliderSetting fov = add(new SliderSetting("Fov", 20, 0, 360, 0.1, () -> page.is(Page.Rotation) && yawStep.isOpen()));
    private final SliderSetting priority = add(new SliderSetting("Priority", 10, 0, 100, () -> page.is(Page.Rotation) && yawStep.isOpen()));

    public final BooleanSetting crystal = add(new BooleanSetting("Crystal", false, () -> page.is(Page.Place)).setParent());
    private final BooleanSetting onlyHeadBomber = add(new BooleanSetting("OnlyCev", true, () -> page.is(Page.Place) && crystal.isOpen()));
    private final BooleanSetting waitPlace = add(new BooleanSetting("WaitPlace", true, () -> page.is(Page.Place) && crystal.isOpen()));
    private final BooleanSetting spamPlace = add(new BooleanSetting("SpamPlace", false, () -> page.is(Page.Place) && crystal.isOpen()));
    private final BooleanSetting afterBreak = add(new BooleanSetting("AfterBreak", true, () -> page.is(Page.Place) && crystal.isOpen()));
    private final BooleanSetting checkDamage = add(new BooleanSetting("DetectProgress", true, () -> page.is(Page.Place) && crystal.isOpen()));
    private final SliderSetting crystalDamage = add(new SliderSetting("Progress", 0.9f, 0.0f, 1.0f, 0.01, () -> page.is(Page.Place) && crystal.isOpen() && checkDamage.getValue()));
    public final BindSetting obsidian = add(new BindSetting("Obsidian", -1, () -> page.is(Page.Place)));
    private final BindSetting enderChest = add(new BindSetting("EnderChest", -1, () -> page.is(Page.Place)));
    private final BooleanSetting placeRotate = add(new BooleanSetting("PlaceRotate", true, () -> page.is(Page.Place)));
    private final BooleanSetting inventory = add(new BooleanSetting("InventorySwap", true, () -> page.is(Page.Place)));
    private final SliderSetting placeDelay = add(new SliderSetting("PlaceDelay", 100, 0, 1000, () -> page.is(Page.Place)));

    private final BooleanSetting checkDouble = add(new BooleanSetting("CheckDouble", false, () -> page.is(Page.Render)));
    private final EnumSetting<Animation> animation = add(new EnumSetting<>("Animation", Animation.Up, () -> page.is(Page.Render)));
    private final EnumSetting<Easing> ease = add(new EnumSetting<>("Ease", Easing.CubicInOut, () -> page.is(Page.Render)));
    private final EnumSetting<Easing> fadeEase = add(new EnumSetting<>("FadeEase", Easing.CubicInOut, () -> page.is(Page.Render)));
    private final SliderSetting expandLine = add(new SliderSetting("ExpandLine", 0.0, 0.0, 1, () -> page.is(Page.Render)));
    private final ColorSetting startColor = add(new ColorSetting("StartFill", new Color(255, 255, 255, 100), () -> page.is(Page.Render)));
    private final ColorSetting startOutlineColor = add(new ColorSetting("StartOutline", new Color(255, 255, 255, 100), () -> page.is(Page.Render)));
    private final ColorSetting endColor = add(new ColorSetting("EndFill", new Color(255, 255, 255, 100), () -> page.is(Page.Render)));
    private final ColorSetting endOutlineColor = add(new ColorSetting("EndOutline", new Color(255, 255, 255, 100), () -> page.is(Page.Render)));
    private final ColorSetting doubleColor = add(new ColorSetting("DoubleFill", new Color(88, 94, 255, 100), () -> doubleBreak.getValue() && page.is(Page.Render)));
    private final ColorSetting doubleOutlineColor = add(new ColorSetting("DoubleOutline", new Color(88, 94, 255, 100), () -> doubleBreak.getValue() && page.is(Page.Render)));
    private final BooleanSetting text = add(new BooleanSetting("Text", true, () -> page.is(Page.Render)));
    private final BooleanSetting box = add(new BooleanSetting("Box", true, () -> page.is(Page.Render)));
    private final BooleanSetting outline = add(new BooleanSetting("Outline", true, () -> page.is(Page.Render)));
    private final Timer mineTimer = new Timer();
    private final Timer sync = new Timer();
    private final Timer secondTimer = new Timer();
    private final Timer delayTimer = new Timer();
    private final Timer placeTimer = new Timer();
    private final Timer startTime = new Timer();
    public static boolean ghost = false;
    public static boolean complete = false;
    int lastSlot = -1;
    Vec3 directionVec = null;
    Runnable switchBack;
    BlockPos breakPos;
    boolean startPacket = false;
    int breakNumber = 0;
    double breakFinalTime;
    double secondFinalTime;
    boolean sendGroundPacket = false;
    boolean swapped = false;
    int mainSlot = 0;

    public PacketMine() {
        super("PacketMine", Category.Player);
        setChinese("发包挖掘");
        INSTANCE = this;
    }

    public static BlockPos getBreakPos() {
        if (INSTANCE.isOn()) {
            return INSTANCE.breakPos;
        }
        return null;
    }

    @Override
    public String getInfo() {
        if (progress >= 1) {
            return "Done";
        }
        return df.format(progress * 100) + "%";
    }

    @EventListener
    public void onRotate(RotationEvent event) {
        if (rotate.getValue() && shouldYawStep() && directionVec != null && !sync.passedMs(syncTime.getValue())) {
            event.setTarget(directionVec, steps.getValueFloat(), priority.getValueFloat());
        }
    }

    @Override
    public void onLogin() {
        startPacket = false;
        ghost = false;
        complete = false;
        breakPos = null;
        secondPos = null;
    }

    @Override
    public void onDisable() {
        startPacket = false;
        ghost = false;
        complete = false;
        breakPos = null;
    }

    private void autoSwitch() {
        if (autoSwitch.getValue() && doubleBreak.getValue()) {
            int index = -1;
            if (secondPos != null) {
                float CurrentFastest = 1.0f;
                for (int i = 0; i < 9; ++i) {
                    ItemStack stack = mc.player.getInventory().getItem(i);
                    if (stack != ItemStack.EMPTY) {
                        float digSpeed = EnchantmentHelper.getItemEnchantmentLevel(mc.level.registryAccess().getOrThrow(Enchantments.EFFICIENCY), stack);
                        float destroySpeed = stack.getDestroySpeed(mc.level.getBlockState(secondPos));
                        if (digSpeed + destroySpeed > CurrentFastest) {
                            CurrentFastest = digSpeed + destroySpeed;
                            index = i;
                        }
                    }
                }
            }
            if (index == -1 || mc.options.keyUse.isDown() || mc.options.keyAttack.isDown() || mc.player.isUsingItem() || !secondTimer.passedMs(getBreakTime(secondPos, index, start.getValue()))) {
                if (swapped) {
                    InventoryUtil.switchToSlot(mainSlot);
                    swapped = false;
                }
            } else {
                if (index != mc.player.getInventory().getSelectedSlot()) {
                    mainSlot = mc.player.getInventory().getSelectedSlot();
                    InventoryUtil.switchToSlot(index);
                    swapped = true;
                }
            }
        }
    }

    @EventListener
    public void onTick(ClientTickEvent event) {
        if (nullCheck()) return;
        if (breakPos != null && mc.level.getBlockState(breakPos).isAir()) {
            complete = true;
        }
        if (secondPos != null) {
            int secondSlot = getTool(secondPos);
            if (secondSlot == -1) {
                secondSlot = mc.player.getInventory().getSelectedSlot();
            }
            secondFinalTime = getBreakTime(secondPos, secondSlot, 1);
            if (isAir(secondPos) || unbreakable(secondPos)) {
                secondPos = null;
            } else {
                double time = getBreakTime(secondPos, mc.player.getInventory().getSelectedSlot(), 1);
                if (secondTimer.passedMs(time * timeOut.getValue())) {
                    secondPos = null;
                }
            }
        }
        if (switchBack != null && event.isPre()) {
            switchBack.run();
            switchBack = null;
        }
        if (timing.is(Timing.Pre) && event.isPost() || timing.is(Timing.Post) && event.isPre()) return;
        if (mc.player.isDeadOrDying()) {
            secondPos = null;
        }
        autoSwitch();
        if (mc.player.isCreative()) {
            startPacket = false;
            ghost = false;
            complete = false;
            breakNumber = 0;
            breakPos = null;
            progress = 0;
            return;
        }
        if (breakPos == null) {
            breakNumber = 0;
            startPacket = false;
            ghost = false;
            complete = false;
            progress = 0;
            return;
        }
        int slot = getTool(breakPos);
        if (slot == -1) {
            slot = mc.player.getInventory().getSelectedSlot();
        }
        breakFinalTime = getBreakTime(breakPos, slot);
        progress = (double) mineTimer.getMs() / breakFinalTime;
        if (isAir(breakPos)) {
            breakNumber = 0;
        }
        if (breakNumber > maxBreak.getValue() - 1 && maxBreak.getValue() > 0 && !complete || !wait.getValue() && isAir(breakPos) && !instant.getValue()) {
            if (breakPos.equals(secondPos)) {
                secondPos = null;
            }
            startPacket = false;
            ghost = false;
            complete = false;
            breakNumber = 0;
            breakPos = null;
            return;
        }
        if (unbreakable(breakPos)) {
            if (unbreakableCancel.getValue()) {
                breakPos = null;
                startPacket = false;
                ghost = false;
                complete = false;
            }
            breakNumber = 0;
            return;
        }
        if (Mth.sqrt((float) mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(breakPos))) > range.getValue()) {
            if (farCancel.getValue()) {
                startPacket = false;
                ghost = false;
                complete = false;
                breakNumber = 0;
                breakPos = null;
            }
            return;
        }
        if (usingPause.getValue() && mc.player.isUsingItem() && (!allowOffhand.getValue() || mc.player.getUsedItemHand() == InteractionHand.MAIN_HAND)) {
            return;
        }
        if (pause.isPressed()) {
            return;
        }
        if (Blink.INSTANCE.isOn() && Blink.INSTANCE.pauseModule.getValue()) return;
        if (breakPos.equals(AutoAnchor.INSTANCE.currentPos) && getBlock(getBreakPos()) instanceof RespawnAnchorBlock)
            return;
        if (!hotBar.getValue() && !EntityUtil.inInventory()) {
            return;
        }

        if (isAir(breakPos)) {
            if (shouldCrystal()) {
                for (Direction facing : Direction.values()) {
                    CombatUtil.attackCrystal(breakPos.relative(facing), placeRotate.getValue(), true);
                }
            }
            if (placeTimer.passedMs(placeDelay.getValue())) {
                if (BlockUtil.canPlace(breakPos) && mc.gui.screen() == null) {
                    if (enderChest.isPressed()) {
                        int eChest = findBlock(Blocks.ENDER_CHEST);
                        if (eChest != -1) {
                            int oldSlot = mc.player.getInventory().getSelectedSlot();
                            doSwap(eChest, eChest);
                            BlockUtil.placeBlock(breakPos, placeRotate.getValue(), true);
                            doSwap(oldSlot, eChest);
                            placeTimer.reset();
                        }
                    } else if (obsidian.isPressed()) {

                        int obsidian = findBlock(Blocks.OBSIDIAN);
                        if (obsidian != -1) {

                            boolean hasCrystal = false;
                            if (shouldCrystal()) {
                                for (Entity entity : BlockUtil.getEntities(new AABB(breakPos.above()))) {
                                    if (entity instanceof EndCrystal) {
                                        hasCrystal = true;
                                        break;
                                    }
                                }
                            }

                            if (!hasCrystal || spamPlace.getValue()) {
                                int oldSlot = mc.player.getInventory().getSelectedSlot();
                                doSwap(obsidian, obsidian);
                                BlockUtil.placeBlock(breakPos, placeRotate.getValue(), true);
                                doSwap(oldSlot, obsidian);
                                placeTimer.reset();
                            }
                        }
                    }
                }
            }
            breakNumber = 0;
        } else if (canPlaceCrystal(breakPos.above())) {
            if (shouldCrystal()) {
                if (placeTimer.passedMs(placeDelay.getValue())) {
                    if (checkDamage.getValue()) {
                        if (mineTimer.getMs() / breakFinalTime >= crystalDamage.getValue()) {
                            if (!placeCrystal()) return;
                        }
                    } else {
                        if (!placeCrystal()) return;
                    }
                } else if (startPacket) {
                    return;
                }
            }
        }
        if (waitPlace.getValue()) {
            for (Direction i : Direction.values()) {
                if (breakPos.relative(i).equals(AutoCrystal.INSTANCE.crystalPos)) {
                    if (AutoCrystal.INSTANCE.canPlaceCrystal(breakPos, false, false)) {
                        return;
                    }
                    break;
                }
            }
        }
        if (!delayTimer.passed((long) stopDelay.getValue())) return;
        if (startPacket) {
            if (isAir(breakPos)) {
                return;
            }
            if (onlyGround.getValue() && !mc.player.onGround()) return;
            if (mineTimer.passed((long) breakFinalTime)) {
                if (endRotate.getValue() && shouldYawStep()) {
                    if (!faceVector(clickVector(breakPos, BlockUtil.getClickSide(breakPos)))) {
                        return;
                    }
                }
                int old = mc.player.getInventory().getSelectedSlot();
                boolean shouldSwitch;
                if (hotBar.getValue()) {
                    shouldSwitch = slot != old;
                } else {
                    if (slot < 9) {
                        slot = slot + 36;
                    }
                    shouldSwitch = old + 36 != slot;
                }
                if (shouldSwitch) {
                    if (hotBar.getValue()) {
                        InventoryUtil.switchToSlot(slot);
                    } else {
                        mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, slot, old, ContainerInput.SWAP, mc.player);
                    }
                }

                int finalSlot = slot;
                switchBack = () -> {
                    if (endRotate.getValue()) {
                        if (!faceVector(clickVector(breakPos, BlockUtil.getClickSide(breakPos)))) {
                            if (shouldSwitch) {
                                if (hotBar.getValue()) {
                                    InventoryUtil.switchToSlot(old);
                                } else {
                                    mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, finalSlot, old, ContainerInput.SWAP, mc.player);
                                    EntityUtil.syncInventory();
                                }
                            }
                            return;
                        }
                    }
                    sendSequencedPacket(id -> new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, breakPos, BlockUtil.getClickSide(breakPos), id));
                    if (endSwing.getValue()) EntityUtil.swingHand(InteractionHand.MAIN_HAND, swingMode.getValue());
                    if (shouldSwitch) {
                        if (hotBar.getValue()) {
                            InventoryUtil.switchToSlot(old);
                        } else {
                            mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, finalSlot, old, ContainerInput.SWAP, mc.player);
                            EntityUtil.syncInventory();
                        }
                    }
                    breakNumber++;
                    delayTimer.reset();
                    startTime.reset();
                    if (afterBreak.getValue() && shouldCrystal()) {
                        for (Direction facing : Direction.values()) {
                            CombatUtil.attackCrystal(breakPos.relative(facing), placeRotate.getValue(), true);
                        }
                    }
                    if (setAir.getValue()) {
                        // 26.2: setBlockState → setBlockAndUpdate / getDefaultState → defaultBlockState
                        mc.level.setBlockAndUpdate(breakPos, Blocks.AIR.defaultBlockState());
                    }
                    if (endRotate.getValue() && !shouldYawStep()) {
                        Alien.ROTATION.snapBack();
                    }
                    ghost = true;
                };
                if (!noGhostHand.getValue()) {
                    switchBack.run();
                    switchBack = null;
                }
            }
        } else {
            if (!startTime.passed(startDelay.getValueInt())) return;
            if (!mineAir.getValue() && isAir(breakPos)) {
                return;
            }
            Direction side = BlockUtil.getClickSide(breakPos);
            if (rotate.getValue()) {
                // 26.2: Direction#getVector() → getUnitVec3i()（getUnitVec3() 返回 Vec3）
                Vec3i vec3i = side.getUnitVec3i();
                if (!faceVector(Vec3.atCenterOf(breakPos).add(new Vec3(vec3i.getX() * 0.5, vec3i.getY() * 0.5, vec3i.getZ() * 0.5)))) {
                    return;
                }
            }
            mineTimer.reset();
            animationTime.reset();
            if (swing.getValue()) {
                EntityUtil.swingHand(InteractionHand.MAIN_HAND, swingMode.getValue());
            }
            if (doubleBreak.getValue()) {
                if (secondPos == null || isAir(secondPos)) {
                    double breakTime = (getBreakTime(breakPos, slot, 1));
                    secondAnim.reset();
                    secondAnim.setLength((long) breakTime);
                    secondTimer.reset();
                    secondPos = breakPos;
                }
                doDoubleBreak(side);
            }
            sendSequencedPacket(id -> new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, breakPos, side, id));
            if (rotate.getValue() && !shouldYawStep()) {
                Alien.ROTATION.snapBack();
            }
            startTime.reset();
        }
    }

    private void breakBlock(BlockPos breakPos) {
        // 26.2: Block#onBreak → playerWillDestroy
        mc.level.getBlockState(breakPos).getBlock().playerWillDestroy(mc.level, breakPos, mc.level.getBlockState(breakPos), mc.player);
    }

    void doDoubleBreak(Direction side) {
        sendSequencedPacket(id -> new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, breakPos, side, id));
        sendSequencedPacket(id -> new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, breakPos, side, id));
    }

    boolean placeCrystal() {
        int crystal = findCrystal();
        if (crystal != -1) {
            int oldSlot = mc.player.getInventory().getSelectedSlot();
            doSwap(crystal, crystal);
            BlockUtil.placeCrystal(breakPos.above(), placeRotate.getValue());
            doSwap(oldSlot, crystal);
            placeTimer.reset();
            return !waitPlace.getValue();
        }
        return true;
    }

    @EventListener
    public void onAttackBlock(ClickBlockEvent event) {
        if (nullCheck()) {
            return;
        }
        if (mc.player.isCreative()) {
            return;
        }
        event.cancel();

        BlockPos pos = (event.getPos());
        if (pos.equals(breakPos)) {
            return;
        }
        if (unbreakable(pos)) {
            return;
        }
        if (breakPos != null && preferWeb.getValue() && getBlock(breakPos) == Blocks.COBWEB) {
            return;
        }
        if (breakPos != null && preferHead.getValue() && mc.player.getPose() == Pose.SWIMMING && EntityUtil.getPlayerPos(true).above().equals(breakPos)) {
            return;
        }
        if (BlockUtil.getClickSideStrict(pos) == null) {
            return;
        }
        if (Mth.sqrt((float) mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos))) > range.getValue()) {
            return;
        }
        breakPos = pos;
        breakNumber = 0;
        startPacket = false;
        ghost = false;
        complete = false;
        mineTimer.reset();
        animationTime.reset();
        if (Blink.INSTANCE.isOn() && Blink.INSTANCE.pauseModule.getValue()) return;
        Direction side = BlockUtil.getClickSide(breakPos);
        if (rotate.getValue()) {
            Vec3i vec3i = side.getUnitVec3i();
            if (!faceVector(Vec3.atCenterOf(breakPos).add(new Vec3(vec3i.getX() * 0.5, vec3i.getY() * 0.5, vec3i.getZ() * 0.5)))) {
                return;
            }
        }
        if (!startTime.passed(startDelay.getValueInt())) return;
        if (swing.getValue()) {
            EntityUtil.swingHand(InteractionHand.MAIN_HAND, swingMode.getValue());
        }
        if (doubleBreak.getValue()) {
            if (secondPos == null || isAir(secondPos)) {
                int slot = getTool(breakPos);
                if (slot == -1) {
                    slot = mc.player.getInventory().getSelectedSlot();
                }
                secondFinalTime = (getBreakTime(breakPos, slot, 1));
                secondAnim.reset();
                secondAnim.setLength((long) secondFinalTime);
                secondTimer.reset();
                secondPos = breakPos;
            }
            doDoubleBreak(side);
        }
        int slot = getTool(breakPos);
        if (slot == -1) {
            slot = mc.player.getInventory().getSelectedSlot();
        }
        breakFinalTime = getBreakTime(breakPos, slot);
        sendSequencedPacket(id -> new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, breakPos, side, id));
        if (rotate.getValue() && !shouldYawStep()) {
            Alien.ROTATION.snapBack();
        }
        startTime.reset();
    }

    public void mine(BlockPos pos) {
        if (nullCheck()) {
            return;
        }
        if (mc.player.isCreative()) {
            // 26.2: attackBlock → startDestroyBlock
            mc.gameMode.startDestroyBlock(pos, BlockUtil.getClickSide(pos));
            return;
        }
        if (isOff()) {
            // 26.2: attackBlock → startDestroyBlock
            mc.gameMode.startDestroyBlock(pos, BlockUtil.getClickSide(pos));
            return;
        }
        if (pos.equals(breakPos)) {
            return;
        }
        if (unbreakable(pos)) {
            return;
        }
        if (breakPos != null && preferWeb.getValue() && getBlock(breakPos) == Blocks.COBWEB) {
            return;
        }
        if (breakPos != null && preferHead.getValue() && mc.player.getPose() == Pose.SWIMMING && EntityUtil.getPlayerPos(true).above().equals(breakPos)) {
            return;
        }
        if (BlockUtil.getClickSideStrict(pos) == null) {
            return;
        }
        if (Mth.sqrt((float) mc.player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos))) > range.getValue()) {
            return;
        }
        breakPos = pos;
        breakNumber = 0;
        startPacket = false;
        ghost = false;
        complete = false;
        mineTimer.reset();
        animationTime.reset();
    }

    boolean faceVector(Vec3 directionVec) {
        if (!shouldYawStep()) {
            Alien.ROTATION.lookAt(directionVec);
            return true;
        } else {
            this.sync.reset();
            this.directionVec = directionVec;
            if (Alien.ROTATION.inFov(directionVec, fov.getValueFloat())) {
                return true;
            }
        }
        return !checkFov.getValue();
    }

    /** 26.2: Vec3#offset(Direction, double) 移除，用单位向量手动偏移 */
    private static Vec3 clickVector(BlockPos pos, Direction side) {
        return Vec3.atCenterOf(pos).add(side.getUnitVec3().scale(0.5));
    }

    @Override
    public void onRender3D(PoseStack matrixStack) {
        if (breakPos != null && mc.level.getBlockState(breakPos).isAir()) {
            complete = true;
        }
        if (!mc.player.isCreative()) {
            if (secondPos != null) {
                if (isAir(secondPos)) {
                    secondPos = null;
                    return;
                }
                if (!checkDouble.getValue() || !secondPos.equals(breakPos)) {
                    secondAnim.setLength((long) secondFinalTime);
                    double ease = secondAnim.ease(this.ease.getValue());
                    if (box.getValue()) {
                        Render3DUtil.drawFill(getFillBox(secondPos, ease), doubleColor.getValue());
                    }
                    if (outline.getValue()) {
                        Render3DUtil.drawBox(getOutlineBox(secondPos, ease), doubleOutlineColor.getValue());
                    }
                }
            }
            if (breakPos != null) {
                progress = (double) mineTimer.getMs() / breakFinalTime;
                animationTime.setLength((long) breakFinalTime);
                double ease = animationTime.ease(this.ease.getValue());
                if (unbreakable(breakPos)) {
                    if (box.getValue()) {
                        Render3DUtil.drawFill(new AABB(breakPos), startColor.getValue());
                    }
                    if (outline.getValue()) {
                        Render3DUtil.drawBox(new AABB(breakPos), startOutlineColor.getValue());
                    }
                    return;
                }
                if (box.getValue()) {
                    Render3DUtil.drawFill(getFillBox(breakPos, ease), getColor(animationTime.ease(fadeEase.getValue())));
                }
                if (outline.getValue()) {
                    Render3DUtil.drawBox(getOutlineBox(breakPos, ease), getOutlineColor(animationTime.ease(fadeEase.getValue())));
                }
                if (text.getValue()) {
                    if (isAir(breakPos)) {
                        Render3DUtil.drawText3D("Waiting", Vec3.atCenterOf(breakPos), -1);
                    } else {
                        if ((int) mineTimer.getMs() < breakFinalTime) {
                            Render3DUtil.drawText3D(df.format(progress * 100) + "%", Vec3.atCenterOf(breakPos), -1);
                        } else {
                            Render3DUtil.drawText3D("100.0%", Vec3.atCenterOf(breakPos), -1);
                        }
                    }
                }
            } else {
                progress = 0;
            }
        } else {
            progress = 0;
        }
    }

    private AABB getFillBox(BlockPos pos, double ease) {
        switch (animation.getValue()) {
            case Grow -> {
                ease = (1 - ease) / 2;
                return new AABB(pos).contract(ease, 0, ease).inflate(ease, 0, ease);
            }
            case Down -> {
                return new AABB(pos.getX(), pos.getY() + 1 - ease, pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
            }
            case Up -> {
                return new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + ease, pos.getZ() + 1);
            }
            case Center -> {
                ease = (1 - ease) / 2;
                return new AABB(pos).contract(ease, ease, ease).inflate(ease, ease, ease);
            }
            case Oscillation -> {
                return new AABB(pos).contract(ease, ease, ease).inflate(ease, ease, ease);
            }
        }
        return new AABB(pos);
    }

    private AABB getOutlineBox(BlockPos pos, double ease) {
        ease = Math.min(ease + expandLine.getValue(), 1);
        switch (animation.getValue()) {
            case Grow -> {
                ease = (1 - ease) / 2;
                return new AABB(pos).contract(ease, 0, ease).inflate(ease, 0, ease);
            }
            case Down -> {
                return new AABB(pos.getX(), pos.getY() + 1 - ease, pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);
            }
            case Up -> {
                return new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + ease, pos.getZ() + 1);
            }
            case Center -> {
                ease = (1 - ease) / 2;
                return new AABB(pos).contract(ease, ease, ease).inflate(ease, ease, ease);
            }
            case Oscillation -> {
                return new AABB(pos).contract(ease, ease, ease).inflate(ease, ease, ease);
            }
        }
        return new AABB(pos);
    }

    @EventListener(priority = EventPriority.LOWEST)
    public void onPacketSend(PacketEvent.Send event) {
        if (nullCheck() || mc.player.isCreative()) {
            return;
        }
        if (event.getPacket() instanceof ServerboundMovePlayerPacket) {
            if (bypassGround.getValue() && !mc.player.isFallFlying() && breakPos != null && !isAir(breakPos) && bypassTime.getValue() > 0 && Mth.sqrt((float) Vec3.atCenterOf(breakPos).distanceToSqr(mc.player.getEyePosition())) <= range.getValueFloat() + 2) {
                double breakTime = (breakFinalTime - bypassTime.getValue());
                if (breakTime <= 0 || mineTimer.passed((long) breakTime)) {
                    sendGroundPacket = true;
                    ((IPlayerMoveC2SPacket) event.getPacket()).setOnGround(true);
                }
            } else {
                sendGroundPacket = false;
            }
        } else if (event.getPacket() instanceof ServerboundSetCarriedItemPacket packet) {
            // 26.2: getSelectedSlot() → getSlot()
            if (packet.getSlot() != lastSlot) {
                lastSlot = packet.getSlot();
                if (switchReset.getValue()) {
                    startPacket = false;
                    ghost = false;
                    complete = false;
                    mineTimer.reset();
                    animationTime.reset();
                }
            }
        } else if (event.getPacket() instanceof ServerboundPlayerActionPacket packet) {
            // 26.2: position() → getPos()
            if (packet.getAction() == ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) {
                if (breakPos == null || !packet.getPos().equals(breakPos)) {
                    return;
                }
                if (grimDisabler.getValue())
                    mc.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, packet.getPos(), packet.getDirection()));
                startPacket = true;
            } else if (packet.getAction() == ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK) {
                if (breakPos == null || !packet.getPos().equals(breakPos)) {
                    return;
                }
                if (!instant.getValue()) {
                    startPacket = false;
                    ghost = false;
                    complete = false;
                }
            }
        }
    }

    boolean canPlaceCrystal(BlockPos pos) {
        BlockPos obsPos = pos.below();
        BlockPos boost = obsPos.above();
        return (getBlock(obsPos) == Blocks.BEDROCK || getBlock(obsPos) == Blocks.OBSIDIAN)
                && BlockUtil.getClickSideStrict(obsPos) != null
                && noEntity(boost)
                && noEntity(boost.above())
                && (!ClientSetting.INSTANCE.lowVersion.getValue() || mc.level.getBlockState(boost.above()).isAir());
    }

    boolean noEntity(BlockPos pos) {
        for (Entity entity : BlockUtil.getEntities(new AABB(pos))) {
            if (entity instanceof ItemEntity || entity instanceof ArmorStand && AntiCheat.INSTANCE.ignoreArmorStand.getValue())
                continue;
            return false;
        }
        return true;
    }

    void doSwap(int slot, int inv) {
        if (!inventory.getValue()) {
            InventoryUtil.switchToSlot(slot);
        } else {
            InventoryUtil.inventorySwap(inv, mc.player.getInventory().getSelectedSlot());
        }
    }

    int findCrystal() {
        if (inventory.getValue()) {
            return InventoryUtil.findItemInventorySlot(Items.END_CRYSTAL);
        } else {
            return InventoryUtil.findItem(Items.END_CRYSTAL);
        }
    }

    int findBlock(Block block) {
        if (inventory.getValue()) {
            return InventoryUtil.findBlockInventorySlot(block);
        } else {
            return InventoryUtil.findBlock(block);
        }
    }

    boolean shouldCrystal() {
        return crystal.getValue() && (!onlyHeadBomber.getValue() || obsidian.isPressed()) || CevBreaker.INSTANCE.isOn();
    }

    public static double getBreakTime(BlockPos pos) {
        int slot = INSTANCE.getTool(pos);
        if (slot == -1) {
            slot = mc.player.getInventory().getSelectedSlot();
        }
        return INSTANCE.getBreakTime(pos, slot);
    }

    double getBreakTime(BlockPos pos, int slot) {
        return getBreakTime(pos, slot, damage.getValue());
    }

    double getBreakTime(BlockPos pos, int slot, double damage) {
        return (1 / getBlockStrength(pos, mc.player.getInventory().getItem(slot)) / 20 * 1000 * damage);
    }

    float getBlockStrength(BlockPos position, ItemStack itemStack) {
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

    float getDigSpeed(BlockState state, ItemStack itemStack) {
        float digSpeed = getDestroySpeed(state, itemStack);
        if (digSpeed > 1) {
            int efficiencyModifier = EnchantmentHelper.getItemEnchantmentLevel(mc.level.registryAccess().getOrThrow(Enchantments.EFFICIENCY), itemStack);
            if (efficiencyModifier > 0 && !itemStack.isEmpty()) {
                digSpeed += (float) (StrictMath.pow(efficiencyModifier, 2) + 1);
            }
        }
        if (mc.player.hasEffect(MobEffects.HASTE)) {
            digSpeed *= 1 + (mc.player.getEffect(MobEffects.HASTE).getAmplifier() + 1) * 0.2F;
        }
        if (mc.player.hasEffect(MobEffects.MINING_FATIGUE)) {
            float fatigueScale;
            switch (mc.player.getEffect(MobEffects.MINING_FATIGUE).getAmplifier()) {
                case 0 -> fatigueScale = 0.3F;
                case 1 -> fatigueScale = 0.09F;
                case 2 -> fatigueScale = 0.0027F;
                default -> fatigueScale = 8.1E-4F;
            }
            digSpeed *= fatigueScale;
        }
        // 26.2: isSubmergedInWater → isUnderWater；PLAYER_SUBMERGED_MINING_SPEED 属性 → SUBMERGED_MINING_SPEED
        if (mc.player.isUnderWater()) {
            digSpeed *= (float) mc.player.getAttributeValue(Attributes.SUBMERGED_MINING_SPEED);
        }
        boolean inWeb = checkWeb.getValue() && Alien.PLAYER.isInWeb(mc.player) && mc.level.getBlockState(breakPos).getBlock() == Blocks.COBWEB;
        if ((!mc.player.onGround() || inWeb) && INSTANCE.checkGround.getValue() && (!smart.getValue() || Criticals.INSTANCE.mode.is(Criticals.Mode.Ground) && Criticals.INSTANCE.isOn() || mc.player.isFallFlying() || inWeb)) {
            digSpeed /= 5;
        }
        return (digSpeed < 0 ? 0 : digSpeed);
    }

    float getDestroySpeed(BlockState state, ItemStack itemStack) {
        float destroySpeed = 1;
        if (itemStack != null && !itemStack.isEmpty()) {
            destroySpeed *= itemStack.getDestroySpeed(state);
        }
        return destroySpeed;
    }

    Color getColor(double quad) {
        int sR = startColor.getValue().getRed();
        int sG = startColor.getValue().getGreen();
        int sB = startColor.getValue().getBlue();
        int sA = startColor.getValue().getAlpha();

        int eR = endColor.getValue().getRed();
        int eG = endColor.getValue().getGreen();
        int eB = endColor.getValue().getBlue();
        int eA = endColor.getValue().getAlpha();
        return new Color((int) (sR + (eR - sR) * quad),
                (int) (sG + (eG - sG) * quad),
                (int) (sB + (eB - sB) * quad),
                (int) (sA + (eA - sA) * quad));
    }

    Color getOutlineColor(double quad) {
        int sR = startOutlineColor.getValue().getRed();
        int sG = startOutlineColor.getValue().getGreen();
        int sB = startOutlineColor.getValue().getBlue();
        int sA = startOutlineColor.getValue().getAlpha();

        int eR = endOutlineColor.getValue().getRed();
        int eG = endOutlineColor.getValue().getGreen();
        int eB = endOutlineColor.getValue().getBlue();
        int eA = endOutlineColor.getValue().getAlpha();
        return new Color((int) (sR + (eR - sR) * quad),
                (int) (sG + (eG - sG) * quad),
                (int) (sB + (eB - sB) * quad),
                (int) (sA + (eA - sA) * quad));
    }

    int getTool(BlockPos pos) {
        if (hotBar.getValue()) {
            int index = -1;
            float CurrentFastest = 1.0f;
            for (int i = 0; i < 9; ++i) {
                ItemStack stack = mc.player.getInventory().getItem(i);
                if (stack != ItemStack.EMPTY) {
                    float digSpeed = EnchantmentHelper.getItemEnchantmentLevel(mc.level.registryAccess().getOrThrow(Enchantments.EFFICIENCY), stack);
                    float destroySpeed = stack.getDestroySpeed(mc.level.getBlockState(pos));
                    if (digSpeed + destroySpeed > CurrentFastest) {
                        CurrentFastest = digSpeed + destroySpeed;
                        index = i;
                    }
                }
            }
            return index;
        } else {
            AtomicInteger slot = new AtomicInteger();
            slot.set(-1);
            float CurrentFastest = 1.0f;
            for (Map.Entry<Integer, ItemStack> entry : InventoryUtil.getInventoryAndHotbarSlots().entrySet()) {
                // 26.2: AirBlockItem 已移除，原来的 instanceof 判断恒为真，改为非空判断
                if (!entry.getValue().isEmpty()) {
                    float digSpeed = EnchantmentHelper.getItemEnchantmentLevel(mc.level.registryAccess().getOrThrow(Enchantments.EFFICIENCY), entry.getValue());
                    float destroySpeed = entry.getValue().getDestroySpeed(mc.level.getBlockState(pos));
                    if (digSpeed + destroySpeed > CurrentFastest) {
                        CurrentFastest = digSpeed + destroySpeed;
                        slot.set(entry.getKey());
                    }
                }
            }
            return slot.get();
        }
    }

    private boolean shouldYawStep() {
        if (!whenElytra.getValue() && (mc.player.isFallFlying() || ElytraFly.INSTANCE.isOn() && ElytraFly.INSTANCE.isFallFlying()))
            return false;
        return yawStep.getValue() && !Velocity.INSTANCE.noRotation();
    }

    boolean isAir(BlockPos breakPos) {
        // 26.2: Level 无 isAir，改用 BlockState#isAir
        return mc.level.getBlockState(breakPos).isAir() || getBlock(breakPos) == Blocks.FIRE && BlockUtil.hasCrystal(breakPos);
    }

    public static boolean unbreakable(BlockPos blockPos) {
        BlockState state = mc.level.getBlockState(blockPos);
        Block block = state.getBlock();
        // 26.2: Block#getHardness() 移除，用 BlockState#getDestroySpeed
        float hardness = state.getDestroySpeed(mc.level, blockPos);
        return !(block instanceof AirBlock) && (hardness == -1 || hardness == 100);
    }

    private enum Animation {
        Center,
        Grow,
        Up,
        Down,
        Oscillation,
        None
    }

    public enum Page {
        General,
        Check,
        Place,
        Rotation,
        Render,
    }
}