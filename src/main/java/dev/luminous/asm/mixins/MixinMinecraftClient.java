package dev.luminous.asm.mixins;

import dev.luminous.Alien;
import dev.luminous.api.events.Event;
import dev.luminous.api.events.impl.*;
import dev.luminous.core.impl.CommandManager;
import dev.luminous.core.impl.FontManager;
import dev.luminous.mod.modules.impl.client.ClientSetting;
import dev.luminous.mod.modules.impl.player.InteractTweaks;
import net.minecraft.SharedConstants;
import net.minecraft.client.FramerateLimiter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.main.GameConfig; // 26.2: was net.minecraft.client.RunArgs
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.resources.language.I18n;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MixinMinecraftClient {
    @Shadow
    @Final
    public Gui gui; // 26.2: was inGameHud
    @Shadow
    public int missTime; // 26.2: was attackCooldown
    @Shadow
    public LocalPlayer player;
    @Shadow
    public HitResult hitResult; // 26.2: was crosshairTarget
    @Shadow
    public MultiPlayerGameMode gameMode; // 26.2: was interactionManager
    @Final
    @Shadow
    public ParticleEngine particleEngine; // 26.2: was particleManager
    @Shadow
    public ClientLevel level; // 26.2: was world
    @Shadow
    private IntegratedServer singleplayerServer; // 26.2: was server

    @Shadow
    @Final
    private Window window;

    // 26.2: was onResolutionChanged
    @Inject(method = "resizeGui", at = @At("TAIL"))
    private void captureResize(CallbackInfo ci) {
        Alien.EVENT_BUS.post(new ResizeEvent(this.window));
    }

    // 26.2: was method "render" + RenderSystem.limitDisplayFPS；现为 renderFrame + FramerateLimiter
    @Redirect(method = "renderFrame", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/FramerateLimiter;limitDisplayFPS(I)V"), require = 0)
    public void fpsHook(int fps) {
        if (!ClientSetting.INSTANCE.fuckFPSLimit.getValue()) {
            FramerateLimiter.limitDisplayFPS(fps);
        }
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    void postWindowInit(GameConfig args, CallbackInfo ci) {
        FontManager.init();
    }

    /*
     * 26.2: 原 @Inject(method = "setScreen", HEAD, cancellable) 已迁移 —— Minecraft 不再持有 setScreen，
     * 屏幕统一由 Gui#setScreen 管理。OpenScreenEvent 现在在 MixinScreen 里挂到 Screen#added（每次打开屏幕调用一次）。
     */

    // 26.2: doAttack -> startAttack；HitResult.Type 的包路径改为 world.phys
    @Inject(method = {"startAttack"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/HitResult;getType()Lnet/minecraft/world/phys/HitResult$Type;", shift = At.Shift.BEFORE))
    public void onAttack(CallbackInfoReturnable<Boolean> cir) {
        Alien.EVENT_BUS.post(DoAttackEvent.getPre());
    }

    // 26.2: swingHand -> swing；由于 startAttack 内有 5 处 swing 调用（穿刺/普通/副手等分支），
    // 逐个 INVOKE 注入会重复触发，这里改为在方法末尾（TAIL）触发一次，语义 = “攻击流程结束”。
    @Inject(method = {"startAttack"}, at = @At("TAIL"))
    public void onAttackPost(CallbackInfoReturnable<Boolean> cir) {
        Alien.EVENT_BUS.post(DoAttackEvent.getPost());
    }

    // 26.2: disconnect(Screen) -> disconnect(Screen, boolean, boolean)（两个重载最终都走这个）
    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("HEAD"))
    private void onDisconnect(Screen screen, boolean keepResourcePacks, boolean stopSound, CallbackInfo info) {
        if (level != null) {
            Alien.EVENT_BUS.post(GameLeftEvent.INSTANCE);
        }
    }

    @Inject(method = "disconnect(Lnet/minecraft/client/gui/screens/Screen;ZZ)V", at = @At("HEAD"))
    private void clearTitleMixin(Screen screen, boolean keepResourcePacks, boolean stopSound, CallbackInfo info) {
        if (ClientSetting.INSTANCE.titleFix.getValue()) {
            // 26.2: clearTitle -> Hud#clearTitles，setDefaultTitleFade -> Hud#resetTitleTimes
            gui.hud.clearTitles();
            gui.hud.resetTitleTimes();
        }
    }

    // 26.2: handleBlockBreaking -> continueAttack
    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void handleBlockBreaking(boolean breaking, CallbackInfo ci) {
        if (this.missTime <= 0 && this.player.isUsingItem() && InteractTweaks.INSTANCE.multiTask()) {
            if (breaking && this.hitResult != null && this.hitResult.getType() == HitResult.Type.BLOCK) {
                BlockHitResult blockHitResult = (BlockHitResult) this.hitResult;
                BlockPos blockPos = blockHitResult.getBlockPos();
                if (!this.level.getBlockState(blockPos).isAir()) {
                    Direction direction = blockHitResult.getDirection();
                    // 26.2: updateBlockBreakingProgress -> continueDestroyBlock
                    if (this.gameMode.continueDestroyBlock(blockPos, direction)) {
                        // 26.2: particleManager.addBlockBreakingParticles -> level.addBreakingBlockEffect
                        this.level.addBreakingBlockEffect(blockPos, direction);
                        // 26.2: swingHand -> swing
                        this.player.swing(InteractionHand.MAIN_HAND);
                    }
                }
            } else {
                // 26.2: cancelBlockBreaking -> stopDestroyBlock
                this.gameMode.stopDestroyBlock();
            }
            ci.cancel();
        }
    }

    @Inject(at = @At("HEAD"), method = "tick()V")
    public void tickHead(CallbackInfo info) {
        try {
            Alien.EVENT_BUS.post(ClientTickEvent.get(Event.Stage.Pre));
        } catch (Exception e) {
            e.printStackTrace();
            if (ClientSetting.INSTANCE.debug.getValue())
                CommandManager.sendMessage("§4An error has occurred (Minecraft.tick() [HEAD]) Message: [" + e.getMessage() + "]");
        }
    }

    @Inject(at = @At("TAIL"), method = "tick()V")
    public void tickTail(CallbackInfo info) {
        try {
            Alien.EVENT_BUS.post(ClientTickEvent.get(Event.Stage.Post));
        } catch (Exception e) {
            e.printStackTrace();
            if (ClientSetting.INSTANCE.debug.getValue())
                CommandManager.sendMessage("§4An error has occurred (Minecraft.tick() [TAIL]) Message: [" + e.getMessage() + "]");
        }
    }

    /**
     * @author me
     * @reason title
     */
    // 26.2: was getWindowTitle()
    @Overwrite
    private String createTitle() {
        if (ClientSetting.INSTANCE == null) {
            return Alien.NAME + ": Loading..";
        }
        if (ClientSetting.INSTANCE.titleOverride.getValue()) {
            return ClientSetting.INSTANCE.windowTitle.getValue();
        }
        StringBuilder stringBuilder = new StringBuilder(ClientSetting.INSTANCE.windowTitle.getValue());

        stringBuilder.append(" ");
        // 26.2: getGameVersion().getName() -> getCurrentVersion().name()
        stringBuilder.append(SharedConstants.getCurrentVersion().name());

        ClientPacketListener clientPlayNetworkHandler = this.getConnection(); // 26.2: was getNetworkHandler
        if (clientPlayNetworkHandler != null && clientPlayNetworkHandler.getConnection().isConnected()) {
            stringBuilder.append(" - ");
            ServerData serverInfo = this.getCurrentServer(); // 26.2: was getCurrentServerEntry
            if (this.singleplayerServer != null && !this.singleplayerServer.isPublished()) {
                stringBuilder.append(I18n.get("title.singleplayer")); // 26.2: was I18n.translate
            } else if (serverInfo != null && serverInfo.isRealm()) {
                stringBuilder.append(I18n.get("title.multiplayer.realms"));
            } else if (this.singleplayerServer == null && (serverInfo == null || !serverInfo.isLan())) {
                stringBuilder.append(I18n.get("title.multiplayer.other"));
            } else {
                stringBuilder.append(I18n.get("title.multiplayer.lan"));
            }
        }

        return stringBuilder.toString();
    }

    @Shadow
    public ClientPacketListener getConnection() {
        return null;
    }

    @Shadow
    public ServerData getCurrentServer() {
        return null;
    }
}
