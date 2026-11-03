package dev.luminous.asm.mixins;

import net.minecraft.client.main.Main;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 26.2: {@code net.minecraft.client.main.Main} 的静态初始化里仍然是
 * {@code System.setProperty("java.awt.headless", "true")}（Main.java:372-373），注入点不变，
 * 只是把 headless 改成 false 以便 AWT 颜色/字体在客户端可用。
 */
@Mixin(Main.class)
public class MixinMain {
    @Redirect(method = "<clinit>", at = @At(value = "INVOKE", target = "Ljava/lang/System;setProperty(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;"), require = 0)
    private static String hookStaticInit(String key, String value) {
        return System.setProperty("java.awt.headless", "false");
    }
}
