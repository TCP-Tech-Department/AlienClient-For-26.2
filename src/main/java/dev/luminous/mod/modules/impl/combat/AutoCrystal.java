package dev.luminous.mod.modules.impl.combat;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.*;
import dev.luminous.api.utils.combat.CombatUtil;
import dev.luminous.api.utils.entity.PlayerEntityPredict;
import dev.luminous.api.utils.math.*;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.api.utils.render.ColorUtil;
import dev.luminous.api.utils.render.JelloUtil;
import dev.luminous.api.utils.render.Render3DUtil;
import dev.luminous.api.utils.world.BlockPosX;
import dev.luminous.api.utils.world.BlockUtil;
import dev.luminous.asm.accessors.IEntity;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.client.ClickGui;
import dev.luminous.mod.modules.impl.client.ClientSetting;
import dev.luminous.mod.modules.impl.exploit.Blink;
import dev.luminous.mod.modules.impl.movement.ElytraFly;
import dev.luminous.mod.modules.impl.movement.Velocity;
import dev.luminous.mod.modules.impl.player.PacketMine;
import dev.luminous.mod.modules.settings.enums.SwingSide;
import dev.luminous.mod.modules.settings.enums.Timing;
import dev.luminous.mod.modules.settings.impl.*;
import net.minecraft.world.level.block.Blocks;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;

import java.awt.*;
import java.text.DecimalFormat;
import java.util.ArrayList;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.world.level.ClipContext;

/**
 * 自动水晶 (AutoCrystal) - NCP 绕过优化版 (已修复编译错误)
 */
public class AutoCrystal extends Module {
    public static AutoCrystal INSTANCE;

    // 核心状态变量
    public BlockPos crystalPos;
    public Player displayTarget;
    public float breakDamage, tempDamage, lastDamage;
    public Vec3 directionVec = null;

    // 计时器
    public final Timer lastBreakTimer = new Timer();
    private final Timer baseTimer = new Timer();
    private final Timer placeTimer = new Timer();
    private final Timer noPosTimer = new Timer();
    private final Timer switchTimer = new Timer();
    private final Timer calcDelay = new Timer();
    private final Timer syncTimer = new Timer();

    // 临时计算变量
    private EndCrystal tempBreakCrystal;
    private EndCrystal breakCrystal;
    private BlockPos tempPos, syncPos, tempBasePos, basePos;
    private Vec3 placeVec3d, curVec3d;

    // 动画与格式化
    final Animation animation = new Animation();
    final DecimalFormat df = new DecimalFormat("0.0");
    double currentFade = 0;
    int lastSlot;

    // ==================== 设置项定义 ====================
    private final EnumSetting<Page> page = add(new EnumSetting<>("页面", Page.General));

    // --- 检查/暂停设置 ---
    private final BindSetting pause = add(new BindSetting("暂停键", -1, () -> page.is(Page.Check)));
    private final BooleanSetting preferAnchor = add(new BooleanSetting("优先锚点", true, () -> page.getValue() == Page.Check));
    private final BooleanSetting breakOnlyHasCrystal = add(new BooleanSetting("仅手持水晶时破坏", true, () -> page.getValue() == Page.Check));
    private final BooleanSetting eatingPause = add(new BooleanSetting("进食暂停", true, () -> page.getValue() == Page.Check));
    private final SliderSetting switchCooldown = add(new SliderSetting("切换冷却", 100, 0, 1000, () -> page.getValue() == Page.Check).setSuffix("ms"));
    private final SliderSetting targetRange = add(new SliderSetting("目标范围", 12.0, 0.0, 20.0, () -> page.getValue() == Page.Check).setSuffix("m"));
    private final SliderSetting updateDelay = add(new SliderSetting("更新延迟", 50, 0, 1000, () -> page.getValue() == Page.Check).setSuffix("ms"));

    // --- 旋转设置 ---
    private final BooleanSetting rotate = add(new BooleanSetting("启用旋转", true, () -> page.getValue() == Page.Rotation).setParent());
    private final BooleanSetting onPlace = add(new BooleanSetting("放置时旋转", false, () -> rotate.isOpen() && page.getValue() == Page.Rotation));
    private final BooleanSetting onBreak = add(new BooleanSetting("破坏时旋转", false, () -> rotate.isOpen() && page.getValue() == Page.Rotation));
    private final BooleanSetting yawStep = add(new BooleanSetting("平滑旋转", false, () -> rotate.isOpen() && page.getValue() == Page.Rotation).setParent());
    private final BooleanSetting whenElytra = add(new BooleanSetting("鞘翅飞行时", true, () -> rotate.isOpen() && page.getValue() == Page.Rotation && yawStep.isOpen()));
    private final SliderSetting steps = add(new SliderSetting("旋转步长", 0.05, 0, 1, 0.01, () -> rotate.isOpen() && yawStep.isOpen() && page.getValue() == Page.Rotation));
    private final BooleanSetting checkFov = add(new BooleanSetting("视场角检查", true, () -> rotate.isOpen() && yawStep.isOpen() && page.getValue() == Page.Rotation));
    private final SliderSetting fov = add(new SliderSetting("最大视场角", 20, 0, 360, 0.1, () -> rotate.isOpen() && yawStep.isOpen() && checkFov.getValue() && page.getValue() == Page.Rotation));
    private final SliderSetting priority = add(new SliderSetting("旋转优先级", 10, 0, 100, () -> rotate.isOpen() && yawStep.isOpen() && page.getValue() == Page.Rotation));

