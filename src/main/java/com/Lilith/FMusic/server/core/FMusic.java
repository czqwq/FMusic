package com.Lilith.FMusic.server.core;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;

import net.minecraft.util.StatCollector;

import com.Lilith.FMusic.server.core.music.MusicHttpClient;
import com.Lilith.FMusic.server.core.music.MusicSearch;
import com.Lilith.FMusic.server.core.music.PlayMusic;
import com.Lilith.FMusic.server.core.music.PlayRuntime;
import com.Lilith.FMusic.server.core.objs.CookieObj;
import com.Lilith.FMusic.server.core.objs.config.ConfigObj;
import com.Lilith.FMusic.server.core.objs.message.MessageObj;
import com.Lilith.FMusic.server.core.objs.music.SongInfoObj;
import com.Lilith.FMusic.server.core.saves.BanSave;
import com.Lilith.FMusic.server.core.saves.HudSave;
import com.Lilith.FMusic.server.core.saves.MusicListSave;
import com.Lilith.FMusic.server.core.saves.SaveTask;
import com.Lilith.FMusic.server.core.side.BaseSide;
import com.Lilith.FMusic.server.core.side.IFMusicLogger;
import com.Lilith.FMusic.server.core.utils.MusicApiLoader;
import com.Lilith.FMusic.server.core.utils.StringReplacer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

public class FMusic {

    public static final Gson gson = new GsonBuilder().disableHtmlEscaping()
        .setPrettyPrinting()
        .create();
    public static final Random random = new Random();

    public static final Map<String, IMusicApi> MUSIC_APIS = new HashMap<>();

    public static final String SERVER_DIR = "fmusic_server/";

    /**
     * 客户端插件信道名
     */
    public static final String channel = "fmusic:channel";
    /**
     * BC插件信道名
     */
    public static final String channelBC = "fmusic:channelbc";
    /**
     * 插件版本号
     */
    public static final String version = "4.0.0";
    /**
     * 单人游戏暂停(pause_at_freeze)时冻结音乐计时;
     * 由客户端 tick 设置, 服务端计时与客户端歌词同步暂停
     */
    public static boolean frozen = false;
    /**
     * 配置文件版本号
     */
    public static final String configVersion = "401";
    /**
     * 语言文件配置版本号
     */
    public static final String messageVersion = "400";
    /**
     * 日志
     */
    public static IFMusicLogger log;
    /**
     * 服务器端操作
     */
    public static BaseSide side;
    /**
     * 是否在运行
     */
    public static boolean isRun;
    /**
     * Cookie对象
     */
    public static List<CookieObj> cookie;
    /**
     * 经济插件对象
     */
    public static IEconomy economy;
    /**
     * 配置对象
     */
    private static ConfigObj config;
    /**
     * 语言对象
     */
    private static MessageObj message;
    /**
     * 配置文件
     */
    private static File configFile;
    /**
     * Cookie文件
     */
    private static File cookieFile;
    /**
     * 语言文件
     */
    private static File messageFile;

    private static File apis;
    /**
     * 正则替换器
     */
    private static StringReplacer replacer;

    public static StringReplacer getReplacer() {
        return replacer;
    }

    /**
     * 检查配置文件完整性
     */
    public static void configCheck() {
        if (config == null || config.check()) {
            config = ConfigObj.make();
            log.data(StatCollector.translateToLocal("fmusic.log.core.config_wrong"));
            saveConfig();
        }
    }

    /**
     * 检查语言文件完整性
     */
    private static void messageCheck() {
        if (message == null || message.check()) {
            message = MessageObj.make();
            log.data(StatCollector.translateToLocal("fmusic.log.core.message_wrong"));
            saveMessage();
        }
    }

    /**
     * 检查是否需要放歌
     *
     * @param name      用户名
     * @param server    服务器名
     * @param checkPlay 是否检查正在播放的列表
     * @return 是否跳过放歌
     */
    public static boolean isSkip(String name, String server, boolean checkPlay) {
        try {
            if (server != null && BanSave.haveBanServer(server)) return true;
            if (BanSave.checkMutePlayer(name)) return true;
            if (PlayMusic.nowPlayMusic != null && PlayMusic.nowPlayMusic.isList() && BanSave.checkMuteListPlayer(name))
                return true;
            if (!checkPlay) return false;
            return PlayMusic.containNowPlay(name);
        } catch (NoSuchElementException e) {
            return true;
        }
    }

    /**
     * 检查是否需要放歌
     *
     * @param name      用户名
     * @param server    服务器名
     * @param checkPlay 是否检查正在播放的列表
     * @param islist    是否为空闲歌单的歌
     * @return 是否跳过放歌
     */
    public static boolean isSkip(String name, String server, boolean checkPlay, boolean islist) {
        try {
            name = name.toLowerCase();
            if (server != null && BanSave.haveBanServer(server)) return true;
            if (BanSave.checkMutePlayer(name)) return true;
            if (islist && BanSave.checkMuteListPlayer(name)) return true;
            if (!checkPlay) return false;
            return PlayMusic.containNowPlay(name);
        } catch (NoSuchElementException e) {
            return true;
        }
    }

