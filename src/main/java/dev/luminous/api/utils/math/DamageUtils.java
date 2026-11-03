package dev.luminous.api.utils.math;

import dev.luminous.Alien;
import dev.luminous.api.utils.combat.CombatUtil;
import dev.luminous.core.impl.PlayerManager;
import dev.luminous.mod.modules.impl.client.ClientSetting;
import dev.luminous.mod.modules.impl.combat.Criticals;
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntMaps;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.ConditionalEffect;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.effects.EnchantmentValueEffect;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.apache.commons.lang3.mutable.MutableInt;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

import static dev.luminous.api.utils.Wrapper.mc;

public class DamageUtils {
    // Explosion damage

    public static float calculateDamage(BlockPos pos, LivingEntity entity) {
        return DamageUtils.explosionDamage(entity, null, new Vec3(pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5), 12);
    }

    public static float calculateDamage(Vec3 pos, LivingEntity entity) {
        return DamageUtils.explosionDamage(entity, null, pos, 12);
    }

    public static final RaycastFactory HIT_FACTORY = (context, blockPos) -> {
        BlockState blockState = mc.level.getBlockState(blockPos);
        if (blockState.getBlock().getExplosionResistance() < 600) return null;

        return blockState.getCollisionShape(mc.level, blockPos).clip(context.start(), context.end(), blockPos);
    };

    public static float explosionDamage(LivingEntity target, Vec3 targetPos, AABB targetBox, Vec3 explosionPos, float power, RaycastFactory raycastFactory) {
        double modDistance = distance(targetPos.x, targetPos.y, targetPos.z, explosionPos.x, explosionPos.y, explosionPos.z);
        if (modDistance > power) return 0f;

        double exposure = getExposure(explosionPos, targetBox, raycastFactory);
        double impact = (1 - (modDistance / power)) * exposure;
        float damage = (int) ((impact * impact + impact) / 2 * 7 * 12 + 1);

        return calculateReductionsExplosion(damage, target, mc.level.damageSources().explosion(null));
    }

    public static float anchorDamage(LivingEntity target, LivingEntity predict, Vec3 anchor) {
        return overridingExplosionDamage(target, predict, anchor, 10f, BlockPos.containing(anchor), Blocks.AIR.defaultBlockState());
    }

    public static float overridingExplosionDamage(LivingEntity target, LivingEntity predict, Vec3 explosionPos, float power, BlockPos overridePos, BlockState overrideState) {
        return explosionDamage(target, predict, explosionPos, power, getOverridingHitFactory(overridePos, overrideState));
    }

    private static float explosionDamage(LivingEntity target, LivingEntity predict, Vec3 explosionPos, float power, RaycastFactory raycastFactory) {
        if (target == null) return 0f;
        if (target instanceof Player player && getGameMode(player) == GameType.CREATIVE) return 0f;

        return explosionDamage(target, predict != null ? predict.position() : target.position(), predict != null ? predict.getBoundingBox() : target.getBoundingBox(), explosionPos, power, raycastFactory);
    }

    public static float explosionDamage(LivingEntity target, LivingEntity predict, Vec3 explosionPos, float power) {
        if (target == null) return 0f;
        if (target instanceof Player player && getGameMode(player) == GameType.CREATIVE) return 0f;

        return explosionDamage(target, predict != null ? predict.position() : target.position(), predict != null ? predict.getBoundingBox() : target.getBoundingBox(), explosionPos, power, HIT_FACTORY);
    }

    public static RaycastFactory getOverridingHitFactory(BlockPos overridePos, BlockState overrideState) {
        return (context, blockPos) -> {
            BlockState blockState;
            if (blockPos.equals(overridePos)) blockState = overrideState;
            else {
                blockState = mc.level.getBlockState(blockPos);
                if (blockState.getBlock().getExplosionResistance() < 600) return null;
            }

            return blockState.getCollisionShape(mc.level, blockPos).clip(context.start(), context.end(), blockPos);
        };
    }

