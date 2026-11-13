package com.Lilith.FMusic.server.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.INetHandler;
import net.minecraft.network.NetHandlerPlayServer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.Lilith.FMusic.api.AddResult;
import com.Lilith.FMusic.api.FMusicApi;
import com.Lilith.FMusic.network.PointSongChannel;
import com.Lilith.FMusic.server.FMusicServer;
import com.Lilith.FMusic.server.core.FMusic;
import com.Lilith.FMusic.server.core.command.PermissionList;
import com.Lilith.FMusic.server.core.saves.SaveTask;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.common.network.internal.FMLProxyPacket;

/**
 * 点歌信道 (fmusic:req) 的服务端处理器。
 *
 * <p>
 * 收到 {@link PointSongChannel#TYPE_REQUEST} 后走和 {@code /music <api> <id>} 完全一样的逻辑:
 * 权限 → 经济 → {@link FMusicApi#addMusicSync} (队列满/禁歌/重复/玩家上限/禁人/取歌信息/
 * MusicAddEvent/入队都在里面) → 把状态码发回客户端。
 *
 * <p>
 * 线程: 网络线程只解析包并定位玩家; 权限和经济检查放 {@code runTask} (服务端 tick 线程);
 * {@code addMusicSync} 会发 HTTP 请求, 必须放 {@link SaveTask} 后台线程; 结果再回 tick 线程发包。
 * 每个环节都 catch 住异常, 坏包/异常绝不能把网络线程或 tick 线程搞崩。
 *
 * <p>
 * 集成服务器 (单人游戏) 里客户端和服务端在同一个 JVM: 本处理器由 commonSetup 注册,
 * 单人游戏同样生效。
 */
public class PointSongServerHandler {

    private static final Logger LOGGER = LogManager.getLogger("FMusic PointSong");

    @SubscribeEvent
    public void onServerPacket(final FMLNetworkEvent.ServerCustomPacketEvent evt) {
        if (evt == null || evt.packet == null) {
            return;
        }
        final PointSongChannel.Message msg;
        final EntityPlayerMP player;
        try {
            msg = PointSongChannel.read(evt.packet);
            // 同一个信道上还有能力包/结果包的方向, 服务端只关心点歌请求
            if (msg == null || msg.type != PointSongChannel.TYPE_REQUEST) {
                return;
            }
            player = playerOf(evt.packet);
        } catch (Throwable t) {
            LOGGER.warn("[PointSong] failed to read an incoming packet", t);
            return;
        }
        if (player == null) {
            return;
        }

        final String apiId = msg.apiId == null ? "" : msg.apiId.trim();
        final String musicId = msg.musicId == null ? "" : msg.musicId.trim();
        if (apiId.isEmpty() || musicId.isEmpty()) {
            reply(player, PointSongChannel.STATUS_BAD_REQUEST, null, null);
            return;
        }

        try {
            if (FMusic.side == null) {
                return;
            }
            final String name = player.getCommandSenderName();
            FMusic.side.runTask(() -> checkAndAdd(apiId, musicId, name, player));
        } catch (Throwable t) {
            LOGGER.warn("[PointSong] failed to schedule a request", t);
        }
    }

    /**
     * tick 线程: 权限 + 经济检查, 然后把真正耗时的点歌丢给后台线程。
     */
    private static void checkAndAdd(final String apiId, final String musicId, final String name,
        final EntityPlayerMP player) {
        try {
            if (FMusic.getConfig().needPermission
                && !FMusic.side.checkPermission(player, PermissionList.PERMISSION_ADD_MUSIC)) {
                reply(player, PointSongChannel.STATUS_PERMISSION_DENIED, null, null);
                return;
            }
            final boolean useCost = FMusic.getConfig().cost.useCost && FMusic.economy != null;
            final int cost = FMusic.getConfig().cost.addMusicCost;
            if (useCost && !FMusic.economy.check(name, cost)) {
                reply(player, PointSongChannel.STATUS_NO_MONEY, null, null);
                return;
            }
            SaveTask.task(() -> add(apiId, musicId, name, player, useCost, cost));
        } catch (Throwable t) {
            LOGGER.warn("[PointSong] request check failed for " + name, t);
            reply(player, AddResult.Status.PARSE_FAILED.name(), null, null);
        }
    }

    /**
     * 后台线程: 走和 {@code /music <api> <id>} 一样的入队流程, 成功后扣钱, 最后把结果发回客户端。
     */
    private static void add(final String apiId, final String musicId, final String name, final EntityPlayerMP player,
        final boolean useCost, final int cost) {
        AddResult result = null;
        try {
            result = FMusicApi.addMusicSync(apiId, musicId, name);
        } catch (Throwable t) {
            LOGGER.warn("[PointSong] addMusicSync threw for " + name, t);
        }
        if (result == null) {
            reply(player, AddResult.Status.PARSE_FAILED.name(), null, null);
            return;
        }
        final String status = result.status.name();
        final String songName = result.song == null ? null : result.song.name;
        final String author = result.song == null ? null : result.song.author;
        if (result.ok() && useCost) {
            // 钱在入队之后才扣: 扣失败也只记 warn, 歌已经在队列里了, 不能反过来回滚/报错
            try {
                if (!FMusic.economy.cost(name, cost)) {
                    LOGGER.warn("[PointSong] failed to charge " + name + " " + cost);
                }
            } catch (Throwable t) {
                LOGGER.warn("[PointSong] economy error while charging " + name, t);
            }
        }
        reply(player, status, songName, author);
    }

    /**
     * 回 tick 线程发结果包: 发包要碰实体/网络, 不能在网络线程或后台线程直接做。
     */
    private static void reply(final EntityPlayerMP player, final String status, final String songName,
        final String author) {
        if (FMusic.side == null) {
            return;
        }
        try {
            FMusic.side.runTask(() -> {
                try {
                    // 解析可能要好几秒: 玩家可能已经下线 (没人收结果包), 也可能已经重生
                    // (旧的 EntityPlayerMP 实例换掉了) —— 按名字取当前在线的那一个
                    EntityPlayerMP target = player;
                    if (FMusicServer.server != null) {
                        Object online = FMusic.side.getPlayer(player.getCommandSenderName());
                        if (!(online instanceof EntityPlayerMP)) {
                            return;
                        }
                        target = (EntityPlayerMP) online;
                    }
                    PointSongChannel.sendResult(target, status, songName, author);
                } catch (Throwable t) {
                    LOGGER.warn("[PointSong] failed to send result " + status, t);
                }
            });
        } catch (Throwable t) {
            LOGGER.warn("[PointSong] failed to schedule result " + status, t);
        }
    }

    /** 从数据包拿到发包的玩家 (1.7.10 里包只带 net handler) */
    private static EntityPlayerMP playerOf(FMLProxyPacket packet) {
        if (packet == null) {
            return null;
        }
        INetHandler handler = packet.handler();
        if (!(handler instanceof NetHandlerPlayServer)) {
            return null;
        }
        return ((NetHandlerPlayServer) handler).playerEntity;
    }
}
