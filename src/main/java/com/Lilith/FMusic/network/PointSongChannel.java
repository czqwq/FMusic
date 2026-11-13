package com.Lilith.FMusic.network;

import java.nio.charset.StandardCharsets;

import net.minecraft.entity.player.EntityPlayerMP;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import cpw.mods.fml.common.network.FMLEventChannel;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.internal.FMLProxyPacket;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * 「右键 → 发送到点歌系统」用的独立信道 (fmusic:req)。
 *
 * <p>
 * 不和已有的 {@code fmusic:channel} 混传: 旧信道的载荷是 {@code CommandType} 序号 + 字段,
 * 老版本客户端/服务端不认识新类型, 混进去只会解析出乱码。这里自带协议版本号, 版本对不上就整包丢弃,
 * 老客户端/老服务端既不认这个信道也就不会崩。
 *
 * <p>
 * 三条消息:
 * <ul>
 * <li>服务端 → 客户端 {@link #TYPE_FEATURE}: 进服后告诉客户端本服务端支不支持点歌;</li>
 * <li>客户端 → 服务端 {@link #TYPE_REQUEST}: 音源 ID + 歌曲 ID;</li>
 * <li>服务端 → 客户端 {@link #TYPE_RESULT}: 状态码 + 歌名 + 作者。</li>
 * </ul>
 *
 * <p>
 * 载荷格式 (大端): byte 协议版本, byte 类型, 之后按类型写字段; 字符串 = int 长度 + UTF-8 字节
 * (-1 表示 null)。FML 1.7.10 出站时会用 {@code payload.array()} 取整个底层数组 (含未写满的容量),
 * 所以发送前先把可读部分裁剪成精确的 byte[]。
 *
 * <p>
 * 一个 JVM 里同名通道只能创建一次 (第二次会抛 "That channel is already registered"), 因此
 * {@link #init(Object)} 做成幂等的: 客户端和集成服务器在同一个 JVM 里各注册自己的 handler 即可。
 */
public final class PointSongChannel {

    /** 信道名, 必须和客户端完全一致 */
    public static final String NAME = "fmusic:req";

    /** 载荷协议版本, 对不上直接当坏包丢弃 */
    public static final byte PROTOCOL = 1;

    /** 服务端 → 客户端: 是否支持右键点歌 */
    public static final byte TYPE_FEATURE = 0;
    /** 客户端 → 服务端: 点歌请求 */
    public static final byte TYPE_REQUEST = 1;
    /** 服务端 → 客户端: 点歌结果 */
    public static final byte TYPE_RESULT = 2;

    /** 点歌成功 */
    public static final String STATUS_SUCCESS = "SUCCESS";
    /** 没有 fmusic.addmusic 权限 */
    public static final String STATUS_PERMISSION_DENIED = "PERMISSION_DENIED";
    /** 钱不够 */
    public static final String STATUS_NO_MONEY = "NO_MONEY";
    /** 包内容非法 (缺字段 / 字段超长) */
    public static final String STATUS_BAD_REQUEST = "BAD_REQUEST";

    /** 单个字符串上限 (字符数), 防止坏包塞超大字符串 */
    private static final int MAX_STRING = 2048;

    private static final Logger LOGGER = LogManager.getLogger("FMusic PointSong");

    /**
     * 静态单例: 同 JVM 里客户端 handler 和服务端 handler 都注册到这一个通道的事件总线上。
     */
    private static volatile FMLEventChannel channel;

    private PointSongChannel() {}

    /**
     * 创建/复用信道并注册 handler (幂等: 重复调用只会多注册 handler, 不会重复建通道)。
     *
     * @param handler 带 {@code @SubscribeEvent} 方法的对象
     */
    public static synchronized void init(Object handler) {
        if (channel == null) {
            channel = NetworkRegistry.INSTANCE.newEventDrivenChannel(NAME);
        }
        if (handler != null) {
            // EventBus.register 对同一个对象重复注册是幂等的
            channel.register(handler);
        }
    }

    /**
     * 客户端 → 服务端: 请求把这首歌加进点歌队列。
     *
     * @param apiId   音源 ID (netapi/qqmusic/kugou)
     * @param musicId 歌曲 ID
     */
    public static void sendRequest(String apiId, String musicId) {
        FMLEventChannel bus = channel;
        if (bus == null) {
            LOGGER.warn("[PointSong] channel is not initialized, request dropped");
            return;
        }
        try {
            ByteBuf buf = Unpooled.buffer();
            buf.writeByte(PROTOCOL);
            buf.writeByte(TYPE_REQUEST);
            writeString(buf, apiId);
            writeString(buf, musicId);
            bus.sendToServer(packet(buf, Side.SERVER));
        } catch (Throwable t) {
            LOGGER.warn("[PointSong] failed to send a point-song request", t);
        }
    }

    /**
     * 服务端 → 客户端: 告诉客户端本服务端支不支持点歌。
     *
     * @param player    目标玩家
     * @param supported 是否支持
     */
    public static void sendFeature(EntityPlayerMP player, boolean supported) {
        FMLEventChannel bus = channel;
        if (bus == null || player == null) {
            return;
        }
        try {
            ByteBuf buf = Unpooled.buffer();
            buf.writeByte(PROTOCOL);
            buf.writeByte(TYPE_FEATURE);
            buf.writeByte(supported ? 1 : 0);
            bus.sendTo(packet(buf, Side.CLIENT), player);
        } catch (Throwable t) {
            LOGGER.warn("[PointSong] failed to send the feature packet", t);
        }
    }

    /**
     * 服务端 → 客户端: 点歌结果。
     *
     * <p>
     * 歌名/作者为空时填空串而不是 null: 客户端把它们直接拼进提示文本里, null 会在那边炸空指针。
     *
     * @param player   目标玩家
     * @param status   状态码 (见 {@link #STATUS_SUCCESS} 等)
     * @param songName 歌名, 可为 null
     * @param author   作者, 可为 null
     */
    public static void sendResult(EntityPlayerMP player, String status, String songName, String author) {
        FMLEventChannel bus = channel;
        if (bus == null || player == null) {
            return;
        }
        try {
            ByteBuf buf = Unpooled.buffer();
            buf.writeByte(PROTOCOL);
            buf.writeByte(TYPE_RESULT);
            writeString(buf, status == null ? "" : status);
            writeString(buf, songName == null ? "" : songName);
            writeString(buf, author == null ? "" : author);
            bus.sendTo(packet(buf, Side.CLIENT), player);
        } catch (Throwable t) {
            LOGGER.warn("[PointSong] failed to send a point-song result", t);
        }
    }

    /**
     * 解析一个数据包。
     *
     * <p>
     * 跑在网络线程: 任何异常 (版本不对、字段缺失、包被截断) 都只返回 null, 绝不往外抛。
     *
     * @param packet 收到的原始包
     * @return 解析结果, 不是我们的包/坏包返回 null
     */
    public static Message read(FMLProxyPacket packet) {
        if (packet == null) {
            return null;
        }
        try {
            ByteBuf payload = packet.payload();
            if (payload == null) {
                return null;
            }
            int size = payload.readableBytes();
            if (size < 2) {
                return null;
            }
            // 先拷一份再读: 不动原缓冲的 readerIndex, 免得影响同一份载荷的其它使用者
            byte[] data = new byte[size];
            payload.getBytes(payload.readerIndex(), data);
            ByteBuf buf = Unpooled.wrappedBuffer(data);

            if (buf.readByte() != PROTOCOL) {
                return null;
            }
            Message message = new Message();
            message.type = buf.readByte();
            switch (message.type) {
                case TYPE_FEATURE:
                    if (buf.readableBytes() < 1) {
                        return null;
                    }
                    message.supported = buf.readByte() != 0;
                    return message;
                case TYPE_REQUEST:
                    message.apiId = readString(buf);
                    message.musicId = readString(buf);
                    if (message.apiId == null || message.musicId == null) {
                        return null;
                    }
                    return message;
                case TYPE_RESULT:
                    message.status = readString(buf);
                    message.songName = readString(buf);
                    message.author = readString(buf);
                    // 发送侧三个字段总会写全; 缺任何一个都当坏包, 免得把 null 传给 Kotlin 侧的非空参数
                    if (message.status == null || message.songName == null || message.author == null) {
                        return null;
                    }
                    return message;
                default:
                    return null;
            }
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 包一层 FMLProxyPacket: 只带可读部分 (FML 出站走的是 {@code payload.array()}, 不裁剪会把
     * 底层数组里没写满的 0 一起发出去)。
     */
    private static FMLProxyPacket packet(ByteBuf buf, Side target) {
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);
        FMLProxyPacket packet = new FMLProxyPacket(Unpooled.wrappedBuffer(data), NAME);
        packet.setTarget(target);
        return packet;
    }

    private static void writeString(ByteBuf buf, String text) {
        if (text == null) {
            buf.writeInt(-1);
            return;
        }
        byte[] data = text.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(data.length);
        buf.writeBytes(data);
    }

    private static String readString(ByteBuf buf) {
        if (buf.readableBytes() < 4) {
            return null;
        }
        int length = buf.readInt();
        // -1 = null; 其余负数/超过上限/超过剩余长度都当坏包
        if (length < 0 || length > MAX_STRING * 4 || length > buf.readableBytes()) {
            return null;
        }
        if (length == 0) {
            return "";
        }
        byte[] data = new byte[length];
        buf.readBytes(data);
        return new String(data, StandardCharsets.UTF_8);
    }

    /** 解析出来的一条消息 (一条包里只有一个) */
    public static final class Message {

        /** {@link #TYPE_FEATURE} / {@link #TYPE_REQUEST} / {@link #TYPE_RESULT} */
        public byte type;
        /** TYPE_FEATURE: 服务端是否支持点歌 */
        public boolean supported;
        /** TYPE_REQUEST: 音源 ID */
        public String apiId;
        /** TYPE_REQUEST: 歌曲 ID */
        public String musicId;
        /** TYPE_RESULT: 状态码 */
        public String status;
        /** TYPE_RESULT: 歌名 (可能为空串) */
        public String songName;
        /** TYPE_RESULT: 作者 (可能为空串) */
        public String author;
    }
}
