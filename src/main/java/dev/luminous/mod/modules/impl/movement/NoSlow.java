package dev.luminous.mod.modules.impl.movement;

import com.mojang.blaze3d.platform.InputConstants;
import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.eventbus.EventPriority;
import dev.luminous.api.events.impl.InteractItemEvent;
import dev.luminous.api.events.impl.KeyboardInputEvent;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.player.MovementUtil;
import dev.luminous.asm.accessors.IClientInput;
import dev.luminous.asm.accessors.IMinecraftClient;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.phys.Vec2;

import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

public class NoSlow extends Module {
    public static NoSlow INSTANCE;

    // 26.2：ClickSlotC2SPacket 已改名为 ServerboundContainerClickPacket
    final Queue<ServerboundContainerClickPacket> storedClicks = new LinkedList<>();
    final AtomicBoolean pause = new AtomicBoolean();

    private final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", Mode.Vanilla));
    private final BooleanSetting soulSand = add(new BooleanSetting("SoulSand", true));
    private final BooleanSetting sneak = add(new BooleanSetting("Sneak", false));
    private final BooleanSetting climb = add(new BooleanSetting("Climb", false));
    private final BooleanSetting gui = add(new BooleanSetting("Gui", true));
    private final BooleanSetting allowSneak = add(new BooleanSetting("AllowSneak", false, gui::getValue));
    private final EnumSetting<Bypass> clickBypass = add(new EnumSetting<>("GuiMoveBypass", Bypass.None));
    boolean using = false;
    int delay = 0;

    public NoSlow() {
        super("NoSlow", Category.Movement);
        setChinese("无减速");
        INSTANCE = this;
    }

    private static float getMovementMultiplier(boolean positive, boolean negative) {
        if (positive == negative) {
            return 0.0F;
        } else {
            return positive ? 1.0F : -1.0F;
        }
    }

    /**
     * 把三个移动键 + 跳跃/潜行按键状态同步到 26.2 的 {@link Input} record 与 {@link Vec2} 移动向量。
     * <p>
     * 旧版直接写 {@code input.pressingForward/movementForward} 等散装字段；
     * 26.2 的 {@code ClientInput} 只保留 {@code keyPresses}（Input record）和 {@code moveVector}（Vec2），
     * 后者是 protected，通过 {@link IClientInput} accessor 写入。
     */
    private static void applyMovementKeys() {
        boolean forward = mc.options.keyUp.isDown();
        boolean back = mc.options.keyDown.isDown();
        boolean left = mc.options.keyLeft.isDown();
        boolean right = mc.options.keyRight.isDown();

        mc.player.input.keyPresses = new Input(
                forward,
                back,
                left,
                right,
                mc.options.keyJump.isDown(),
                mc.options.keyShift.isDown(),
                mc.player.input.keyPresses.sprint()
        );

        // Vec2(x = 左右冲量, y = 前后冲量)，与 KeyboardInput.tick() 的计算方式一致
        ((IClientInput) mc.player.input).setMoveVector(new Vec2(
                getMovementMultiplier(left, right),
                getMovementMultiplier(forward, back)
        ).normalized());
    }

    private static boolean isKeyDown(KeyMapping mapping) {
        return InputConstants.isKeyDown(mc.getWindow(), mapping.getDefaultKey().getValue());
    }

