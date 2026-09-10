package com.Lilith.FMusic.api;

/**
 * 搜索结果 (对外只读视图)
 */
public final class SearchResult {

    public final String api;
    public final String id;
    public final String name;
    public final String author;
    public final String album;

    public SearchResult(String api, String id, String name, String author, String album) {
        this.api = api;
        this.id = id;
        this.name = name;
        this.author = author;
        this.album = album;
    }

    @Override
    public String toString() {
        return name + " - " + author + " (" + album + ") [" + api + ":" + id + "]";
    }
}
