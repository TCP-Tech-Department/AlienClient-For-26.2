package dev.luminous.asm.accessors;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 26.2：{@code LevelRenderer.entityOutlineTarget} 是 private final 的持久帧缓冲，
 * 公开的 {@code entityOutlineTarget()} 只返回 frame graph 里的临时句柄（每帧末尾被
 * {@code targets.clear()} 清空），后处理链在帧外读不到。
 *
 * <p>26.2 的原版实体发光流程是：outline pass 把选中实体的剪影写进这个 target，
 * 再由 {@code minecraft:entity_outline} 链（sobel + 两次 box blur）模糊后写回，
 * 最后由 {@code GameRenderer#render → LevelRenderer#doEntityOutline()} 混合回主 target。
 * Alien 的描边/发光后处理把这份结果当成 “In” 采样源，因此需要拿到持久字段本身。
 */
@Mixin(LevelRenderer.class)
public interface ILevelRenderer {

    @Accessor("entityOutlineTarget")
    RenderTarget alien$getEntityOutlineTarget();
}
