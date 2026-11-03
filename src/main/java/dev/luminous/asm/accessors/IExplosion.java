package dev.luminous.asm.accessors;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/*
 * 26.2: net.minecraft.world.level.Explosion 由 class 改为 interface，
 * 真正的实现类是 {@link ServerExplosion}，字段布局也随之变化：
 *   x / y / z      -> center（private final Vec3）
 *   power          -> radius（private final float）
 *   entity         -> source（private final @Nullable Entity）
 *   world          -> level（private final ServerLevel）
 *   damageSource   -> damageSource（private final DamageSource）
 * 26.2 的 ServerExplosion 已经公开了 getDamageSource() / center() / radius() / level()，
 * 与 accessor 同名会冲突，因此 getDamageSource() 不再由本类提供（调用方直接用公开方法即可）。
 *
 * 注意：这里是**抽象类**而不是 interface —— Mixin 要求 interface 型 mixin 的目标也必须是 interface，
 * 而 ServerExplosion 是普通类，用 interface 声明会在启动时报
 * "@Mixin target type mismatch: net.minecraft.world.level.ServerExplosion is not an interface"。
 *
 * 坐标 setter：26.2 的 ServerExplosion 用 private final Vec3 center 而不是三个 double 字段，
 * 所以 setX/setY/setZ 只能是「读 center → 换一个分量 → setCenter(new Vec3(...))」，
 * 对外语义（把爆炸中心移到某坐标）与旧版一致，只是实现从原地写变成了整体替换。
 * 字段都是 private final，@Mutable 是必要的。
 * 本 accessor 目前没有任何调用方（Blink/Speed 等已改用 ClientboundExplodePacket record）。
 */
@Mixin(ServerExplosion.class)
public abstract class IExplosion {

    @Mutable
    @Accessor("center")
    public abstract void setCenter(Vec3 center);

    @Accessor("center")
    public abstract Vec3 getCenter();

    @Mutable
    @Accessor("radius")
    public abstract void setPower(float power);

    @Accessor("radius")
    public abstract float getPower();

    @Mutable
    @Accessor("source")
    public abstract void setEntity(Entity entity);

    @Accessor("source")
    public abstract Entity getEntity();

    @Accessor("level")
    public abstract ServerLevel getWorld();

    public void setX(double x) {
        Vec3 center = this.getCenter();
        this.setCenter(new Vec3(x, center.y, center.z));
    }

    public void setY(double y) {
        Vec3 center = this.getCenter();
        this.setCenter(new Vec3(center.x, y, center.z));
    }

    public void setZ(double z) {
        Vec3 center = this.getCenter();
        this.setCenter(new Vec3(center.x, center.y, z));
    }
}
