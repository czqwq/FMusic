package com.Lilith.FMusic.server.core.objs.message;

public class FunObj {

    public Msg rain;

    public static FunObj make() {
        FunObj obj = new FunObj();
        obj.init();

        return obj;
    }

    public void init() {
        rain = Msg.prefixed("fmusic.message.fun.rain", MsgColor.YELLOW);
    }
}
