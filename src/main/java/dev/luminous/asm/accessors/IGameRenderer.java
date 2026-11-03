package dev.luminous.asm.accessors;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/*
 * 26.2: GameRenderer#renderHand(Camera, float, Matrix4f) 已不存在，
 * 手部渲染改名为 private renderItemInHand(CameraRenderState, float, Matrix4fc)。
 * 调用方可以从 mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState
 * 取到当前帧的 CameraRenderState。
 * 注意：GameRenderer 的 Minecraft 字段在 26.2 叫 minecraft（旧名 client），本接口不访问该字段，无需改动。
 */
@Mixin(GameRenderer.class)
public interface IGameRenderer {

    @Invoker("renderItemInHand")
    void IRenderHand(CameraRenderState cameraState, float deltaPartialTick, Matrix4fc modelViewMatrix);
}
