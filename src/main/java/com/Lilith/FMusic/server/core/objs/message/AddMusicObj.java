package com.Lilith.FMusic.server.core.objs.message;

public class AddMusicObj {

    public Msg banMusic;
    public Msg cancel;
    public Msg existMusic;
    public Msg listFull;
    public Msg noID;
    public Msg noPlayer;
    public Msg playerBan;
    public Msg playerToMany;
    public Msg success;
    public Msg timeOut;

    public static AddMusicObj make() {
        AddMusicObj obj = new AddMusicObj();
        obj.init();

        return obj;
    }

    public void init() {
        listFull = Msg.prefixed("fmusic.message.addMusic.listFull", MsgColor.RED);
        playerToMany = Msg.prefixed("fmusic.message.addMusic.playerToMany", MsgColor.RED);
        playerBan = Msg.prefixed("fmusic.message.addMusic.playerBan", MsgColor.RED);
        banMusic = Msg.prefixed("fmusic.message.addMusic.banMusic", MsgColor.RED);
        existMusic = Msg.prefixed("fmusic.message.addMusic.existMusic", MsgColor.RED);
        success = Msg.prefixed("fmusic.message.addMusic.success", MsgColor.WHITE);
        noPlayer = Msg.prefixed("fmusic.message.addMusic.noPlayer", MsgColor.RED);
        noID = Msg.prefixed("fmusic.message.addMusic.noID", MsgColor.RED);
        cancel = Msg.prefixed("fmusic.message.addMusic.cancel", MsgColor.YELLOW);
        timeOut = Msg.prefixed("fmusic.message.addMusic.timeOut", MsgColor.YELLOW);
    }
}
