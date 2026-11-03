package dev.luminous.mod.modules.impl.render;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.EntitySpawnedEvent;
import dev.luminous.api.utils.math.MathUtil;
import dev.luminous.api.utils.render.Render3DUtil;
import dev.luminous.api.utils.world.BlockUtil;
import dev.luminous.asm.accessors.IEntity;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.ColorSetting;
import net.minecraft.world.level.block.entity.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;

public class ESP extends Module {
    public static ESP INSTANCE;

    public final BooleanSetting box = add(new BooleanSetting("BoxESP", true).setParent());
    private final ColorSetting endPortalFill = add(new ColorSetting("EndPortalFill", new Color(255, 243, 129, 100), box::isOpen).injectBoolean(false));
    private final ColorSetting endPortalOutline = add(new ColorSetting("EndPortalOutline", new Color(255, 243, 129, 100), box::isOpen).injectBoolean(false));
    private final ColorSetting itemFill = add(new ColorSetting("ItemFill", new Color(255, 255, 255, 100), box::isOpen).injectBoolean(true));
    private final ColorSetting itemOutline = add(new ColorSetting("ItemOutline", new Color(255, 255, 255, 100), box::isOpen).injectBoolean(true));
    private final ColorSetting playerFill = add(new ColorSetting("PlayerFill", new Color(255, 255, 255, 100), box::isOpen).injectBoolean(true));
    private final ColorSetting playerOutline = add(new ColorSetting("PlayerOutline", new Color(255, 255, 255, 100), box::isOpen).injectBoolean(true));
    private final ColorSetting chestFill = add(new ColorSetting("ChestFill", new Color(255, 198, 123, 100), box::isOpen).injectBoolean(false));
    private final ColorSetting chestOutline = add(new ColorSetting("ChestOutline", new Color(255, 198, 123, 100), box::isOpen).injectBoolean(false));
    private final ColorSetting enderChestFill = add(new ColorSetting("EnderChestFill", new Color(255, 100, 255, 100), box::isOpen).injectBoolean(false));
    private final ColorSetting enderChestOutline = add(new ColorSetting("EnderChestOutline", new Color(255, 100, 255, 100), box::isOpen).injectBoolean(false));
    private final ColorSetting shulkerBoxFill = add(new ColorSetting("ShulkerBoxFill", new Color(15, 255, 255, 100), box::isOpen).injectBoolean(false));
    private final ColorSetting shulkerBoxOutline = add(new ColorSetting("ShulkerBoxOutline", new Color(15, 255, 255, 100), box::isOpen).injectBoolean(false));
    public final BooleanSetting item = add(new BooleanSetting("ItemName", false).setParent());
    public final BooleanSetting customName = add(new BooleanSetting("CustomName", false, item::isOpen));
    public final BooleanSetting count = add(new BooleanSetting("Count", true, item::isOpen));
    private final ColorSetting text = add(new ColorSetting("Text", new Color(255, 255, 255, 255), item::isOpen));
    public final BooleanSetting pearl = add(new BooleanSetting("PearlOwner", true));

    public ESP() {
        super("ESP", Category.Render);
        setChinese("透视");
        INSTANCE = this;
    }

