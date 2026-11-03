package dev.luminous.mod.modules.impl.movement;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.eventbus.EventPriority;
import dev.luminous.api.events.impl.*;
import dev.luminous.api.utils.player.MovementUtil;
import dev.luminous.api.utils.render.ColorUtil;
import dev.luminous.asm.accessors.IClientInput;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.client.AntiCheat;
import dev.luminous.mod.modules.impl.player.Freecam;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.ColorSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.Gizmo;
import net.minecraft.gizmos.GizmoPrimitives;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.awt.*;

public class HoleSnap extends Module {
    public static HoleSnap INSTANCE;
    public final BooleanSetting any = add(new BooleanSetting("AnyHole", true));
    public final SliderSetting timer = add(new SliderSetting("Timer", 1, 0.1, 8, 0.1));
    public final BooleanSetting up = add(new BooleanSetting("Up", true));
    public final BooleanSetting grim = add(new BooleanSetting("Grim", false));
    public final ColorSetting color = add(new ColorSetting("Color", new Color(255, 255, 255, 100)));
    public final SliderSetting circleSize = add(new SliderSetting("CircleSize", 1f, 0.1f, 2.5f));
    public final BooleanSetting fade = add(new BooleanSetting("Fade", true));
    public final SliderSetting segments = add(new SliderSetting("Segments", 180, 0, 360));
    private final SliderSetting range = this.add(new SliderSetting("Range", 5, 1, 50));
    private final SliderSetting timeoutTicks = this.add(new SliderSetting("TimeOut", 40, 0, 100));
    private final SliderSetting steps = add(new SliderSetting("Steps", 0.8, 0, 1, 0.01, grim::getValue));
    private final SliderSetting priority = add(new SliderSetting("Priority", 10, 0, 100, grim::getValue));
    boolean resetMove = false;
    boolean applyTimer = false;
    Vec3 targetPos;
    private BlockPos holePos;
    private int stuckTicks;
    private int enabledTicks;

    public HoleSnap() {
        super("HoleSnap", "HoleSnap", Category.Movement);
        setChinese("拉坑");
        INSTANCE = this;
    }

    // 26.2: Vec2f → Vec2
    public static Vec2 getRotationTo(Vec3 posFrom, Vec3 posTo) {
        Vec3 vec3d = posTo.subtract(posFrom);
        return getRotationFromVec(vec3d);
    }

    /**
     * 26.2: 旧的即时模式绘制（Tessellator + BufferRenderer.drawWithGlobalProgram + RenderSystem.setShader）
     * 已整体移除，改用 gizmos 的 {@link Gizmos}。
     * gizmo 使用世界坐标，不再需要相机偏移，也不需要 PoseStack。
     * <p>
     * 26.2 的 {@link net.minecraft.gizmos.CircleGizmo} 把段数写死成 20（{@code CIRCLE_VERTICES = 20}），
     * {@link Gizmos#circle} 也没有 segments 重载，直接用会让模块的 Segments 设置彻底失效。
     * 因此这里按 {@code CircleGizmo#emit} 的实现（等角度取点 → 闭合三角扇 [+ 可选描边]）
     * 自己拼一个可指定段数的 gizmo，视觉与原版 CircleGizmo 完全一致，但段数由参数决定。
     */
    public static void drawCircle(PoseStack matrixStack, Color color, double circleSize, Vec3 pos, int segments) {
        try (Gizmos.TemporaryCollection ignored = mc.levelRenderer.collectPerFrameRenderThreadGizmos()) {
            Gizmos.addGizmo(new SegmentedCircleGizmo(pos, (float) circleSize, GizmoStyle.fill(color.getRGB()), segments)).setAlwaysOnTop();
        }
    }

