package com.Lilith.FMusic.server.core.objs.message;

public class PageObj {

    public Msg choice;
    public Msg last;
    public Msg next;

    public static PageObj make() {
        PageObj obj = new PageObj();
        obj.init();

        return obj;
    }

    public void init() {
        choice = Msg.plain("fmusic.message.page.choice", MsgColor.YELLOW)
            .arg(ARG.index)
            .arg(ARG.musicName)
            .arg(ARG.musicAuthor)
            .arg(ARG.musicAl);
        next = Msg.plain("fmusic.message.page.next", MsgColor.YELLOW);
        last = Msg.plain("fmusic.message.page.last", MsgColor.YELLOW);
    }
}
