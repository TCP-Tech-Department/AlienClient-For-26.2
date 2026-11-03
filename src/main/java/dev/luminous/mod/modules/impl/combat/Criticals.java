package dev.luminous.mod.modules.impl.combat;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.player.MovementUtil;
import dev.luminous.api.utils.world.BlockUtil;
import dev.luminous.asm.accessors.IPlayerMoveC2SPacket;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.exploit.Blink;
import dev.luminous.mod.modules.impl.exploit.BowBomb;
import dev.luminous.mod.modules.impl.exploit.Phase;
import dev.luminous.mod.modules.impl.player.AutoPearl;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.AABB;

import static dev.luminous.api.utils.player.EntityUtil.getPlayerPos;

public class Criticals extends Module {
    public static Criticals INSTANCE;
    public final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", Mode.OldNCP));
    public final BooleanSetting onlyGround = add(new BooleanSetting("OnlyGround", true, () -> !mode.is(Mode.Ground)));
    private final BooleanSetting setOnGround = add(new BooleanSetting("SetNoGround", false, () -> mode.is(Mode.Ground)));
    private final BooleanSetting blockCheck = add(new BooleanSetting("BlockCheck", true, () -> mode.is(Mode.Ground)));
    private final BooleanSetting autoJump = add(new BooleanSetting("AutoJump", true, () -> mode.is(Mode.Ground)).setParent());
    private final BooleanSetting mini = add(new BooleanSetting("Mini", true, () -> mode.is(Mode.Ground) && autoJump.isOpen()));
    private final SliderSetting y = add(new SliderSetting("MotionY", 0.05, 0, 1, 0.0000000001, () -> mode.is(Mode.Ground) && autoJump.isOpen()));
    private final BooleanSetting autoDisable = add(new BooleanSetting("AutoDisable", true, () -> mode.is(Mode.Ground)));
    private final BooleanSetting crawlingDisable = add(new BooleanSetting("CrawlingDisable", true, () -> mode.is(Mode.Ground)));
    private final BooleanSetting flight = add(new BooleanSetting("Flight", false, () -> mode.is(Mode.Ground)));

    public Criticals() {
        super("Criticals", Category.Combat);
        setChinese("刀刀暴击");
        INSTANCE = this;
    }

    @Override
    public String getInfo() {
        return mode.getValue().name();
    }

    @EventListener
    public void onPacketSend(PacketEvent.Send event) {
        if (event.isCancelled()) return;
        if (Blink.INSTANCE.isOn() && Blink.INSTANCE.pauseModule.getValue()) return;
        if (mode.is(Mode.Ground)) {
            if (BowBomb.send) return;
            if (AutoPearl.throwing || Phase.INSTANCE.isOn()) return;
            if (!setOnGround.getValue()) {
                return;
            }
            if (event.getPacket() instanceof ServerboundMovePlayerPacket) {
                ((IPlayerMoveC2SPacket) event.getPacket()).setOnGround(false);
            }
            return;
        }
        Entity entity;
        // 26.2: 攻击已拆成独立的 ServerboundAttackPacket(entityId)（record），
        // 旧的 PlayerInteractEntityC2SPacket.InteractType.ATTACK 判定等价于 instanceof ServerboundAttackPacket。
        //
        // 26.2 服务端暴击判定 = Player#attack → canCriticalAttack(entity)（26.2/net/minecraft/world/entity/player/Player.java:1032）：
        //     fallDistance > 0 && !onGround && !onClimbable && !isInWater && !isMobilityRestricted
        //     && !isPassenger && entity instanceof LivingEntity && !isSprinting
        // 所以只需要让服务端在处理攻击包时看到「玩家离地 + fallDistance > 0」。
        // doCrit() 发出的 ServerboundMovePlayerPacket.Pos 会把 y 抬高并置 onGround=false；
        // 这些包是在 ClientConnection#send 的 HEAD 里（MixinClientConnection 的 PacketEvent.Send）
        // 重入发出的，因此一定排在攻击包之前被写入，服务端处理攻击包时已经处于下落状态 → 触发暴击。
        // 注意：疾跑时不暴击（Vanilla 规则），本模块不改变该行为。
        if (event.getPacket() instanceof ServerboundAttackPacket packet && !((entity = getEntity(packet)) instanceof EndCrystal)) {
            if ((!onlyGround.getValue() || mc.player.onGround() || mc.player.getAbilities().flying) && !mc.player.isInLava() && !mc.player.isInWater() && entity != null) {
                doCrit(entity);
            }
        }
    }