    // Sword damage

    public static float getAttackDamage(LivingEntity attacker, LivingEntity target) {
        float itemDamage = (float) attacker.getAttributeValue(Attributes.ATTACK_DAMAGE);
        DamageSource damageSource = attacker instanceof Player player ? mc.level.damageSources().playerAttack(player) : mc.level.damageSources().mobAttack(attacker);

        MobEffectInstance effect = attacker.getEffect(MobEffects.STRENGTH);
        if (effect != null) {
            itemDamage += 3f * (effect.getAmplifier() + 1);
        }
        float damage = modifyAttackDamage(attacker, target, attacker.getWeaponItem(), damageSource, itemDamage);
        return calculateReductions(damage, target, damageSource);
    }

    public static float getAttackDamage(LivingEntity attacker, LivingEntity target, ItemStack weapon) {
        AttributeInstance original = attacker.getAttribute(Attributes.ATTACK_DAMAGE);
        AttributeInstance copy = new AttributeInstance(Attributes.ATTACK_DAMAGE, o -> {
        });

        copy.setBaseValue(original.getBaseValue());
        for (AttributeModifier modifier : original.getModifiers()) {
            copy.addTransientModifier(modifier);
        }
        copy.removeModifier(Item.BASE_ATTACK_DAMAGE_ID);

        ItemAttributeModifiers attributeModifiers = weapon.get(DataComponents.ATTRIBUTE_MODIFIERS);
        if (attributeModifiers != null) {
            attributeModifiers.forEach(EquipmentSlot.MAINHAND, (entry, modifier) -> {
                if (entry == Attributes.ATTACK_DAMAGE) copy.addOrUpdateTransientModifier(modifier);
            });
        }

        float itemDamage = (float) copy.getValue();
        DamageSource damageSource = attacker instanceof Player player ? mc.level.damageSources().playerAttack(player) : mc.level.damageSources().mobAttack(attacker);

        float damage = modifyAttackDamage(attacker, target, weapon, damageSource, itemDamage);
        return calculateReductions(damage, target, damageSource);
    }

