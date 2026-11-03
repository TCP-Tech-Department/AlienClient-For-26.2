package dev.luminous.asm.accessors;

import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 26.2: Vec3 仍是普通 final 类，字段 {@code public final double x/y/z} 名字未变，
 * {@link Mutable} 会去掉 final 才能写入。本 accessor 当前没有调用方。
 */
@Mixin(Vec3.class)
public interface IVec3d {

    @Mutable
    @Accessor("x")
    void setX(double x);

    @Mutable
    @Accessor("y")
    void setY(double y);

    @Mutable
    @Accessor("z")
    void setZ(double z);
}
