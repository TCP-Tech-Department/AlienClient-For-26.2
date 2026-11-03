package dev.luminous.mod.modules.impl.combat;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.ClientTickEvent;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.api.events.impl.RotationEvent;
import dev.luminous.api.utils.combat.CombatUtil;
import dev.luminous.api.utils.math.Animation;
import dev.luminous.api.utils.math.Easing;
import dev.luminous.api.utils.math.MathUtil;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.render.ColorUtil;
import dev.luminous.api.utils.render.JelloUtil;
import dev.luminous.api.utils.render.Render3DUtil;
import dev.luminous.asm.accessors.IEntity;
import dev.luminous.asm.accessors.ILivingEntity;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.exploit.Blink;
import dev.luminous.mod.modules.impl.movement.ElytraFly;
import dev.luminous.mod.modules.impl.movement.Velocity;
import dev.luminous.mod.modules.settings.enums.SwingSide;
import dev.luminous.mod.modules.settings.enums.Timing;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.ColorSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;

import java.awt.*;

public class Aura extends Module {

    public static Aura INSTANCE;
    public static Entity target;
    public final EnumSetting<Page> page = add(new EnumSetting<>("Page", Page.General));

    public final SliderSetting range =
            add(new SliderSetting("Range", 6.0f, 0.1f, 7.0f, () -> page.getValue() == Page.General));
    private final SliderSetting targetRange =
            add(new SliderSetting("TargetRange", 8.0f, 0.1f, 14.0f, () -> page.getValue() == Page.General));
    private final EnumSetting<Cooldown> cooldownMode = add(new EnumSetting<>("CooldownMode", Cooldown.Delay, () -> page.getValue() == Page.General));
    private final BooleanSetting reset =
            add(new BooleanSetting("Reset", true, () -> page.getValue() == Page.General && cooldownMode.is(Cooldown.Delay)));
    private final EnumSetting<SwingSide> swingMode = add(new EnumSetting<>("Swing", SwingSide.All, () -> page.getValue() == Page.General));
    private final SliderSetting hurtTime = add(new SliderSetting("HurtTime", 10, 0, 10, 1, () -> page.getValue() == Page.General));
    private final SliderSetting cooldown =
            add(new SliderSetting("Cooldown", 1.1f, 0f, 1.2f, 0.01, () -> page.getValue() == Page.General));
    private final SliderSetting wallRange =
            add(new SliderSetting("WallRange", 6.0f, 0.1f, 7.0f, () -> page.getValue() == Page.General));
    private final BooleanSetting whileEating =
            add(new BooleanSetting("WhileUsing", true, () -> page.getValue() == Page.General));
    private final BooleanSetting weaponOnly =
            add(new BooleanSetting("WeaponOnly", true, () -> page.getValue() == Page.General));
    private final EnumSetting<Timing> timing =
            add(new EnumSetting<>("Timing", Timing.All, () -> page.getValue() == Page.General));

    private final BooleanSetting Players = add(new BooleanSetting("Players", true, () -> page.getValue() == Page.Target).setParent());
    private final BooleanSetting armorLow = add(new BooleanSetting("ArmorLow", true, () -> page.getValue() == Page.Target && Players.isOpen()));
    private final BooleanSetting Mobs = add(new BooleanSetting("Mobs", true, () -> page.getValue() == Page.Target));
    private final BooleanSetting Animals = add(new BooleanSetting("Animals", true, () -> page.getValue() == Page.Target));
    private final BooleanSetting Villagers = add(new BooleanSetting("Villagers", true, () -> page.getValue() == Page.Target));
    private final BooleanSetting Slimes = add(new BooleanSetting("Slimes", true, () -> page.getValue() == Page.Target));
    private final EnumSetting<TargetMode> targetMode =
            add(new EnumSetting<>("Filter", TargetMode.DISTANCE, () -> page.getValue() == Page.Target));

    private final EnumSetting<TargetESP> mode = add(new EnumSetting<>("TargetESP", TargetESP.Fill, () -> page.getValue() == Page.Render));
    private final SliderSetting animationTime = add(new SliderSetting("AnimationTime", 200, 0, 2000, 1, () -> page.getValue() == Page.Render));
    private final EnumSetting<Easing> ease = add(new EnumSetting<>("Ease", Easing.CubicInOut, () -> page.getValue() == Page.Render));
    private final ColorSetting color = add(new ColorSetting("Color", new Color(255, 255, 255, 50), () -> page.getValue() == Page.Render));
    private final ColorSetting outlineColor = add(new ColorSetting("OutlineColor", new Color(255, 255, 255, 50), () -> page.getValue() == Page.Render));
    private final ColorSetting hitColor = add(new ColorSetting("HitColor", new Color(255, 255, 255, 150), () -> page.getValue() == Page.Render));
    private final ColorSetting hitOutlineColor = add(new ColorSetting("HitOutlineColor", new Color(255, 255, 255, 150), () -> page.getValue() == Page.Render));

    private final Animation animation = new Animation();

