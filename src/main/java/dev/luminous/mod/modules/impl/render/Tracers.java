package dev.luminous.mod.modules.impl.render;

import dev.luminous.Alien;
import dev.luminous.api.utils.render.Render3DUtil;
import dev.luminous.api.utils.world.BlockUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.ColorSetting;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.EnderChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.awt.*;
import java.util.ArrayList;

public class Tracers extends Module {
    private final ColorSetting item = add(new ColorSetting("Item", new Color(255, 255, 255, 100)).injectBoolean(true));
    private final ColorSetting player = add(new ColorSetting("Player", new Color(255, 255, 255, 100)).injectBoolean(true));
    private final ColorSetting chest = add(new ColorSetting("Chest", new Color(255, 255, 255, 100)).injectBoolean(false));
    private final ColorSetting enderChest = add(new ColorSetting("EnderChest", new Color(255, 100, 255, 100)).injectBoolean(false));
    private final ColorSetting shulkerBox = add(new ColorSetting("ShulkerBox", new Color(15, 255, 255, 100)).injectBoolean(false));

    public Tracers() {
        super("Tracers", Category.Render);
        setChinese("追踪者");
    }

    @Override
    public void onRender3D(PoseStack matrixStack) {
        // 26.2: Options.getBobView().setValue(v) → bobView().set(v)
        mc.options.bobView().set(false);
        if (item.booleanValue || player.booleanValue) {
            for (Entity entity : Alien.THREAD.getEntities()) {
                if (entity instanceof ItemEntity && item.booleanValue) {
                    drawLine(entity.position(), item.getValue());
                } else if (entity instanceof Player && player.booleanValue && entity != mc.player) {
                    drawLine(entity.position(), player.getValue());
                }
            }
        }
        ArrayList<BlockEntity> blockEntities = BlockUtil.getTileEntities();
        for (BlockEntity blockEntity : blockEntities) {
            // 26.2: BlockEntity.getPos()/position() → getBlockPos()，Vec3#toCenterPos() → Vec3.atCenterOf()
            if (blockEntity instanceof ChestBlockEntity && chest.booleanValue) {
                drawLine(Vec3.atCenterOf(blockEntity.getBlockPos()), chest.getValue());
            } else if (blockEntity instanceof EnderChestBlockEntity && enderChest.booleanValue) {
                drawLine(Vec3.atCenterOf(blockEntity.getBlockPos()), enderChest.getValue());
            } else if (blockEntity instanceof ShulkerBoxBlockEntity && shulkerBox.booleanValue) {
                drawLine(Vec3.atCenterOf(blockEntity.getBlockPos()), shulkerBox.getValue());
            }
        }
    }

    private void drawLine(Vec3 pos, Color color) {
        // 26.2: GameRenderer.getCamera() → mainCamera()；Vec3.fromPolar(pitch,yaw) → Vec3.directionFromRotation(pitch,yaw)
        float partialTick = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Vec3 direction = Vec3.directionFromRotation(mc.player.getXRot(partialTick), mc.player.getYRot(partialTick));
        // 26.2: Vec3.multiply(double) 不存在标量重载，标量缩放用 scale(double)
        Render3DUtil.drawLine(pos, mc.gameRenderer.mainCamera().position().add(direction.scale(0.2)), color);
    }
}
