package dev.luminous.mod.modules.impl.misc;

import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.events.impl.DeathEvent;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.math.MathUtil;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;

public class KillEffect extends Module {
    private final BooleanSetting lightning = add(new BooleanSetting("Lightning", true));
    private final BooleanSetting levelUp = add(new BooleanSetting("LevelUp", true).setParent());
    private final SliderSetting lMaxPitch = add(new SliderSetting("LMaxPitch", 1, 0, 2, 0.1, levelUp::isOpen));
    private final SliderSetting lMinPitch = add(new SliderSetting("LMinPitch", 1, 0, 2, 0.1, levelUp::isOpen));
    private final BooleanSetting trident = add(new BooleanSetting("Trident", false).setParent());
    private final SliderSetting tMaxPitch = add(new SliderSetting("TMaxPitch", 1, 0, 2, 0.1, trident::isOpen));
    private final SliderSetting tMinPitch = add(new SliderSetting("TMinPitch", 1, 0, 2, 0.1, trident::isOpen));
    private final SliderSetting factor = add(new SliderSetting("Factor", 1, 1, 10, 1));

    public KillEffect() {
        super("KillEffect", Category.Misc);
        setChinese("击杀效果");
    }

    @EventListener
    public void onPlayerDeath(DeathEvent event) {
        if (nullCheck()) {
            return;
        }
        Player player = event.getPlayer();
        if (player == null) {
            return;
        }
        for (int i = 0; i < this.factor.getValue(); ++i) {
            doEffect(player);
        }
    }

    private void doEffect(Player player) {
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        if (lightning.getValue()) {

            LightningBolt lightningEntity = new LightningBolt(EntityTypes.LIGHTNING_BOLT, mc.level);

            lightningEntity.snapTo(x, y, z);

            EntityUtil.addClientEntity(lightningEntity);
        }
        if (levelUp.getValue()) {
            mc.level.playSound(mc.player, x, y, z, SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 100f, MathUtil.random(lMinPitch.getValueFloat(), lMaxPitch.getValueFloat()));
        }

        if (trident.getValue()) {
            mc.level.playSound(mc.player, x, y, z, SoundEvents.TRIDENT_THUNDER, SoundSource.MASTER, 999.0F, MathUtil.random(tMinPitch.getValueFloat(), tMaxPitch.getValueFloat()));
        }
    }
}
