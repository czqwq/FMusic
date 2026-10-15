package com.Lilith.FMusic.server.core.objs.message;

public class HudObj {

    public Msg emptyList;
    public Msg emptyLyric;
    public Msg emptyMusic;
    public String klyric;
    public String lyric;
    public String music;
    public Msg picRotate;
    public Msg picSize;
    public Msg picSpeed;
    public Msg reset;
    public Msg set;
    public Msg set1;
    public Msg set2;
    public Msg set3;
    public Msg set4;
    public Msg set5;
    public Msg set6;
    public Msg set7;
    public Msg state;
    public String tlyric;

    public static HudObj make() {
        HudObj obj = new HudObj();
        obj.init();

        return obj;
    }

    public void init() {
        state = Msg.prefixed("fmusic.message.hud.state", MsgColor.YELLOW)
            .arg(ARG.hud, MsgColor.AQUA)
            .arg(ARG.value, MsgColor.BLUE);
        set = Msg.prefixed("fmusic.message.hud.set", MsgColor.YELLOW)
            .arg(ARG.hud, MsgColor.AQUA)
            .arg(ARG.x, MsgColor.BLUE)
            .arg(ARG.y, MsgColor.BLUE);
        set1 = Msg.prefixed("fmusic.message.hud.set1", MsgColor.YELLOW)
            .arg(ARG.hud, MsgColor.AQUA)
            .arg(ARG.value, MsgColor.BLUE);
        set2 = Msg.prefixed("fmusic.message.hud.set2", MsgColor.YELLOW)
            .arg(ARG.hud, MsgColor.AQUA)
            .arg(ARG.value, MsgColor.BLUE);
        set3 = Msg.prefixed("fmusic.message.hud.set3", MsgColor.YELLOW)
            .arg(ARG.hud, MsgColor.AQUA)
            .arg(ARG.value, MsgColor.BLUE);
        set4 = Msg.prefixed("fmusic.message.hud.set4", MsgColor.YELLOW)
            .arg(ARG.hud, MsgColor.AQUA)
            .arg(ARG.value, MsgColor.BLUE);
        set5 = Msg.prefixed("fmusic.message.hud.set5", MsgColor.YELLOW)
            .arg(ARG.hud, MsgColor.AQUA)
            .arg(ARG.value, MsgColor.BLUE);
        set6 = Msg.prefixed("fmusic.message.hud.set6", MsgColor.YELLOW)
            .arg(ARG.hud, MsgColor.AQUA)
            .arg(ARG.value, MsgColor.BLUE);
        set7 = Msg.prefixed("fmusic.message.hud.set7", MsgColor.YELLOW)
            .arg(ARG.hud, MsgColor.AQUA)
            .arg(ARG.value, MsgColor.BLUE);
        reset = Msg.prefixed("fmusic.message.hud.reset", MsgColor.YELLOW)
            .arg(ARG.hud, MsgColor.AQUA);
        emptyMusic = Msg.plain("fmusic.message.hud.emptyMusic", MsgColor.WHITE);
        emptyList = Msg.plain("fmusic.message.hud.emptyList", MsgColor.WHITE);
        emptyLyric = Msg.plain("fmusic.message.hud.emptyLyric", MsgColor.WHITE);
        picSize = Msg.prefixed("fmusic.message.hud.picSize", MsgColor.YELLOW)
            .arg(ARG.value);
        picRotate = Msg.prefixed("fmusic.message.hud.picRotate", MsgColor.YELLOW)
            .arg(ARG.value);
        picSpeed = Msg.prefixed("fmusic.message.hud.picSpeed", MsgColor.YELLOW)
            .arg(ARG.value);
        // HUD 信息行/歌词是"参数拼装模板" (纯结构, 不需要翻译): 保持 String + ARG 拼接,
        // 由 HudUtils 组装后作为纯文本下发 (服务端语言)
        music = ARG.name + " " + ARG.musicAuthor + "\n" + ARG.musicAlia + "\n" + ARG.musicAl + "\nby: " + ARG.player;
        lyric = ARG.lyric;
        tlyric = ARG.lyric;
        klyric = "§b" + ARG.lyric;
    }
}
