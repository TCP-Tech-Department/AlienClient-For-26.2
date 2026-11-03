package dev.luminous.mod.modules.impl.misc;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.eventbus.EventPriority;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.events.impl.UpdateRotateEvent;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.path.BaritoneUtil;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.api.utils.player.MovementUtil;
import dev.luminous.api.utils.world.BlockUtil;
import dev.luminous.asm.accessors.ILivingEntity;
import dev.luminous.core.impl.RotationManager;
import dev.luminous.mod.gui.windows.WindowsScreen;
import dev.luminous.mod.gui.windows.impl.ItemSelectWindow;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.impl.client.AntiCheat;
import dev.luminous.mod.modules.impl.combat.Aura;
import dev.luminous.mod.modules.impl.player.PacketMine;
import dev.luminous.mod.modules.impl.render.PlaceRender;
import dev.luminous.mod.modules.settings.enums.SwingSide;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.EnumSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import dev.luminous.mod.modules.settings.impl.StringSetting;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
// 26.2：client.gui.screen.ingame.HandledScreen → client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
// 26.2：recipebook.RecipeResultCollection → RecipeCollection；配方结果改为 RecipeDisplayEntry
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.entity.animal.turtle.Turtle;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.world.inventory.*;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.EntityHitResult;
import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
// 26.2：village.TradeOffer/TradeOfferList → world.item.trading.MerchantOffer/MerchantOffers
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.tags.ItemTags;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.inventory.ShulkerBoxMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShovelItem;

public class Bot extends Module {
    public static Bot INSTANCE;
    public final EnumSetting<Mode> mode = add(new EnumSetting<>("Mode", Mode.AutoTrade));
    private final BooleanSetting autoEat = add(new BooleanSetting("AutoEat", true).setParent());
    private final SliderSetting hunger =
            add(new SliderSetting("Hunger", 10, 0, 20, 1, autoEat::isOpen));
    private final SliderSetting health =
            add(new SliderSetting("Health", 20, 0, 36, .1, autoEat::isOpen));
    private final BooleanSetting anyFood = add(new BooleanSetting("AnyFood", false, autoEat::isOpen));
    private final BooleanSetting rotate = add(new BooleanSetting("Rotate", true));

    private final SliderSetting delay = add(new SliderSetting("Delay", 5, 0, 100, () -> mode.is(Mode.TridentDupe)));
    private final BooleanSetting dropTridents = add(new BooleanSetting("DropTridents", true, () -> mode.is(Mode.TridentDupe)));

    private final BooleanSetting durabilityManagement = add(new BooleanSetting("DurabilityManagement", true, () -> mode.is(Mode.TridentDupe)));

    public final BooleanSetting edit = add(new BooleanSetting("Edit", false, () -> mode.is(Mode.AutoTrade)).injectTask(this::openAutoTradeEdit));
    public final SliderSetting repeatSetting = add(new SliderSetting("Repeat", 2, 1, 15, 1, () -> mode.is(Mode.AutoTrade)));
    public final BooleanSetting autoCloseSetting = add(new BooleanSetting("AutoClose", true, () -> mode.is(Mode.AutoTrade)));
    public final BooleanSetting timeoutCloseSetting = add(new BooleanSetting("TimeoutClose", true, () -> mode.is(Mode.AutoTrade)));
    public final SliderSetting timeOutSetting = add(new SliderSetting("Timeout", 1, 0, 15, 0.1, () -> mode.is(Mode.AutoTrade)));
    public final BooleanSetting autoOpenSetting = add(new BooleanSetting("AutoOpen", true, () -> mode.is(Mode.AutoTrade)));

    private final SliderSetting range =
            add(new SliderSetting("Range", 4, 0, 8, .1, () -> mode.is(Mode.SlabPlacer) || mode.is(Mode.AutoTrade)));

    private final BooleanSetting inventory = add(new BooleanSetting("Inventory", true, () -> mode.is(Mode.NPlusOneDupe)));
    private final BooleanSetting ai = add(new BooleanSetting("AI", true, () -> mode.is(Mode.SandMiner)));
    private final BooleanSetting nuker = add(new BooleanSetting("Nuker", true, () -> mode.is(Mode.SandMiner)));
    private final BooleanSetting redSand = add(new BooleanSetting("RedSand", false, () -> mode.is(Mode.SandMiner)));

    private final SliderSetting breaks =
            add(new SliderSetting("Breaks", 10, 0, 20, () -> mode.is(Mode.SandMiner)));
    private final SliderSetting maxTime =
            add(new SliderSetting("MaxTime", 60, 0, 100, () -> mode.is(Mode.Ominous)));

    public final SliderSetting placeDelay =
            add(new SliderSetting("PlaceDelay", 50, 0, 500, () -> mode.is(Mode.SlabPlacer)));
    private final SliderSetting blocksPer =
            add(new SliderSetting("BlocksPer", 1, 1, 8, () -> mode.is(Mode.SlabPlacer)));

    private final BooleanSetting usingPause =
            add(new BooleanSetting("UsingPause", true, () -> mode.is(Mode.SlabPlacer)));

    private final StringSetting name = add(new StringSetting("Name", "KizuatoResu", () -> mode.is(Mode.ScoreFarmer)));
    private final BooleanSetting getScore = add(new BooleanSetting("GetScore", false, () -> mode.is(Mode.ScoreFarmer)));
    private final Timer commandTimer = new Timer();
    private final Timer duelItemTimer = new Timer();

    final List<BlockPos> emptyBox = new ArrayList<>();
    final List<Turtle> inLove = new ArrayList<>();
    final Timer timeOut = new Timer();
    final Timer closeScreen = new Timer();
    final Timer openTimeOut = new Timer();
    final Timer putTimer = new Timer();
    final Timer ominousTimer = new Timer();
    Stage stage = Stage.Open;
    BlockPos boxPos;
    boolean closeToBox;
    boolean putIn;
    Llama llama;
    boolean storageSand = false;
    boolean endEat = false;
    final Timer craftTimer = new Timer();
    final Timer screenTimeout = new Timer();
    int lastSlot = -1;
    int tick = 0;
    private final List<Villager> tradedVillager = new ArrayList<>();
    private final Timer timeoutTimer = new Timer();

    public Bot() {
        super("Bot", Category.Misc);
        setChinese("机器人");
        INSTANCE = this;
    }

    private void openAutoTradeEdit() {
        edit.setValueWithoutTask(false);
        if (!nullCheck()) {
            // 26.2：mc.setScreen → mc.setScreenAndShow
            mc.setScreenAndShow(new WindowsScreen(new ItemSelectWindow(Alien.TRADE)));
        }
    }

    private void placeBlock(BlockPos pos) {
        int block;
        if ((block = getBlock()) == -1) {
            return;
        }
        int oldSlot = mc.player.getInventory().getSelectedSlot();
        if (BlockUtil.canPlace(pos)) {

            if (BlockUtil.allowAirPlace()) {
                doSwap(block);
                BlockUtil.placedPos.add(pos);
                BlockUtil.airPlace(pos, rotate.getValue());
                if (inventory.getValue()) {
                    doSwap(block);
                    EntityUtil.syncInventory();
                } else {
                    doSwap(oldSlot);
                }
                return;
            }
            Direction side = BlockUtil.getPlaceSide(pos);
            if (side == null) return;
            doSwap(block);
            BlockUtil.placedPos.add(pos);
            BlockUtil.clickBlock(pos.relative(side), side.getOpposite(), rotate.getValue());
            if (inventory.getValue()) {
                doSwap(block);
                EntityUtil.syncInventory();
            } else {
                doSwap(oldSlot);
            }
        }
    }

