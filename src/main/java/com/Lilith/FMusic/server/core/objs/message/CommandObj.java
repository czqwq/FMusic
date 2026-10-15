package com.Lilith.FMusic.server.core.objs.message;

public class CommandObj {

    public Msg error;
    public Msg noPer;

    public static CommandObj make() {
        CommandObj obj = new CommandObj();
        obj.init();

        return obj;
    }

    public void init() {
        error = Msg.prefixed("fmusic.message.command.error", MsgColor.RED);
        noPer = Msg.prefixed("fmusic.message.command.noPer", MsgColor.RED);
    }
}
