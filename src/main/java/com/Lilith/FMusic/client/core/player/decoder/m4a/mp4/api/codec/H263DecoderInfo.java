package com.Lilith.FMusic.client.core.player.decoder.m4a.mp4.api.codec;

import com.Lilith.FMusic.client.core.player.decoder.m4a.mp4.api.DecoderInfo;
import com.Lilith.FMusic.client.core.player.decoder.m4a.mp4.boxes.impl.sampleentries.codec.CodecSpecificBox;
import com.Lilith.FMusic.client.core.player.decoder.m4a.mp4.boxes.impl.sampleentries.codec.H263SpecificBox;

public class H263DecoderInfo extends DecoderInfo {

    private H263SpecificBox box;

    public H263DecoderInfo(CodecSpecificBox box) {
        this.box = (H263SpecificBox) box;
    }

    public int getDecoderVersion() {
        return box.getDecoderVersion();
    }

    public long getVendor() {
        return box.getVendor();
    }

    public int getLevel() {
        return box.getLevel();
    }

    public int getProfile() {
        return box.getProfile();
    }
}
