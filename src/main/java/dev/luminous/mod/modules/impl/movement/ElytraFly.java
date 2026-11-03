package dev.luminous.mod.modules.impl.movement;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.*;
import dev.luminous.api.utils.math.MathUtil;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.api.utils.player.MovementUtil;
import dev.luminous.asm.accessors.ILivingEntity;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.client.AntiCheat;
import dev.luminous.mod.modules.settings.impl.BindSetting;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
// 26.2: MovementType → MoverType
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.InteractionHand;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;

import static dev.luminous.api.utils.player.MovementUtil.*;

public class ElytraFly extends Module {
    public static ElytraFly INSTANCE;
    public final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", Mode.Control));
    public final BooleanSetting infiniteDura = add(new BooleanSetting("InfiniteDura", false));
    public final BooleanSetting packet = add(new BooleanSetting("Packet", false).setParent());
    private final SliderSetting packetDelay =
            add(new SliderSetting("PacketDelay", 0, 0, 20, 1, packet::isOpen));
    private final BooleanSetting setFlag = add(new BooleanSetting("SetFlag", false, () -> !mode.is(Mode.Bounce)));
    private final BooleanSetting firework = add(new BooleanSetting("Firework", false).setParent());
    public final BindSetting fireWork = add(new BindSetting("FireWorkBind", -1, firework::isOpen));
    public final BooleanSetting packetInteract = add(new BooleanSetting("PacketInteract", true, firework::isOpen));
    public final BooleanSetting inventory = add(new BooleanSetting("InventorySwap", true, firework::isOpen));
    public final BooleanSetting onlyOne = add(new BooleanSetting("OnlyOne", true, firework::isOpen));
    private final BooleanSetting usingPause = add(new BooleanSetting("UsingPause", true, firework::isOpen));
    public final BooleanSetting autoJump = add(new BooleanSetting("AutoJump", true, () -> mode.is(Mode.Bounce)));
    public final SliderSetting upPitch = add(new SliderSetting("UpPitch", 0.0f, 0.0f, 90.0f, () -> mode.getValue() == Mode.Control));
    public final SliderSetting upFactor = add(new SliderSetting("UpFactor", 1.0f, 0.0f, 10.0f, () -> mode.getValue() == Mode.Control));
    public final SliderSetting downFactor = add(new SliderSetting("FallSpeed", 1.0f, 0.0f, 10.0f, () -> mode.getValue() == Mode.Control));
    public final SliderSetting speed = add(new SliderSetting("Speed", 1.0f, 0.1f, 10.0f, () -> mode.getValue() == Mode.Control));
    public final BooleanSetting speedLimit = add(new BooleanSetting("SpeedLimit", true, () -> mode.getValue() == Mode.Control));
    public final SliderSetting maxSpeed = add(new SliderSetting("MaxSpeed", 2.5f, 0.1f, 10.0f, () -> speedLimit.getValue() && mode.getValue() == Mode.Control));
    public final BooleanSetting noDrag = add(new BooleanSetting("NoDrag", false, () -> mode.getValue() == Mode.Control));
    public final Timer fireworkTimer = new Timer();
    private final BooleanSetting autoStop = add(new BooleanSetting("AutoStop", true));
    private final BooleanSetting sprint = add(new BooleanSetting("Sprint", true, () -> mode.is(Mode.Bounce)));
    private final SliderSetting pitch =
            add(new SliderSetting("Pitch", 88, -90, 90, 0.1, () -> mode.is(Mode.Bounce)));
    private final BooleanSetting instantFly = add(new BooleanSetting("AutoStart", true, () -> !mode.is(Mode.Bounce)));
    private final BooleanSetting checkSpeed = add(new BooleanSetting("CheckSpeed", false, () -> !mode.is(Mode.Bounce)));
    public final SliderSetting minSpeed = add(new SliderSetting("MinSpeed", 70.0, 0.1, 200.0, () -> !mode.is(Mode.Bounce)));
    private final SliderSetting delay =
            add(new SliderSetting("Delay", 1000, 0, 20000, 50, () -> !mode.is(Mode.Bounce)));
    private final SliderSetting timeout = add(new SliderSetting("Timeout", 0, 0.1, 1, 0.1, () -> !mode.is(Mode.Bounce)));
    private final SliderSetting sneakDownSpeed = add(new SliderSetting("DownSpeed", 1.0, 0.1, 10.0, () -> mode.getValue() == Mode.Control));
    private final SliderSetting boost = add(new SliderSetting("Boost", 1, 0.1, 4, () -> mode.getValue() == Mode.Boost));
    private final BooleanSetting freeze = add(new BooleanSetting("Freeze", false, () -> mode.is(Mode.Rotation)));
    private final BooleanSetting motionStop = add(new BooleanSetting("MotionStop", false, () -> mode.is(Mode.Rotation)));
    private final SliderSetting infiniteMaxSpeed = add(new SliderSetting("InfiniteMaxSpeed", 150f, 50f, 170f, () -> mode.getValue() == Mode.Pitch));
    private final SliderSetting infiniteMinSpeed = add(new SliderSetting("InfiniteMinSpeed", 25f, 10f, 70f, () -> mode.getValue() == Mode.Pitch));
    private final SliderSetting infiniteMaxHeight = add(new SliderSetting("InfiniteMaxHeight", 200, -50, 360, () -> mode.getValue() == Mode.Pitch));
    public final BooleanSetting releaseSneak = add(new BooleanSetting("ReleaseSneak", false));
    private final Timer instantFlyTimer = new Timer();

