package com.Lilith.FMusic.server.core.objs.message;

public class CancelObj {

    public Msg done;
    public Msg err1;
    public Msg err2;
    public Msg err3;
    public Msg err4;

    public static CancelObj make() {
        CancelObj obj = new CancelObj();
        obj.init();

        return obj;
    }

    public void init() {
        err1 = Msg.prefixed("fmusic.message.cancel.err1", MsgColor.RED);
        err2 = Msg.prefixed("fmusic.message.cancel.err2", MsgColor.RED)
            .arg(ARG.musicName)
            .arg(ARG.musicAuthor);
        err3 = Msg.prefixed("fmusic.message.cancel.err3", MsgColor.RED)
            .arg(ARG.index);
        err4 = Msg.prefixed("fmusic.message.cancel.err4", MsgColor.RED);
        done = Msg.prefixed("fmusic.message.cancel.done", MsgColor.YELLOW);
    }
}
