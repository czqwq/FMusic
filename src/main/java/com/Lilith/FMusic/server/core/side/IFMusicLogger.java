package com.Lilith.FMusic.server.core.side;

import net.kyori.adventure.text.Component;

import com.Lilith.FMusic.server.core.FMusic;

public interface IFMusicLogger {

    default void data(String data) {
        data(FMusic.side.miniMessage(data));
    }

    void data(Component data);
}
