package com.Lilith.FMusic.client.core.player.decoder.m4a.mp4.boxes.impl.samplegroupentries;

import java.io.IOException;

import com.Lilith.FMusic.client.core.player.decoder.m4a.mp4.MP4InputStream;

public class AudioSampleGroupEntry extends SampleGroupDescriptionEntry {

    public AudioSampleGroupEntry() {
        super("Audio Sample Group Entry");
    }

    @Override
    public void decode(MP4InputStream in) throws IOException {}
}