    @Override
    public void onRender3D(PoseStack matrixStack) {
        if (item.getValue()) {
            for (Entity entity : Alien.THREAD.getEntities()) {
                if (entity instanceof ItemEntity itemEntity) {
                    int itemCount = itemEntity.getItem().getCount();
                    String s = count.getValue() && itemCount > 1 ? " x" + itemCount : "";
                    // 26.2: ItemStack#getName → getHoverName；Item#getName(ItemStack) 需要传入物品栈
                    String name = (customName.getValue() ? itemEntity.getItem().getHoverName() : itemEntity.getItem().getItem().getName(itemEntity.getItem())).getString();

                    // 26.2: Entity#lastRenderX/Y/Z → xOld/yOld/zOld
                    Vec3 renderPos = new Vec3(
                            MathUtil.interpolate(itemEntity.xOld, itemEntity.getX(), mc.getDeltaTracker().getGameTimeDeltaPartialTick(true)),
                            MathUtil.interpolate(itemEntity.yOld, itemEntity.getY(), mc.getDeltaTracker().getGameTimeDeltaPartialTick(true)),
                            MathUtil.interpolate(itemEntity.zOld, itemEntity.getZ(), mc.getDeltaTracker().getGameTimeDeltaPartialTick(true)));
                    // 26.2: EntityDimensions#getBoxAt → makeBoundingBox；AABB#expand → inflate
                    Render3DUtil.drawText3D(name + s, ((IEntity) itemEntity).getDimensions().makeBoundingBox(renderPos).inflate(0, 0.1, 0).getCenter().add(0, 0.5, 0), text.getValue());
                }
            }
        }
        if (box.getValue()) {
            if (itemFill.booleanValue || playerFill.booleanValue) {
                for (Entity entity : Alien.THREAD.getEntities()) {
                    // 26.2: lastRenderX/Y/Z → xOld/yOld/zOld；getBoxAt → makeBoundingBox；expand → inflate
                    Vec3 renderPos = new Vec3(
                            MathUtil.interpolate(entity.xOld, entity.getX(), mc.getDeltaTracker().getGameTimeDeltaPartialTick(true)),
                            MathUtil.interpolate(entity.yOld, entity.getY(), mc.getDeltaTracker().getGameTimeDeltaPartialTick(true)),
                            MathUtil.interpolate(entity.zOld, entity.getZ(), mc.getDeltaTracker().getGameTimeDeltaPartialTick(true)));
                    if (entity instanceof ItemEntity && (itemFill.booleanValue || itemOutline.booleanValue)) {
                        Color color = this.itemFill.getValue();
                        Render3DUtil.draw3DBox(((IEntity) entity).getDimensions().makeBoundingBox(renderPos), color, itemOutline.getValue(), itemOutline.booleanValue, itemFill.booleanValue);
                    } else if (entity instanceof Player && (playerFill.booleanValue || playerOutline.booleanValue)) {
                        Color color = this.playerFill.getValue();
                        Render3DUtil.draw3DBox(((IEntity) entity).getDimensions().makeBoundingBox(renderPos).inflate(0, 0.1, 0), color, playerOutline.getValue(), playerOutline.booleanValue, playerFill.booleanValue);
                    }
                }
            }
            ArrayList<BlockEntity> blockEntities = BlockUtil.getTileEntities();
            for (BlockEntity blockEntity : blockEntities) {
                // 26.2: BlockEntity#position → getBlockPos
                if (blockEntity instanceof ChestBlockEntity && (chestFill.booleanValue || chestOutline.booleanValue)) {
                    AABB box = new AABB(blockEntity.getBlockPos());
                    Render3DUtil.draw3DBox(box, chestFill.getValue(), chestOutline.getValue(), chestOutline.booleanValue, chestFill.booleanValue);
                } else if (blockEntity instanceof EnderChestBlockEntity && (enderChestFill.booleanValue || enderChestOutline.booleanValue)) {
                    AABB box = new AABB(blockEntity.getBlockPos());
                    Render3DUtil.draw3DBox(box, enderChestFill.getValue(), enderChestOutline.getValue(), enderChestOutline.booleanValue, enderChestFill.booleanValue);
                } else if (blockEntity instanceof ShulkerBoxBlockEntity && (shulkerBoxFill.booleanValue || shulkerBoxOutline.booleanValue)) {
                    AABB box = new AABB(blockEntity.getBlockPos());
                    Render3DUtil.draw3DBox(box, shulkerBoxFill.getValue(), shulkerBoxOutline.getValue(), shulkerBoxOutline.booleanValue, shulkerBoxFill.booleanValue);
                } else if (blockEntity instanceof TheEndPortalBlockEntity && (endPortalFill.booleanValue || endPortalOutline.booleanValue)) {
                    // 26.2: EndPortalBlockEntity → TheEndPortalBlockEntity
                    AABB box = new AABB(blockEntity.getBlockPos());
                    Render3DUtil.draw3DBox(box, endPortalFill.getValue(), endPortalOutline.getValue(), endPortalOutline.booleanValue, endPortalFill.booleanValue);
                }
            }
        }
    }

    @EventListener
    public void onReceivePacket(EntitySpawnedEvent event) {
        if (nullCheck()) return;
        if (pearl.getValue() && event.getEntity() instanceof ThrownEnderpearl pearlEntity) {
            if (pearlEntity.getOwner() != null) {
                pearlEntity.setCustomName(pearlEntity.getOwner().getName());
                pearlEntity.setCustomNameVisible(true);
            } else {
                // 26.2: ClientLevel#getPlayers → players()
                mc.level.players().stream().min(Comparator.comparingDouble((p) -> p.position().distanceTo(new Vec3(pearlEntity.getX(), pearlEntity.getY(), pearlEntity.getZ())))).ifPresent((player) -> {
                    pearlEntity.setCustomName(player.getName());
                    pearlEntity.setCustomNameVisible(true);
                });
            }
        }
    }
}
