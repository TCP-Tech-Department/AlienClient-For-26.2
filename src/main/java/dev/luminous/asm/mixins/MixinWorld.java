package dev.luminous.asm.mixins;

import dev.luminous.Alien;
import dev.luminous.api.utils.combat.CombatUtil;
import dev.luminous.mod.modules.impl.client.ClientSetting;
import dev.luminous.mod.modules.impl.combat.SelfTrap;
import dev.luminous.mod.modules.impl.player.InteractTweaks;
import dev.luminous.mod.modules.impl.render.NoRender;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static dev.luminous.api.utils.Wrapper.mc;

@Mixin(Level.class) // 26.2: was World
public abstract class MixinWorld {

    @Inject(method = "getBlockState", at = @At("HEAD"), cancellable = true)
    public void blockStateHook(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
        // 26.2: isInBuildLimit -> isInsideBuildHeight
        if (mc.level != null && mc.level.isInsideBuildHeight(pos)) {
            if (SelfTrap.airList.contains(pos)) {
                cir.setReturnValue(Blocks.AIR.defaultBlockState()); // 26.2: was getDefaultState()
                return;
            }
            if (!ClientSetting.INSTANCE.mioCompatible.getValue()) {
                boolean terrainIgnore = CombatUtil.terrainIgnore;
                BlockPos modifyPos = CombatUtil.modifyPos;
                BlockState modifyBlockState = CombatUtil.modifyBlockState;
                if (terrainIgnore || modifyPos != null) {
                    // 26.2: WorldChunk -> LevelChunk
                    LevelChunk worldChunk = mc.level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);

                    BlockState tempState = worldChunk.getBlockState(pos);

                    if (modifyPos != null && modifyBlockState != null) {
                        if (pos.equals(modifyPos)) {
                            cir.setReturnValue(modifyBlockState);
                            return;
                        }
                    }

                    if (terrainIgnore) {
                        if (Alien.HOLE.isHard(tempState.getBlock())) return;
                        cir.setReturnValue(Blocks.AIR.defaultBlockState());
                    }
                } else if (InteractTweaks.INSTANCE.isActive) {
                    LevelChunk worldChunk = mc.level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);

                    BlockState tempState = worldChunk.getBlockState(pos);
                    if (tempState.getBlock() == Blocks.BEDROCK
                    ) {
                        cir.setReturnValue(Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
    }

    /*
     * 26.2: 从 MixinClientWorld 搬过来 —— 1.21.1 里 ClientWorld 覆写了 getRainGradient/getThunderGradient，
     * 26.2 改名为 getRainLevel/getThunderLevel 且只在 Level 中声明（ClientLevel 不再覆写），
     * 所以挂到 Level 上（渲染都在客户端，效果一致）。
     */
    // 26.2: was getRainGradient
    @Inject(method = "getRainLevel", at = @At("HEAD"), cancellable = true)
    private void onGetRainLevel(float delta, CallbackInfoReturnable<Float> cir) {
        if (NoRender.INSTANCE.isOn() && NoRender.INSTANCE.weather.getValue()) {
            cir.setReturnValue(0.0F);
        }
    }

    // 26.2: was getThunderGradient
    @Inject(method = "getThunderLevel", at = @At("HEAD"), cancellable = true)
    private void onGetThunderLevel(float delta, CallbackInfoReturnable<Float> cir) {
        if (NoRender.INSTANCE.isOn() && NoRender.INSTANCE.weather.getValue()) {
            cir.setReturnValue(0.0F);
        }
    }
}
