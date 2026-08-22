package com.Lilith.FMusic.server.core.objs.message;

public class CommandObj {

    public String error;
    public String noPer;

    public static CommandObj make() {
        CommandObj obj = new CommandObj();
        obj.init();

        return obj;
    }

    public boolean check() {
        if (error == null) return true;
        return noPer == null;
    }

    public void init() {
        if (error == null) error = "<gold>[FMusic]<red>参数错误，请输入/music help获取帮助";
        if (noPer == null) noPer = "<gold>[FMusic]<red>你没有权限执行这个操作";
    }
}
