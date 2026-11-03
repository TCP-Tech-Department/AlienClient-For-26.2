package dev.luminous.asm.accessors;

import net.minecraft.world.entity.WalkAnimationState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 26.2: {@code WalkAnimationState}（旧 {@code LimbAnimator}）的字段为
 * {@code speedOld / speed / position / positionScale}，全部 private，
 * 公开 API 只有 {@code setSpeed} / {@code update} / {@code stop} 以及只读的
 * {@code speed()} / {@code position()}。
 * <p>
 * 没有 setter 可以「把步态相位搬过来」：FakePlayer 需要和 1.21.1 一样执行
 * {@code limbAnimator.pos = player.limbAnimator.getPos()}，因此这里直接用 @Accessor
 * 读写 private 字段 {@code position}（该字段不是 final，无需 @Mutable）。
 */
@Mixin(WalkAnimationState.class)
public interface IWalkAnimationState {
    @Accessor("position")
    float alienClient$getPosition();

    @Accessor("position")
    void alienClient$setPosition(float position);
}
