package dev.luminous.mod.modules.impl.render;

import dev.luminous.api.events.Event;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.api.events.impl.ParticleEvent;
import dev.luminous.api.events.impl.RenderEntityEvent;
import dev.luminous.api.events.impl.TickEntityEvent;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import net.minecraft.client.particle.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.AbstractThrownPotion;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEgg;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;

public class NoRender extends Module {
    public static NoRender INSTANCE;
    public final BooleanSetting fastItem = add(new BooleanSetting("2DItem", false).setParent());
    public final BooleanSetting castShadow = add(new BooleanSetting("CastShadow", true, fastItem::isOpen));
    public final BooleanSetting renderSidesOfItems = add(new BooleanSetting("RenderSidesOfItems", false, fastItem::isOpen));
    public final BooleanSetting potionsIcon = add(new BooleanSetting("PotionsIcon", false));
    public final BooleanSetting weather = add(new BooleanSetting("Weather", true));
    public final BooleanSetting invisible = add(new BooleanSetting("Invisible", false));
    public final BooleanSetting lightsUpdate = add(new BooleanSetting("LightsUpdate", false));
    public final BooleanSetting potions = add(new BooleanSetting("Potions", true));
    public final BooleanSetting xp = add(new BooleanSetting("XP", true));
    public final BooleanSetting arrows = add(new BooleanSetting("Arrows", false));
    public final BooleanSetting eggs = add(new BooleanSetting("Eggs", false));
    public final BooleanSetting item = add(new BooleanSetting("Items", false));
    public final BooleanSetting armorParts = add(new BooleanSetting("ArmorParts", false));
    public final BooleanSetting armorTrim = add(new BooleanSetting("ArmorTrim", false));
    public final BooleanSetting armorGlint = add(new BooleanSetting("ArmorGlint", false));
    public final BooleanSetting hurtCam = add(new BooleanSetting("HurtCam", true));
    public final BooleanSetting fireOverlay = add(new BooleanSetting("FireOverlay", true));
    public final BooleanSetting waterOverlay = add(new BooleanSetting("WaterOverlay", true));
    public final BooleanSetting blockOverlay = add(new BooleanSetting("BlockOverlay", true));
    public final BooleanSetting portal = add(new BooleanSetting("Portal", true));
    public final BooleanSetting totem = add(new BooleanSetting("Totem", true));
    public final BooleanSetting nausea = add(new BooleanSetting("Nausea", true));
    public final BooleanSetting blindness = add(new BooleanSetting("Blindness", true));
    public final BooleanSetting fog = add(new BooleanSetting("Fog", false));
    public final BooleanSetting darkness = add(new BooleanSetting("Darkness", true));
    public final BooleanSetting fireEntity = add(new BooleanSetting("EntityFire", true));
    public final BooleanSetting antiTitle = add(new BooleanSetting("Title", false));
    public final BooleanSetting antiPlayerCollision = add(new BooleanSetting("PlayerCollision", true));
    public final BooleanSetting effect = add(new BooleanSetting("Effect", true));
    public final BooleanSetting elderGuardian = add(new BooleanSetting("Guardian", false));
    public final BooleanSetting explosions = add(new BooleanSetting("Explosions", true));
    public final BooleanSetting campFire = add(new BooleanSetting("CampFire", false));
    public final BooleanSetting fireworks = add(new BooleanSetting("Fireworks", false));
    public final BooleanSetting guiToast = add(new BooleanSetting("GuiToast", false));

    public NoRender() {
        super("NoRender", "Disables all overlays and potion effects.", Category.Render);
        setChinese("禁用渲染");
        INSTANCE = this;
    }

    @EventListener
    public void onPacketReceive(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundSetTitleTextPacket && antiTitle.getValue()) {
            event.setCancelled(true);
        }
    }

    @EventListener
    public void onRender(TickEntityEvent event) {
        cancelEvent(event.getEntity(), event);
    }

    @EventListener
    public void onRender(RenderEntityEvent event) {
        cancelEvent(event.getEntity(), event);
    }

    private void cancelEvent(Entity entity, Event event) {
        // 26.2: thrown 实体改名/移包：PotionEntity→AbstractThrownPotion（26.2 拆成 splash/lingering 两个子类）、
        //       ExperienceBottleEntity→ThrownExperienceBottle、EggEntity→ThrownEgg
        if (entity instanceof AbstractThrownPotion && potions.getValue()) {
            event.cancel();
        } else if (entity instanceof ThrownExperienceBottle && xp.getValue()) {
            event.cancel();
        } else if (entity instanceof Arrow && arrows.getValue()) {
            event.cancel();
        } else if (entity instanceof ThrownEgg && eggs.getValue()) {
            event.cancel();
        } else if (entity instanceof ItemEntity && item.getValue()) {
            event.cancel();
        }
    }

    @EventListener
    public void onParticle(ParticleEvent event) {
        // 26.2 粒子类改名：ElderGuardianAppearanceParticle→ElderGuardianParticle、
        // ExplosionLargeParticle→HugeExplosionParticle、FireworksSparkParticle.*→FireworkParticles.*
        if (elderGuardian.getValue() && event.particle instanceof ElderGuardianParticle) {
            event.cancel();
        } else if (explosions.getValue() && event.particle instanceof HugeExplosionParticle) {
            event.cancel();
        } else if (campFire.getValue() && event.particle instanceof CampfireSmokeParticle) {
            event.cancel();
        } else if (fireworks.getValue() && (event.particle instanceof FireworkParticles.Starter || event.particle instanceof FireworkParticles.OverlayParticle)) {
            // 26.2: 火花粒子类 FireworkParticles$SparkParticle 在 26.2 是 private（外部无法 instanceof，
            // 也不该靠反射猜）。但它<b>唯一的生成者</b>是 public 的 FireworkParticles.Starter：
            //   Starter#tick → createParticle/createParticleBall/... 全部走
            //   this.engine.createParticle(ParticleTypes.FIREWORK, ...) → SparkProvider → new SparkParticle
            // 而 Starter 自身由 ClientLevel#addFireworkParticle 交给 ParticleEngine#add，
            // 正好被 MixinParticleManager 的 add HEAD 拦下。因此拦掉 Starter 就等于
            // 「本次烟花不再生成任何火花」，再拦 OverlayParticle（原 Flash）即可让烟花完全没有残留。
            event.cancel();
        } else if (effect.getValue() && event.particle instanceof SpellParticle) {
            event.cancel();
        }
    }
}