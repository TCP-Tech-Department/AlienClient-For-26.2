package dev.luminous.asm.mixins;

import dev.luminous.Alien;
import dev.luminous.api.events.impl.EntitySpawnEvent;
import dev.luminous.api.events.impl.EntitySpawnedEvent;
import dev.luminous.api.events.impl.RemoveEntityEvent;
import dev.luminous.api.events.impl.TickEntityEvent;
import dev.luminous.mod.modules.impl.render.Ambience;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.timeline.Timeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.awt.*;

@Mixin(ClientLevel.class)
public abstract class MixinClientWorld {

    // 26.2: was tickEntity(Entity)
    @Inject(method = "tickNonPassenger", at = @At("HEAD"), cancellable = true)
    public void onTickEntity(Entity entity, CallbackInfo ci) {
        TickEntityEvent event = TickEntityEvent.get(entity);
        Alien.EVENT_BUS.post(event);
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "addEntity", at = @At("HEAD"), cancellable = true)
    public void onAddEntity(Entity entity, CallbackInfo ci) {
        EntitySpawnEvent event = EntitySpawnEvent.get(entity);
        Alien.EVENT_BUS.post(event);
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Inject(method = "removeEntity", at = @At(value = "HEAD"))
    private void hookRemoveEntity(int entityId, Entity.RemovalReason removalReason, CallbackInfo ci) {
        Entity entity = ((ClientLevel) (Object) this).getEntity(entityId); // 26.2: was getEntityById
        if (entity == null) {
            return;
        }
        RemoveEntityEvent removeEntityEvent = RemoveEntityEvent.get(entity, removalReason);
        Alien.EVENT_BUS.post(removeEntityEvent);
    }

    @Inject(method = "addEntity", at = @At("TAIL"))
    public void onAddEntityTail(Entity entity, CallbackInfo ci) {
        EntitySpawnedEvent event = EntitySpawnedEvent.get(entity);
        Alien.EVENT_BUS.post(event);
    }

    /**
     * 26.2 环境视觉总入口。
     * <p>
     * 1.21.1 里 {@code ClientWorld#getSkyColor/getCloudsColor}、{@code Biome#getFogColor}、
     * {@code BiomeEffects#getSkyColor}、{@code ClientWorld#getDimensionEffects} 是四个彼此独立的注入点；
     * 26.2 把这些全部收敛成 {@code ClientLevel} 构造期烘焙的一棵
     * {@link EnvironmentAttributeSystem}（层序 = 维度常量层 → 群系位置层 → 时间轴层 → 天气层）。
     * 本方法（{@code ClientLevel#addEnvironmentAttributeLayers}，private，官方只在构造器里调用一次）
     * 是在官方层之后追加常量层的唯一位置，四个旧注入点因此合并到这里：
     * <ul>
     *   <li>{@code Ambience.sky} → {@code EnvironmentAttributes.SKY_COLOR}；</li>
     *   <li>{@code Ambience.cloud} → {@code EnvironmentAttributes.CLOUD_COLOR}；</li>
     *   <li>{@code Ambience.dimensionColor} → {@code SKY_COLOR}（雾色那一半由 {@code MixinDimensionEffects}
     *       挂在 {@code FogRenderer#computeFogColor} TAIL，因为 {@code FOG_COLOR} 会再被天空色 srgbLerp 混合）；</li>
     *   <li>{@code Ambience.forceOverworld} → 把主世界 {@link DimensionType} 的整张属性表与全部时间轴再叠一遍。</li>
     * </ul>
     * 常量层的 lambda 内部才判断模块开关，因此「开关模块」不需要重建世界也能立刻生效
     * （{@code forceOverworld} 例外：它是结构性的，必须重建 ClientLevel 才生效，切维度即可）。
     */
    @Inject(method = "addEnvironmentAttributeLayers", at = @At("RETURN"))
    private void onAddEnvironmentAttributeLayers(EnvironmentAttributeSystem.Builder builder,
                                                 CallbackInfoReturnable<EnvironmentAttributeSystem.Builder> cir) {
        ClientLevel level = (ClientLevel) (Object) this;

        // 26.2: was @Inject(ClientLevel#getDimensionEffects, HEAD, cancellable) returning DimensionEffects.Overworld
        DimensionType overworld = alien$overworldDimensionType(level);
        if (overworld != null && !Level.OVERWORLD.equals(level.dimension()) && Ambience.INSTANCE != null
                && Ambience.INSTANCE.forceOverworld.getValue()) {
            alien$addOverworldLayers(builder, level, overworld);
        }

        builder.addConstantLayer(EnvironmentAttributes.SKY_COLOR, base -> {
            if (Ambience.INSTANCE != null && Ambience.INSTANCE.isOn()) {
                Color color = null;
                if (Ambience.INSTANCE.dimensionColor.booleanValue) {
                    color = Ambience.INSTANCE.dimensionColor.getValue();
                } else if (Ambience.INSTANCE.sky.booleanValue) {
                    color = Ambience.INSTANCE.sky.getValue();
                }
                if (color != null) {
                    return 0xFF000000 | color.getRed() << 16 | color.getGreen() << 8 | color.getBlue();
                }
            }
            return base;
        });

        builder.addConstantLayer(EnvironmentAttributes.CLOUD_COLOR, base -> {
            if (Ambience.INSTANCE != null && Ambience.INSTANCE.isOn() && Ambience.INSTANCE.cloud.booleanValue) {
                Color cloud = Ambience.INSTANCE.cloud.getValue();
                return cloud.getAlpha() << 24 | cloud.getRed() << 16 | cloud.getGreen() << 8 | cloud.getBlue();
            }
            return base;
        });
    }

    /**
     * 26.2 的「维度效果」就是 {@code DimensionType} 自带的 {@code EnvironmentAttributeMap} + {@code timelines}。
     * 旧 {@code getDimensionEffects()} 换成 {@code DimensionEffects.Overworld} 的等价做法：
     * 把主世界的属性表与时间轴按同样的机制再追加一次
     * （{@code addConstantLayer(EnvironmentAttributeMap)} 官方就是这样遍历 map 的，
     * 追加在官方层之后 → 覆盖本维度的同名属性）。
     */
    @Unique
    private static void alien$addOverworldLayers(EnvironmentAttributeSystem.Builder builder, ClientLevel level,
                                                 DimensionType overworld) {
        builder.addConstantLayer(overworld.attributes());
        // 时间轴（太阳/月亮/星星角度、白天天空色等）官方在 addDefaultLayers 里只挂本维度自己的，
        // 这里把主世界的全部 Timeline 再挂一遍；clockManager 用当前 level 的客户端时钟管理器
        for (Holder<Timeline> timeline : overworld.timelines()) {
            builder.addTimelineLayer(timeline, level.clockManager());
        }
    }

    @Unique
    private static DimensionType alien$overworldDimensionType(ClientLevel level) {
        // 26.2: 维度类型注册表的 key 是 ResourceKey<DimensionType>（BuiltinDimensionTypes.OVERWORLD），
        // 不是 Level.OVERWORLD（那是 ResourceKey<Level>），两者不可混用。
        Holder<DimensionType> holder = level.registryAccess()
                .lookupOrThrow(Registries.DIMENSION_TYPE)
                .get(BuiltinDimensionTypes.OVERWORLD)
                .orElse(null);
        return holder == null ? null : holder.value();
    }
}