    private static float modifyAttackDamage(LivingEntity attacker, LivingEntity target, ItemStack weapon, DamageSource damageSource, float damage) {
        // Get enchant damage
        Object2IntMap<Holder<Enchantment>> enchantments = new Object2IntOpenHashMap<>();
        getEnchantments(weapon, enchantments);
        float enchantDamage = 0f;

        int sharpness = getEnchantmentLevel(enchantments, Enchantments.SHARPNESS);
        if (sharpness > 0) {
            enchantDamage += 1 + 0.5f * (sharpness - 1);
        }

        int baneOfArthropods = getEnchantmentLevel(enchantments, Enchantments.BANE_OF_ARTHROPODS);
        if (baneOfArthropods > 0 && target.getType().builtInRegistryHolder().is(EntityTypeTags.SENSITIVE_TO_BANE_OF_ARTHROPODS)) {
            enchantDamage += 2.5f * baneOfArthropods;
        }

        int impaling = getEnchantmentLevel(enchantments, Enchantments.IMPALING);
        if (impaling > 0 && target.getType().builtInRegistryHolder().is(EntityTypeTags.SENSITIVE_TO_IMPALING)) {
            enchantDamage += 2.5f * impaling;
        }

        int smite = getEnchantmentLevel(enchantments, Enchantments.SMITE);
        if (smite > 0 && target.getType().builtInRegistryHolder().is(EntityTypeTags.SENSITIVE_TO_SMITE)) {
            enchantDamage += 2.5f * smite;
        }

        // Factor charge
        if (attacker instanceof Player playerEntity) {
            float charge = playerEntity.getAttackStrengthScale(0.5f);
            damage *= 0.2f + charge * charge * 0.8f;
            enchantDamage *= charge;

            if (weapon.getItem() instanceof MaceItem) {
                // 26.2: MaceItem.getBonusAttackDamage 被删除，逻辑搬到 Item#getAttackDamageBonus，
                // 由原版 Player#attack 在「充能缩放之后、暴击倍率之前」调用（见
                // 26.2/net/minecraft/world/entity/player/Player.java:971）：
                //     baseDamage += attackingItemStack.getItem().getAttackDamageBonus(entity, baseDamage, damageSource);
                // MaceItem#getAttackDamageBonus 的分段公式（fallDistance d）：
                //     d <= 3 → 4*d ；d <= 8 → 12 + 2*(d-3) ；否则 22 + (d-8)
                // 并且只在 canSmashAttack（fallDistance > 1.5 且 !isFallFlying）时生效。
                float maceBonus = weapon.getItem().getAttackDamageBonus(target, damage, damageSource);
                // 客户端 level 不是 ServerLevel，MaceItem 里那句
                // `attacker.level() instanceof ServerLevel ? ... modifyFallBasedDamage(...) : damage`
                // 会走 else 分支，丢掉 density（smash_damage_per_fallen_block）部分，这里按同一数据组件补上。
                if (MaceItem.canSmashAttack(playerEntity)) {
                    maceBonus += smashDamagePerFallenBlock(playerEntity, weapon) * playerEntity.fallDistance;
                }
                damage += maceBonus;
            }

            // Factor critical hit
            if (charge > 0.9f
                    && (attacker.fallDistance > 0f || attacker == mc.player && Criticals.INSTANCE.isOn() && !Criticals.INSTANCE.mode.is(Criticals.Mode.Ground) && (mc.player.onGround() || !Criticals.INSTANCE.onlyGround.getValue()))
                    && (!attacker.onGround() || attacker == mc.player && Criticals.INSTANCE.isOn() && !Criticals.INSTANCE.mode.is(Criticals.Mode.Ground))
                    && !attacker.onClimbable()
                    && !attacker.isInWater()
                    && !attacker.hasEffect(MobEffects.BLINDNESS)
                    && !attacker.isPassenger()
                    // 26.2 Player#canCriticalAttack 新增了 !isSprinting()（疾跑时只触发横扫/击退攻击，不暴击）
                    && !attacker.isSprinting()) {
                damage *= 1.5f;
            }
        }

        return damage + enchantDamage;
    }

