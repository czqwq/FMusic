package com.Lilith.FMusic.server.core.objs.message;

public class CostObj {

    public Msg addMusic;
    public Msg costFail;
    public Msg noMoney;
    public Msg search;

    public static CostObj make() {
        CostObj obj = new CostObj();
        obj.init();

        return obj;
    }

    public void init() {
        search = Msg.prefixed("fmusic.message.cost.search", MsgColor.YELLOW)
            .arg(ARG.cost);
        addMusic = Msg.prefixed("fmusic.message.cost.addMusic", MsgColor.YELLOW)
            .arg(ARG.cost);
        noMoney = Msg.prefixed("fmusic.message.cost.noMoney", MsgColor.RED);
        costFail = Msg.prefixed("fmusic.message.cost.costFail", MsgColor.RED);
    }
}
