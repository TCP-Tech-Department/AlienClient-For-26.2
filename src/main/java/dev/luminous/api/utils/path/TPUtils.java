package dev.luminous.api.utils.path;

import com.google.common.collect.Lists;
import dev.luminous.api.utils.Wrapper;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class TPUtils implements Wrapper {
    public static void teleportWithBack(Vec3 newPos, TeleportType type, Runnable runnable) {
        switch (type) {
            case Legacy -> legacyTeleportWithBack(newPos, runnable);
            case New -> newTeleportWithBack(newPos, runnable);
        }
    }

    public static void legacyTeleportWithBack(Vec3 newPos, Runnable runnable) {
        // 注意：PathUtils 用的是项目自定义 Vec3（dev.luminous.api.utils.path.Vec3，record 风格 x()/y()/z()），
        // 与 Minecraft 的 net.minecraft.world.phys.Vec3 不是同一个类，这里显式全限定，避免 import 冲突。
        List<dev.luminous.api.utils.path.Vec3> tpPath = PathUtils.computePath(newPos);
        tpPath.removeFirst();
        tpPath.forEach(vec3 -> mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(vec3.x(), vec3.y(), vec3.z(), false, false)));

        runnable.run();

        tpPath = Lists.reverse(tpPath);
        tpPath.removeFirst();
        tpPath.forEach(vec3 -> mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(vec3.x(), vec3.y(), vec3.z(), false, false)));
        mc.player.connection
                .send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(),
                        mc.player.getY(), mc.player.getZ(), false, false));
    }

    public static void newTeleportWithBack(Vec3 newPos, Runnable runnable) {
        int packetsRequired = (int) Math.ceil(mc.player.position().distanceTo(newPos) / 10) - 1;

        for (int i = 0; i < packetsRequired; i++) {
            mc.player.connection.send(new ServerboundMovePlayerPacket.StatusOnly(true, false));
        }
        mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(newPos.x, newPos.y, newPos.z, true, false));

        runnable.run();

        for (int i = 0; i < packetsRequired; i++) {
            mc.player.connection.send(new ServerboundMovePlayerPacket.StatusOnly(true, false));
        }
        mc.player.connection
                .send(new ServerboundMovePlayerPacket.Pos(mc.player.getX(),
                        mc.player.getY(), mc.player.getZ(), false, false));
    }

    public static void newTeleport(Vec3 newPos) {
        int packetsRequired = (int) Math.ceil(mc.player.position().distanceTo(newPos) / 10) - 1;

        for (int i = 0; i < packetsRequired; i++) {
            mc.player.connection.send(new ServerboundMovePlayerPacket.StatusOnly(true, false));
        }
        mc.player.connection.send(new ServerboundMovePlayerPacket.Pos(newPos.x, newPos.y, newPos.z, true, false));
    }

    public enum TeleportType {
        Legacy,
        New
    }
}
