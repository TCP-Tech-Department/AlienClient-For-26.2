package dev.luminous.asm.accessors;

import com.mojang.blaze3d.platform.NativeImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(NativeImage.class)
public interface INativeImage {
    /**
     * 26.2：{@code NativeImage} 的像素指针字段由 {@code pointer} 改名为 {@code pixels}。
     */
    @Accessor("pixels")
    long getPointer();
}
