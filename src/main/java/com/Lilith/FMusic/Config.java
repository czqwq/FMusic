package com.Lilith.FMusic;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

public class Config {

    private static final String PAUSE_AT_FREEZE = "pause_at_freeze";

    /**
     * 单人游戏中暂停(按 Esc)时是否暂停音乐播放
     */
    public static boolean pauseAtFreeze = false;

    private static File configFile;

    /**
     * 读取/保存 FMusic.cfg。
     * 音乐相关配置不在此文件: 服务端在 ../fmusic_server, 客户端在 ./fmusic_client.json。
     */
    public static void synchronizeConfiguration(File file) {
        configFile = file;
        Configuration configuration = new Configuration(file);
        configuration.load();
        pauseAtFreeze = configuration
            .get(
                Configuration.CATEGORY_GENERAL,
                PAUSE_AT_FREEZE,
                false,
                "单人游戏中暂停(按Esc)时是否暂停音乐播放\n" + "音乐配置: 服务端在 ../fmusic_server, 客户端在 ./fmusic_client.json")
            .getBoolean();
        if (configuration.hasChanged()) {
            configuration.save();
        }
    }

    /**
     * /music reload 时刷新 FMusic.cfg
     */
    public static void reload() {
        if (configFile != null) {
            synchronizeConfiguration(configFile);
        }
    }

    /**
     * 将当前内存值写回 FMusic.cfg (设置指令调用, 立即生效并持久化)
     */
    public static void save() {
        if (configFile == null) {
            return;
        }
        Configuration configuration = new Configuration(configFile);
        configuration.load();
        configuration
            .get(
                Configuration.CATEGORY_GENERAL,
                PAUSE_AT_FREEZE,
                false,
                "单人游戏中暂停(按Esc)时是否暂停音乐播放\n" + "音乐配置: 服务端在 ../fmusic_server, 客户端在 ./fmusic_client.json")
            .set(pauseAtFreeze);
        configuration.save();
    }
}
