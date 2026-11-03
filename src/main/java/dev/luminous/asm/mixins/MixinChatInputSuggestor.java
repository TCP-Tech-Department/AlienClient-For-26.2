package dev.luminous.asm.mixins;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.luminous.Alien;
import dev.luminous.api.utils.render.Render2DUtil;
import dev.luminous.mod.commands.Command;
import dev.luminous.mod.modules.impl.client.ClientSetting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.apache.commons.lang3.StringUtils;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mixin(CommandSuggestions.class) // 26.2: was ChatInputSuggestor
public abstract class MixinChatInputSuggestor {
    @Final
    @Shadow
    EditBox input; // 26.2: was TextFieldWidget textField
    @Shadow
    private CompletableFuture<Suggestions> pendingSuggestions;
    @Final
    @Shadow
    private List<FormattedCharSequence> commandUsage; // 26.2: was List<OrderedText> messages
    @Unique
    private boolean showOutline = false;

    // 26.2: show(boolean) -> showSuggestions(boolean)
    @Shadow
    public abstract void showSuggestions(boolean narrateFirstSuggestion);

    // 26.2: was method = "render"
    @Inject(at = {@At(value = "HEAD")}, method = "extractRenderState")
    private void onRender(GuiGraphicsExtractor context, int mouseX, int mouseY, CallbackInfo ci) {
        if (showOutline) {

            int x = input.getX() - 3;
            int y = input.getY() - 3;
            //Up
            Render2DUtil.drawRect(context, x, y, input.getWidth() + 1, 1, ClientSetting.INSTANCE.color.getValue().getRGB());

            //Down
            Render2DUtil.drawRect(context, x, y + input.getHeight() + 1, input.getWidth() + 1, 1, ClientSetting.INSTANCE.color.getValue().getRGB());

            //Left
            Render2DUtil.drawRect(context, x, y, 1, input.getHeight() + 1, ClientSetting.INSTANCE.color.getValue().getRGB());

            //Right
            Render2DUtil.drawRect(context, x + input.getWidth() + 1, y, 1, input.getHeight() + 2, ClientSetting.INSTANCE.color.getValue().getRGB());
        }
    }

    // 26.2: was method = "refresh()V" + TextFieldWidget#getCursor；现在为 updateCommandInfo() + EditBox#getCursorPosition
    @Inject(at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/EditBox;getCursorPosition()I", ordinal = 0)}, method = "updateCommandInfo()V")
    private void onRefresh(CallbackInfo ci) {
        String prefix = Alien.getPrefix();
        String string = this.input.getValue(); // 26.2: was getText

        showOutline = string.startsWith(prefix);

        if (!string.isEmpty()) {
            int cursorPos = this.input.getCursorPosition(); // 26.2: was getCursor
            String string2 = string.substring(0, cursorPos);

            if (prefix.startsWith(string2) || string2.startsWith(prefix)) {
                int j = 0;
                Matcher matcher = Pattern.compile("(\\s+)").matcher(string2);
                while (matcher.find()) {
                    j = matcher.end();
                }

                SuggestionsBuilder builder = new SuggestionsBuilder(string2, j);
                if (string2.length() < prefix.length()) {
                    if (prefix.startsWith(string2)) {
                        builder.suggest(prefix);
                    } else {
                        return;
                    }
                } else if (string2.startsWith(prefix)) {
                    int count = StringUtils.countMatches(string2, " ");
                    List<String> seperated = Arrays.asList(string2.split(" "));
                    if (count == 0) {
                        for (Object strObj : Alien.COMMAND.getCommands().keySet().toArray()) {
                            String str = (String) strObj;
                            builder.suggest(Alien.getPrefix() + str + " ");
                        }
                    } else {
                        if (seperated.isEmpty()) return;
                        Command c = Alien.COMMAND.getCommandBySyntax(seperated.getFirst().substring(prefix.length()));
                        if (c == null) {
                            // 26.2: OrderedText -> FormattedCharSequence，asOrderedText() -> getVisualOrderText()
                            commandUsage.add(Component.literal(" §4no commands found: §f" + seperated.getFirst().substring(prefix.length())).getVisualOrderText());
                            return;
                        }

                        String[] suggestions = c.getAutocorrect(count, seperated);

                        if (suggestions == null || suggestions.length == 0) return;
                        for (String str : suggestions) {
                            builder.suggest(str + " ");
                        }
                    }
                } else {
                    return;
                }

                this.pendingSuggestions = builder.buildFuture();
                this.showSuggestions(false);
            }
        }
    }
}
