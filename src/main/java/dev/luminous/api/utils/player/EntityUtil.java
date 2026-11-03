package dev.luminous.api.utils.player;

import dev.luminous.api.utils.Wrapper;
import dev.luminous.api.utils.world.BlockPosX;
import dev.luminous.api.utils.world.BlockUtil;
import dev.luminous.mod.gui.ClickGuiScreen;
import dev.luminous.mod.gui.PeekScreen;
import dev.luminous.mod.modules.impl.client.AntiCheat;
import dev.luminous.mod.modules.settings.enums.SwingSide;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class EntityUtil implements Wrapper {

    public static boolean inInventory() {
        return mc.gui.screen() == null || mc.gui.screen() instanceof OptionsSubScreen || mc.gui.screen() instanceof OptionsScreen || mc.gui.screen() instanceof PeekScreen || mc.gui.screen() instanceof ChatScreen || mc.gui.screen() instanceof InventoryScreen || mc.gui.screen() instanceof ClickGuiScreen || mc.gui.screen() instanceof PauseScreen;
    }

    public static boolean isHoldingWeapon(Player player) {
        ItemStack stack = player.getMainHandItem();
        return stack.getItem().builtInRegistryHolder().is(ItemTags.SWORDS) || stack.getItem() instanceof AxeItem || stack.getItem() instanceof MaceItem || stack.getItem() instanceof TridentItem;
    }

    public static boolean isInsideBlock(Player player) {
        return BlockUtil.canCollide(player, player.getBoundingBox());
    }

    public static boolean isInsideBlock() {
        return isInsideBlock(mc.player);
    }

    public static int getDamagePercent(ItemStack stack) {
        if (stack.getDamageValue() == stack.getMaxDamage()) return 100;
        return (int) ((stack.getMaxDamage() - stack.getDamageValue()) / Math.max(0.1, stack.getMaxDamage()) * 100.0f);
    }

    public static boolean isArmorLow(Player player, int durability) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack piece = player.getItemBySlot(slot);
            if (piece == null || piece.isEmpty()) {
                return true;
            }

            if (getDamagePercent(piece) >= durability) continue;
            return true;
        }
        return false;
    }

    public static float getHealth(Entity entity) {
        if (entity.isAlive()) {
            LivingEntity livingBase = (LivingEntity) entity;
            return livingBase.getHealth() + livingBase.getAbsorptionAmount();
        }
        return 0.0f;
    }

    public static BlockPos getEntityPos(Entity entity) {
        return new BlockPosX(entity.position());
    }

    public static BlockPos getPlayerPos(boolean fix) {
        return new BlockPosX(mc.player.position(), fix);
    }

    public static BlockPos getEntityPos(Entity entity, boolean fix) {
        return new BlockPosX(entity.position(), fix);
    }

    public static boolean canSee(BlockPos pos, Direction side) {
        Vec3 testVec = Vec3.atCenterOf(pos).add(side.getUnitVec3().x * 0.5, side.getUnitVec3().y * 0.5, side.getUnitVec3().z * 0.5);
        BlockHitResult result = mc.level.clip(new ClipContext(mc.player.getEyePosition(), testVec, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        return result == null || result.getType() == HitResult.Type.MISS;
    }

    public static void swingHand(InteractionHand hand, SwingSide side) {
        switch (side) {
            case All -> mc.player.swing(hand);
            case Client -> mc.player.swing(hand, false);
            case Server -> mc.getConnection().send(new ServerboundSwingPacket(hand));
        }
    }

    public static void syncInventory() {
        if (AntiCheat.INSTANCE.closeScreen.getValue())
            mc.getConnection().send(new ServerboundContainerClosePacket(mc.player.containerMenu.containerId));
    }

    /** 客户端自建实体的下一个 id（负 id，与服务端分配的正 id 永不冲突）。 */
    private static int alienClient$nextClientEntityId = -1;

    /**
     * 把「客户端自己 new 出来的实体」加进当前世界（假人、闪电特效等）。
     *
     * <p><b>26.2 变化：</b>{@code ClientLevel#addEntity} 内部第一句就是
     * {@code this.removeEntity(entity.getId(), ...)}，而 {@code Entity#getId()} 在 id 未分配（== 0）时
     * 直接抛 {@code IllegalStateException("Tried to access entity ID before ID assignment")}。
     * 1.21.1 的 {@code ClientLevel#addEntity} 不读 id，所以旧代码 {@code mc.level.addEntity(entity)} 能跑；
     * 26.2 起必须**先给实体分配 id 再加**。
     *
     * <p>这里用负 id：服务端分配的实体 id 恒为正，负 id 不会撞车；
     * {@code EntityLookup} 是 {@code Int2ObjectMap}，接受任意 int。
     *
     * @return 该实体最终使用的 id（-1 表示没加进去）
     */
    public static int addClientEntity(Entity entity) {
        if (entity == null || mc.level == null) {
            return -1;
        }
        int id = alienClient$nextClientEntityId--;
        while (mc.level.getEntity(id) != null) {
            id = alienClient$nextClientEntityId--;
        }
        entity.setId(id);
        mc.level.addEntity(entity);
        return id;
    }
}
