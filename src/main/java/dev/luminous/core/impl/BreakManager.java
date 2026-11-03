package dev.luminous.core.impl;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.BlockBreakingProgressEvent;
import dev.luminous.api.events.impl.ClientTickEvent;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.api.events.impl.ServerConnectBeginEvent;
import dev.luminous.api.utils.Wrapper;
import dev.luminous.api.utils.math.FadeUtils;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.client.AntiCheat;
import dev.luminous.mod.modules.impl.player.PacketMine;
import dev.luminous.mod.modules.impl.render.BreakESP;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.ConcurrentHashMap;

public class BreakManager implements Wrapper {
    public final ConcurrentHashMap<Integer, BreakData> breakMap = new ConcurrentHashMap<>();
    public final ConcurrentHashMap<Integer, BreakData> doubleMap = new ConcurrentHashMap<>();

    public BreakManager() {
        Alien.EVENT_BUS.subscribe(this);
    }

    @EventListener
    public void onServerConnectBegin(ServerConnectBeginEvent event) {
        breakMap.clear();
        doubleMap.clear();
    }

    @EventListener
    public void onTick(ClientTickEvent event) {
        if (Module.nullCheck()) return;
        if (AntiCheat.INSTANCE.detectDouble.getValue()) {
            for (int i : Alien.BREAK.doubleMap.keySet()) {
                BreakManager.BreakData breakData = Alien.BREAK.doubleMap.get(i);
                if (breakData == null || breakData.getEntity() == null || mc.level.getBlockState(breakData.pos).isAir() || breakData.timer.passedMs(Math.max(AntiCheat.INSTANCE.minTimeout.getValue() * 1000, breakData.breakTime * AntiCheat.INSTANCE.doubleMineTimeout.getValue()))) {
                    Alien.BREAK.doubleMap.remove(i);
                }
            }
        }
        for (BreakData breakData : breakMap.values()) {
            breakData.breakTime = Math.max(BreakESP.getBreakTime(breakData.pos, false), 50);
            if (PacketMine.unbreakable(breakData.pos)) {
                breakData.fade.setLength(0);
                breakData.complete = false;
                breakData.failed = true;
            } else if (mc.level.getBlockState(breakData.pos).isAir()) {
                breakData.fade.setLength(0);
                breakData.complete = true;
                breakData.failed = false;
            } else if (!breakData.complete && breakData.timer.passedMs(breakData.breakTime * AntiCheat.INSTANCE.breakTimeout.getValue())) {
                breakData.fade.setLength(0);
                breakData.failed = true;
            } else {
                breakData.fade.setLength((long) breakData.breakTime);
            }
        }
    }

    @EventListener
    public void onPacket(PacketEvent.Receive event) {
        if (Module.nullCheck()) return;
        if (event.getPacket() instanceof ClientboundBlockDestructionPacket packet) {
            if (packet.getPos() == null) return;
            BreakData breakData = new BreakData(packet.getPos(), packet.getId(), false);
            if (breakData.getEntity() == null) {
                return;
            }
            if (Mth.sqrt((float) breakData.getEntity().getEyePosition().distanceToSqr(Vec3.atCenterOf(packet.getPos()))) > 8) {
                return;
            }
            if (AntiCheat.INSTANCE.detectDouble.getValue()) {
                if (packet.getProgress() != 255) {
                    if (packet.getProgress() != 0) {
                        BreakData doublePos = doubleMap.get(packet.getId());
                        if (doublePos != null) {
                            doublePos.pos = packet.getPos();
                            doublePos.timer.reset();
                        } else {
                            if (!PacketMine.unbreakable(packet.getPos()))
                                doubleMap.put(packet.getId(), new BreakData(packet.getPos(), packet.getId(), true));
                        }
                        return;
                    }
                    BreakData doublePos = doubleMap.get(packet.getId());
                    if (doublePos != null) {
                        if (doublePos.pos.equals(packet.getPos())) {
                            if (!doublePos.timer.passedS(150)) {
                                return;
                            }
                        }
                    }
                }
            }
            BreakData current = breakMap.get(packet.getId());
            if (current != null && !current.failed && current.pos.equals(packet.getPos())) {
                return;
            }
            breakMap.put(packet.getId(), breakData);
            Alien.EVENT_BUS.post(BlockBreakingProgressEvent.get(packet.getPos(), packet.getId(), packet.getProgress()));
            if (AntiCheat.INSTANCE.detectDouble.getValue()) {
                if (!doubleMap.containsKey(packet.getId()) && !PacketMine.unbreakable(packet.getPos())) {
                    doubleMap.put(packet.getId(), new BreakData(packet.getPos(), packet.getId(), true));
                }
            }
        }
    }

    public boolean isMining(BlockPos pos) {
        return isMining(pos, true);
    }

    public boolean isMining(BlockPos pos, boolean self) {
        if (self && PacketMine.getBreakPos() != null && PacketMine.getBreakPos().equals(pos)) {
            return true;
        }

        for (BreakData breakData : breakMap.values()) {
            if (breakData.getEntity() == null) {
                continue;
            }
            if (breakData.getEntity().getEyePosition().distanceTo(Vec3.atCenterOf(pos)) > 7) {
                continue;
            }
            if (breakData.failed) {
                continue;
            }
            if (breakData.pos.equals(pos)) {
                return true;
            }
        }

        return false;
    }

    public static class BreakData {
        public BlockPos pos;
        private final int entityId;
        public final FadeUtils fade;
        public final Timer timer;
        public double breakTime;
        public boolean failed = false;
        public boolean complete = false;

        public BreakData(BlockPos pos, int entityId, boolean extraBreak) {
            this.pos = pos;
            this.entityId = entityId;
            this.breakTime = Math.max(BreakESP.getBreakTime(pos, extraBreak), 50);
            this.fade = new FadeUtils((long) this.breakTime);
            this.timer = new Timer();
        }

        public Entity getEntity() {
            if (mc.level == null) return null;
            Entity entity = mc.level.getEntity(entityId);
            if (entity instanceof Player) {
                return entity;
            }
            return null;
        }
    }
}
