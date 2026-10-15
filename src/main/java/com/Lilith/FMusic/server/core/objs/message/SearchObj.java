package com.Lilith.FMusic.server.core.objs.message;

public class SearchObj {

    public Msg cantLast;
    public Msg cantNext;
    public Msg cantSearch;
    public Msg choice;
    public Msg emptySearch;
    public Msg errorNum;
    public Msg lastPage;
    public Msg nextPage;
    public Msg noPer;
    public Msg res;
    public Msg startSearch;

    public static SearchObj make() {
        SearchObj obj = new SearchObj();
        obj.init();

        return obj;
    }

    public void init() {
        startSearch = Msg.prefixed("fmusic.message.search.startSearch", MsgColor.YELLOW);
        noPer = Msg.prefixed("fmusic.message.search.noPer", MsgColor.RED);
        cantSearch = Msg.prefixed("fmusic.message.search.cantSearch", MsgColor.RED)
            .arg(ARG.name);
        res = Msg.prefixed("fmusic.message.search.res", MsgColor.YELLOW);
        emptySearch = Msg.prefixed("fmusic.message.search.emptySearch", MsgColor.RED);
        errorNum = Msg.prefixed("fmusic.message.search.errorNum", MsgColor.RED);
        choice = Msg.prefixed("fmusic.message.search.choice", MsgColor.YELLOW)
            .arg(ARG.index);
        cantNext = Msg.prefixed("fmusic.message.search.cantNext", MsgColor.RED);
        cantLast = Msg.prefixed("fmusic.message.search.cantLast", MsgColor.RED);
        lastPage = Msg.prefixed("fmusic.message.search.lastPage", MsgColor.WHITE);
        nextPage = Msg.prefixed("fmusic.message.search.nextPage", MsgColor.WHITE);
    }
}
