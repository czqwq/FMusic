package com.Lilith.FMusic.server.core.objs.message;

public class PushObj {

    public Msg bq;
    public Msg bq1;
    public Msg bq2;
    public Msg cancel;
    public Msg cancel1;
    public Msg doPush;
    public Msg doVote;
    public Msg err1;
    public Msg err3;
    public Msg err4;
    public Msg err5;
    public Msg noId;
    public Msg noId1;
    public Msg noPermission;
    public Msg pushErr;

    public static PushObj make() {
        PushObj obj = new PushObj();
        obj.init();

        return obj;
    }

    public void init() {
        noPermission = Msg.prefixed("fmusic.message.push.noPermission", MsgColor.RED);
        doVote = Msg.prefixed("fmusic.message.push.doVote", MsgColor.YELLOW);
        bq = Msg.prefixed("fmusic.message.push.bq", MsgColor.YELLOW)
            .arg(ARG.player)
            .arg(ARG.musicName)
            .arg(ARG.musicAuthor)
            .arg(ARG.time)
            .arg(ARG.countAll);
        bq1 = Msg.prefixed("fmusic.message.push.bq1", MsgColor.YELLOW);
        bq2 = Msg.plain("fmusic.message.push.bq2", MsgColor.GREEN);
        doPush = Msg.prefixed("fmusic.message.push.doPush", MsgColor.YELLOW);
        noId = Msg.prefixed("fmusic.message.push.noId", MsgColor.RED);
        noId1 = Msg.prefixed("fmusic.message.push.noId1", MsgColor.RED)
            .arg(ARG.index);
        pushErr = Msg.prefixed("fmusic.message.push.pushErr", MsgColor.RED);
        err1 = Msg.prefixed("fmusic.message.push.err1", MsgColor.RED);
        err3 = Msg.prefixed("fmusic.message.push.err3", MsgColor.RED);
        err4 = Msg.prefixed("fmusic.message.push.err4", MsgColor.RED);
        err5 = Msg.prefixed("fmusic.message.push.err5", MsgColor.RED);
        cancel = Msg.prefixed("fmusic.message.push.cancel", MsgColor.YELLOW);
        cancel1 = Msg.prefixed("fmusic.message.push.cancel1", MsgColor.YELLOW);
    }
}
