package dev.luminous.api.utils.entity;

import net.minecraft.world.entity.player.Player;

public class PlayerEntityPredict {
    public final Player player;
    public final Player predict;

    public PlayerEntityPredict(Player player, double maxMotionY, int ticks, int simulation, boolean step, boolean doubleStep, boolean jump, boolean inBlockPause) {
        this.player = player;
        if (ticks > 0) {
            predict = new CopyPlayerEntity(player, true, maxMotionY, ticks, simulation, step, doubleStep, jump, inBlockPause);
        } else {
            predict = player;
        }
    }
}
