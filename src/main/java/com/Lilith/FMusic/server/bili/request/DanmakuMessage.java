package com.Lilith.FMusic.server.bili.request;

public final class DanmakuMessage {

    public final long uid;
    public final String username;
    public final String text;
    public final long receivedAtMillis;

    public DanmakuMessage(long uid, String username, String text, long receivedAtMillis) {
        this.uid = uid;
        this.username = username == null ? "" : username;
        this.text = text == null ? "" : text;
        this.receivedAtMillis = receivedAtMillis;
    }
}
