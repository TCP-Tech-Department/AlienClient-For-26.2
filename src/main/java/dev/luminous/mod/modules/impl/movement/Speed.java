package dev.luminous.mod.modules.impl.movement;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.eventbus.EventPriority;
import dev.luminous.api.events.impl.MoveEvent;
import dev.luminous.api.events.impl.MovedEvent;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.path.BaritoneUtil;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.player.MovementUtil;
import dev.luminous.api.utils.world.BlockUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.phys.AABB;
// 26.2: Vec2f → Vec2
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

public class Speed extends Module {

    public static Speed INSTANCE;
    private final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", Mode.Strafe));
    public final SliderSetting collideSpeed = add(new SliderSetting("CollideSpeed", 0.08, 0, 0.08, 0.01, () -> mode.is(Mode.Grim)));
    private final BooleanSetting strict =
            add(new BooleanSetting("Strict", true, () -> mode.is(Mode.Grim)));
    private final BooleanSetting boat =
            add(new BooleanSetting("BoatLongJump", true, () -> mode.is(Mode.Grim)));
    public final SliderSetting boatExpand = add(new SliderSetting("BoatExpand", 0.2, 0, 1, 0.01, () -> mode.is(Mode.Grim)));
    public final SliderSetting boatSpeed = add(new SliderSetting("BoatSpeed", 0.2, -2, 2, 0.01, () -> mode.is(Mode.Grim)));
    public final SliderSetting boatJump = add(new SliderSetting("BoatJump", 0.2, 0, 2, 0.01, () -> mode.is(Mode.Grim)));

    private final BooleanSetting inWater =
            add(new BooleanSetting("InWater", false, () -> !mode.is(Mode.Grim)));
    private final BooleanSetting inBlock =
            add(new BooleanSetting("InBlock", false, () -> !mode.is(Mode.Grim)));
    private final BooleanSetting airStop =
            add(new BooleanSetting("AirStop", false, () -> !mode.is(Mode.Grim)));
    private final SliderSetting lagTime =
            add(new SliderSetting("LagTime", 500, 0, 1000, 1, () -> !mode.is(Mode.Grim)));

    private final BooleanSetting jump =
            add(new BooleanSetting("Jump", true, () -> mode.is(Mode.Strafe)));
    private final SliderSetting strafeSpeed =
            add(new SliderSetting("Speed", 0.2873, 0, 1.0, 0.0001, () -> mode.is(Mode.Strafe)));
    private final BooleanSetting explosions =
            add(new BooleanSetting("ExplosionsBoost", false, () -> mode.is(Mode.Strafe)));
    private final BooleanSetting velocity =
            add(new BooleanSetting("VelocityBoost", true, () -> mode.is(Mode.Strafe)));
    private final SliderSetting multiplier =
            add(new SliderSetting("H-Factor", 1.0f, 0.0f, 5.0f, 0.01, () -> mode.is(Mode.Strafe)));
    private final SliderSetting vertical =
            add(new SliderSetting("V-Factor", 1.0f, 0.0f, 5.0f, 0.01, () -> mode.is(Mode.Strafe)));
    private final SliderSetting coolDown =
            add(new SliderSetting("CoolDown", 1000, 0, 5000, 1, () -> mode.is(Mode.Strafe)));
    private final BooleanSetting slow =
            add(new BooleanSetting("Slowness", false, () -> mode.is(Mode.Strafe)));
    private final Timer expTimer = new Timer();
    private final Timer lagTimer = new Timer();
    private boolean stop;
    private double speed;
    private double distance;

    private int strictTicks;
    private int strafe = 4;
    private int stage;
    private double lastExp;
    private boolean boost;

    public Speed() {
        super("Speed", Category.Movement);
        setChinese("加速");
        INSTANCE = this;
    }

    @Override
    public String getInfo() {
        return mode.getValue().name();
    }

    @Override
    public void onEnable() {
        if (mc.player != null) {
            speed = MovementUtil.getSpeed(false);
            distance = MovementUtil.getDistance2D();
        }

        stage = 4;
    }

