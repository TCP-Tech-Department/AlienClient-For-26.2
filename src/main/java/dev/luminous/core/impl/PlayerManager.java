package dev.luminous.core.impl;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.BlockActivateEvent;
import dev.luminous.api.events.impl.GameLeftEvent;
import dev.luminous.api.events.impl.OpenScreenEvent;
import dev.luminous.api.utils.Wrapper;
import dev.luminous.api.utils.world.BlockPosX;
import dev.luminous.mod.modules.Module;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EnderChestBlock;
import net.minecraft.world.phys.AABB;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class PlayerManager implements Wrapper {

    public static Screen screenToOpen;
    public final NonNullList<ItemStack> ENDERCHEST_ITEM = NonNullList.withSize(27, ItemStack.EMPTY);
    public final Map<Player, EntityAttribute> map = new ConcurrentHashMap<>();
    public final CopyOnWriteArrayList<Player> inWebPlayers = new CopyOnWriteArrayList<>();
    public boolean known = false;
    private int echestOpenedState;

    public PlayerManager() {
        Alien.EVENT_BUS.subscribe(this);
    }

    @EventListener
    public void onLogout(GameLeftEvent event) {
        inWebPlayers.clear();
        map.clear();
        ENDERCHEST_ITEM.clear();
        known = false;
    }

    @EventListener
    private void onBlockActivate(BlockActivateEvent event) {
        if (event.blockState.getBlock() instanceof EnderChestBlock && echestOpenedState == 0) echestOpenedState = 1;
    }

    @EventListener
    private void onOpenScreenEvent(OpenScreenEvent event) {
        if (echestOpenedState == 1 && event.screen instanceof ContainerScreen) {
            echestOpenedState = 2;
            return;
        }
        if (echestOpenedState == 0) return;

        if (!(mc.gui.screen() instanceof ContainerScreen containerScreen)) return;
        AbstractContainerMenu container = containerScreen.getMenu();
        if (container == null) return;

        for (int i = 0; i < 27; i++) {
            ENDERCHEST_ITEM.set(i, container.getSlot(i).getItem());
        }
        known = true;

        echestOpenedState = 0;
    }

    public void onUpdate() {
        if (Module.nullCheck()) return;
        if (screenToOpen != null && mc.gui.screen() == null) {
            mc.setScreenAndShow(screenToOpen);
            screenToOpen = null;
        }
        inWebPlayers.clear();
        for (Player player : Alien.THREAD.getPlayers()) {
            map.put(player, new EntityAttribute(player.getArmorValue(), player.getAttributeValue(Attributes.ARMOR_TOUGHNESS)));
            webUpdate(player);
        }
    }

    public boolean isInWeb(Player player) {
        return inWebPlayers.contains(player);
    }

    private void webUpdate(Player player) {
        for (float x : new float[]{0, 0.3F, -0.3f}) {
            for (float z : new float[]{0, 0.3F, -0.3f}) {
                for (int y : new int[]{-1, 0, 1, 2}) {
                    BlockPos pos = new BlockPosX(player.getX() + x, player.getY(), player.getZ() + z).above(y);
                    if (new AABB(pos).intersects(player.getBoundingBox()) && mc.level.getBlockState(pos).getBlock() == Blocks.COBWEB) {
                        inWebPlayers.add(player);
                        return;
                    }
                }
            }
        }
    }

    public record EntityAttribute(int armor, double toughness) {
    }
}
