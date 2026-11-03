package dev.luminous.mod.modules.impl.render;

import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.PacketEvent;
import dev.luminous.api.events.impl.PreRender2DEvent;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.ColorSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.world.clock.ClockNetworkState;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.timeline.Timeline;

import java.awt.*;
import java.util.Map;
import java.util.Optional;

public class Ambience extends Module {

    public static Ambience INSTANCE;
    public final ColorSetting filter = add(new ColorSetting("Filter", new Color(255, 255, 255, 20)).injectBoolean(false));
    public final ColorSetting worldColor = add(new ColorSetting("WorldColor", new Color(0xFFFFFFFF, true)).injectBoolean(true));
    public final BooleanSetting customTime =
            add(new BooleanSetting("CustomTime", false).setParent());
    public final SliderSetting time =
            add(new SliderSetting("Time", 0, 0, 24000, customTime::isOpen));
    public final ColorSetting fog =
            add(new ColorSetting("FogColor", new Color(0xCC7DD5)).injectBoolean(false));
    public final ColorSetting sky =
            add(new ColorSetting("SkyColor", new Color(0x000000)).injectBoolean(false));
    public final ColorSetting cloud =
            add(new ColorSetting("CloudColor", new Color(0x000000)).injectBoolean(false));
    public final ColorSetting dimensionColor =
            add(new ColorSetting("DimensionColor", new Color(0x000000)).injectBoolean(false));
    public final BooleanSetting fogDistance =
            add(new BooleanSetting("FogDistance", false).setParent());
    public final SliderSetting fogStart =
            add(new SliderSetting("FogStart", 50, 0, 1000, fogDistance::isOpen));
    public final SliderSetting fogEnd =
            add(new SliderSetting("FogEnd", 100, 0, 1000, fogDistance::isOpen));
    public final BooleanSetting fullBright =
            add(new BooleanSetting("FullBright", false));
    public final BooleanSetting forceOverworld =
            add(new BooleanSetting("ForceOverworld", false));
    public final BooleanSetting customLuminance =
            add(new BooleanSetting("CustomLuminance", false).setParent().injectTask(() -> {
                if (!nullCheck()) {
                    // 26.2: LevelRenderer.reload() 已移除，改为 LevelExtractor.allChanged()
                    // （原版在切换维度/资源重载时也是走这条路径重建区块渲染数据）
                    mc.levelExtractor.allChanged();
                }
            }));
    public final SliderSetting luminance =
            add(new SliderSetting("Luminance", 15, 0, 15, customLuminance::isOpen).injectTask(() -> {
                if (!nullCheck() && customLuminance.getValue()) {
                    mc.levelExtractor.allChanged();
                }
            }));
    long oldTime;

    public Ambience() {
        super("Ambience", "Custom ambience", Category.Render);
        setChinese("自定义环境");
        INSTANCE = this;
    }

    @EventListener
    public void onRender2D(PreRender2DEvent event) {
        if (filter.booleanValue) {
            event.drawContext.fill(0, 0, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), filter.getValue().getRGB());
        }
    }

    @EventListener
    public void onUpdate(UpdateEvent event) {
        if (customTime.getValue()) {
            setTimeOfDay((long) this.time.getValue());
        }
    }

    @Override
    public void onEnable() {
        if (nullCheck()) return;
        oldTime = currentClockTime();
        if (customTime.getValue()) {
            setTimeOfDay((long) this.time.getValue());
        }
    }

    @Override
    public void onDisable() {
        if (nullCheck()) return;
        setClockTime(defaultClock(), oldTime);
    }

    @EventListener
    public void onReceivePacket(PacketEvent.Receive event) {
        if (event.getPacket() instanceof ClientboundSetTimePacket packet && customTime.getValue()) {
            // 26.2: ClientboundSetTimePacket.getTime() → gameTime()，且时间改由 WorldClock 承载；
            // 这里读取该包里本维度时钟的快照作为“旧值”，然后拦包阻止服务端覆盖。
            Holder<WorldClock> clock = defaultClock();
            ClockNetworkState state = clock == null ? null : packet.clockUpdates().get(clock);
            oldTime = state != null ? state.totalTicks() : currentClockTime();
            event.cancel();
        }
    }

    /**
     * 26.2: 旧 {@code Level.setTimeOfDay(long)} 已移除，时间改由 {@code WorldClock} 驱动
     * （{@code Level#getDefaultClockTime()} 只是 {@code clockManager().getTotalTicks(defaultClock)} 的糖，
     * 且没有 default_clock 的维度恒返回 0，不能再用作「当前时间」）。
     * 这里把目标时刻换算成该时钟的 totalTicks 后写回客户端时钟。
     */
    private void setTimeOfDay(long timeOfDay) {
        Holder<WorldClock> clock = defaultClock();
        if (clock == null) return;
        long total = mc.level.clockManager().getTotalTicks(clock);
        setClockTime(clock, total - Math.floorMod(total, 24000L) + timeOfDay);
    }

    private void setClockTime(Holder<WorldClock> clock, long totalTicks) {
        if (clock == null) return;
        mc.level.clockManager().handleUpdates(
                mc.level.getGameTime(),
                Map.of(clock, new ClockNetworkState(totalTicks, 0.0f, 1.0f))
        );
        // 与 ClientPacketListener#handleSetTime 保持一致：清掉环境属性（天空/雾色）的 tick 缓存
        mc.level.environmentAttributes().invalidateTickCache();
    }

    private long currentClockTime() {
        Holder<WorldClock> clock = defaultClock();
        return clock == null ? 0L : mc.level.clockManager().getTotalTicks(clock);
    }

    /**
     * 26.2: 时间不再挂在 {@code ClientLevel} 的 dayTime 字段上（该字段已删除），
     * 而是由 {@code ClockManager} 按 {@code Holder<WorldClock>} 分别记账；
     * 视觉（太阳/月亮角度、天空色）再由 {@code DimensionType#timelines()} 里各条
     * {@link Timeline} 读对应时钟算出来。
     * <p>
     * 所以「强制设置客户端时间」的路径是：拿到本维度实际使用的时钟，写它的 totalTicks。
     * 旧代码只走 {@code DimensionType#defaultClock()}，而 {@code the_nether.json} 根本没有
     * {@code default_clock} 字段，于是旧代码在下界直接放弃。26.2 的正确兜底顺序：
     * <ol>
     *   <li>{@code DimensionType#defaultClock()}：主世界 / 末地；</li>
     *   <li>否则取该维度 {@code timelines} 里第一条 {@code Timeline#clock()}：下界的 timelines 是
     *       {@code #minecraft:in_nether}（只含 universal → villager_schedule → {@code minecraft:overworld}
     *       时钟），至少能保证时间真的写进客户端时钟管理器；</li>
     *   <li>再兜底到注册表里的 {@code WorldClocks.OVERWORLD}，避免返回 null 让整个模块静默失效。</li>
     * </ol>
     */
    private Holder<WorldClock> defaultClock() {
        Optional<Holder<WorldClock>> clock = mc.level.dimensionType().defaultClock();
        if (clock.isPresent()) return clock.get();
        for (Holder<Timeline> timeline : mc.level.dimensionType().timelines()) {
            Holder<WorldClock> timelineClock = timeline.value().clock();
            if (timelineClock != null) return timelineClock;
        }
        return mc.level.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK)
                .get(WorldClocks.OVERWORLD).orElse(null);
    }
}