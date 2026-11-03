package dev.luminous.mod.modules.impl.misc;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.PlaySoundEvent;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;

public class NoSound extends Module {
    // 26.2：SoundEvent 变成 record(Identifier location, ...)，不再有 getId()；
    // 判断声音改为比较 SoundInstance#getIdentifier() 与 SoundEvent#location()。
    static final ArrayList<SoundEvent> armor = new ArrayList<>();
    public static NoSound INSTANCE;

    static {
        armor.add(SoundEvents.ARMOR_EQUIP_NETHERITE.value());
        armor.add(SoundEvents.ARMOR_EQUIP_TURTLE.value());
        armor.add(SoundEvents.ARMOR_EQUIP_CHAIN.value());
        armor.add(SoundEvents.ARMOR_EQUIP_ELYTRA.value());
        armor.add(SoundEvents.ARMOR_EQUIP_DIAMOND.value());
        armor.add(SoundEvents.ARMOR_EQUIP_GOLD.value());
        armor.add(SoundEvents.ARMOR_EQUIP_IRON.value());
        armor.add(SoundEvents.ARMOR_EQUIP_LEATHER.value());
        armor.add(SoundEvents.ARMOR_EQUIP_GENERIC.value());
    }

    private final BooleanSetting equip =
            add(new BooleanSetting("ArmorEquip", true));
    private final BooleanSetting explode =
            add(new BooleanSetting("Explode", true));
    private final BooleanSetting attack =
            add(new BooleanSetting("Attack", true));
    private final BooleanSetting teleport =
            add(new BooleanSetting("Teleport", true));
    private final BooleanSetting throwConfig =
            add(new BooleanSetting("Throw", true));
    private final BooleanSetting potion =
            add(new BooleanSetting("Potion", true));
    private final BooleanSetting elytra =
            add(new BooleanSetting("Elytra", true));

    public NoSound() {
        super("NoSound", Category.Misc);
        setChinese("去除声音");
        INSTANCE = this;
    }

    private static boolean isSound(SoundInstance sound, SoundEvent event) {
        return sound != null && sound.getIdentifier().equals(event.location());
    }

    @EventListener
    public void onPlaySound(PlaySoundEvent event) {
        if (equip.getValue()) {
            for (SoundEvent se : armor) {
                if (isSound(event.sound, se)) {
                    event.cancel();
                    return;
                }
            }
        }
        if (explode.getValue()) {
            if (isSound(event.sound, SoundEvents.GENERIC_EXPLODE.value()) || isSound(event.sound, SoundEvents.DRAGON_FIREBALL_EXPLODE)) {
                event.cancel();
                return;
            }
        }
        if (attack.getValue()) {
            if (isSound(event.sound, SoundEvents.PLAYER_ATTACK_WEAK) || isSound(event.sound, SoundEvents.PLAYER_ATTACK_KNOCKBACK) || isSound(event.sound, SoundEvents.PLAYER_ATTACK_STRONG)) {
                event.cancel();
            }
        }
        if (teleport.getValue()) {
            if (isSound(event.sound, SoundEvents.PLAYER_TELEPORT)) {
                event.cancel();
            }
        }
        if (potion.getValue()) {
            if (isSound(event.sound, SoundEvents.SPLASH_POTION_BREAK)) {
                event.cancel();
            }
        }
        if (elytra.getValue()) {
            if (isSound(event.sound, SoundEvents.ELYTRA_FLYING)) {
                event.cancel();
            }
        }
        if (throwConfig.getValue()) {
            if (
                    isSound(event.sound, SoundEvents.ENDER_PEARL_THROW)
                            || isSound(event.sound, SoundEvents.EGG_THROW)
                            || isSound(event.sound, SoundEvents.EXPERIENCE_BOTTLE_THROW)
                            || isSound(event.sound, SoundEvents.SNOWBALL_THROW)
                            || isSound(event.sound, SoundEvents.SPLASH_POTION_THROW)
            ) {
                event.cancel();
            }
        }
    }
}
