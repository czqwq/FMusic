package com.Lilith.FMusic;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

import com.Lilith.FMusic.client.core.FMusicLog;
import com.Lilith.FMusic.codec.HudPosObj;

public class Config {

    private static final String PAUSE_AT_FREEZE = "pause_at_freeze";
    private static final String HUD_CATEGORY = "hud";

    /**
     * 单人游戏中暂停(按 Esc)时是否暂停音乐播放
     */
    public static boolean pauseAtFreeze = false;

    /**
     * 是否输出 FMusic 诊断日志 (播放/seek/连接等, 默认关闭)
     */
    public static boolean debug = false;

    /** HUD 可视化配置保存的模块位置 (hud category, 默认 -1 = 未保存) */
    public static int hudInfoX = -1;
    public static int hudInfoY = -1;
    public static int hudLyricX = -1;
    public static int hudLyricY = -1;
    public static int hudStateX = -1;
    public static int hudStateY = -1;
    public static int hudPicX = -1;
    public static int hudPicY = -1;

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
        debug = configuration.get(Configuration.CATEGORY_GENERAL, "debug", false, "是否输出 FMusic 诊断日志 (播放/seek/连接等)")
            .getBoolean();
        FMusicLog.enabled = debug;

        // HUD 可视化配置 (与 PowerGoggles 相同的 Forge Configuration 模式)
        hudInfoX = configuration.get(HUD_CATEGORY, "hud_info_x", -1, "")
            .getInt(-1);
        hudInfoY = configuration.get(HUD_CATEGORY, "hud_info_y", -1, "")
            .getInt(-1);
        hudLyricX = configuration.get(HUD_CATEGORY, "hud_lyric_x", -1, "")
            .getInt(-1);
        hudLyricY = configuration.get(HUD_CATEGORY, "hud_lyric_y", -1, "")
            .getInt(-1);
        hudStateX = configuration.get(HUD_CATEGORY, "hud_state_x", -1, "")
            .getInt(-1);
        hudStateY = configuration.get(HUD_CATEGORY, "hud_state_y", -1, "")
            .getInt(-1);
        hudPicX = configuration.get(HUD_CATEGORY, "hud_pic_x", -1, "")
            .getInt(-1);
        hudPicY = configuration.get(HUD_CATEGORY, "hud_pic_y", -1, "")
            .getInt(-1);

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
        configuration.get(Configuration.CATEGORY_GENERAL, PAUSE_AT_FREEZE, false, "")
            .set(pauseAtFreeze);
        configuration.get(Configuration.CATEGORY_GENERAL, "debug", false, "")
            .set(debug);
        saveHudProperties(configuration);
        configuration.save();
    }

    /** 是否保存过 HUD 位置 (本地优先于服务端配置) */
    public static boolean hasHudPos() {
        return hudInfoX != -1 || hudLyricX != -1 || hudStateX != -1 || hudPicX != -1;
    }

    /**
     * 用本地保存的 HUD 位置覆盖传入的 HudPosObj (收到服务端 HUD_DATA 时调用)
     */
    public static void loadHudPos(HudPosObj obj) {
        if (obj == null) {
            return;
        }
        if (obj.info != null && hudInfoX != -1) {
            obj.info.x = hudInfoX;
            obj.info.y = hudInfoY;
        }
        if (obj.lyric != null && hudLyricX != -1) {
            obj.lyric.x = hudLyricX;
            obj.lyric.y = hudLyricY;
        }
        if (obj.state != null && hudStateX != -1) {
            obj.state.x = hudStateX;
            obj.state.y = hudStateY;
        }
        if (obj.pic != null && hudPicX != -1) {
            obj.pic.x = hudPicX;
            obj.pic.y = hudPicY;
        }
    }

    /**
     * 可视化配置界面松手时保存 HUD 位置到 FMusic.cfg
     */
    public static void saveHudPos(HudPosObj obj) {
        if (obj == null || configFile == null) {
            return;
        }
        if (obj.info != null) {
            hudInfoX = obj.info.x;
            hudInfoY = obj.info.y;
        }
        if (obj.lyric != null) {
            hudLyricX = obj.lyric.x;
            hudLyricY = obj.lyric.y;
        }
        if (obj.state != null) {
            hudStateX = obj.state.x;
            hudStateY = obj.state.y;
        }
        if (obj.pic != null) {
            hudPicX = obj.pic.x;
            hudPicY = obj.pic.y;
        }
        Configuration configuration = new Configuration(configFile);
        configuration.load();
        saveHudProperties(configuration);
        configuration.save();
    }

    private static void saveHudProperties(Configuration configuration) {
        configuration.get(HUD_CATEGORY, "hud_info_x", -1, "")
            .set(hudInfoX);
        configuration.get(HUD_CATEGORY, "hud_info_y", -1, "")
            .set(hudInfoY);
        configuration.get(HUD_CATEGORY, "hud_lyric_x", -1, "")
            .set(hudLyricX);
        configuration.get(HUD_CATEGORY, "hud_lyric_y", -1, "")
            .set(hudLyricY);
        configuration.get(HUD_CATEGORY, "hud_state_x", -1, "")
            .set(hudStateX);
        configuration.get(HUD_CATEGORY, "hud_state_y", -1, "")
            .set(hudStateY);
        configuration.get(HUD_CATEGORY, "hud_pic_x", -1, "")
            .set(hudPicX);
        configuration.get(HUD_CATEGORY, "hud_pic_y", -1, "")
            .set(hudPicY);
    }
}
