package dev.luminous.mod.modules.impl.movement;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.MoveEvent;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.api.events.impl.TimerEvent;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.player.MovementUtil;
import dev.luminous.api.utils.world.BlockPosX;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;

import java.util.HashMap;
import java.util.Map;

public class FastFall extends Module {

    private final EnumSetting<Mode> mode =
            add(new EnumSetting<>("Mode", Mode.Fast));
    private final BooleanSetting noLag =
            add(new BooleanSetting("NoLag", true, () -> mode.getValue() == Mode.Fast));
    private final BooleanSetting useTimerSetting =
            add(new BooleanSetting("UseTimer", false));
    private final SliderSetting timer =
            add(new SliderSetting("Timer", 2.5, 1, 8, 0.1, useTimerSetting::getValue));
    private final BooleanSetting anchor =
            add(new BooleanSetting("Anchor", true));
    private final SliderSetting height =
            add(new SliderSetting("Height", 10, 1, 20, 0.5));

    private final Timer lagTimer = new Timer();
    boolean onGround = false;
    private boolean useTimer;

    public FastFall() {
        super("FastFall", "Miyagi son simulator", Category.Movement);
        setChinese("快速坠落");
    }

    @Override
    public void onDisable() {
        useTimer = false;
    }

    @Override
    public String getInfo() {
        return mode.getValue().name();
    }

    @EventListener(priority = -100)
    public void onMove(MoveEvent event) {
        if (nullCheck()) return;
        if (mc.player.onGround() && anchor.getValue()) {
            if (traceDown() != 0 && traceDown() <= height.getValue() && trace()) {
                event.setX(event.getX() * 0.05);
                event.setZ(event.getZ() * 0.05);
            }
        }
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if ((height.getValue() > 0 && (traceDown() > height.getValue()))
                // 26.2: isInsideWall → isInWall、isSubmergedInWater → isUnderWater、isHoldingOntoLadder → onClimbable
                || mc.player.isInWall()
                || mc.player.isUnderWater()
                || mc.player.isInLava()
                || mc.player.onClimbable()
                || !lagTimer.passed(1000)
                || mc.player.isFallFlying()
                || Fly.INSTANCE.isOn()
                || nullCheck()) {
            return;
        }

        if (Alien.PLAYER.isInWeb(mc.player)) return;

        if (mc.player.onGround()) {

            if (mode.getValue() == Mode.Fast) {
                MovementUtil.setMotionY(MovementUtil.getMotionY() - (noLag.getValue() ? 0.62f : 1));
            }
        }

        if (useTimerSetting.getValue()) {
            if (!mc.player.onGround()) {
                if (onGround) {
                    useTimer = true;
                }
                if (MovementUtil.getMotionY() >= 0) {
                    useTimer = false;
                }
                onGround = false;
            } else {
                useTimer = false;
                MovementUtil.setMotionY(-0.08);
                onGround = true;
            }
        } else {
            useTimer = false;
        }
    }

    @EventListener
    public void onTimer(TimerEvent event) {
        if (nullCheck()) return;
        if (!mc.player.onGround() && useTimer) {
            event.set(timer.getValueFloat());
        }
    }

    @EventListener
    public void onPacket(PacketEvent.Receive event) {
        if (!nullCheck()) {
            if (event.getPacket() instanceof ClientboundPlayerPositionPacket) {
                lagTimer.reset();
            }
        }
    }

    private int traceDown() {
        int retval = 0;

        int y = (int) Math.round(mc.player.getY()) - 1;

        for (int tracey = y; tracey >= 0; tracey--) {

            // 26.2: Level.raycast → Level.clip；ShapeType → ClipContext.Block，FluidHandling → ClipContext.Fluid
            HitResult trace = mc.level.clip(new ClipContext(
                    mc.player.position(),
                    new Vec3(mc.player.getX(), tracey, mc.player.getZ()),
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    mc.player
            ));

            if (trace != null && trace.getType() == HitResult.Type.BLOCK) return retval;

            retval++;
        }
        return retval;
    }

    private boolean trace() {
        AABB bbox = mc.player.getBoundingBox();
        Vec3 basepos = bbox.getCenter();

        double minX = bbox.minX;
        double minZ = bbox.minZ;
        double maxX = bbox.maxX;
        double maxZ = bbox.maxZ;

        Map<Vec3, Vec3> positions = new HashMap<>();

        positions.put(
                basepos,
                new Vec3(basepos.x, basepos.y - 1, basepos.z));

        positions.put(
                new Vec3(minX, basepos.y, minZ),
                new Vec3(minX, basepos.y - 1, minZ));

        positions.put(
                new Vec3(maxX, basepos.y, minZ),
                new Vec3(maxX, basepos.y - 1, minZ));

        positions.put(
                new Vec3(minX, basepos.y, maxZ),
                new Vec3(minX, basepos.y - 1, maxZ));

        positions.put(
                new Vec3(maxX, basepos.y, maxZ),
                new Vec3(maxX, basepos.y - 1, maxZ));

        for (Vec3 key : positions.keySet()) {
            ClipContext context = new ClipContext(
                    key,
                    positions.get(key),
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    mc.player
            );

            BlockHitResult result = mc.level.clip(context);

            if (result != null && result.getType() == HitResult.Type.BLOCK) {
                return false;
            }
        }

        BlockState state = mc.level.getBlockState(new BlockPosX(mc.player.getX(), mc.player.getY() - 1, mc.player.getZ()));

        return state.isAir();
    }

    private enum Mode {
        Fast,
        None
    }
}