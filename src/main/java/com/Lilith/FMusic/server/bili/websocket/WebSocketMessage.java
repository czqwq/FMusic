package com.Lilith.FMusic.server.bili.websocket;

public final class WebSocketMessage {

    public final boolean text;
    public final byte[] payload;

    public WebSocketMessage(boolean text, byte[] payload) {
        this.text = text;
        this.payload = payload == null ? new byte[0] : payload;
    }
}
