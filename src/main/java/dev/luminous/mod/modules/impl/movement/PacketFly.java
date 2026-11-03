package dev.luminous.mod.modules.impl.movement;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.MoveEvent;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.api.events.impl.TickEvent;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.player.MovementUtil;
import dev.luminous.api.utils.world.BlockUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import io.netty.util.internal.ConcurrentSet;
// 26.2: DownloadingTerrainScreen → LevelLoadingScreen（下载地形的界面已并入后者）
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
// 26.2: TeleportConfirmC2SPacket → ServerboundAcceptTeleportationPacket
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;

public class PacketFly extends Module {
    public static PacketFly INSTANCE;
    public final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", Mode.Factor));
    public final SliderSetting factor = add(new SliderSetting("Factor", 1.0f, 0.0f, 10.0f));
    public final EnumSetting<Phase> phase = add(new EnumSetting<>("Phase", Phase.Full));
    public final EnumSetting<Type> type = add(new EnumSetting<>("Type", Type.Up));
    public final BooleanSetting antiKick = add(new BooleanSetting("AntiKick", true));
    public final BooleanSetting noRotation = add(new BooleanSetting("NoRotation", false));
    public final BooleanSetting noMovePacket = add(new BooleanSetting("NoMovePacket", false));
    public final BooleanSetting bbOffset = add(new BooleanSetting("BB-Offset", false));
    public final SliderSetting invalidY = add(new SliderSetting("Invalid-Offset", 1337, 0, 1337));
    public final SliderSetting invalids = add(new SliderSetting("Invalids", 1, 0, 10));
    public final SliderSetting sendTeleport = add(new SliderSetting("Teleport", 1, 0, 10));
    public final SliderSetting concealY = add(new SliderSetting("C-Y", 0.0, -256.0, 256.0));
    public final SliderSetting conceal = add(new SliderSetting("C-Multiplier", 1.0, 0.0, 2.0));
    public final SliderSetting ySpeed = add(new SliderSetting("Y-Multiplier", 1.0, 0.0, 2.0));
    public final SliderSetting xzSpeed = add(new SliderSetting("X/Z-Multiplier", 1.0, 0.0, 2.0));
    public final BooleanSetting elytra = add(new BooleanSetting("Elytra", false));
    public final BooleanSetting xzJitter = add(new BooleanSetting("Jitter-XZ", false));
    public final BooleanSetting yJitter = add(new BooleanSetting("Jitter-Y", false));
    public final BooleanSetting zeroSpeed = add(new BooleanSetting("Zero-Speed", false));
    public final BooleanSetting zeroY = add(new BooleanSetting("Zero-Y", false));
    public final BooleanSetting zeroTeleport = add(new BooleanSetting("Zero-Teleport", true));
    public final SliderSetting zoomer = add(new SliderSetting("Zoomies", 3, 0, 10));

    public final Map<Integer, TimeVec> posLooks = new ConcurrentHashMap<>();
    public final Set<Packet<?>> playerPackets = new ConcurrentSet<>();
    public final AtomicInteger teleportID = new AtomicInteger();
    public Vec3 vecDelServer;
    public int packetCounter;
    public boolean zoomies;
    public float lastFactor;
    public int zoomTimer = 0;

    public PacketFly() {
        super("PacketFly", Category.Movement);
        setChinese("发包飞行");
        INSTANCE = this;
    }

    @Override
    public void onLogin() {
        disable();
        clearValues();
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        posLooks.entrySet().removeIf(entry -> System.currentTimeMillis() - entry.getValue().getTime() > TimeUnit.SECONDS.toMillis(30L));
    }

