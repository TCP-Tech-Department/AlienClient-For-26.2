package dev.luminous.mod.modules.impl.render;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.TotemEvent;
import dev.luminous.api.utils.entity.CopyPlayerEntity;
import dev.luminous.api.utils.math.Animation;
import dev.luminous.api.utils.math.Easing;
import dev.luminous.api.utils.render.ModelPlayer;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.ColorSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.player.Player;

import java.awt.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class PopChams extends Module {
    public static PopChams INSTANCE;
    public final EnumSetting<Easing> ease = add(new EnumSetting<>("Ease", Easing.CubicInOut));
    private final ColorSetting fill = add(new ColorSetting("Fill", new Color(255, 255, 255, 100)).injectBoolean(true));
    private final ColorSetting line = add(new ColorSetting("Line", new Color(255, 255, 255, 100)).injectBoolean(true));
    final CopyOnWriteArrayList<Person> popList = new CopyOnWriteArrayList<>();
    private final BooleanSetting alpha = add(new BooleanSetting("Alpha", true));
    private final BooleanSetting forceSneak = add(new BooleanSetting("ForceSneak", false));
    private final BooleanSetting noSelf = add(new BooleanSetting("NoSelf", true));
    private final BooleanSetting noLimb = add(new BooleanSetting("NoLimb", true));
    private final SliderSetting fadeTime = add(new SliderSetting("FadeTime", 300, 0, 1000));
    private final SliderSetting yOffset = add(new SliderSetting("YOffset", 0, -10, 10, .01));
    private final SliderSetting scale = add(new SliderSetting("Scale", 1, 0, 2, .01));
    private final SliderSetting yaw = add(new SliderSetting("Yaw", 0, 0, 720, .01));

    public PopChams() {
        super("PopChams", Category.Render);
        setChinese("爆图腾上色");
        INSTANCE = this;
    }

    /**
     * 26.2: 旧的包夹状态
     * <pre>
     * RenderSystem.depthMask(false);  ...渲染...  RenderSystem.depthMask(true);
     * </pre>
     * 不需要了，也不是「降级」：本模块的模型由 {@link ModelPlayer} 以 gizmo 提交，
     * 而 26.2 的 gizmo 管线本身基本不写深度：
     * <ul>
     *   <li>填充 {@code RenderPipelines.DEBUG_FILLED_BOX} → {@code new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false)}</li>
     *   <li>线框：不透明 {@code LINES} 是 {@code DepthStencilState.DEFAULT}
     *       （{@code GREATER_THAN_OR_EQUAL, depthWrite = true}）；半透明走
     *       {@code LINES_TRANSLUCENT}（{@code depthWrite = false}）。
     *       {@code GizmoFeatureRenderer#buildLines} 按颜色 alpha 选管线，chams 的线色默认带 alpha，
     *       所以实际走的是不写深度那条。</li>
     * </ul>
     * 填充管线的 {@code false} 就是 {@code depthWrite}，即旧的 {@code depthMask(false)}。
     * <p>
     * 深度<b>测试</b>（是否被方块遮挡）现在由 {@code ModelPlayer#render(...)} 的最后一个
     * {@code alwaysOnTop} 参数决定：26.2 的 gizmo 只有「standard（带深度测试）」与
     * 「always_on_top（该 pass 先 clearDepthTexture）」两个去处，{@code GizmoProperties}
     * 本身没有深度 API。1.21.1 的 {@code ModelPlayer} 内部写死了
     * {@code RenderSystem.disableDepthTest()}，也就是说旧 PopChams 其实一直是「永远在最上层」。
     * 现在改为跟随 {@code Chams.throughWall}，见下面 {@code Person#render} 的注释。
     */
    @Override
    public void onRender3D(PoseStack matrixStack) {
        popList.removeIf(person -> person.render(matrixStack));
    }

    @EventListener
    private void onTotemPop(TotemEvent event) {
        if (noSelf.getValue() && event.getPlayer().equals(mc.player)) return;

        popList.add(new Person(new CopyPlayerEntity(event.getPlayer())));
    }

    private class Person {
        public final ModelPlayer modelPlayer;
        final Animation animation;

        public Person(Player player) {
            modelPlayer = new ModelPlayer(player);
            animation = new Animation();
        }

        public boolean render(PoseStack matrixStack) {
            double animation = this.animation.get(1, fadeTime.getValueInt(), ease.getValue());
            if (animation >= 1) return true;
            // 最后一个参数是 26.2 的 alwaysOnTop（等价旧 ModelPlayer 内部写死的 disableDepthTest）。
            // PopChams 没有自己的穿墙开关，这里跟随客户端的 Chams 穿墙开关：
            // throughWall 关闭 ⇒ 走 gizmo 的普通深度阶段，残影会被方块正常遮挡；
            // throughWall 开启 ⇒ 进 always_on_top 阶段（该 pass 会先 clearDepthTexture），穿墙可见。
            boolean alwaysOnTop = Chams.INSTANCE != null && Chams.INSTANCE.throughWall.getValue();
            modelPlayer.render(matrixStack, fill, line, alpha.getValue() ? (1 - animation) : 1, yOffset.getValue() * animation, 1 + (scale.getValue() - 1) * animation, yaw.getValue() * animation, noLimb.getValue(), forceSneak.getValue(), alwaysOnTop);
            return false;
        }
    }
}
