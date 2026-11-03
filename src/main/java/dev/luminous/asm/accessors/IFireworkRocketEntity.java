package dev.luminous.asm.accessors;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/*
 * 26.2 映射（Yarn 1.21.1 -> Mojang 26.2）：
 *   shooter 字段            -> attachedToEntity（private @Nullable LivingEntity）
 *   wasShotByEntity()       -> isAttachedToEntity()（private boolean，判断是否附着在实体上）
 *   explodeAndRemove()      -> 无同名方法；客户端等价物是 handleEntityEvent((byte) 17)（生成烟花粒子）
 *                              + discard()。26.2 的 explode(ServerLevel) 只能在服务端执行。
 *
 * 注意：这个接口里**不能有 default 方法**。Mixin 会按「有没有具体方法」判定 mixin 子类型，
 * 带 default 方法时会被当成普通 interface mixin，启动即报
 * "@Mixin target type mismatch: ...FireworkRocketEntity is not an interface"。
 * 因此原本的 hookExplodeAndRemove() 已下移到调用方 RocketExtend#explodeAndRemove(...)。
 */
@Mixin(FireworkRocketEntity.class)
public interface IFireworkRocketEntity {

    @Accessor("attachedToEntity")
    LivingEntity getShooter();

    @Invoker("isAttachedToEntity")
    boolean hookWasShotByEntity();
}
