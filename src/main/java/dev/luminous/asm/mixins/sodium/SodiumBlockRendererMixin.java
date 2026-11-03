package dev.luminous.asm.mixins.sodium;

import net.minecraft.client.renderer.block.ModelBlockRenderer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * TODO(26.2): 等待 Sodium 26.2 版本。
 * <p>
 * Sodium 未发布 26.2 版本，{@code build.gradle} 里依赖已停用，原目标
 * {@code net.caffeinemc.mods.sodium.client.render.chunk.compile.pipeline.BlockRenderer} 不可引用，
 * 因此清空方法体、去掉 Sodium 内部类引用，只保留类与文件。
 * <p>
 * 原功能：在 {@code BlockRenderer#renderModel} 里对 Xray 屏蔽的方块 {@code ci.cancel()}，
 * 跳过该方块的几何构建。26.2 原版路径上的等价实现是
 * {@code MixinBlockModelRenderer} / {@code MixinAbstractBlockRenderContext}
 * （{@code ModelBlockRenderer} 上取消），Sodium 恢复后再补回专属注入。
 * <p>
 * {@code @Mixin} 暂时挂到语义最接近的原版类 {@link ModelBlockRenderer}，保证 config 可解析。
 */
@Mixin(ModelBlockRenderer.class)
public abstract class SodiumBlockRendererMixin {
}
