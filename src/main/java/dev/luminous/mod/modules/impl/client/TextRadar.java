package dev.luminous.mod.modules.impl.client;

import dev.luminous.Alien;
import dev.luminous.core.impl.FontManager;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.ColorSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.ChatFormatting;

import java.awt.*;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TextRadar extends Module {
    public static TextRadar INSTANCE;

    public TextRadar() {
        super("TextRadar", Category.Client);
        setChinese("文字雷达");
        INSTANCE = this;
    }

    private final DecimalFormat df = new DecimalFormat("0.0");

    private final BooleanSetting font = add(new BooleanSetting("Font", true));
    private final BooleanSetting shadow = add(new BooleanSetting("Shadow", true));
    private final SliderSetting x = add(new SliderSetting("X", 0, 0, 1500));
    private final SliderSetting y = add(new SliderSetting("Y", 100, 0, 1000));
    private final ColorSetting color = add(new ColorSetting("Color", new Color(255, 255, 255)));
    private final ColorSetting friend = add(new ColorSetting("Friend").injectBoolean(true));
    private final BooleanSetting doubleBlank = add(new BooleanSetting("Double", false));
    private final BooleanSetting health = add(new BooleanSetting("Health", true));
    private final BooleanSetting pops = add(new BooleanSetting("Pops", true));
    public final BooleanSetting red = add(new BooleanSetting("Red", false));
    private final BooleanSetting distance = add(new BooleanSetting("Distance", true));
    private final BooleanSetting effects = add(new BooleanSetting("Effects", true));

    @Override
    public void onRender2D(GuiGraphicsExtractor drawContext, float tickDelta) {
        int currentY = y.getValueInt();
        List<AbstractClientPlayer> players = new ArrayList<>(mc.level.players());

        players.sort(Comparator.comparingDouble((player) -> mc.player.distanceTo(player)));

        for (Player player : players) {
            if (player == mc.player) continue;
            StringBuilder stringBuilder = new StringBuilder();
            String blank = doubleBlank.getValue() ? "  " : " ";
            if (health.getValue()) {
                stringBuilder.append(getHealthColor(player));
                stringBuilder.append(df.format(player.getHealth() + player.getAbsorptionAmount()));
                stringBuilder.append(blank);
            }
            stringBuilder.append(ChatFormatting.RESET);
            stringBuilder.append(player.getName().getString());
            if (distance.getValue()) {
                stringBuilder.append(blank);
                stringBuilder.append(ChatFormatting.WHITE);
                stringBuilder.append(df.format(mc.player.distanceTo(player)));
                stringBuilder.append("m");
            }
            if (effects.getValue()) {
                if (player.hasEffect(MobEffects.SLOWNESS)) {
                    stringBuilder.append(blank);
                    stringBuilder.append(ChatFormatting.GRAY);
                    stringBuilder.append("Lv.");
                    stringBuilder.append(player.getEffect(MobEffects.SLOWNESS).getAmplifier() + 1);
                    stringBuilder.append(blank);
                    stringBuilder.append(player.getEffect(MobEffects.SLOWNESS).getDuration() / 20 + 1);
                    stringBuilder.append("s");
                }
                if (player.hasEffect(MobEffects.SPEED)) {
                    stringBuilder.append(blank);
                    stringBuilder.append(ChatFormatting.AQUA);
                    stringBuilder.append("Lv.");
                    stringBuilder.append(player.getEffect(MobEffects.SPEED).getAmplifier() + 1);
                    stringBuilder.append(blank);
                    stringBuilder.append(player.getEffect(MobEffects.SPEED).getDuration() / 20 + 1);
                    stringBuilder.append("s");
                }
                if (player.hasEffect(MobEffects.STRENGTH)) {
                    stringBuilder.append(blank);
                    stringBuilder.append(ChatFormatting.DARK_RED);
                    stringBuilder.append("Lv.");
                    stringBuilder.append(player.getEffect(MobEffects.STRENGTH).getAmplifier() + 1);
                    stringBuilder.append(blank);
                    stringBuilder.append(player.getEffect(MobEffects.STRENGTH).getDuration() / 20 + 1);
                    stringBuilder.append("s");
                }
                if (player.hasEffect(MobEffects.RESISTANCE)) {
                    stringBuilder.append(blank);
                    stringBuilder.append(ChatFormatting.BLUE);
                    stringBuilder.append("Lv.");
                    stringBuilder.append(player.getEffect(MobEffects.RESISTANCE).getAmplifier() + 1);
                    stringBuilder.append(blank);
                    stringBuilder.append(player.getEffect(MobEffects.RESISTANCE).getDuration() / 20 + 1);
                    stringBuilder.append("s");
                }
            }
            if (pops.getValue()) {
                int totemPopped = Alien.POP.getPop(player);
                if (totemPopped > 0) {
                    stringBuilder.append(blank);
                    stringBuilder.append(getPopColor(totemPopped));
                    stringBuilder.append("-");
                    stringBuilder.append(totemPopped);
                }
            }

            boolean isFriend = Alien.FRIEND.isFriend(player);
            if (isFriend && !friend.booleanValue) continue;
            int color = isFriend ? friend.getValue().getRGB() : this.color.getValue().getRGB();
            if (font.getValue()) {
                FontManager.ui.drawString(drawContext, stringBuilder.toString(), x.getValueInt(), currentY, color, shadow.getValue());
            } else {
                drawContext.text(mc.font, stringBuilder.toString(), x.getValueInt(), currentY, color, shadow.getValue());
            }
            currentY += font.getValue() ? (int) FontManager.ui.getFontHeight() : mc.font.lineHeight;
        }
    }

    public static ChatFormatting getHealthColor(Player player) {
        double health = player.getHealth() + player.getAbsorptionAmount();
        if (health > 18) {
            return ChatFormatting.GREEN;
        } else if (health > 16) {
            return ChatFormatting.DARK_GREEN;
        } else if (health > 12) {
            return ChatFormatting.YELLOW;
        } else if (health > 8) {
            return ChatFormatting.GOLD;
        } else if (health > 4) {
            return ChatFormatting.RED;
        } else {
            return ChatFormatting.DARK_RED;
        }
    }

    public static ChatFormatting getPopColor(int totems) {
        if (INSTANCE.red.getValue()) {
            return ChatFormatting.RED;
        }

        if (totems > 10) {
            return ChatFormatting.DARK_RED;
        } else if (totems > 8) {
            return ChatFormatting.RED;
        } else if (totems > 6) {
            return ChatFormatting.GOLD;
        } else if (totems > 4) {
            return ChatFormatting.YELLOW;
        } else if (totems > 2) {
            return ChatFormatting.DARK_GREEN;
        } else {
            return ChatFormatting.GREEN;
        }
    }
}