    @EventListener
    public void invoke(TickEvent event) {
        if (event.isPre() && this.mode.getValue() != Mode.Compatibility) {
            MovementUtil.setMotionX(0);
            MovementUtil.setMotionY(0);
            MovementUtil.setMotionZ(0);

            if (this.mode.getValue() != Mode.Setback && this.teleportID.get() == 0) {
                if (this.checkPackets(6)) {
                    this.sendPackets(0.0, 0.0, 0.0, true);
                }

                return;
            }

            boolean isPhasing = this.isPlayerCollisionBoundingBoxEmpty();
            double ySpeed;

            // 26.2: input.jumping / input.sneaking → keyPresses.jump() / keyPresses.shift()
            if (mc.player.input.keyPresses.jump() && (isPhasing || !MovementUtil.isMoving())) {
                if (this.antiKick.getValue() && !isPhasing) {
                    ySpeed = this.checkPackets(this.mode.getValue() == Mode.Setback ? 10 : 20) ? -0.032 : 0.062;
                } else {
                    ySpeed = this.yJitter.getValue() && this.zoomies ? 0.061 : 0.062;
                }
            } else if (mc.player.input.keyPresses.shift()) {
                ySpeed = this.yJitter.getValue() && this.zoomies ? -0.061 : -0.062;
            } else {
                ySpeed = !isPhasing ? (this.checkPackets(4) ? (this.antiKick.getValue() ? -0.04 : 0.0) : 0.0) : 0.0;
            }

            if (this.phase.getValue() == Phase.Full && isPhasing && MovementUtil.isMoving() && ySpeed != 0.0) {
                ySpeed /= 2.5;
            }

            double high = this.xzJitter.getValue() && this.zoomies ? 0.25 : 0.26;
            double low = this.xzJitter.getValue() && this.zoomies ? 0.030 : 0.031;

            double[] dirSpeed = MovementUtil.directionSpeed(this.phase.getValue() == Phase.Full && isPhasing ? low : high);

            if (this.mode.getValue() == Mode.Increment) {
                if (this.lastFactor >= this.factor.getValue()) {
                    this.lastFactor = 1.0f;
                } else if (++this.lastFactor > this.factor.getValue()) {
                    this.lastFactor = this.factor.getValueFloat();
                }
            } else {
                this.lastFactor = this.factor.getValueFloat();
            }

            for (int i = 1; i <= (this.mode.getValue() == Mode.Factor || this.mode.getValue() == Mode.Slow || this.mode.getValue() == Mode.Increment ? this.lastFactor : 1); i++) {
                double conceal = mc.player.getY() < this.concealY.getValue() && MovementUtil.isMoving() ? this.conceal.getValue() : 1.0;

                MovementUtil.setMotionX(dirSpeed[0] * i * conceal * this.xzSpeed.getValue());
                MovementUtil.setMotionY(ySpeed * i * this.ySpeed.getValue());
                MovementUtil.setMotionZ(dirSpeed[1] * i * conceal * this.xzSpeed.getValue());
                this.sendPackets(MovementUtil.getMotionX(), MovementUtil.getMotionY(), MovementUtil.getMotionZ(), this.mode.getValue() != Mode.Setback);
            }

            this.zoomTimer++;
            if (this.zoomTimer > this.zoomer.getValue()) {
                this.zoomies = !this.zoomies;
                this.zoomTimer = 0;
            }
        }
    }

    @EventListener
    public void invoke(PacketEvent.Receive event) {
        if (nullCheck()) return;
        if (this.mode.getValue() == Mode.Compatibility) {
            return;
        }

        if (event.getPacket() instanceof ClientboundPlayerPositionPacket packet) {
            // 26.2: 该包改为 record(int id, PositionMoveRotation change, Set<Relative> relatives)
            Vec3 changePos = packet.change().position();
            if (mc.player.isAlive() && this.mode.getValue() != Mode.Setback && this.mode.getValue() != Mode.Slow && !(mc.gui.screen() instanceof LevelLoadingScreen)) {
                TimeVec vec = this.posLooks.remove(packet.id());
                if (vec != null && vec.x == changePos.x && vec.y == changePos.y && vec.z == changePos.z) {
                    event.setCancelled(true);
                    return;
                }
            }

            this.teleportID.set(packet.id());
        }
    }

    @EventListener
    public void invoke(MoveEvent event) {
        if (this.phase.getValue() == Phase.Semi || this.isPlayerCollisionBoundingBoxEmpty()) {
            // 26.2: noClip → noPhysics
            mc.player.noPhysics = true;
        }
        if (this.mode.getValue() != Mode.Compatibility && (this.mode.getValue() == Mode.Setback || this.teleportID.get() != 0)) {
            if (this.zeroSpeed.getValue()) {
                event.setX(0.0);
                event.setY(0.0);
                event.setZ(0.0);
            } else {
                event.setX(MovementUtil.getMotionX());
                event.setY(MovementUtil.getMotionY());
                event.setZ(MovementUtil.getMotionZ());
            }

            if (this.zeroY.getValue()) {
                event.setY(0.0);
            }
        }
    }

    @EventListener
    public void onPacket(PacketEvent.Send event) {
        if (event.getPacket() instanceof ServerboundMovePlayerPacket packet) {
            if (mode.getValue() != Mode.Compatibility && !playerPackets.remove(event.getPacket())) {
                // 26.2: LookAndOnGround → Rot
                if (packet instanceof ServerboundMovePlayerPacket.Rot && !noRotation.getValue()) return;
                if (!noMovePacket.getValue()) return;
                event.setCancelled(true);
            }
        }
    }

    @Override
    public void onEnable() {
        clearValues();
        if (mc.player == null) {
            this.disable();
        }

        // teleportID.set(Managers.POSITION.getTeleportID());
    }

