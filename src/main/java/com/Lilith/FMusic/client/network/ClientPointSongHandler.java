package com.Lilith.FMusic.client.network;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.Lilith.FMusic.dango.music.PointSongBridge;
import com.Lilith.FMusic.network.PointSongChannel;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * 点歌通道（fmusic:req）的客户端收包器。
 *
 * <p>
 * 必须是一个独立对象: 不能复用 {@link com.Lilith.FMusic.client.FMusic} 里已有的
 * {@code @SubscribeEvent} 方法, 那个对象注册在旧的 "fmusic:channel" 上, 混在一起会把
 * 点歌包当成旧协议解析。注册入口是 {@code FMusic.preload} 里的
 * {@code PointSongChannel.init(new ClientPointSongHandler())}。
 *
 * <p>
 * 这里跑在网络线程 (Netty): 一次坏包 (字段缺失、版本不对、服务端乱发) 绝不能把异常抛出去,
 * 否则可能直接把客户端踢下线, 所以整个方法包在 try/catch 里。
 */
@SideOnly(Side.CLIENT)
public class ClientPointSongHandler {

    private static final Logger LOGGER = LogManager.getLogger("FMusic PointSong");

    @SubscribeEvent
    public void onPacket(final FMLNetworkEvent.ClientCustomPacketEvent evt) {
        try {
            PointSongChannel.Message msg = PointSongChannel.read(evt.packet);
            if (msg == null) return;
            if (msg.type == PointSongChannel.TYPE_FEATURE) {
                // 进服时服务端告诉我们它支不支持点歌
                PointSongBridge.onFeature(msg.supported);
            } else if (msg.type == PointSongChannel.TYPE_RESULT) {
                // 服务端处理完一次点歌请求的结果 (SUCCESS / 各种失败码)
                PointSongBridge.onResult(msg.status, msg.songName, msg.author);
            }
        } catch (Throwable t) {
            LOGGER.warn("Dropped an unreadable point-song packet", t);
        }
    }
}