    // --- 常规/放置设置 ---
    private final SliderSetting minDamage = add(new SliderSetting("最小伤害", 5.0, 0.0, 36.0, () -> page.getValue() == Page.General).setSuffix("伤害"));
    private final SliderSetting maxSelf = add(new SliderSetting("最大自伤", 12.0, 0.0, 36.0, () -> page.getValue() == Page.General).setSuffix("伤害"));
    private final SliderSetting reserve = add(new SliderSetting("生命保留", 2.0, 0.0, 10.0, () -> page.getValue() == Page.General).setSuffix("血量"));
    private final BooleanSetting balance = add(new BooleanSetting("伤害平衡", true, () -> page.getValue() == Page.General).setParent());
    private final SliderSetting balanceOffset = add(new SliderSetting("平衡偏移", 0, -20, 20, .1, () -> page.getValue() == Page.General && balance.isOpen()).setSuffix("血量"));
    private final BooleanSetting place = add(new BooleanSetting("启用放置", true, () -> page.getValue() == Page.General).setParent());
    public final SliderSetting placeRange = add(new SliderSetting("放置范围", 5.0, 0.0, 6, 0.01, () -> page.getValue() == Page.General && place.isOpen()).setSuffix("m"));
    private final SliderSetting placeDelay = add(new SliderSetting("放置延迟", 300, 0, 1000, () -> page.getValue() == Page.General && place.isOpen()).setSuffix("ms"));
    private final EnumSetting<SwapMode> autoSwap = add(new EnumSetting<>("自动切换", SwapMode.None, () -> page.getValue() == Page.General && place.isOpen()));
    private final BooleanSetting afterBreak = add(new BooleanSetting("破后立置", true, () -> page.getValue() == Page.General && place.isOpen()));
    private final BooleanSetting forcePlace = add(new BooleanSetting("强制放置", false, () -> page.getValue() == Page.General && place.isOpen()));
    private final BooleanSetting breakSetting = add(new BooleanSetting("启用破坏", true, () -> page.getValue() == Page.General).setParent());
    public final SliderSetting breakRange = add(new SliderSetting("破坏范围", 4.0, 0.0, 6, 0.01, () -> page.getValue() == Page.General && breakSetting.isOpen()).setSuffix("m"));
    private final SliderSetting breakDelay = add(new SliderSetting("破坏延迟", 300, 0, 1000, () -> page.getValue() == Page.General && breakSetting.isOpen()).setSuffix("ms"));
    private final SliderSetting minAge = add(new SliderSetting("最小存在时间", 0, 0, 20, () -> page.getValue() == Page.General && breakSetting.isOpen()).setSuffix("tick"));
    private final BooleanSetting breakRemove = add(new BooleanSetting("移除实体", false, () -> page.getValue() == Page.General && breakSetting.isOpen()));
    private final BooleanSetting onAdd = add(new BooleanSetting("生成即破", false, () -> page.getValue() == Page.General && breakSetting.isOpen()));
    private final BooleanSetting resetCD = add(new BooleanSetting("重置攻击冷却", true, () -> page.getValue() == Page.General && breakSetting.isOpen()));
    private final EnumSetting<Timing> timing = add(new EnumSetting<>("执行时机", Timing.All, () -> page.getValue() == Page.General));
    private final BooleanSetting interactOnRender = add(new BooleanSetting("渲染时交互", false, () -> page.getValue() == Page.General));
    private final SliderSetting wallRange = add(new SliderSetting("穿墙范围", 6.0, 0.0, 6.0, () -> page.getValue() == Page.General).setSuffix("m"));
    private final EnumSetting<SwingSide> swingMode = add(new EnumSetting<>("挥手方式", SwingSide.All, () -> page.getValue() == Page.General));

    // --- 渲染设置 ---
    private final ColorSetting text = add(new ColorSetting("文本颜色", new Color(-1), () -> page.getValue() == Page.Render).injectBoolean(true));
    private final EnumSetting<TargetESP> mode = add(new EnumSetting<>("目标特效", TargetESP.Fill, () -> page.getValue() == Page.Render));
    private final SliderSetting animationTime = add(new SliderSetting("动画时间", 200, 0, 2000, 1, () -> page.getValue() == Page.Render));
    private final EnumSetting<Easing> ease = add(new EnumSetting<>("缓动效果", Easing.CubicInOut, () -> page.getValue() == Page.Render));
    private final ColorSetting color = add(new ColorSetting("目标颜色", new Color(255, 255, 255, 50), () -> page.getValue() == Page.Render));
    private final ColorSetting outlineColor = add(new ColorSetting("目标轮廓色", new Color(255, 255, 255, 50), () -> page.getValue() == Page.Render));
    private final ColorSetting hitColor = add(new ColorSetting("击中颜色", new Color(255, 255, 255, 150), () -> page.getValue() == Page.Render));
    private final ColorSetting hitOutlineColor = add(new ColorSetting("击中轮廓色", new Color(255, 255, 255, 150), () -> page.getValue() == Page.Render));
    private final BooleanSetting render = add(new BooleanSetting("启用渲染", true, () -> page.getValue() == Page.Render));
    private final BooleanSetting sync = add(new BooleanSetting("同步位置", true, () -> page.getValue() == Page.Render && render.getValue()));
    private final BooleanSetting shrink = add(new BooleanSetting("收缩效果", true, () -> page.getValue() == Page.Render && render.getValue()));
    private final ColorSetting box = add(new ColorSetting("方框颜色", new Color(255, 255, 255, 255), () -> page.getValue() == Page.Render && render.getValue()).injectBoolean(true));
    private final SliderSetting lineWidth = add(new SliderSetting("线宽", 1.5d, 0.01d, 3d, 0.01, () -> page.getValue() == Page.Render && render.getValue()));
    private final ColorSetting fill = add(new ColorSetting("填充颜色", new Color(255, 255, 255, 100), () -> page.getValue() == Page.Render && render.getValue()).injectBoolean(true));
    private final SliderSetting sliderSpeed = add(new SliderSetting("滑动速度", 0.2, 0.01, 1, 0.01, () -> page.getValue() == Page.Render && render.getValue()));
    private final SliderSetting startFadeTime = add(new SliderSetting("开始淡出", 0.3d, 0d, 2d, 0.01, () -> page.getValue() == Page.Render && render.getValue()).setSuffix("s"));
    private final SliderSetting fadeSpeed = add(new SliderSetting("淡出速度", 0.2d, 0.01d, 1d, 0.01, () -> page.getValue() == Page.Render && render.getValue()));

    // --- 计算设置 ---
    private final SliderSetting attackVecStep = add(new SliderSetting("攻击向量步长", 0.1, 0.01, 1, 0.01, () -> page.getValue() == Page.Calc));
    private final BooleanSetting thread = add(new BooleanSetting("线程计算", false, () -> page.getValue() == Page.Calc));
    private final BooleanSetting doCrystal = add(new BooleanSetting("计算中交互", false, () -> page.getValue() == Page.Calc));
    private final SliderSetting selfPredict = add(new SliderSetting("自我预测", 0, 0, 20, () -> page.getValue() == Page.Calc).setSuffix("ticks"));
    private final SliderSetting predictTicks = add(new SliderSetting("目标预测", 4, 0, 20, () -> page.getValue() == Page.Calc).setSuffix("ticks"));
    private final SliderSetting simulation = add(new SliderSetting("模拟步数", 5, 0, 20, 1, () -> page.getValue() == Page.Calc));
    private final SliderSetting maxMotionY = add(new SliderSetting("最大Y轴运动", 0.34, 0, 2, 0.01, () -> page.getValue() == Page.Calc));
    private final BooleanSetting step = add(new BooleanSetting("模拟上台阶", false, () -> page.getValue() == Page.Calc));
    private final BooleanSetting doubleStep = add(new BooleanSetting("模拟双台阶", false, () -> page.getValue() == Page.Calc));
    private final BooleanSetting jump = add(new BooleanSetting("模拟跳跃", false, () -> page.getValue() == Page.Calc));
    private final BooleanSetting inBlockPause = add(new BooleanSetting("卡块暂停", true, () -> page.getValue() == Page.Calc));
    private final BooleanSetting terrainIgnore = add(new BooleanSetting("忽略地形", true, () -> page.getValue() == Page.Calc));