    @EventListener(priority = EventPriority.HIGH)
    public void invoke(PacketEvent.Receive event) {
        if (BaritoneUtil.isActive()) return;
        if (mode.is(Mode.Strafe)) {
            if (event.getPacket() instanceof ClientboundSetEntityMotionPacket packet) {
                // 26.2: 该包改为 record(int id, Vec3 movement)
                if (mc.player != null
                        && packet.id() == mc.player.getId()
                        && this.velocity.getValue()) {
                    double speed = Math.sqrt(
                            packet.movement().x * packet.movement().x
                                    + packet.movement().z * packet.movement().z);

                    this.lastExp = this.expTimer
                            .passed(this.coolDown.getValueInt())
                            ? speed
                            : (speed - this.lastExp);

                    if (this.lastExp > 0) {
                        this.expTimer.reset();

                        this.speed +=
                                this.lastExp * this.multiplier.getValue();

                        this.distance +=
                                this.lastExp * this.multiplier.getValue();

                        if (MovementUtil.getMotionY() > 0
                                && this.vertical.getValue() != 0) {
                            MovementUtil.setMotionY(MovementUtil.getMotionY() * this.vertical.getValue());
                        }
                    }
                }
            } else if (event.getPacket() instanceof ClientboundExplodePacket packet) {
                if (this.explosions.getValue()) {
                    // 26.2: 该包改为 record(center, radius, blockCount, Optional<Vec3> playerKnockback, ...)
                    Vec3 knockback = packet.playerKnockback().orElse(Vec3.ZERO);
                    if (mc.player.position().distanceTo(packet.center()) < 15) {
                        double speed = Math.sqrt(knockback.x * knockback.x
                                + knockback.z * knockback.z);
                        this.lastExp = this.expTimer
                                .passed(this.coolDown.getValueInt())
                                ? speed
                                : (speed - this.lastExp);

                        if (this.lastExp > 0) {
                            this.expTimer.reset();

                            this.speed +=
                                    this.lastExp * this.multiplier.getValue();

                            this.distance +=
                                    this.lastExp * this.multiplier.getValue();

                            if (MovementUtil.getMotionY() > 0) {
                                MovementUtil.setMotionY(MovementUtil.getMotionY() * this.vertical.getValue());
                            }
                        }
                    }
                }
            }
        }
        if (event.getPacket() instanceof ClientboundPlayerPositionPacket) {
            lagTimer.reset();
            resetStrafe();
        }
    }

    @EventListener
    public void onMove(MovedEvent event) {
        if (nullCheck()) return;
        // 26.2: prevX/prevZ → xo/zo
        double dx = mc.player.getX() - mc.player.xo;
        double dz = mc.player.getZ() - mc.player.zo;
        distance = Math.sqrt(dx * dx + dz * dz);
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (mode.is(Mode.Grim)) {
            if (!MovementUtil.isMoving()) {
                return;
            }

            int collisions = 0;
            AABB box = strict.getValue() ? mc.player.getBoundingBox() : mc.player.getBoundingBox().inflate(1.0);

            for (Entity entity : Alien.THREAD.getEntities()) {
                AABB entityBox = entity.getBoundingBox();
                if (boat.getValue() && mc.player.onGround() && entity instanceof Boat && box.intersects(entityBox.inflate(boatExpand.getValue()))) {
                    double yaw = Math.toRadians(Sprint.getSprintYaw(mc.player.getYRot()));
                    double boost = boatSpeed.getValue();
                    mc.player.setDeltaMovement(-Math.sin(yaw) * boost, boatJump.getValue(), Math.cos(yaw) * boost);
                    return;
                } else if (box.intersects(entityBox) && canCauseSpeed(entity)) {
                    collisions++;
                }
            }

            double yaw = Math.toRadians(Sprint.getSprintYaw(mc.player.getYRot()));
            double boost = this.collideSpeed.getValue() * collisions;
            mc.player.push(-Math.sin(yaw) * boost, 0.0, Math.cos(yaw) * boost);
        }
    }

    private boolean canCauseSpeed(Entity entity) {
        return entity != mc.player && entity instanceof LivingEntity && !(entity instanceof ArmorStand);
    }

