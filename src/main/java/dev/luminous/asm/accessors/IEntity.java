package dev.luminous.asm.accessors;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/*
 * 26.2: position 字段仍为 private Vec3，但 Entity 已经提供了 public final setPos(Vec3)，
 * 旧 accessor 的 setPos(Vec3) 会与目标类现有方法重名冲突，故删除（调用方直接 entity.setPos(vec) 即可）。
 * 现有所有调用方（ESP / Aura / AutoCrystal / LogoutSpots / TeleportLogger / PredictUtil）
 * 只使用 getDimensions()，其目标字段 dimensions 未改名。
 */
@Mixin(Entity.class)
public interface IEntity {

    @Accessor("dimensions")
    EntityDimensions getDimensions();
}
