package com.Lilith.FMusic.server.bili.bilibili;

public final class BiliPacket {

    public final int protocolVersion;
    public final int operation;
    public final int sequence;
    public final byte[] body;

    public BiliPacket(int protocolVersion, int operation, int sequence, byte[] body) {
        this.protocolVersion = protocolVersion;
        this.operation = operation;
        this.sequence = sequence;
        this.body = body == null ? new byte[0] : body;
    }
}