    @EventListener
    public void invoke(MoveEvent event) {
        if (!MovementUtil.isMoving() && airStop.getValue() && !mode.is(Mode.Grim)) {
            MovementUtil.setMotionX(0);
            MovementUtil.setMotionZ(0);
        }
        // 26.2: isSubmergedInWater → isUnderWater；isRiding → isPassenger；isHoldingOntoLadder → onClimbable
        if (!this.inWater.getValue() && (mc.player.isUnderWater() || mc.player.isInWater() || mc.player.isInLava())
                || mc.player.isPassenger()
                || mc.player.onClimbable()
                || !inBlock.getValue() && EntityUtil.isInsideBlock()
                || mc.player.getAbilities().flying
                || mc.player.isFallFlying()
                || !MovementUtil.isMoving()) {
            resetStrafe();
            this.stop = true;
            return;
        }
        if (mode.is(Mode.Strafe)) {

            if (this.stop) {
                this.stop = false;
                return;
            }

            if (!lagTimer.passed(this.lagTime.getValueInt())) {
                return;
            }

            if (this.stage == 1) {
                this.speed = 1.35 * MovementUtil.getSpeed(this.slow.getValue(), this.strafeSpeed.getValue()) - 0.01;
            } else if (this.stage == 2 && mc.player.onGround() && (mc.options.keyJump.isDown() || this.jump.getValue())) {
                double yMotion = 0.3999 + MovementUtil.getJumpSpeed();
                MovementUtil.setMotionY(yMotion);
                event.setY(yMotion);
                this.speed = this.speed * (this.boost ? 1.6835 : 1.395);
            } else if (this.stage == 3) {
                this.speed = this.distance - 0.66
                        * (this.distance - MovementUtil.getSpeed(this.slow.getValue(), this.strafeSpeed.getValue()));

                this.boost = !this.boost;
            } else {
                if ((BlockUtil.canCollide(null,
                        mc.player
                                .getBoundingBox()
                                .move(0.0, MovementUtil.getMotionY(), 0.0))
                        || mc.player.minorHorizontalCollision)
                        && this.stage > 0) {
                    this.stage = 1;
                }

                this.speed = this.distance - this.distance / 159.0;
            }

            this.speed = Math.min(this.speed, 10);
            this.speed = Math.max(this.speed, MovementUtil.getSpeed(this.slow.getValue(), this.strafeSpeed.getValue()));
            // 26.2: movementForward / movementSideways → ClientInput#getMoveVector
            double n = mc.player.input.getMoveVector().y;
            double n2 = mc.player.input.getMoveVector().x;
            double n3 = mc.player.getYRot();
            if (n == 0.0 && n2 == 0.0) {
                event.setX(0.0);
                event.setZ(0.0);
            } else if (n != 0.0 && n2 != 0.0) {
                n *= Math.sin(0.7853981633974483);
                n2 *= Math.cos(0.7853981633974483);
            }
            event.setX((n * this.speed * -Math.sin(Math.toRadians(n3)) + n2 * this.speed * Math.cos(Math.toRadians(n3))) * 0.99);
            event.setZ((n * this.speed * Math.cos(Math.toRadians(n3)) - n2 * this.speed * -Math.sin(Math.toRadians(n3))) * 0.99);

            this.stage++;
            return;
        }
        double speedEffect = 1.0;
        double slowEffect = 1.0;
        if (mc.player.hasEffect(MobEffects.SPEED)) {
            double amplifier = mc.player.getEffect(MobEffects.SPEED).getAmplifier();
            speedEffect = 1 + (0.2 * (amplifier + 1));
        }
        if (mc.player.hasEffect(MobEffects.SLOWNESS)) {
            double amplifier = mc.player.getEffect(MobEffects.SLOWNESS).getAmplifier();
            slowEffect = 1 + (0.2 * (amplifier + 1));
        }
        final double base = 0.2873f * speedEffect / slowEffect;
        float jumpEffect = 0.0f;
        if (mc.player.hasEffect(MobEffects.JUMP_BOOST)) {
            jumpEffect += (mc.player.getEffect(MobEffects.JUMP_BOOST).getAmplifier() + 1) * 0.1f;
        }

        if (mode.getValue() == Mode.StrafeStrict) {
            if (!lagTimer.passed(lagTime.getValueInt())) {
                return;
            }
            if (strafe == 1) {
                speed = 1.35f * base - 0.01f;
            } else if (strafe == 2) {
                // 26.2: input.jumping → keyPresses.jump()
                if (mc.player.input.keyPresses.jump() || !mc.player.onGround()) {
                    return;
                }
                float jump = 0.3999999463558197f + jumpEffect;
                event.setY(jump);
                MovementUtil.setMotionY(jump);
                speed *= 2.149;
            } else if (strafe == 3) {
                double moveSpeed = 0.66 * (distance - base);
                speed = distance - moveSpeed;
            } else {
                // 26.2: isSpaceEmpty → noCollision；getDeltaMovement().getY() → .y
                if ((!mc.level.noCollision(mc.player, mc.player.getBoundingBox().move(0,
                        mc.player.getDeltaMovement().y, 0)) || mc.player.verticalCollision) && strafe > 0) {
                    strafe = 1;
                }
                speed = distance - distance / 159.0;
            }
            strictTicks++;
            speed = Math.max(speed, base);
            double baseMax = 0.465 * speedEffect / slowEffect;
            double baseMin = 0.44 * speedEffect / slowEffect;
            speed = Math.min(speed, strictTicks > 25 ? baseMax : baseMin);
            if (strictTicks > 50) {
                strictTicks = 0;
            }
            final Vec2 motion = handleStrafeMotion((float) speed);
            event.setX(motion.x);
            event.setZ(motion.y);
            strafe++;
        }
    }

    public Vec2 handleStrafeMotion(final float speed) {
        // 26.2: movementForward / movementSideways → getMoveVector；prevYaw → yRotO
        float forward = mc.player.input.getMoveVector().y;
        float strafe = mc.player.input.getMoveVector().x;
        float yaw = mc.player.yRotO + (mc.player.getYRot() - mc.player.yRotO) * mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        if (forward == 0.0f && strafe == 0.0f) {
            return Vec2.ZERO;
        } else if (forward != 0.0f) {
            if (strafe >= 1.0f) {
                yaw += forward > 0.0f ? -45 : 45;
                strafe = 0.0f;
            } else if (strafe <= -1.0f) {
                yaw += forward > 0.0f ? 45 : -45;
                strafe = 0.0f;
            }
            if (forward > 0.0f) {
                forward = 1.0f;
            } else if (forward < 0.0f) {
                forward = -1.0f;
            }
        }
        float rx = (float) Math.cos(Math.toRadians(yaw));
        float rz = (float) -Math.sin(Math.toRadians(yaw));
        return new Vec2((forward * speed * rz) + (strafe * speed * rx),
                (forward * speed * rx) - (strafe * speed * rz));
    }

    public void resetStrafe() {
        strafe = 4;
        strictTicks = 0;
        speed = 0.0f;
        distance = 0.0;
    }

    public enum Mode {
        Strafe,
        StrafeStrict,
        Grim,
    }
}