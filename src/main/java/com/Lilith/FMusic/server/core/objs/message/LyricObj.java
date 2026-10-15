package com.Lilith.FMusic.server.core.objs.message;

public class LyricObj {

    public String data;
    public Msg empty1;
    public Msg empty2;
    public Msg empty3;
    public String tdata;

    public static LyricObj make() {
        LyricObj obj = new LyricObj();
        obj.init();

        return obj;
    }

    public void init() {
        // 歌词拼装模板 (纯结构, 不翻译): 由调用方 replace(ARG.lyric, ...) 后使用
        data = ARG.lyric;
        tdata = ARG.lyric + "(" + ARG.lyric + ")";
        empty1 = Msg.plain("fmusic.message.lyric.empty1", MsgColor.WHITE);
        empty2 = Msg.plain("fmusic.message.lyric.empty2", MsgColor.WHITE);
        empty3 = Msg.plain("fmusic.message.lyric.empty3", MsgColor.WHITE);
    }
}
