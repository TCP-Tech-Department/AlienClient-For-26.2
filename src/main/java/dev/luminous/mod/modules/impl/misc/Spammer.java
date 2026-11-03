package dev.luminous.mod.modules.impl.misc;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.api.utils.math.Timer;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import dev.luminous.mod.modules.settings.impl.StringSetting;
// 26.2：Yarn PlayerListEntry → Mojang PlayerInfo
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.*;

public class Spammer extends Module {

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    public final BooleanSetting checkSelf =
            add(new BooleanSetting("CheckSelf", false));
    final StringSetting message = add(new StringSetting("Message", "最强外挂Alien社区版免费试用 群\uD835\uDFF1\uD835\uDFF4\uD835\uDFF5\uD835\uDFED\uD835\uDFF5\uD835\uDFED\uD835\uDFF1\uD835\uDFF2\uD835\uDFED"));
    private final Random random = new Random();
    private final SliderSetting randoms =
            add(new SliderSetting("Random", 3, 0, 20, 1));
    private final SliderSetting delay =
            add(new SliderSetting("Delay", 5, 0, 60, 0.1).setSuffix("s"));
    private final BooleanSetting tellMode =
            add(new BooleanSetting("RandomWhisper", false));
    private final BooleanSetting autoDisable =
            add(new BooleanSetting("AutoDisable", true));
    private final Timer timer = new Timer();

    public Spammer() {
        super("Spammer", Category.Misc);
        setChinese("自动刷屏");
    }

    @Override
    public void onLogout() {
        if (autoDisable.getValue()) {
            disable();
        }
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (!timer.passedS(delay.getValue())) return;
        timer.reset();
        String randomString = generateRandomString(randoms.getValueInt());
        if (!randomString.isEmpty()) {
            randomString = " " + randomString;
        }
        if (tellMode.getValue()) {
            // 26.2：getPlayerList() → getOnlinePlayers()，PlayerListEntry → PlayerInfo
            Collection<PlayerInfo> players = mc.getConnection().getOnlinePlayers();
            List<PlayerInfo> list = new ArrayList<>(players);
            int size = list.size();
            if (size == 0) {
                return;
            }
            PlayerInfo playerListEntry = list.get(random.nextInt(size));
            int i = 0;
            // 26.2：authlib GameProfile 仍是普通类，用 getName()
            while (checkSelf.getValue() && Objects.equals(playerListEntry.getProfile().name(), mc.player.getGameProfile().name())) {
                if (i > 50) return;
                i++;
                playerListEntry = list.get(random.nextInt(size));
            }
            // 26.2：sendChatCommand → sendCommand
            mc.getConnection().sendCommand("tell " + playerListEntry.getProfile().name() + " " + message.getValue() + randomString);
        } else {
            if (message.getValue().startsWith("/")) {
                mc.getConnection().sendCommand(message.getValue().replaceFirst("/", "") + randomString);
            } else {
                // 26.2：sendChatMessage → sendChat
                mc.getConnection().sendChat(message.getValue() + randomString);
            }
        }
    }

    private String generateRandomString(int LENGTH) {
        StringBuilder sb = new StringBuilder(LENGTH);

        for (int i = 0; i < LENGTH; i++) {
            int index = random.nextInt(CHARACTERS.length());
            sb.append(CHARACTERS.charAt(index));
        }

        return sb.toString();
    }
}
