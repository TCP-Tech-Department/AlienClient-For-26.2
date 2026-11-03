package dev.luminous.asm.mixins;

import dev.luminous.mod.modules.impl.player.FreeLook;
import dev.luminous.mod.modules.impl.player.Freecam;
import dev.luminous.mod.modules.impl.render.CameraClip;
import dev.luminous.mod.modules.impl.render.MotionCamera;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/**
 * 26.2: {@code Camera#update} 的签名变为 {@code update(DeltaTracker)}，相机对齐逻辑全部搬进
 * {@code Camera#alignWithEntity(float)}：
 * <ul>
 *   <li>{@code moveBy(FFF)} → {@code move(FFF)}（ordinal 0 仍是第三人称拉远那一处）；</li>
 *   <li>{@code clipToSpace(F)F} → {@code getMaxZoom(F)F}；</li>
 *   <li>{@code setPos(DDD)} → {@code setPosition(DDD)}，{@code setRotation(FF)} 名字不变；</li>
 *   <li>{@code thirdPerson} 字段 → {@code detached}。</li>
 * </ul>
 */
@Mixin(Camera.class)
public abstract class MixinCamera {
    @Shadow
    private boolean detached;

    /** 下面 onClipToSpace 已经把 getMaxZoom 的返回值顶成配置距离，所以这里直接用配置值即可。 */
    @ModifyArgs(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;move(FFF)V", ordinal = 0))
    private void modifyCameraDistance(Args args) {
        if (CameraClip.INSTANCE.isOn()) {
            args.set(0, -((float) CameraClip.INSTANCE.getDistance()));
        }
    }

    /** 26.2: was {@code clipToSpace}。 */
    @Inject(method = "getMaxZoom", at = @At("HEAD"), cancellable = true)
    private void onClipToSpace(float f, CallbackInfoReturnable<Float> cir) {
        if (CameraClip.INSTANCE.isOn()) {
            cir.setReturnValue((float) CameraClip.INSTANCE.getDistance());
        }
    }

    /** 26.2: was {@code update(BlockGetter, Entity, boolean, boolean, float)}，TAIL 改挂 alignWithEntity。 */
    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void updateHook(float partialTicks, CallbackInfo ci) {
        if (Freecam.INSTANCE.isOn()) {
            this.detached = true;
        }
    }

    @ModifyArgs(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V"))
    private void setRotationHook(Args args) {
        if (Freecam.INSTANCE.isOn()) {
            args.setAll(Freecam.INSTANCE.getFakeYaw(), Freecam.INSTANCE.getFakePitch());
        } else if (FreeLook.INSTANCE.isOn()) {
            args.setAll(FreeLook.INSTANCE.getFakeYaw(), FreeLook.INSTANCE.getFakePitch());
        }
    }

    /** 26.2: was {@code Camera#setPos(DDD)}。 */
    @ModifyArgs(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setPosition(DDD)V"))
    private void setPosHook(Args args) {
        if (Freecam.INSTANCE.isOn()) {
            args.setAll(Freecam.INSTANCE.getFakeX(), Freecam.INSTANCE.getFakeY(), Freecam.INSTANCE.getFakeZ());
        } else if (MotionCamera.INSTANCE.on()) {
            args.setAll(MotionCamera.INSTANCE.getFakeX(), MotionCamera.INSTANCE.getFakeY(), MotionCamera.INSTANCE.getFakeZ());
        }
    }
}
