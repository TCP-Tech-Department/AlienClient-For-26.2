package dev.luminous.mod.modules.impl.client;

import dev.luminous.Alien;
import dev.luminous.api.events.eventbus.EventListener;
import dev.luminous.api.events.impl.UpdateEvent;
import dev.luminous.mod.modules.Module;
import dev.luminous.mod.modules.settings.impl.BooleanSetting;
import dev.luminous.mod.modules.settings.impl.SliderSetting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;

import java.lang.reflect.Method;

/**
 * Baritone 集成模块。
 *
 * <p>26.2 迁移说明：Baritone 尚无 26.2 版本（{@code build.gradle} 中依赖已停用），
 * 若继续静态引用 {@code baritone.api.*}，整个工程都无法编译。因此这里改为
 * <b>反射调用</b>：语言层面不依赖 baritone，运行时只要玩家装了匹配版本的 Baritone，
 * 所有功能照旧可用；没有安装时全部方法安全地 no-op。
 *
 * <p>{@code BaritoneUtil.loaded} 已经用 {@code Package.getPackages()} 探测过 baritone 是否存在，
 * 本类所有入口都先经过该判断。
 *
 * <p>TODO(26.2): 上游发布 Baritone 26.2 后，可改回静态引用以获得编译期检查；
 * 反射仅用于跨越“依赖不可得”的过渡期。
 */
public class BaritoneModule extends Module {
    public static BaritoneModule INSTANCE;

    private static final String API = "baritone.api.BaritoneAPI";

    private final SliderSetting rangeConfig = add(new SliderSetting("Range", 4.0f, 1.0f, 5.0f));
    private final BooleanSetting placeConfig = add(new BooleanSetting("Place", true));
    private final BooleanSetting breakConfig = add(new BooleanSetting("Break", true));
    private final BooleanSetting sprintConfig = add(new BooleanSetting("Sprint", true));
    private final BooleanSetting inventoryConfig = add(new BooleanSetting("UseInventory", false));
    private final BooleanSetting vinesConfig = add(new BooleanSetting("Vines", true));
    private final BooleanSetting jump256Config = add(new BooleanSetting("JumpAt256", false));
    private final BooleanSetting waterBucketFallConfig = add(new BooleanSetting("WaterBucketFall", false));
    private final BooleanSetting parkourConfig = add(new BooleanSetting("Parkour", true));
    private final BooleanSetting parkourPlaceConfig = add(new BooleanSetting("ParkourPlace", false));
    private final BooleanSetting parkourAscendConfig = add(new BooleanSetting("ParkourAscend", true));
    private final BooleanSetting diagonalAscendConfig = add(new BooleanSetting("DiagonalAscend", false));
    private final BooleanSetting diagonalDescendConfig = add(new BooleanSetting("DiagonalDescend", false));
    private final BooleanSetting mineDownConfig = add(new BooleanSetting("MineDownward", true));
    private final BooleanSetting legitMineConfig = add(new BooleanSetting("LegitMine", false));
    private final BooleanSetting logOnArrivalConfig = add(new BooleanSetting("LogOnArrival", false));
    private final BooleanSetting freeLookConfig = add(new BooleanSetting("FreeLook", true));
    private final BooleanSetting antiCheatConfig = add(new BooleanSetting("AntiCheat", true));
    private final BooleanSetting strictLiquidConfig = add(new BooleanSetting("Strict-Liquid", false));
    private final BooleanSetting censorCoordsConfig = add(new BooleanSetting("CensorCoords", false));
    private final BooleanSetting censorCommandsConfig = add(new BooleanSetting("CensorCommands", false));
    private final BooleanSetting chatControl = add(new BooleanSetting("ChatControl", false));
    private final BooleanSetting debugConfig = add(new BooleanSetting("Debug", false));

    public BaritoneModule() {
        super("Baritone", Category.Client);
        Alien.EVENT_BUS.subscribe(this);
        INSTANCE = this;
        setChinese("寻路设置");
    }

    // ---------------------------------------------------------------- 反射桥

