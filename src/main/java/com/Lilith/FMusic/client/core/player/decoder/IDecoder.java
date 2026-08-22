package com.Lilith.FMusic.client.core.player.decoder;

public interface IDecoder {
    //so what the hell about that decoder
    //heavy code:(

    BuffPack decodeFrame() throws Exception;

    void close() throws Exception;

    boolean set() throws Exception;

    int getOutputFrequency();

    int getOutputChannels();

    void set(int time);
}
