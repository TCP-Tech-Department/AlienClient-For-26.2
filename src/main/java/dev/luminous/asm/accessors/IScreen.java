package dev.luminous.asm.accessors;

import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * 26.2: Yarn 的 Drawable → Mojang 的 {@link Renderable}，
 * Screen 的 {@code drawables} 字段改名为 {@code renderables}（private final List<Renderable>）。
 * 本 accessor 当前没有调用方，方法名沿用旧名以减少调用方改动。
 */
@Mixin(Screen.class)
public interface IScreen {
    @Accessor("renderables")
    List<Renderable> getDrawables();
}
