package dev.luminous.mod.modules.impl.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.ColorSetting;
import net.minecraft.gizmos.CuboidGizmo;
import net.minecraft.gizmos.GizmoProperties;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.awt.*;

/**
 * 方块高亮：绘制准星指向方块的描边 + 填充。
 * <p>
 * 26.2 迁移：旧实现用 {@code Tessellator} 手工拼 6 个 quad + 12 条线，并自行减去相机位置。
 * 现改为提交一个 {@link CuboidGizmo}：gizmo 直接使用世界坐标，描边与填充由
 * {@link GizmoStyle} 一次表达，深度测试开关映射为 {@link GizmoProperties#setAlwaysOnTop()}。
 * <p>
 * {@code matrixStack} 参数保留以匹配 {@link Module#onRender3D} 的既有签名，gizmo 路径不再使用它。
 */
public class HighLight extends Module {
    public static HighLight INSTANCE;
    private final BooleanSetting depth = add(new BooleanSetting("Depth", true));
    private final ColorSetting fill = add(new ColorSetting("Fill", new Color(255, 0, 0, 50)).injectBoolean(true));
    private final ColorSetting boxColor = add(new ColorSetting("AABB", new Color(255, 0, 0, 100)).injectBoolean(true));

    public HighLight() {
        super("HighLight", Category.Render);
        INSTANCE = this;
        setChinese("方块高亮");
    }

    @Override
    public void onRender3D(PoseStack matrixStack) {
        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }
        if (!(mc.hitResult instanceof BlockHitResult hitResult)) {
            return;
        }
        if (!fill.booleanValue && !boxColor.booleanValue) {
            return;
        }

        // 26.2：getOutlineShape(...) 改名为 getShape(...)；VoxelShape.getBoundingBox() 改名为 bounds()
        // 26.2: BlockHitResult#blockPosition → getBlockPos
        VoxelShape shape = mc.level.getBlockState(hitResult.getBlockPos())
                .getShape(mc.level, hitResult.getBlockPos());
        if (shape.isEmpty()) {
            return;
        }

        // 26.2：AABB.offset(...) 改名为 move(...)；expand(double) 改名为 inflate(double)
        AABB box = shape.bounds().move(hitResult.getBlockPos()).inflate(0.001);

        int stroke = boxColor.booleanValue ? boxColor.getValue().getRGB() : 0;
        int fillArgb = fill.booleanValue ? fill.getValue().getRGB() : 0;
        // GizmoStyle(stroke, strokeWidth, fill)：stroke / fill 为 0 时该项不绘制
        GizmoStyle style = new GizmoStyle(stroke, stroke != 0 ? 1.5f : 0.0f, fillArgb);

        try (Gizmos.TemporaryCollection ignored = mc.levelRenderer.collectPerFrameRenderThreadGizmos()) {
            GizmoProperties properties = Gizmos.addGizmo(new CuboidGizmo(box, style, false));
            if (!depth.getValue()) {
                properties.setAlwaysOnTop();
            }
        }
    }
}