    int placeProgress = 0;
    private final Timer slabPlacerDelay = new Timer();

    private void tryPlaceBlock(BlockPos pos) {
        if (pos == null) return;
        if (!(placeProgress < blocksPer.getValue())) return;
        int block;
        if (inventory.getValue()) {
            block = InventoryUtil.findClassInventorySlot(SlabBlock.class);
        } else {
            block = InventoryUtil.findClass(SlabBlock.class);
        }
        if (block == -1) return;
        Direction side = BlockUtil.getPlaceSide(pos);
        if (side == null) return;
        // 26.2：Direction#getUnitVec3 返回 Mojang Vec3，字段访问用 .x/.y/.z（无 getX()）
        Vec3 directionVec = new Vec3(pos.getX() + 0.5 + side.getUnitVec3().x * 0.5, pos.getY() + 0.5 + side.getUnitVec3().y * 0.5, pos.getZ() + 0.5 + side.getUnitVec3().z * 0.5);
        if (!BlockUtil.canPlace(pos, 6, true)) return;
        if (rotate.getValue()) {
            Alien.ROTATION.lookAt(directionVec);
        }
        if (BlockUtil.hasEntity(pos, false)) return;
        int old = mc.player.getInventory().getSelectedSlot();
        doSwap(block);
        BlockUtil.placedPos.add(pos);
        if (BlockUtil.allowAirPlace()) {
            BlockUtil.airPlace(pos, false, InteractionHand.MAIN_HAND, true);
        } else {
            BlockUtil.clickBlock(pos.relative(side), side.getOpposite(), false, InteractionHand.MAIN_HAND);
        }
        if (inventory.getValue()) {
            doSwap(block);
            EntityUtil.syncInventory();
        } else {
            doSwap(old);
        }
        if (rotate.getValue()) {
            Alien.ROTATION.snapBack();
        }
        placeProgress++;
        slabPlacerDelay.reset();
    }

    private void doSwap(int slot) {
        if (inventory.getValue()) {
            InventoryUtil.inventorySwap(slot, mc.player.getInventory().getSelectedSlot());
        } else {
            InventoryUtil.switchToSlot(slot);
        }
    }

    private int getBlock() {
        if (inventory.getValue()) {
            return InventoryUtil.findClassInventorySlot(ShulkerBoxBlock.class);
        } else {
            return InventoryUtil.findClass(ShulkerBoxBlock.class);
        }
    }

    @Override
    public String getInfo() {
        if (mode.is(Mode.XinDupe))
            return "Stage:" + stage.name() + ", Riding:" + mc.player.isPassenger();
        return mode.getValue().name();
    }

    @Override
    public void onEnable() {
        emptyBox.clear();
        storageSand = false;
        stage = Stage.Summon;
        boxPos = null;
        closeToBox = false;
        llama = null;
        putIn = false;
        tick = 0;
        scheduledTasks.clear();
        scheduledTasks2.clear();
        if (mode.is(Mode.TridentDupe)) {
            tridentDupe();
        }
    }

    @EventListener(priority = EventPriority.HIGHEST + 1)
    private void onSendPacket(PacketEvent.Send event) {
        if (mode.is(Mode.TridentDupe)) {
            if (!cancel)
                return;
            if (event.getPacket() instanceof ServerboundMovePlayerPacket
                    || event.getPacket() instanceof ServerboundContainerClosePacket)
                return;

            if (!(event.getPacket() instanceof ServerboundContainerClickPacket)
                    && !(event.getPacket() instanceof ServerboundPlayerActionPacket)) {
                return;
            }

            event.cancel();
        }
    }

    private boolean cancel = true;

    private final List<Pair<Long, Runnable>> scheduledTasks = new ArrayList<>();
    private final List<Pair<Long, Runnable>> scheduledTasks2 = new ArrayList<>();