    // --- 基座设置 ---
    private final BooleanSetting basePlace = add(new BooleanSetting("启用基座放置", true, () -> page.getValue() == Page.Base));
    private final SliderSetting baseMin = add(new SliderSetting("基座最小伤害", 6, 0, 36, 0.1, () -> page.getValue() == Page.Base).setSuffix("血量"));
    private final SliderSetting baseMax = add(new SliderSetting("基座最大自伤", 12, 0, 36, 0.1, () -> page.getValue() == Page.Base).setSuffix("血量"));
    private final SliderSetting overrideMax = add(new SliderSetting("覆盖最大伤害", 8, 0, 36, 0.1, () -> page.getValue() == Page.Base).setSuffix("血量"));
    private final BooleanSetting baseBalance = add(new BooleanSetting("基座伤害平衡", true, () -> page.getValue() == Page.Base));
    private final BooleanSetting onlyBelow = add(new BooleanSetting("仅下方目标", true, () -> page.getValue() == Page.Base));
    private final BooleanSetting inventory = add(new BooleanSetting("背包切换", true, () -> page.getValue() == Page.Base));
    private final BooleanSetting detectMining = add(new BooleanSetting("检测挖掘", true, () -> page.getValue() == Page.Base));
    private final SliderSetting delay = add(new SliderSetting("基座延迟", 3000, 0, 10000, () -> page.getValue() == Page.Base).setSuffix("ms"));

    // --- 杂项设置 ---
    private final BooleanSetting ignoreMine = add(new BooleanSetting("忽略挖掘中方块", true, () -> page.getValue() == Page.Misc).setParent());
    private final SliderSetting constantProgress = add(new SliderSetting("挖掘进度阈值", 90.0, 0.0, 100.0, () -> page.getValue() == Page.Misc && ignoreMine.isOpen()).setSuffix("%"));
    private final BooleanSetting antiSurround = add(new BooleanSetting("反包围", false, () -> page.getValue() == Page.Misc).setParent());
    private final SliderSetting miningProgress = add(new SliderSetting("挖掘进度", 90.0, 0.0, 100.0, () -> page.getValue() == Page.Misc && antiSurround.isOpen()).setSuffix("%"));
    private final SliderSetting antiSurroundMax = add(new SliderSetting("低伤害触发", 5.0, 0.0, 36.0, () -> page.getValue() == Page.Misc && antiSurround.isOpen()).setSuffix("伤害"));
    private final BooleanSetting slowPlace = add(new BooleanSetting("低速放置模式", true, () -> page.getValue() == Page.Misc).setParent());
    private final SliderSetting slowDelay = add(new SliderSetting("低速延迟", 600, 0, 2000, () -> page.getValue() == Page.Misc && slowPlace.isOpen()).setSuffix("ms"));
    private final SliderSetting slowMinDamage = add(new SliderSetting("低速最小伤害", 1.5, 0.0, 36.0, () -> page.getValue() == Page.Misc && slowPlace.isOpen()).setSuffix("伤害"));
    private final BooleanSetting lethalOverride = add(new BooleanSetting("致命覆盖", true, () -> page.getValue() == Page.Misc).setParent());
    private final SliderSetting forceMaxHealth = add(new SliderSetting("目标低血量阈值", 7, 0, 36, .1, () -> page.getValue() == Page.Misc && lethalOverride.isOpen()).setSuffix("血量"));
    private final SliderSetting forceMin = add(new SliderSetting("致命最小伤害", 1.5, 0.0, 36.0, () -> page.getValue() == Page.Misc && lethalOverride.isOpen()).setSuffix("伤害"));
    private final BooleanSetting armorBreaker = add(new BooleanSetting("破甲模式", true, () -> page.getValue() == Page.Misc).setParent());
    private final SliderSetting maxDurable = add(new SliderSetting("最大耐久度", 8, 0, 100, () -> page.getValue() == Page.Misc && armorBreaker.isOpen()).setSuffix("%"));
    private final SliderSetting armorBreakerDamage = add(new SliderSetting("破甲最小伤害", 3.0, 0.0, 36.0, () -> page.getValue() == Page.Misc && armorBreaker.isOpen()).setSuffix("伤害"));
    private final BooleanSetting forceWeb = add(new BooleanSetting("强制蜘蛛网", true, () -> page.getValue() == Page.Misc).setParent());
    public final BooleanSetting airPlace = add(new BooleanSetting("空中放置", false, () -> page.getValue() == Page.Misc && forceWeb.isOpen()));
    public final BooleanSetting replace = add(new BooleanSetting("替换放置", false, () -> page.getValue() == Page.Misc && forceWeb.isOpen()));
    private final SliderSetting hurtTime = add(new SliderSetting("目标受伤时间", 10, 0, 10, 1, () -> page.getValue() == Page.Misc));
    private final SliderSetting waitHurt = add(new SliderSetting("等待受伤时间", 10, 0, 10, 1, () -> page.getValue() == Page.Misc));
    private final SliderSetting syncTimeout = add(new SliderSetting("同步超时", 500, 0, 2000, 10, () -> page.getValue() == Page.Misc));

    public AutoCrystal() {
        super("AutoCrystal", Category.Combat);
        setChinese("自动水晶");
        INSTANCE = this;
        Alien.EVENT_BUS.subscribe(new CrystalRender());
    }

    @Override
    public String getInfo() {
        if (displayTarget != null && lastDamage > 0) {
            return df.format(lastDamage);
        }
        return null;
    }

    @Override
    public void onDisable() {
        crystalPos = null;
        tempPos = null;
        displayTarget = null;
    }

    @Override
    public void onEnable() {
        crystalPos = null;
        tempPos = null;
        tempBreakCrystal = null;
        displayTarget = null;
        syncTimer.reset();
        lastBreakTimer.reset();
    }

    public void onThread() {
        if (isOff()) return;
        if (thread.getValue()) {
            updateCrystalPos();
        }
    }