    @Override
    public String getInfo() {
        return mode.getValue().toString();
    }

    public void clearValues() {
        lastFactor = 1.0f;
        packetCounter = 0;
        teleportID.set(0);
        playerPackets.clear();
        posLooks.clear();
        vecDelServer = null;
    }

    public boolean isPlayerCollisionBoundingBoxEmpty() {
        double o = bbOffset.getValue() ? -0.0625 : 0;
        // 26.2: expand → inflate
        return BlockUtil.canCollide(mc.player, mc.player.getBoundingBox().inflate(o, o, o));
    }

    public boolean checkPackets(int amount) {
        if (++this.packetCounter >= amount) {
            this.packetCounter = 0;
            return true;
        }

        return false;
    }

    public void sendPackets(double x, double y, double z, boolean confirm) {
        Vec3 offset = new Vec3(x, y, z);
        Vec3 vec = mc.player.position().add(offset);
        vecDelServer = vec;
        Vec3 oOB = type.getValue().createOutOfBounds(vec, invalidY.getValueInt());

        // 26.2: PositionAndOnGround → Pos(x,y,z,onGround,horizontalCollision)
        sendCPacket(new ServerboundMovePlayerPacket.Pos(vec.x, vec.y, vec.z, mc.player.onGround(), mc.player.horizontalCollision));

        // 26.2: isInSingleplayer → hasSingleplayerServer
        if (!mc.hasSingleplayerServer()) {
            for (int i = 0; i < invalids.getValue(); i++) {
                sendCPacket(new ServerboundMovePlayerPacket.Pos(oOB.x, oOB.y, oOB.z, mc.player.onGround(), mc.player.horizontalCollision));
                oOB = type.getValue().createOutOfBounds(oOB, invalidY.getValueInt());
            }
        }

        if (confirm && (zeroTeleport.getValue() || teleportID.get() != 0)) {
            for (int i = 0; i < sendTeleport.getValue(); i++) {
                sendConfirmTeleport(vec);
            }
        }

        if (elytra.getValue()) {
            mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
        }
    }

    public void sendConfirmTeleport(Vec3 vec) {
        int id = teleportID.incrementAndGet();
        // 26.2: TeleportConfirmC2SPacket → ServerboundAcceptTeleportationPacket
        mc.getConnection().send(new ServerboundAcceptTeleportationPacket(id));
        posLooks.put(id, new TimeVec(vec));
    }

    public void sendCPacket(Packet<?> packet) {
        playerPackets.add(packet);
        mc.getConnection().send(packet);
    }

    public enum Mode {
        Setback, Fast, Factor, Slow, Increment, Compatibility
    }

    public enum Phase {
        Off, Semi, Full
    }

    public enum Type {
        Down() {
            @Override
            public Vec3 createOutOfBounds(Vec3 vec3d, int invalid) {
                return vec3d.add(0, -invalid, 0);
            }
        }, Up() {
            @Override
            public Vec3 createOutOfBounds(Vec3 vec3d, int invalid) {
                return vec3d.add(0, invalid, 0);
            }
        }, Preserve() {
            final Random random = new Random();

            private int randomInt() {
                int result = random.nextInt(29000000);
                if (random.nextBoolean()) {
                    return result;
                }

                return -result;
            }

            @Override
            public Vec3 createOutOfBounds(Vec3 vec3d, int invalid) {
                return vec3d.add(randomInt(), 0, randomInt());
            }
        }, Switch() {
            final Random random = new Random();

            @Override
            public Vec3 createOutOfBounds(Vec3 vec3d, int invalid) {
                boolean down = random.nextBoolean();
                return down ? vec3d.add(0, -invalid, 0) : vec3d.add(0, invalid, 0);
            }
        }, X() {
            @Override
            public Vec3 createOutOfBounds(Vec3 vec3d, int invalid) {
                return vec3d.add(invalid, 0, 0);
            }
        }, Z() {
            @Override
            public Vec3 createOutOfBounds(Vec3 vec3d, int invalid) {
                return vec3d.add(0, 0, invalid);
            }
        }, XZ() {
            @Override
            public Vec3 createOutOfBounds(Vec3 vec3d, int invalid) {
                return vec3d.add(invalid, 0, invalid);
            }
        };

        public abstract Vec3 createOutOfBounds(Vec3 vec3d, int invalid);

    }

    public static class TimeVec extends Vec3 {
        final long time;

        public TimeVec(Vec3 vec3d) {
            this(vec3d.x, vec3d.y, vec3d.z, System.currentTimeMillis());
        }

        public TimeVec(double xIn, double yIn, double zIn, long time) {
            super(xIn, yIn, zIn);
            this.time = time;
        }

        public long getTime() {
            return time;
        }

    }

}
