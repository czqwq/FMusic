package com.Lilith.FMusic.client.core.player.decoder.m4a.mp4.boxes.impl.meta;

import java.io.IOException;

import com.Lilith.FMusic.client.core.player.decoder.m4a.mp4.MP4InputStream;

public class ThreeGPPAlbumBox extends ThreeGPPMetadataBox {

    private int trackNumber;

    public ThreeGPPAlbumBox() {
        super("3GPP Album Box");
    }

    @Override
    public void decode(MP4InputStream in) throws IOException {
        super.decode(in);

        trackNumber = (getLeft(in) > 0) ? in.read() : -1;
    }

    /**
     * The track number (order number) of the media on this album. This is an
     * optional field. If the field is not present, -1 is returned.
     *
     * @return the track number
     */
    public int getTrackNumber() {
        return trackNumber;
    }
}
