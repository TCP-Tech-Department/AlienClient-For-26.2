package dev.luminous.core.impl;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.luminous.api.utils.Wrapper;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.asm.accessors.ILevelRenderer;
import dev.luminous.asm.accessors.IPostEffectProcessor;
import dev.luminous.asm.accessors.IPostProcessShader;
import dev.luminous.mod.modules.impl.render.ShaderModule;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.Identifier;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;

import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 后处理着色器调度器（26.2 真实实现）。
 *
 * <p>与原 satin 版的差别：
 * <ul>
 *   <li>资源：{@code assets/alien/post_effect/<name>.json}（26.2 {@code PostChainConfig}，
 *       targets 是 map、passes 用 vertex_shader/fragment_shader/inputs/output/uniforms）；
 *       着色器在 {@code assets/alien/shaders/post/<name>.fsh}（{@code #version 330}）。</li>
 *   <li>加载：{@code mc.getShaderManager().getPostChain(id, allowedTargets)}，
 *       chain 由原版 {@code ShaderManager} 持有并随资源重载自动 close。</li>
 *   <li>执行：用 {@link PostChain#addToFrame(FrameGraphBuilder, int, int, PostChain.TargetBundle)}
 *       把 pass 挂进 {@link FrameGraphBuilder}，再 {@code frame.execute(allocator)}。
 *       这里没有用 {@code PostChain#process(RenderTarget, GraphicsResourceAllocator)}，
 *       因为 {@code process()} 的 {@code TargetBundle} 只提供 {@code minecraft:main}，
 *       而描边链要采样原版实体描边缓冲 {@code minecraft:entity_outline} 和
 *       Alien 自己重绘的手部剪影 {@code alien:hands}，输出到 {@code alien:glow}。
 *       模糊链（blurarea）只用 main，走的就是 {@code process()}。</li>
 *   <li>合成：特效链输出到 {@code alien:glow}，再用
 *       {@link RenderTarget#blitAndBlendToTexture} 以 SRC_ALPHA / ONE_MINUS_SRC_ALPHA
 *       混合回主 target（等价于旧的 {@code RenderSystem.blendFuncSeparate} + 画 bufOut）。</li>
 *   <li>uniform：{@code PostPass#customUniforms} 里的 buffer 只有 USAGE_UNIFORM，
 *       先用同样大小的 {@code USAGE_UNIFORM | USAGE_COPY_DST} 替换，再每帧
 *       {@code CommandEncoder#writeToBuffer} 写 std140 数据。</li>
 * </ul>
 */
public class ShaderManager implements Wrapper {

    /** 手部剪影目标（ShaderModule 把当前帧的手重绘到这里，含深度）。 */
    private static final Identifier HANDS_TARGET_ID = Identifier.fromNamespaceAndPath("alien", "hands");
    /** 特效输出目标（Alpha 为发光强度，最后混合回主 target）。 */
    private static final Identifier GLOW_TARGET_ID = Identifier.fromNamespaceAndPath("alien", "glow");
    /** 原版实体发光缓冲（sobel + blur 之后的柔化轮廓）。 */
    private static final Identifier ENTITY_OUTLINE_TARGET_ID = Identifier.withDefaultNamespace("entity_outline");

    private static final Set<Identifier> EFFECT_TARGETS = Set.of(
            PostChain.MAIN_TARGET_ID, ENTITY_OUTLINE_TARGET_ID, HANDS_TARGET_ID, GLOW_TARGET_ID);
    private static final Set<Identifier> BLUR_TARGETS = Set.of(PostChain.MAIN_TARGET_ID);

    /** blurarea 的 BlurAreaConfig 固定 8 个矩形。 */
    public static final int MAX_BLUR_REGIONS = 8;

    private static final List<RenderTask> tasks = new ArrayList<>();

    private final Timer timer = new Timer();
    private final Map<Shader, ChainRuntime> chains = new EnumMap<>(Shader.class);
    private final Set<Shader> failedModes = EnumSet.noneOf(Shader.class);
    private final CrossFrameResourcePool resourcePool = new CrossFrameResourcePool(3);

    private RenderTarget handsTarget;
    private RenderTarget glowTarget;

    private ChainRuntime blurRuntime;
    private boolean blurFailed;

    /** 后处理动画时间（秒）。保留原公开字段。 */
    public float time = 0;

    /** 着色器预设，供 {@code ShaderModule} 的枚举设置使用。 */
    public enum Shader {
        Solid,
        Pulse,
        Smoke,
        Gradient,
        Snow,
        Flow,
        Rainbow
    }

    /** 一个已加载 chain 及其可写 uniform buffer。 */
    private record ChainRuntime(PostChain chain, List<UniformSlot> slots) {
    }

    /** 某个 pass 的某个 uniform block。 */
    private record UniformSlot(int passIndex, String block, GpuBuffer buffer) {
    }

    public record RenderTask(Runnable task, Shader shader) {
    }

    // ------------------------------------------------------------------
    // 对外 API（签名保持与 1.21 版一致）
    // ------------------------------------------------------------------

    /**
     * 排队一次「需要后处理」的绘制。
     * <p>{@code runnable} 会在 alien:hands 离屏目标里执行（ShaderModule 用它重绘手部）；
     * 传 {@code null} 表示这一帧只用原版实体描边缓冲做特效。
     */
    public void renderShader(Runnable runnable, Shader mode) {
        // 防止 renderShaders 的注入点失效时任务无限堆积（正常情况下每帧消费一次）
        if (tasks.size() > 4) {
            tasks.clear();
        }
        tasks.add(new RenderTask(runnable, mode));
    }

    /** 执行本帧排队的任务：手部剪影 → 跑 PostChain → 混合回主 target。 */
    public void renderShaders() {
        ShaderModule module = ShaderModule.INSTANCE;
        if (module != null) {
            this.time = timer.getMs() / 5f * module.speed.getValueFloat() * 0.004f;
        }
        if (!tasks.isEmpty()) {
            List<RenderTask> pending = new ArrayList<>(tasks);
            tasks.clear();
            for (RenderTask task : pending) {
                applyShader(task.task(), task.shader());
            }
        }
        // 每帧推进一次资源池（与 GameRenderer#resourcePool 同一套生命周期语义）
        resourcePool.endFrame();
    }

    /** 应用一次后处理：按 {@code mode} 选 chain，把 {@code runnable} 画进手部剪影后跑链。 */
    public void applyShader(Runnable runnable, Shader mode) {
        if (mode == null) {
            return;
        }
        ChainRuntime runtime = chain(mode);
        if (runtime == null) {
            return;
        }
        if (!ensureTargets()) {
            return;
        }
        clearHandsTarget();
        if (runnable != null) {
            RenderSystem.outputColorTextureOverride = handsTarget.getColorTextureView();
            RenderSystem.outputDepthTextureOverride = handsTarget.getDepthTextureView();
            try {
                runnable.run();
            } finally {
                RenderSystem.outputColorTextureOverride = null;
                RenderSystem.outputDepthTextureOverride = null;
            }
        }
        writeEffectUniforms(runtime, mode);
        runEffectChain(runtime);
    }

    /**
     * 重新加载后处理资源。
     *
     * <p>chain 的所有权在原版 {@code ShaderManager}（资源重载时它会 close 旧 chain），
     * 所以这里只丢引用、不 close；下一帧 {@link #chain} 会重新 {@code getPostChain}。
     */
    public void reloadShaders() {
        chains.clear();
        failedModes.clear();
        blurRuntime = null;
        blurFailed = false;
    }

    /** 资源/上下文是否还没准备好。 */
    public boolean fullNullCheck() {
        return mc == null || mc.level == null || mc.player == null || mc.gameRenderer == null;
    }

    public Timer getTimer() {
        return timer;
    }

    /**
     * 当前 {@code ShaderModule} 的模式是否已经有可用的 post chain。
     * {@code MixinWorldRenderer} 用它决定是否接管原版实体描边合成。
     */
    public boolean isEffectReady() {
        ShaderModule module = ShaderModule.INSTANCE;
        if (module == null || !module.isOn() || fullNullCheck()) {
            return false;
        }
        return chains.containsKey(module.mode.getValue());
    }

    /**
     * 区域模糊：{@code areas} 是屏幕归一化矩形 (x0, y0, x1, y1)（GL 坐标，原点左下）。
     *
     * @return 是否成功跑了 alien:blurarea（false 时调用方回退到原版 minecraft:blur）
     */
    public boolean applyRegionBlur(List<float[]> areas, float radius) {
        if (areas == null || areas.isEmpty() || fullNullCheck()) {
            return false;
        }
        ChainRuntime runtime = blurChain();
        if (runtime == null) {
            return false;
        }
        writeBlurUniforms(runtime, areas, radius);
        RenderTarget mainTarget = mc.gameRenderer.mainRenderTarget();
        if (mainTarget == null) {
            return false;
        }
        runtime.chain().process(mainTarget, resourcePool);
        return true;
    }

    // ------------------------------------------------------------------
    // chain 加载 / 生命周期
    // ------------------------------------------------------------------

    private ChainRuntime chain(Shader mode) {
        ChainRuntime runtime = chains.get(mode);
        if (runtime != null) {
            if (isAlive(runtime)) {
                return runtime;
            }
            // 资源重载后原版会 close 旧 chain，自定义 uniform buffer 也随之 close
            chains.remove(mode);
        }
        if (failedModes.contains(mode)) {
            return null;
        }
        Identifier id = Identifier.fromNamespaceAndPath("alien", idFor(mode));
        PostChain chain = mc.getShaderManager() == null ? null : mc.getShaderManager().getPostChain(id, EFFECT_TARGETS);
        if (chain == null) {
            failedModes.add(mode);
            return null;
        }
        ChainRuntime loaded = new ChainRuntime(chain, installUniformBuffers(chain));
        chains.put(mode, loaded);
        return loaded;
    }

    private ChainRuntime blurChain() {
        if (blurRuntime != null) {
            if (isAlive(blurRuntime)) {
                return blurRuntime;
            }
            blurRuntime = null;
        }
        if (blurFailed) {
            return null;
        }
        PostChain chain = mc.getShaderManager() == null
                ? null
                : mc.getShaderManager().getPostChain(Identifier.fromNamespaceAndPath("alien", "blurarea"), BLUR_TARGETS);
        if (chain == null) {
            blurFailed = true;
            return null;
        }
        blurRuntime = new ChainRuntime(chain, installUniformBuffers(chain));
        return blurRuntime;
    }

    private static String idFor(Shader mode) {
        return switch (mode) {
            case Solid -> "outline";
            case Pulse -> "pulse";
            case Smoke -> "smoke";
            case Gradient -> "gradient";
            case Snow -> "snow";
            case Flow -> "flow";
            case Rainbow -> "rainbow";
        };
    }

    /** 原版 chain 被资源重载关闭时，我们替换的 buffer 也会被 close —— 用它判断缓存是否过期。 */
    private static boolean isAlive(ChainRuntime runtime) {
        for (UniformSlot slot : runtime.slots()) {
            if (slot.buffer().isClosed()) {
                return false;
            }
        }
        return true;
    }

    /**
     * 把 {@code PostPass#customUniforms} 里只读的 uniform buffer 换成可写 buffer。
     * 尺寸沿用原 buffer 的 std140 大小，所以 JSON 与 GLSL 的字段顺序必须一致。
     */
    private List<UniformSlot> installUniformBuffers(PostChain chain) {
        List<UniformSlot> slots = new ArrayList<>();
        List<PostPass> passes = ((IPostEffectProcessor) chain).getPasses();
        for (int passIndex = 0; passIndex < passes.size(); passIndex++) {
            Map<String, GpuBuffer> uniforms = ((IPostProcessShader) passes.get(passIndex)).alien$getCustomUniforms();
            for (Map.Entry<String, GpuBuffer> entry : uniforms.entrySet()) {
                GpuBuffer old = entry.getValue();
                GpuBuffer usable = old;
                if ((old.usage() & GpuBuffer.USAGE_COPY_DST) == 0) {
                    GpuBuffer replacement = RenderSystem.getDevice().createBuffer(
                            () -> "Alien post uniform / " + entry.getKey(),
                            GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                            old.size());
                    entry.setValue(replacement);
                    old.close();
                    usable = replacement;
                }
                slots.add(new UniformSlot(passIndex, entry.getKey(), usable));
            }
        }
        return slots;
    }

    // ------------------------------------------------------------------
    // 离屏目标（手部剪影 + 特效输出）
    // ------------------------------------------------------------------

    private boolean ensureTargets() {
        RenderTarget mainTarget = mc.gameRenderer.mainRenderTarget();
        if (mainTarget == null) {
            return false;
        }
        int width = mainTarget.width;
        int height = mainTarget.height;
        if (width <= 0 || height <= 0) {
            return false;
        }
        if (handsTarget == null) {
            handsTarget = new TextureTarget("Alien Shader Hands", width, height, true, GpuFormat.RGBA8_UNORM);
            handsTarget.resize(width, height);
        } else if (handsTarget.width != width || handsTarget.height != height) {
            handsTarget.resize(width, height);
        }
        if (glowTarget == null) {
            glowTarget = new TextureTarget("Alien Shader Glow", width, height, false, GpuFormat.RGBA8_UNORM);
            glowTarget.resize(width, height);
        } else if (glowTarget.width != width || glowTarget.height != height) {
            glowTarget.resize(width, height);
        }
        return true;
    }

    private void clearHandsTarget() {
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(
                handsTarget.getColorTexture(),
                new Vector4f(0.0F, 0.0F, 0.0F, 0.0F),
                handsTarget.getDepthTexture(),
                0.0);
    }

    // ------------------------------------------------------------------
    // 执行
    // ------------------------------------------------------------------

    private void runEffectChain(ChainRuntime runtime) {
        RenderTarget mainTarget = mc.gameRenderer.mainRenderTarget();
        RenderTarget entityOutline = ((ILevelRenderer) mc.levelRenderer).alien$getEntityOutlineTarget();
        if (mainTarget == null || entityOutline == null || glowTarget == null || handsTarget == null) {
            return;
        }

        FrameGraphBuilder frame = new FrameGraphBuilder();
        Map<Identifier, ResourceHandle<RenderTarget>> handles = new HashMap<>();
        handles.put(PostChain.MAIN_TARGET_ID, frame.importExternal("main", mainTarget));
        handles.put(ENTITY_OUTLINE_TARGET_ID, frame.importExternal("entity_outline", entityOutline));
        handles.put(HANDS_TARGET_ID, frame.importExternal("alien_hands", handsTarget));
        handles.put(GLOW_TARGET_ID, frame.importExternal("alien_glow", glowTarget));

        PostChain.TargetBundle bundle = new PostChain.TargetBundle() {
            @Override
            public void replace(Identifier id, ResourceHandle<RenderTarget> handle) {
                if (handles.containsKey(id)) {
                    handles.put(id, handle);
                }
            }

            @Override
            public ResourceHandle<RenderTarget> get(Identifier id) {
                return handles.get(id);
            }
        };

        runtime.chain().addToFrame(frame, mainTarget.width, mainTarget.height, bundle);
        frame.execute(resourcePool);

        glowTarget.blitAndBlendToTexture(mainTarget.getColorTextureView(), mainTarget.getDepthTextureView());
    }

    // ------------------------------------------------------------------
    // uniform 写入（std140，顺序必须与 JSON / GLSL 一致）
    // ------------------------------------------------------------------

    private void writeEffectUniforms(ChainRuntime runtime, Shader mode) {
        ShaderModule module = ShaderModule.INSTANCE;
        if (module == null) {
            return;
        }
        for (UniformSlot slot : runtime.slots()) {
            if ("AlienConfig".equals(slot.block())) {
                writeUniformBlock(slot, builder -> writeAlienConfig(builder, mode, module));
            }
        }
    }

    private void writeAlienConfig(Std140Builder builder, Shader mode, ShaderModule module) {
        Color color = module.fill.getValue();
        float red = color.getRed() / 255.0F;
        float green = color.getGreen() / 255.0F;
        float blue = color.getBlue() / 255.0F;
        float alpha = color.getAlpha() / 255.0F;
        float radius = module.radius.getValueFloat();
        float divider = module.divider.getValueFloat();
        float maxSample = module.maxSample.getValueFloat();
        float quality = module.smoothness.getValueFloat();
        float minAlpha = module.alpha.getValueFloat() / 255.0F;
        float timeValue = this.time;

        switch (mode) {
            case Solid -> builder
                    .putVec3(red, green, blue)
                    .putFloat(alpha)
                    .putFloat(radius)
                    .putFloat(divider)
                    .putFloat(maxSample)
                    .putFloat(quality)
                    .putFloat(minAlpha);
            case Pulse -> {
                Color color2 = module.pulse.getValue();
                builder
                        .putVec3(red, green, blue)
                        .putVec3(color2.getRed() / 255.0F, color2.getGreen() / 255.0F, color2.getBlue() / 255.0F)
                        .putFloat(alpha)
                        .putFloat(radius)
                        .putFloat(divider)
                        .putFloat(maxSample)
                        .putFloat(quality)
                        .putFloat(minAlpha)
                        .putFloat(timeValue)
                        .putFloat(module.pulseSpeed.getValueFloat());
            }
            case Smoke -> {
                Color first = module.smoke1.getValue();
                Color second = module.smoke2.getValue();
                Color third = module.smoke3.getValue();
                builder
                        .putVec4(first.getRed() / 255.0F, first.getGreen() / 255.0F, first.getBlue() / 255.0F, first.getAlpha() / 255.0F)
                        .putVec3(second.getRed() / 255.0F, second.getGreen() / 255.0F, second.getBlue() / 255.0F)
                        .putVec3(third.getRed() / 255.0F, third.getGreen() / 255.0F, third.getBlue() / 255.0F)
                        .putFloat(alpha)
                        .putFloat(radius)
                        .putFloat(divider)
                        .putFloat(maxSample)
                        .putFloat(quality)
                        .putFloat(timeValue)
                        .putInt(module.octaves.getValueInt());
            }
            case Gradient -> {
                Color rgb0 = module.smoke1.getValue();
                Color rgb1 = module.smoke2.getValue();
                Color rgb2 = module.smoke3.getValue();
                Color rgb3 = module.smoke4.getValue();
                builder
                        .putVec3(rgb0.getRed() / 255.0F, rgb0.getGreen() / 255.0F, rgb0.getBlue() / 255.0F)
                        .putVec3(rgb1.getRed() / 255.0F, rgb1.getGreen() / 255.0F, rgb1.getBlue() / 255.0F)
                        .putVec3(rgb2.getRed() / 255.0F, rgb2.getGreen() / 255.0F, rgb2.getBlue() / 255.0F)
                        .putVec3(rgb3.getRed() / 255.0F, rgb3.getGreen() / 255.0F, rgb3.getBlue() / 255.0F)
                        .putFloat(alpha)
                        .putFloat(module.step.getValueFloat() * 300.0F)
                        .putFloat(radius)
                        .putFloat(divider)
                        .putFloat(maxSample)
                        .putFloat(quality)
                        .putFloat(timeValue * 300.0F);
            }
            case Snow -> builder
                    .putVec4(red, green, blue, alpha)
                    .putFloat(radius)
                    .putFloat(divider)
                    .putFloat(maxSample)
                    .putFloat(quality)
                    .putFloat(timeValue);
            case Flow -> builder
                    .putFloat(alpha)
                    .putFloat(radius)
                    .putFloat(divider)
                    .putFloat(maxSample)
                    .putFloat(quality)
                    .putFloat(timeValue);
            case Rainbow -> builder
                    .putFloat(alpha)
                    .putFloat(timeValue)
                    .putFloat(0.0F)
                    .putFloat(0.0F);
        }
    }

    private void writeBlurUniforms(ChainRuntime runtime, List<float[]> areas, float radius) {
        int count = Math.min(areas.size(), MAX_BLUR_REGIONS);
        float actualRadius = Math.max(radius, 1.0F);
        for (UniformSlot slot : runtime.slots()) {
            if ("BlurConfig".equals(slot.block())) {
                float dirX = slot.passIndex() == 0 ? 1.0F : 0.0F;
                float dirY = slot.passIndex() == 0 ? 0.0F : 1.0F;
                writeUniformBlock(slot, builder -> builder.putVec2(dirX, dirY).putFloat(actualRadius));
            } else if ("BlurAreaConfig".equals(slot.block())) {
                writeUniformBlock(slot, builder -> {
                    for (int i = 0; i < MAX_BLUR_REGIONS; i++) {
                        if (i < count) {
                            float[] area = areas.get(i);
                            builder.putVec4(area[0], area[1], area[2], area[3]);
                        } else {
                            builder.putVec4(0.0F, 0.0F, 0.0F, 0.0F);
                        }
                    }
                    builder.putFloat(count);
                });
            }
        }
    }

    private void writeUniformBlock(UniformSlot slot, Consumer<Std140Builder> writer) {
        MemoryStack stack = MemoryStack.stackPush();
        try {
            Std140Builder builder = Std140Builder.onStack(stack, (int) slot.buffer().size());
            writer.accept(builder);
            RenderSystem.getDevice()
                    .createCommandEncoder()
                    .writeToBuffer(slot.buffer().slice(), builder.get());
        } finally {
            stack.close();
        }
    }
}
