package com.Lilith.FMusic.server.bili.allmusic;

import com.Lilith.FMusic.server.bili.request.SongSelection;

public final class QueueResult {

    public enum Status {
        SUCCESS,
        ALLMUSIC_MISSING,
        API_MISSING,
        SEARCH_FAILED,
        NOT_FOUND,
        INVALID_ID,
        LIST_FULL,
        SONG_BANNED,
        DUPLICATE,
        PLAYER_LIMIT,
        PLAYER_BANNED,
        NO_PLAYER,
        EVENT_CANCELLED,
        INCOMPATIBLE,
        CANCELLED,
        DISABLED,
        INTERNAL_ERROR
    }

    public final Status status;
    public final String detail;
    public final SongSelection song;

    private QueueResult(Status status, String detail, SongSelection song) {
        this.status = status;
        this.detail = detail == null ? "" : detail;
        this.song = song;
    }

    public static QueueResult success(SongSelection song) {
        return new QueueResult(Status.SUCCESS, "", song);
    }

    public static QueueResult failure(Status status, String detail) {
        return new QueueResult(status, detail, null);
    }

    public boolean successful() {
        return status == Status.SUCCESS;
    }
}
