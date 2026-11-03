package dev.luminous.mod.modules.impl.client;

import dev.luminous.mod.modules.Module;

public class IRC extends Module {


    public IRC() {
        super("IRC", Category.Client);
    }


    /*
        已清理：

        - Socket连接
        - 47.121.113.160:6667
        - 用户名上传
        - userId上传
        - IRC远程聊天
        - !消息拦截

        此模块现在为空模块，
        仅用于兼容 ModuleManager 引用。
    */


    @Override
    public void onEnable() {
        // 禁用远程IRC功能
        disable();
    }


    @Override
    public void onDisable() {

    }


    @Override
    public void onLogin() {

    }


    @Override
    public void onLogout() {

    }

}