    /**
     * 获取配置文件
     *
     * @return 配置对象
     */
    public static ConfigObj getConfig() {
        if (config == null) {
            log.data(StatCollector.translateToLocal("fmusic.log.core.config_wrong_default"));
            config = ConfigObj.make();
        }
        return config;
    }

    /**
     * 获取语言文件
     *
     * @return 语言对象
     */
    public static MessageObj getMessage() {
        if (message == null) {
            log.data(StatCollector.translateToLocal("fmusic.log.core.message_wrong_default"));
            message = MessageObj.make();
        }
        return message;
    }

    /**
     * 保存配置文件
     */
    public static void saveConfig() {
        try {
            String data = gson.toJson(config);
            FileOutputStream out = new FileOutputStream(configFile);
            OutputStreamWriter write = new OutputStreamWriter(out, StandardCharsets.UTF_8);
            write.write(data);
            write.close();
            out.close();
        } catch (Exception e) {
            log.data(StatCollector.translateToLocal("fmusic.log.core.config_save_err"));
            e.printStackTrace();
        }
    }

    public static void saveMessage() {
        try {
            String data = gson.toJson(message);
            FileOutputStream out = new FileOutputStream(messageFile);
            OutputStreamWriter write = new OutputStreamWriter(out, StandardCharsets.UTF_8);
            write.write(data);
            write.close();
            out.close();
        } catch (Exception e) {
            log.data(StatCollector.translateToLocal("fmusic.log.core.message_save_err"));
            e.printStackTrace();
        }
    }

    /**
     * 保存Cookie
     */
    public static void saveCookie() {
        try {
            String data = gson.toJson(cookie);
            FileOutputStream out = new FileOutputStream(cookieFile);
            OutputStreamWriter write = new OutputStreamWriter(out, StandardCharsets.UTF_8);
            write.write(data);
            write.close();
        } catch (Exception e) {
            log.data(StatCollector.translateToLocal("fmusic.log.core.cookie_save_err"));
            e.printStackTrace();
        }
    }

    /**
     * 启动插件
     */
    public static void start() {
        isRun = true;

        MusicHttpClient.init();

        PlayMusic.start();
        PlayRuntime.start();
        MusicSearch.start();
        SaveTask.start();

        // 注册内置音乐API: netapi (网易云音乐)
        try {
            IMusicApi api = new com.Lilith.FMusic.netapi.NetiApiMain();
            MUSIC_APIS.put(api.getId(), api);
            FMusic.log.data(StatCollector.translateToLocalFormatted("fmusic.log.core.api_registered", api.getId()));
        } catch (Exception e) {
            FMusic.log.data(StatCollector.translateToLocal("fmusic.log.core.api_reg_fail"));
            e.printStackTrace();
        }

        // 注册内置音乐API: qqmusic (QQ音乐) / kugou (酷狗音乐)
        try {
            IMusicApi api = new com.Lilith.FMusic.server.api.qqmusic.QQMusicApiMain();
            MUSIC_APIS.put(api.getId(), api);
            FMusic.log.data(StatCollector.translateToLocalFormatted("fmusic.log.core.api_registered", api.getId()));
        } catch (Exception e) {
            FMusic.log.data(StatCollector.translateToLocal("fmusic.log.core.api_reg_qq_fail"));
            e.printStackTrace();
        }
        try {
            IMusicApi api = new com.Lilith.FMusic.server.api.kugou.KugouApiMain();
            MUSIC_APIS.put(api.getId(), api);
            FMusic.log.data(StatCollector.translateToLocalFormatted("fmusic.log.core.api_registered", api.getId()));
        } catch (Exception e) {
            FMusic.log.data(StatCollector.translateToLocal("fmusic.log.core.api_reg_kugou_fail"));
            e.printStackTrace();
        }

        List<IMusicApi> list = MusicApiLoader.loadFromDirectory(apis);
        for (IMusicApi item : list) {
            FMusic.log.data(StatCollector.translateToLocalFormatted("fmusic.log.core.api_external", item.getId()));
            MUSIC_APIS.put(item.getId(), item);
        }

        if (MUSIC_APIS.isEmpty()) {
            FMusic.log.data(StatCollector.translateToLocal("fmusic.log.core.no_api"));
        }

        // B站点歌 (BiliMusicBridge, 直播间弹幕点歌; 配置 room-id 后自动连接)
        try {
            com.Lilith.FMusic.server.bili.BiliMusicBridge.start();
        } catch (Exception e) {
            log.data(StatCollector.translateToLocal("fmusic.log.core.bili_start_fail"));
            e.printStackTrace();
        }

        log.data(StatCollector.translateToLocalFormatted("fmusic.log.core.started", version));
    }

