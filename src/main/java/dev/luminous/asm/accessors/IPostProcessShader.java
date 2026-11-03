package dev.luminous.asm.accessors;

import com.mojang.blaze3d.buffers.GpuBuffer;
import net.minecraft.client.renderer.PostPass;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/**
 * 26.2 后处理的运行时 uniform 写入入口。
 *
 * <p>{@code PostPass} 的 {@code private final Map<String, GpuBuffer> customUniforms}
 * 对应 JSON 里每个 uniform block；{@code PostPass#addToFrame} 每帧把它逐个
 * {@code renderPass.setUniform(name, buffer)} 绑定。Alien 的动画 uniform（Time / Color /
 * Radius / BlurArea …）就是通过替换 / 覆写这里的 buffer 实现的。
 *
 * <p>注意：{@code PostPass} 构造时用 {@code createBuffer(..., USAGE_UNIFORM, ...)} 建的 buffer
 * 只有 {@code USAGE_UNIFORM(128)}，既没有 {@code USAGE_COPY_DST(8)}（{@code CommandEncoder#writeToBuffer}
 * 要求）也没有 {@code USAGE_MAP_WRITE(2)}（{@code GpuBuffer#map} 要求），所以
 * {@code ShaderManager} 会先用同样大小的 {@code USAGE_UNIFORM | USAGE_COPY_DST} buffer 替换，
 * 再逐帧 {@code writeToBuffer}。
 */
@Mixin(PostPass.class)
public interface IPostProcessShader {

    @Accessor("customUniforms")
    Map<String, GpuBuffer> alien$getCustomUniforms();
}
