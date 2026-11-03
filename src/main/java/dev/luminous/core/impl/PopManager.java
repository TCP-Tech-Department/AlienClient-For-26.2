package dev.luminous.core.impl;

import by.radioegor146.nativeobfuscator.Native;
import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.DeathEvent;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.api.events.impl.TotemEvent;
import dev.luminous.api.utils.Wrapper;
import dev.luminous.mod.modules.Module;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class PopManager implements Wrapper {
    public final HashMap<String, Integer> popContainer = new HashMap<>();
    private final List<Player> deadPlayer = new ArrayList<>();

    public PopManager() {
        init();
    }

    @Native
    public void init() {
        Alien.EVENT_BUS.subscribe(this);
    }

    public int getPop(String s) {
        return popContainer.getOrDefault(s, 0);
    }

    public int getPop(Player player) {
        return getPop(player.getName().getString());
    }

    public void onUpdate() {
        if (Module.nullCheck()) return;
        for (AbstractClientPlayer player : Alien.THREAD.getPlayers()) {
            if (player == null || !player.isDeadOrDying()) {
                deadPlayer.remove(player);
                continue;
            }
            if (deadPlayer.contains(player)) {
                continue;
            }
            Alien.EVENT_BUS.post(DeathEvent.get(player));
            onDeath(player);
            deadPlayer.add(player);
        }
    }

    @EventListener
    public void onPacketReceive(PacketEvent.Receive event) {
        if (Module.nullCheck()) return;
        if (event.getPacket() instanceof ClientboundEntityEventPacket packet) {
            if (packet.getEventId() == EntityEvent.PROTECTED_FROM_DEATH) {
                Entity entity = packet.getEntity(mc.level);
                if (entity instanceof Player player) {
                    onTotemPop(player);
                }
            }
        }
    }

    public void onDeath(Player player) {
        popContainer.remove(player.getName().getString());
    }

    public void onTotemPop(Player player) {
        int l_Count = 1;
        if (popContainer.containsKey(player.getName().getString())) {
            l_Count = popContainer.get(player.getName().getString());
            popContainer.put(player.getName().getString(), ++l_Count);
        } else {
            popContainer.put(player.getName().getString(), l_Count);
        }
        Alien.EVENT_BUS.post(TotemEvent.get(player));
    }
}
