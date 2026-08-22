package com.Lilith.FMusic.client.core.player.decoder.m4a.mp4.boxes.impl;

import java.io.IOException;

import com.Lilith.FMusic.client.core.player.decoder.m4a.mp4.MP4InputStream;
import com.Lilith.FMusic.client.core.player.decoder.m4a.mp4.boxes.BoxImpl;

/**
 * This class is used for all boxes, that are known but don't contain necessary
 * data and can be skipped. This is mainly used for 'skip', 'free' and 'wide'.
 *
 * @author in-somnia
 */
public class FreeSpaceBox extends BoxImpl {

    public FreeSpaceBox() {
        super("Free Space Box");
    }

    @Override
    public void decode(MP4InputStream in) throws IOException {
        // no need to read, box will be skipped
    }
}