    private void tridentDupe() {
        int delayInt = delay.getValueInt() * 100;

        int lowestHotbarSlot = 0;
        int lowestHotbarDamage = 1000;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem((i)).getItem() == Items.TRIDENT) {
                // 26.2：ItemStack.getDamage → getDamageValue
                int currentHotbarDamage = mc.player.getInventory().getItem((i)).getDamageValue();
                if (lowestHotbarDamage > currentHotbarDamage) {
                    lowestHotbarSlot = i;
                    lowestHotbarDamage = currentHotbarDamage;
                }
            }
        }
        // 26.2：interactItem → useItem
        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        cancel = true;

        int finalLowestHotbarSlot = lowestHotbarSlot;
        scheduleTask(() -> {
            cancel = false;

            if (durabilityManagement.getValue()) {
                if (finalLowestHotbarSlot != 0) {
                    mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, (44), 0, ContainerInput.SWAP, mc.player);
                    if (dropTridents.getValue())
                        mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, 44, 0, ContainerInput.THROW, mc.player);
                    mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, (36 + finalLowestHotbarSlot), 0, ContainerInput.SWAP, mc.player);
                }
            }

            mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, 3, 0, ContainerInput.SWAP, mc.player);

            // 26.2：PlayerActionC2SPacket → ServerboundPlayerActionPacket
            ServerboundPlayerActionPacket packet2 = new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM, BlockPos.ZERO, Direction.DOWN, 0);
            mc.getConnection().send(packet2);

            if (dropTridents.getValue())
                mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, 44, 0, ContainerInput.THROW, mc.player);

            cancel = true;
            scheduleTask2(this::tridentDupe, delayInt);
        }, delayInt);
    }

    public void scheduleTask(Runnable task, long delayMillis) {
        long executeTime = System.currentTimeMillis() + delayMillis;
        // 26.2：net.minecraft.util.Pair → com.mojang.datafixers.util.Pair（getLeft/getRight → getFirst/getSecond）
        scheduledTasks.add(Pair.of(executeTime, task));
    }

    public void scheduleTask2(Runnable task, long delayMillis) {
        long executeTime = System.currentTimeMillis() + delayMillis;
        scheduledTasks2.add(Pair.of(executeTime, task));
    }

    @Override
    public void onDisable() {
        BaritoneUtil.cancelEverything();
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (nullCheck()) {
            emptyBox.clear();
            stage = Stage.Summon;
            boxPos = null;
            closeToBox = false;
            llama = null;
            putIn = false;
            return;
        }
        if (autoEat.getValue() && (mc.player.getHealth() + mc.player.getAbsorptionAmount() < health.getValueFloat() || mc.player.getFoodData().getFoodLevel() < hunger.getValueInt())) {
            int food = InventoryUtil.findItem(Items.ENCHANTED_GOLDEN_APPLE);
            if (food == -1) {
                food = InventoryUtil.findItem(Items.GOLDEN_APPLE);
            }
            if (food == -1) {
                food = InventoryUtil.findItem(Items.GOLDEN_CARROT);
            }
            if (food == -1 && anyFood.getValue()) {
                food = InventoryUtil.getFood();
            }
            if (food != -1) {
                if (mc.gui.screen() != null && mode.is(Mode.XinDupe)) mc.gui.screen().onClose();

                if (mc.player.getInventory().getSelectedSlot() != food) {
                    InventoryUtil.switchToSlot(food);
                }
                // 26.2：useKey/setPressed → keyUse/setDown；PlayerInteractItemC2SPacket → ServerboundUseItemPacket
                mc.options.keyUse.setDown(true);
                endEat = true;
                sendSequencedPacket(id -> new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, id, Alien.ROTATION.getLastYaw(), Alien.ROTATION.getLastPitch()));
            }
            BaritoneUtil.cancelEverything();
            return;
        }
        if (endEat) {
            endEat = false;
            mc.options.keyUse.setDown(false);
        }
        switch (mode.getValue()) {
            case ScoreFarmer -> {
                if (getScore.getValue()) {
                    if (commandTimer.passedS(4)) {
                        // 26.2：networkHandler → mc.getConnection()，sendCommand
                        mc.getConnection().sendCommand("duel " + name.getValue());
                        commandTimer.reset();
                    }
                }
                // 26.2：GenericContainerScreenHandler → ChestMenu
                if (mc.player.containerMenu instanceof ChestMenu) {
                    if (duelItemTimer.passedS(1)) {
                        mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, 0, 0, ContainerInput.PICKUP, mc.player);
                        duelItemTimer.reset();
                    }
                }
            }
            case TridentDupe -> {
                long currentTime = System.currentTimeMillis();
                {
                    Iterator<Pair<Long, Runnable>> iterator = scheduledTasks.iterator();

                    while (iterator.hasNext()) {
                        Pair<Long, Runnable> entry = iterator.next();
                        if (entry.getFirst() <= currentTime) {
                            entry.getSecond().run();
                            iterator.remove(); // Remove executed task from the list
                        }
                    }
                }
                {
                    Iterator<Pair<Long, Runnable>> iterator = scheduledTasks2.iterator();

                    while (iterator.hasNext()) {
                        Pair<Long, Runnable> entry = iterator.next();
                        if (entry.getFirst() <= currentTime) {
                            entry.getSecond().run();
                            iterator.remove(); // Remove executed task from the list
                        }
                    }
                }
            }
            case AutoTrade -> {
                // 26.2：MerchantScreenHandler → MerchantMenu
                if (mc.player.containerMenu instanceof MerchantMenu handler) {
                    if (timeoutCloseSetting.getValue()) {
                        if (timeoutTimer.passedS(timeOutSetting.getValue())) {
                            // 26.2：CloseHandledScreenC2SPacket → ServerboundContainerClosePacket
                            mc.getConnection().send(new ServerboundContainerClosePacket(mc.player.containerMenu.containerId));
                            mc.gui.screen().onClose();
                            return;
                        }
                    }

                    int i = 0;

                    // 26.2：getRecipes() → getOffers()，TradeOfferList → MerchantOffers，TradeOffer → MerchantOffer
                    MerchantOffers list = handler.getOffers();
                    for (int size = 0; size < list.size(); ++size) {
                        if (i >= repeatSetting.getValue()) return;
                        MerchantOffer tradeOffer = list.get(size);
                        // 26.2：isDisabled → isOutOfStock
                        if (!tradeOffer.isOutOfStock()) {
                            // 26.2：getSellItem → getResult
                            if (Alien.TRADE.inWhitelist(tradeOffer.getResult().getItem().getDescriptionId())) {
                                while (i < repeatSetting.getValue()) {
                                    // 26.2：getDisplayedFirstBuyItem → getCostA，getDisplayedSecondBuyItem → getCostB
                                    if (!tradeOffer.getCostA().isEmpty()) {
                                        int count = InventoryUtil.getItemCount(tradeOffer.getCostA().getItem());
                                        if (handler.getSlot(0).getItem().getItem() == tradeOffer.getCostA().getItem()) {
                                            count += handler.getSlot(0).getItem().getCount();
                                        }
                                        if (count < tradeOffer.getCostA().getCount()) {
                                            break;
                                        }
                                    }
                                    if (!tradeOffer.getCostB().isEmpty()) {
                                        int count = InventoryUtil.getItemCount(tradeOffer.getCostB().getItem());
                                        if (handler.getSlot(1).getItem().getItem() == tradeOffer.getCostB().getItem()) {
                                            count += handler.getSlot(1).getItem().getCount();
                                        }
                                        if (count < tradeOffer.getCostB().getCount()) {
                                            break;
                                        }
                                    }
                                    // 26.2：SelectMerchantTradeC2SPacket → ServerboundSelectTradePacket
                                    mc.getConnection().send(new ServerboundSelectTradePacket(size));
                                    mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, 2, 1, ContainerInput.QUICK_MOVE, mc.player);
                                    i++;
                                }
                            }
                        }
                    }
                    if (autoCloseSetting.getValue()) {
                        if (i < repeatSetting.getValue()) {
                            mc.getConnection().send(new ServerboundContainerClosePacket(mc.player.containerMenu.containerId));
                            mc.gui.screen().onClose();
                        }
                    }
                } else {
                    timeoutTimer.reset();
                    if (autoOpenSetting.getValue()) {
                        for (Entity entity : Alien.THREAD.getEntities()) {
                            if (entity instanceof Villager villager) {
                                if (mc.player.distanceTo(villager) <= range.getValue()) {
                                    if (tradedVillager.contains(villager)) {
                                        continue;
                                    }
                                    tradedVillager.add(villager);
                                    if (rotate.getValue()) Alien.ROTATION.snapAt(villager.getEyePosition());
                                    // 26.2：PlayerInteractEntityC2SPacket 拆分为 ServerboundInteractPacket（record）
                                    mc.getConnection().send(new ServerboundInteractPacket(villager.getId(), InteractionHand.MAIN_HAND, Vec3.ZERO, mc.player.isShiftKeyDown()));
                                    if (rotate.getValue()) Alien.ROTATION.snapBack();
                                    return;
                                } else {
                                    tradedVillager.remove(villager);
                                }
                            }
                        }
                    }
                }
            }
            case SlabPlacer -> {
                if (inventory.getValue() && !EntityUtil.inInventory()) return;
                placeProgress = 0;
                if (!slabPlacerDelay.passed((long) placeDelay.getValue())) return;
                if (mc.player.isUsingItem() && usingPause.getValue()) {
                    return;
                }
                for (BlockPos pos : BlockUtil.getSphere(range.getValueFloat(), mc.player.position())) {
                    // 26.2：isFullCube → isCollisionShapeFullBlock；Level#isAir → BlockState#isAir
                    if (!mc.level.getBlockState(pos).isCollisionShapeFullBlock(mc.level, pos) && mc.level.getBlockState(pos).getBlock() instanceof SlabBlock)
                        continue;
                    if (mc.level.getBlockState(pos).isAir() || !BlockUtil.canReplace(pos.above())) continue;
                    tryPlaceBlock(pos.above());
                }
            }
            case ItemFrameDupe -> {
                tick++;
                int shulker = InventoryUtil.findClass(ShulkerBoxBlock.class);
                if (shulker != -1) {
                    for (Entity entity : Alien.THREAD.getEntities()) {
                        if (entity instanceof ItemFrame itemFrameEntity) {
                            if (entity.distanceTo(mc.player) > 3) continue;
                            InventoryUtil.switchToSlot(shulker);
                            // 26.2：interact/attack 拆分为 ServerboundInteractPacket / ServerboundAttackPacket
                            mc.getConnection().send(new ServerboundInteractPacket(itemFrameEntity.getId(), InteractionHand.MAIN_HAND, Vec3.ZERO, false));
                            if (tick >= 2) {
                                mc.getConnection().send(new ServerboundAttackPacket(itemFrameEntity.getId()));
                                // 26.2：swingHand → swing
                                mc.player.swing(InteractionHand.MAIN_HAND);
                                tick = 0;
                            }
                            return;
                        }
                    }
                }
                tick = 0;
            }
            case SandMiner -> {
                Block sandBlock = Blocks.SAND;
                if (redSand.getValue()) {
                    sandBlock = Blocks.RED_SAND;
                }
                if (Aura.INSTANCE.isOn()) {
                    if (Aura.INSTANCE.getTarget(Aura.INSTANCE.range.getValue()) != null) {
                        if (mc.gui.screen() != null) mc.gui.screen().onClose();
                        // 26.2: SwordItem 类已被扁平化（剑 = 普通 Item + WEAPON/TOOL 组件 + 标签），
                        // 原 InventoryUtil.findClass(SwordItem.class) 改为按物品标签 ItemTags.SWORDS 查找
                        int slot = InventoryUtil.findTag(ItemTags.SWORDS);
                        if (mc.player.getInventory().getSelectedSlot() != slot) {
                            InventoryUtil.switchToSlot(slot);
                        }
                        BaritoneUtil.cancelEverything();
                        return;
                    }
                }
                if (mc.gui.screen() == null) {
                    screenTimeout.reset();
                } else if (screenTimeout.passedS(5)) {
                    mc.gui.screen().onClose();
                }
                if (InventoryUtil.findClassInventorySlot(ShovelItem.class) == -1) {
                    BaritoneUtil.cancelEverything();
                    // 26.2：CraftingScreenHandler → CraftingMenu
                    if (mc.player.containerMenu instanceof CraftingMenu) {
                        if (craftTimer.passedS(1)) {
                            // 26.2：配方书改为 RecipeCollection/RecipeDisplayEntry，结果物品用 resultItems(ContextMap) 解析
                            ContextMap context = SlotDisplayContext.fromLevel(mc.level);
                            for (RecipeCollection recipeResult : mc.player.getRecipeBook().getCollections()) {
                                for (RecipeDisplayEntry recipe : recipeResult.getRecipes()) {
                                    if (recipe.resultItems(context).stream().anyMatch(stack -> stack.getItem() instanceof ShovelItem)) {
                                        craftTimer.reset();
                                        // 26.2：clickRecipe → handlePlaceRecipe(containerId, RecipeDisplayId, useMaxItems)
                                        mc.gameMode.handlePlaceRecipe(mc.player.containerMenu.containerId, recipe.id(), false);
                                        mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, 0, 1, ContainerInput.QUICK_MOVE, mc.player);
                                        return;
                                    }
                                }
                            }
                        }
                    } else {
                        BlockPos bestPos = null;
                        double distance = 100;
                        for (BlockPos pos : BlockUtil.getSphere(3)) {
                            if (mc.level.getBlockState(pos).getBlock() == Blocks.CRAFTING_TABLE && BlockUtil.getClickSideStrict(pos) != null) {
                                BlockUtil.clickBlock(pos, BlockUtil.getClickSide(pos), rotate.getValue());
                                return;
                            }
                            if (BlockUtil.canPlace(pos)) {
                                if (bestPos == null || Mth.sqrt((float) mc.player.distanceToSqr(Vec3.atCenterOf(pos))) < distance) {
                                    bestPos = pos;
                                    distance = Mth.sqrt((float) mc.player.distanceToSqr(Vec3.atCenterOf(pos)));
                                }
                            }
                        }

                        if (bestPos != null) {
                            int craftTable = InventoryUtil.findItemInventorySlot(Items.CRAFTING_TABLE);
                            if (craftTable == -1) return;
                            InventoryUtil.inventorySwap(craftTable, mc.player.getInventory().getSelectedSlot());
                            BlockUtil.placeBlock(bestPos, rotate.getValue());
                            InventoryUtil.inventorySwap(craftTable, mc.player.getInventory().getSelectedSlot());
                        }
                    }
                    return;
                }

                Entity hasShulkerItemEntity = null;
                for (Entity entity : Alien.THREAD.getEntities()) {
                    if (entity instanceof ItemEntity itemEntity) {
                        if (itemEntity.getItem().getItem() instanceof BlockItem item && (item.getBlock() instanceof ShulkerBoxBlock || item.getBlock() == Blocks.ENDER_CHEST)) {
                            hasShulkerItemEntity = itemEntity;
                            break;
                        }
                    }
                }
                if (mc.player.containerMenu instanceof CraftingMenu) {
                    if (mc.gui.screen() != null) mc.gui.screen().onClose();
                }
                int sands = InventoryUtil.getItemCount(Items.SAND);
                if (sands >= 27 * 64) {
                    //CommandManager.sendMessage("Sand Full");
                    for (int slot1 = 9; slot1 < 36; ++slot1) {
                        ItemStack stack = mc.player.getInventory().getItem(slot1);
                        if (stack.isEmpty()) continue;
                        if (stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() == sandBlock && stack.getCount() < stack.getMaxStackSize()) {
                            mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, slot1, 1, ContainerInput.THROW, mc.player);
                        }
                    }
                    if (mc.gui.screen() instanceof InventoryScreen) {
                        mc.gui.screen().onClose();
                        return;
                    }
                    int shulkerSlot = InventoryUtil.findClassInventorySlot(ShulkerBoxBlock.class);
                    BlockPos shulker = BlockUtil.getBlock(ShulkerBoxBlock.class, 3f);
                    if (mc.gui.screen() instanceof AbstractContainerScreen<?> && mc.player.containerMenu instanceof AbstractContainerMenu screenHandler) {
                        if (!(mc.player.containerMenu instanceof ShulkerBoxMenu)) {
                            //CommandManager.sendMessage("Take Shulker");
                            BaritoneUtil.cancelEverything();
                            if (shulkerSlot != -1) {
                                //CommandManager.sendMessage("Have shulker, Close E   nderChest");
                                mc.gui.screen().onClose();
                            } else {
                                for (Slot slot : screenHandler.slots) {
                                    if (slot.index < 27 && slot.getItem().getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock && !ShulkerViewer.hasItems(slot.getItem())) {
                                        mc.gameMode.handleContainerInput(screenHandler.containerId, slot.index, 0, ContainerInput.QUICK_MOVE, mc.player);
                                        break;
                                    }
                                }
                            }
                        } else {
                            //CommandManager.sendMessage("Fill Sand");
                            BaritoneUtil.cancelEverything();
                            storageSand = true;
                            if (mc.player.containerMenu instanceof ShulkerBoxMenu shulkerBoxScreenHandler) {
                                for (Slot slot : shulkerBoxScreenHandler.slots) {
                                    if (slot.index < 27 && slot.getItem().isEmpty()) {
                                        for (Slot slot2 : shulkerBoxScreenHandler.slots) {
                                            if (slot2.index >= 27 && slot2.getItem().getItem() == Items.SAND && slot2.getItem().getCount() == slot2.getItem().getMaxStackSize()) {
                                                mc.gameMode.handleContainerInput(shulkerBoxScreenHandler.containerId, slot2.index, 0, ContainerInput.QUICK_MOVE, mc.player);
                                                return;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else if (shulker == null) {
                        //CommandManager.sendMessage("shulker == null");
                        double distance = 100;
                        BlockPos bestPos = null;
                        for (BlockPos pos : BlockUtil.getSphere(3, mc.player.getEyePosition())) {
                            if (!mc.level.getBlockState(pos.above()).isAir()) continue;
                            if (!BlockUtil.clientCanPlace(pos, false)
                                    || !BlockUtil.isStrictDirection(pos.relative(Direction.DOWN), Direction.UP)
                                    || !BlockUtil.canClick(pos.relative(Direction.DOWN))
                            ) continue;
                            if (bestPos == null || Mth.sqrt((float) mc.player.distanceToSqr(Vec3.atCenterOf(pos))) < distance) {
                                distance = Mth.sqrt((float) mc.player.distanceToSqr(Vec3.atCenterOf(pos)));
                                bestPos = pos;
                            }
                        }
                        if (bestPos != null) {
                            //CommandManager.sendMessage("bestPos != null");
                            BaritoneUtil.cancelEverything();
                            if (shulkerSlot != -1) {
                                //CommandManager.sendMessage("shulkerSlot != -1");
                                InventoryUtil.inventorySwap(shulkerSlot, mc.player.getInventory().getSelectedSlot());
                                BlockUtil.clickBlock(bestPos.relative(Direction.DOWN), Direction.UP, rotate.getValue());
                                InventoryUtil.inventorySwap(shulkerSlot, mc.player.getInventory().getSelectedSlot());
                            } else {
                                BlockPos ec = BlockUtil.getBlock(Blocks.ENDER_CHEST, 3f);
                                if (ec != null) {
                                    //CommandManager.sendMessage("enderchest BlockPos != null");
                                    BlockUtil.clickBlock(ec, BlockUtil.getClickSide(ec), rotate.getValue());
                                } else {
                                    int enderChest = InventoryUtil.findBlockInventorySlot(Blocks.ENDER_CHEST);
                                    if (enderChest != -1) {
                                        //CommandManager.sendMessage("enderChest != -1");
                                        InventoryUtil.inventorySwap(enderChest, mc.player.getInventory().getSelectedSlot());
                                        BlockUtil.placeBlock(bestPos, true);
                                        InventoryUtil.inventorySwap(enderChest, mc.player.getInventory().getSelectedSlot());
                                    }
                                }
                            }
                        }
                    } else {
                        //CommandManager.sendMessage("shulker != null, try click shulker");
                        BlockUtil.clickBlock(shulker, BlockUtil.getClickSide(shulker), rotate.getValue());
                    }
                } else if (storageSand) {
                    //CommandManager.sendMessage("Fill Sand");
                    if (mc.player.containerMenu instanceof ShulkerBoxMenu shulkerBoxScreenHandler) {
                        for (Slot slot : shulkerBoxScreenHandler.slots) {
                            if (slot.index < 27 && slot.getItem().isEmpty()) {
                                for (Slot slot2 : shulkerBoxScreenHandler.slots) {
                                    if (slot2.index >= 27 && slot2.getItem().getItem() == Items.SAND && slot2.getItem().getCount() == slot2.getItem().getMaxStackSize()) {
                                        mc.gameMode.handleContainerInput(shulkerBoxScreenHandler.containerId, slot2.index, 0, ContainerInput.QUICK_MOVE, mc.player);
                                        return;
                                    }
                                }
                            }
                        }
                        mc.gui.screen().onClose();
                        storageSand = false;
                    }
                } else {
                    BlockPos shulker = BlockUtil.getBlock(ShulkerBoxBlock.class, 3f);
                    if (shulker != null) {
                        //CommandManager.sendMessage("Recovery Shulker");
                        BaritoneUtil.mine(mc.level.getBlockState(shulker).getBlock());
                    } else {
                        int fillShulker = InventoryUtil.findClassInventorySlot(ShulkerBoxBlock.class);
                        if (fillShulker != -1) {
                            BaritoneUtil.cancelEverything();
                            if (mc.player.containerMenu instanceof AbstractContainerMenu screenHandler) {
                                for (Slot slot : screenHandler.slots) {
                                    if (slot.index >= 27 && slot.getItem().getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock) {
                                        mc.gameMode.handleContainerInput(screenHandler.containerId, slot.index, 0, ContainerInput.QUICK_MOVE, mc.player);
                                        return;
                                    }
                                }
                            }
                            double distance = 100;
                            BlockPos bestPos = null;
                            for (BlockPos pos : BlockUtil.getSphere(3)) {
                                if (!mc.level.getBlockState(pos.above()).isAir()) continue;
                                if (!BlockUtil.clientCanPlace(pos, false)
                                        || !BlockUtil.isStrictDirection(pos.relative(Direction.DOWN), Direction.UP)
                                        || !BlockUtil.canClick(pos.relative(Direction.DOWN))
                                ) continue;
                                if (bestPos == null || Mth.sqrt((float) mc.player.distanceToSqr(Vec3.atCenterOf(pos))) < distance) {
                                    distance = Mth.sqrt((float) mc.player.distanceToSqr(Vec3.atCenterOf(pos)));
                                    bestPos = pos;
                                }
                            }
                            if (bestPos != null) {
                                BlockPos ec = BlockUtil.getBlock(Blocks.ENDER_CHEST, 3f);
                                if (ec != null) {
                                    BlockUtil.clickBlock(ec, BlockUtil.getClickSide(ec), rotate.getValue());
                                } else {
                                    int enderChest = InventoryUtil.findBlockInventorySlot(Blocks.ENDER_CHEST);
                                    if (enderChest != -1) {
                                        InventoryUtil.inventorySwap(enderChest, mc.player.getInventory().getSelectedSlot());
                                        BlockUtil.placeBlock(bestPos, true);
                                        InventoryUtil.inventorySwap(enderChest, mc.player.getInventory().getSelectedSlot());
                                    }
                                }
                            }
                            return;
                        }
                        if (hasShulkerItemEntity != null) {
                            BaritoneUtil.gotoPos(hasShulkerItemEntity.blockPosition());
                        }
                        BlockPos pos = BlockUtil.getBlock(Blocks.ENDER_CHEST, 5);
                        if (pos != null) {
                            BaritoneUtil.mine(Blocks.ENDER_CHEST);
                            if (mc.gui.screen() != null) {
                                mc.gui.screen().onClose();
                            }
                            /*BaritoneUtil.gotoPos(pos);
                            if (mc.player.position().distanceTo(Vec3.atCenterOf(pos)) < 2) {
                                PacketMine.INSTANCE.mine(pos);
                            }*/
                            return;
                        }
                        if (ai.getValue()) {
                            BaritoneUtil.mine(sandBlock);
                        }
                        if (nuker.getValue()) {
                            if (!mc.player.onGround()) return;
                            int b = 0;
                            for (BlockPos sand : BlockUtil.getSphere(3f, mc.player.getEyePosition())) {
                                if (sandBlock != mc.level.getBlockState(sand).getBlock()) continue;
                                Direction side = BlockUtil.getClickSideStrict(sand);
                                if (side == null) continue;
                                PlaceRender.INSTANCE.create(sand);
                                Alien.ROTATION.snapAt(Vec3.atCenterOf(sand));
                                // 26.2：PlayerActionC2SPacket → ServerboundPlayerActionPacket
                                sendSequencedPacket(id -> new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, sand, side, id));
                                sendSequencedPacket(id -> new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, sand, side, id));
                                sendSequencedPacket(id -> new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, sand, side, id));
                                Alien.ROTATION.snapBack();
                                b++;
                                if (b >= breaks.getValue()) {
                                    return;
                                }
                            }
                        }
                    }
                }
            }
            case Ominous -> {
                int ominousSlot = InventoryUtil.findItem(Items.OMINOUS_BOTTLE);
                if (ominousSlot != -1) {
                    if (!mc.player.hasEffect(MobEffects.BAD_OMEN) && !mc.player.hasEffect(MobEffects.RAID_OMEN)) {
                        if (ominousTimer.passedS(maxTime.getValue())) {
                            if (mc.player.getMainHandItem().getItem() != Items.OMINOUS_BOTTLE) {
                                lastSlot = mc.player.getInventory().getSelectedSlot();
                                InventoryUtil.switchToSlot(ominousSlot);
                            } else {
                                mc.options.keyUse.setDown(true);
                                ominousTimer.reset();
                            }
                        }
                    } else if (mc.player.getMainHandItem().getItem() == Items.OMINOUS_BOTTLE) {
                        mc.options.keyUse.setDown(false);
                        if (lastSlot != -1) {
                            InventoryUtil.switchToSlot(lastSlot);
                            lastSlot = -1;
                        }
                    }
                }
            }

            case TurtlePath -> {
                int seagrass = InventoryUtil.findItem(Items.SEAGRASS);
                if (seagrass == -1) {
                    if (BaritoneUtil.isActive()) {
                        BaritoneUtil.cancelEverything();
                    }
                    return;
                }
                if (timeOut.passedS(300)) {
                    inLove.clear();
                    timeOut.reset();
                }
                double distance = 0;
                Turtle target = null;
                for (Entity entity : Alien.THREAD.getEntities()) {
                    if (entity instanceof Turtle turtle) {
                        if (turtle.isBaby()) continue;
                        if (inLove.contains(turtle)) continue;
                        if (Math.abs(mc.player.getY() - turtle.getY()) > 3) continue;
                        double dis = mc.player.distanceTo(turtle);
                        if (target == null || dis < distance) {
                            distance = dis;
                            target = turtle;
                        }
                    }
                }
                if (target == null) {
                    if (timeOut.passedS(20)) {
                        inLove.clear();
                        timeOut.reset();
                    }
                    if (BaritoneUtil.isActive()) {
                        BaritoneUtil.cancelEverything();
                    }
                    return;
                }
                if (mc.player.distanceTo(target) < 3) {
                    BaritoneUtil.cancelEverything();
                    InventoryUtil.switchToSlot(seagrass);
                    Alien.ROTATION.snapAt(target.position());
                    mc.getConnection().send(new ServerboundInteractPacket(target.getId(), InteractionHand.MAIN_HAND, Vec3.ZERO, mc.player.isShiftKeyDown()));
                    EntityUtil.swingHand(InteractionHand.MAIN_HAND, AntiCheat.INSTANCE.interactSwing.getValue());
                    //InventoryUtil.switchToSlot(selectedSlot);
                    Alien.ROTATION.snapBack();
                    inLove.add(target);
                } else {
                    BaritoneUtil.gotoPos(target.blockPosition());
                }
            }
            case Turtle -> {
                int seagrass = InventoryUtil.findItem(Items.SEAGRASS);
                if (seagrass == -1) {
                    return;
                }
                if (timeOut.passedS(300)) {
                    inLove.clear();
                    timeOut.reset();
                }
                double distance = 0;
                Turtle target = null;
                for (Entity entity : Alien.THREAD.getEntities()) {
                    if (entity instanceof Turtle turtle) {
                        if (turtle.isBaby()) continue;
                        if (inLove.contains(turtle)) continue;
                        if (mc.player.distanceTo(turtle) > 3) continue;
                        double dis = mc.player.distanceTo(turtle);
                        if (target == null || dis < distance) {
                            distance = dis;
                            target = turtle;
                        }
                    }
                }
                if (target == null) {
                    if (timeOut.passedS(20)) {
                        inLove.clear();
                        timeOut.reset();
                    }
                    return;
                }
                InventoryUtil.switchToSlot(seagrass);
                Alien.ROTATION.snapAt(target.position());
                mc.getConnection().send(new ServerboundInteractPacket(target.getId(), InteractionHand.MAIN_HAND, Vec3.ZERO, mc.player.isShiftKeyDown()));
                EntityUtil.swingHand(InteractionHand.MAIN_HAND, AntiCheat.INSTANCE.interactSwing.getValue());
                //InventoryUtil.switchToSlot(selectedSlot);
                Alien.ROTATION.snapBack();
                inLove.add(target);
            }
            case NPlusOneDupe -> {
                BlockPos placePos = PacketMine.getBreakPos();
                if (placePos != null && BlockUtil.canPlace(placePos)) {
                    placeBlock(placePos);
                }
            }
            case XinDupe -> {
                if (llama != null) {
                    if (llama.isDeadOrDying() || llama.distanceTo(mc.player) > 20) {
                        llama = null;
                    }
                }
                int chestSlot = InventoryUtil.findBlockInventorySlot(Blocks.CHEST);
                // 26.2：SwordItem 类已扁平化，改用 ItemTags.SWORDS 标签查找
                int swordSlot = InventoryUtil.findTag(ItemTags.SWORDS);
                if (chestSlot == -1) {
                    emptyBox.clear();
                    stage = Stage.Open;
                    boxPos = null;
                    closeToBox = false;
                    llama = null;
                    putIn = false;
                    return;
                }

                int shulkers = 0;
                for (Map.Entry<Integer, ItemStack> entry : InventoryUtil.getInventoryAndHotbarSlots().entrySet()) {
                    if (entry.getValue().getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock) {
                        shulkers++;
                    }
                }
                if (shulkers > 18) {
                    if (mc.gui.screen() != null) mc.gui.screen().onClose();
                    for (int slot1 = 9; slot1 < 36; ++slot1) {
                        ItemStack stack = mc.player.getInventory().getItem(slot1);
                        if (stack.isEmpty()) continue;
                        if (stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock) {
                            shulkers--;
                            mc.gameMode.handleContainerInput(mc.player.inventoryMenu.containerId, slot1, 1, ContainerInput.THROW, mc.player);
                            if (shulkers <= 18) return;
                        }
                    }
                    return;
                }

                if (closeToBox && boxPos != null) {
                    closeTo(boxPos);
                }
                if (!openTimeOut.passed(100)) return;
                switch (stage) {
                    case Open -> {
                        if (!closeScreen.passed(250)) {
                            if (mc.gui.screen() != null) mc.gui.screen().onClose();
                            return;
                        }
                        if (mc.player.isPassenger()) {
                            mc.getConnection().send(new ServerboundPlayerInputPacket(new Input(false, false, false, false, false, true, false)));
                            return;
                        }
                        if (boxPos == null || emptyBox.contains(boxPos)) {
                            for (BlockPos pos : BlockUtil.getSphere(3)) {
                                if (!emptyBox.contains(pos) && mc.level.getBlockEntity(pos) instanceof ShulkerBoxBlockEntity && BlockUtil.getClickSideStrict(pos) != null) {
                                    closeToBox = false;
                                    boxPos = pos;
                                    break;
                                }
                            }
                        }
                        if (boxPos != null && !emptyBox.contains(boxPos)) {
                            if (mc.player.getEyePosition().distanceTo(Vec3.atCenterOf(boxPos)) < 4) {
                                if (openTimeOut.passedS(1)) {
                                    closeToBox = false;
                                    openTimeOut.reset();
                                    BlockUtil.clickBlock(boxPos, BlockUtil.getClickSide(boxPos), true);
                                    stage = Stage.Take;
                                }
                            } else {
                                closeToBox = true;
                            }
                        }
                    }
                    case Take -> {
                        if (mc.player.isPassenger()) {
                            mc.getConnection().send(new ServerboundPlayerInputPacket(new Input(false, false, false, false, false, true, false)));
                            return;
                        }
                        if (boxPos == null || emptyBox.contains(boxPos)) {
                            closeScreen.reset();
                            stage = Stage.Open;
                            return;
                        }
                        if (mc.player.containerMenu instanceof ShulkerBoxMenu shulker) {
                            boolean egg = false;
                            boolean hay = false;
                            for (Slot slot : shulker.slots) {
                                if (slot.index < 27 && !slot.getItem().isEmpty()) {
                                    if (slot.getItem().getItem() == Items.EGG) {
                                        egg = true;
                                    }
                                    if (slot.getItem().getItem() == Blocks.HAY_BLOCK.asItem()) {
                                        hay = true;
                                    }
                                }
                            }
                            if (egg && hay) {
                                int eggs = 0;
                                int hays = 0;
                                for (Map.Entry<Integer, ItemStack> entry : InventoryUtil.getInventoryAndHotbarSlots().entrySet()) {
                                    if (entry.getValue().getItem() == Items.EGG) {
                                        eggs++;
                                    }
                                    if (entry.getValue().getItem() == Blocks.HAY_BLOCK.asItem()) {
                                        hays++;
                                    }
                                }
                                for (Slot slot : shulker.slots) {
                                    if (!slot.getItem().isEmpty()) {
                                        if (slot.index < 27) {
                                            if (slot.getItem().getItem() == Items.EGG && eggs < 2) {
                                                eggs++;
                                                mc.gameMode.handleContainerInput(shulker.containerId, slot.index, 0, ContainerInput.QUICK_MOVE, mc.player);
                                            }
                                            if (slot.getItem().getItem() == Blocks.HAY_BLOCK.asItem() && hays < 2) {
                                                hays++;
                                                mc.gameMode.handleContainerInput(shulker.containerId, slot.index, 0, ContainerInput.QUICK_MOVE, mc.player);
                                            }
                                        } else {
                                            if (slot.getItem().getItem() == Items.LEATHER) {
                                                mc.gameMode.handleContainerInput(shulker.containerId, slot.index, 0, ContainerInput.QUICK_MOVE, mc.player);
                                            }
                                        }
                                    }
                                }
                                if (hays < 1 || eggs < 1) {
                                    emptyBox.add(boxPos);
                                }
                                if (mc.gui.screen() != null) mc.gui.screen().onClose();
                                            stage = Stage.Summon;
                            } else {
                                closeScreen.reset();
                                emptyBox.add(boxPos);
                                stage = Stage.Open;
                            }
                        } else if (openTimeOut.passedS(1)) {
                            closeScreen.reset();
                            stage = Stage.Open;
                        }
                    }
                    case Summon -> {
                        int eggs = 0;
                        int hays = 0;
                        for (Map.Entry<Integer, ItemStack> entry : InventoryUtil.getInventoryAndHotbarSlots().entrySet()) {
                            if (entry.getValue().getItem() == Items.EGG) {
                                eggs++;
                            }
                            if (entry.getValue().getItem() == Blocks.HAY_BLOCK.asItem()) {
                                hays++;
                            }
                        }
                        if (eggs <= 1 || hays <= 1) {
                            closeScreen.reset();
                            stage = Stage.Open;
                            return;
                        }
                        for (Entity entity : Alien.THREAD.getEntities()) {
                            if (entity instanceof Llama llamaEntity && mc.player.getEyePosition().distanceTo(entity.position()) < 10 && entity.isAlive()) {
                                if (mc.player.getEyePosition().distanceTo(entity.position()) < 5) {
                                    llama = llamaEntity;
                                    stage = Stage.Tame;
                                } else {
                                    closeTo(entity.blockPosition());
                                }
                                return;
                            }
                        }
                        if (mc.gui.screen() != null) mc.gui.screen().onClose();
    
                        int slot = InventoryUtil.findItemInventorySlot(Items.EGG);
                        InventoryUtil.inventorySwap(slot, mc.player.getInventory().getSelectedSlot());
                        Alien.ROTATION.snapAt(Alien.ROTATION.getLastYaw(), 89);
                        sendSequencedPacket(id -> new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, id, Alien.ROTATION.getLastYaw(), Alien.ROTATION.getLastPitch()));
                        InventoryUtil.inventorySwap(slot, mc.player.getInventory().getSelectedSlot());
                    }
                    case Tame -> {
                        if (llama == null || llama.isDeadOrDying()) {
                            stage = Stage.Summon;
                            return;
                        }
                        int eggs = 0;
                        int hays = 0;
                        for (Map.Entry<Integer, ItemStack> entry : InventoryUtil.getInventoryAndHotbarSlots().entrySet()) {
                            if (entry.getValue().getItem() == Items.EGG) {
                                eggs++;
                            }
                            if (entry.getValue().getItem() == Blocks.HAY_BLOCK.asItem()) {
                                hays++;
                            }
                        }
                        if (eggs <= 1 || hays <= 1) {
                            closeScreen.reset();
                            stage = Stage.Open;
                            return;
                        }

                        if (mc.player.isPassenger()) {
                            if (llama.isTamed()) {
                                if (llama.hasChest()) {
                                    int moves = 0;
                                    // 26.2：HorseScreenHandler → HorseInventoryMenu
                                    if (mc.player.containerMenu instanceof HorseInventoryMenu shulker) {
                                        if (putTimer.passed(250)) {
                                            if (!putIn) {
                                                for (Slot slot : shulker.slots) {
                                                    if (slot.getItem().getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock) {
                                                        mc.gameMode.handleContainerInput(shulker.containerId, slot.index, 0, ContainerInput.QUICK_MOVE, mc.player);
                                                        moves++;
                                                        putTimer.reset();
                                                        if (moves >= 15) break;
                                                    }
                                                }
                                                putIn = true;
                                            } else {
                                                stage = Stage.Kill;
                                                mc.getConnection().send(new ServerboundPlayerInputPacket(new Input(false, false, false, false, false, true, false)));
                                            }
                                        }
                                    } else {
                                        putIn = false;
                                        putTimer.reset();
                                        mc.player.sendOpenInventory();
                                    }
                                } else {
                                    mc.getConnection().send(new ServerboundPlayerInputPacket(new Input(false, false, false, false, false, true, false)));
                                }
                            }
                        } else {
                            if (mc.player.getEyePosition().distanceTo(llama.position()) > 5) {
                                closeTo(llama.blockPosition());
                                return;
                            }
                            if (llama.isBaby()) {
                                if (mc.gui.screen() != null) mc.gui.screen().onClose();
            
                                int slot = InventoryUtil.findBlockInventorySlot(Blocks.HAY_BLOCK);
                                InventoryUtil.inventorySwap(slot, mc.player.getInventory().getSelectedSlot());
                                Alien.ROTATION.lookAt(llama.position());
                                mc.gameMode.interact(mc.player, llama, new EntityHitResult(llama), InteractionHand.MAIN_HAND);
                                InventoryUtil.inventorySwap(slot, mc.player.getInventory().getSelectedSlot());
                            } else {
                                if (llama.isTamed()) {
                                    if (llama.hasChest()) {
                                        for (int i = 0; i < 9; ++i) {
                                            if (mc.player.getInventory().getItem(i).isEmpty()) {
                                                InventoryUtil.switchToSlot(i);
                                                mc.gameMode.interact(mc.player, llama, new EntityHitResult(llama), InteractionHand.MAIN_HAND);
                                                return;
                                            }
                                        }
                                        for (int i = 0; i < 9; ++i) {
                                            if (mc.player.getInventory().getItem(i).getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock || mc.player.getInventory().getItem(i).getItem() == Items.LEATHER) {
                                                InventoryUtil.switchToSlot(i);
                                                mc.player.drop(true);
                                                mc.gameMode.interact(mc.player, llama, new EntityHitResult(llama), InteractionHand.MAIN_HAND);
                                                return;
                                            }
                                        }
                                    } else {
                                        putTimer.reset();
                                        putIn = false;
                                        if (mc.gui.screen() != null) mc.gui.screen().onClose();
                    
                                        InventoryUtil.inventorySwap(chestSlot, mc.player.getInventory().getSelectedSlot());
                                        Alien.ROTATION.lookAt(llama.position());
                                        mc.gameMode.interact(mc.player, llama, new EntityHitResult(llama), InteractionHand.MAIN_HAND);
                                        InventoryUtil.inventorySwap(chestSlot, mc.player.getInventory().getSelectedSlot());
                                    }
                                } else {
                                    if (mc.gui.screen() != null) mc.gui.screen().onClose();
                
                                    for (int i = 0; i < 9; ++i) {
                                        if (mc.player.getInventory().getItem(i).isEmpty()) {
                                            InventoryUtil.switchToSlot(i);
                                            mc.gameMode.interact(mc.player, llama, new EntityHitResult(llama), InteractionHand.MAIN_HAND);
                                            return;
                                        }
                                    }
                                    for (int i = 0; i < 9; ++i) {
                                        if (mc.player.getInventory().getItem(i).getItem() instanceof BlockItem blockItem) {
                                            if (blockItem.getBlock() instanceof ShulkerBoxBlock) {
                                                InventoryUtil.switchToSlot(i);
                                                mc.player.drop(true);
                                                mc.gameMode.interact(mc.player, llama, new EntityHitResult(llama), InteractionHand.MAIN_HAND);
                                                return;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    case Kill -> {
                        if (llama == null || llama.isDeadOrDying()) {
                            llama = null;
                            stage = Stage.Summon;
                            return;
                        }
                        if (mc.gui.screen() != null) mc.gui.screen().onClose();
                        if (mc.player.isPassenger()) {
                            mc.getConnection().send(new ServerboundPlayerInputPacket(new Input(false, false, false, false, false, true, false)));
                            return;
                        }
                        if (mc.player.position().distanceTo(llama.position()) > 1) {
                            closeTo(llama.blockPosition());
                        }
                        if (mc.player.position().distanceTo(llama.position()) > 2) {
                            return;
                        }
                        InventoryUtil.switchToSlot(swordSlot);
                        if (check()) {
                            Alien.ROTATION.lookAt(llama.getEyePosition());
                            mc.gameMode.attack(mc.player, llama);
                            EntityUtil.swingHand(InteractionHand.MAIN_HAND, SwingSide.All);
                        }

                    }
                }
            }
        }
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @EventListener
    private void PacketReceive(PacketEvent.Receive event) {
        if (nullCheck()) {
            return;
        }
        if (mode.is(Mode.ScoreFarmer) && event.getPacket() instanceof ClientboundSystemChatPacket packet) {
            if (packet.content() != null) {
                String received = packet.content().getString().replaceAll("§[a-zA-Z0-9]", "");
                if (!getScore.getValue() && received.contains("你收到一个决斗申请")) {
                    mc.getConnection().sendCommand("duel accept " + name.getValue());
                } else if (received.contains("<")) {
                } else if (getScore.getValue() && received.contains("Starting in 3 seconds")) {
                    mc.getConnection().sendCommand("suicide");
                } else if (!getScore.getValue() && received.contains("Starting in 4 seconds")) {
                    mc.getConnection().sendCommand("suicide");
                }
            }
        }
    }

    @EventListener
    public void onRotate(UpdateRotateEvent event) {
        if (mode.is(Mode.XinDupe)) {
            event.setPitch(88);
        }
    }

    private boolean check() {
        int at = ((ILivingEntity) mc.player).getLastAttackedTicks();
        return Math.max(at / Aura.getAttackCooldownProgressPerTick(), 0.0F) >= 1.3;
    }

    private void closeTo(BlockPos pos) {
        double speed = 0.2873 / 1.5;
        float forward = 1f;
        float side = 0;
        float yaw = RotationManager.getRotation(Vec3.atCenterOf(pos))[0];
        final double sin = Math.sin(Math.toRadians(yaw + 90.0f));
        final double cos = Math.cos(Math.toRadians(yaw + 90.0f));
        final double posX = forward * speed * cos + side * speed * sin;
        final double posZ = forward * speed * sin - side * speed * cos;
        MovementUtil.setMotionX(posX);
        MovementUtil.setMotionZ(posZ);
    }

    public enum Mode {
        AutoTrade,
        ItemFrameDupe,
        XinDupe,
        NPlusOneDupe,
        TridentDupe,
        TurtlePath,
        Turtle,
        SandMiner,
        ScoreFarmer,
        SlabPlacer,
        Ominous,
        None,
    }

    public enum Stage {
        Open,
        Take,
        Summon,
        Tame,
        Kill
    }
}