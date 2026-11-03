package dev.luminous.asm.mixins;

import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;

/**
 * 26.2: 原注入点 {@code Biome#getFogColor()} 已不存在，本类不再注入，仅作占位
 * （{@code alien.mixins.json} 里仍登记着它，删除类会导致 mixin 配置解析失败）。
 * <p>
 * 已在 {@code 26.2/net/minecraft/world/level/biome/Biome.java} 核实：26.2 的 Biome 只剩下
 * {@code getAttributes() -> EnvironmentAttributeMap}、{@code getSpecialEffects()}、
 * {@code getWaterColor()}、{@code getGrassColor/getFoliageColor}，没有雾色/天空色字段，
 * 也没有任何 {@code getFogColor/getSkyColor/getCloudsColor}。
 * <p>
 * 雾色在 26.2 只有一条来源链：
 * <pre>
 * EnvironmentAttributes.FOG_COLOR（维度/群系/时间轴层叠加）
 *   → FogRenderer#computeFogColor → FogRenderer#setupFog 返回的 FogData.color → "Fog" UBO
 * </pre>
 * 因此原 {@code Biome#getFogColor} 的覆写语义（{@code Ambience.dimensionColor}）现在落在两个更权威的点上，
 * 本类无需再介入：
 * <ul>
 *   <li>雾色 → {@code MixinDimensionEffects}（{@code FogRenderer#computeFogColor} TAIL，最终计算点）；</li>
 *   <li>天空色 → {@link MixinClientWorld#onAddEnvironmentAttributeLayers}（{@code SKY_COLOR} 常量层，
 *       等价旧 {@code BiomeEffects#getSkyColor}）。</li>
 * </ul>
 */
@Mixin(Biome.class)
public class MixinBiome {
}
