package com.Lilith.FMusic.server.bili.request;

public final class SongRequest {

    public final long uid;
    public final String username;
    public final String requesterName;
    public final String keyword;
    public final long receivedAtMillis;

    public SongRequest(long uid, String username, String requesterName, String keyword, long receivedAtMillis) {
        this.uid = uid;
        this.username = username == null ? "" : username;
        this.requesterName = requesterName == null ? "" : requesterName;
        this.keyword = keyword == null ? "" : keyword;
        this.receivedAtMillis = receivedAtMillis;
    }
}
