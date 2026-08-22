package com.Lilith.FMusic.server.core;

import com.Lilith.FMusic.server.core.music.LyricSave;
import com.Lilith.FMusic.server.core.objs.music.SearchPageObj;
import com.Lilith.FMusic.server.core.objs.music.SongInfoObj;

public interface IMusicApi {

    String getId();

    SongInfoObj getMusic(String id, String player, boolean isList);

    SearchPageObj search(String[] args, boolean isDefault);

    void setList(String id, Object sender);

    LyricSave getLyric(String id);

    String getPlayUrl(String id);

    boolean isBusy();

    String getMusicId(String arg);

    boolean checkId(String id);
}
