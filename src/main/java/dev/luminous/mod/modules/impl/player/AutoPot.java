package dev.luminous.mod.modules.impl.player;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.ClientTickEvent;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.api.utils.player.EntityUtil;
import dev.luminous.api.utils.player.InventoryUtil;
import dev.luminous.api.utils.world.BlockPosX;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BindSetting;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;

public class AutoPot extends Module {

    public static AutoPot INSTANCE;
    private final SliderSetting delay = add(new SliderSetting("Delay", 5, 0, 10, 0.1).setSuffix("s"));
    private final BooleanSetting speed =
            add(new BooleanSetting("Speed", false));
    private final BooleanSetting resistance =
            add(new BooleanSetting("Resistance", false));
    private final BooleanSetting strength =
            add(new BooleanSetting("Strength", false));
    private final BooleanSetting slowFalling =
            add(new BooleanSetting("SlowFalling", false));
    private final BooleanSetting usingPause =
            add(new BooleanSetting("UsingPause", true));
    private final BooleanSetting onlyGround =
            add(new BooleanSetting("OnlyGround", false));
    private final BooleanSetting inventory =
            add(new BooleanSetting("InventorySwap", true));
    private final BindSetting speedKey =
            add(new BindSetting("SpeedKey", -1));
    private final BindSetting strengthKey =
            add(new BindSetting("StrengthKey", -1));
    private final BindSetting resistanceKey =
            add(new BindSetting("ResistanceKey", -1));
    private final Timer delayTimer = new Timer();
    private boolean throwing = false;
    private boolean turtlePress;
    private boolean speedPress;
    private boolean strengthPress;

    public AutoPot() {
        super("AutoPot", Category.Player);
        setChinese("自动药水");
        INSTANCE = this;
        Alien.EVENT_BUS.subscribe(new AutoPotTick());
    }

    public static int findPotionInventorySlot(MobEffect targetEffect) {
        for (int i = 35; i >= 0; --i) {
            ItemStack itemStack = mc.player.getInventory().getItem(i);
            if (Item.getId(itemStack.getItem()) != Item.getId(Items.SPLASH_POTION)) continue;
            // 26.2: PotionContentsComponent → PotionContents（DEFAULT → EMPTY，getEffects → getAllEffects，getEffectType → getEffect）
            PotionContents potionContentsComponent = itemStack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
            for (MobEffectInstance effect : potionContentsComponent.getAllEffects()) {
                if (effect.getEffect().value() == targetEffect) {
                    return i < 9 ? i + 36 : i;
                }
            }
        }
        return -1;
    }

