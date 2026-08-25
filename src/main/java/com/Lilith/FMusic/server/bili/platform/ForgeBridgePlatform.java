package com.Lilith.FMusic.server.bili.platform;

import java.io.File;
import java.util.logging.Level;

import com.Lilith.FMusic.server.FMusicServer;
import com.Lilith.FMusic.server.core.FMusic;

/**
 * Forge 服务端平台适配 (原 BiliMusicBridge 的 Bukkit/Bungee/Velocity 平台层,
 * 移植到本模组后由 Forge 集成服务器实现)。
 */
public final class ForgeBridgePlatform implements BridgePlatform {

    @Override
    public String platformName() {
        return "Forge 1.7.10";
    }

    @Override
    public File dataFolder() {
        // fmusic_server/bili/ (与 fmusic_server/config.json 同目录)
        return new File(FMusic.SERVER_DIR, "bili");
    }

    @Override
    public boolean isActive() {
        return FMusic.isRun;
    }

    @Override
    public boolean executeGlobal(Runnable task) {
        if (FMusic.side == null) {
            return false;
        }
        FMusic.side.runTask(task);
        return true;
    }

    @Override
    public void broadcast(String legacyMessage) {
        FMusicServer.LOGGER.info("[FMusic][B站点歌] " + legacyMessage);
    }

    @Override
    public void log(Level level, String message, Throwable throwable) {
        if (throwable != null) {
            FMusicServer.LOGGER.warn("[B站点歌] " + message, throwable);
        } else if (level.intValue() >= Level.WARNING.intValue()) {
            FMusicServer.LOGGER.warn("[B站点歌] " + message);
        } else {
            FMusicServer.LOGGER.info("[B站点歌] " + message);
        }
    }

    @Override
    public AllMusicHandle findAllMusic() {
        return new AllMusicHandle(FMusic.isRun);
    }
}
