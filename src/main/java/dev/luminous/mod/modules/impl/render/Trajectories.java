package dev.luminous.mod.modules.impl.render;

import dev.luminous.Alien;
import dev.luminous.api.utils.math.MathUtil;
import dev.luminous.api.utils.render.ColorUtil;
import dev.luminous.api.utils.render.Render3DUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.player.AutoPearl;
import dev.luminous.mod.modules.settings.impl.ColorSetting;
import net.minecraft.world.level.block.Blocks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownExperienceBottle;
import net.minecraft.world.item.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;

import java.awt.*;

public class Trajectories extends Module {
    private final ColorSetting hand = add(new ColorSetting("InteractionHand", new Color(255, 255, 255, 255)).injectBoolean(true));
    private final ColorSetting pearl = add(new ColorSetting("Pearl", new Color(255, 255, 255, 255)).injectBoolean(true));
    private final ColorSetting arrow = add(new ColorSetting("Arrow", new Color(255, 255, 255, 255)).injectBoolean(true));
    private final ColorSetting xp = add(new ColorSetting("XP", new Color(255, 255, 255, 255)).injectBoolean(true));

    public Trajectories() {
        super("Trajectories", Category.Render);
        setChinese("抛物线预测");
    }

    @Override
    public void onRender3D(PoseStack matrixStack) {
        if (nullCheck()) return;
        // 26.2: 旧 RenderSystem#disableDepthTest/enableDepthTest 已移除；
        // Render3DUtil 内部提交的 gizmo 默认 setAlwaysOnTop，等价于旧的关闭深度测试。
        if (pearl.booleanValue || arrow.booleanValue || xp.booleanValue) {
            for (Entity en : Alien.THREAD.getEntities()) {
                if (en instanceof ThrownEnderpearl && pearl.booleanValue)
                    calcTrajectory(en, pearl.getValue());

                if (en instanceof Arrow && arrow.booleanValue)
                    calcTrajectory(en, arrow.getValue());

                if (en instanceof ThrownExperienceBottle && xp.booleanValue)
                    calcTrajectory(en, xp.getValue());
            }
        }
        if (hand.booleanValue) {
            // 26.2: Options#getPerspective → getCameraType
            if (!mc.options.getCameraType().isFirstPerson())
                return;
            InteractionHand hand;

            ItemStack mainHand = mc.player.getMainHandItem();
            ItemStack offHand = mc.player.getOffhandItem();

            if (mainHand.getItem() instanceof BowItem || mainHand.getItem() instanceof CrossbowItem || isThrowable(mainHand.getItem()) || AutoPearl.INSTANCE.isOn()) {
                hand = InteractionHand.MAIN_HAND;
            } else if (offHand.getItem() instanceof BowItem || offHand.getItem() instanceof CrossbowItem || isThrowable(offHand.getItem())) {
                hand = InteractionHand.OFF_HAND;
            } else return;
            // 26.2: Options#getBobView → bobView()（OptionInstance）
            boolean prev_bob = mc.options.bobView().get();
            mc.options.bobView().set(false);
            // 26.2: Entity#prevX/prevY/prevZ → xo/yo/zo
            double x = MathUtil.interpolate(mc.player.xo, mc.player.getX(), mc.getDeltaTracker().getGameTimeDeltaPartialTick(true));
            double y = MathUtil.interpolate(mc.player.yo, mc.player.getY(), mc.getDeltaTracker().getGameTimeDeltaPartialTick(true));
            double z = MathUtil.interpolate(mc.player.zo, mc.player.getZ(), mc.getDeltaTracker().getGameTimeDeltaPartialTick(true));
            // 26.2: EnchantmentHelper#getLevel 移除，改用 getItemEnchantmentLevel(Holder, ItemStack)；
            // 注册表查询改为 registryAccess().getOrThrow(ResourceKey)
            var multishot = mc.level.registryAccess().getOrThrow(Enchantments.MULTISHOT);
            if ((offHand.getItem() instanceof CrossbowItem && EnchantmentHelper.getItemEnchantmentLevel(multishot, offHand) != 0) ||
                    (mainHand.getItem() instanceof CrossbowItem && EnchantmentHelper.getItemEnchantmentLevel(multishot, mainHand) != 0)) {

                calcTrajectory(hand == InteractionHand.OFF_HAND ? offHand.getItem() : mainHand.getItem(), mc.player.getYRot() - 10, x, y, z);
                calcTrajectory(hand == InteractionHand.OFF_HAND ? offHand.getItem() : mainHand.getItem(), mc.player.getYRot(), x, y, z);
                calcTrajectory(hand == InteractionHand.OFF_HAND ? offHand.getItem() : mainHand.getItem(), mc.player.getYRot() + 10, x, y, z);

            } else
                calcTrajectory(hand == InteractionHand.OFF_HAND ? offHand.getItem() : mainHand.getItem(), mc.player.getYRot(), x, y, z);
            mc.options.bobView().set(prev_bob);
        }
    }

