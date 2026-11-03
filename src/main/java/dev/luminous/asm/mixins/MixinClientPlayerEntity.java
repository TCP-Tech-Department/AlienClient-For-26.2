package dev.luminous.asm.mixins;

import dev.luminous.Alien;
import dev.luminous.api.events.Event;
import dev.luminous.api.events.impl.*;
import dev.luminous.asm.accessors.IClientPlayerEntity;
import dev.luminous.core.impl.CommandManager;
import dev.luminous.core.impl.RotationManager;
import dev.luminous.mod.modules.impl.client.AntiCheat;
import dev.luminous.mod.modules.impl.client.ClientSetting;
import dev.luminous.mod.modules.impl.exploit.PacketControl;
import dev.luminous.mod.modules.impl.movement.NoSlow;
import dev.luminous.mod.modules.impl.movement.Velocity;
import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.chat.ChatAbilities;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.stats.StatsCounter;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class MixinClientPlayerEntity extends AbstractClientPlayer {

    @Shadow
    public ClientInput input; // 26.2: 类型改为 ClientInput
    @Final
    @Shadow
    protected Minecraft minecraft; // 26.2: was client
    @Unique
    private float preYaw;
    @Unique
    private float prePitch;
    @Unique
    private boolean rotation = false;

    @Shadow
    private double xLast; // 26.2: was lastX
    @Shadow
    private double yLast; // 26.2: was lastBaseY
    @Shadow
    private double zLast; // 26.2: was lastZ

    // 26.2: LocalPlayer 只剩一个构造器（多了 Minecraft/connection/stats/recipeBook/lastSentInput/wasSprinting/chatAbilities），
    // Mixin 构造器必须与目标构造器签名一致
    public MixinClientPlayerEntity(Minecraft minecraft, ClientLevel level, ClientPacketListener connection, StatsCounter stats,
                                   ClientRecipeBook recipeBook, Input lastSentInput, boolean wasSprinting, ChatAbilities chatAbilities) {
        super(level, connection.getLocalGameProfile());
    }

    // 26.2: was pushOutOfBlocks(double, double)
    @Inject(method = "moveTowardsClosestSpace",
            at = @At("HEAD"),
            cancellable = true)
    private void onPushOutOfBlocksHook(double x, double z, CallbackInfo info) {
        if (Velocity.INSTANCE.isOn() && Velocity.INSTANCE.blockPush.getValue()) {
            info.cancel();
        }
    }

    // 26.2: was @Redirect on LocalPlayer#isUsingItem in "tickMovement"；
    // 26.2 的减速改在 modifyInput() 里判断 isUsingItem() && !isPassenger()
    @Redirect(method = "modifyInput",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/player/LocalPlayer;isUsingItem()Z"),
            require = 0)
    private boolean tickMovementHook(LocalPlayer player) {
        if (NoSlow.INSTANCE.noSlow()) {
            return false;
        }
        return player.isUsingItem();
    }

    // 26.2: was @Inject(method = "tickNausea", at = @At("HEAD"), cancellable = true)。
    //      1.21 的 LocalPlayer#tickNausea(boolean) 在 26.2 改名为
    //      LocalPlayer#handlePortalTransitionEffect(boolean)（仍是 private void，签名一致）：
    //      内部的「进传送门时关掉当前屏幕 / 播 PORTAL_TRIGGER 音效 / intensity 每 tick +0.0125 /
    //      离开后 -0.05 并 clamp 到 [0,1]」与旧 tickNausea 逐行对应，强度字段为
    //      portalEffectIntensity / oPortalEffectIntensity（LocalPlayer.java:147）。
    //      强度恒为 0 时：Hud.java:298-301 不画 extractPortalOverlay，
    //      GameRenderer.java:263-270 的 spinningEffectSpeed 恒为 0（镜头不再旋转）。
    //      aiStep 里的调用点也从 `currentScreen instanceof DownloadingTerrainScreen` 换成了
    //      `gui.screen() instanceof LevelLoadingScreen`（LocalPlayer.java:768）。
    @Inject(method = "handlePortalTransitionEffect",
            at = @At("HEAD"),
            cancellable = true)
    private void updateNausea(boolean active, CallbackInfo ci) {
        if (ClientSetting.INSTANCE.portalGui()) {
            ci.cancel();
        }
    }

    // 26.2: MovementType -> MoverType，Vec3d -> Vec3，owner 仍为 AbstractClientPlayer
    @Inject(method = "move", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/AbstractClientPlayer;move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V"), cancellable = true)
    public void onMoveHook(MoverType movementType, Vec3 movement, CallbackInfo ci) {
        MoveEvent event = MoveEvent.get(movement.x, movement.y, movement.z);
        Alien.EVENT_BUS.post(event);
        if (event.isCancelled()) {
            ci.cancel();
        } else if (event.modify) {
            ci.cancel();
            super.move(movementType, new Vec3(event.getX(), event.getY(), event.getZ()));
            Alien.EVENT_BUS.post(MovedEvent.INSTANCE);
        }
    }

    @Inject(method = "move", at = @At(value = "TAIL"))
    public void onMoveReturnHook(MoverType movementType, Vec3 movement, CallbackInfo ci) {
        Alien.EVENT_BUS.post(MovedEvent.INSTANCE);
    }

    @Inject(method = "tick",
            at = @At(value = "HEAD"))
    private void tickHead(CallbackInfo ci) {
        try {
            Alien.EVENT_BUS.post(TickEvent.get(Event.Stage.Pre));
        } catch (Exception e) {
            e.printStackTrace();
            if (ClientSetting.INSTANCE.debug.getValue())
                CommandManager.sendMessage("§4An error has occurred (LocalPlayer.tick() [HEAD]) Message: [" + e.getMessage() + "]");
        }
    }

    @Inject(method = "tick",
            at = @At(value = "RETURN"))
    private void tickReturn(CallbackInfo ci) {
        try {
            Alien.EVENT_BUS.post(TickEvent.get(Event.Stage.Post));
        } catch (Exception e) {
            e.printStackTrace();
            if (ClientSetting.INSTANCE.debug.getValue())
                CommandManager.sendMessage("§4An error has occurred (LocalPlayer.tick() [RETURN]) Message: [" + e.getMessage() + "]");
        }
    }

    @Shadow
    private int positionReminder; // 26.2: was ticksSinceLastPositionPacketSent
    @Shadow
    private float yRotLast; // 26.2: was lastYaw
    @Shadow
    private float xRotLast; // 26.2: was lastPitch

    // 26.2: was sendMovementPackets
    @Inject(method = "sendPosition", at = @At("HEAD"))
    private void onSendMovementPacketsHead(CallbackInfo info) {
        rotation();
        if (PacketControl.INSTANCE.isOn() && PacketControl.INSTANCE.positionSync.getValue()) {
            if (this.positionReminder >= PacketControl.INSTANCE.positionDelay.getValueInt() - 1) {
                ((IClientPlayerEntity) this).setTicksSinceLastPositionPacketSent(50);
            }
        }
        if (RotationManager.snapBack) {
            ((IClientPlayerEntity) this).setTicksSinceLastPositionPacketSent(50);
            ((IClientPlayerEntity) this).setLastYaw(999);
            RotationManager.snapBack = false;
            return;
        }
        if (AntiCheat.INSTANCE.fullPackets.getValue()) {
            double d = this.getX() - this.xLast;
            double e = this.getY() - this.yLast;
            double f = this.getZ() - this.zLast;
            double g = this.getYRot() - this.yRotLast; // 26.2: was getYaw()
            double h = this.getXRot() - this.xRotLast; // 26.2: was getPitch()
            boolean bl3 = g != 0.0 || h != 0.0;
            if (AntiCheat.INSTANCE.force.getValue() || !(Mth.lengthSquared(d, e, f) > Mth.square(2.0E-4)) && this.positionReminder >= 19 || bl3) {
                ((IClientPlayerEntity) this).setTicksSinceLastPositionPacketSent(50);
                ((IClientPlayerEntity) this).setLastYaw(999);
            }
        }
    }

    // 26.2: was @At(INVOKE, ClientPlayNetworkHandler#sendPacket, ordinal = 0)
    // 现在 tick() 内乘客分支构造 ServerboundMovePlayerPacket.Rot 之前就是发包时机
    @Inject(method = "tick", at = @At(value = "NEW", target = "Lnet/minecraft/network/protocol/game/ServerboundMovePlayerPacket$Rot;"))
    private void onTickHasVehicleBeforeSendPackets(CallbackInfo info) {
        rotation();
    }

    @Unique
    private void rotation() {
        rotation = true;
        preYaw = getYRot();
        prePitch = getXRot();

        SendMovementPacketsEvent event = SendMovementPacketsEvent.get(getYRot(), getXRot());
        Alien.EVENT_BUS.post(event);

        Alien.ROTATION.rotationYaw = event.getYaw();
        Alien.ROTATION.rotationPitch = event.getPitch();
        setYRot(event.getYaw()); // 26.2: was setYaw
        setXRot(event.getPitch()); // 26.2: was setPitch
    }

    @Inject(method = "sendPosition", at = @At("TAIL"))
    private void onSendMovementPacketsTail(CallbackInfo info) {
        if (rotation) {
            setYRot(preYaw);
            setXRot(prePitch);
            rotation = false;
        }
    }

    // 26.2: was @At(INVOKE, ClientPlayNetworkHandler#sendPacket, ordinal = 1, shift = AFTER)
    @Inject(method = "tick", at = @At("TAIL"))
    private void onTickHasVehicleAfterSendPackets(CallbackInfo info) {
        if (rotation) {
            setYRot(preYaw);
            setXRot(prePitch);
            rotation = false;
        }
    }

    // 26.2: tickMovement -> aiStep
    @Inject(method = "aiStep", at = @At("HEAD"))
    private void tickMovement(CallbackInfo ci) {
        Alien.EVENT_BUS.post(TickMovementEvent.INSTANCE);
    }
}
