package dev.luminous.asm.accessors;

import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/*
 * 26.2: net.minecraft.client.render.item.HeldItemRenderer -> net.minecraft.client.renderer.ItemInHandRenderer，
 * 字段改名：
 *   equipProgressMainHand -> mainHandHeight
 *   equipProgressOffHand  -> offHandHeight
 *   mainHand              -> mainHandItem
 *   offHand               -> offHandItem
 * 方法名保持不变（ViewModel 直接复用），语义都是 0..1 的装备动画进度。
 *
 * 调用方需要同步：ViewModel 里的
 * {@code mc.getEntityRenderDispatcher().getHeldItemRenderer()} 在 26.2 改名为
 * {@code mc.getEntityRenderDispatcher().getItemInHandRenderer()}。
 */
@Mixin(ItemInHandRenderer.class)
public interface IHeldItemRenderer {
    @Accessor("mainHandHeight")
    float getEquippedProgressMainHand();

    @Accessor("mainHandHeight")
    void setEquippedProgressMainHand(float value);

    @Accessor("offHandHeight")
    float getEquippedProgressOffHand();

    @Accessor("offHandHeight")
    void setEquippedProgressOffHand(float value);

    @Accessor("mainHandItem")
    void setItemStackMainHand(ItemStack stack);

    @Accessor("offHandItem")
    void setItemStackOffHand(ItemStack stack);
}
