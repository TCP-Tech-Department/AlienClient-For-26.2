package dev.luminous.mod.modules.impl.misc;

import dev.luminous.Alien;
import dev.luminous.mod.modules.Module;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public class Friend extends Module {
    public static Friend INSTANCE;

    public Friend() {
        super("Friend", Category.Misc);
        setChinese("好友");
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        if (nullCheck()) {
            disable();
            return;
        }
        HitResult target = mc.hitResult;
        if (target instanceof EntityHitResult entityHitResult && entityHitResult.getEntity() instanceof Player player) {
            Alien.FRIEND.friend(player);
        }
        disable();
    }
}