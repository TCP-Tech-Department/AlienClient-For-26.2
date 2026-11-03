package dev.luminous.asm.mixins;

import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalIntRef;
import dev.luminous.mod.modules.impl.misc.ExtraTab;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Comparator;
import java.util.List;

import static dev.luminous.api.utils.Wrapper.mc;

@Mixin(PlayerTabOverlay.class) // 26.2: was PlayerListHud
public abstract class MixinPlayerListHud {
    @Final
    @Shadow
    private static Comparator<PlayerInfo> PLAYER_COMPARATOR; // 26.2: was ENTRY_ORDERING

    // 26.2: was collectPlayerEntries()
    @Shadow
    private List<PlayerInfo> getPlayerInfos() {
        return null;
    }

    @Inject(method = "getPlayerInfos", at = @At("HEAD"), cancellable = true)
    private void collectPlayerEntriesHook(CallbackInfoReturnable<List<PlayerInfo>> cir) {
        if (ExtraTab.INSTANCE.isOn()) {
            // 26.2: networkHandler -> connection；getListedPlayerListEntries -> getListedOnlinePlayers
            cir.setReturnValue(mc.player.connection.getListedOnlinePlayers()
                    .stream().sorted(PLAYER_COMPARATOR).limit(ExtraTab.INSTANCE.size.getValueInt()).toList());
        }
    }

    // 26.2: was method = "render"
    @Inject(method = "extractRenderState", at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I", ordinal = 0, shift = At.Shift.BEFORE))
    private void hookRender(CallbackInfo ci, @Local(ordinal = 5) LocalIntRef o, @Local(ordinal = 6) LocalIntRef p) {
        int totalPlayers = this.getPlayerInfos().size();
        int newP = 1;

        int newO = (totalPlayers + newP - 1) / newP;

        while (newO > ExtraTab.INSTANCE.columns.getValueInt()) {
            newP++;
            newO = (totalPlayers + newP - 1) / newP;
        }

        o.set(newO);
        p.set(newP);
    }
}
