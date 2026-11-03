package dev.luminous.asm.mixins;

import dev.luminous.Alien;
import dev.luminous.api.events.impl.*;
import dev.luminous.mod.modules.impl.exploit.AntiPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.client.multiplayer.CommonListenerCookie;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.game.ClientboundContainerClosePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.network.protocol.game.ClientboundLoginPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundStartConfigurationPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(ClientPacketListener.class)
public abstract class MixinClientPlayNetworkHandler extends ClientCommonPacketListenerImpl {

    @Shadow
    private ClientLevel level; // 26.2: was world
    @Unique
    private boolean alien$worldNotNull;
    @Unique
    private boolean ignore;

    protected MixinClientPlayNetworkHandler(Minecraft minecraft, Connection connection, CommonListenerCookie cookie) {
        super(minecraft, connection, cookie);
    }

    // 26.2: was onEnterReconfiguration(EnterReconfigurationS2CPacket)
    @Inject(method = "handleConfigurationStart", at = @At("HEAD"))
    private void onEnterReconfiguration(ClientboundStartConfigurationPacket packet, CallbackInfo info) {
        Alien.EVENT_BUS.post(GameLeftEvent.INSTANCE);
    }

    // 26.2: was onGameJoin(GameJoinS2CPacket)
    @Inject(method = "handleLogin", at = @At("HEAD"))
    private void onGameJoinHead(ClientboundLoginPacket packet, CallbackInfo info) {
        alien$worldNotNull = level != null;
    }

    @Inject(method = "handleLogin", at = @At("TAIL"))
    private void onGameJoinTail(ClientboundLoginPacket packet, CallbackInfo info) {
        if (alien$worldNotNull) {
            Alien.EVENT_BUS.post(GameLeftEvent.INSTANCE);
        }
    }

    // 26.2: was sendChatMessage(String)
    @Shadow
    public abstract void sendChat(String content);

