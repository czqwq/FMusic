package com.Lilith.FMusic.server.bili.command;

public interface CommandAudience {

    String name();

    boolean hasPermission(String permission);

    void sendMessage(String legacyMessage);
}
