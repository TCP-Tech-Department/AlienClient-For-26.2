package dev.luminous.asm.mixins;

import dev.luminous.Alien;
import dev.luminous.api.events.impl.PreRender2DEvent;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.render.ColorUtil;
import dev.luminous.api.utils.render.TextUtil;
import dev.luminous.core.impl.FontManager;
import dev.luminous.mod.modules.impl.client.ClientSetting;
import dev.luminous.mod.modules.impl.client.HUD;
import dev.luminous.mod.modules.impl.render.Crosshair;
import dev.luminous.mod.modules.impl.render.NoRender;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.FluidTags;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.*;

/*
 * 26.2: InGameHud 拆成 {@link net.minecraft.client.gui.Gui}（屏幕/覆盖层管理）与
 * {@link net.minecraft.client.gui.Hud}（HUD 本体，所有 render* 方法改名 extract*）。
 * 本 Mixin 目标改为 Hud。
 */
@Mixin(Hud.class)
public abstract class MixinInGameHud {

    @Final
    @Shadow
    private Minecraft minecraft; // 26.2: was client

    // 26.2: was renderPortalOverlay(GuiGraphicsExtractor, float)
    @Inject(method = "extractPortalOverlay", at = @At("HEAD"), cancellable = true)
    private void onRenderPortalOverlay(GuiGraphicsExtractor graphics, float nauseaStrength, CallbackInfo ci) {
        if (NoRender.INSTANCE.isOn() && NoRender.INSTANCE.portal.getValue()) ci.cancel();
    }

    // 26.2: was renderStatusEffectOverlay
    @Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true)
    private void onRenderStatusEffectOverlay(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo info) {
        if (NoRender.INSTANCE.isOn() && NoRender.INSTANCE.potionsIcon.getValue()) info.cancel();
    }

    @Unique
    final
    Color minColor = new Color(196, 0, 0);
    @Unique
    final
    Color maxColor = new Color(0, 227, 0);

    // 26.2: was renderMainHud(GuiGraphicsExtractor, RenderTickCounter)
    @Inject(method = "extractPlayerHealth", at = @At("TAIL"))
    private void onRenderMainHud(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (HUD.INSTANCE.isOn() && HUD.INSTANCE.armor.getValue()) {
            Player player = this.getCameraPlayer();
            if (player != null) {
                int x = graphics.guiWidth() / 2 + 91;
                int y = graphics.guiHeight() - 28 - HUD.INSTANCE.armorOffset.getValueInt();

                // 26.2: interactionManager.hasStatusBars() 已移除，等价语义为“非创造/旁观”
                if (this.minecraft.player != null && !this.minecraft.player.isCreative() && !this.minecraft.player.isSpectator()) {
                    y -= 16;
                }
                int t = this.getVehicleMaxHearts(this.getPlayerVehicleWithHealth());
                if (t == 0) {
                    y -= 10;
                }

                int maxAir = player.getMaxAirSupply(); // 26.2: was getMaxAir
                int air = Math.min(player.getAirSupply(), maxAir); // 26.2: was getAir
                if (player.isEyeInFluid(FluidTags.WATER) || air < maxAir) { // 26.2: was isSubmergedIn
                    int w = this.getVisibleVehicleHeartRows(t) - 1;
                    y += w * 10;
                }

                // 26.2: Inventory#armor 移除，装备改由 EntityEquipment 持有
                for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD}) {
                    ItemStack armor = player.getItemBySlot(slot);
                    x -= 20;
                    if (armor.isEmpty()) continue;
                    graphics.pose().pushMatrix(); // 26.2: was pose().push()
                    int damage = EntityUtil.getDamagePercent(armor);
                    graphics.item(armor, x, y); // 26.2: was drawItem
                    graphics.itemDecorations(this.minecraft.font, armor, x, y); // 26.2: was drawItemInSlot

                    if (HUD.INSTANCE.durability.getValue()) {
                        if (HUD.INSTANCE.font.getValue()) {
                            FontManager.small.drawString(graphics, damage + "%",
                                    (x + 1),
                                    y - FontManager.small.getFontHeight() / 2,
                                    ColorUtil.fadeColor(minColor, maxColor, damage / 100f).getRGB(), HUD.INSTANCE.shadow.getValue());
                        } else {
                            TextUtil.drawStringScale(graphics, damage + "%",
                                    (x + 2),
                                    (y - this.minecraft.font.lineHeight / 4f), // 26.2: textRenderer.fontHeight -> font.lineHeight
                                    ColorUtil.fadeColor(minColor, maxColor, damage / 100f).getRGB(),
                                    0.5F,
                                    HUD.INSTANCE.shadow.getValue()
                            );
                        }
                    }
                    graphics.pose().popMatrix();
                }
            }
        }
    }

    // 26.2: was getHeartRows
    @Shadow
    private int getVisibleVehicleHeartRows(int hearts) {
        return 0;
    }

    // 26.2: was getHeartCount
    @Shadow
    private int getVehicleMaxHearts(LivingEntity vehicle) {
        return 0;
    }

    @Shadow
    private Player getCameraPlayer() {
        return null;
    }

    // 26.2: was getRiddenEntity
    @Shadow
    private LivingEntity getPlayerVehicleWithHealth() {
        return null;
    }

    // 26.2: was render(GuiGraphicsExtractor, RenderTickCounter)
    @Inject(at = @At(value = "HEAD"), method = "extractRenderState")
    public void renderStart(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        Alien.EVENT_BUS.post(PreRender2DEvent.get(graphics));
    }

    @Inject(at = @At(value = "TAIL"), method = "extractRenderState")
    public void renderHook(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        Alien.MODULE.onRender2D(graphics);
    }

    // 26.2: was Gui#clear() + ChatHud#clear(Z)；现在清屏逻辑在 Hud#onDisconnected（内含 chat.clearMessages(true)）
    @Inject(method = "onDisconnected", at = @At("HEAD"), cancellable = true)
    private void onClear(CallbackInfo info) {
        if (ClientSetting.INSTANCE.isOn() && ClientSetting.INSTANCE.keepHistory.getValue()) {
            info.cancel();
        }
    }

    // 26.2: was method "renderHotbar" + drawGuiTexture(Identifier, int, int, int, int)
    @ModifyArg(method = "extractItemHotbar", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V", ordinal = 1), index = 2)
    private int selectedSlotX(int x) {
        if (ClientSetting.INSTANCE.hotbar()) {
            double hotbarX = ClientSetting.animation.get(x, ClientSetting.INSTANCE.hotbarTime.getValueInt(), ClientSetting.INSTANCE.animEase.getValue());
            return (int) hotbarX;
        }
        return (x);
    }

    // 26.2: was renderCrosshair
    @Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
    private void onRenderCrosshairBegin(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (Crosshair.INSTANCE.isOn()) {
            Crosshair.INSTANCE.draw(graphics);
            ci.cancel();
        }
    }
}
