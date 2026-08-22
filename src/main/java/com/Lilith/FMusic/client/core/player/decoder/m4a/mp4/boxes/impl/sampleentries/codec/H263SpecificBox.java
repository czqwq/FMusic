package com.Lilith.FMusic.client.core.player.decoder.m4a.mp4.boxes.impl.sampleentries.codec;

import java.io.IOException;

import com.Lilith.FMusic.client.core.player.decoder.m4a.mp4.MP4InputStream;

public class H263SpecificBox extends CodecSpecificBox {

    private int level, profile;

    public H263SpecificBox() {
        super("H.263 Specific Box");
    }

    @Override
    public void decode(MP4InputStream in) throws IOException {
        decodeCommon(in);

        level = in.read();
        profile = in.read();
    }

    public int getLevel() {
        return level;
    }

    public int getProfile() {
        return profile;
    }
}
