package dev.luminous.asm.mixins;

import dev.luminous.Alien;
import dev.luminous.api.events.Event;
import dev.luminous.api.events.impl.TravelEvent;
import dev.luminous.api.utils.Wrapper;
import dev.luminous.mod.modules.impl.client.ClientSetting;
import dev.luminous.mod.modules.impl.player.InteractTweaks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 26.2 改名对照：
 * <pre>
 * canChangeIntoPose(EntityPose)      → 消失（EntityPose → Pose，且不再有该方法）；
 *                                      改为在 Player#updatePlayerPose 入口整体跳过（等价于旧版
 *                                      「canChangeIntoPose(SWIMMING) 返回 false → 整个姿势更新不执行」）
 * getBlockInteractionRange()         → blockInteractionRange()
 * getEntityInteractionRange()        → entityInteractionRange()
 * travel(Vec3)                       → travel(Vec3)（不变，参数是输入向量）
 * jump()                             → 消失，起跳统一到 LivingEntity#jumpFromGround，
 *                                      JumpEvent 已迁至 MixinLivingEntity（见文件末尾说明）
 * </pre>
 */
@Mixin(value = Player.class)
public class MixinPlayerEntity implements Wrapper {

    @Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true)
    private void poseNotCollide(CallbackInfo ci) {
        if (Player.class.cast(this) == mc.player) {
            if (!ClientSetting.INSTANCE.crawl.getValue()) {
                ci.cancel();
            }
        }
    }

    @Inject(method = "blockInteractionRange", at = @At("HEAD"), cancellable = true)
    public void getBlockInteractionRangeHook(CallbackInfoReturnable<Double> cir) {
        if (InteractTweaks.INSTANCE.reach()) {
            cir.setReturnValue(InteractTweaks.INSTANCE.blockRange.getValue());
        }
    }

    @Inject(method = "entityInteractionRange", at = @At("HEAD"), cancellable = true)
    public void getEntityInteractionRangeHook(CallbackInfoReturnable<Double> cir) {
        if (InteractTweaks.INSTANCE.reach()) {
            cir.setReturnValue(InteractTweaks.INSTANCE.entityRange.getValue());
        }
    }

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void onTravelPre(Vec3 movementInput, CallbackInfo ci) {
        Player player = Player.class.cast(this);
        if (player != mc.player)
            return;

        TravelEvent event = TravelEvent.get(Event.Stage.Pre, player);
        Alien.EVENT_BUS.post(event);
        if (event.isCancelled()) {
            ci.cancel();
            event = TravelEvent.get(Event.Stage.Post, player);
            Alien.EVENT_BUS.post(event);
        }
    }

    @Inject(method = "travel", at = @At("RETURN"))
    private void onTravelPost(Vec3 movementInput, CallbackInfo ci) {
        Player player = Player.class.cast(this);
        if (player != mc.player)
            return;

        TravelEvent event = TravelEvent.get(Event.Stage.Post, player);
        Alien.EVENT_BUS.post(event);
    }

    /*
     * 26.2: 原注入点 Player#jump() 已消失 —— 26.2 把起跳统一成 LivingEntity#jumpFromGround()
     * （LivingEntity.java:2381，Player 不再覆写 jump；Player 里只剩 protected int jumpTriggerTime，
     *  它在 aiStep 里递减，与 JumpEvent 无关）。
     * 因此 JumpEvent 的 Pre/Post 已经迁到本 slice 的 MixinLivingEntity#onJumpPre / #onJumpPost
     * （@Inject method = "jumpFromGround" HEAD/RETURN），RotationManager#onJump 的
     * movementSync 旋转修正语义保持不变；此处不再重复注入，避免同一方法被注入两次。
     */
}