    private void calcTrajectory(Entity e, Color color) {
        double motionX = e.getDeltaMovement().x;
        double motionY = e.getDeltaMovement().y;
        double motionZ = e.getDeltaMovement().z;
        if (motionX == 0 && motionY == 0 && motionZ == 0) return;
        double x = e.getX();
        double y = e.getY();
        double z = e.getZ();
        Vec3 lastPos;
        for (int i = 0; i < 300; i++) {
            lastPos = new Vec3(x, y, z);
            x += motionX;
            y += motionY;
            z += motionZ;
            if (mc.level.getBlockState(new BlockPos((int) x, (int) y, (int) z)).getBlock() == Blocks.WATER) {
                motionX *= 0.8;
                motionY *= 0.8;
                motionZ *= 0.8;
            } else {
                motionX *= 0.99;
                motionY *= 0.99;
                motionZ *= 0.99;
            }
            if (e instanceof Arrow) {
                motionY -= 0.05000000074505806;
            } else {
                motionY -= 0.03f;
            }
            Vec3 pos = new Vec3(x, y, z);

            // 26.2: Level#raycast → BlockGetter#clip；ClipContext.ShapeType → ClipContext.Block，
            // ClipContext.FluidHandling → ClipContext.Fluid
            BlockHitResult hit = mc.level.clip(new ClipContext(lastPos, pos, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
            if (hit != null) {
                if (hit.getType() == HitResult.Type.ENTITY)
                    break;
                if (hit.getType() == HitResult.Type.BLOCK)
                    break;
            }

            if (y <= -65) break;

            int alpha = (int) MathUtil.clamp((255f * ((i + 1) / 10f)), 0, 255);

            Render3DUtil.drawLine(lastPos, pos, ColorUtil.injectAlpha(color, alpha));
        }
    }

    private void calcTrajectory(Item item, float yaw, double x, double y, double z) {

        y = y + mc.player.getEyeHeight(mc.player.getPose()) - 0.1000000014901161;

        if (item == mc.player.getMainHandItem().getItem()) {
            x = x - Mth.cos(yaw / 180.0f * 3.1415927f) * 0.16f;
            z = z - Mth.sin(yaw / 180.0f * 3.1415927f) * 0.16f;
        } else {
            x = x + Mth.cos(yaw / 180.0f * 3.1415927f) * 0.16f;
            z = z + Mth.sin(yaw / 180.0f * 3.1415927f) * 0.16f;
        }

        final float maxDist = getDistance(item);
        double motionX = -Mth.sin(yaw / 180.0f * 3.1415927f) * Mth.cos(mc.player.getXRot() / 180.0f * 3.1415927f) * maxDist;
        double motionY = -Mth.sin((mc.player.getXRot() - getThrowPitch(item)) / 180.0f * 3.141593f) * maxDist;
        double motionZ = Mth.cos(yaw / 180.0f * 3.1415927f) * Mth.cos(mc.player.getXRot() / 180.0f * 3.1415927f) * maxDist;
        float power = mc.player.getTicksUsingItem() / 20.0f;
        power = (power * power + power * 2.0f) / 3.0f;
        if (power > 1.0f) {
            power = 1.0f;
        }
        final float distance = Mth.sqrt((float) (motionX * motionX + motionY * motionY + motionZ * motionZ));
        motionX /= distance;
        motionY /= distance;
        motionZ /= distance;

        final float pow = (item instanceof BowItem ? (power * 2.0f) : item instanceof CrossbowItem ? (2.2f) : 1.0f) * getThrowVelocity(item);

        motionX *= pow;
        motionY *= pow;
        motionZ *= pow;
        motionX += mc.player.getDeltaMovement().x;
        motionY += mc.player.getDeltaMovement().y;
        motionZ += mc.player.getDeltaMovement().z;

        Vec3 lastPos;
        for (int i = 0; i < 300; i++) {
            lastPos = new Vec3(x, y, z);
            x += motionX;
            y += motionY;
            z += motionZ;
            if (mc.level.getBlockState(new BlockPos((int) x, (int) y, (int) z)).getBlock() == Blocks.WATER) {
                motionX *= 0.8;
                motionY *= 0.8;
                motionZ *= 0.8;
            } else {
                motionX *= 0.99;
                motionY *= 0.99;
                motionZ *= 0.99;
            }

            if (item instanceof BowItem) motionY -= 0.05000000074505806;
            else if (mc.player.getMainHandItem().getItem() instanceof CrossbowItem) motionY -= 0.05000000074505806;
            else motionY -= 0.03f;

            Vec3 pos = new Vec3(x, y, z);

            for (Entity ent : Alien.THREAD.getEntities()) {
                if (ent instanceof Arrow || ent.equals(mc.player)) continue;
                if (ent.getBoundingBox().intersects(new AABB(x - 0.3, y - 0.3, z - 0.3, x + 0.3, y + 0.3, z + 0.3))) {
                    Render3DUtil.drawBox(ent.getBoundingBox(), hand.getValue());
                    break;
                }
            }

            BlockHitResult bhr = mc.level.clip(new ClipContext(lastPos, pos, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
            if (bhr != null && bhr.getType() == HitResult.Type.BLOCK) {
                Render3DUtil.drawBox(new AABB(bhr.getBlockPos()), hand.getValue());
                break;
            }

            if (y <= -65) break;
            if (motionX == 0 && motionY == 0 && motionZ == 0) continue;

            Render3DUtil.drawLine(lastPos, pos, hand.getValue());
        }
    }

    private boolean isThrowable(Item item) {
        // 26.2: EnderPearlItem → EnderpearlItem
        return item instanceof EnderpearlItem || item instanceof TridentItem || item instanceof ExperienceBottleItem || item instanceof SnowballItem || item instanceof EggItem || item instanceof SplashPotionItem || item instanceof LingeringPotionItem;
    }

    private float getDistance(Item item) {
        return item instanceof BowItem ? 1.0f : 0.4f;
    }

    private float getThrowVelocity(Item item) {
        if (item instanceof SplashPotionItem || item instanceof LingeringPotionItem) return 0.5f;
        if (item instanceof ExperienceBottleItem) return 0.59f;
        if (item instanceof TridentItem) return 2f;
        return 1.5f;
    }

    private int getThrowPitch(Item item) {
        if (item instanceof SplashPotionItem || item instanceof LingeringPotionItem || item instanceof ExperienceBottleItem)
            return 20;
        return 0;
    }
}