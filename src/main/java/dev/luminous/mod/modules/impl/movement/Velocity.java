package dev.luminous.mod.modules.impl.movement;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.EntityVelocityUpdateEvent;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.api.events.impl.TickEvent;
import dev.luminous.api.events.impl.UpdateRotateEvent;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.player.MovementUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.exploit.Blink;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import dev.luminous.api.utils.world.BlockUtil;
import net.minecraft.world.level.block.Blocks;
// 26.2: FishingBobberEntity → FishingHook
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.Direction;

public class Velocity extends Module {
    public static Velocity INSTANCE;
    private final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", Mode.Plain));
    private final SliderSetting lagPause = add(new SliderSetting("LagPause", 50, 0, 500, () -> mode.is(Mode.Grim) || mode.is(Mode.Wall)));
    public final BooleanSetting ignorePearlLag = add(new BooleanSetting("IgnorePearlLag", true, () -> mode.is(Mode.Grim) || mode.is(Mode.Wall)).setParent());
    private final SliderSetting phaseTime = add(new SliderSetting("PhaseTime", 250, 0, 1000, () -> (mode.is(Mode.Grim) || mode.is(Mode.Wall)) && ignorePearlLag.isOpen()));
    public final BooleanSetting noRotation = add(new BooleanSetting("NoRotation", false, () -> mode.is(Mode.Grim) || mode.is(Mode.Wall)));
    public final BooleanSetting flagInWall = add(new BooleanSetting("FlagInWall", false, () -> mode.is(Mode.Grim) || mode.is(Mode.Wall)).setParent());
    public final BooleanSetting whenPushOutOfBlocks = add(new BooleanSetting("WhilePushOut", false, () -> (mode.is(Mode.Grim) || mode.is(Mode.Wall)) && flagInWall.isOpen()));
    public final BooleanSetting staticSetting = add(new BooleanSetting("Static", false, () -> mode.is(Mode.Grim)));
    public final BooleanSetting cancelAll = add(new BooleanSetting("CancelAll", false, () -> !mode.is(Mode.None)));
    private final SliderSetting horizontal = add(new SliderSetting("Horizontal", 0, 0, 100, 1, () -> !mode.is(Mode.None) && !cancelAll.getValue()));
    private final SliderSetting vertical = add(new SliderSetting("Vertical", 0, 0, 100, 1, () -> !mode.is(Mode.None) && !cancelAll.getValue()));

    public final BooleanSetting whileLiquid = add(new BooleanSetting("WhileLiquid", false));
    public final BooleanSetting whileElytra = add(new BooleanSetting("FallFlying", false));
    public final BooleanSetting noClimb = add(new BooleanSetting("NoClimb", false));
    public final BooleanSetting waterPush = add(new BooleanSetting("NoWaterPush", false));
    public final BooleanSetting entityPush = add(new BooleanSetting("NoEntityPush", true));
    public final BooleanSetting blockPush = add(new BooleanSetting("NoBlockPush", true));
    public final BooleanSetting fishBob = add(new BooleanSetting("NoFishBob", true));

    public final Timer pearlTimer = new Timer();
    private final Timer lagBackTimer = new Timer();
    private boolean flag;

    public Velocity() {
        super("Velocity", Category.Movement);
        setChinese("反击退");
        INSTANCE = this;
    }

    @Override
    public String getInfo() {
        if (mode.is(Mode.None)) {
            return null;
        }
        return mode.getValue().name() + ", " + (cancelAll.getValue() ? "Cancel" : (horizontal.getValueInt() + "%, " + vertical.getValueInt() + "%"));
    }

    @EventListener
    public void onRotate(UpdateRotateEvent event) {
        if (noRotation()) {
            event.setRotation(Alien.ROTATION.rotationYaw, 89);
        }
    }

    public boolean noRotation() {
        return isOn() && (mode.is(Mode.Grim) || mode.is(Mode.Wall)) && EntityUtil.isInsideBlock() && noRotation.getValue();
    }

    @EventListener
    public void onVelocity(EntityVelocityUpdateEvent event) {
        if (nullCheck()) return;
        if (event.getEntity() != mc.player)
            return;
        if (mode.is(Mode.None))
            return;
        // 26.2: isInFluid()（Fabric 扩展需 TagKey）→ 水或岩浆
        if ((mc.player.isInWater() || mc.player.isInLava()) && !whileLiquid.getValue())
            return;
        if (mc.player.isFallFlying() && !whileElytra.getValue())
            return;
        switch (mode.getValue()) {
            case Grim -> {
                if (!lagBackTimer.passedMs(lagPause.getValue())) {
                    return;
                }
                if (EntityUtil.isInsideBlock() || getPos() != null || staticSetting.getValue() && MovementUtil.isStatic()) {
                    if (event.getX() != 0 || event.getZ() != 0)
                        flag = true;
                } else {
                    return;
                }
            }
            case Wall -> {
                if (!lagBackTimer.passedMs(lagPause.getValue())) {
                    return;
                }
                if (EntityUtil.isInsideBlock()) {
                    if (event.getX() != 0 || event.getZ() != 0)
                        flag = true;
                } else {
                    return;
                }
            }
            case Plain -> {
            }
        }
        if (cancelAll.getValue()) {
            event.cancel();
        } else {
            double h = horizontal.getValueInt() / 100d;
            double v = vertical.getValueInt() / 100d;

            event.setX(event.getX() * h);
            event.setZ(event.getZ() * h);

            event.setY(event.getY() * v);
        }
    }

