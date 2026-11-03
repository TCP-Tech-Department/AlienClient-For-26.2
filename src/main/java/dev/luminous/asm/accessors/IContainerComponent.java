package dev.luminous.asm.accessors;

import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.ItemContainerContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.Optional;

/*
 * 26.2: net.minecraft.component.type.ContainerComponent → net.minecraft.world.item.component.ItemContainerContents。
 *
 * 旧的 stacks 字段（DefaultedList<ItemStack>，可写）在 26.2 被换成
 * private final List<Optional<ItemStackTemplate>> items（ItemContainerContents.java:35），
 * 空槽位是 Optional.empty()，元素是 ItemStackTemplate 而不是 ItemStack，且这个 List 由
 * fromSlots / emptyContents 建成不可变列表 —— 所以“就地改格子”这条路在 26.2 根本不存在，
 * 本 accessor 只保留直接读取内部列表（含空槽位布局）的能力，等价于旧的 getStacks() 读语义。
 *
 * 读：优先用公开 API —— copyInto(NonNullList<ItemStack>) / allItemsCopyStream() /
 *     nonEmptyItemCopyStream() / nonEmptyItems()（ShulkerViewer 等调用方已经这么用）。
 * 写（XCarry / PeekScreen 之类需要改容器内容时）26.2 的等价做法是“重建整个组件”：
 *     ItemContainerContents.fromItems(List<ItemStack>)  ← 26.2 新增的公开工厂
 *     stack.set(DataComponents.CONTAINER, newContents);
 * 目前项目内没有调用方需要写，故本接口不提供 setter（容器组件本身是不可变 record 语义）。
 */
@Mixin(ItemContainerContents.class)
public interface IContainerComponent {
    @Accessor("items")
    List<Optional<ItemStackTemplate>> getItems();
}
