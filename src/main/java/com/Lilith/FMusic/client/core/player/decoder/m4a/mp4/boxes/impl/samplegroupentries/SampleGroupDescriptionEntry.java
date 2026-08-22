package com.Lilith.FMusic.client.core.player.decoder.m4a.mp4.boxes.impl.samplegroupentries;

import java.io.IOException;

import com.Lilith.FMusic.client.core.player.decoder.m4a.mp4.MP4InputStream;
import com.Lilith.FMusic.client.core.player.decoder.m4a.mp4.boxes.BoxImpl;

public abstract class SampleGroupDescriptionEntry extends BoxImpl {

    protected SampleGroupDescriptionEntry(String name) {
        super(name);
    }

    @Override
    public abstract void decode(MP4InputStream in) throws IOException;
}
