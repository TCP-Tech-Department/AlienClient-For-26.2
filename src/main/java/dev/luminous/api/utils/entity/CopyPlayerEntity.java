package dev.luminous.api.utils.entity;

import com.mojang.authlib.GameProfile;
import dev.luminous.api.utils.math.PredictUtil;
import dev.luminous.asm.accessors.ILivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.component.ResolvableProfile;

import java.util.ArrayList;

import static dev.luminous.api.utils.Wrapper.mc;

public class CopyPlayerEntity extends Player {

    private final boolean onGround;

    public CopyPlayerEntity(Player player) {
        this(player, false, 0, 0, 0, false, false, false, false);
    }

    public CopyPlayerEntity(Player player, boolean effect) {
        super(mc.level, new GameProfile(player.getGameProfile().id(), player.getGameProfile().name()));
        snapTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        xo = player.xo;
        yo = player.yo;
        zo = player.zo;

        yBodyRot = player.yBodyRot;
        yHeadRot = player.yHeadRot;

        attackAnim = player.attackAnim;
        swingTime = player.swingTime;
        walkAnimation.setSpeed(player.walkAnimation.speed());

        ((ILivingEntity) this).setLeaningPitch(((ILivingEntity) player).getLeaningPitch());
        ((ILivingEntity) this).setLastLeaningPitch(((ILivingEntity) player).getLeaningPitch());
        wasTouchingWater = player.isInWater();
        setShiftKeyDown(player.isShiftKeyDown());
        setPose(player.getPose());
        setSharedFlag(FLAG_FALL_FLYING, player.isFallFlying());
        onGround = (player.onGround());
        setOnGround(onGround);
        setDeltaMovement(player.getDeltaMovement());
        getInventory().replaceWith(player.getInventory());

        if (effect) {
            for (MobEffectInstance se : new ArrayList<>(player.getActiveEffects())) {
                addEffect(se);
            }
        }
        setAbsorptionAmount(player.getAbsorptionAmount());
        setHealth(player.getHealth());
        setBoundingBox(player.getBoundingBox());
    }

    public CopyPlayerEntity(Player player, boolean effect, double maxMotionY, int ticks, int simulation, boolean step, boolean doubleStep, boolean jump, boolean inBlockPause) {
        this(player, effect);
        if (ticks > 0) {
            setPos(PredictUtil.getPos(player, maxMotionY, ticks, simulation, step, doubleStep, jump, inBlockPause));
        }
    }

    @Override
    public boolean onGround() {
        return onGround;
    }

    @Override
    public GameType gameMode() {
        return GameType.DEFAULT_MODE;
    }

    @Override
    public ResolvableProfile getProfile() {
        return ResolvableProfile.createResolved(this.getGameProfile());
    }
}
