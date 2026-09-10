package com.Lilith.FMusic.api;

/**
 * 点歌结果
 */
public final class AddResult {

    public enum Status {
        /** 成功 */
        SUCCESS,
        /** FMusic 未启动 */
        NOT_RUNNING,
        /** 音源不存在 */
        API_MISSING,
        /** 无效的歌曲 ID/链接 */
        INVALID_ID,
        /** 歌曲信息解析失败 */
        PARSE_FAILED,
        /** 播放地址获取失败 */
        NO_PLAY_URL,
        /** 播放列表已满 */
        LIST_FULL,
        /** 该歌曲被禁止点播 */
        SONG_BANNED,
        /** 歌曲已在列表中 */
        DUPLICATE,
        /** 该玩家点歌数量已达上限 */
        PLAYER_LIMIT,
        /** 该玩家被禁止点歌 */
        PLAYER_BANNED,
        /** 当前没有可接收音乐的玩家 */
        NO_PLAYER,
        /** 被 MusicAddEvent 事件取消 */
        EVENT_CANCELLED
    }

    public final Status status;
    public final String message;
    /** 成功时的歌曲信息 */
    public final SongInfo song;

    private AddResult(Status status, String message, SongInfo song) {
        this.status = status;
        this.message = message;
        this.song = song;
    }

    public static AddResult success(SongInfo song) {
        return new AddResult(Status.SUCCESS, "", song);
    }

    public static AddResult failure(Status status, String message) {
        return new AddResult(status, message, null);
    }

    public boolean ok() {
        return status == Status.SUCCESS;
    }

    @Override
    public String toString() {
        return "AddResult{" + status + (message.isEmpty() ? "" : ", " + message) + "}";
    }

    /** 歌曲信息 (对外只读视图) */
    public static final class SongInfo {
        public final String api;
        public final String id;
        public final String name;
        public final String author;
        public final String album;
        public final String alia;
        /** 点歌者 */
        public final String requester;
        /** 时长 (毫秒) */
        public final long lengthMs;
        public final String picUrl;
        public final boolean trial;

        public SongInfo(String api, String id, String name, String author, String album, String alia,
                        String requester, long lengthMs, String picUrl, boolean trial) {
            this.api = api;
            this.id = id;
            this.name = name;
            this.author = author;
            this.album = album;
            this.alia = alia;
            this.requester = requester;
            this.lengthMs = lengthMs;
            this.picUrl = picUrl;
            this.trial = trial;
        }

        @Override
        public String toString() {
            return name + " - " + author + " [" + api + ":" + id + "]";
        }
    }
}
