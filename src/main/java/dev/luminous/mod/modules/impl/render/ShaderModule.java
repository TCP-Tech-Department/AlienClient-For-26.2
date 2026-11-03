package dev.luminous.mod.modules.impl.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.luminous.Alien;
import dev.luminous.core.impl.ShaderManager;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.ColorSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

import java.awt.*;

public class ShaderModule extends Module {

    public static ShaderModule INSTANCE;
    private final EnumSetting<Page> page = add(new EnumSetting<>("Page", Page.Shader));
    public final EnumSetting<ShaderManager.Shader> mode = add(new EnumSetting<>("Mode", ShaderManager.Shader.Solid, () -> page.getValue() == Page.Shader));
    public final SliderSetting speed = add(new SliderSetting("Speed", 4, 0, 20, 0.1, () -> page.getValue() == Page.Shader));
    public final ColorSetting fill = add(new ColorSetting("Color", () -> page.getValue() == Page.Shader));
    public final SliderSetting maxSample =
            add(new SliderSetting("MaxSample", 10.0f, 0f, 20.0f, () -> page.getValue() == Page.Shader));
    public final SliderSetting divider =
            add(new SliderSetting("Divider", 150.0f, 0f, 300.0f, () -> page.getValue() == Page.Shader));
    public final SliderSetting radius =
            add(new SliderSetting("Radius", 2.0f, 0f, 6.0f, () -> page.getValue() == Page.Shader));
    public final SliderSetting smoothness =
            add(new SliderSetting("Smoothness", 1.0f, 0f, 1.0f, 0.01, () -> page.getValue() == Page.Shader));
    public final SliderSetting alpha =
            add(new SliderSetting("GlowAlpha", 255, 0, 255, () -> page.getValue() == Page.Shader));
    public final SliderSetting maxRange = add(new SliderSetting("MaxRange", 64, 16, 512, () -> page.getValue() == Page.Target));

    public final SliderSetting pulseSpeed = add(new SliderSetting("PulseSize", 10, 0, 200, 0.1, () -> page.getValue() == Page.Color));
    public final ColorSetting pulse = add(new ColorSetting("Pulse", new Color(255, 255, 255), () -> page.getValue() == Page.Color));
    public final SliderSetting step =
            add(new SliderSetting("Step", 0.5f, 0f, 4.0f, 0.01f, () -> page.getValue() == Page.Color));
    public final SliderSetting octaves = add(new SliderSetting("Octaves", 10, 5, 30, () -> page.getValue() == Page.Color));
    public final ColorSetting smoke1 = add(new ColorSetting("Color1", new Color(255, 255, 255), () -> page.getValue() == Page.Color));
    public final ColorSetting smoke2 = add(new ColorSetting("Color2", new Color(255, 255, 255), () -> page.getValue() == Page.Color));
    public final ColorSetting smoke3 = add(new ColorSetting("Color3", new Color(255, 255, 255), () -> page.getValue() == Page.Color));
    public final ColorSetting smoke4 = add(new ColorSetting("Color4", new Color(255, 255, 255), () -> page.getValue() == Page.Color));

    private final BooleanSetting hands = add(new BooleanSetting("Hands", true, () -> page.getValue() == Page.Target));
    private final BooleanSetting self = add(new BooleanSetting("Self", true, () -> page.getValue() == Page.Target));
    private final BooleanSetting players = add(new BooleanSetting("Players", true, () -> page.getValue() == Page.Target));
    private final BooleanSetting friends = add(new BooleanSetting("Friends", true, () -> page.getValue() == Page.Target));
    private final BooleanSetting crystals = add(new BooleanSetting("Crystals", true, () -> page.getValue() == Page.Target));
    private final BooleanSetting creatures = add(new BooleanSetting("Creatures", false, () -> page.getValue() == Page.Target));
    private final BooleanSetting monsters = add(new BooleanSetting("Monsters", false, () -> page.getValue() == Page.Target));
    private final BooleanSetting ambients = add(new BooleanSetting("Ambients", false, () -> page.getValue() == Page.Target));
    private final BooleanSetting items = add(new BooleanSetting("Items", true, () -> page.getValue() == Page.Target));
    private final BooleanSetting others = add(new BooleanSetting("Others", false, () -> page.getValue() == Page.Target));

