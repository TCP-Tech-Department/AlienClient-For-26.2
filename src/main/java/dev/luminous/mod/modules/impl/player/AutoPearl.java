package dev.luminous.mod.modules.impl.player;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.RotationEvent;
import dev.luminous.api.events.impl.TickEvent;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.exploit.Blink;
import dev.luminous.mod.modules.impl.movement.ElytraFly;
import dev.luminous.mod.modules.impl.movement.Velocity;
import dev.luminous.mod.modules.settings.enums.SwingSide;
import dev.luminous.mod.modules.settings.enums.Timing;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;

public class AutoPearl extends Module {
    public static AutoPearl INSTANCE;
    public static boolean throwing = false;
    public final BooleanSetting inventory =
            add(new BooleanSetting("InventorySwap", true));
    private final EnumSetting<Timing> timing =
            add(new EnumSetting<>("Timing", Timing.All));
    public final EnumSetting<SwingSide> interactSwing = add(new EnumSetting<>("Swing", SwingSide.All));
    private final BooleanSetting rotation = add(new BooleanSetting("Rotation", true));
    private final BooleanSetting yawStep = add(new BooleanSetting("YawStep", false).setParent());
    private final BooleanSetting whenElytra =
            add(new BooleanSetting("FallFlying", true, yawStep::isOpen));
    private final SliderSetting steps = add(new SliderSetting("Steps", 0.05, 0, 1, 0.01, yawStep::isOpen));
    private final SliderSetting fov = add(new SliderSetting("Fov", 20, 0, 360, 0.1, yawStep::isOpen));
    private final SliderSetting priority = add(new SliderSetting("Priority", 100, 0, 100, yawStep::isOpen));
    private final BooleanSetting sync = add(new BooleanSetting("Sync", true, yawStep::isOpen));

    public AutoPearl() {
        super("AutoPearl", Category.Player);
        setChinese("扔珍珠");
        INSTANCE = this;
    }

    @Override
    public void onEnable() {
        if (nullCheck()) {
            disable();
            return;
        }
        if (AutoPearl.INSTANCE.inventory.getValue()) {
            if (InventoryUtil.findItemInventorySlotFromZero(Items.ENDER_PEARL) == -1) {
                disable();
                return;
            }
        } else {
            if (InventoryUtil.findItem(Items.ENDER_PEARL) == -1) {
                disable();
                return;
            }
        }
        if (shouldYawStep()) {
            return;
        }
        if (inventory.getValue() && !EntityUtil.inInventory()) return;
        if (Blink.INSTANCE.isOn() && Blink.INSTANCE.pauseModule.getValue()) return;
        throwPearl(mc.player.getYRot(), mc.player.getXRot());
        disable();
    }

    @EventListener
    public void onUpdate(TickEvent event) {
        if (nullCheck()) return;
        if (timing.is(Timing.Pre) && event.isPost() || timing.is(Timing.Post) && event.isPre()) return;
        if (Blink.INSTANCE.isOn() && Blink.INSTANCE.pauseModule.getValue()) return;
        if (!shouldYawStep()) {
            throwPearl(mc.player.getYRot(), mc.player.getXRot());
            disable();
        } else if (Alien.ROTATION.inFov(mc.player.getYRot(), mc.player.getXRot(), fov.getValueFloat())) {
            if (sync.getValue()) {
                throwPearl(mc.player.getYRot(), mc.player.getXRot());
            } else {
                throwing = true;
                int pearl;

                if (mc.player.getMainHandItem().getItem() == Items.ENDER_PEARL) {
                    sendSequencedPacket(id -> new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, id, Alien.ROTATION.getLastYaw(), Alien.ROTATION.getLastPitch()));
                    EntityUtil.swingHand(InteractionHand.MAIN_HAND, interactSwing.getValue());
                } else if (inventory.getValue() && (pearl = InventoryUtil.findItemInventorySlotFromZero(Items.ENDER_PEARL)) != -1) {
                    InventoryUtil.inventorySwap(pearl, mc.player.getInventory().getSelectedSlot());
                    sendSequencedPacket(id -> new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, id, Alien.ROTATION.getLastYaw(), Alien.ROTATION.getLastPitch()));
                    EntityUtil.swingHand(InteractionHand.MAIN_HAND, interactSwing.getValue());
                    InventoryUtil.inventorySwap(pearl, mc.player.getInventory().getSelectedSlot());
                    EntityUtil.syncInventory();
                } else if ((pearl = InventoryUtil.findItem(Items.ENDER_PEARL)) != -1) {
                    int old = mc.player.getInventory().getSelectedSlot();
                    InventoryUtil.switchToSlot(pearl);
                    sendSequencedPacket(id -> new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, id, Alien.ROTATION.getLastYaw(), Alien.ROTATION.getLastPitch()));
                    EntityUtil.swingHand(InteractionHand.MAIN_HAND, interactSwing.getValue());
                    InventoryUtil.switchToSlot(old);
                }
                throwing = false;
            }
            disable();
        }
    }

    @EventListener()
    public void onRotate(RotationEvent event) {
        if (shouldYawStep()) {
            event.setRotation(mc.player.getYRot(), mc.player.getXRot(), steps.getValueFloat(), priority.getValueFloat());
        }
    }

    private boolean shouldYawStep() {
        if (!whenElytra.getValue() && (mc.player.isFallFlying() || ElytraFly.INSTANCE.isOn() && ElytraFly.INSTANCE.isFallFlying()))
            return false;
        return yawStep.getValue() && !Velocity.INSTANCE.noRotation();
    }

    public void throwPearl(float yaw, float pitch) {
        throwing = true;
        int pearl;
        if (mc.player.getMainHandItem().getItem() == Items.ENDER_PEARL) {
            if (rotation.getValue())
                Alien.ROTATION.snapAt(yaw, pitch);
            sendSequencedPacket(id -> new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, id, yaw, pitch));
            EntityUtil.swingHand(InteractionHand.MAIN_HAND, interactSwing.getValue());
            if (rotation.getValue())
                Alien.ROTATION.snapBack();
        } else if (inventory.getValue() && (pearl = InventoryUtil.findItemInventorySlotFromZero(Items.ENDER_PEARL)) != -1) {
            InventoryUtil.inventorySwap(pearl, mc.player.getInventory().getSelectedSlot());
            if (rotation.getValue())
                Alien.ROTATION.snapAt(yaw, pitch);
            sendSequencedPacket(id -> new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, id, yaw, pitch));
            EntityUtil.swingHand(InteractionHand.MAIN_HAND, interactSwing.getValue());
            InventoryUtil.inventorySwap(pearl, mc.player.getInventory().getSelectedSlot());
            EntityUtil.syncInventory();
            if (rotation.getValue())
                Alien.ROTATION.snapBack();
        } else if ((pearl = InventoryUtil.findItem(Items.ENDER_PEARL)) != -1) {
            int old = mc.player.getInventory().getSelectedSlot();
            InventoryUtil.switchToSlot(pearl);
            if (rotation.getValue())
                Alien.ROTATION.snapAt(yaw, pitch);
            sendSequencedPacket(id -> new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, id, yaw, pitch));
            EntityUtil.swingHand(InteractionHand.MAIN_HAND, interactSwing.getValue());
            InventoryUtil.switchToSlot(old);
            if (rotation.getValue())
                Alien.ROTATION.snapBack();
        }
        throwing = false;
    }
}