    private final BooleanSetting rotate =
            add(new BooleanSetting("Rotate", true, () -> page.getValue() == Page.Rotate));
    private final BooleanSetting yawStep =
            add(new BooleanSetting("YawStep", false, () -> rotate.isOpen() && page.getValue() == Page.Rotate).setParent());
    private final BooleanSetting whenElytra =
            add(new BooleanSetting("FallFlying", true, () -> rotate.isOpen() && yawStep.isOpen() && page.getValue() == Page.Rotate));
    private final SliderSetting steps =
            add(new SliderSetting("Steps", 0.05, 0, 1, 0.01, () -> page.getValue() == Page.Rotate && yawStep.isOpen()));
    private final BooleanSetting checkFov =
            add(new BooleanSetting("OnlyLooking", true, () -> page.getValue() == Page.Rotate && yawStep.isOpen()));
    private final SliderSetting fov =
            add(new SliderSetting("Fov", 20, 0, 360, 0.1, () -> checkFov.getValue() && page.getValue() == Page.Rotate && yawStep.isOpen()));
    private final SliderSetting priority = add(new SliderSetting("Priority", 10, 0, 100, () -> page.getValue() == Page.Rotate && yawStep.isOpen()));

    private final Timer tick = new Timer();
    public Vec3 directionVec = null;

    public Aura() {
        super("Aura", Category.Combat);
        setChinese("杀戮光环");
        INSTANCE = this;
    }

    public static void doRender(PoseStack stack, float partialTicks, Entity entity, Color color, Color outlineColor, TargetESP mode) {
        switch (mode) {
            case Box ->
                    Render3DUtil.draw3DBox(((IEntity) entity).getDimensions().makeBoundingBox(new Vec3(MathUtil.interpolate(entity.xOld, entity.getX(), partialTicks), MathUtil.interpolate(entity.yOld, entity.getY(), partialTicks), MathUtil.interpolate(entity.zOld, entity.getZ(), partialTicks))).inflate(0, 0.1, 0), color, outlineColor, true, true);
            case Fill ->
                    Render3DUtil.draw3DBox(((IEntity) entity).getDimensions().makeBoundingBox(new Vec3(MathUtil.interpolate(entity.xOld, entity.getX(), partialTicks), MathUtil.interpolate(entity.yOld, entity.getY(), partialTicks), MathUtil.interpolate(entity.zOld, entity.getZ(), partialTicks))).inflate(0, 0.1, 0), color, outlineColor, false, true);
            case Jello -> JelloUtil.drawJello(stack, entity, color);
            case ThunderHack -> Render3DUtil.drawTargetEsp(stack, target, color);
        }
    }

    public static float getAttackCooldownProgressPerTick() {
        return (float) (1.0 / mc.player.getAttributeValue(Attributes.ATTACK_SPEED) * 20.0);
    }

    @Override
    public void onRender3D(PoseStack matrixStack) {
        if (target != null) {
            doRender(matrixStack, mc.getDeltaTracker().getGameTimeDeltaPartialTick(true), target, mode.getValue());
        }
    }

    public void doRender(PoseStack stack, float partialTicks, Entity entity, TargetESP mode) {
        switch (mode) {
            case Box ->
                    Render3DUtil.draw3DBox(((IEntity) entity).getDimensions().makeBoundingBox(new Vec3(MathUtil.interpolate(entity.xOld, entity.getX(), partialTicks), MathUtil.interpolate(entity.yOld, entity.getY(), partialTicks), MathUtil.interpolate(entity.zOld, entity.getZ(), partialTicks))).inflate(0, 0.1, 0), ColorUtil.fadeColor(color.getValue(), hitColor.getValue(), animation.get(0, animationTime.getValueInt(), ease.getValue())), ColorUtil.fadeColor(outlineColor.getValue(), hitOutlineColor.getValue(), animation.get(0, animationTime.getValueInt(), ease.getValue())), true, true);
            case Fill ->
                    Render3DUtil.draw3DBox(((IEntity) entity).getDimensions().makeBoundingBox(new Vec3(MathUtil.interpolate(entity.xOld, entity.getX(), partialTicks), MathUtil.interpolate(entity.yOld, entity.getY(), partialTicks), MathUtil.interpolate(entity.zOld, entity.getZ(), partialTicks))).inflate(0, 0.1, 0), ColorUtil.fadeColor(color.getValue(), hitColor.getValue(), animation.get(0, animationTime.getValueInt(), ease.getValue())), ColorUtil.fadeColor(outlineColor.getValue(), hitOutlineColor.getValue(), animation.get(0, animationTime.getValueInt(), ease.getValue())), false, true);
            case Jello -> JelloUtil.drawJello(stack, entity, color.getValue());
            case ThunderHack -> Render3DUtil.drawTargetEsp(stack, target, color.getValue());
        }
    }

    @Override
    public String getInfo() {
        return target == null ? null : target.getName().getString();
    }

    @EventListener
    public void onTick(ClientTickEvent event) {
        if (nullCheck()) return;
        if (timing.is(Timing.Pre) && event.isPost() || timing.is(Timing.Post) && event.isPre()) return;
        if (weaponOnly.getValue() && !EntityUtil.isHoldingWeapon(mc.player)) {
            target = null;
            return;
        }
        target = getTarget(range.getValueFloat());
        if (target == null) {
            target = getTarget(targetRange.getValueFloat());
            return;
        }
        doAura();
    }

