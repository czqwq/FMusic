package com.Lilith.FMusic.server.core.objs.message;

public class VoteObj {

    public Msg agree;
    public Msg arAgree;
    public Msg bq;
    public Msg bq1;
    public Msg bq2;
    public Msg bqAgree;
    public Msg cancel;
    public Msg cancel1;
    public Msg doVote;
    public Msg err1;
    public Msg err3;
    public Msg err4;
    public Msg err5;
    public Msg list;
    public Msg next;
    public Msg noPermission;
    public Msg timeOut;

    public static VoteObj make() {
        VoteObj obj = new VoteObj();
        obj.init();

        return obj;
    }

    public void init() {
        noPermission = Msg.prefixed("fmusic.message.vote.noPermission", MsgColor.RED);
        doVote = Msg.prefixed("fmusic.message.vote.doVote", MsgColor.YELLOW);
        bq = Msg.prefixed("fmusic.message.vote.bq", MsgColor.YELLOW)
            .arg(ARG.player)
            .arg(ARG.time)
            .arg(ARG.countAll);
        bq1 = Msg.prefixed("fmusic.message.vote.bq1", MsgColor.YELLOW);
        bq2 = Msg.plain("fmusic.message.vote.bq2", MsgColor.GREEN);
        agree = Msg.prefixed("fmusic.message.vote.agree", MsgColor.YELLOW);
        bqAgree = Msg.prefixed("fmusic.message.vote.bqAgree", MsgColor.YELLOW)
            .arg(ARG.player)
            .arg(ARG.count)
            .arg(ARG.countAll);
        arAgree = Msg.prefixed("fmusic.message.vote.arAgree", MsgColor.YELLOW);
        timeOut = Msg.prefixed("fmusic.message.vote.timeOut", MsgColor.YELLOW)
            .arg(ARG.count)
            .arg(ARG.countAll);
        err1 = Msg.prefixed("fmusic.message.vote.err1", MsgColor.RED);
        err3 = Msg.prefixed("fmusic.message.vote.err3", MsgColor.RED);
        err4 = Msg.prefixed("fmusic.message.vote.err4", MsgColor.RED);
        err5 = Msg.prefixed("fmusic.message.vote.err5", MsgColor.RED);
        next = Msg.prefixed("fmusic.message.vote.next", MsgColor.YELLOW);
        cancel = Msg.prefixed("fmusic.message.vote.cancel", MsgColor.YELLOW);
        cancel1 = Msg.prefixed("fmusic.message.vote.cancel1", MsgColor.YELLOW);
        list = Msg.prefixed("fmusic.message.vote.list", MsgColor.YELLOW)
            .arg(ARG.count);
    }
}
