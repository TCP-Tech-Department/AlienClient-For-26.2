package dev.luminous.mod.modules.impl.render;

import dev.luminous.api.utils.math.Easing;
import dev.luminous.api.utils.math.FadeUtils;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.render.ColorUtil;
import dev.luminous.api.utils.render.Render3DUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.ColorSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import java.awt.*;
import java.util.HashMap;

public class PlaceRender extends Module {
    public static final HashMap<BlockPos, PlacePos> renderMap = new HashMap<>();
    public static PlaceRender INSTANCE;
    public final SliderSetting fadeTime = add(new SliderSetting("FadeTime", 500, 0, 3000));
    public final SliderSetting timeout = add(new SliderSetting("TimeOut", 500, 0, 3000));
    private final ColorSetting box = add(new ColorSetting("AABB", new Color(255, 255, 255, 255)).injectBoolean(true));
    private final ColorSetting fill = add(new ColorSetting("Fill", new Color(255, 255, 255, 100)).injectBoolean(true));
    private final ColorSetting tryPlaceBox = add(new ColorSetting("TryPlaceBox", new Color(178, 178, 178, 255)).injectBoolean(true));
    private final ColorSetting tryPlaceFill = add(new ColorSetting("TryPlaceFill", new Color(255, 119, 119, 157)).injectBoolean(true));
    private final BooleanSetting noFail = add(new BooleanSetting("NoFail", false));
    private final EnumSetting<Easing> ease = add(new EnumSetting<>("Ease", Easing.CubicInOut));
    private final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", Mode.All));

    public PlaceRender() {
        super("PlaceRender", Category.Render);
        setChinese("放置显示");
        enable();
        INSTANCE = this;
    }

    @Override
    public void onRender3D(PoseStack matrixStack) {
        renderMap.values().removeIf(v -> v.draw(matrixStack));
    }

    public void create(BlockPos pos) {
        renderMap.put(pos, new PlacePos(pos));
    }

    private enum Mode {
        Fade,
        Shrink,
        All,
    }

    public class PlacePos {
        public final FadeUtils fade;
        public final BlockPos pos;
        public final Timer timer;
        public boolean isAir;

        public PlacePos(BlockPos placePos) {
            this.fade = new FadeUtils((long) fadeTime.getValue());
            this.pos = placePos;
            this.timer = new Timer();
            this.isAir = true;
        }

        public boolean draw(PoseStack matrixStack) {
            if (isAir) {
                if (noFail.getValue() || !mc.level.getBlockState(pos).isAir()) {
                    isAir = false;
                } else {
                    if (!timer.passedMs(timeout.getValue())) {
                        fade.reset();
                        AABB aBox = new AABB(pos);
                        if (tryPlaceFill.booleanValue) {
                            Render3DUtil.drawFill(aBox, tryPlaceFill.getValue());
                        }
                        if (tryPlaceBox.booleanValue) {
                            Render3DUtil.drawBox(aBox, tryPlaceBox.getValue());
                        }
                    }
                    return false;
                }
            }
            double quads = fade.ease(ease.getValue());
            if (quads == 1) return true;
            double alpha = (mode.getValue() == Mode.Fade || mode.getValue() == Mode.All) ? 1 - quads : 1;
            double size = (mode.getValue() == Mode.Shrink || mode.getValue() == Mode.All) ? quads : 0;
            // 26.2: AABB.expand(x,y,z) → inflate(x,y,z)
            AABB aBox = new AABB(pos).inflate(-size * 0.5, -size * 0.5, -size * 0.5);
            if (fill.booleanValue) {
                Render3DUtil.drawFill(aBox, ColorUtil.injectAlpha(fill.getValue(), (int) ((double) fill.getValue().getAlpha() * alpha)));
            }
            if (box.booleanValue) {
                Render3DUtil.drawBox(aBox, ColorUtil.injectAlpha(box.getValue(), (int) ((double) box.getValue().getAlpha() * alpha)));
            }
            return false;
        }
    }
}
