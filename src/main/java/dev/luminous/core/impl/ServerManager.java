package dev.luminous.core.impl;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.eventbus.EventPriority;
import dev.luminous.api.events.impl.GameLeftEvent;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.api.utils.Wrapper;
import dev.luminous.api.utils.math.MathUtil;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.mod.modules.impl.client.AntiCheat;
import dev.luminous.mod.modules.impl.client.Fonts;
import dev.luminous.mod.modules.impl.misc.AutoLog;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;

public class ServerManager implements Wrapper {

    public final Timer playerNull = new Timer();
    public int currentSlot = -1;

    private final ArrayDeque<Float> tpsResult = new ArrayDeque<>(20);

    boolean worldNull = true;

    private long time;
    private long tickTime;
    private float tps;

    public ServerManager() {
        Alien.EVENT_BUS.subscribe(this);
    }


    public static float round2(double value) {
        BigDecimal bd = new BigDecimal(value);
        bd = bd.setScale(2, RoundingMode.HALF_UP);
        return bd.floatValue();
    }


    int lastSlot;


    @EventListener(priority = EventPriority.LOWEST)
    public void onPacket(PacketEvent.Send event) {

        if (AntiCheat.INSTANCE.attackCDFix.getValue()) {

            if (event.isCancelled())
                return;

            Packet<?> packet = event.getPacket();

            // 26.2: PlayerInteractEntityC2SPacket.InteractType 已移除，攻击拆成独立的
            // ServerboundAttackPacket(entityId) record（见 26.2/.../protocol/game/ServerboundAttackPacket.java）。
            // 旧判定 `type == InteractType.ATTACK` 因此等价于 instanceof ServerboundAttackPacket。
            if (
                    packet instanceof ServerboundSwingPacket ||
                            packet instanceof ServerboundAttackPacket
            ) {

                mc.player.resetAttackStrengthTicker();

            } else if (packet instanceof ServerboundSetCarriedItemPacket packet2) {

                if (lastSlot != packet2.getSlot()) {

                    lastSlot = packet2.getSlot();
                    mc.player.resetAttackStrengthTicker();

                }
            }
        }
    }



    @EventListener
    public void onLeft(GameLeftEvent event) {
        currentSlot = -1;
    }



    @EventListener
    public void onPacketSend(PacketEvent.Send event) {

        if (event.getPacket() instanceof ServerboundSetCarriedItemPacket packet) {

            int packetSlot = packet.getSlot();

            if (AntiCheat.INSTANCE.noBadSlot.getValue()) {

                if (packetSlot == currentSlot) {

                    event.cancel();
                    return;

                }
            }

            currentSlot = packetSlot;
        }
    }



    public float getTPS() {
        return round2(tps);
    }


    public float getCurrentTPS() {
        return round2(20.0f * ((float) tickTime / 1000f));
    }


    public float getTPSFactor() {
        return getTPS() / 20f;
    }



    @EventListener(priority = 999)
    public void onPacketReceive(PacketEvent.Receive event) {

        if (event.getPacket() instanceof ClientboundSetTimePacket) {

            if (time != 0L) {

                tickTime = System.currentTimeMillis() - time;

                if (tpsResult.size() > 20)
                    tpsResult.poll();


                tpsResult.add(
                        20.0f * (1000.0f / (float)(tickTime))
                );


                float average = 0.0f;


                for (Float value : tpsResult)
                    average += MathUtil.clamp(value,0f,20f);


                tps = average / (float)tpsResult.size();

            }

            time = System.currentTimeMillis();
        }
    }



    /*
        已删除：

         坐标门

        RecDuJjyGWS2hnR 丢物品门

        AES坐标加密发送

        服务器聊天远程控制
    */


    @EventListener
    private void PacketReceive(PacketEvent.Receive event) {

        // 保留空监听，避免事件结构变化
        // 原远程控制代码已删除

    }



    public void onUpdate() {

        if (mc.player == null)
            playerNull.reset();


        if (worldNull && mc.level != null) {

            Fonts.INSTANCE.refresh();

            AutoLog.loggedOut = false;

            Alien.MODULE.onLogin();

            worldNull = false;


        } else if (!worldNull && mc.level == null) {


            Alien.save();

            Alien.MODULE.onLogout();

            worldNull = true;

        }
    }
}