    @EventListener
    public void onRotate(RotationEvent event) {
        if (target != null && rotate.getValue() && shouldYawStep()) {
            directionVec = getAttackVec(target);
            event.setTarget(directionVec, steps.getValueFloat(), priority.getValueFloat());
        }
    }

    @EventListener
    public void onPacket(PacketEvent.Send event) {
        if (reset.getValue()) {
            Packet<?> packet = event.getPacket();
            if (packet instanceof ServerboundSwingPacket || packet instanceof ServerboundAttackPacket) {
                tick.reset();
            }
        }
    }

    private boolean check() {
        if (Blink.INSTANCE.isOn() && Blink.INSTANCE.pauseModule.getValue()) return false;
        int at = (int) (tick.getMs() / 50);
        if (cooldownMode.getValue() == Cooldown.Vanilla) {
            at = ((ILivingEntity) mc.player).getLastAttackedTicks();
        }
        at = (int) (at * Alien.SERVER.getTPSFactor());
        if (!(Math.max(at / getAttackCooldownProgressPerTick(), 0.0F) >= cooldown.getValue())) {
            return false;
        }
        if (target instanceof LivingEntity entity && entity.hurtTime > hurtTime.getValue()) {
            return false;
        }
        return whileEating.getValue() || !mc.player.isUsingItem();
    }

    private void doAura() {
        if (!check()) {
            return;
        }
        if (rotate.getValue()) {
            Vec3 hitVec = getAttackVec(target);
            if (!faceVector(hitVec)) return;
        }
        animation.to = 1;
        animation.from = 1;

        mc.getConnection().send(new ServerboundAttackPacket(target.getId()));
        mc.player.resetAttackStrengthTicker();
        EntityUtil.swingHand(InteractionHand.MAIN_HAND, swingMode.getValue());
        tick.reset();
        if (rotate.getValue() && !shouldYawStep()) {
            Alien.ROTATION.snapBack();
        }
    }

    private Vec3 getAttackVec(Entity entity) {
        return MathUtil.getClosestPointToBox(mc.player.getEyePosition(), entity.getBoundingBox());
    }

    private boolean shouldYawStep() {
        if (!whenElytra.getValue() && (mc.player.isFallFlying() || ElytraFly.INSTANCE.isOn() && ElytraFly.INSTANCE.isFallFlying()))
            return false;
        return yawStep.getValue() && !Velocity.INSTANCE.noRotation();
    }

    public boolean faceVector(Vec3 directionVec) {
        if (!shouldYawStep()) {
            Alien.ROTATION.lookAt(directionVec);
            return true;
        } else {
            this.directionVec = directionVec;
            if (Alien.ROTATION.inFov(directionVec, fov.getValueFloat())) {
                return true;
            }
        }
        return !checkFov.getValue();
    }

    public Entity getTarget(double range) {
        Entity target = null;
        double distance = range;
        double maxHealth = 36.0;
        for (Entity entity : Alien.THREAD.getEntities()) {
            if (!isEnemy(entity)) continue;
            Vec3 hitVec = getAttackVec(entity);
            if (mc.player.getEyePosition().distanceTo(hitVec) > range) continue;
            if (!mc.player.hasLineOfSight(entity) && mc.player.getEyePosition().distanceTo(hitVec) > wallRange.getValue()) {
                continue;
            }
            if (!CombatUtil.isValid(entity)) continue;

            if (target == null) {
                target = entity;
                distance = mc.player.getEyePosition().distanceTo(hitVec);
                maxHealth = EntityUtil.getHealth(entity);
            } else {
                if (armorLow.getValue() && entity instanceof Player && EntityUtil.isArmorLow((Player) entity, 10)) {
                    target = entity;
                    break;
                }
                if (targetMode.getValue() == TargetMode.HEALTH && EntityUtil.getHealth(entity) < maxHealth) {
                    target = entity;
                    maxHealth = EntityUtil.getHealth(entity);
                    continue;
                }
                if (targetMode.getValue() == TargetMode.DISTANCE && mc.player.getEyePosition().distanceTo(hitVec) < distance) {
                    target = entity;
                    distance = mc.player.getEyePosition().distanceTo(hitVec);
                }
            }
        }
        return target;
    }

    private boolean isEnemy(Entity entity) {
        if (entity instanceof Slime) return Slimes.getValue();
        if (entity instanceof Player) return Players.getValue();
        if (entity instanceof Villager || entity instanceof WanderingTrader) return Villagers.getValue();
        if (entity instanceof Animal) return Animals.getValue();
        if (entity instanceof Mob) return Mobs.getValue();
        return false;
    }

    public enum TargetESP {
        Fill,
        Box,
        Jello,
        ThunderHack,
        None
    }

    public enum Cooldown {
        Vanilla,
        Delay
    }

    private enum TargetMode {
        DISTANCE,
        HEALTH,
    }

    public enum Page {
        General,
        Rotate,
        Target,
        Render
    }

    public enum Mode {
        Mace,
        Axe,
        Sword,
    }
}