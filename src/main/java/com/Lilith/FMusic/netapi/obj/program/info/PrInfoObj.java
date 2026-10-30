package com.Lilith.FMusic.netapi.obj.program.info;

public class PrInfoObj {

    private program program;

    public boolean isOk() {
        return (program != null && program.getMainSong()
            .getName() != null);
    }

    public String getId() {

        return String.valueOf(
            program.getMainSong()
                .getId());
    }

    public String getName() {

        return program.getMainSong()
            .getName();
    }

    public String getAlia() {
        if (program.getDj() == null) return null;
        return program.getDj()
            .getBrand();
    }

    public long getLength() {
        return program.getMainSong()
            .getLength();
    }

    public String getAuthor() {
        return program.getDj()
            .getNickname();
    }
}

class mainSong {

    private String name;
    private long id;
    /**
     * 毫秒级歌曲总时长 (接口直接返回, 最准确)
     */
    private long dt;
    private hMusic hMusic;
    private hMusic mMusic;
    private hMusic lMusic;
    private hMusic bMusic;

    public long getLength() {
        if (dt > 0) return dt;
        if (hMusic != null) return hMusic.getLength();
        else if (mMusic != null) return mMusic.getLength();
        else if (lMusic != null) return lMusic.getLength();
        else if (bMusic != null) return bMusic.getLength();
        return 0;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}

class program {

    private dj dj;
    private mainSong mainSong;

    public dj getDj() {
        return dj;
    }

    public mainSong getMainSong() {
        return mainSong;
    }
}

class dj {

    private String brand;
    private String nickname;

    public String getNickname() {
        return nickname;
    }

    public String getBrand() {
        return brand;
    }
}

class hMusic {

    private long size;
    private long bitrate;

    public long getLength() {
        // 同 InfoObj: 必须先乘后除, 整数除法 size/bitrate 会把时长截断到 8 秒整数倍
        if (size <= 0 || bitrate <= 0) return 0;
        return (size * 8000L + bitrate / 2) / bitrate;
    }
}
