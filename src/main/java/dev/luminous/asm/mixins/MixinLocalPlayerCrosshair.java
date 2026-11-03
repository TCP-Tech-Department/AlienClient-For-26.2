package dev.luminous.asm.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.luminous.api.utils.world.InteractUtil;
import dev.luminous.mod.modules.impl.player.Freecam;
import dev.luminous.mod.modules.impl.player.InteractTweaks;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Predicate;

/**
 * 26.2: {@code GameRenderer#findCrosshairTarget(Entity, double, double, float)} 已删除，
 * 准星拾取迁到 {@code Minecraft#pick(float)} → {@code LocalPlayer#raycastHitResult(float, Entity)}，
 * 其中 block/entity 的射程裁剪在 {@code LocalPlayer#pick(Entity, double, double, float)}（private static）。
 * <p>
 * 旧的 {@code MixinGameRenderer#findCrosshairTarget}（整体重写原版方法 + 三个模块钩子）在这里按原语义重建：
 * <ol>
 *   <li>{@link Freecam} 开启 → 直接用假摄像机位置/朝向做一次拾取并返回（原 cir.setReturnValue）；</li>
 *   <li>{@link InteractTweaks#ghostHand()} → 只在 block 射线（{@code Entity#pick}）期间置
 *       {@code InteractTweaks.isActive}，对应 {@code MixinWorld#getBlockState} 里的 bedrock → air；</li>
 *   <li>{@link InteractTweaks#noEntityTrace()} → 丢掉 entity 命中。
 *       26.2 多了一条「武器自带 attack_range 组件」的捷径拾取，也要一并丢掉，
 *       否则拿着带该组件的武器时仍然会锁定实体。</li>
 * </ol>
 * 其余逻辑（先 block 后 entity、取最近命中、超出交互距离降级为 miss）26.2 原版
 * {@code LocalPlayer#pick} / {@code #filterHitResult} 已经与 1.21.1 的 findCrosshairTarget 等价，不再重写。
 */
@Mixin(LocalPlayer.class)
public class MixinLocalPlayerCrosshair {

    @Inject(
            method = "raycastHitResult(FLnet/minecraft/world/entity/Entity;)Lnet/minecraft/world/phys/HitResult;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void alienClient$freecamHitResult(float partialTicks, Entity cameraEntity, CallbackInfoReturnable<HitResult> cir) {
        if (Freecam.INSTANCE.isOn()) {
            cir.setReturnValue(InteractUtil.getRtxTarget(
                    Freecam.INSTANCE.getFakeYaw(),
                    Freecam.INSTANCE.getFakePitch(),
                    Freecam.INSTANCE.getFakeX(),
                    Freecam.INSTANCE.getFakeY(),
                    Freecam.INSTANCE.getFakeZ()));
        }
    }

    /**
     * 26.2 新增：物品的 {@code ATTACK_RANGE} 组件会先做一次 entity 拾取，
     * NoEntityTrace 开启时直接返回 null，让流程回落到 block/entity 的常规拾取（那里同样被拦截）。
     */
    @WrapOperation(
            method = "raycastHitResult",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/component/AttackRange;getClosesetHit(Lnet/minecraft/world/entity/Entity;FLjava/util/function/Predicate;)Lnet/minecraft/world/phys/HitResult;"
            ),
            require = 0
    )
    private HitResult alienClient$noEntityTraceAttackRange(AttackRange range, Entity attacker, float partialTicks,
                                                           Predicate<Entity> matching, Operation<HitResult> original) {
        if (InteractTweaks.INSTANCE.noEntityTrace()) {
            return null;
        }
        return original.call(range, attacker, partialTicks, matching);
    }

    /**
     * ghostHand：原实现在 {@code camera.raycast(...)} 前后开关 {@code isActive}，
     * 26.2 对应的 block 射线就是 {@code cameraEntity.pick(maxDistance, partialTicks, false)}。
     */
    @WrapOperation(
            method = "pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;pick(DFZ)Lnet/minecraft/world/phys/HitResult;"
            )
    )
    private static HitResult alienClient$ghostHandBlockPick(Entity cameraEntity, double range, float partialTicks,
                                                            boolean withLiquids, Operation<HitResult> original) {
        InteractTweaks.INSTANCE.isActive = InteractTweaks.INSTANCE.ghostHand();
        try {
            return original.call(cameraEntity, range, partialTicks, withLiquids);
        } finally {
            InteractTweaks.INSTANCE.isActive = false;
        }
    }

    /**
     * noEntityTrace：原实现整段跳过 entity 射线，这里直接让 entity 拾取返回 null，
     * 原版随后会落到 {@code filterHitResult(blockHitResult, ...)}，结果一致。
     */
    @WrapOperation(
            method = "pick(Lnet/minecraft/world/entity/Entity;DDF)Lnet/minecraft/world/phys/HitResult;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/projectile/ProjectileUtil;getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;"
            )
    )
    private static EntityHitResult alienClient$noEntityTrace(Entity except, Vec3 from, Vec3 to, AABB box,
                                                             Predicate<Entity> matching, double maxValue,
                                                             Operation<EntityHitResult> original) {
        if (InteractTweaks.INSTANCE.noEntityTrace()) {
            return null;
        }
        return original.call(except, from, to, box, matching, maxValue);
    }
}