    /**
     * 停止插件
     */
    public static void stop() {
        isRun = false;
        PlayRuntime.stop();
        SaveTask.stop();
        com.Lilith.FMusic.server.bili.BiliMusicBridge.stop();
        side.sendStop();
        log.data(StatCollector.translateToLocal("fmusic.log.core.stopped"));
    }

    /**
     * 加载配置文件
     */
    private static void loadConfig() {
        try {
            InputStreamReader reader = new InputStreamReader(
                Files.newInputStream(configFile.toPath()),
                StandardCharsets.UTF_8);
            BufferedReader bf = new BufferedReader(reader);
            config = gson.fromJson(bf, ConfigObj.class);
            bf.close();
            reader.close();
            configCheck();

            reader = new InputStreamReader(Files.newInputStream(messageFile.toPath()), StandardCharsets.UTF_8);
            bf = new BufferedReader(reader);
            message = gson.fromJson(bf, MessageObj.class);
            bf.close();
            reader.close();
            messageCheck();

            if (!message.version.equalsIgnoreCase(messageVersion)) {
                log.data(StatCollector.translateToLocal("fmusic.log.core.lang_version_wrong"));
            }

            reader = new InputStreamReader(Files.newInputStream(cookieFile.toPath()), StandardCharsets.UTF_8);
            bf = new BufferedReader(reader);
            Type listType = new TypeToken<ArrayList<CookieObj>>() {}.getType();
            cookie = gson.fromJson(bf, listType);
            bf.close();
            reader.close();
            if (cookie == null) {
                cookie = new ArrayList<>();
                saveCookie();
            }

            // 刷新 Kugou/QQ 独立 cookie 文件缓存 (/music reload 时生效)
            com.Lilith.FMusic.server.api.kugou.KugouHttpClient.clearCookieCache();
            com.Lilith.FMusic.server.api.qqmusic.QQMusicHttpClient.clearCookieCache();

            if (!FMusic.configVersion.equalsIgnoreCase(config.version)) {
                log.data(StatCollector.translateToLocal("fmusic.log.core.config_update"));
            }

            replacer = new StringReplacer();
            if (!config.lyricReplace.isEmpty()) {
                for (Map.Entry<String, String> item : config.lyricReplace.entrySet()) {
                    replacer.put(item.getKey(), item.getValue());
                }
            }

            BanSave.loadBan();
            HudSave.loadHud();
            MusicListSave.loadMusic();
        } catch (Exception e) {
            log.data(StatCollector.translateToLocal("fmusic.log.core.config_read_err"));
            e.printStackTrace();
        }
    }

    /**
     * 加入时播放
     *
     * @param player 用户名
     */
    public static void joinPlay(String player) {
        if (FMusic.side == null) {
            return;
        }
        FMusic.side.runTask(() -> joinPlayNow(player), FMusic.config.joinDelay);
    }

    public static void joinPlayNow(String player) {
        SaveTask.task(() -> {
            String player1 = player.toLowerCase();
            Object player2 = FMusic.side.getPlayer(player1);
            String server = FMusic.side.getPlayerServer(player2);
            if (server != null && BanSave.haveBanServer(server)) {
                return;
            }
            if (BanSave.checkMutePlayer(player1)) {
                return;
            }
            if (BanSave.checkMuteListPlayer(player1)) {
                return;
            }

            FMusic.side.runTask(() -> {
                SongInfoObj music = PlayMusic.nowPlayMusic;
                if (music != null && PlayMusic.url != null) {
                    FMusic.side.sendHudPos(player1);
                    FMusic.side.sendMusic(player1, PlayMusic.url);
                    FMusic.side.sendPic(player1, music.getPicUrl());
                    FMusic.side.runTask(() -> FMusic.side.sendPos(player1, (int) PlayMusic.musicNowTime), 20);
                }
            });
        });
    }

    /**
     * 读取配置文件
     *
     * @param file 配置文件文件夹
     */
    public static void init(File file) {
        try {
            file.mkdir();

            configFile = new File(file, "config.json");
            messageFile = new File(file, "message.json");
            cookieFile = new File(file, "cookie.json");
            if (!configFile.exists()) {
                configFile.createNewFile();
            }
            if (!messageFile.exists()) {
                messageFile.createNewFile();
            }
            if (!cookieFile.exists()) {
                cookieFile.createNewFile();
            }

            BanSave.init(file);
            HudSave.init(file);
            MusicListSave.init(file);

            loadConfig();

            apis = new File(file, "api");
            apis.mkdirs();

            isRun = true;
        } catch (IOException e) {
            isRun = false;
            log.data(StatCollector.translateToLocal("fmusic.log.core.start_fail"));
            e.printStackTrace();
        }
    }
}
