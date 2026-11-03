package dev.luminous;


import by.radioegor146.nativeobfuscator.Native;
import dev.luminous.api.events.eventbus.EventBus;
import dev.luminous.api.events.impl.InitEvent;
import dev.luminous.core.impl.*;
import dev.luminous.mod.modules.impl.client.ClientSetting;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.Minecraft;


import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.*;


public class Alien implements ModInitializer {


    public static final String NAME = "Alien";
    public static final String VERSION = "4.0";


    public static final EventBus EVENT_BUS = new EventBus();


    public static HoleManager HOLE;
    public static PlayerManager PLAYER;
    public static TradeManager TRADE;
    public static CleanerManager CLEANER;
    public static XrayManager XRAY;

    public static ModuleManager MODULE;
    public static CommandManager COMMAND;
    public static ConfigManager CONFIG;

    public static RotationManager ROTATION;
    public static BreakManager BREAK;

    public static PopManager POP;
    public static FriendManager FRIEND;

    public static TimerManager TIMER;

    public static ShaderManager SHADER;
    public static BlurManager BLUR;

    public static FPSManager FPS;

    public static ServerManager SERVER;

    public static ThreadManager THREAD;


    public static boolean loaded = false;

    public static long initTime;


    /*
        已删除：

        - HWID收集
        - CPU信息采集
        - 主板序列号读取
        - Socket远程验证
        - 47.121.113.160:12345
        - AES授权通信
        - 用户身份上传

    */



    public static String userId = "local";


    public static String getPrefix() {
        return ClientSetting.INSTANCE.prefix.getValue();
    }



    public static void save() {


        System.out.println(
                "[" + Alien.NAME + "] Saving"
        );


        CONFIG.save();

        CLEANER.save();

        FRIEND.save();

        XRAY.save();

        TRADE.save();


        System.out.println(
                "[" + Alien.NAME + "] Saved"
        );

    }



    private void register() {


        EVENT_BUS.registerLambdaFactory(
                (lookupInMethod, klass) ->
                        (MethodHandles.Lookup)
                                lookupInMethod.invoke(
                                        null,
                                        klass,
                                        MethodHandles.lookup()
                                )
        );


        Runtime.getRuntime().addShutdownHook(
                new Thread(() -> {

                    if (loaded) {
                        save();
                    }

                })
        );
    }





    @Native
    @Override
    public void onInitialize() {


        register();



        CONFIG = new ConfigManager();

        HOLE = new HoleManager();

        MODULE = new ModuleManager();

        COMMAND = new CommandManager();

        FRIEND = new FriendManager();

        XRAY = new XrayManager();

        CLEANER = new CleanerManager();

        TRADE = new TradeManager();

        ROTATION = new RotationManager();

        BREAK = new BreakManager();

        PLAYER = new PlayerManager();

        POP = new PopManager();

        TIMER = new TimerManager();

        SHADER = new ShaderManager();

        BLUR = new BlurManager();

        FPS = new FPSManager();


        SERVER = new ServerManager();


        CONFIG.load();


        THREAD = new ThreadManager();



        initTime = System.currentTimeMillis();


        loaded = true;


        Alien.EVENT_BUS.post(
                new InitEvent()
        );



        File folder =
                new File(
                        Minecraft
                                .getInstance()
                                .gameDirectory
                                .getPath()
                                +
                                File.separator
                                +
                                Alien.NAME.toLowerCase()
                                +
                                File.separator
                                +
                                "cfg"
                );


        if (!folder.exists()) {

            folder.mkdirs();

        }


    }

}