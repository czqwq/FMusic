package com.Lilith.FMusic.server.bili.bilibili;

public final class DanmuServer {

    public final String host;
    public final int wssPort;

    public DanmuServer(String host, int wssPort) {
        this.host = host;
        this.wssPort = wssPort;
    }

    public String url() {
        return "wss://" + host + ':' + wssPort + "/sub";
    }
}