    /**
     * 26.2: 可指定段数的圆形 gizmo（等价于 {@link net.minecraft.gizmos.CircleGizmo}，段数不再写死）。
     */
    private record SegmentedCircleGizmo(Vec3 pos, float radius, GizmoStyle style, int segments) implements Gizmo {
        @Override
        public void emit(GizmoPrimitives primitives, float alphaMultiplier) {
            if (!this.style.hasStroke() && !this.style.hasFill()) {
                return;
            }
            int count = Math.max(3, this.segments);
            Vec3[] points = new Vec3[count + 1];
            for (int i = 0; i < count; i++) {
                float theta = i * (Mth.TWO_PI / count);
                points[i] = this.pos.add(this.radius * Mth.cos(theta), 0.0, this.radius * Mth.sin(theta));
            }
            // 与 CircleGizmo 一致：末尾补回首点，三角扇才能闭合
            points[count] = points[0];
            if (this.style.hasFill()) {
                primitives.addTriangleFan(points, this.style.multipliedFill(alphaMultiplier));
            }
            if (this.style.hasStroke()) {
                int strokeColor = this.style.multipliedStroke(alphaMultiplier);
                for (int i = 0; i < count; i++) {
                    primitives.addLine(points[i], points[i + 1], strokeColor, this.style.strokeWidth());
                }
            }
        }
    }

    private static Vec2 getRotationFromVec(Vec3 vec) {
        double d = vec.x;
        double d2 = vec.z;
        double xz = Math.hypot(d, d2);
        d2 = vec.z;
        double d3 = vec.x;
        double yaw = normalizeAngle(Math.toDegrees(Math.atan2(d2, d3)) - 90.0);
        double pitch = normalizeAngle(Math.toDegrees(-Math.atan2(vec.y, xz)));
        return new Vec2((float) yaw, (float) pitch);
    }

    private static double normalizeAngle(double angleIn) {
        double angle = angleIn;
        if ((angle %= 360.0) >= 180.0) {
            angle -= 360.0;
        }
        if (angle < -180.0) {
            angle += 360.0;
        }
        return angle;
    }

    @EventListener(priority = EventPriority.LOW + 1)
    public void onTimer(TimerEvent event) {
        if (applyTimer) event.set(timer.getValueFloat());
    }

    @Override
    public void onEnable() {
        applyTimer = false;
        if (nullCheck()) {
            disable();
            return;
        }
        resetMove = false;
        holePos = Alien.HOLE.getHole((float) range.getValue(), true, any.getValue(), up.getValue());
    }

    @Override
    public void onDisable() {
        this.holePos = null;
        this.stuckTicks = 0;
        this.enabledTicks = 0;
        if (nullCheck()) {
            return;
        }
        if (resetMove && !grim.getValue()) {
            MovementUtil.setMotionX(0);
            MovementUtil.setMotionZ(0);
        }
    }

