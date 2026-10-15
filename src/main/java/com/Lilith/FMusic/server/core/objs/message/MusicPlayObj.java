package com.Lilith.FMusic.server.core.objs.message;

public class MusicPlayObj {

    public Msg addMusic;
    public Msg cancel;
    public Msg checkMusic;
    public Msg emptyCanPlay;
    public Msg emptyPlay;
    public Msg emptyPlayingMusic;
    public Msg error1;
    public Msg error2;
    public ListMusicObj listMusic;
    public String musicInfo;
    public Msg mute;
    public Msg mute1;
    public Msg mute2;
    public Msg mute3;
    public Msg nowPlay;
    public Msg stopPlaying;
    public Msg switchMusic;
    public Msg trail;

    public static MusicPlayObj make() {
        MusicPlayObj obj = new MusicPlayObj();
        obj.init();

        return obj;
    }

    public void init() {
        listMusic = ListMusicObj.make();
        stopPlaying = Msg.prefixed("fmusic.message.musicPlay.stopPlaying", MsgColor.YELLOW);
        emptyPlayingMusic = Msg.prefixed("fmusic.message.musicPlay.emptyPlayingMusic", MsgColor.YELLOW);
        nowPlay = Msg.prefixed("fmusic.message.musicPlay.nowPlay", MsgColor.YELLOW)
            .arg(ARG.musicName)
            .arg(ARG.musicAuthor)
            .arg(ARG.player);
        emptyPlay = Msg.prefixed("fmusic.message.musicPlay.emptyPlay", MsgColor.YELLOW);
        emptyCanPlay = Msg.prefixed("fmusic.message.musicPlay.emptyCanPlay", MsgColor.RED)
            .arg(ARG.musicId);
        mute = Msg.prefixed("fmusic.message.musicPlay.mute", MsgColor.YELLOW);
        mute1 = Msg.prefixed("fmusic.message.musicPlay.mute1", MsgColor.YELLOW);
        mute2 = Msg.prefixed("fmusic.message.musicPlay.mute2", MsgColor.YELLOW);
        mute3 = Msg.prefixed("fmusic.message.musicPlay.mute3", MsgColor.YELLOW);
        switchMusic = Msg.prefixed("fmusic.message.musicPlay.switchMusic", MsgColor.YELLOW);
        addMusic = Msg.prefixed("fmusic.message.musicPlay.addMusic", MsgColor.YELLOW)
            .arg(ARG.musicName)
            .arg(ARG.musicAuthor)
            .arg(ARG.musicAl)
            .arg(ARG.musicAlia)
            .arg(ARG.player);
        checkMusic = Msg.prefixed("fmusic.message.musicPlay.checkMusic", MsgColor.YELLOW)
            .arg(ARG.musicId);
        // 歌单信息拼装模板 (纯结构, 不翻译): SongInfoObj 组装后作为纯文本使用
        musicInfo = ARG.musicName + " | "
            + ARG.musicAuthor
            + " | "
            + ARG.musicAl
            + " | "
            + ARG.musicAlia
            + " | by: "
            + ARG.player;
        trail = Msg.prefixed("fmusic.message.musicPlay.trail", MsgColor.YELLOW);
        cancel = Msg.prefixed("fmusic.message.musicPlay.cancel", MsgColor.YELLOW);
        error1 = Msg.prefixed("fmusic.message.musicPlay.error1", MsgColor.RED);
        error2 = Msg.prefixed("fmusic.message.musicPlay.error2", MsgColor.RED);
    }
}
