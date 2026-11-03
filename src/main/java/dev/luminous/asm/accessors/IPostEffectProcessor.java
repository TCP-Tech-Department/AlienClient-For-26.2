package dev.luminous.asm.accessors;

import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/**
 * 原 {@code net.minecraft.client.gl.PostEffectProcessor} 的 passes 访问器。
 *
 * <p>26.2：{@code PostEffectProcessor} → {@code net.minecraft.client.renderer.PostChain}，
 * 字段 {@code passes}（{@code private final List<PostPass>}）仍然存在，故直接改挂。
 */
@Mixin(PostChain.class)
public interface IPostEffectProcessor {
    @Accessor("passes")
    List<PostPass> getPasses();
}
