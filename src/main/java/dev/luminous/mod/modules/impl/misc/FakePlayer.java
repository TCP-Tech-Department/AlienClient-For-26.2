package dev.luminous.mod.modules.impl.misc;

import com.mojang.authlib.GameProfile;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.Alien;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.math.DamageUtils;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.world.BlockPosX;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.world.BlockUtil;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.asm.accessors.ILivingEntity;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.asm.accessors.IWalkAnimationState;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.mod.modules.impl.combat.AutoAnchor;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.mod.modules.impl.combat.AutoCrystal;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.mod.modules.impl.combat.Criticals;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.mod.modules.settings.impl.StringSetting;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class FakePlayer extends Module {
    public static FakePlayer INSTANCE;
    public static FakePlayerEntity fakePlayer;
    final StringSetting name = add(new StringSetting("Name", "FakePlayer"));
    private final BooleanSetting damage = add(new BooleanSetting("Damage", true));
    private final BooleanSetting autoTotem = add(new BooleanSetting("AutoTotem", true));
    public final BooleanSetting record = add(new BooleanSetting("Record", false));
    public final BooleanSetting play = add(new BooleanSetting("Play", false));

    public FakePlayer() {
        super("FakePlayer", Category.Misc);
        setChinese("假人");
        INSTANCE = this;
    }

    final List<PlayerState> positions = new ArrayList<>();
    int movementTick;
    boolean lastRecordValue = false;

    @Override
    public String getInfo() {
        return name.getValue();
    }

    @Override
    public void onEnable() {
        if (nullCheck()) {
            disable();
            return;
        }
        fakePlayer = new FakePlayerEntity(mc.player, name.getValue());
        EntityUtil.addClientEntity(fakePlayer);
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        // 26.2：AbstractClientPlayer.clientWorld → Entity#level()
        if (fakePlayer == null || fakePlayer.level() != mc.level) {
            disable();
            return;
        }
        if (autoTotem.getValue()) {
            if (fakePlayer.getOffhandItem().getItem() != Items.TOTEM_OF_UNDYING) {
                // 26.2：setStackInHand → setItemInHand
                fakePlayer.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            }
            if (fakePlayer.getMainHandItem().getItem() != Items.TOTEM_OF_UNDYING) {
                fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            }
        }
        if (record.getValue() != lastRecordValue) {
            if (record.getValue()) {
                positions.clear();
            }
        }
        lastRecordValue = record.getValue();
        if (record.getValue()) {
            positions.add(new PlayerState(mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.getYRot(), mc.player.getXRot()));
        }
        if (play.getValue() && !positions.isEmpty()) {
            movementTick++;

            if (movementTick >= positions.size()) {
                movementTick = 0;
            }
            PlayerState p = positions.get(movementTick);
            // 26.2：setYaw/setPitch/setHeadYaw → setYRot/setXRot/setYHeadRot
            fakePlayer.setYRot(p.yaw);
            fakePlayer.setXRot(p.pitch);
            fakePlayer.setYHeadRot(p.yaw);

            // 26.2：Entity#updateTrackedPosition / #updateTrackedPositionAndAngles 是服务端追踪专用方法，
            // 客户端已移除；回放位置改用 snapTo（同时刷新当前位置与 prev 位置）
            fakePlayer.snapTo(p.x, p.y, p.z, p.yaw, p.pitch);
        }
    }

    @Override
    public void onDisable() {
        if (fakePlayer == null) return;
        // 26.2：Entity#kill() 需要 ServerLevel 参数，客户端改用 remove(KILLED)
        // （内部即 setRemoved + onRemoval，等价于旧的 kill() + setRemoved + onRemoved）
        fakePlayer.remove(Entity.RemovalReason.KILLED);
        fakePlayer = null;
    }

    @EventListener
    public void onAttack(PacketEvent.Send event) {
        // 26.2: 攻击包已从 PlayerInteractEntityC2SPacket.attack 拆成独立的 ServerboundAttackPacket(entityId)；
        // 这里只是监听已发出的攻击包来模拟假人受击，无需再发包，实体 id 用 packet.entityId()。
        if (event.getPacket() instanceof ServerboundAttackPacket packet) {
            if (Criticals.getEntity(packet) == fakePlayer) {
                mc.level.playSound(mc.player, fakePlayer.getX(), fakePlayer.getY(), fakePlayer.getZ(), SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1f, 1f);
                float damage = DamageUtils.getAttackDamage(mc.player, fakePlayer);
                if ((mc.player.fallDistance > 0f || Criticals.INSTANCE.isOn() && !Criticals.INSTANCE.mode.is(Criticals.Mode.Ground) && (mc.player.onGround() || !Criticals.INSTANCE.onlyGround.getValue()))
                        && (!mc.player.onGround() || Criticals.INSTANCE.isOn() && !Criticals.INSTANCE.mode.is(Criticals.Mode.Ground))
                        && !mc.player.onClimbable()
                        && !mc.player.isInWater()
                        && !mc.player.hasEffect(MobEffects.BLINDNESS)
                        && !mc.player.isPassenger()) {
                    mc.level.playSound(mc.player, fakePlayer.getX(), fakePlayer.getY(), fakePlayer.getZ(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1f, 1f);
                    // 26.2：addCritParticles → crit
                    mc.player.crit(fakePlayer);
                }
                if (fakePlayer.hurtTime <= 0) {
                    hurtFakePlayer();
                    if (fakePlayer.getAbsorptionAmount() >= damage) {
                        fakePlayer.setAbsorptionAmount(fakePlayer.getAbsorptionAmount() - damage);
                    } else {
                        float damage2 = damage - fakePlayer.getAbsorptionAmount();
                        fakePlayer.setAbsorptionAmount(0);
                        fakePlayer.setHealth(fakePlayer.getHealth() - damage2);
                    }
                    if (fakePlayer.isDeadOrDying()) {
                        Alien.POP.onTotemPop(fakePlayer);
                        if (tryUseTotem(fakePlayer)) {
                            fakePlayer.setHealth(10f);
                            new ClientboundEntityEventPacket(fakePlayer, EntityEvent.PROTECTED_FROM_DEATH).handle(mc.getConnection());
                        }
                    }
                }
            }
        }
    }

    @EventListener
    public void onPacketReceive(PacketEvent.Receive event) {
        if (damage.getValue() && fakePlayer != null && fakePlayer.hurtTime <= 0) {
            if (event.getPacket() instanceof ClientboundExplodePacket explosion) {
                // 26.2：ExplosionS2CPacket.getX/getY/getZ → ClientboundExplodePacket#center()
                Vec3 center = explosion.center();
                // 26.2：squaredDistanceTo → distanceToSqr
                if (Math.sqrt(center.distanceToSqr(fakePlayer.position())) > 10)
                    return;
                float damage;
                if (BlockUtil.getBlock(new BlockPosX(center.x, center.y, center.z)) == Blocks.RESPAWN_ANCHOR) {
                    damage = (float) AutoAnchor.INSTANCE.getAnchorDamage(new BlockPosX(center.x, center.y, center.z), fakePlayer, fakePlayer);
                } else {
                    damage = AutoCrystal.INSTANCE.calculateDamage(center, fakePlayer, fakePlayer);
                }
                hurtFakePlayer();
                if (fakePlayer.getAbsorptionAmount() >= damage) {
                    fakePlayer.setAbsorptionAmount(fakePlayer.getAbsorptionAmount() - damage);
                } else {
                    float damage2 = damage - fakePlayer.getAbsorptionAmount();
                    fakePlayer.setAbsorptionAmount(0);
                    fakePlayer.setHealth(fakePlayer.getHealth() - damage2);
                }
                if (fakePlayer.isDeadOrDying()) {
                    Alien.POP.onTotemPop(fakePlayer);
                    if (tryUseTotem(fakePlayer)) {
                        fakePlayer.setHealth(10f);
                        new ClientboundEntityEventPacket(fakePlayer, EntityEvent.PROTECTED_FROM_DEATH).handle(mc.getConnection());
                    }
                }
            }
        }
    }

    /** 26.2：Yarn onDamaged(DamageSource) → animateHurt（只保留受击动画，伤害数值本方法自己结算） */
    private static void hurtFakePlayer() {
        fakePlayer.animateHurt(0.0f);
    }

    /**
     * 26.2: LivingEntity#tryUseTotem(DamageSource) 变为 private checkTotemDeathProtection(DamageSource)，
     * 通过 {@link ILivingEntity#alienClient$checkTotemDeathProtection(DamageSource)}（@Invoker）调用，
     * 语义与原版一致：查找 DEATH_PROTECTION 组件（不死图腾）、消耗一个、置血量 1 并套用效果。
     */
    private static boolean tryUseTotem(Player player) {
        return ((ILivingEntity) player).alienClient$checkTotemDeathProtection(mc.level.damageSources().generic());
    }

    public static class FakePlayerEntity extends RemotePlayer {

        private final boolean onGround;

        public FakePlayerEntity(Player player, String name) {
            super(mc.level, new GameProfile(UUID.fromString("66666666-6666-6666-6666-666666666666"), name));
            // 26.2：copyPositionAndRotation → snapTo(同时刷新 old pos/rot)
            snapTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());

            yBodyRot = player.yBodyRot;
            yHeadRot = player.yHeadRot;

            // 26.2：handSwingProgress/handSwingTicks → attackAnim/swingTime
            attackAnim = player.attackAnim;
            swingTime = player.swingTime;
            // 26.2：limbAnimator → walkAnimation（旧 LimbAnimator#getSpeed/setSpeed → WalkAnimationState#speed()/setSpeed）
            walkAnimation.setSpeed(player.walkAnimation.speed());
            // 26.2：WalkAnimationState#position 仍是 private 且无 setter，
            // 用 IWalkAnimationState（@Accessor）直接搬运步态相位，等价于旧的 limbAnimator.pos = getPos()
            ((IWalkAnimationState) walkAnimation).alienClient$setPosition(((IWalkAnimationState) player.walkAnimation).alienClient$getPosition());

            ((ILivingEntity) this).setLeaningPitch(((ILivingEntity) player).getLeaningPitch());
            ((ILivingEntity) this).setLastLeaningPitch(((ILivingEntity) player).getLeaningPitch());
            wasTouchingWater = player.isInWater();
            // 26.2：setSneaking → setShiftKeyDown
            setShiftKeyDown(player.isShiftKeyDown());
            setPose(player.getPose());
            setSharedFlag(FLAG_FALL_FLYING, player.isFallFlying());
            onGround = (player.onGround());
            setOnGround(onGround);

            // 26.2：Inventory#clone → replaceWith
            getInventory().replaceWith(player.getInventory());

            // 26.2：setAbsorptionAmountUnclamped 已移除，setAbsorptionAmount 会按上限截断
            setAbsorptionAmount(player.getAbsorptionAmount());
            setHealth(player.getHealth());
            setBoundingBox(player.getBoundingBox());
        }

        @Override
        public boolean onGround() {
            return onGround;
        }

        @Override
        public boolean isSpectator() {
            return false;
        }

        @Override
        public boolean isCreative() {
            return false;
        }
    }

    private record PlayerState(double x, double y, double z, float yaw, float pitch) {
    }
}