    public static int findPotion(MobEffect targetEffect) {
        for (int i = 0; i < 9; ++i) {
            ItemStack itemStack = mc.player.getInventory().getItem(i);
            if (Item.getId(itemStack.getItem()) != Item.getId(Items.SPLASH_POTION)) continue;
            PotionContents potionContentsComponent = itemStack.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY);
            for (MobEffectInstance effect : potionContentsComponent.getAllEffects()) {
                if (effect.getEffect().value() == targetEffect) {
                    return i;
                }
            }
        }
        return -1;
    }

    @Override
    public void onDisable() {
        throwing = false;
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (inventory.getValue() && !EntityUtil.inInventory()) return;
        if (!delayTimer.passedMs(delay.getValue() * 1000)) return;
        // 26.2: Level 无 isAir，改用 BlockState#isAir
        if (!onlyGround.getValue() || (mc.player.onGround() || Alien.PLAYER.isInWeb(mc.player)) && !mc.level.getBlockState(new BlockPosX(mc.player.position().add(0, -1, 0))).isAir()) {
            if (resistance.getValue() && (!mc.player.hasEffect(MobEffects.RESISTANCE) || mc.player.getEffect(MobEffects.RESISTANCE).getAmplifier() < 2)) {
                throwing = checkThrow(MobEffects.RESISTANCE.value());
                if (isThrow()) {
                    throwPotion(MobEffects.RESISTANCE.value());
                    return;
                }
            }
            if (speed.getValue() && !mc.player.hasEffect(MobEffects.SPEED)) {
                throwing = checkThrow(MobEffects.SPEED.value());
                if (isThrow()) {
                    throwPotion(MobEffects.SPEED.value());
                    return;
                }
            }
            if (strength.getValue() && !mc.player.hasEffect(MobEffects.STRENGTH)) {
                throwing = checkThrow(MobEffects.STRENGTH.value());
                if (isThrow()) {
                    throwPotion(MobEffects.STRENGTH.value());
                    return;
                }
            }
            if (slowFalling.getValue() && !mc.player.hasEffect(MobEffects.SLOW_FALLING)) {
                throwing = checkThrow(MobEffects.SLOW_FALLING.value());
                if (isThrow()) {
                    throwPotion(MobEffects.SLOW_FALLING.value());
                }
            }
        }
    }

    public void throwPotion(MobEffect targetEffect) {
        int oldSlot = mc.player.getInventory().getSelectedSlot();
        int newSlot;
        if (inventory.getValue() && (newSlot = findPotionInventorySlot(targetEffect)) != -1) {
            Alien.ROTATION.snapAt(Alien.ROTATION.rotationYaw, 90);
            InventoryUtil.inventorySwap(newSlot, mc.player.getInventory().getSelectedSlot());
            sendSequencedPacket(id -> new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, id, Alien.ROTATION.getLastYaw(), Alien.ROTATION.getLastPitch()));
            InventoryUtil.inventorySwap(newSlot, mc.player.getInventory().getSelectedSlot());
            EntityUtil.syncInventory();
            Alien.ROTATION.snapBack();
            delayTimer.reset();
        } else if ((newSlot = findPotion(targetEffect)) != -1) {
            Alien.ROTATION.snapAt(Alien.ROTATION.rotationYaw, 90);
            InventoryUtil.switchToSlot(newSlot);
            sendSequencedPacket(id -> new ServerboundUseItemPacket(InteractionHand.MAIN_HAND, id, Alien.ROTATION.getLastYaw(), Alien.ROTATION.getLastPitch()));
            InventoryUtil.switchToSlot(oldSlot);
            Alien.ROTATION.snapBack();
            delayTimer.reset();
        }
    }

    public boolean isThrow() {
        return throwing;
    }

    public boolean checkThrow(MobEffect targetEffect) {
        if (!EntityUtil.inInventory()) {
            return false;
        }
        if (usingPause.getValue() && mc.player.isUsingItem()) {
            return false;
        }
        return findPotion(targetEffect) != -1 || (inventory.getValue() && findPotionInventorySlot(targetEffect) != -1);
    }

    public class AutoPotTick {

        @EventListener
        public void onTick(ClientTickEvent event) {
            if (nullCheck() || event.isPost()) return;
            if (inventory.getValue() && !EntityUtil.inInventory()) return;
            if (mc.gui.screen() == null) {
                if (resistanceKey.isPressed()) {
                    if (!turtlePress) {
                        if (checkThrow(MobEffects.RESISTANCE.value())) {
                            throwPotion(MobEffects.RESISTANCE.value());
                            turtlePress = true;
                            return;
                        }
                    }
                } else {
                    turtlePress = false;
                }

                if (strengthKey.isPressed()) {
                    if (!strengthPress) {
                        if (checkThrow(MobEffects.STRENGTH.value())) {
                            throwPotion(MobEffects.STRENGTH.value());
                            strengthPress = true;
                            return;
                        }
                    }
                } else {
                    strengthPress = false;
                }

                if (speedKey.isPressed()) {
                    if (!speedPress) {
                        if (checkThrow(MobEffects.SPEED.value())) {
                            throwPotion(MobEffects.SPEED.value());
                            speedPress = true;
                        }
                    }
                } else {
                    speedPress = false;
                }
            } else {
                speedPress = false;
                turtlePress = false;
                strengthPress = false;
            }
        }
    }
}
