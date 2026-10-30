package com.Lilith.FMusic.netapi.obj.music.info;

import java.util.List;

public class InfoObj {

    private List<Songs> songs;

    public boolean isOk() {
        return (songs != null && !songs.isEmpty());
    }

    public String getName() {
        if (songs == null || songs.isEmpty()) return "";
        return songs.get(0)
            .getName();
    }

    public String getAuthor() {
        StringBuilder Author = new StringBuilder();
        if (songs.isEmpty()) return "";
        for (ar ar : songs.get(0)
            .getAr()) {
            Author.append(ar.getName())
                .append(",");
        }
        if (Author.length() != 0) {
            Author = new StringBuilder(Author.substring(0, Author.length() - 1));
        }
        return Author.toString();
    }

    public String getAlia() {
        StringBuilder Alia = new StringBuilder();
        for (String alia : songs.get(0)
            .getAlia()) {
            Alia.append(alia)
                .append(",");
        }
        if (Alia.length() != 0) {
            Alia = new StringBuilder(Alia.substring(0, Alia.length() - 1));
        }
        return Alia.toString();
    }

    public String getAl() {
        return songs.get(0)
            .getAl();
    }

    public long getLength() {
        if (songs == null) return 0;
        return songs.get(0)
            .getLength();
    }

    public String getPicUrl() {
        if (songs == null) return null;
        return songs.get(0)
            .getPicUrl();
    }
}

class Songs {

    private String name;
    private List<ar> ar;
    private List<String> alia;
    private al al;
    /**
     * 毫秒级歌曲总时长 (v3 详情接口直接返回, 最准确)
     */
    private long dt;
    private h l;
    private h m;
    private h h;

    public long getLength() {
        // 优先使用接口给出的精确时长, 缺失时才回退到按文件大小+码率估算
        if (dt > 0) return dt;
        if (l != null) return l.getLength();
        if (m != null) return m.getLength();
        if (h != null) return h.getLength();
        return 0;
    }

    public String getName() {
        return name == null ? "" : name;
    }

    public List<ar> getAr() {
        return ar;
    }

    public List<String> getAlia() {
        return alia;
    }

    public String getAl() {
        return al.getName() == null ? "" : al.getName();
    }

    public String getPicUrl() {
        return al.getPicUrl();
    }
}

class ar {

    private String name;

    public String getName() {
        return name == null ? "" : name;
    }
}

class al {

    private String name;
    private String picUrl;

    public String getName() {
        return name == null ? "" : name;
    }

    public String getPicUrl() {
        return picUrl == null ? "" : picUrl;
    }
}

class h {

    private long br;
    private long size;

    public long getLength() {
        // 时长(ms) = 字节数 * 8000 / 码率(bps). 必须先乘后除并四舍五入:
        // 原写法 size / br * 8000 是整数除法, 会把时长向下截断到 8 秒的整数倍
        // (2:03.871 的歌被算成 2:00.000, 服务端提前发 STOP → 尾部被切)
        if (size <= 0 || br <= 0) return 0;
        return (size * 8000L + br / 2) / br;
    }
}