    // 26.2: was onInventory(InventoryS2CPacket)
    @Inject(method = "handleContainerContent", at = @At("HEAD"), cancellable = true)
    public void onInventoryS2CPacket(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        InventoryS2CPacketEvent event = InventoryS2CPacketEvent.get(packet);
        Alien.EVENT_BUS.post(event);
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    // 26.2: was sendChatMessage
    @Inject(method = "sendChat", at = @At("HEAD"), cancellable = true)
    private void onSendChatMessage(String message, CallbackInfo ci) {
        if (ignore) return;
        if (message.startsWith(Alien.getPrefix())) {
            Alien.COMMAND.command(message.split(" "));
            ci.cancel();
        } else {
            SendMessageEvent event = SendMessageEvent.get(message);
            Alien.EVENT_BUS.post(event);
            if (event.isCancelled()) {
                ci.cancel();
            } else if (!event.message.equals(event.defaultMessage)) {
                ignore = true;
                sendChat(event.message);
                ignore = false;
                ci.cancel();
            }
        }
    }

    // 26.2: was onCloseScreen(CloseScreenS2CPacket)
    @Inject(method = "handleContainerClose", at = @At("HEAD"), cancellable = true)
    public void onCloseScreen(ClientboundContainerClosePacket packet, CallbackInfo ci) {
        S2CCloseScreenEvent event = S2CCloseScreenEvent.get();
        Alien.EVENT_BUS.post(event);
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    // 26.2: was @Redirect(method = "onEntityVelocityUpdate", target = Entity#setVelocityClient(DDD))
    // 现在实体速度由 Entity#lerpMotion(Vec3) 设置
    // require = 1：已核实 ClientPacketListener#handleSetEntityMotion(623) 内 lerpMotion 只有一处调用。
    // 不能用 require = 0 —— 那样一旦目标改名，注入会静默失败，Velocity 模块会「开了没反应」且不报错。
    @Redirect(method = {"handleSetEntityMotion"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;lerpMotion(Lnet/minecraft/world/phys/Vec3;)V"), require = 1)
    private void velocityHook(Entity instance, Vec3 movement) {
        EntityVelocityUpdateEvent event = EntityVelocityUpdateEvent.get(instance, movement.x, movement.y, movement.z, false);
        Alien.EVENT_BUS.post(event);
        if (!event.isCancelled()) {
            instance.lerpMotion(new Vec3(event.getX(), event.getY(), event.getZ()));
        }
    }

    // 26.2: was @Redirect(method = "onExplosion", target = Vec3#add(DDD))
    // 26.2 的爆炸击退是 packet.playerKnockback().ifPresent(player::addDeltaMovement)，没有直接可改的
    // Vec3#add 调用点，因此改为在方法末尾（TAIL）把已经加上去的击退减掉、再加上事件修正后的值。
    @Inject(method = "handleExplosion", at = @At("TAIL"))
    private void velocityHook2(ClientboundExplodePacket packet, CallbackInfo ci) {
        Player player = this.minecraft.player;
        if (player == null) return;
        packet.playerKnockback().ifPresent(knockback -> {
            EntityVelocityUpdateEvent event = EntityVelocityUpdateEvent.get(player, knockback.x, knockback.y, knockback.z, true);
            Alien.EVENT_BUS.post(event);
            if (event.isCancelled()) {
                player.addDeltaMovement(knockback.scale(-1.0));
            } else if (event.getX() != knockback.x || event.getY() != knockback.y || event.getZ() != knockback.z) {
                player.addDeltaMovement(knockback.scale(-1.0));
                player.addDeltaMovement(new Vec3(event.getX(), event.getY(), event.getZ()));
            }
        });
    }

    // 26.2: was onPlayerPositionLook(PlayerPositionLookS2CPacket)
    @Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
    public void onPlayerPositionLook(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        boolean noRotate = AntiPacket.INSTANCE.isOn() && AntiPacket.INSTANCE.s2CRotate.getValue() && Alien.SERVER.playerNull.passedS(0.25);
        if (noRotate) {
            ci.cancel();
            PacketUtils.ensureRunningOnSameThread(packet, (ClientPacketListener) (Object) this, this.minecraft.packetProcessor());
            Player playerEntity = this.minecraft.player;
            if (playerEntity == null) return;
            Vec3 vec3d = playerEntity.getDeltaMovement();
            Set<Relative> flags = packet.relatives(); // 26.2: was packet.getFlags()/PositionFlag
            Vec3 target = packet.change().position(); // 26.2: was packet.getX()/getY()/getZ()
            boolean bl = flags.contains(Relative.X);
            boolean bl2 = flags.contains(Relative.Y);
            boolean bl3 = flags.contains(Relative.Z);
            double d;
            double e;
            if (bl) {
                d = vec3d.x;
                e = playerEntity.getX() + target.x;
                playerEntity.xo += target.x; // 26.2: was lastRenderX
                playerEntity.xOld += target.x; // 26.2: was prevX
            } else {
                d = 0.0;
                e = target.x;
                playerEntity.xo = e;
                playerEntity.xOld = e;
            }

            double f;
            double g;
            if (bl2) {
                f = vec3d.y;
                g = playerEntity.getY() + target.y;
                playerEntity.yo += target.y;
                playerEntity.yOld += target.y;
            } else {
                f = 0.0;
                g = target.y;
                playerEntity.yo = g;
                playerEntity.yOld = g;
            }

            double h;
            double i;
            if (bl3) {
                h = vec3d.z;
                i = playerEntity.getZ() + target.z;
                playerEntity.zo += target.z;
                playerEntity.zOld += target.z;
            } else {
                h = 0.0;
                i = target.z;
                playerEntity.zo = i;
                playerEntity.zOld = i;
            }

            playerEntity.setPos(e, g, i); // 26.2: was setPosition
            playerEntity.setDeltaMovement(d, f, h);

            if (AntiPacket.INSTANCE.applyYaw.getValue()) {
                float yaw = packet.change().yRot();
                float pitch = packet.change().xRot();

                if (flags.contains(Relative.X_ROT)) {
                    pitch += Alien.ROTATION.getLastPitch();
                }

                if (flags.contains(Relative.Y_ROT)) {
                    yaw += Alien.ROTATION.getLastYaw();
                }

                // 26.2: TeleportConfirmC2SPacket -> ServerboundAcceptTeleportationPacket
                this.connection.send(new ServerboundAcceptTeleportationPacket(packet.id()));
                this.connection
                        .send(new ServerboundMovePlayerPacket.PosRot(playerEntity.position(), yaw, pitch, false, false));
            } else {
                this.connection.send(new ServerboundAcceptTeleportationPacket(packet.id()));
                this.connection
                        .send(new ServerboundMovePlayerPacket.PosRot(playerEntity.position(), Alien.ROTATION.getLastYaw(), Alien.ROTATION.getLastPitch(), false, false));
            }
            Alien.EVENT_BUS.post(ServerChangePositionEvent.INSTANCE);
        }
    }
}
