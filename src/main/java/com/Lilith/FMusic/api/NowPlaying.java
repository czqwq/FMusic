package com.Lilith.FMusic.api;

/**
 * 当前播放信息 (对外只读视图)
 */
public final class NowPlaying {

    public final boolean playing;
    public final String api;
    public final String id;
    public final String name;
    public final String author;
    public final String album;
    public final String alia;
    public final String requester;
    /** 歌曲总时长 (毫秒) */
    public final long lengthMs;
    /** 当前播放位置 (毫秒) */
    public final long nowMs;
    /** 播放地址 */
    public final String url;
    public final boolean trial;

    public NowPlaying(boolean playing, String api, String id, String name, String author, String album, String alia,
        String requester, long lengthMs, long nowMs, String url, boolean trial) {
        this.playing = playing;
        this.api = api;
        this.id = id;
        this.name = name;
        this.author = author;
        this.album = album;
        this.alia = alia;
        this.requester = requester;
        this.lengthMs = lengthMs;
        this.nowMs = nowMs;
        this.url = url;
        this.trial = trial;
    }

    @Override
    public String toString() {
        if (!playing) {
            return "NowPlaying{idle}";
        }
        return name + " - " + author + " " + nowMs + "/" + lengthMs + "ms [" + api + ":" + id + "]";
    }
}