    @EventListener
    public void onTick(ClientTickEvent event) {
        if (nullCheck()) return;
        if (timing.is(Timing.Pre) && event.isPost() || timing.is(Timing.Post) && event.isPre()) return;
        
        if (!thread.getValue()) {
            updateCrystalPos();
        }
        
        if (shouldReturn()) {
            return;
        }
        
        doInteract();
        
        BlockPos basePos = this.basePos;
        if (basePlace.getValue() && basePos != null && BlockUtil.canPlace(basePos)) {
            doPlace(basePos);
        }
    }

    @Override
    public void onRender3D(PoseStack matrixStack) {
        if (interactOnRender.getValue()) {
            if (!shouldReturn()) {
                doInteract();
                BlockPos basePos = this.basePos;
                if (basePlace.getValue() && basePos != null && BlockUtil.canPlace(basePos)) {
                    doPlace(basePos);
                }
            }
        }
        
        if (displayTarget != null && !noPosTimer.passed(500)) {
            doRender(matrixStack, mc.getDeltaTracker().getGameTimeDeltaPartialTick(true), displayTarget, mode.getValue());
        }
    }

    public void doRender(PoseStack stack, float partialTicks, Entity entity, TargetESP mode) {
        switch (mode) {
            case AABB ->
                Render3DUtil.draw3DBox(((IEntity) entity).getDimensions().makeBoundingBox(new Vec3(MathUtil.interpolate(entity.xOld, entity.getX(), partialTicks), MathUtil.interpolate(entity.yOld, entity.getY(), partialTicks), MathUtil.interpolate(entity.zOld, entity.getZ(), partialTicks))).inflate(0, 0.1, 0), ColorUtil.fadeColor(color.getValue(), hitColor.getValue(), animation.get(0, animationTime.getValueInt(), ease.getValue())), ColorUtil.fadeColor(outlineColor.getValue(), hitOutlineColor.getValue(), animation.get(0, animationTime.getValueInt(), ease.getValue())), true, true);
            case Fill ->
                Render3DUtil.draw3DBox(((IEntity) entity).getDimensions().makeBoundingBox(new Vec3(MathUtil.interpolate(entity.xOld, entity.getX(), partialTicks), MathUtil.interpolate(entity.yOld, entity.getY(), partialTicks), MathUtil.interpolate(entity.zOld, entity.getZ(), partialTicks))).inflate(0, 0.1, 0), ColorUtil.fadeColor(color.getValue(), hitColor.getValue(), animation.get(0, animationTime.getValueInt(), ease.getValue())), ColorUtil.fadeColor(outlineColor.getValue(), hitOutlineColor.getValue(), animation.get(0, animationTime.getValueInt(), ease.getValue())), false, true);
            case Jello -> JelloUtil.drawJello(stack, entity, color.getValue());
            case ThunderHack -> Render3DUtil.drawTargetEsp(stack, displayTarget, color.getValue());
        }
    }

    private void doInteract() {
        BlockPos crystalPos = this.crystalPos;
        if (crystalPos != null) {
            doCrystal(crystalPos);
        }
        if (breakCrystal != null) {
            doBreak(breakCrystal);
            breakCrystal = null;
        }
    }

    @EventListener
    public void onRotate(RotationEvent event) {
        if (rotate.getValue() && shouldYawStep() && directionVec != null && displayTarget != null && !noPosTimer.passed(1000) && !shouldReturn()) {
            event.setTarget(directionVec, steps.getValueFloat(), priority.getValueFloat());
        }
    }

    @EventListener(priority = -199)
    public void onPacketSend(PacketEvent.Send event) {
        if (event.isCancelled()) return;
        if (event.getPacket() instanceof ServerboundSetCarriedItemPacket packet) {
            if (lastSlot != packet.getSlot()) {
                lastSlot = packet.getSlot();
                switchTimer.reset();
            }
        }
    }

    public Vec3 getAttackVec(Vec3 feetPos) {
        return MathUtil.getPointToBoxFromBottom(mc.player.getEyePosition(), feetPos, breakRange.getValue(), 2, attackVecStep.getValue());
    }

    private void updateCrystalPos() {
        if (calcDelay.passedMs(updateDelay.getValue())) {
            calcDelay.reset();
            calcCrystalPos();
            CombatUtil.modifyPos = null;
            CombatUtil.modifyBlockState = null;
            basePos = tempBasePos;
            lastDamage = tempDamage;
            breakCrystal = tempBreakCrystal;
            crystalPos = tempPos;
        }
    }

    private boolean shouldReturn() {
        if (eatingPause.getValue() && mc.player.isUsingItem() || Blink.INSTANCE.isOn() && Blink.INSTANCE.pauseModule.getValue()) {
            lastBreakTimer.reset();
            return true;
        } else if (preferAnchor.getValue() && AutoAnchor.INSTANCE.currentPos != null) {
            lastBreakTimer.reset();
            return true;
        } else if (pause.isPressed()) {
            lastBreakTimer.reset();
            return true;
        }
        return false;
    }

