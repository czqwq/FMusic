package com.Lilith.FMusic.server.core.objs.message;

public class ClickObj {

    public Msg clickCheck;
    public Msg clickRun;

    public static ClickObj make() {
        ClickObj obj = new ClickObj();
        obj.init();

        return obj;
    }

    public void init() {
        clickRun = Msg.plain("fmusic.message.click.clickRun", MsgColor.AQUA);
        clickCheck = Msg.plain("fmusic.message.click.clickCheck", MsgColor.AQUA);
    }
}
