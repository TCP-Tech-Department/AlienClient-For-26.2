package dev.luminous.asm.mixins;

import dev.luminous.Alien;
import dev.luminous.api.events.impl.RenderEntityEvent;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 26.2: {@code EntityRenderDispatcher#render(Entity,double,double,double,float,PoseStack,
 * VertexConsumerProvider,int)} 消失 —— 实体绘制拆成
 * {@code extractEntity(Entity,float)}（产 {@code EntityRenderState}）+
 * {@code submit(EntityRenderState, CameraRenderState, ...)}，submit 阶段已经拿不到 Entity 实例。
 * <p>
 * 因此「取消某实体渲染」改为在同样拿到 Entity 的 {@code shouldRender(Entity, Frustum, ...)}
 * 返回 false（每实体每帧一次，与旧 render 注入的调用频率一致）。
 * 事件对象/取消语义（NoRender 隐藏实体）保持不变。
 */
@Mixin(EntityRenderDispatcher.class)
public class MixinEntityRenderDispatcher {

    @Inject(method = "shouldRender", at = @At("RETURN"), cancellable = true)
    public <E extends Entity> void onRender(E entity, Frustum culler, double camX, double camY, double camZ,
                                            CallbackInfoReturnable<Boolean> cir) {
        RenderEntityEvent event = RenderEntityEvent.get(entity);
        Alien.EVENT_BUS.post(event);
        if (event.isCancelled()) {
            cir.setReturnValue(false);
        }
    }
}