    private void calcCrystalPos() {
        if (nullCheck()) return;

        if (breakOnlyHasCrystal.getValue() && !mc.player.getMainHandItem().getItem().equals(Items.END_CRYSTAL) && !mc.player.getOffhandItem().getItem().equals(Items.END_CRYSTAL) && !hasCrystal()) {
            tempPos = null;
            tempBreakCrystal = null;
            lastBreakTimer.reset();
            return;
        }

        boolean shouldReturn = shouldReturn();
        boolean needBasePlace = basePlace.getValue() && baseTimer.passedMs(delay.getValue()) && getBlock() != -1;
        
        tempBreakCrystal = null;
        breakDamage = 0;
        tempPos = null;
        tempDamage = 0f;
        tempBasePos = null;
        float baseDamage = 0;
        
        ArrayList<PlayerEntityPredict> list = new ArrayList<>();
        
        for (Player target : CombatUtil.getEnemies(targetRange.getValueFloat())) {
            if (target.hurtTime <= hurtTime.getValueInt()) {
                list.add(new PlayerEntityPredict(target, maxMotionY.getValue(), predictTicks.getValueInt(), simulation.getValueInt(), step.getValue(), doubleStep.getValue(), jump.getValue(), inBlockPause.getValue()));
            }
        }
        
        PlayerEntityPredict self = new PlayerEntityPredict(mc.player, maxMotionY.getValue(), selfPredict.getValueInt(), simulation.getValueInt(), step.getValue(), doubleStep.getValue(), jump.getValue(), inBlockPause.getValue());
        
        if (list.isEmpty()) {
            lastBreakTimer.reset();
        } else {
            for (Entity entity : Alien.THREAD.getEntities()) {
                if (entity instanceof EndCrystal crystal) {
                    if (entity.tickCount < minAge.getValueInt()) continue;
                    
                    Vec3 attackVec = getAttackVec(crystal.position());
                    if (attackVec == null) continue;
                    
                    if (!mc.player.hasLineOfSight(crystal) && mc.player.getEyePosition().distanceTo(attackVec) > wallRange.getValue())
                        continue;
                    
                    float selfDamage = calculateDamage(crystal.position(), self.player, self.predict);
                    
                    for (PlayerEntityPredict pap : list) {
                        float damage = calculateDamage(crystal.position(), pap.player, pap.predict);
                        
                        if (damage > breakDamage) {
                            if (selfDamage > maxSelf.getValue()) continue;
                            if (reserve.getValue() > 0 && selfDamage > mc.player.getHealth() + mc.player.getAbsorptionAmount() - reserve.getValue())
                                continue;
                            
                            if (damage < EntityUtil.getHealth(pap.player)) {
                                if (damage < getDamage(pap.player)) continue;
                                if (balance.getValue()) {
                                    if (getDamage(pap.player) == forceMin.getValue()) {
                                        if (damage < selfDamage - 2.5) continue;
                                    } else {
                                        if (damage < selfDamage + balanceOffset.getValue()) continue;
                                    }
                                }
                            }
                            
                            breakDamage = damage;
                            tempBreakCrystal = crystal;
                            displayTarget = pap.player;
                        }
                    }
                }
            }
            
            if (doCrystal.getValue() && tempBreakCrystal != null && !shouldReturn) {
                doBreak(tempBreakCrystal);
                tempBreakCrystal = null;
            }
            
            for (BlockPos pos : BlockUtil.getSphere((float) breakRange.getValue() + 1.5f)) {
                boolean base = false;
                CombatUtil.modifyPos = null;
                CombatUtil.modifyBlockState = null;
                
                if (needBasePlace && BlockUtil.canPlace(pos.below())) {
                    CombatUtil.modifyPos = pos.below();
                    CombatUtil.modifyBlockState = Blocks.OBSIDIAN.defaultBlockState();
                    base = true;
                }
                
                if (base && Alien.BREAK.isMining(pos.below()) && detectMining.getValue()) {
                    continue;
                }
                
                Vec3 attackVec = getAttackVec(Vec3.atBottomCenterOf(pos));
                if (attackVec == null) continue;
                if (behindWall(pos, attackVec)) continue;
                if (!canTouch(pos.below())) continue;
                if (!canPlaceCrystal(pos, true, false)) continue;
                
                float selfDamage = base ? calculateBaseDamage(pos, self.player, self.predict) : calculateDamage(pos, self.player, self.predict);
                
                for (PlayerEntityPredict pap : list) {
                    if (base && onlyBelow.getValue()) {
                        if (pos.getY() - 0.5 > pap.player.getY()) continue;
                    }
                    
                    float damage = base ? calculateBaseDamage(pos, pap.player, pap.predict) : calculateDamage(pos, pap.player, pap.predict);
                    
                    if (base) {
                        if (tempDamage <= overrideMax.getValue() && (damage > tempDamage && damage > baseDamage)) {
                            if (selfDamage > baseMax.getValue()) continue;
                            if (reserve.getValue() > 0 && selfDamage > mc.player.getHealth() + mc.player.getAbsorptionAmount() - reserve.getValue())
                                continue;
                            if (damage < EntityUtil.getHealth(pap.player)) {
                                if (damage < baseMin.getValue()) continue;
                                if (baseBalance.getValue()) {
                                    if (damage < selfDamage) continue;
                                }
                            }
                            displayTarget = pap.player;
                            baseDamage = damage;
                            tempBasePos = pos.below();
                            tempPos = null;
                        }
                    } else {
                        if (damage > tempDamage && (damage >= baseDamage || tempDamage > overrideMax.getValue())) {
                            if (selfDamage > maxSelf.getValue()) continue;
                            if (reserve.getValue() > 0 && selfDamage > mc.player.getHealth() + mc.player.getAbsorptionAmount() - reserve.getValue())
                                continue;
                            if (damage < EntityUtil.getHealth(pap.player)) {
                                if (damage < getDamage(pap.player)) continue;
                                if (balance.getValue()) {
                                    if (getDamage(pap.player) == forceMin.getValue()) {
                                        if (damage < selfDamage - 2.5) continue;
                                    } else {
                                        if (damage < selfDamage + balanceOffset.getValue()) continue;
                                    }
                                }
                            }
                            displayTarget = pap.player;
                            tempPos = pos;
                            tempBasePos = null;
                            tempDamage = damage;
                        }
                    }
                }
            }
            
            CombatUtil.modifyPos = null;
            CombatUtil.modifyBlockState = null;
            
            if (antiSurround.getValue() && PacketMine.getBreakPos() != null && PacketMine.progress >= miningProgress.getValueFloat() && !BlockUtil.hasEntity(PacketMine.getBreakPos(), false)) {
                if (tempDamage <= antiSurroundMax.getValueFloat()) {
                    for (PlayerEntityPredict pap : list) {
                        BlockPos pos = new BlockPosX(pap.player.position().add(0, 0.5, 0));
                        if (BlockUtil.canCollide(pap.player, new AABB(pos))) continue;
                        for (Direction i : Direction.values()) {
                            if (i == Direction.DOWN || i == Direction.UP) continue;
                            BlockPos offsetPos = pos.relative(i);
                            if (offsetPos.equals(PacketMine.getBreakPos())) {
                                for (Direction direction : Direction.values()) {
                                    if (direction == Direction.DOWN || direction == Direction.UP) continue;
                                    if (canPlaceCrystal(offsetPos.relative(direction), false, false)) {
                                        float selfDamage = calculateDamage(offsetPos.relative(direction), self.player, self.predict);
                                        if (selfDamage < maxSelf.getValue() && !(reserve.getValue() > 0 && selfDamage > mc.player.getHealth() + mc.player.getAbsorptionAmount() - reserve.getValue())) {
                                            tempPos = offsetPos.relative(direction);
                                            if (doCrystal.getValue() && tempPos != null && !shouldReturn) {
                                                doCrystal(tempPos);
                                            }
                                            return;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        
        if (doCrystal.getValue() && tempPos != null && !shouldReturn) {
            doCrystal(tempPos);
        }
    }

    @EventListener
    private void onEntity(EntitySpawnedEvent event) {
        Entity entity = event.getEntity();
        if (onAdd.getValue() && entity instanceof EndCrystal crystal && crystal.blockPosition().equals(syncPos)) {
            doBreak(crystal);
        }
    }

    public boolean canPlaceCrystal(BlockPos pos, boolean ignoreCrystal, boolean ignoreItem) {
        BlockPos obsPos = pos.below();
        BlockPos boost = obsPos.above();
        BlockPos boost2 = boost.above();
        
        if (BlockUtil.getBlock(obsPos) != Blocks.BEDROCK && BlockUtil.getBlock(obsPos) != Blocks.OBSIDIAN) {
            return false;
        }
        if (BlockUtil.getClickSideStrict(obsPos) == null) {
            return false;
        }
        if (!noEntityBlockCrystal(boost, ignoreCrystal, ignoreItem)) return false;
        if (!noEntityBlockCrystal(boost2, ignoreCrystal, ignoreItem)) return false;
        if (!(mc.level.getBlockState(boost).isAir() || (BlockUtil.hasCrystal(boost) && BlockUtil.getBlock(boost) == Blocks.FIRE))) {
            return false;
        }
        if (ClientSetting.INSTANCE.lowVersion.getValue() && !mc.level.getBlockState(boost2).isAir()) {
            return false;
        }
        
        // NCP 绕过：确保视线能接触到放置面 (已修复 HitResult 编译错误)
        Direction side = BlockUtil.getClickSideStrict(obsPos);
        if (side != null) {
            Vec3 clickVec = Vec3.atCenterOf(obsPos).add(side.getUnitVec3().x * 0.5, side.getUnitVec3().y * 0.5, side.getUnitVec3().z * 0.5);
            HitResult result = mc.level.clip(new ClipContext(mc.player.getEyePosition(), clickVec, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            
            if (result instanceof BlockHitResult blockHitResult) {
                BlockPos hitPos = blockHitResult.getBlockPos();
                if (!hitPos.equals(obsPos) && !hitPos.equals(pos)) {
                    if (mc.player.getEyePosition().distanceTo(clickVec) > wallRange.getValue()) {
                        return false;
                    }
                }
            } else if (result != null && result.getType() != HitResult.Type.MISS) {
                if (mc.player.getEyePosition().distanceTo(clickVec) > wallRange.getValue()) {
                    return false;
                }
            }
        }
        
        return true;
    }

    private boolean noEntityBlockCrystal(BlockPos pos, boolean ignoreCrystal, boolean ignoreItem) {
        for (Entity entity : BlockUtil.getEntities(new AABB(pos))) {
            if (!entity.isAlive()) continue;
            if (entity instanceof ItemEntity && ignoreItem) continue;
            if (entity instanceof EndCrystal) {
                if (ignoreCrystal &&
                    getAttackVec(entity.position()) != null &&
                    (mc.player.hasLineOfSight(entity) || mc.player.getEyePosition().distanceTo(entity.position()) <= wallRange.getValue())
                ) {
                    continue;
                }
            }
            return false;
        }
        return true;
    }

    public boolean behindWall(BlockPos pos, Vec3 attackVec) {
        Vec3 crystalEyePos = new Vec3(pos.getX() + 0.5, pos.getY() + 2 * 0.85, pos.getZ() + 0.5);
        HitResult result = mc.level.clip(new ClipContext(mc.player.getEyePosition(), crystalEyePos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        if (result == null || result.getType() == HitResult.Type.MISS) return false;
        return mc.player.getEyePosition().distanceTo(attackVec) > wallRange.getValue();
    }

    private boolean canTouch(BlockPos pos) {
        Direction side = BlockUtil.getClickSideStrict(pos);
        return side != null && Vec3.atCenterOf(pos).add(new Vec3(side.getUnitVec3().x * 0.5, side.getUnitVec3().y * 0.5, side.getUnitVec3().z * 0.5)).distanceTo(mc.player.getEyePosition()) <= placeRange.getValue();
    }

    private void doCrystal(BlockPos pos) {
        if (canPlaceCrystal(pos, false, false)) {
            doPlace(pos, rotate.getValue() && onPlace.getValue());
        }
        doBreak(pos);
    }

    private void doPlace(BlockPos pos) {
        if (!baseTimer.passed((long) delay.getValue())) return;
        if (detectMining.getValue() && Alien.BREAK.isMining(pos)) return;
        
        int block = getBlock();
        if (block == -1) return;
        
        int old = mc.player.getInventory().getSelectedSlot();
        baseSwap(block);
        BlockUtil.placeBlock(pos, rotate.getValue());
        
        if (inventory.getValue()) {
            baseSwap(block);
            EntityUtil.syncInventory();
        } else {
            baseSwap(old);
        }
        baseTimer.reset();
    }

    public float calculateDamage(BlockPos pos, Player player, Player predict) {
        return calculateDamage(new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5), player, predict);
    }

    public float calculateDamage(Vec3 pos, Player player, Player predict) {
        if (ignoreMine.getValue() && PacketMine.getBreakPos() != null) {
            if (mc.player.getEyePosition().distanceTo(Vec3.atCenterOf(PacketMine.getBreakPos())) <= PacketMine.INSTANCE.range.getValue()) {
                if (PacketMine.progress >= constantProgress.getValue() / 100) {
                    CombatUtil.modifyPos = PacketMine.getBreakPos();
                    CombatUtil.modifyBlockState = Blocks.AIR.defaultBlockState();
                }
            }
        }
        if (terrainIgnore.getValue()) {
            CombatUtil.terrainIgnore = true;
        }
        float damage = ExplosionUtil.calculateDamage(pos, player, predict, 6);
        CombatUtil.modifyPos = null;
        CombatUtil.terrainIgnore = false;
        return damage;
    }

    public float calculateBaseDamage(BlockPos pos, Player player, Player predict) {
        if (terrainIgnore.getValue()) {
            CombatUtil.terrainIgnore = true;
        }
        float damage = DamageUtils.overridingExplosionDamage(player, predict, new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5), 12f, pos.below(), Blocks.OBSIDIAN.defaultBlockState());
        CombatUtil.terrainIgnore = false;
        return damage;
    }

    private double getDamage(Player target) {
        if (!PacketMine.INSTANCE.obsidian.isPressed() && slowPlace.getValue() && lastBreakTimer.passed((long) slowDelay.getValue())) {
            return slowMinDamage.getValue();
        }
        if (lethalOverride.getValue() && EntityUtil.getHealth(target) <= forceMaxHealth.getValue() && !PacketMine.INSTANCE.obsidian.isPressed()) {
            return forceMin.getValue();
        }
        if (armorBreaker.getValue()) {
            // 26.2: Inventory#armor 已移除，改为按装备槽读取
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack armor = target.getItemBySlot(slot);
                if (armor.isEmpty()) continue;
                if (EntityUtil.getDamagePercent(armor) > maxDurable.getValue()) continue;
                return armorBreakerDamage.getValue();
            }
        }
        return minDamage.getValue();
    }

    private boolean shouldYawStep() {
        if (!whenElytra.getValue() && (mc.player.isFallFlying() || ElytraFly.INSTANCE.isOn() && ElytraFly.INSTANCE.isFallFlying()))
            return false;
        return yawStep.getValue() && !Velocity.INSTANCE.noRotation();
    }

    public boolean hasCrystal() {
        if (autoSwap.getValue() == SwapMode.None) return false;
        return getCrystal() != -1;
    }

    private void doBreak(EndCrystal entity) {
        noPosTimer.reset();
        if (!breakSetting.getValue()) return;
        if (!entity.isAlive()) return;
        
        if (displayTarget != null && displayTarget.hurtTime > waitHurt.getValueInt() && !syncTimer.passedMs(syncTimeout.getValue())) {
            return;
        }
        
        lastBreakTimer.reset();
        if (!switchTimer.passed((long) switchCooldown.getValue())) {
            return;
        }
        syncTimer.reset();
        
        if (entity.tickCount < minAge.getValueInt()) return;
        
        if (!shouldYawStep() && !CombatUtil.breakTimer.passed((long) breakDelay.getValue())) {
            if (forcePlace.getValue() && crystalPos != null) {
                doPlace(crystalPos, false);
            }
            return;
        }
        
        if (rotate.getValue() && onBreak.getValue()) {
            Vec3 attackVec = getAttackVec(entity.position());
            if (!faceVector(attackVec == null ? entity.position() : attackVec)) {
                if (forcePlace.getValue() && crystalPos != null) {
                    doPlace(crystalPos, false);
                }
                return;
            }
        }
        
        if (shouldYawStep() && !CombatUtil.breakTimer.passed((long) breakDelay.getValue())) {
            if (forcePlace.getValue() && crystalPos != null) {
                doPlace(crystalPos, false);
            }
            return;
        }
        
        animation.to = 1;
        animation.from = 1;
        CombatUtil.breakTimer.reset();
        syncPos = entity.blockPosition();
        
        mc.getConnection().send(new ServerboundAttackPacket(entity.getId()));
        
        if (resetCD.getValue()) {
            mc.player.resetAttackStrengthTicker();
        }
        
        EntityUtil.swingHand(InteractionHand.MAIN_HAND, swingMode.getValue());
        
        if (breakRemove.getValue()) {
            mc.level.removeEntity(entity.getId(), Entity.RemovalReason.KILLED);
        }
        
        BlockPos crystalPos = this.crystalPos;
        if (crystalPos != null && displayTarget != null && lastDamage >= getDamage(displayTarget) && afterBreak.getValue()) {
            if (!rotate.getValue() || !shouldYawStep() || !checkFov.getValue() || Alien.ROTATION.inFov(entity.position(), fov.getValueFloat())) {
                doPlace(crystalPos, false);
            }
        }
        
        if (forceWeb.getValue() && AutoWeb.INSTANCE.isOn()) {
            AutoWeb.force = true;
        }
        
        if (rotate.getValue() && !shouldYawStep()) {
            Alien.ROTATION.snapBack();
        }
    }

    private void doBreak(BlockPos pos) {
        noPosTimer.reset();
        if (!breakSetting.getValue()) return;
        
        if (displayTarget != null && displayTarget.hurtTime > waitHurt.getValueInt() && !syncTimer.passedMs(syncTimeout.getValue())) {
            return;
        }
        
        lastBreakTimer.reset();
        if (!switchTimer.passed((long) switchCooldown.getValue())) {
            return;
        }
        syncTimer.reset();
        
        for (EndCrystal entity : BlockUtil.getEndCrystals(new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 2, pos.getZ() + 1))) {
            if (entity.tickCount < minAge.getValueInt()) continue;
            if (!entity.isAlive()) continue;
            
            if (!shouldYawStep() && !CombatUtil.breakTimer.passed((long) breakDelay.getValue())) {
                if (forcePlace.getValue() && crystalPos != null) {
                    doPlace(crystalPos, false);
                }
                return;
            }
            
            if (rotate.getValue() && onBreak.getValue()) {
                Vec3 attackVec = getAttackVec(entity.position());
                if (!faceVector(attackVec == null ? entity.position() : attackVec)) {
                    if (forcePlace.getValue() && crystalPos != null) {
                        doPlace(crystalPos, false);
                    }
                    return;
                }
            }
            
            if (shouldYawStep() && !CombatUtil.breakTimer.passed((long) breakDelay.getValue())) {
                if (forcePlace.getValue() && crystalPos != null) {
                    doPlace(crystalPos, false);
                }
                return;
            }
            
            animation.to = 1;
            animation.from = 1;
            CombatUtil.breakTimer.reset();
            syncPos = pos;
            
            mc.getConnection().send(new ServerboundAttackPacket(entity.getId()));
            
            if (resetCD.getValue()) {
                mc.player.resetAttackStrengthTicker();
            }
            
            EntityUtil.swingHand(InteractionHand.MAIN_HAND, swingMode.getValue());
            
            if (breakRemove.getValue()) {
                mc.level.removeEntity(entity.getId(), Entity.RemovalReason.KILLED);
            }
            
            BlockPos crystalPos = this.crystalPos;
            if (crystalPos != null && displayTarget != null && lastDamage >= getDamage(displayTarget) && afterBreak.getValue()) {
                if (!rotate.getValue() || !shouldYawStep() || !checkFov.getValue() || Alien.ROTATION.inFov(entity.position(), fov.getValueFloat())) {
                    doPlace(crystalPos, false);
                }
            }
            
            if (forceWeb.getValue() && AutoWeb.INSTANCE.isOn()) {
                AutoWeb.force = true;
            }
            
            if (rotate.getValue() && !shouldYawStep()) {
                Alien.ROTATION.snapBack();
            }
            return;
        }
        
        if (forcePlace.getValue() && crystalPos != null) {
            doPlace(crystalPos, false);
        }
    }

    private void doPlace(BlockPos pos, boolean rotate) {
        noPosTimer.reset();
        if (!place.getValue()) return;
        
        if (!mc.player.getMainHandItem().getItem().equals(Items.END_CRYSTAL) && !mc.player.getOffhandItem().getItem().equals(Items.END_CRYSTAL) && !hasCrystal()) {
            return;
        }
        
        if (!canTouch(pos.below())) {
            return;
        }
        
        BlockPos obsPos = pos.below();
        Direction facing = BlockUtil.getClickSide(obsPos);
        Vec3 vec = Vec3.atCenterOf(obsPos).add(facing.getUnitVec3().x * 0.5, facing.getUnitVec3().y * 0.5, facing.getUnitVec3().z * 0.5);
        if (facing != Direction.UP && facing != Direction.DOWN) {
            vec = vec.add(0, 0.45, 0);
        }
        
        if (!shouldYawStep() && !placeTimer.passed((long) placeDelay.getValue())) return;
        
        if (rotate) {
            if (!faceVector(vec)) return;
        }
        
        if (!placeTimer.passed((long) placeDelay.getValue())) return;
        
        if (mc.player.getMainHandItem().getItem().equals(Items.END_CRYSTAL) || mc.player.getOffhandItem().getItem().equals(Items.END_CRYSTAL)) {
            placeTimer.reset();
            syncPos = pos;
            placeCrystal(pos);
        } else {
            placeTimer.reset();
            syncPos = pos;
            int old = mc.player.getInventory().getSelectedSlot();
            int crystal = getCrystal();
            if (crystal == -1) return;
            doSwap(crystal);
            placeCrystal(pos);
            if (autoSwap.getValue() == SwapMode.Silent) {
                doSwap(old);
            } else if (autoSwap.getValue() == SwapMode.Inventory) {
                doSwap(crystal);
                EntityUtil.syncInventory();
            }
        }
        
        if (rotate && !shouldYawStep()) {
            Alien.ROTATION.snapBack();
        }
    }

    /**
     * 动态延迟计算 (已修复 Alien.TPS 编译错误)
     * 仅使用 Ping 补偿来绕过 NCP 的 FastPlace/FightBack 检测，更加稳定安全
     */
    private long getDynamicDelay(double baseDelay) {
        int ping = mc.player.connection.getPlayerInfo(mc.player.getUUID()) != null ?
                   mc.player.connection.getPlayerInfo(mc.player.getUUID()).getLatency() : 0;
        
        // 基础延迟 + Ping补偿 (每 50ms ping 增加 10ms 延迟)
        double adjustedDelay = baseDelay + (ping / 5.0);
        return (long) Math.max(baseDelay * 0.8, adjustedDelay);
    }

    private void doSwap(int slot) {
        if (autoSwap.getValue() == SwapMode.Silent || autoSwap.getValue() == SwapMode.Normal) {
            InventoryUtil.switchToSlot(slot);
        } else if (autoSwap.getValue() == SwapMode.Inventory) {
            InventoryUtil.inventorySwap(slot, mc.player.getInventory().getSelectedSlot());
        }
    }

    private void baseSwap(int slot) {
        if (!inventory.getValue()) {
            InventoryUtil.switchToSlot(slot);
        } else {
            InventoryUtil.inventorySwap(slot, mc.player.getInventory().getSelectedSlot());
        }
    }

    private int getBlock() {
        if (inventory.getValue()) {
            return InventoryUtil.findBlockInventorySlot(Blocks.OBSIDIAN);
        } else {
            return InventoryUtil.findBlock(Blocks.OBSIDIAN);
        }
    }

    private int getCrystal() {
        if (autoSwap.getValue() == SwapMode.Silent || autoSwap.getValue() == SwapMode.Normal) {
            return InventoryUtil.findItem(Items.END_CRYSTAL);
        } else if (autoSwap.getValue() == SwapMode.Inventory) {
            return InventoryUtil.findItemInventorySlot(Items.END_CRYSTAL);
        }
        return -1;
    }

    private void placeCrystal(BlockPos pos) {
        boolean offhand = mc.player.getOffhandItem().getItem() == Items.END_CRYSTAL;
        BlockPos obsPos = pos.below();
        Direction facing = BlockUtil.getClickSide(obsPos);
        BlockUtil.clickBlock(obsPos, facing, false, offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND, swingMode.getValue());
    }

    private boolean faceVector(Vec3 directionVec) {
        if (directionVec == null) return false;
        if (!shouldYawStep()) {
            Alien.ROTATION.lookAt(directionVec);
            return true;
        } else {
            this.directionVec = directionVec;
            if (Alien.ROTATION.inFov(directionVec, fov.getValueFloat())) {
                return true;
            }
        }
        return !checkFov.getValue();
    }

    public enum TargetESP {
        AABB, Fill, Jello, ThunderHack, None
    }

    private enum Page {
        General, Base, Misc, Rotation, Check, Calc, Render
    }

    private enum SwapMode {
        None, Normal, Silent, Inventory
    }

    private class CrystalRender {
        @EventListener
        public void onRender3D(Render3DEvent event) {
            BlockPos cpos = sync.getValue() && crystalPos != null ? syncPos : crystalPos;
            if (cpos != null) {
                placeVec3d = Vec3.atCenterOf(cpos.below());
            }
            if (placeVec3d == null) {
                return;
            }
            
            if (fadeSpeed.getValue() >= 1) {
                currentFade = noPosTimer.passed((long) (startFadeTime.getValue() * 1000)) ? 0 : 0.5;
            } else {
                currentFade = AnimateUtil.animate(currentFade, noPosTimer.passed((long) (startFadeTime.getValue() * 1000)) ? 0 : 0.5, fadeSpeed.getValue() / 10);
            }
            
            if (currentFade == 0) {
                curVec3d = null;
                return;
            }
            
            if (curVec3d == null || sliderSpeed.getValue() >= 1) {
                curVec3d = placeVec3d;
            } else {
                curVec3d = new Vec3(AnimateUtil.animate(curVec3d.x, placeVec3d.x, sliderSpeed.getValue() / 10), AnimateUtil.animate(curVec3d.y, placeVec3d.y, sliderSpeed.getValue() / 10), AnimateUtil.animate(curVec3d.z, placeVec3d.z, sliderSpeed.getValue() / 10));
            }
            
            if (render.getValue()) {
                AABB cbox = new AABB(curVec3d, curVec3d);
                if (shrink.getValue()) {
                    cbox = cbox.inflate(currentFade);
                } else {
                    cbox = cbox.inflate(0.5);
                }
                PoseStack matrixStack = event.matrixStack;
                if (fill.booleanValue) {
                    Render3DUtil.drawFill(cbox, ColorUtil.injectAlpha(fill.getValue(), (int) (fill.getValue().getAlpha() * currentFade * 2D)));
                }
                if (box.booleanValue) {
                    Render3DUtil.drawBox(cbox, ColorUtil.injectAlpha(box.getValue(), (int) (box.getValue().getAlpha() * currentFade * 2D)), lineWidth.getValueFloat());
                }
            }
            
            if (text.booleanValue && lastDamage > 0) {
                if (!noPosTimer.passed((long) (startFadeTime.getValue() * 1000)))
                    Render3DUtil.drawText3D(df.format(lastDamage), curVec3d, text.getValue());
            }
        }
    }
}