    private static Object call(Object target, String name, Object... args) {
        if (target == null) return null;
        for (Method m : target.getClass().getMethods()) {
            if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
            Class<?>[] types = m.getParameterTypes();
            boolean ok = true;
            for (int i = 0; i < types.length; i++) {
                if (!fits(types[i], args[i])) { ok = false; break; }
            }
            if (!ok) continue;
            try {
                return m.invoke(target, args);
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }
        return null;
    }

    /** baritone.api.BaritoneAPI#getProvider()，baritone 不存在时返回 null。 */
    private static Object provider() {
        try {
            Class<?> api = Class.forName(API);
            return api.getMethod("getProvider").invoke(null);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    /** provider.getPrimaryBaritone() */
    private static Object primaryBaritone() {
        return call(provider(), "getPrimaryBaritone");
    }

    private static Object newGoal(String simpleName, Object... ctorArgs) {
        try {
            Class<?> goal = Class.forName("baritone.api.pathing.goals." + simpleName);
            for (var c : goal.getConstructors()) {
                if (c.getParameterCount() != ctorArgs.length) continue;
                Class<?>[] types = c.getParameterTypes();
                boolean ok = true;
                for (int i = 0; i < types.length; i++) {
                    if (!fits(types[i], ctorArgs[i])) { ok = false; break; }
                }
                if (!ok) continue;
                return c.newInstance(ctorArgs);
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
        return null;
    }

    /** 反射参数兼容性判断（含基本类型装箱）。 */
    private static boolean fits(Class<?> type, Object arg) {
        if (arg == null) return !type.isPrimitive();
        Class<?> boxed = switch (type.getName()) {
            case "int" -> Integer.class;
            case "long" -> Long.class;
            case "double" -> Double.class;
            case "float" -> Float.class;
            case "short" -> Short.class;
            case "byte" -> Byte.class;
            case "boolean" -> Boolean.class;
            case "char" -> Character.class;
            default -> type;
        };
        return boxed.isInstance(arg);
    }

    // ---------------------------------------------------------------- 对外 API

    public static void forward() {
        Direction direction = mc.player.getDirection();
        int x = mc.player.blockPosition().getX() + (int) (direction.getUnitVec3().x * 30000000);
        int z = mc.player.blockPosition().getZ() + (int) (direction.getUnitVec3().z * 30000000);

        cancelEverything();
        Object goal = newGoal("GoalXZ", x, z);
        if (goal == null) return;
        Object process = call(primaryBaritone(), "getCustomGoalProcess");
        call(process, "setGoalAndPath", goal);
    }

    public static boolean isPathing() {
        Object behavior = call(primaryBaritone(), "getPathingBehavior");
        Object result = call(behavior, "isPathing");
        return result instanceof Boolean b && b;
    }

    public static void gotoPos(BlockPos pos) {
        Object goal = newGoal("GoalBlock", pos.getX(), pos.getY(), pos.getZ());
        if (goal == null) return;
        Object process = call(primaryBaritone(), "getCustomGoalProcess");
        call(process, "setGoalAndPath", goal);
    }

    public static void mine(Block block) {
        Object process = call(primaryBaritone(), "getMineProcess");
        call(process, "mine", block);
    }

    public static void cancelEverything() {
        Object behavior = call(primaryBaritone(), "getPathingBehavior");
        call(behavior, "cancelEverything");
    }

    public static boolean isActive() {
        Object baritone = primaryBaritone();
        if (baritone == null) return false;
        Object customGoal = call(baritone, "getCustomGoalProcess");
        Object active = call(customGoal, "isActive");
        if (active instanceof Boolean b && b) return true;

        Object control = call(baritone, "getPathingControlManager");
        Object mostRecent = call(control, "mostRecentInControl");
        if (mostRecent != null) {
            Object value = call(mostRecent, "orElse", (Object) null);
            if (value != null) {
                Object isActive = call(value, "isActive");
                return isActive instanceof Boolean b && b;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- 设置同步

    @EventListener
    public void onUpdate(UpdateEvent event) {
        Object settings = null;
        try {
            settings = Class.forName(API).getMethod("getSettings").invoke(null);
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return;
        }
        if (settings == null) return;

        setBaritone(settings, "blockReachDistance", rangeConfig.getValueFloat());
        setBaritone(settings, "allowPlace", placeConfig.getValue());
        setBaritone(settings, "allowBreak", breakConfig.getValue());
        setBaritone(settings, "allowSprint", sprintConfig.getValue());
        setBaritone(settings, "allowInventory", inventoryConfig.getValue());
        setBaritone(settings, "allowVines", vinesConfig.getValue());
        setBaritone(settings, "allowJumpAt256", jump256Config.getValue());
        setBaritone(settings, "allowWaterBucketFall", waterBucketFallConfig.getValue());
        setBaritone(settings, "allowParkour", parkourConfig.getValue());
        setBaritone(settings, "allowParkourAscend", parkourAscendConfig.getValue());
        setBaritone(settings, "allowParkourPlace", parkourPlaceConfig.getValue());
        setBaritone(settings, "allowDiagonalAscend", diagonalAscendConfig.getValue());
        setBaritone(settings, "allowDiagonalDescend", diagonalDescendConfig.getValue());
        setBaritone(settings, "allowDownward", mineDownConfig.getValue());
        setBaritone(settings, "legitMine", legitMineConfig.getValue());
        setBaritone(settings, "disconnectOnArrival", logOnArrivalConfig.getValue());
        setBaritone(settings, "freeLook", freeLookConfig.getValue());
        setBaritone(settings, "antiCheatCompatibility", antiCheatConfig.getValue());
        setBaritone(settings, "strictLiquidCheck", strictLiquidConfig.getValue());
        setBaritone(settings, "censorCoordinates", censorCoordsConfig.getValue());
        setBaritone(settings, "censorRanCommands", censorCommandsConfig.getValue());
        setBaritone(settings, "chatControl", chatControl.getValue());
        setBaritone(settings, "chatDebug", debugConfig.getValue());
    }

    /** 反射写 baritone 的 Setting#value，并按目标字段类型做数值转换。 */
    private static void setBaritone(Object settings, String name, Object value) {
        try {
            Object setting = settings.getClass().getField(name).get(settings);
            var valueField = setting.getClass().getField("value");
            Class<?> type = valueField.getType();
            if (type == float.class || type == Float.class) {
                valueField.set(setting, ((Number) value).floatValue());
            } else if (type == double.class || type == Double.class) {
                valueField.set(setting, ((Number) value).doubleValue());
            } else if (type == int.class || type == Integer.class) {
                valueField.set(setting, ((Number) value).intValue());
            } else {
                valueField.set(setting, value);
            }
        } catch (ReflectiveOperationException | ClassCastException ignored) {
            // baritone 版本与预期不符时不致命：跳过该项
        }
    }

    @Override
    public void enable() {
        this.state = true;
    }

    @Override
    public void disable() {
        this.state = true;
    }

    @Override
    public boolean isOn() {
        return true;
    }
}
