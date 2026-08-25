package com.Lilith.FMusic.server.bili.request;

public final class SongSelection {

    public final String id;
    public final String api;
    public final String name;
    public final String artist;

    public SongSelection(String id, String api, String name, String artist) {
        this.id = id == null ? "" : id;
        this.api = api == null ? "" : api;
        this.name = name == null ? "" : name;
        this.artist = artist == null ? "" : artist;
    }
}