    public ShaderModule() {
        super("Shader", Category.Render);
        setChinese("着色器");
        INSTANCE = this;
    }

    @Override
    public String getInfo() {
        return mode.getValue().name();
    }

    public boolean shouldRender(Entity entity) {
        if (entity == null)
            return false;

        if (mc.player == null)
            return false;

        if (Mth.sqrt((float) mc.player.distanceToSqr(entity.position())) > maxRange.getValue())
            return false;

        switch (entity) {
            case Player playerEntity -> {
                if (entity == mc.player)
                    return self.getValue();
                if (Alien.FRIEND.isFriend(playerEntity))
                    return friends.getValue();
                return players.getValue();
            }
            case EndCrystal endCrystalEntity -> {
                return crystals.getValue();
            }
            case ItemEntity itemEntity -> {
                return items.getValue();
            }
            default -> {
            }
        }

        return switch (entity.getType().getCategory()) {
            case CREATURE, WATER_CREATURE -> creatures.getValue();
            case MONSTER -> monsters.getValue();
            case AMBIENT, WATER_AMBIENT -> ambients.getValue();
            default -> others.getValue();
        };
    }

    /**
     * 26.2：实体部分直接复用原版发光描边缓冲（{@code MixinEntity#isCurrentlyGlowing}
     * 让选中的实体进入 outline pass），手部则由 {@link ShaderManager} 在
     * {@code alien:hands} 离屏目标里用同一个 {@code ItemInHandRenderer} 再画一次。
     * 这里只负责排队，真正的离屏绑定 / chain 执行在 {@code ShaderManager#renderShaders()}。
     */
    @Override
    public void onRender3D(PoseStack matrixStack) {
        if (!isOn() || mc.player == null || mc.level == null || Alien.SHADER == null) {
            return;
        }
        Alien.SHADER.renderShader(this::renderHandsSilhouette, mode.getValue());
    }

    /** 把第一人称手部重绘到 ShaderManager 绑定的离屏目标（等价于旧的 {@code IRenderHand}）。 */
    private void renderHandsSilhouette() {
        if (!hands.getValue() || mc.player == null || mc.gameMode == null) {
            return;
        }
        var renderState = mc.gameRenderer.gameRenderState();
        CameraRenderState cameraState = renderState.levelRenderState.cameraRenderState;
        if (cameraState.isPanoramicMode
                || !renderState.optionsRenderState.cameraType.isFirstPerson()
                || cameraState.entityRenderState.isSleeping
                || renderState.guiRenderState.isHudHidden
                || mc.gameMode.getPlayerMode() == GameType.SPECTATOR) {
            return;
        }

        float tickDelta = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Matrix4f viewRotation = cameraState.viewRotationMatrix;
        PoseStack poseStack = new PoseStack();
        poseStack.mulPose(viewRotation.invert(new Matrix4f()));

        Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
        modelViewStack.pushMatrix().mul(viewRotation);
        try {
            SubmitNodeStorage storage = new SubmitNodeStorage();
            mc.gameRenderer.itemInHandRenderer.submitHandsWithItems(
                    tickDelta,
                    poseStack,
                    storage,
                    mc.player,
                    mc.getEntityRenderDispatcher().getPackedLightCoords(mc.player, tickDelta));
            mc.gameRenderer.featureRenderDispatcher().renderAllFeatures(storage);
        } finally {
            modelViewStack.popMatrix();
        }
    }

    @Override
    public void onToggle() {
        Alien.SHADER.reloadShaders();
    }

    @Override
    public void onLogin() {
        Alien.SHADER.reloadShaders();
    }

    private enum Page {
        Shader,
        Target,
        Color
    }
}
