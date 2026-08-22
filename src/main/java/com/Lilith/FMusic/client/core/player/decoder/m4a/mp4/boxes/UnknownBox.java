package com.Lilith.FMusic.client.core.player.decoder.m4a.mp4.boxes;

import java.io.IOException;

import com.Lilith.FMusic.client.core.player.decoder.m4a.mp4.MP4InputStream;

/**
 * Box implementation that is used for unknown types.
 *
 * @author in-somnia
 */
class UnknownBox extends BoxImpl {

    UnknownBox() {
        super("unknown");
    }

    @Override
    public void decode(MP4InputStream in) throws IOException {
        // no need to read, box will be skipped
    }
}
