package dev.luminous.api.utils.combat;

import dev.luminous.Alien;
import dev.luminous.api.utils.Wrapper;
import dev.luminous.api.utils.math.MathUtil;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.world.BlockUtil;
import dev.luminous.mod.modules.impl.client.AntiCheat;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

public class CombatUtil implements Wrapper {
    public static final Timer breakTimer = new Timer();
    public static boolean terrainIgnore = false;
    public static BlockPos modifyPos;
    public static BlockState modifyBlockState = Blocks.AIR.defaultBlockState();

    public static List<Player> getEnemies(double range) {
        List<Player> list = new ArrayList<>();
        for (AbstractClientPlayer player : Alien.THREAD.getPlayers()) {
            if (!isValid(player, range)) continue;
            list.add(player);
        }
        return list;
    }

    public static void attackCrystal(BlockPos pos, boolean rotate, boolean eatingPause) {
        attackCrystal(new AABB(pos), rotate, eatingPause);
    }

    public static void attackCrystal(AABB box, boolean rotate, boolean eatingPause) {
        for (EndCrystal entity : BlockUtil.getEndCrystals(box)) {
            attackWithDelay(entity, rotate, eatingPause);
        }
    }

    public static void attackWithDelay(Entity entity, boolean rotate, boolean usingPause) {
        if (!CombatUtil.breakTimer.passed((long) (AntiCheat.INSTANCE.attackDelay.getValue() * 1000))) return;
        if (usingPause && mc.player.isUsingItem())
            return;
        attack(entity, rotate);
    }

    public static void attack(Entity entity, boolean rotate) {
        if (entity != null) {
            Vec3 attackVec = MathUtil.getClosestPointToBox(mc.player.getEyePosition(), entity.getBoundingBox());
            if (mc.player.getEyePosition().distanceTo(attackVec) > AntiCheat.INSTANCE.ieRange.getValue()) return;
            CombatUtil.breakTimer.reset();
            if (rotate && AntiCheat.INSTANCE.attackRotate.getValue())
                Alien.ROTATION.lookAt(attackVec);
            mc.getConnection().send(new ServerboundAttackPacket(entity.getId()));
            mc.player.resetAttackStrengthTicker();
            EntityUtil.swingHand(InteractionHand.MAIN_HAND, AntiCheat.INSTANCE.attackSwing.getValue());
            if (rotate && AntiCheat.INSTANCE.attackRotate.getValue()) {
                Alien.ROTATION.snapBack();
            }
        }
    }

    public static boolean isntValid(Entity entity, double range) {
        return !isValid(entity, range);
    }

    public static boolean isValid(Entity entity, double range) {
        boolean invalid = entity == null || !entity.isAlive() || entity.equals(mc.player) || entity instanceof Player player && Alien.FRIEND.isFriend(player) || mc.player.position().distanceTo(entity.position()) > range;

        return !invalid;
    }

    public static boolean isValid(Entity entity) {
        boolean invalid = entity == null || !entity.isAlive() || entity.equals(mc.player) || entity instanceof Player player && Alien.FRIEND.isFriend(player);

        return !invalid;
    }

    public static Player getClosestEnemy(double distance) {
        Player closest = null;

        for (Player player : getEnemies(distance)) {
            if (closest == null) {
                closest = player;
                continue;
            }

            if (!(mc.player.distanceToSqr(player.position()) < mc.player.distanceToSqr(closest))) continue;

            closest = player;
        }
        return closest;
    }
}
