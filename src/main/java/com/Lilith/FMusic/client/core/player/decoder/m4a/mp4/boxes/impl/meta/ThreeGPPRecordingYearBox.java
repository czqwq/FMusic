package com.Lilith.FMusic.client.core.player.decoder.m4a.mp4.boxes.impl.meta;

import java.io.IOException;

import com.Lilith.FMusic.client.core.player.decoder.m4a.mp4.MP4InputStream;
import com.Lilith.FMusic.client.core.player.decoder.m4a.mp4.boxes.FullBox;

public class ThreeGPPRecordingYearBox extends FullBox {

    private int year;

    public ThreeGPPRecordingYearBox() {
        super("3GPP Recording Year Box");
    }

    @Override
    public void decode(MP4InputStream in) throws IOException {
        super.decode(in);

        year = (int) in.readBytes(2);
    }

    public int getYear() {
        return year;
    }
}
