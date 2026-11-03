package dev.luminous.asm.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.resource.GraphicsResourceAllocator;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.luminous.Alien;
import dev.luminous.mod.modules.impl.player.Freecam;
import dev.luminous.mod.modules.impl.render.Chams;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.phase.FeatureRenderPhase;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * 26.2: {@code LevelRenderer} 的渲染流程整体重写（extract → submit → frame graph），
 * 旧的 {@code renderEntity(...)} / {@code setupTerrain(...)} / {@code PostEffectProcessor#render}
 * 均不存在。这里按 26.2 的真实结构重新找注入点。
 */
@Mixin(LevelRenderer.class)
public abstract class MixinWorldRenderer {

    /**
     * 26.2: {@code setupTerrain(Camera,Frustum,ZZ)} 消失，视锥裁剪开关改由
     * {@code CameraRenderState#smartCull} 控制（{@code SectionOcclusionGraph} 直接读它）。
     * 这里在 render 入口把 smartCull 关掉，等价于旧的「把 spectator 参数改成 true」，
     * Freecam 下依然能看到全部区块。
     */
    @Inject(method = "render", at = @At("HEAD"))
    private void renderSetupTerrainModifyArg(GraphicsResourceAllocator resourceAllocator, DeltaTracker deltaTracker,
                                             boolean renderOutline, CameraRenderState cameraState,
                                             Matrix4fc modelViewMatrix, GpuBufferSlice terrainFog,
                                             Vector4f fogColor, boolean shouldRenderSky, CallbackInfo ci) {
        if (Freecam.INSTANCE.isOn()) {
            cameraState.smartCull = false;
        }
    }

    /**
     * 原 {@code @Redirect LevelRenderer#render → PostEffectProcessor#render(F)V}（替换原版实体描边后处理）。
     *
     * <p>26.2 的等价位置是 {@link LevelRenderer#doEntityOutline()}：它把
     * {@code entityOutlineTarget}（原版 entity_outline 链 sobel + blur 之后的结果）
     * 用 {@code blitAndBlendToTexture} 混合回主 target。Alien 的 Shader 模块
     * 在 {@code GameRenderer#renderLevel} 的同一帧、更早的时机（手部渲染之后）
     * 已经把这份 outline 缓冲当作 {@code In} 采样，跑完自己的 {@code alien:outline} 等 chain，
     * 并把结果混合回主 target（见 {@code ShaderManager#runEffectChain}）。
     * 因此这里取消原版的第二次混合，避免出现双重描边。
     *
     * <p>链不可用（资源缺失 / 未加载）时保留原版行为。
     */
    @Inject(method = "doEntityOutline", at = @At("HEAD"), cancellable = true)
    private void alien$replaceEntityOutlinePass(CallbackInfo ci) {
        if (Alien.SHADER != null && Alien.SHADER.isEffectReady()) {
            ci.cancel();
        }
    }

    /*
     * ============================ Chams 穿墙（26.2 实现） ============================
     *
     * 1.21.1 的做法（见 .tools/src-backup 里的旧 MixinWorldRenderer）：
     * <pre>
     * @Inject(method = "renderEntity", at = HEAD/RETURN)
     * glEnable(GL_POLYGON_OFFSET_FILL); glPolygonOffset(1f, -1000000F);  ...  glDisable(...)
     * </pre>
     * 即「包裹单个实体自己的即时绘制、把深度往相机方向压 1e6」，靠 GL 状态实现穿墙。
     *
     * 26.2 没有任何「按实体包一层 GL/RenderType 状态」的入口：
     *  - 实体在 {@code LevelExtractor} 里 extract 成 {@code EntityRenderState}，
     *    由 {@code LevelRenderer#submitEntities} → {@code EntityRenderDispatcher#submit}
     *    提交成 SubmitNode，最后由 {@code FeatureRenderDispatcher} 延后下发；
     *  - 深度属于 {@code RenderPipeline}，{@code RenderTypes.entityCutoutZOffset} 之类的
     *    Z-offset 管线只能在**实体 renderer 内部**选择（{@code LivingEntityRenderer#getRenderType}），
     *    调用方无法替换（那需要给每个 renderer 开 mixin，超出本批 scope）。
     *
     * 26.2 里真正等价、且不需要碰任何 renderer 的杠杆是 **提交阶段（phase）**：
     * 每个 {@code SubmitNodeCollection} 有一份 {@code alwaysOnTop} 阶段
     * （{@code SubmitNodeCollection.java:64}），由 {@code LevelRenderer#addAlwaysOnTopPass}
     * 在**地形之后**执行，并且该 pass 第一步就是 {@code clearDepthTexture}
     * （{@code LevelRenderer.java:501}）—— 这正是旧 {@code glPolygonOffset(1f, -1e6)} 的效果。
     * 所以「穿墙 Chams」= 把同一份实体几何**再提交一次到 alwaysOnTop 阶段**：
     * 实体类型过滤放在 {@code Chams#chams(EntityType)}（模块 scope，见 Chams.java），
     * 这里只做「按过滤结果把这一份 submit 分流到不同 phase」。
     *
     * 关闭 throughWall 时 {@code Chams#chams(EntityType)} 恒 false ⇒ 只走下面的正常提交，
     * 实体留在普通深度阶段 ⇒ 被方块正常遮挡。（旧 TODO 里设想的
     * 「Chams 模块用 EntityType 过滤 + RenderType Z-offset」即此方案的等价物。）
     */

    /** 额外一份「穿墙副本」的落点：与正式 collector 分离，再按阶段搬过去。 */
    @Unique
    private SubmitNodeStorage alien$chamsSubmits;

    /** 缓存的「哪些 phase 不搬去 alwaysOnTop」（阴影/名牌/文字/outline 等已在正常阶段画过）。 */
    @Unique
    private boolean[] alien$keepPhases;

    @WrapOperation(
            method = "submitEntities",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;submit(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lnet/minecraft/client/renderer/state/level/CameraRenderState;DDDLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V"
            )
    )
    private void alien$submitChamsThroughWall(EntityRenderDispatcher dispatcher, EntityRenderState state,
                                              CameraRenderState camera, double x, double y, double z,
                                              PoseStack poseStack, SubmitNodeCollector collector,
                                              Operation<Void> original) {
        Chams chams = Chams.INSTANCE;
        // 过滤（模块开关 + throughWall + EntityType）与 phase 可用性都不满足时，行为与原版完全一致。
        if (chams == null || !chams.chams(state.entityType)
                || !(collector.order(0) instanceof SubmitNodeCollection target)) {
            original.call(dispatcher, state, camera, x, y, z, poseStack, collector);
            return;
        }

        // ① 正常深度阶段：原版那一份，保持不变（不穿墙时看到的就是它）。
        original.call(dispatcher, state, camera, x, y, z, poseStack, collector);

        // ② 穿墙副本：同一份 render state / 同一个 poseStack，再提交到独立的 storage。
        SubmitNodeStorage subms = this.alien$chamsSubmits;
        if (subms == null) {
            // 延迟创建：不依赖 Mixin 对「实例字段初始化器」的处理。
            subms = this.alien$chamsSubmits = new SubmitNodeStorage();
        }
        subms.order(0);
        original.call(dispatcher, state, camera, x, y, z, poseStack, subms);
        this.alien$moveToAlwaysOnTop(target);
    }

    /**
     * 把 {@link #alien$chamsSubmits} 里的提交全部搬进正式 collector 的 {@code alwaysOnTop} 阶段。
     * <p>
     * 只搬几何相关的 phase；阴影 / 名牌 / 文字 / 方块破坏轮廓 / outline 直接丢弃
     * （这些在 ① 的正常提交里已经画过，重复搬过去会出现「阴影浮在地形上」「名牌双描」）。
     */
    @Unique
    private void alien$moveToAlwaysOnTop(SubmitNodeCollection target) {
        boolean[] keep = this.alien$phaseMask(target);
        for (SubmitNodeCollection source : this.alien$chamsSubmits.getSubmitsPerOrder().values()) {
            List<FeatureRenderPhase<?>> phases = source.allPhases();
            for (int index = 0; index < phases.size() && index < keep.length; index++) {
                if (keep[index]) {
                    continue;
                }
                // sortInto 会把该 phase 的内容按 feature 分组后交给 Output，并清空原 phase。
                phases.get(index).sortInto((node, strictlyOrdered) -> target.alwaysOnTop.submit(node));
            }
        }
        this.alien$chamsSubmits.getSubmitsPerOrder().clear();
    }

    /**
     * 26.2 的 {@code SubmitNodeCollection#allPhases()} 顺序是类内固定的，两个实例天然一致，
     * 所以按「引用相等」在第一个实例上算一次掩码即可（不硬编码下标，避免以后顺序变动）。
     */
    @Unique
    private boolean[] alien$phaseMask(SubmitNodeCollection reference) {
        if (this.alien$keepPhases != null) {
            return this.alien$keepPhases;
        }
        List<FeatureRenderPhase<?>> phases = reference.allPhases();
        boolean[] keep = new boolean[phases.size()];
        for (int index = 0; index < phases.size(); index++) {
            FeatureRenderPhase<?> phase = phases.get(index);
            keep[index] = phase == reference.shadows
                    || phase == reference.nameTags
                    || phase == reference.seeThroughNameTags
                    || phase == reference.texts
                    || phase == reference.shapeOutlines
                    || phase == reference.outline;
        }
        this.alien$keepPhases = keep;
        return keep;
    }
}
