package com.Lilith.FMusic.server.core.objs.message;

public class CustomObj {

    public Msg idle;
    public Msg info;

    public static CustomObj make() {
        CustomObj obj = new CustomObj();
        obj.init();

        return obj;
    }

    public void init() {
        info = Msg.plain("fmusic.message.custom.info", MsgColor.WHITE);
        idle = Msg.plain("fmusic.message.custom.idle", MsgColor.WHITE);
    }
}
