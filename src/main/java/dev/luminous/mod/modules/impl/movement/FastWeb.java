package dev.luminous.mod.modules.impl.movement;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.eventbus.EventPriority;
import dev.luminous.api.events.impl.TimerEvent;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.player.MovementUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.world.level.block.state.BlockState;
// 26.2: CobwebBlock 改名为 WebBlock
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

public class FastWeb extends Module {
    public static FastWeb INSTANCE;
    public final EnumSetting<Mode> mode =
            add(new EnumSetting<>("Mode", Mode.Vanilla));
    public final BooleanSetting onlySneak = add(new BooleanSetting("OnlySneak", true));
    public final BooleanSetting grim = add(new BooleanSetting("Grim", false).setParent());
    public final BooleanSetting abortPacket = add(new BooleanSetting("AbortPacket", true, grim::isOpen));
    public final SliderSetting xZSlow =
            add(new SliderSetting("XZSpeed", 25, 0.0, 100, 0.1, () -> mode.getValue() == Mode.Custom).setSuffix("%"));
    public final SliderSetting ySlow =
            add(new SliderSetting("YSpeed", 100, 0.0, 100, 0.1, () -> mode.getValue() == Mode.Custom).setSuffix("%"));
    private final SliderSetting fastSpeed =
            add(new SliderSetting("Speed", 3.0, 0.0, 8.0, () -> mode.getValue() == Mode.Vanilla || mode.getValue() == Mode.Strict));
    private boolean work = false;

    public FastWeb() {
        super("FastWeb", "So you don't need to keep timer on keybind", Category.Movement);
        setChinese("蜘蛛网加速");
        INSTANCE = this;
    }

    @Override
    public String getInfo() {
        return mode.getValue().name();
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        // 26.2: sneakKey.isPressed → keyShift.isDown
        work = (!mc.player.onGround()) && (mc.options.keyShift.isDown() || !onlySneak.getValue()) && Alien.PLAYER.isInWeb(mc.player);
        if (work && mode.is(Mode.Vanilla)) {
            MovementUtil.setMotionY(-fastSpeed.getValue());
        }
        if (grim.getValue() && (mc.options.keyShift.isDown() || !onlySneak.getValue())) {
            for (BlockPos pos : getIntersectingWebs()) {
                if (abortPacket.getValue()) mc.getConnection().send(new ServerboundPlayerActionPacket(
                        ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, pos, Direction.DOWN));
                mc.getConnection().send(new ServerboundPlayerActionPacket(
                        ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos, Direction.DOWN));
            }
        }
    }

    @EventListener(priority = EventPriority.LOW)
    public void onTimer(TimerEvent event) {
        if (work && mode.getValue() == Mode.Strict) {
            event.set(fastSpeed.getValueFloat());
        }
    }

    public List<BlockPos> getIntersectingWebs() {
        int radius = 2;
        final List<BlockPos> blocks = new ArrayList<>();
        for (int x = radius; x > -radius; --x) {
            for (int y = radius; y > -radius; --y) {
                for (int z = radius; z > -radius; --z) {
                    BlockPos blockPos = BlockPos.containing(mc.player.getX() + x,
                            mc.player.getY() + y, mc.player.getZ() + z);
                    if (mc.player.position().distanceTo(Vec3.atCenterOf(blockPos)) > 1 && mc.player.getEyePosition().distanceTo(Vec3.atCenterOf(blockPos)) > 1) {
                        continue;
                    }
                    BlockState state = mc.level.getBlockState(blockPos);
                    if (state.getBlock() instanceof WebBlock) {
                        blocks.add(blockPos);
                    }
                }
            }
        }
        return blocks;
    }

    public enum Mode {
        Vanilla,
        Strict,
        Custom,
        Ignore
    }
}
