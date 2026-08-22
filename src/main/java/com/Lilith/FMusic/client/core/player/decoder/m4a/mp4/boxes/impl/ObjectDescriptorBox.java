package com.Lilith.FMusic.client.core.player.decoder.m4a.mp4.boxes.impl;

import java.io.IOException;

import com.Lilith.FMusic.client.core.player.decoder.m4a.mp4.MP4InputStream;
import com.Lilith.FMusic.client.core.player.decoder.m4a.mp4.boxes.FullBox;
import com.Lilith.FMusic.client.core.player.decoder.m4a.mp4.od.Descriptor;

public class ObjectDescriptorBox extends FullBox {

    private Descriptor objectDescriptor;

    public ObjectDescriptorBox() {
        super("Object Descriptor Box");
    }

    @Override
    public void decode(MP4InputStream in) throws IOException {
        super.decode(in);
        objectDescriptor = Descriptor.createDescriptor(in);
    }

    public Descriptor getObjectDescriptor() {
        return objectDescriptor;
    }
}
