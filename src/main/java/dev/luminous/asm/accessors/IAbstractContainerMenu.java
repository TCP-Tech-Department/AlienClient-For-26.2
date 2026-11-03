package dev.luminous.asm.accessors;

import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/*
 * 26.2: AbstractContainerMenu 的容器版本号字段仍是
 *     private int stateId;                     // 26.2/net/minecraft/world/inventory/AbstractContainerMenu.java:57
 * 且没有公开 setter（只有 getStateId() / incrementStateId()，以及会连带改写槽位的
 * setItem(int,int,ItemStack) / initializeContents(...)）。
 *
 * 1.21.1 的 `playerScreenHandler.revision = packet.getRevision()` 这类「只同步版本号、不动槽位」的
 * 写法在 26.2 只能通过本 accessor 完成（AntiPacket 的 S2CInventory 重同步逻辑需要）。
 * 字段非 final，@Mutable 只是显式声明可写，避免跨版本/混淆差异导致 IllegalAccessError。
 *
 * 注意：只声明 setter。setStateId 与目标类里已有的 getStateId() 不冲突；
 * 若把 getStateId 也写进来，Mixin 会因「方法已存在于目标类」报重复方法。
 */
@Mixin(AbstractContainerMenu.class)
public interface IAbstractContainerMenu {

    @Mutable
    @Accessor("stateId")
    void setStateId(int stateId);
}
