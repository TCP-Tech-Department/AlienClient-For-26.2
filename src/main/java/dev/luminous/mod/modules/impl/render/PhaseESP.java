package dev.luminous.mod.modules.impl.render;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.render.Render3DUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.ColorSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class PhaseESP extends Module {

    public static PhaseESP INSTANCE;

    private final SliderSetting distance = add(new SliderSetting("Distance", 0.1, 0, 1, .1));
    private final SliderSetting bevelDistance = add(new SliderSetting("BevelDistance", 0.2, 0, 1, .1));

    private final ColorSetting safeFill = add(new ColorSetting("SafeFill", new Color(0, 255, 0, 50)).injectBoolean(true));
    private final ColorSetting safeBox = add(new ColorSetting("SafeBox", new Color(0, 255, 0, 100)).injectBoolean(true));

    private final ColorSetting semiSafeFill = add(new ColorSetting("SemiSafeFill", new Color(244, 255, 0, 50)).injectBoolean(true));
    private final ColorSetting semiSafeBox = add(new ColorSetting("SemiSafeBox", new Color(244, 255, 0, 100)).injectBoolean(true));

    private final ColorSetting unsafeFill = add(new ColorSetting("UnsafeFill", new Color(148, 0, 0, 50)).injectBoolean(true));
    private final ColorSetting unsafeBox = add(new ColorSetting("UnsafeBox", new Color(148, 0, 0, 100)).injectBoolean(true));


    public PhaseESP() {
        super("PhaseESP", Category.Render);
        setChinese("穿墙显示");
        INSTANCE = this;
    }

    List<BlockPos> safe = new ArrayList<>();
    List<BlockPos> semiSafe = new ArrayList<>();
    List<BlockPos> unsafe = new ArrayList<>();

    int[] offsets = new int[] {1, 0, -1};
    @EventListener
    public void onUpdate(UpdateEvent event) {
        safe.clear();
        semiSafe.clear();
        unsafe.clear();

        for (int x : offsets) {
            for (int z : offsets) {
                // 26.2: BlockPos.add(x,y,z) → offset(x,y,z)，BlockPos#toBottomCenterPos() → Vec3.atBottomCenterOf()
                BlockPos pos = mc.player.blockPosition().offset(x, 0, z);
                if (mc.player.position().distanceTo(Vec3.atBottomCenterOf(pos)) <= (x != 0 && z != 0 ? bevelDistance.getValue() + 1 : distance.getValue() + 0.8)) {
                    BlockState blockState = mc.level.getBlockState(pos);

                    BlockPos downPos = pos.below();

                    if (blockState.getBlock() == Blocks.BEDROCK) {
                        Block downBlock = mc.level.getBlockState(downPos).getBlock();
                        if (downBlock == Blocks.BEDROCK) {
                            safe.add(pos);
                        } else {
                            unsafe.add(pos);
                        }
                    } else if (blockState.isCollisionShapeFullBlock(mc.level, pos)) {
                        Block downBlock = mc.level.getBlockState(downPos).getBlock();
                        if (downBlock == Blocks.BEDROCK) {
                            semiSafe.add(pos);
                        } else {
                            unsafe.add(pos);
                        }
                    }
                }
            }
        }
    }
    @Override
    public void onRender3D(PoseStack matrixStack) {
        draw(matrixStack, safe, safeFill, safeBox);
        draw(matrixStack, unsafe, unsafeFill, unsafeBox);
        draw(matrixStack, semiSafe, semiSafeFill, semiSafeBox);
    }

    private void draw(PoseStack matrixStack, List<BlockPos> list, ColorSetting fill, ColorSetting box) {
        for (BlockPos pos : list) {
            AABB espBox = new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY(), pos.getZ() + 1);
            if (fill.booleanValue) {
                Render3DUtil.drawFill(espBox, fill.getValue());
            }
            if (box.booleanValue) {
                Render3DUtil.drawBox(espBox, box.getValue());
            }
        }
    }

    public enum Type {
        None,
        Air,
        Normal,
        Bedrock
    }
}
