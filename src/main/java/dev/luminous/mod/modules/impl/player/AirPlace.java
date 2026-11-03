package dev.luminous.mod.modules.impl.player;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.render.Render3DUtil;
import dev.luminous.api.utils.world.BlockUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.ColorSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.world.level.block.Blocks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.AABB;

import java.awt.*;

public class AirPlace extends Module {
    public AirPlace() {
        super("AirPlace", Category.Player);
        setChinese("空气放置");
        INSTANCE = this;
    }

    public static AirPlace INSTANCE;
    public final BooleanSetting module = add(new BooleanSetting("Module", true));
    public final BooleanSetting grimBypass = add(new BooleanSetting("GrimBypass", false));
    public final BooleanSetting crossHair = add(new BooleanSetting("Crosshair", true).setParent());
    private final SliderSetting range = add(new SliderSetting("Range", 5f, 0f, 6f, crossHair::isOpen));
    private final ColorSetting fill = add(new ColorSetting("Fill", new Color(255, 0, 0, 50), crossHair::isOpen).injectBoolean(true));
    private final ColorSetting box = add(new ColorSetting("AABB", new Color(255, 0, 0, 100), crossHair::isOpen).injectBoolean(true));

    private BlockHitResult hit;
    private int cooldown;

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (crossHair.getValue()) {
            if (cooldown > 0)
                cooldown--;

            // 26.2: Entity#raycast → Entity#pick；BlockHitResult#blockPosition() → getBlockPos()
            HitResult hitResult = mc.getCameraEntity().pick(range.getValue(), 0, false);

            if (hitResult instanceof BlockHitResult bhr) {
                hit = bhr;
            } else {
                hit = null;
            }
            if (hit == null || !mc.level.getBlockState(hit.getBlockPos()).getBlock().equals(Blocks.AIR) || (!(mc.player.getMainHandItem().getItem() instanceof BlockItem)))
                return;

            boolean main = mc.player.getMainHandItem().getItem() instanceof BlockItem;
            if (mc.options.keyUse.isDown() && main && cooldown <= 0) {
                BlockUtil.airPlace(hit.getBlockPos(), false);
                cooldown = 2;
            }
        }
    }

    @Override
    public void onRender3D(PoseStack stack) {
        if (crossHair.getValue()) {
            if (hit == null || !mc.level.getBlockState(hit.getBlockPos()).getBlock().equals(Blocks.AIR) || (!(mc.player.getMainHandItem().getItem() instanceof BlockItem)))
                return;
            Render3DUtil.draw3DBox(new AABB(hit.getBlockPos()), fill.getValue(), box.getValue(), box.booleanValue, fill.booleanValue);
        }
    }
}