    /**
     * 26.2 的「每下落一格的重锤伤害」由附魔数据组件
     * {@link EnchantmentEffectComponents#SMASH_DAMAGE_PER_FALLEN_BLOCK} 提供（density 原版值是 0.5/级），
     * 原版通过 {@code EnchantmentHelper#modifyFallBasedDamage(ServerLevel, ...)} 求值。
     * 客户端拿不到 ServerLevel（无法构造 LootContext），所以这里直接遍历武器上的附魔并应用
     * 无条件（requirements 为空，原版 density 即如此）的 EnchantmentValueEffect，
     * 数值与原版一致，且对改过 density 数值的数据包同样生效。
     */
    private static float smashDamagePerFallenBlock(LivingEntity attacker, ItemStack weapon) {
        Object2IntMap<Holder<Enchantment>> enchantments = new Object2IntOpenHashMap<>();
        getEnchantments(weapon, enchantments);
        float perBlock = 0f;
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : Object2IntMaps.fastIterable(enchantments)) {
            for (ConditionalEffect<EnchantmentValueEffect> effect : entry.getKey().value().getEffects(EnchantmentEffectComponents.SMASH_DAMAGE_PER_FALLEN_BLOCK)) {
                if (effect.requirements().isEmpty()) {
                    perBlock = effect.effect().process(entry.getIntValue(), attacker.getRandom(), perBlock);
                }
            }
        }
        return perBlock;
    }

    // Fall Damage

    public static float fallDamage(LivingEntity entity) {
        if (entity instanceof Player player && player.getAbilities().flying) return 0f;
        if (entity.hasEffect(MobEffects.SLOW_FALLING) || entity.hasEffect(MobEffects.LEVITATION))
            return 0f;

        // Fast path - Above the surface
        int surface = mc.level.getChunkAt(entity.blockPosition()).getHeight(Heightmap.Types.MOTION_BLOCKING, entity.blockPosition().getX() & 15, entity.blockPosition().getZ() & 15);
        if (entity.blockPosition().getY() >= surface) return fallDamageReductions(entity, surface);

        // Under the surface
        BlockHitResult raycastResult = mc.level.clip(new ClipContext(entity.position(), new Vec3(entity.getX(), mc.level.getMinY(), entity.getZ()), ClipContext.Block.COLLIDER, ClipContext.Fluid.WATER, entity));
        if (raycastResult.getType() == HitResult.Type.MISS) return 0;

        return fallDamageReductions(entity, raycastResult.getBlockPos().getY());
    }

    private static float fallDamageReductions(LivingEntity entity, int surface) {
        int fallHeight = (int) (entity.getY() - surface + entity.fallDistance - 3d);
        @Nullable MobEffectInstance jumpBoostInstance = entity.getEffect(MobEffects.JUMP_BOOST);
        if (jumpBoostInstance != null) fallHeight -= jumpBoostInstance.getAmplifier() + 1;

        return calculateReductions(fallHeight, entity, mc.level.damageSources().fall());
    }

    // Utils

    public static float calculateReductionsExplosion(float damage, LivingEntity entity, DamageSource damageSource) {
        if (damageSource.scalesWithDifficulty()) {
            switch (mc.level.getDifficulty()) {
                case EASY -> damage = Math.min(damage / 2 + 1, damage);
                case HARD -> damage *= 1.5f;
            }
        }

        // Armor reduction
        damage = CombatRules.getDamageAfterAbsorb(entity, damage, damageSource, getArmor(entity), (float) getARMOR_TOUGHNESS(entity));

        // Resistance reduction
        damage = resistanceReduction(entity, damage);

        // Protection reduction
        damage = CombatRules.getDamageAfterMagicAbsorb(damage, getProtectionAmount(getArmorItems(entity)));

        return Math.max(damage, 0);
    }

    public static float calculateReductions(float damage, LivingEntity entity, DamageSource damageSource) {
        if (damageSource.scalesWithDifficulty()) {
            switch (mc.level.getDifficulty()) {
                case EASY -> damage = Math.min(damage / 2 + 1, damage);
                case HARD -> damage *= 1.5f;
            }
        }

        // Armor reduction
        damage = CombatRules.getDamageAfterAbsorb(entity, damage, damageSource, getArmor(entity), (float) getARMOR_TOUGHNESS(entity));

        // Resistance reduction
        damage = resistanceReduction(entity, damage);

        // Protection reduction
        damage = protectionReduction(entity, damage, damageSource);

        return Math.max(damage, 0);
    }

    private static Iterable<ItemStack> getArmorItems(LivingEntity entity) {
        return java.util.List.of(
                entity.getItemBySlot(EquipmentSlot.HEAD),
                entity.getItemBySlot(EquipmentSlot.CHEST),
                entity.getItemBySlot(EquipmentSlot.LEGS),
                entity.getItemBySlot(EquipmentSlot.FEET)
        );
    }

    public static double getARMOR_TOUGHNESS(LivingEntity entity) {
        if (entity instanceof Player player) {
            PlayerManager.EntityAttribute entityAttribute = Alien.PLAYER.map.get(player);
            if (entityAttribute != null)
                return entityAttribute.toughness();
        }
        return entity.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
    }

    private static float getArmor(LivingEntity entity) {
        if (entity instanceof Player player) {
            PlayerManager.EntityAttribute entityAttribute = Alien.PLAYER.map.get(player);
            if (entityAttribute != null)
                return entityAttribute.armor();
        }
        return (float) Math.floor(entity.getAttributeValue(Attributes.ARMOR));
    }

    private static float protectionReduction(LivingEntity player, float damage, DamageSource source) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return damage;

        int damageProtection = 0;

        for (ItemStack stack : getArmorItems(player)) {
            Object2IntMap<Holder<Enchantment>> enchantments = new Object2IntOpenHashMap<>();
            getEnchantments(stack, enchantments);

            int protection = getEnchantmentLevel(enchantments, Enchantments.PROTECTION);
            if (protection > 0) {
                damageProtection += protection;
            }

            int fireProtection = getEnchantmentLevel(enchantments, Enchantments.FIRE_PROTECTION);
            if (fireProtection > 0 && source.is(DamageTypeTags.IS_FIRE)) {
                damageProtection += 2 * fireProtection;
            }

            int blastProtection = getEnchantmentLevel(enchantments, Enchantments.BLAST_PROTECTION);
            if (blastProtection > 0 && source.is(DamageTypeTags.IS_EXPLOSION)) {
                damageProtection += 2 * blastProtection;
            }

            int projectileProtection = getEnchantmentLevel(enchantments, Enchantments.PROJECTILE_PROTECTION);
            if (projectileProtection > 0 && source.is(DamageTypeTags.IS_PROJECTILE)) {
                damageProtection += 2 * projectileProtection;
            }

            int featherFalling = getEnchantmentLevel(enchantments, Enchantments.FEATHER_FALLING);
            if (featherFalling > 0 && source.is(DamageTypeTags.IS_FALL)) {
                damageProtection += 3 * featherFalling;
            }
        }

        return CombatRules.getDamageAfterMagicAbsorb(damage, damageProtection);
    }

    public static int getProtectionAmount(Iterable<ItemStack> equipment) {
        MutableInt mutableInt = new MutableInt();
        equipment.forEach(i -> mutableInt.add(getProtectionAmount(i)));
        return mutableInt.intValue();
    }

    public static int getProtectionAmount(ItemStack stack) {
        // 26.2: EnchantmentHelper.getItemEnchantmentLevel 只接受 Holder<Enchantment>，
        // 而 Enchantments.* 是 ResourceKey<Enchantment>；这里改走本类已有的
        // getEnchantments + ResourceKey 匹配，计算结果与旧实现一致。
        Object2IntMap<Holder<Enchantment>> enchantments = new Object2IntArrayMap<>();
        getEnchantments(stack, enchantments);
        int modifierBlast = getEnchantmentLevel(enchantments, Enchantments.BLAST_PROTECTION);
        int modifier = getEnchantmentLevel(enchantments, Enchantments.PROTECTION);
        return modifierBlast * 2 + modifier;
    }

    private static float resistanceReduction(LivingEntity player, float damage) {
        MobEffectInstance resistance = player.getEffect(MobEffects.RESISTANCE);
        if (resistance != null) {
            int lvl = resistance.getAmplifier() + 1;
            damage *= (1 - (lvl * 0.2f));
        }

        return Math.max(damage, 0);
    }

    private static float getExposure(Vec3 source, AABB box, RaycastFactory raycastFactory) {
        if (ClientSetting.INSTANCE.optimizedCalc.getValue()) {
            int miss = 0;
            int hit = 0;

            for (int k = 0; k <= 1; k += 1) {
                for (int l = 0; l <= 1; l += 1) {
                    for (int m = 0; m <= 1; m += 1) {
                        double n = Mth.lerp(k, box.minX, box.maxX);
                        double o = Mth.lerp(l, box.minY, box.maxY);
                        double p = Mth.lerp(m, box.minZ, box.maxZ);
                        Vec3 vec3d = new Vec3(n, o, p);
                        if (raycast(vec3d, source, CombatUtil.terrainIgnore) == HitResult.Type.MISS)
                            ++miss;
                        ++hit;
                    }
                }
            }
            return (float) miss / (float) hit;
        }
        double xDiff = box.maxX - box.minX;
        double yDiff = box.maxY - box.minY;
        double zDiff = box.maxZ - box.minZ;

        double xStep = 1 / (xDiff * 2 + 1);
        double yStep = 1 / (yDiff * 2 + 1);
        double zStep = 1 / (zDiff * 2 + 1);

        if (xStep > 0 && yStep > 0 && zStep > 0) {
            int misses = 0;
            int hits = 0;

            double xOffset = (1 - Math.floor(1 / xStep) * xStep) * 0.5;
            double zOffset = (1 - Math.floor(1 / zStep) * zStep) * 0.5;

            xStep = xStep * xDiff;
            yStep = yStep * yDiff;
            zStep = zStep * zDiff;

            double startX = box.minX + xOffset;
            double startY = box.minY;
            double startZ = box.minZ + zOffset;
            double endX = box.maxX + xOffset;
            double endY = box.maxY;
            double endZ = box.maxZ + zOffset;

            for (double x = startX; x <= endX; x += xStep) {
                for (double y = startY; y <= endY; y += yStep) {
                    for (double z = startZ; z <= endZ; z += zStep) {
                        Vec3 position = new Vec3(x, y, z);

                        if (raycast(new ExposureRaycastContext(position, source), raycastFactory) == null) misses++;

                        hits++;
                    }
                }
            }

            return (float) misses / hits;
        }

        return 0f;
    }

    /* Raycasts */

    public static HitResult.Type raycast(Vec3 start, Vec3 end, boolean ignoreTerrain) {
        return traverseBlocks(start, end, null, (innerContext, blockPos) -> {
            BlockState blockState = mc.level.getBlockState(blockPos);
            if (blockState.getBlock().getExplosionResistance() < 600 && ignoreTerrain) return null;
            BlockHitResult hitResult = blockState.getCollisionShape(mc.level, blockPos).clip(start, end, blockPos);
            return hitResult == null ? null : hitResult.getType();
        }, (innerContext) -> HitResult.Type.MISS);
    }

    public static BlockHitResult raycast(ExposureRaycastContext context, RaycastFactory raycastFactory) {
        return traverseBlocks(context.start(), context.end(), context, raycastFactory, ctx -> null);
    }

    // 26.2: BlockGetter.traverseBlocks 是包私有的，这里在本类内重建等价遍历逻辑。
    private static <T, C> T traverseBlocks(Vec3 from, Vec3 to, C context, BiFunction<C, BlockPos, @Nullable T> consumer, Function<C, T> missFactory) {
        if (from.equals(to)) {
            return missFactory.apply(context);
        }

        double toX = Mth.lerp(-1.0E-7, to.x, from.x);
        double toY = Mth.lerp(-1.0E-7, to.y, from.y);
        double toZ = Mth.lerp(-1.0E-7, to.z, from.z);
        double fromX = Mth.lerp(-1.0E-7, from.x, to.x);
        double fromY = Mth.lerp(-1.0E-7, from.y, to.y);
        double fromZ = Mth.lerp(-1.0E-7, from.z, to.z);
        int currentBlockX = Mth.floor(fromX);
        int currentBlockY = Mth.floor(fromY);
        int currentBlockZ = Mth.floor(fromZ);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(currentBlockX, currentBlockY, currentBlockZ);
        T first = consumer.apply(context, pos);
        if (first != null) {
            return first;
        }

        double dx = toX - fromX;
        double dy = toY - fromY;
        double dz = toZ - fromZ;
        int signX = Mth.sign(dx);
        int signY = Mth.sign(dy);
        int signZ = Mth.sign(dz);
        double tDeltaX = signX == 0 ? Double.MAX_VALUE : signX / dx;
        double tDeltaY = signY == 0 ? Double.MAX_VALUE : signY / dy;
        double tDeltaZ = signZ == 0 ? Double.MAX_VALUE : signZ / dz;
        double tX = tDeltaX * (signX > 0 ? 1.0 - Mth.frac(fromX) : Mth.frac(fromX));
        double tY = tDeltaY * (signY > 0 ? 1.0 - Mth.frac(fromY) : Mth.frac(fromY));
        double tZ = tDeltaZ * (signZ > 0 ? 1.0 - Mth.frac(fromZ) : Mth.frac(fromZ));

        while (tX <= 1.0 || tY <= 1.0 || tZ <= 1.0) {
            if (tX < tY) {
                if (tX < tZ) {
                    currentBlockX += signX;
                    tX += tDeltaX;
                } else {
                    currentBlockZ += signZ;
                    tZ += tDeltaZ;
                }
            } else if (tY < tZ) {
                currentBlockY += signY;
                tY += tDeltaY;
            } else {
                currentBlockZ += signZ;
                tZ += tDeltaZ;
            }

            T result = consumer.apply(context, pos.set(currentBlockX, currentBlockY, currentBlockZ));
            if (result != null) {
                return result;
            }
        }

        return missFactory.apply(context);
    }

    public record ExposureRaycastContext(Vec3 start, Vec3 end) {
    }

    @FunctionalInterface
    public interface RaycastFactory extends BiFunction<ExposureRaycastContext, BlockPos, BlockHitResult> {
    }

    public static int getEnchantmentLevel(ItemStack itemStack, ResourceKey<Enchantment> enchantment) {
        if (itemStack.isEmpty()) return 0;
        Object2IntMap<Holder<Enchantment>> itemEnchantments = new Object2IntArrayMap<>();
        getEnchantments(itemStack, itemEnchantments);
        return getEnchantmentLevel(itemEnchantments, enchantment);
    }

    public static int getEnchantmentLevel(Object2IntMap<Holder<Enchantment>> itemEnchantments, ResourceKey<Enchantment> enchantment) {
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : Object2IntMaps.fastIterable(itemEnchantments)) {
            if (entry.getKey().is(enchantment)) return entry.getIntValue();
        }
        return 0;
    }

    public static double distance(double x1, double y1, double z1, double x2, double y2, double z2) {
        return Math.sqrt(squaredDistance(x1, y1, z1, x2, y2, z2));
    }

    public static GameType getGameMode(Player player) {
        if (player == null) return null;
        PlayerInfo playerInfo = mc.getConnection().getPlayerInfo(player.getUUID());
        if (playerInfo == null) return null;
        return playerInfo.getGameMode();
    }

    public static double squaredDistanceTo(Entity entity) {
        return squaredDistanceTo(entity.getX(), entity.getY(), entity.getZ());
    }

    public static double squaredDistanceTo(BlockPos blockPos) {
        return squaredDistanceTo(blockPos.getX(), blockPos.getY(), blockPos.getZ());
    }

    public static double squaredDistanceTo(double x, double y, double z) {
        return squaredDistance(mc.player.getX(), mc.player.getY(), mc.player.getZ(), x, y, z);
    }

    public static double squaredDistance(double x1, double y1, double z1, double x2, double y2, double z2) {
        double f = x1 - x2;
        double g = y1 - y2;
        double h = z1 - z2;
        return org.joml.Math.fma(f, f, org.joml.Math.fma(g, g, h * h));
    }

    public static void getEnchantments(ItemStack itemStack, Object2IntMap<Holder<Enchantment>> enchantments) {
        enchantments.clear();

        if (!itemStack.isEmpty()) {
            Set<Object2IntMap.Entry<Holder<Enchantment>>> itemEnchantments = itemStack.getItem() == Items.ENCHANTED_BOOK
                    ? itemStack.get(DataComponents.STORED_ENCHANTMENTS).entrySet()
                    : itemStack.getEnchantments().entrySet();

            for (Object2IntMap.Entry<Holder<Enchantment>> entry : itemEnchantments) {
                enchantments.put(entry.getKey(), entry.getIntValue());
            }
        }
    }
}
