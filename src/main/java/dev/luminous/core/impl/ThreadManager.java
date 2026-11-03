package dev.luminous.core.impl;

import by.radioegor146.nativeobfuscator.Native;
import com.google.common.collect.Lists;
import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.eventbus.EventPriority;
import dev.luminous.api.events.impl.ClientTickEvent;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.Wrapper;
import dev.luminous.api.utils.render.JelloUtil;
import dev.luminous.api.utils.world.BlockUtil;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.client.ClientSetting;
import dev.luminous.mod.modules.impl.combat.AutoAnchor;
import dev.luminous.mod.modules.impl.combat.AutoCrystal;
import dev.luminous.mod.modules.impl.render.HoleESP;
import dev.luminous.mod.modules.impl.render.PlaceRender;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ThreadManager implements Wrapper {
    public static final ExecutorService EXECUTOR = Executors.newCachedThreadPool();
    public static ClientService clientService;
    public volatile Iterable<Entity> threadSafeEntityList = Collections.emptyList();
    public volatile List<AbstractClientPlayer> threadSafePlayersList = Collections.emptyList();
    public volatile boolean tickRunning = false;

    public ThreadManager() {
        init();
    }

    @Native
    public void init() {
        Alien.EVENT_BUS.subscribe(this);
        clientService = new ClientService();
        clientService.setName("AlienClientService");
        clientService.setDaemon(true);
        clientService.start();
    }

    public Iterable<Entity> getEntities() {
        return threadSafeEntityList;
    }

    public List<AbstractClientPlayer> getPlayers() {
        return threadSafePlayersList;
    }

    public void execute(Runnable runnable) {
        EXECUTOR.execute(runnable);
    }

    @EventListener(priority = EventPriority.HIGHEST)
    public void onEvent(ClientTickEvent event) {
        Alien.POP.onUpdate();
        Alien.SERVER.onUpdate();
        if (event.isPre()) {
            JelloUtil.updateJello();
            tickRunning = true;
            BlockUtil.placedPos.forEach(pos -> PlaceRender.INSTANCE.create(pos));
            BlockUtil.placedPos.clear();
            Alien.PLAYER.onUpdate();
            if (!Module.nullCheck())
                Alien.EVENT_BUS.post(UpdateEvent.INSTANCE);
        } else {
            tickRunning = false;
            if (mc.level == null || mc.player == null) return;
            threadSafeEntityList = Lists.newArrayList(mc.level.entitiesForRendering());
            threadSafePlayersList = Lists.newArrayList(mc.level.players());
        }

        if (!clientService.isAlive() || clientService.isInterrupted()) {
            clientService = new ClientService();
            clientService.setName("AlienService");
            clientService.setDaemon(true);
            clientService.start();
        }
    }

    public class ClientService extends Thread {
        @SuppressWarnings("InfiniteLoopStatement")
        @Override
        public void run() {
            while (true) {
                try {
                    while (tickRunning) {
                        Thread.onSpinWait();
                    }
                    AutoCrystal.INSTANCE.onThread();
                    HoleESP.INSTANCE.onThread();
                    AutoAnchor.INSTANCE.onThread();
                } catch (Exception e) {
                    e.printStackTrace();
                    if (ClientSetting.INSTANCE.debug.getValue())
                        CommandManager.sendMessage("§4An error has occurred [Thread] Message: [" + e.getMessage() + "]");
                }
            }
        }
    }
}