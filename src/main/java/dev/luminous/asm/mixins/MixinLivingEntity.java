package dev.luminous.asm.mixins;

import dev.luminous.Alien;
import dev.luminous.api.events.impl.JumpEvent;
import dev.luminous.api.events.impl.LerpToEvent;
import dev.luminous.api.events.impl.SprintEvent;
import dev.luminous.mod.modules.impl.movement.ElytraFly;
import dev.luminous.mod.modules.impl.movement.NoSlow;
import dev.luminous.mod.modules.impl.movement.Velocity;
import dev.luminous.mod.modules.impl.player.AntiEffects;
import dev.luminous.mod.modules.impl.render.ViewModel;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.InterpolationHandler;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.core.Holder;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity extends Entity {
    @Final
    @Shadow
    private static AttributeModifier SPEED_MODIFIER_SPRINTING; // 26.2: was SPRINTING_SPEED_BOOST
    @Unique
    private boolean previousElytra = false;
    @Unique
    private long alienClient$lastLerp = 0L;

    public MixinLivingEntity(EntityType<? extends LivingEntity> type, Level world) {
        super(type, world);
    }

    // 26.2: was getAttributeInstance(RegistryEntry<EntityAttribute>)
    @Shadow
    public abstract @Nullable AttributeInstance getAttribute(Holder<Attribute> attribute);

    // 26.2: was getHandSwingDuration
    @Inject(method = {"getCurrentSwingDuration"}, at = {@At("HEAD")}, cancellable = true)
    private void getArmSwingAnimationEnd(final CallbackInfoReturnable<Integer> info) {
        if (ViewModel.INSTANCE.isOn() && ViewModel.INSTANCE.slowAnimation.getValue())
            info.setReturnValue(ViewModel.INSTANCE.slowAnimationVal.getValueInt());
    }

    /**
     * 26.2: {@code Player#jump()} 已移除，起跳统一到 {@code LivingEntity#jumpFromGround()}，
     * 所以 JumpEvent 的 Pre/Post 从 MixinPlayerEntity 迁到这里（1.21.1 是 Player#jump）。
     */
    @Inject(method = "jumpFromGround", at = @At("HEAD"))
    private void onJumpPre(CallbackInfo ci) {
        Alien.EVENT_BUS.post(JumpEvent.get(JumpEvent.Stage.Pre));
    }

    @Inject(method = "jumpFromGround", at = @At("RETURN"))
    private void onJumpPost(CallbackInfo ci) {
        Alien.EVENT_BUS.post(JumpEvent.get(JumpEvent.Stage.Post));
    }

    @Inject(method = "isFallFlying", at = @At("TAIL"), cancellable = true)
    public void recastOnLand(CallbackInfoReturnable<Boolean> cir) {
        boolean elytra = cir.getReturnValue();
        if (previousElytra && !elytra && ElytraFly.INSTANCE.isOn() && ElytraFly.INSTANCE.mode.is(ElytraFly.Mode.Bounce)) {
            cir.setReturnValue(ElytraFly.recastElytra(Minecraft.getInstance().player));
        }
        previousElytra = elytra;
    }

    // 26.2: was @Redirect(method = "travel", target = LivingEntity#hasStatusEffect)
    // 26.2 的缓降判定在 getEffectiveGravity() 里（travel 已拆成 travelInAir/travelInFluid/...）
    @Redirect(method = "getEffectiveGravity",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;hasEffect(Lnet/minecraft/core/Holder;)Z"),
            require = 0)
    private boolean travelEffectHook(LivingEntity instance, Holder<MobEffect> effect) {
        if (AntiEffects.INSTANCE.isOn()) {
            if (effect == MobEffects.SLOW_FALLING && AntiEffects.INSTANCE.slowFalling.getValue()) {
                return false;
            }
            if (effect == MobEffects.LEVITATION && AntiEffects.INSTANCE.levitation.getValue()) {
                return false;
            }
        }
        return instance.hasEffect(effect);
    }

    // 26.2: 新增 —— 漂浮效果在 travelInAir 里通过 getEffect(LEVITATION) 判定（旧版是 hasStatusEffect）
    @Redirect(method = "travelInAir",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/LivingEntity;getEffect(Lnet/minecraft/core/Holder;)Lnet/minecraft/world/effect/MobEffectInstance;"),
            require = 0)
    private @Nullable MobEffectInstance levitationHook(LivingEntity instance, Holder<MobEffect> effect) {
        if (AntiEffects.INSTANCE.isOn() && AntiEffects.INSTANCE.levitation.getValue() && effect == MobEffects.LEVITATION) {
            return null;
        }
        return instance.getEffect(effect);
    }

    /**
     * 26.2: 攀爬相关的两个旧注入点全部合并到这里。
     * <ul>
     *   <li>1.21.1 的 {@code applyMovementInput} / {@code applyClimbingSpeed} 在 26.2 都已消失
     *       （{@code handleOnClimbable(Vec3)} 是 private，见 LivingEntity.java:2693）；</li>
     *   <li>唯一决定「是否按攀爬处理」的判定就是入口处的 {@code this.onClimbable()}
     *       （LivingEntity.java:2694），所以在这里拦一次即可同时覆盖：
     *       {@code Velocity.noClimb}（原 applyMovementInput 的 isClimbing 拦截）与
     *       {@code NoSlow.climb}（原 applyClimbingSpeed 的 isClimbing 拦截）。</li>
     * </ul>
     * 返回 false = 不当作攀爬处理 → 不上梯子/不减速，与原两个旧回调的效果一致。
     */
    @Redirect(method = "handleOnClimbable", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;onClimbable()Z"), require = 0)
    public boolean climbingHook(LivingEntity instance) {
        if (LivingEntity.class.cast(this) == Minecraft.getInstance().player) {
            if (Velocity.INSTANCE.isOn() && Velocity.INSTANCE.noClimb.getValue()) {
                return false;
            }
            if (NoSlow.INSTANCE.climb()) {
                return false;
            }
        }
        return instance.onClimbable();
    }

    /**
     * 26.2: was @Inject(method = "updateTrackedPositionAndAngles", HEAD) —— 该方法在 26.2 消失，
     * 位置插值改由 {@link InterpolationHandler#interpolateTo} 触发。
     * 这里把 LivingEntity 构造时创建的 InterpolationHandler 换成一个带 “插值开始” 回调的实例，
     * 回调里拿到本次插值的目标位置/朝向并发出 LerpToEvent。
     */
    @Redirect(method = "<init>", at = @At(value = "NEW", target = "Lnet/minecraft/world/entity/InterpolationHandler;"))
    private InterpolationHandler alienClient$createInterpolationHandler(Entity entity) {
        return new InterpolationHandler(entity, InterpolationHandler.DEFAULT_INTERPOLATION_STEPS, handler -> {
            if (entity instanceof LivingEntity living) {
                Alien.EVENT_BUS.post(LerpToEvent.get(living, handler.position().x, handler.position().y, handler.position().z,
                        handler.yRot(), handler.xRot(), alienClient$lastLerp));
                alienClient$lastLerp = System.currentTimeMillis();
            }
        });
    }

    @Inject(method = {"setSprinting"}, at = {@At("HEAD")}, cancellable = true)
    public void setSprintingHook(boolean sprinting, CallbackInfo ci) {
        if (LivingEntity.class.cast(this) == Minecraft.getInstance().player) {
            SprintEvent event = SprintEvent.get();
            Alien.EVENT_BUS.post(event);
            if (event.isCancelled()) {
                ci.cancel();
                sprinting = event.isSprint();
                super.setSprinting(sprinting);
                // 26.2: EntityAttributes.GENERIC_MOVEMENT_SPEED -> Attributes.MOVEMENT_SPEED
                //       getAttributeInstance -> getAttribute；EntityAttributeInstance -> AttributeInstance
                AttributeInstance entityAttributeInstance = this.getAttribute(Attributes.MOVEMENT_SPEED);
                if (entityAttributeInstance != null) {
                    entityAttributeInstance.removeModifier(SPEED_MODIFIER_SPRINTING.id());
                    if (sprinting) {
                        entityAttributeInstance.addTransientModifier(SPEED_MODIFIER_SPRINTING);
                    }
                }
            }
        }
    }
}