    boolean prev;
    float prePitch;
    private boolean hasElytra = false;

    public ElytraFly() {
        super("ElytraFly", Category.Movement);
        setChinese("鞘翅飞行");
        INSTANCE = this;
        Alien.EVENT_BUS.subscribe(new FireWorkTweak());
    }

    public void off() {
        if (inventory.getValue() && !EntityUtil.inInventory()) return;
        if (onlyOne.getValue()) {
            for (Entity entity : Alien.THREAD.getEntities()) {
                if (entity instanceof FireworkRocketEntity fireworkRocketEntity) {
                    // 26.2: 旧的 shooter 字段（accessor）已不存在，Projectile#getOwner 是等价语义
                    if (fireworkRocketEntity.getOwner() == mc.player) {
                        return;
                    }
                }
            }
        }
        ElytraFly.INSTANCE.fireworkTimer.reset();
        int firework;
        if (mc.player.getMainHandItem().getItem() == Items.FIREWORK_ROCKET) {
            if (packetInteract.getValue()) {
                sendSequencedPacket(id -> new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, id, Alien.ROTATION.getLastYaw(), Alien.ROTATION.getLastPitch()));
            } else {
                // 26.2: interactItem → useItem
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            }
        } else if (inventory.getValue() && (firework = InventoryUtil.findItemInventorySlot(Items.FIREWORK_ROCKET)) != -1) {
            InventoryUtil.inventorySwap(firework, mc.player.getInventory().getSelectedSlot());
            if (packetInteract.getValue()) {
                sendSequencedPacket(id -> new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, id, Alien.ROTATION.getLastYaw(), Alien.ROTATION.getLastPitch()));
            } else {
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            }
            InventoryUtil.inventorySwap(firework, mc.player.getInventory().getSelectedSlot());
            EntityUtil.syncInventory();
        } else if ((firework = InventoryUtil.findItem(Items.FIREWORK_ROCKET)) != -1) {
            int old = mc.player.getInventory().getSelectedSlot();
            InventoryUtil.switchToSlot(firework);
            if (packetInteract.getValue()) {
                sendSequencedPacket(id -> new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, id, Alien.ROTATION.getLastYaw(), Alien.ROTATION.getLastPitch()));
            } else {
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            }
            InventoryUtil.switchToSlot(old);
        }
    }

    /**
     * 26.2: ElytraItem 类已移除（鞘翅改为普通物品 + GLIDER / EQUIPPABLE 组件）。
     * 等价判断：带鞘翅物品且未损坏到下一次使用会碎，即 {@code LivingEntity.canGlideUsing} 的语义。
     */
    private static boolean isUsableElytra(ItemStack stack) {
        return stack.is(Items.ELYTRA) && !stack.nextDamageWillBreak();
    }

    public static boolean recastElytra(LocalPlayer player) {
        if (checkConditions(player) && ignoreGround(player)) {
            // 26.2: ServerboundPlayerCommandPacket.Mode → Action
            player.connection.send(new ServerboundPlayerCommandPacket(player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
            if (INSTANCE.setFlag.getValue()) mc.player.startFallFlying();
            return true;
        } else return false;
    }

    public static boolean checkConditions(LocalPlayer player) {
        // 26.2: getEquippedStack → getItemBySlot；isOf → is
        ItemStack itemStack = player.getItemBySlot(EquipmentSlot.CHEST);
        return (!player.getAbilities().flying && !player.isPassenger() && !player.onClimbable() && itemStack.is(Items.ELYTRA) && isUsableElytra(itemStack));
    }

    private static boolean ignoreGround(LocalPlayer player) {
        if (!player.isInWater() && !player.hasEffect(MobEffects.LEVITATION)) {
            ItemStack itemStack = player.getItemBySlot(EquipmentSlot.CHEST);
            if (itemStack.is(Items.ELYTRA) && isUsableElytra(itemStack)) {
                player.startFallFlying();
                return true;
            } else return false;
        } else return false;
    }

    @Override
    public String getInfo() {
        return mode.getValue().name();
    }

    @Override
    public void onEnable() {
        if (nullCheck()) return;
        hasElytra = false;
        yaw = mc.player.getYRot();
        rotationPitch = mc.player.getXRot();
    }

    @Override
    public void onDisable() {
        if (nullCheck()) return;
        if (releaseSneak.getValue()) {
            /*
             * 26.2: ServerboundPlayerCommandPacket.Action.RELEASE_SHIFT_KEY 已被移除
             * （26.2 枚举只剩 STOP_SLEEPING / START_SPRINTING / STOP_SPRINTING /
             *  START_RIDING_JUMP / STOP_RIDING_JUMP / OPEN_INVENTORY / START_FALL_FLYING）。
             * 潜行/按键状态改由 ServerboundPlayerInputPacket(Input) 逐 tick 同步
             * （见 LocalPlayer.java:231-233，只有 keyPresses 变化时才发）。
             * 因此这里清掉本地潜行键 + 输入位，并立刻补发一次输入包，
             * 让服务端马上收到“松开潜行”，等价于旧的 RELEASE_SHIFT_KEY。
             */
            mc.options.keyShift.setDown(false);
            Input keys = mc.player.input.keyPresses;
            Input released = new Input(keys.forward(), keys.backward(), keys.left(), keys.right(), keys.jump(), false, keys.sprint());
            mc.player.input.keyPresses = released;
            if (mc.getConnection() != null) {
                mc.getConnection().send(new ServerboundPlayerInputPacket(released));
            }
        }
    }

    private void boost() {
        if (hasElytra) {
            if (!isFallFlying()) {
                return;
            }
            float yaw = (float) Math.toRadians(mc.player.getYRot());
            if (mc.options.keyUp.isDown()) {
                // 26.2: addVelocity → push
                mc.player.push(-Mth.sin(yaw) * boost.getValueFloat() / 10, 0, Mth.cos(yaw) * boost.getValueFloat() / 10);
            }
        }
    }

    float yaw = 0;
    float rotationPitch = 0;

    @EventListener(priority = -9999)
    public void onRotation(UpdateRotateEvent event) {
        if (nullCheck()) return;
        if (mode.is(Mode.Rotation)) {
            if (isFallFlying()) {
                if (MovementUtil.isMoving()) {
                    // 26.2: jumpKey/sneakKey → keyJump/keyShift, isPressed → isDown
                    if (mc.options.keyJump.isDown()) {
                        rotationPitch = (-45);
                    } else if (mc.options.keyShift.isDown()) {
                        rotationPitch = (45);
                    } else {
                        rotationPitch = (-1.9f);
                        if (motionStop.getValue()) {
                            setY(0);
                        }
                    }
                } else {
                    if (mc.options.keyJump.isDown()) {
                        rotationPitch = (-89);
                    } else if (mc.options.keyShift.isDown()) {
                        rotationPitch = (89);
                    } else {
                        if (motionStop.getValue()) {
                            setY(0);
                        }
                    }
                }
                if (mc.options.keyUp.isDown() || mc.options.keyDown.isDown() || mc.options.keyLeft.isDown() || mc.options.keyRight.isDown()) {
                    yaw = Sprint.getSprintYaw(mc.player.getYRot());
                } else if (motionStop.getValue()) {
                    setX(0);
                    setZ(0);
                }
                event.setYaw(yaw);
                event.setPitch(rotationPitch);
            }
        } else if (mode.is(Mode.Pitch)) {
            if (isFallFlying()) {
                event.setPitch(infinitePitch);
            }
        } else if (mode.is(Mode.Bounce)) {
            if (isFallFlying()) {
                event.setPitch(pitch.getValueFloat());
            }
        }
    }

    boolean flying = false;
    int packetDelayInt = 0;

    @EventListener
    public void onUpdate(UpdateEvent event) {
        getInfinitePitch();
        flying = false;
        if (packet.getValue()) {
            hasElytra = InventoryUtil.findItemInventorySlot(Items.ELYTRA) != -1;
        } else {
            hasElytra = false;
            // 26.2: getArmorItems() 已移除，改为按装备槽检查；ElytraItem → GLIDER 组件判断
            for (EquipmentSlot slot : EquipmentSlot.VALUES) {
                if (isUsableElytra(mc.player.getItemBySlot(slot))) {
                    hasElytra = true;
                    break;
                }
            }
            if (infiniteDura.getValue()) {
                if (!mc.player.onGround() && hasElytra) {
                    flying = true;
                    mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, 6, 0, ContainerInput.PICKUP, mc.player);
                    mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, 6, 0, ContainerInput.PICKUP, mc.player);
                    mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
                    if (setFlag.getValue()) mc.player.startFallFlying();
                }
            }
            if (mode.is(Mode.Bounce)) {
                ((ILivingEntity) mc.player).setLastJumpCooldown(0);
                return;
            }
        }
        double x = mc.player.getX() - mc.player.xo;
        double y = mc.player.getY() - mc.player.yo;
        double z = mc.player.getZ() - mc.player.zo;
        double dist = Math.sqrt(x * x + z * z + y * y) / 1000.0;
        double div = 0.05 / 3600.0;
        float timer = Alien.TIMER.get();
        final double speed = dist / div * timer;
        if (mode.getValue() == Mode.Boost) {
            boost();
        }
        if (packet.getValue()) {
            if (mc.player.onGround()) return;
            packetDelayInt++;
            if (packetDelayInt <= packetDelay.getValue()) return;
            int elytra = InventoryUtil.findItem(Items.ELYTRA);
            if (elytra != -1) {
                mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, 6, elytra, ContainerInput.SWAP, mc.player);
                mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
                mc.player.startFallFlying();
                if ((!checkSpeed.getValue() || speed <= minSpeed.getValue()) && firework.getValue() && fireworkTimer.passed(delay.getValueInt()) && (MovementUtil.isMoving() || mode.is(Mode.Rotation) && (mc.options.keyJump.isDown())) && (!mc.player.isUsingItem() || !usingPause.getValue()) && isFallFlying()) {
                    off();
                    fireworkTimer.reset();
                }
                mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, 6, elytra, ContainerInput.SWAP, mc.player);
                packetDelayInt = 0;
            } else {
                elytra = InventoryUtil.findItemInventorySlot(Items.ELYTRA);
                if (elytra != -1) {
                    mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, elytra, 0, ContainerInput.PICKUP, mc.player);
                    mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, 6, 0, ContainerInput.PICKUP, mc.player);
                    mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
                    mc.player.startFallFlying();
                    if ((!checkSpeed.getValue() || speed <= minSpeed.getValue()) && firework.getValue() && fireworkTimer.passed(delay.getValueInt()) && (MovementUtil.isMoving() || mode.is(Mode.Rotation) && (mc.options.keyJump.isDown())) && (!mc.player.isUsingItem() || !usingPause.getValue()) && isFallFlying()) {
                        off();
                        fireworkTimer.reset();
                    }
                    mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, 6, 0, ContainerInput.PICKUP, mc.player);
                    mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, elytra, 0, ContainerInput.PICKUP, mc.player);
                    packetDelayInt = 0;
                }
            }
            return;
        }
        if ((!checkSpeed.getValue() || speed <= minSpeed.getValue()) && firework.getValue() && fireworkTimer.passed(delay.getValueInt()) && (MovementUtil.isMoving() || mode.is(Mode.Rotation) && (mc.options.keyJump.isDown())) && (!mc.player.isUsingItem() || !usingPause.getValue()) && isFallFlying()) {
            off();
            fireworkTimer.reset();
        }
        if (!isFallFlying() && hasElytra) {
            fireworkTimer.setMs(99999999);
            if (!mc.player.onGround() && instantFly.getValue() && mc.player.getDeltaMovement().y < 0D && !infiniteDura.getValue()) {
                if (!instantFlyTimer.passed((long) (1000 * timeout.getValue()))) return;
                instantFlyTimer.reset();
                mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
                if (setFlag.getValue()) mc.player.startFallFlying();
            }
        }
    }

    protected final Vec3 getRotationVector(float pitch, float yaw) {
        float f = pitch * 0.017453292F;
        float g = -yaw * 0.017453292F;
        float h = Mth.cos(g);
        float i = Mth.sin(g);
        float j = Mth.cos(f);
        float k = Mth.sin(f);
        return new Vec3(i * j, -k, h * j);
    }

    public Vec3 getRotationVec(float tickDelta) {
        // 26.2: getYaw(tickDelta) → getViewYRot(tickDelta)
        return this.getRotationVector(-upPitch.getValueFloat(), mc.player.getViewYRot(tickDelta));
    }

    @EventListener
    private void onPlayerMove(MoveEvent event) {
        if (autoStop.getValue()) {
            if (isFallFlying()) {
                int chunkX = (int) ((mc.player.getX()) / 16);
                int chunkZ = (int) ((mc.player.getZ()) / 16);
                // 26.2: getChunkManager().isChunkLoaded → getChunkSource().hasChunk
                if (!mc.level.getChunkSource().hasChunk(chunkX, chunkZ)) {
                    event.cancel();
                }
            }
        }
    }

    @EventListener
    private void onTick(ClientTickEvent event) {
        if (nullCheck()) return;
        if (mode.is(Mode.Bounce) && hasElytra) {
            // 26.2: jumpKey.setPressed(true) → keyJump.setDown(true)
            if (autoJump.getValue()) mc.options.keyJump.setDown(true);
            if (event.isPost()) {
                if (!isFallFlying())
                    mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));

                if (checkConditions(mc.player)) {
                    if (!sprint.getValue()) {
                        // Sprinting all the time (when not on ground) makes it rubberband on certain anticheats.
                        if (isFallFlying()) mc.player.setSprinting(mc.player.onGround());
                        else mc.player.setSprinting(true);
                    }
                }
            } else {
                if (checkConditions(mc.player) && sprint.getValue()) mc.player.setSprinting(true);
            }
        }
    }

    @EventListener
    private void onPacketSend(PacketEvent.Send event) {
        if (nullCheck()) return;
        if (mode.is(Mode.Bounce) && hasElytra && event.getPacket() instanceof ServerboundPlayerCommandPacket && ((ServerboundPlayerCommandPacket) event.getPacket()).getAction().equals(ServerboundPlayerCommandPacket.Action.START_FALL_FLYING) && !sprint.getValue()) {
            mc.player.setSprinting(true);
        }
    }

    @EventListener
    private void onPacketReceive(PacketEvent.Receive event) {
        if (nullCheck()) return;
        if (mode.is(Mode.Bounce) && hasElytra && event.getPacket() instanceof ClientboundPlayerPositionPacket) {
            mc.player.stopFallFlying();
        }
    }

    @EventListener
    public void travel(TravelEvent event) {
        if (nullCheck()) return;
        if (!AntiCheat.INSTANCE.movementSync()) {
            if (mode.is(Mode.Bounce) && hasElytra) {
                if (event.isPre()) {
                    prev = true;
                    prePitch = mc.player.getXRot();
                    mc.player.setXRot(pitch.getValueFloat());
                } else {
                    if (prev) {
                        prev = false;
                        mc.player.setXRot(prePitch);
                    }
                }
            } else if (mode.is(Mode.Pitch) && isFallFlying()) {
                if (event.isPre()) {
                    prev = true;
                    prePitch = mc.player.getXRot();
                    mc.player.setXRot(lastInfinitePitch);
                } else {
                    if (prev) {
                        prev = false;
                        mc.player.setXRot(prePitch);
                    }
                }
            }
        }
    }

    @EventListener
    public void onMove(TravelEvent event) {
        if (nullCheck() || !hasElytra || !isFallFlying() || event.isPost()) return;
        if (mode.is(Mode.Freeze) || mode.is(Mode.Rotation) && freeze.getValue()) {
            if (!MovementUtil.isMoving() && !mc.options.keyJump.isDown() && !mc.options.keyShift.isDown()) {
                event.cancel();
                return;
            }
        }
        if (mode.getValue() == Mode.Control) {
            if (firework.getValue()) {
                // 26.2: input.jumping / input.sneaking → keyPresses.jump() / keyPresses.shift()
                if (!(mc.options.keyShift.isDown() && mc.player.input.keyPresses.jump())) {
                    if (mc.options.keyShift.isDown()) {
                        setY(-sneakDownSpeed.getValue());
                    } else if (mc.player.input.keyPresses.jump()) {
                        setY(upFactor.getValue());
                    } else {
                        setY(-0.00000000003D * downFactor.getValue());
                    }
                } else {
                    setY(0);
                }
                double[] dir = directionSpeed(speed.getValue());
                setX(dir[0]);
                setZ(dir[1]);
            } else {
                Vec3 lookVec = getRotationVec(mc.getDeltaTracker().getGameTimeDeltaPartialTick(true));
                double lookDist = Math.sqrt(lookVec.x * lookVec.x + lookVec.z * lookVec.z);
                double motionDist = Math.sqrt(getX() * getX() + getZ() * getZ());
                if (mc.player.input.keyPresses.shift()) {
                    setY(-sneakDownSpeed.getValue());
                } else if (!mc.player.input.keyPresses.jump()) {
                    setY(-0.00000000003D * downFactor.getValue());
                }
                if (mc.player.input.keyPresses.jump()) {
                    if (motionDist > upFactor.getValue() / upFactor.getMax()) {
                        double rawUpSpeed = motionDist * 0.01325D;
                        setY(getY() + rawUpSpeed * 3.2D);
                        setX(getX() - lookVec.x * rawUpSpeed / lookDist);
                        setZ(getZ() - lookVec.z * rawUpSpeed / lookDist);
                    } else {
                        double[] dir = directionSpeed(speed.getValue());
                        setX(dir[0]);
                        setZ(dir[1]);
                    }
                }
                if (lookDist > 0.0D) {
                    setX(getX() + (lookVec.x / lookDist * motionDist - getX()) * 0.1D);
                    setZ(getZ() + (lookVec.z / lookDist * motionDist - getZ()) * 0.1D);
                }
                if (!mc.player.input.keyPresses.jump()) {
                    double[] dir = directionSpeed(speed.getValue());
                    setX(dir[0]);
                    setZ(dir[1]);
                }
                if (!noDrag.getValue()) {
                    setY(getY() * 0.9900000095367432D);
                    setX(getX() * 0.9800000190734863D);
                    setZ(getZ() * 0.9900000095367432D);
                }
                double finalDist = Math.sqrt(getX() * getX() + getZ() * getZ());
                if (speedLimit.getValue() && finalDist > maxSpeed.getValue()) {
                    setX(getX() * maxSpeed.getValue() / finalDist);
                    setZ(getZ() * maxSpeed.getValue() / finalDist);
                }
                event.cancel();
                mc.player.move(MoverType.SELF, mc.player.getDeltaMovement());
            }
        }
    }

    private double getX() {
        return getMotionX();
    }

    private void setX(double f) {
        setMotionX(f);
    }

    private double getY() {
        return getMotionY();
    }

    private void setY(double f) {
        setMotionY(f);
    }

    private double getZ() {
        return getMotionZ();
    }

    private void setZ(double f) {
        setMotionZ(f);
    }

    public enum Mode {
        Control, Boost, Bounce, Freeze, None, Rotation, Pitch
    }

    public class FireWorkTweak {

        boolean press;

        @EventListener
        public void onTick(ClientTickEvent event) {
            if (nullCheck() || event.isPost()) return;
            if (inventory.getValue() && !EntityUtil.inInventory()) return;
            if (mc.gui.screen() == null) {
                if (fireWork.isPressed()) {
                    if (!press) {
                        if (fireworkTimer.passed(delay.getValueInt()) && (!mc.player.isUsingItem() || !usingPause.getValue()) && isFallFlying()) {
                            off();
                            fireworkTimer.reset();
                        }
                    }
                    press = true;
                } else {
                    press = false;
                }
            } else {
                press = false;
            }
        }
    }

    private boolean down;
    private float lastInfinitePitch;
    private float infinitePitch;

    private void getInfinitePitch() {
        lastInfinitePitch = infinitePitch;
        double currentPlayerSpeed = Math.hypot(mc.player.getX() - mc.player.xo, mc.player.getZ() - mc.player.zo);
        if (mc.player.getY() < infiniteMaxHeight.getValue()) {
            if (currentPlayerSpeed * 72f < infiniteMinSpeed.getValue() && !down)
                down = true;
            if (currentPlayerSpeed * 72f > infiniteMaxSpeed.getValue() && down)
                down = false;
        } else down = true;

        if (down) infinitePitch += 3;
        else infinitePitch -= 3;

        infinitePitch = MathUtil.clamp(infinitePitch, -40, 40);
    }

    public boolean isFallFlying() {
        return mc.player.isFallFlying() || packet.getValue() && hasElytra && !mc.player.onGround() || flying;
    }
}