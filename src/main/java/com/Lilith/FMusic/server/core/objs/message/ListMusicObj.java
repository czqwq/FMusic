package com.Lilith.FMusic.server.core.objs.message;

public class ListMusicObj {

    public Msg get;
    public Msg head;
    public Msg item;

    public static ListMusicObj make() {
        ListMusicObj obj = new ListMusicObj();
        obj.init();

        return obj;
    }

    public void init() {
        head = Msg.prefixed("fmusic.message.listMusic.head", MsgColor.YELLOW)
            .arg(ARG.count);
        item = Msg.plain("fmusic.message.listMusic.item", MsgColor.YELLOW)
            .arg(ARG.index)
            .arg(ARG.musicName)
            .arg(ARG.musicAuthor)
            .arg(ARG.musicAl)
            .arg(ARG.musicAlia)
            .arg(ARG.player);
        get = Msg.prefixed("fmusic.message.listMusic.get", MsgColor.YELLOW)
            .arg(ARG.name);
    }
}