    @EventListener
    public void onReceivePacket(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundPlayerPositionPacket && (!ignorePearlLag.getValue() || pearlTimer.passed(phaseTime.getValueInt()))) {
            lagBackTimer.reset();
        }
        if (nullCheck()) return;
        if ((mc.player.isInWater() || mc.player.isInLava()) && !whileLiquid.getValue())
            return;

        if (fishBob.getValue()) {
            // 26.2: getStatus → getEventId；getHookedEntity → getHookedIn
            if (event.getPacket() instanceof ClientboundEntityEventPacket packet && packet.getEventId() == 31 && packet.getEntity(mc.level) instanceof FishingHook fishHook) {
                if (fishHook.getHookedIn() == mc.player) {
                    event.setCancelled(true);
                }
            }
        }
    }

    static boolean pushOutOfBlocks = false;

    @EventListener
    public void onUpdate(TickEvent event) {
        if (nullCheck()) return;
        if (event.isPost() || (mc.player.isInWater() || mc.player.isInLava()) && !whileLiquid.getValue())
            return;
        if (flagInWall.getValue()) {
            pushOutOfBlocks = false;
            // 26.2: getWidth → getBbWidth
            pushOutOfBlocks(mc.player.getX() - (double) mc.player.getBbWidth() * 0.35, mc.player.getZ() + (double) mc.player.getBbWidth() * 0.35);
            pushOutOfBlocks(mc.player.getX() - (double) mc.player.getBbWidth() * 0.35, mc.player.getZ() - (double) mc.player.getBbWidth() * 0.35);
            pushOutOfBlocks(mc.player.getX() + (double) mc.player.getBbWidth() * 0.35, mc.player.getZ() - (double) mc.player.getBbWidth() * 0.35);
            pushOutOfBlocks(mc.player.getX() + (double) mc.player.getBbWidth() * 0.35, mc.player.getZ() + (double) mc.player.getBbWidth() * 0.35);
        }

        if (Blink.INSTANCE.isOn() && Blink.INSTANCE.pauseModule.getValue()) return;
        if (flag) {
            if (lagBackTimer.passedMs(lagPause.getValue()) && (flagInWall.getValue() && (!pushOutOfBlocks || whenPushOutOfBlocks.getValue()) || !EntityUtil.isInsideBlock())) {
                // 26.2: PlayerMoveC2SPacket.Full → ServerboundMovePlayerPacket.PosRot(x,y,z,yRot,xRot,onGround,horizontalCollision)
                mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(mc.player.getX(), mc.player.getY(), mc.player.getZ(), Alien.ROTATION.rotationYaw, Alien.ROTATION.rotationPitch, mc.player.onGround(), mc.player.horizontalCollision));
                BlockPos pos = getPos();
                if (pos != null) {
                    // 26.2: getHorizontalFacing → getDirection
                    mc.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                            pos, mc.player.getDirection().getOpposite()));
                }
            }
            flag = false;
        }
    }

    public BlockPos getPos() {
        if (mc.level.getBlockState(mc.player.blockPosition().below()).getBlock() == Blocks.OBSIDIAN) {
            return mc.player.blockPosition().below();
        }
        return null;
    }

    private static void pushOutOfBlocks(double x, double z) {
        BlockPos blockPos = BlockPos.containing(x, mc.player.getY(), z);
        if (wouldCollideAt(blockPos)) {
            double d = x - (double) blockPos.getX();
            double e = z - (double) blockPos.getZ();
            Direction direction = null;
            double f = Double.MAX_VALUE;
            Direction[] directions = new Direction[]{Direction.WEST, Direction.EAST, Direction.NORTH, Direction.SOUTH};

            for (Direction direction2 : directions) {
                double g = direction2.getAxis().choose(d, 0.0, e);
                // 26.2: Direction.getDirection() → getAxisDirection()
                double h = direction2.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 1.0 - g : g;
                if (h < f && !wouldCollideAt(blockPos.relative(direction2))) {
                    f = h;
                    direction = direction2;
                }
            }

            if (direction != null) {
                pushOutOfBlocks = true;
            }
        }
    }

    private static boolean wouldCollideAt(BlockPos pos) {
        AABB box = mc.player.getBoundingBox();
        // 26.2: AABB.contract(单参数) 不存在 → deflate；level().canCollide 已移除，用 BlockUtil.canCollide
        AABB box2 = (new AABB(pos.getX(), box.minY, pos.getZ(), (double) pos.getX() + (double) 1.0F, box.maxY, (double) pos.getZ() + (double) 1.0F)).deflate(1.0E-7);
        return BlockUtil.canCollide(mc.player, box2);
    }

    public enum Mode {
        Plain, Grim, Wall, None
    }
}