    @Override
    public String getInfo() {
        return mode.getValue().name();
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        using = mc.player.isUsingItem();
        delay--;
        if (using) {
            delay = 2;
        }
        if (using && !mc.player.isPassenger() && !mc.player.isFallFlying()) {
            switch (mode.getValue()) {
                case NCP ->
                        mc.getConnection().send(new ServerboundSetCarriedItemPacket(mc.player.getInventory().getSelectedSlot()));
                case Grim -> {
                    // 26.2：PlayerInteractItemC2SPacket → ServerboundUseItemPacket(hand, sequence, yRot, xRot)
                    if (mc.player.getUsedItemHand() == InteractionHand.OFF_HAND) {
                        sendSequencedPacket(id -> new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, id, Alien.ROTATION.getLastYaw(), Alien.ROTATION.getLastPitch()));
                    } else {
                        sendSequencedPacket(id -> new ServerboundUseItemPacket(InteractionHand.OFF_HAND, id, Alien.ROTATION.getLastYaw(), Alien.ROTATION.getLastPitch()));
                    }
                }
                case GrimPacket -> {
                    // 26.2：clickSlot → handleContainerInput，SlotActionType → ContainerInput
                    mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, 1, 0, ContainerInput.PICKUP, mc.player);
                    if (mc.player.getUsedItemHand() == InteractionHand.OFF_HAND) {
                        sendSequencedPacket(id -> new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, id, Alien.ROTATION.getLastYaw(), Alien.ROTATION.getLastPitch()));
                    } else {
                        sendSequencedPacket(id -> new ServerboundUseItemPacket(InteractionHand.OFF_HAND, id, Alien.ROTATION.getLastYaw(), Alien.ROTATION.getLastPitch()));
                    }
                }
            }
        }
        if (gui.getValue()) {
            // 26.2：mc.gui.screen() → mc.gui.screen()
            if (!(mc.gui.screen() instanceof ChatScreen)) {
                // 26.2：KeyBinding → KeyMapping，backKey/leftKey/rightKey → keyDown/keyLeft/keyRight
                for (KeyMapping k : new KeyMapping[]{mc.options.keyDown, mc.options.keyLeft, mc.options.keyRight}) {
                    k.setDown(isKeyDown(k));
                }
                mc.options.keyJump.setDown(ElytraFly.INSTANCE.isOn() && ElytraFly.INSTANCE.mode.is(ElytraFly.Mode.Bounce) && ElytraFly.INSTANCE.autoJump.getValue() || isKeyDown(mc.options.keyJump));
                mc.options.keyUp.setDown(AutoWalk.INSTANCE.forward() || isKeyDown(mc.options.keyUp));
                mc.options.keySprint.setDown(Sprint.INSTANCE.isOn() && !Sprint.INSTANCE.inWater() || isKeyDown(mc.options.keySprint));

                if (allowSneak.getValue()) {
                    mc.options.keyShift.setDown(isKeyDown(mc.options.keyShift));
                }
            }
        }
    }

    @EventListener(priority = EventPriority.HIGH)
    public void keyboard(KeyboardInputEvent event) {
        if (sneak.getValue()) {
            event.cancel();
        }
        if (gui.getValue()) {
            if (!(mc.gui.screen() instanceof ChatScreen)) {
                for (KeyMapping k : new KeyMapping[]{mc.options.keyDown, mc.options.keyLeft, mc.options.keyRight}) {
                    k.setDown(isKeyDown(k));
                }
                mc.options.keyJump.setDown(ElytraFly.INSTANCE.isOn() && ElytraFly.INSTANCE.mode.is(ElytraFly.Mode.Bounce) && ElytraFly.INSTANCE.autoJump.getValue() || isKeyDown(mc.options.keyJump));
                mc.options.keyUp.setDown(AutoWalk.INSTANCE.forward() || isKeyDown(mc.options.keyUp));
                mc.options.keySprint.setDown(Sprint.INSTANCE.isOn() && !Sprint.INSTANCE.inWater() || isKeyDown(mc.options.keySprint));

                if (allowSneak.getValue()) {
                    mc.options.keyShift.setDown(isKeyDown(mc.options.keyShift));
                }
                applyMovementKeys();
            }
        }
    }

    @EventListener
    public void onUse(InteractItemEvent event) {
        if (event.isPre()) {
            if (delay > 0) {
                // 26.2：itemUseCooldown 改名为 rightClickDelay（private，走 accessor）
                ((IMinecraftClient) mc).setRightClickDelay(0);
                event.cancel();
            } else if (mode.is(Mode.GrimPacket) && mc.player != null
                    && mc.player.getItemInHand(event.hand).getItem().components().has(DataComponents.FOOD)) {
                mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, 1, 0, ContainerInput.PICKUP, mc.player);
            }
        }
    }

    @EventListener
    public void onPacketSend(PacketEvent.Send e) {
        if (nullCheck()) {
            return;
        }
        if (mode.is(Mode.Drop) && e.getPacket() instanceof ServerboundUseItemPacket packet) {
            if (packet.getHand() == InteractionHand.MAIN_HAND
                    && mc.player.getMainHandItem().getItem().components().has(DataComponents.FOOD)) {
                mc.getConnection().send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.DROP_ITEM, BlockPos.ZERO, Direction.DOWN));
                return;
            }
        }
        if (!MovementUtil.isMoving() || pause.get()) {
            return;
        }

        if (e.getPacket() instanceof ServerboundContainerClickPacket click) {
            switch (clickBypass.getValue()) {
                case Grim -> {
                    if (click.containerInput() != ContainerInput.PICKUP && click.containerInput() != ContainerInput.PICKUP_ALL)
                        mc.getConnection().send(new ServerboundContainerClosePacket(0));
                }

                case NCP -> {
                    if (mc.player.onGround() && !mc.level.getBlockCollisions(mc.player, mc.player.getBoundingBox().move(0.0, 0.0656, 0.0)).iterator().hasNext()) {
                        if (mc.player.isSprinting())
                            mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
                        mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY() + 0.0656, mc.player.getZ(), false, false));
                    }
                }

                case NCP2 -> {
                    if (mc.player.onGround() && !mc.level.getBlockCollisions(mc.player, mc.player.getBoundingBox().move(0.0, 0.000000271875, 0.0)).iterator().hasNext()) {
                        if (mc.player.isSprinting())
                            mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.STOP_SPRINTING));
                        mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(), mc.player.getY() + 0.000000271875, mc.player.getZ(), false, false));
                    }
                }

                case Delay -> {
                    storedClicks.add(click);
                    e.cancel();
                }
            }
        }

        if (e.getPacket() instanceof ServerboundContainerClosePacket) {
            if (clickBypass.is(Bypass.Delay)) {
                pause.set(true);
                while (!storedClicks.isEmpty())
                    mc.getConnection().send(storedClicks.poll());
                pause.set(false);
            }
        }
    }

    @EventListener
    public void onPacketSendPost(PacketEvent.Sent e) {
        if (e.getPacket() instanceof ServerboundContainerClickPacket) {
            if (mc.player.isSprinting() && clickBypass.is(Bypass.NCP))
                mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_SPRINTING));
        }
    }

    public boolean noSlow() {
        return isOn() && mode.getValue() != Mode.None && (mode.getValue() != Mode.Drop && mode.getValue() != Mode.GrimPacket || using);
    }

    public boolean soulSand() {
        return isOn() && soulSand.getValue();
    }

    public boolean climb() {
        return isOn() && climb.getValue();
    }

    private enum Bypass {
        None, NCP, NCP2, Grim, Delay,
    }

    public enum Mode {
        Vanilla,
        NCP,
        Grim,
        GrimPacket,
        Drop,
        None
    }
}
