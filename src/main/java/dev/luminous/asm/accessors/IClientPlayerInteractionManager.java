package dev.luminous.asm.accessors;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 26.2: 字段 {@code lastSelectedSlot} 改名为 {@code carriedIndex}（private int，非 final）。
 * 方法名保持不变，避免调用方改动。
 */
@Mixin(MultiPlayerGameMode.class)
public interface IClientPlayerInteractionManager {

    @Accessor("carriedIndex")
    int getLastSelectedSlot();

    @Accessor("carriedIndex")
    void setLastSelectedSlot(int slot);
}
