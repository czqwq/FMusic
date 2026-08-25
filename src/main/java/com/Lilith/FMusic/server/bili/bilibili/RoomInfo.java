package com.Lilith.FMusic.server.bili.bilibili;

public final class RoomInfo {

    public final long requestedRoomId;
    public final long realRoomId;
    public final int liveStatus;

    public RoomInfo(long requestedRoomId, long realRoomId, int liveStatus) {
        this.requestedRoomId = requestedRoomId;
        this.realRoomId = realRoomId;
        this.liveStatus = liveStatus;
    }
}
