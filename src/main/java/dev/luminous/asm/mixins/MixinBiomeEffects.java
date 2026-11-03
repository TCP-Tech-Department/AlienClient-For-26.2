package dev.luminous.asm.mixins;

import net.minecraft.world.level.biome.BiomeSpecialEffects;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 26.2: 目标类 {@code net.minecraft.world.biome.BiomeEffects} 已改名为
 * {@link BiomeSpecialEffects}（同包），且天空色被移出该类，本类不再注入，仅作占位
 * （{@code alien.mixins.json} 里仍登记着它，删除类会导致 mixin 配置解析失败）。
 * <p>
 * 已在 {@code 26.2/net/minecraft/world/level/biome/BiomeSpecialEffects.java} 核实：26.2 里它只剩
 * {@code waterColor / foliageColorOverride / dryFoliageColorOverride / grassColorOverride / grassColorModifier}
 * 五个分量（record），{@code getSkyColor()} / {@code getFogColor()} / {@code getWaterFogColor()} 全部移除，
 * 颜色改由 {@code EnvironmentAttributes.SKY_COLOR / FOG_COLOR / WATER_FOG_COLOR} 承载。
 * <p>
 * {@code Ambience.sky} 的新落点是 {@link MixinClientWorld#onAddEnvironmentAttributeLayers}
 * 里追加的 {@code SKY_COLOR} 常量层（官方层序之后，因此覆盖维度/群系/时间轴给出的天空色）。
 */
@Mixin(BiomeSpecialEffects.class)
public class MixinBiomeEffects {
}
