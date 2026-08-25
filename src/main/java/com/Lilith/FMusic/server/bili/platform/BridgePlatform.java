package com.Lilith.FMusic.server.bili.platform;

import java.io.File;
import java.util.logging.Level;

public interface BridgePlatform {

    String platformName();

    File dataFolder();

    boolean isActive();

    boolean executeGlobal(Runnable task);

    void broadcast(String legacyMessage);

    void log(Level level, String message, Throwable throwable);

    AllMusicHandle findAllMusic();
}
