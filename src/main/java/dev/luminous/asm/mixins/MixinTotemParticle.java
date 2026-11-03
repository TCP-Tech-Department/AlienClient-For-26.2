package dev.luminous.asm.mixins;

import dev.luminous.Alien;
import dev.luminous.api.events.impl.TotemParticleEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TotemParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.*;

/**
 * 26.2: {@code TotemParticle} 移到 {@code net.minecraft.client.particle}，构造参数为
 * (ClientLevel, x, y, z, xa, ya, za, SpriteSet)；速度字段 {@code velocityX/Y/Z} → {@code xd/yd/zd}；
 * {@code setColor}/{@code setAlpha} 声明在父类 {@code SingleQuadParticle} 上（不是 Particle）：
 * {@code setColor} 是 public，可以直接强转调用；{@code setAlpha} 是 protected，
 * 只有同一继承链的类才能调用 —— 本 mixin 通过 @Shadow 声明不行（@Shadow 只在目标类自身找），
 * 所以用 {@link AlphaAccessor} 在 {@code SingleQuadParticle} 上开一个公开入口。
 */
@Mixin(TotemParticle.class)
public abstract class MixinTotemParticle extends MixinParticle {

    @Inject(method = "<init>", at = @At(value = "TAIL"))
    private void hookInit(ClientLevel level, double x, double y, double z, double xa,
                          double ya, double za, SpriteSet sprites, CallbackInfo ci) {
        TotemParticleEvent event = TotemParticleEvent.get(xa, ya, za);
        Alien.EVENT_BUS.post(event);
        if (event.isCancelled()) {
            this.xd = event.velocityX;
            this.yd = event.velocityY;
            this.zd = event.velocityZ;
            Color color = event.color;
            if (color != null) {
                SingleQuadParticle quad = (SingleQuadParticle) (Object) this;
                quad.setColor(color.getRed() / 255.0f, color.getGreen() / 255.0f, color.getBlue() / 255.0f);
                ((AlphaAccessor) (Object) this).alienClient$setAlpha(color.getAlpha() / 255f);
            }
        }
    }

    /** 26.2: {@code SingleQuadParticle#setAlpha} 是 protected，用子接口暴露给本 mixin。 */
    @Mixin(SingleQuadParticle.class)
    interface AlphaAccessor {
        @org.spongepowered.asm.mixin.gen.Invoker("setAlpha")
        void alienClient$setAlpha(float alpha);
    }
}