    boolean requireJump = false;

    @Override
    public void onLogout() {
        if (mode.is(Mode.Ground)) {
            if (autoDisable.getValue())
                disable();
        }
    }

    @Override
    public void onEnable() {
        if (Blink.INSTANCE.isOn() && Blink.INSTANCE.pauseModule.getValue()) return;
        requireJump = true;
        if (mode.is(Mode.Ground)) {
            if (nullCheck()) {
                if (autoDisable.getValue()) {
                    disable();
                }
            } else if (MovementUtil.isMoving() && autoDisable.getValue()) {
                disable();
            } else if (crawlingDisable.getValue() && mc.player.isVisuallyCrawling()) {
                disable();
            } else if (mc.player.onGround() && autoJump.getValue() && (!blockCheck.getValue() || BlockUtil.canCollide(mc.player, new AABB(getPlayerPos(true).above(2))))) {
                jump();
            }
        }
    }

    public void jump() {
        if (mini.getValue()) {
            MovementUtil.setMotionY(y.getValue());
        } else {
            mc.player.jumpFromGround();
        }
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (Blink.INSTANCE.isOn() && Blink.INSTANCE.pauseModule.getValue()) return;
        if (mode.is(Mode.Ground)) {
            if (crawlingDisable.getValue() && mc.player.isVisuallyCrawling()) {
                disable();
            } else if (MovementUtil.isMoving() && autoDisable.getValue()) {
                disable();
            } else if (flight.getValue() && mc.player.fallDistance > 0) {
                MovementUtil.setMotionY(0.0);
                MovementUtil.setMotionX(0.0);
                MovementUtil.setMotionZ(0.0);
                requireJump = false;
            } else if (blockCheck.getValue() && !BlockUtil.canCollide(mc.player, new AABB(getPlayerPos(true).above(2)))) {
                requireJump = true;
            } else if (mc.player.onGround() && autoJump.getValue() && (flight.getValue() || requireJump)) {
                jump();
                requireJump = false;
            }
        }
    }

    public void doCrit(Entity entity) {
        switch (mode.getValue()) {
            case BBTT -> {
                if (MovementUtil.isMoving() || !MovementUtil.isStatic()) return;
                mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY(), mc.player.getZ(), true, false));
                mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY() + 0.0625, mc.player.getZ(), false, false));
                mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY() + 0.045, mc.player.getZ(), false, false));
            }
            case Strict -> {
                mc.player.crit(entity);
                mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY() + 0.062600301692775, mc.player.getZ(), false, false));
                mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY() + 0.07260029960661, mc.player.getZ(), false, false));
                mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY(), mc.player.getZ(), false, false));
                mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY(), mc.player.getZ(), false, false));
            }
            case NCP -> {
                mc.player.crit(entity);
                mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY() + 0.0625D, mc.player.getZ(), false, false));
                mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY(), mc.player.getZ(), false, false));
            }
            case OldNCP -> {
                mc.player.crit(entity);
                mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY() + 0.00001058293536, mc.player.getZ(), false, false));
                mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY() + 0.00000916580235, mc.player.getZ(), false, false));
                mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY() + 0.00000010371854, mc.player.getZ(), false, false));
            }
            case UpdatedNCP -> {
                mc.player.crit(entity);
                mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY() + 0.000000271875, mc.player.getZ(), false, false));
                mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY(), mc.player.getZ(), false, false));
            }
            case Hypixel2K22 -> {
                mc.player.crit(entity);
                mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY() + 0.0045, mc.player.getZ(), true, false));
                mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY() + 0.000152121, mc.player.getZ(), false, false));
                mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY() + 0.3, mc.player.getZ(), false, false));
                mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY() + 0.025, mc.player.getZ(), false, false));
            }
            case Packet -> {
                mc.player.crit(entity);
                mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY() + 0.0005, mc.player.getZ(), false, false));
                mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY() + 0.0001, mc.player.getZ(), false, false));
            }
        }
    }

    public enum Mode {
        UpdatedNCP, Strict, NCP, OldNCP, Hypixel2K22, Packet, Ground, BBTT
    }

    public static Entity getEntity(ServerboundAttackPacket packet) {
        return mc.level == null ? null : mc.level.getEntity(packet.entityId());
    }

    public static Entity getEntity(ServerboundInteractPacket packet) {
        return mc.level == null ? null : mc.level.getEntity(packet.entityId());
    }
}