    @EventListener
    public void onReceivePacket(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundPlayerPositionPacket) {
            this.disable();
        }
    }

    @EventListener(priority = -999)
    public void onKeyInput(KeyboardInputEvent e) {
        if (!grim.getValue()) {
            return;
        } else if (!AntiCheat.INSTANCE.movementSync()) {
            sendMessage("§4HoleSnap require MovementSync.");
            disable();
            return;
        }
        // 26.2: isRiding → isPassenger
        if (mc.player.isPassenger() || Freecam.INSTANCE.isOn())
            return;

        // 26.2: movementSideways / movementForward 散装字段已移除，改写 Input record + moveVector
        mc.player.input.keyPresses = new Input(
                true,
                false,
                false,
                false,
                mc.player.input.keyPresses.jump(),
                mc.player.input.keyPresses.shift(),
                mc.player.input.keyPresses.sprint());
        ((IClientInput) mc.player.input).setMoveVector(new Vec2(0.0f, 1.0f));
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        holePos = Alien.HOLE.getHole((float) range.getValue(), true, any.getValue(), up.getValue());
        if (holePos == null) {
            disable();
            return;
        }
        ++enabledTicks;
        if (enabledTicks > timeoutTicks.getValue() - 1) {
            disable();
            return;
        }
        applyTimer = true;
        if (!grim.getValue()) return;
        if (!mc.player.isAlive() || mc.player.isFallFlying()) {
            disable();
            return;
        }
        if (stuckTicks > 8) {
            disable();
            return;
        }
        if (holePos == null) {
            //CommandManager.sendChatMessageWidthId("§e[!] §fHoles?", hashCode());
            disable();
            return;
        }
        Vec3 playerPos = mc.player.position();
        targetPos = new Vec3(holePos.getX() + 0.5, mc.player.getY(), holePos.getZ() + 0.5);
        if (Alien.HOLE.isDoubleHole(holePos)) {
            Direction facing = Alien.HOLE.is3Block(holePos);
            if (facing != null) {
                targetPos = targetPos.add(new Vec3(facing.getUnitVec3().x * 0.5, facing.getUnitVec3().y * 0.5, facing.getUnitVec3().z * 0.5));
            }
        }

        applyTimer = true;
        resetMove = true;
        float rotation = getRotationTo(playerPos, targetPos).x;
        float yawRad = rotation / 180.0f * 3.1415927f;
        double dist = playerPos.distanceTo(targetPos);
        double cappedSpeed = Math.min(0.2873, dist);
        double x = -(float) Math.sin(yawRad) * cappedSpeed;
        double z = (float) Math.cos(yawRad) * cappedSpeed;
        if (Math.abs(x) < 0.25 && Math.abs(z) < 0.25 && playerPos.y <= holePos.getY() + 0.8) {
            disable();
            return;
        }
        if (mc.player.horizontalCollision) {
            stuckTicks++;
        } else {
            stuckTicks = 0;
        }
    }

    @EventListener()
    public void onRotate(RotationEvent event) {
        if (grim.getValue() && holePos != null) {
            targetPos = new Vec3(holePos.getX() + 0.5, mc.player.getY(), holePos.getZ() + 0.5);
            if (Alien.HOLE.isDoubleHole(holePos)) {
                Direction facing = Alien.HOLE.is3Block(holePos);
                if (facing != null) {
                    targetPos = targetPos.add(new Vec3(facing.getUnitVec3().x * 0.5, facing.getUnitVec3().y * 0.5, facing.getUnitVec3().z * 0.5));
                }
            }

            event.setTarget(targetPos, steps.getValueFloat(), priority.getValueFloat());
        }
    }

    @EventListener
    public void onMove(MoveEvent event) {
        if (grim.getValue()) return;
        if (!mc.player.isAlive() || mc.player.isFallFlying()) {
            disable();
            return;
        }
        if (stuckTicks > 8) {
            disable();
            return;
        }
        if (holePos == null) {
            //CommandManager.sendChatMessageWidthId("§e[!] §fHoles?", hashCode());
            disable();
            return;
        }
        Vec3 playerPos = mc.player.position();
        targetPos = new Vec3(holePos.getX() + 0.5, mc.player.getY(), holePos.getZ() + 0.5);
        if (Alien.HOLE.isDoubleHole(holePos)) {
            Direction facing = Alien.HOLE.is3Block(holePos);
            if (facing != null) {
                targetPos = targetPos.add(new Vec3(facing.getUnitVec3().x * 0.5, facing.getUnitVec3().y * 0.5, facing.getUnitVec3().z * 0.5));
            }
        }

        applyTimer = true;
        resetMove = true;
        float rotation = getRotationTo(playerPos, targetPos).x;
        float yawRad = rotation / 180.0f * 3.1415927f;
        double dist = playerPos.distanceTo(targetPos);
        double cappedSpeed = Math.min(0.2873, dist);
        double x = -(float) Math.sin(yawRad) * cappedSpeed;
        double z = (float) Math.cos(yawRad) * cappedSpeed;
        event.setX(x);
        event.setZ(z);
        if (Math.abs(x) < 0.1 && Math.abs(z) < 0.1 && playerPos.y <= holePos.getY() + 0.5) {
            disable();
        }
        if (mc.player.horizontalCollision) {
            stuckTicks++;
        } else {
            stuckTicks = 0;
        }
    }

    @Override
    public void onRender3D(PoseStack matrixStack) {
        if (targetPos == null || holePos == null) {
            return;
        }
        // 26.2: GL11 混合状态不再需要，gizmos 自行处理
        Color color = this.color.getValue();

        Vec3 pos = new Vec3(targetPos.x, holePos.getY(), targetPos.z);
        if (fade.getValue()) {
            double temp = 0.01;
            for (double i = 0; i < circleSize.getValue(); i += temp) {
                drawCircle(matrixStack, ColorUtil.injectAlpha(color, (int) Math.min(color.getAlpha() * 2 / (circleSize.getValue() / temp), 255)), i, pos, segments.getValueInt());
            }
        } else {
            drawCircle(matrixStack, color, circleSize.getValue(), pos, segments.getValueInt());
        }
    }
}
