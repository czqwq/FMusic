package com.Lilith.FMusic.client;

import java.util.List;

import paulscode.sound.Channel;

public interface IGetSound {

    List<Channel> fMusic_Client$getNormalChannels();

    List<Channel> fMusic_Client$getStreamingChannels();
}
