package dev.luminous.asm.accessors;

import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/*
 * 26.2: net.minecraft.registry.SimpleRegistry -> net.minecraft.core.MappedRegistry，
 * net.minecraft.registry.entry.RegistryEntry.Reference -> net.minecraft.core.Holder.Reference，
 * 字段 valueToEntry -> byValue（private final Map<T, Holder.Reference<T>>，IdentityHashMap）。
 * HUD 已经改用 BuiltInRegistries.MOB_EFFECT.listElements()，本 accessor 全库零调用方，
 * 但 alien.mixins.json 仍注册着它，因此保留为可加载的合法 Mixin。
 */
@Mixin(MappedRegistry.class)
public interface ISimpleRegistry<T> {
    @Accessor("byValue")
    Map<T, Holder.Reference<T>> getByValue();
}
