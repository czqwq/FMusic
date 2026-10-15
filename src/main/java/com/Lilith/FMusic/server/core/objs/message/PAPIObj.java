package com.Lilith.FMusic.server.core.objs.message;

public class PAPIObj {

    public Msg emptyMusic;

    public static PAPIObj make() {
        PAPIObj obj = new PAPIObj();
        obj.init();

        return obj;
    }

    public void init() {
        emptyMusic = Msg.plain("fmusic.message.pAPI.emptyMusic", MsgColor.WHITE);
    }
}
