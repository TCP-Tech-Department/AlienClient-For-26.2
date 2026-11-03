package dev.luminous.mod.modules.impl.render;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.AmbientOcclusionEvent;
import dev.luminous.api.events.impl.ChunkOcclusionEvent;
import dev.luminous.api.events.impl.RenderBlockEntityEvent;
import dev.luminous.mod.gui.windows.WindowsScreen;
import dev.luminous.mod.gui.windows.impl.ItemSelectWindow;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.level.BlockGetter;

public class Xray extends Module {
    public static Xray INSTANCE;

    public Xray() {
        super("Xray", Category.Render);
        setChinese("矿物透视");
        INSTANCE = this;
    }

    private void openGui() {
        edit.setValueWithoutTask(false);
        if (!nullCheck()) {
            // 26.2: MinecraftClient.setScreen → Minecraft.setScreenAndShow
            mc.setScreenAndShow(new WindowsScreen(new ItemSelectWindow(Alien.XRAY)));
        }
    }

    public final BooleanSetting edit = add(new BooleanSetting("Edit", false).injectTask(this::openGui));

    @Override
    public void onEnable() {
        if (nullCheck()) return;
        mc.levelExtractor.allChanged();
    }

    @Override
    public void onDisable() {
        mc.levelExtractor.allChanged();
    }

    public boolean isBlocked(Block block) {
        return !Alien.XRAY.inWhitelist(block.getDescriptionId());
    }

    @EventListener
    private void onRenderBlockEntity(RenderBlockEntityEvent event) {
        if (isBlocked(event.blockEntity.getBlockState().getBlock())) event.cancel();
    }

    @EventListener
    private void onChunkOcclusion(ChunkOcclusionEvent event) {
        event.cancel();
    }

    @EventListener
    private void onAmbientOcclusion(AmbientOcclusionEvent event) {
        event.lightLevel = 1;
    }

    public static boolean shouldBlock(BlockState state) {
        return INSTANCE.isOn() && INSTANCE.isBlocked(state.getBlock());
    }

    public boolean modifyDrawSide(BlockState state, BlockGetter view, BlockPos pos, Direction facing, boolean returns) {
        if (!returns && !isBlocked(state.getBlock())) {
            BlockPos adjPos = pos.relative(facing);
            BlockState adjState = view.getBlockState(adjPos);
            // 26.2: getCullingFace(view,pos,dir) → getFaceOcclusionShape(dir)；VoxelShapes.fullCube() → Shapes.block()
            return adjState.getFaceOcclusionShape(facing.getOpposite()) != Shapes.block() || adjState.getBlock() != state.getBlock() || isExposed(adjPos);
        }

        return returns;
    }

    public static boolean isExposed(BlockPos blockPos) {
        for (Direction direction : Direction.values()) {
            // 26.2: BlockState.isOpaque() → isSolidRender()；MutableBlockPos.set(pos,dir) → setWithOffset(pos,dir)
            if (!mc.level.getBlockState(EXPOSED_POS.get().setWithOffset(blockPos, direction)).isSolidRender()) return true;
        }

        return false;
    }

    private static final ThreadLocal<BlockPos.MutableBlockPos> EXPOSED_POS = ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);
}
