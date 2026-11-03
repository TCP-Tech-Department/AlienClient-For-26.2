package dev.luminous.core.impl;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.eventbus.EventPriority;
import dev.luminous.api.events.impl.*;
import dev.luminous.api.utils.Wrapper;
import dev.luminous.api.utils.math.MathUtil;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.path.BaritoneUtil;
import dev.luminous.asm.accessors.IClientInput;
import dev.luminous.asm.accessors.IClientPlayerEntity;
import dev.luminous.mod.modules.impl.client.AntiCheat;
import dev.luminous.mod.modules.impl.client.ClientSetting;
import dev.luminous.mod.modules.impl.movement.HoleSnap;
import dev.luminous.mod.modules.impl.player.Freecam;
import dev.luminous.mod.modules.settings.enums.SnapBack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public class RotationManager implements Wrapper {
    public static final Timer ROTATE_TIMER = new Timer();
    public static Vec3 directionVec = null;
    public static boolean snapBack = false;
    private static float renderPitch;
    private static float renderYawOffset;
    private static float prevRenderPitch;
    private static float prevRenderYawOffset;
    private static float prevRotationYawHead;
    private static float rotationYawHead;
    public float nextYaw;
    public float nextPitch;
    public float rotationYaw;
    public float rotationPitch;
    public float lastYaw;
    public float lastPitch;
    public Vec3 crossHairUpdatePos;
    private int ticksExisted;

    public RotationManager() {
        Alien.EVENT_BUS.subscribe(this);
    }

    @EventListener
    public void onInteract(InteractItemEvent event) {
        if (AntiCheat.INSTANCE.interactRotation.getValue() && mc.player != null) {
            if (event.isPre()) {
                snapAt(mc.player.getYRot(), mc.player.getXRot());
            } else {
                snapBack();
            }
        }
    }

    @EventListener
    public void onInteract(InteractBlockEvent event) {
        if (AntiCheat.INSTANCE.interactRotation.getValue() && mc.player != null) {
            if (event.isPre()) {
                snapAt(mc.player.getYRot(), mc.player.getXRot());
            } else {
                snapBack();
            }
        }
    }

    @EventListener
    public void doAttack(DoAttackEvent event) {
        if (AntiCheat.INSTANCE.interactRotation.getValue() && mc.player != null) {
            if (event.isPre()) {
                snapAt(mc.player.getYRot(), mc.player.getXRot());
            } else {
                snapBack();
            }
        }
    }

    public void snapBack() {
        if (AntiCheat.INSTANCE.snapBackEnum.is(SnapBack.Force)) {
            mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(mc.player.getX(), mc.player.getY(), mc.player.getZ(), rotationYaw, rotationPitch, mc.player.onGround(), false));
        } else if (AntiCheat.INSTANCE.snapBackEnum.is(SnapBack.Tick))
            snapBack = true;
    }

    public void lookAt(Vec3 directionVec) {
        rotationTo(directionVec);
        snapAt(directionVec);
    }

    public void lookAt(BlockPos pos, Direction side) {
        final Vec3 hitVec = Vec3.atCenterOf(pos).add(new Vec3(side.getUnitVec3().x * 0.5, side.getUnitVec3().y * 0.5, side.getUnitVec3().z * 0.5));
        lookAt(hitVec);
    }

    public void snapAt(float yaw, float pitch) {
        setRenderRotation(yaw, pitch, true);
        if (AntiCheat.INSTANCE.grimRotation.getValue()) {
            mc.getConnection().send(new ServerboundMovePlayerPacket.PosRot(mc.player.getX(), mc.player.getY(), mc.player.getZ(), yaw, pitch, mc.player.onGround(), false));
        } else {
            mc.getConnection().send(new ServerboundMovePlayerPacket.Rot(yaw, pitch, mc.player.onGround(), false));
        }
    }

    public void snapAt(Vec3 directionVec) {
        float[] angle = getRotation(directionVec);
        snapAt(angle[0], angle[1]);
    }

    public void rotationTo(Vec3 vec3d) {
        ROTATE_TIMER.reset();
        directionVec = vec3d;
    }

    public boolean inFov(Vec3 directionVec, float fov) {
        float[] angle = getRotation(crossHairUpdatePos != null ? crossHairUpdatePos : new Vec3(mc.player.getX(), mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()), mc.player.getZ()), directionVec);
        return inFov(angle[0], angle[1], fov);
    }

    public boolean inFov(float yaw, float pitch, float fov) {
        float yawDifferent = Math.abs(Mth.wrapDegrees(yaw - rotationYaw));
        float pitchDifferent = Math.abs(pitch - rotationPitch);
        return yawDifferent * yawDifferent + pitchDifferent * pitchDifferent <= fov * fov;
    }

    @EventListener
    public void onTickMovement(TickMovementEvent event) {
        if (mc.player == null) return;
        crossHairUpdatePos = new Vec3(mc.player.getX(), mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()), mc.player.getZ());
    }

    @EventListener
    public void update(SendMovementPacketsEvent event) {
        if (AntiCheat.INSTANCE.movementSync() && !BaritoneUtil.isActive()) {
            event.setYaw(nextYaw);
            event.setPitch(nextPitch);
        } else {
            UpdateRotateEvent updateRotateEvent = UpdateRotateEvent.get(event.getYaw(), event.getPitch());
            Alien.EVENT_BUS.post(updateRotateEvent);
            event.setYaw(updateRotateEvent.getYaw());
            event.setPitch(updateRotateEvent.getPitch());
        }
    }

    @EventListener(priority = 999)
    public void update(TickMovementEvent event) {
        if (mc.player == null) return;
        if (AntiCheat.INSTANCE.movementSync() && !BaritoneUtil.isActive()) {
            UpdateRotateEvent updateRotateEvent = UpdateRotateEvent.get(mc.player.getYRot(), mc.player.getXRot());
            Alien.EVENT_BUS.post(updateRotateEvent);
            nextYaw = updateRotateEvent.getYaw();
            nextPitch = updateRotateEvent.getPitch();
            fixYaw = nextYaw;
            fixPitch = nextPitch;
        }
    }

    @EventListener(priority = EventPriority.LOWEST)
    public void onLastRotation(UpdateRotateEvent event) {
        RotationEvent rotationEvent = RotationEvent.get();
        Alien.EVENT_BUS.post(rotationEvent);
        if (rotationEvent.getRotation()) {
            float[] newAngle = injectStep(new float[]{rotationEvent.getYaw(), rotationEvent.getPitch()}, rotationEvent.getSpeed());
            event.setYaw(newAngle[0]);
            event.setPitch(newAngle[1]);
        } else if (rotationEvent.getTarget() != null) {
            float[] newAngle = injectStep(rotationEvent.getTarget(), rotationEvent.getSpeed());
            event.setYaw(newAngle[0]);
            event.setPitch(newAngle[1]);
        } else if (!event.isModified() && AntiCheat.INSTANCE.look.getValue()) {
            if (directionVec != null && !ROTATE_TIMER.passed((long) (AntiCheat.INSTANCE.rotateTime.getValue() * 1000))) {
                float[] newAngle = injectStep(directionVec, AntiCheat.INSTANCE.steps.getValueFloat());
                event.setYaw(newAngle[0]);
                event.setPitch(newAngle[1]);
            }
        }
    }

    public static float fixYaw;
    public static float fixPitch;

    private float prevYaw;
    private float prevPitch;

    @EventListener
    public void travel(TravelEvent e) {
        if (!AntiCheat.INSTANCE.movementSync()) return;
        if (BaritoneUtil.isActive()) return;
        if (mc.player.isPassenger())
            return;

        if (e.isPre()) {
            prevYaw = mc.player.getYRot();
            prevPitch = mc.player.getXRot();
            mc.player.setYRot(fixYaw);
            mc.player.setXRot(fixPitch);
        } else {
            mc.player.setYRot(prevYaw);
            mc.player.setXRot(prevPitch);
        }
    }

    @EventListener
    public void onJump(JumpEvent e) {
        if (!AntiCheat.INSTANCE.movementSync()) return;
        if (BaritoneUtil.isActive()) return;
        if (mc.player.isPassenger())
            return;

        if (e.isPre()) {
            prevYaw = mc.player.getYRot();
            prevPitch = mc.player.getXRot();
            mc.player.setYRot(fixYaw);
            mc.player.setXRot(fixPitch);
        } else {
            mc.player.setYRot(prevYaw);
            mc.player.setXRot(prevPitch);
        }
    }

    @EventListener
    public void onFirework(FireworkShooterRotationEvent event) {
        if (!AntiCheat.INSTANCE.movementSync()) return;
        if (BaritoneUtil.isActive()) return;
        if (event.shooter == mc.player) {
            event.yaw = fixYaw;
            event.pitch = fixPitch;
            event.cancel();
        }
    }

    @EventListener(priority = -999)
    public void onKeyInput(KeyboardInputEvent e) {
        if (!AntiCheat.INSTANCE.movementSync()) return;
        if (BaritoneUtil.isActive()) return;
        if (HoleSnap.INSTANCE.isOn()) return;
        if (mc.player.isPassenger() || Freecam.INSTANCE.isOn())
            return;

        float mF = mc.player.input.getMoveVector().y;
        float mS = mc.player.input.getMoveVector().x;
        float delta = (mc.player.getYRot() - fixYaw) * Mth.DEG_TO_RAD;
        float cos = Mth.cos(delta);
        float sin = Mth.sin(delta);
        float newSide = Math.round(mS * cos - mF * sin);
        float newForward = Math.round(mF * cos + mS * sin);
        ((IClientInput) mc.player.input).setMoveVector(new Vec2(newSide, newForward));
    }

    public float[] injectStep(Vec3 vec, float steps) {
        float currentYaw = AntiCheat.INSTANCE.serverSide.getValue() ? getLastYaw() : rotationYaw;
        float currentPitch = AntiCheat.INSTANCE.serverSide.getValue() ? getLastPitch() : rotationPitch;

        float yawDelta = Mth.wrapDegrees((float) Mth.wrapDegrees(Math.toDegrees(Math.atan2(vec.z - mc.player.getZ(), (vec.x - mc.player.getX()))) - 90) - currentYaw);
        float pitchDelta = ((float) (-Math.toDegrees(Math.atan2(vec.y - (mc.player.position().y + mc.player.getEyeHeight(mc.player.getPose())), Math.sqrt(Math.pow((vec.x - mc.player.getX()), 2) + Math.pow(vec.z - mc.player.getZ(), 2))))) - currentPitch);

        if (AntiCheat.INSTANCE.random.getValue()) {
            float angleToRad = (float) Math.toRadians(27 * (mc.player.tickCount % 30));
            yawDelta = (float) (yawDelta + Math.sin(angleToRad) * 3) + MathUtil.random(-1, 1);
            pitchDelta = pitchDelta + MathUtil.random(-0.6f, 0.6f);
        }

        if (yawDelta > 180)
            yawDelta = yawDelta - 180;

        float yawStepVal = 180 * steps;

        float clampedYawDelta = Mth.clamp(Mth.abs(yawDelta), -yawStepVal, yawStepVal);
        float clampedPitchDelta = Mth.clamp(pitchDelta, -45, 45);

        float newYaw = currentYaw + (yawDelta > 0 ? clampedYawDelta : -clampedYawDelta);
        float newPitch = Mth.clamp(currentPitch + clampedPitchDelta, -90.0F, 90.0F);

        return new float[]{newYaw, newPitch};
    }

    public float[] injectStep(float[] angle, float steps) {
        float currentYaw = AntiCheat.INSTANCE.serverSide.getValue() ? getLastYaw() : rotationYaw;
        float currentPitch = AntiCheat.INSTANCE.serverSide.getValue() ? getLastPitch() : rotationPitch;

        float yawDelta = Mth.wrapDegrees(angle[0] - currentYaw);
        float pitchDelta = (angle[1] - currentPitch);

        if (AntiCheat.INSTANCE.random.getValue()) {
            float angleToRad = (float) Math.toRadians(27 * (mc.player.tickCount % 30));
            yawDelta = (float) (yawDelta + Math.sin(angleToRad) * 3) + MathUtil.random(-1, 1);
            pitchDelta = pitchDelta + MathUtil.random(-0.6f, 0.6f);
        }

        if (yawDelta > 180)
            yawDelta = yawDelta - 180;

        float yawStepVal = 180 * steps;
        float pitchStepVal = 90 * steps;

        float clampedYawDelta = Mth.clamp(Mth.abs(yawDelta), -yawStepVal, yawStepVal);
        float clampedPitchDelta = Mth.clamp(pitchDelta, -pitchStepVal, pitchStepVal);

        float newYaw = currentYaw + (yawDelta > 0 ? clampedYawDelta : -clampedYawDelta);
        float newPitch = Mth.clamp(currentPitch + clampedPitchDelta, -90.0F, 90.0F);

        return new float[]{newYaw, newPitch};
    }

    @EventListener(priority = -999)
    public void onPacketSend(PacketEvent.Sent event) {
        if (mc.player == null) return;
        if (event.getPacket() instanceof ServerboundMovePlayerPacket packet) {
            if (packet.hasRotation()) {
                setLastYaw(packet.getYRot(getLastYaw()));
                setLastPitch(packet.getXRot(getLastPitch()));
                setRenderRotation(getLastYaw(), getLastPitch(), ClientSetting.INSTANCE.sync.getValue());
            }
        }
    }

    @EventListener
    public void onUpdateWalkingPost(TickEvent event) {
        if (event.isPost())
            setRenderRotation(getLastYaw(), getLastPitch(), false);
    }

    public void setRenderRotation(float yaw, float pitch, boolean force) {
        if (mc.player == null) return;
        if (mc.player.tickCount == ticksExisted && !force) {
            return;
        }

        ticksExisted = mc.player.tickCount;
        prevRenderPitch = renderPitch;

        prevRenderYawOffset = renderYawOffset;
        renderYawOffset = getRenderYawOffset(yaw, prevRenderYawOffset);

        prevRotationYawHead = rotationYawHead;
        rotationYawHead = yaw;

        renderPitch = pitch;
    }

    private float getRenderYawOffset(float yaw, float offsetIn) {
        float result = offsetIn;
        float offset;

        double xDif = mc.player.getX() - mc.player.xo;
        double zDif = mc.player.getZ() - mc.player.zo;

        if (xDif * xDif + zDif * zDif > 0.0025000002f) {
            offset = (float) Mth.atan2(zDif, xDif) * 57.295776f - 90.0f;
            float wrap = Mth.abs(Mth.wrapDegrees(yaw) - offset);
            if (95.0F < wrap && wrap < 265.0F) {
                result = offset - 180.0F;
            } else {
                result = offset;
            }
        }

        if (mc.player.attackAnim > 0.0F) {
            result = yaw;
        }

        result = offsetIn + Mth.wrapDegrees(result - offsetIn) * 0.3f;
        offset = Mth.wrapDegrees(yaw - result);

        if (offset < -75.0f) {
            offset = -75.0f;
        } else if (offset >= 75.0f) {
            offset = 75.0f;
        }

        result = yaw - offset;
        if (offset * offset > 2500.0f) {
            result += offset * 0.2f;
        }

        return result;
    }

    public static float[] getRotation(Vec3 eyesPos, Vec3 vec) {
        double diffX = vec.x - eyesPos.x;
        double diffY = vec.y - eyesPos.y;
        double diffZ = vec.z - eyesPos.z;
        double diffXZ = Math.sqrt(diffX * diffX + diffZ * diffZ);
        float yaw = (float) Math.toDegrees(Math.atan2(diffZ, diffX)) - 90.0f;
        float pitch = (float) (-Math.toDegrees(Math.atan2(diffY, diffXZ)));
        return new float[]{Mth.wrapDegrees(yaw), Mth.wrapDegrees(pitch)};
    }

    public static float[] getRotation(Vec3 vec) {
        Vec3 eyesPos = mc.player.getEyePosition();
        return getRotation(eyesPos, vec);
    }

    public static float getRenderPitch() {
        return renderPitch;
    }

    public static float getRotationYawHead() {
        return rotationYawHead;
    }

    public static float getRenderYawOffset() {
        return renderYawOffset;
    }

    public static float getPrevRenderPitch() {
        return prevRenderPitch;
    }

    public static float getPrevRotationYawHead() {
        return prevRotationYawHead;
    }

    public static float getPrevRenderYawOffset() {
        return prevRenderYawOffset;
    }

    public float getLastYaw() {
        return lastYaw;
    }

    public void setLastYaw(float lastYaw) {
        this.lastYaw = lastYaw;
        if (AntiCheat.INSTANCE.forceSync.getValue() && Alien.SERVER.playerNull.passedS(0.15)) {
            ((IClientPlayerEntity) mc.player).setLastYaw(lastYaw);
        }
    }

    public float getLastPitch() {
        return lastPitch;
    }

    public void setLastPitch(float lastPitch) {
        this.lastPitch = lastPitch;
        if (AntiCheat.INSTANCE.forceSync.getValue() && Alien.SERVER.playerNull.passedS(0.15)) {
            ((IClientPlayerEntity) mc.player).setLastPitch(lastPitch);
        }
    }
}
