package com.Lilith.FMusic.client.core.player.decoder.m4a.mp4.od;

import java.io.IOException;

import com.Lilith.FMusic.client.core.player.decoder.m4a.mp4.MP4InputStream;

/**
 * This class is used if any unknown Descriptor is found in a stream. All
 * contents of the Descriptor will be skipped.
 *
 * @author in-somnia
 */
public class UnknownDescriptor extends Descriptor {

    @Override
    void decode(MP4InputStream in) throws IOException {
        // content will be skipped
    }
}
