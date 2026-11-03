package dev.luminous.asm.mixins.sodium;

import net.minecraft.client.renderer.block.ModelBlockRenderer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * TODO(26.2): 等待 Sodium 26.2 版本。
 * <p>
 * Sodium 尚未发布 26.2 版本，{@code build.gradle} 里 sodium 依赖已停用，因此本类不能再引用任何
 * {@code net.caffeinemc.mods.sodium.*} 内部类（原 {@code BlockOcclusionCache} 目标已移除）。
 * 文件与类保留，方法体清空；{@code @Mixin} 暂时挂到语义最接近的原版类
 * {@link ModelBlockRenderer} 上，保证 mixin config 仍能正常解析。
 * <p>
 * 原功能：{@code BlockOcclusionCache#shouldDrawSide} 上 {@code @ModifyReturnValue}，Xray 下强制绘制
 * 被遮挡的面。该功能已被 {@code MixinBlockModelRenderer#onShouldDrawSide}
 * （原版 {@code ModelBlockRenderer#shouldRenderFace}）覆盖，Sodium 版降级为空不影响现有行为。
 * <p>
 * 恢复方式：Sodium 26.2 发布后恢复依赖，并把
 * {@code @Mixin(value = BlockOcclusionCache.class, remap = false)} 与原来的 ModifyReturnValue 补回。
 * （{@code alien.sodium.mixins.json} 仍列着本类，该 config 为 {@code "required": false}。）
 */
@Mixin(ModelBlockRenderer.class)
public abstract class SodiumBlockOcclusionCacheMixin {
}
