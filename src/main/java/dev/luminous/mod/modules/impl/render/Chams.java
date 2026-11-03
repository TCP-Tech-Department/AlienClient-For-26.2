package dev.luminous.mod.modules.impl.render;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.Render3DEvent;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.ColorSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.cubemob.Slime;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import net.minecraft.world.entity.player.Player;

import java.awt.*;
import java.util.HashSet;
import java.util.Set;

public class Chams extends Module {
    public static Chams INSTANCE;
    public final BooleanSetting crystal = add(new BooleanSetting("Crystal", true).setParent());
    public final BooleanSetting custom = add(new BooleanSetting("Custom", false, crystal::isOpen).setParent());
    public final BooleanSetting depth = add(new BooleanSetting("Depth", false, () -> crystal.isOpen() && custom.isOpen()));
    public final BooleanSetting chamsTexture = add(new BooleanSetting("ChamsTexture", true, () -> crystal.isOpen() && custom.isOpen()));
    public final ColorSetting fill = add(new ColorSetting("Fill", new Color(255, 255, 255, 100), () -> crystal.isOpen() && custom.isOpen()).injectBoolean(false));
    public final ColorSetting line = add(new ColorSetting("Line", new Color(255, 255, 255, 100), () -> crystal.isOpen() && custom.isOpen()).injectBoolean(false));
    public final ColorSetting core =
            add(new ColorSetting("Core", new Color(255, 255, 255, 255), crystal::isOpen).injectBoolean(true));
    public final ColorSetting outerFrame =
            add(new ColorSetting("OuterFrame", new Color(255, 255, 255, 255), crystal::isOpen).injectBoolean(true));
    public final ColorSetting innerFrame =
            add(new ColorSetting("InnerFrame", new Color(255, 255, 255, 255), crystal::isOpen).injectBoolean(true));
    public final BooleanSetting glint = add(new BooleanSetting("Glint", true, crystal::isOpen));
    public final BooleanSetting texture = add(new BooleanSetting("Texture", true, crystal::isOpen));
    public final BooleanSetting spinSync = add(new BooleanSetting("SpinSync", false, crystal::isOpen));
    public final SliderSetting scale = add(new SliderSetting("Scale", 1, 0, 3f, 0.01, crystal::isOpen));
    public final SliderSetting spinValue = add(new SliderSetting("SpinSpeed", 1f, 0, 3f, 0.01, crystal::isOpen));
    public final SliderSetting bounceHeight = add(new SliderSetting("BounceHeight", 1, 0, 3f, 0.01, crystal::isOpen));
    public final SliderSetting floatValue = add(new SliderSetting("BounceSpeed", 1f, 0, 3f, 0.01, crystal::isOpen));
    public final SliderSetting floatOffset = add(new SliderSetting("YOffset", 0f, -1, 1f, 0.01, crystal::isOpen));

    public final BooleanSetting throughWall = add(new BooleanSetting("ThroughWall", false).setParent());
    private final BooleanSetting Crystals = add(new BooleanSetting("Crystals", true, throughWall::isOpen));
    private final BooleanSetting Players = add(new BooleanSetting("Players", true, throughWall::isOpen));
    private final BooleanSetting Mobs = add(new BooleanSetting("Mobs", true, throughWall::isOpen));
    private final BooleanSetting Animals = add(new BooleanSetting("Animals", true, throughWall::isOpen));
    private final BooleanSetting Villagers = add(new BooleanSetting("Villagers", true, throughWall::isOpen));
    private final BooleanSetting Slimes = add(new BooleanSetting("Slimes", true, throughWall::isOpen));

    public final ColorSetting hand = add(new ColorSetting("Hand", -1).injectBoolean(true));

    /**
     * 本帧需要「穿墙绘制」的实体类型。
     * <p>
     * 26.2 的实体提交阶段（{@code LevelRenderer#submitEntities}）只有
     * {@code EntityRenderState}（含 {@code entityType}），拿不到 {@code Entity} 实例，
     * 而 {@link #chams(Entity)} 的过滤是 instanceof 的。所以在
     * {@link #onRender3D}（每帧 extract 阶段，早于 {@code LevelExtractor#extract}）
     * 用真实实体把类型解析一次缓存下来，提交阶段只按 {@code EntityType} 查表。
     * 这就是 A1 报告建议的「按 EntityType 过滤」。
     */
    private final Set<EntityType<?>> throughWallTypes = new HashSet<>();

    public Chams() {
        super("Chams", Category.Render);
        setChinese("模型上色");
        INSTANCE = this;
    }

    public boolean customCrystal() {
        return isOn() && crystal.getValue();
    }

    public int age;

    @EventListener
    public void onUpdate(UpdateEvent event) {
        age++;
    }

    /**
     * 26.2: 这里原本是 {@code RenderSystem.setShaderColor(...)}，用来给第一人称手部 / 手持物染色。
     * 26.2 的 {@code RenderSystem} 已经没有颜色状态（见 {@code 26.2/com/mojang/blaze3d/systems/RenderSystem.java}，
     * 逐 draw 的 {@code ColorModulator} 由 {@code RenderType#writeTransform} 写死为白色），
     * 颜色只能写进顶点 / 模型的 tintedColor。
     * <p>
     * 现在的实现位置是 {@code MixinHeldItemRenderer}（{@code ItemInHandRenderer#submitArmWithItem}），
     * 在那里用 {@code SubmitNodeCollector} 代理把半透明手臂模型的 {@code tintedColor}
     * 与手持物每个 quad 的 {@code QuadInstance} 颜色乘以 {@link #hand} 的颜色。
     * 本方法保留下来负责两件事：
     * <ol>
     *   <li>维护 {@link #throughWallTypes}（穿墙的 EntityType 过滤表）；</li>
     *   <li>作为「本帧渲染阶段开始」的时机点（事件 priority 为 MIN_VALUE，最后执行）。</li>
     * </ol>
     */
    @EventListener(priority = Integer.MIN_VALUE)
    public void onRender3D(Render3DEvent event) {
        this.throughWallTypes.clear();
        if (!isOn() || !throughWall.getValue() || mc.level == null) {
            return;
        }
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (chams(entity)) {
                this.throughWallTypes.add(entity.getType());
            }
        }
    }

    public boolean chams(Entity entity) {
        if (entity instanceof EndCrystal) return Crystals.getValue();
        if (entity instanceof Slime) return Slimes.getValue();
        if (entity instanceof Player) return Players.getValue();
        if (entity instanceof Villager || entity instanceof WanderingTrader) return Villagers.getValue();
        if (entity instanceof Animal) return Animals.getValue();
        if (entity instanceof Mob) return Mobs.getValue();
        return false;
    }

    /**
     * 提交阶段（只有 {@code EntityRenderState}）使用的 EntityType 版本过滤，
     * 由 {@code MixinWorldRenderer} 在 {@code LevelRenderer#submitEntities} 调用。
     *
     * <p>语义：模块开启 + {@code throughWall} 开启 + 该类型在本帧被 {@link #chams(Entity)} 选中。
     * 未开启时恒 false ⇒ 实体只在正常深度阶段提交一次 ⇒ 被方块正常遮挡（旧 1.21.1 的
     * 「没有 polygon offset」行为）。
     */
    public boolean chams(EntityType<?> type) {
        return isOn() && throughWall.getValue() && type != null && this.throughWallTypes.contains(type);
    }
}
