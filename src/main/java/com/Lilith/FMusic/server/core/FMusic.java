package com.Lilith.FMusic.server.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PushbackReader;
import java.io.Reader;
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

import com.Lilith.FMusic.netapi.NetiApiMain;
import com.Lilith.FMusic.server.api.kugou.KugouApiMain;
import com.Lilith.FMusic.server.api.kugou.KugouHttpClient;
import com.Lilith.FMusic.server.api.qqmusic.QQMusicApiMain;
import com.Lilith.FMusic.server.api.qqmusic.QQMusicHttpClient;
import com.Lilith.FMusic.server.bili.BiliMusicBridge;
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
    public static final String configVersion = "402";
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
     *
     * <p>
     * 缺个别配置段时只补齐缺失段并保留用户其余设置 (issues/2: 旧版本整体重置成默认,
     * 会把手改的 mutePlayMessage/adminList 等全部覆盖掉)。只有文件为空/不存在时才生成完整默认配置。
     */
    public static void configCheck() {
        if (config == null) {
            config = ConfigObj.make();
            log.data(StatCollector.translateToLocal("fmusic.log.core.config_wrong"));
            saveConfig();
            return;
        }
        if (config.check()) {
            // check() 已经在原地补齐缺失的段: 直接落盘, 用户手改的其余设置原样保留
            config.version = configVersion;
            log.data(StatCollector.translateToLocal("fmusic.log.core.config_repair"));
            saveConfig();
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
     * 获取消息模板 (文本本身在 lang 文件里, 由客户端按自己的语言渲染)
     *
     * @return 消息对象
     */
    public static MessageObj getMessage() {
        if (message == null) {
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
        // 文本已迁移到 lang i18n (assets/fmusic/lang), 不再有 message.json
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
            IMusicApi api = new NetiApiMain();
            MUSIC_APIS.put(api.getId(), api);
            FMusic.log.data(StatCollector.translateToLocalFormatted("fmusic.log.core.api_registered", api.getId()));
        } catch (Exception e) {
            FMusic.log.data(StatCollector.translateToLocal("fmusic.log.core.api_reg_fail"));
            e.printStackTrace();
        }

        // 注册内置音乐API: qqmusic (QQ音乐) / kugou (酷狗音乐)
        try {
            IMusicApi api = new QQMusicApiMain();
            MUSIC_APIS.put(api.getId(), api);
            FMusic.log.data(StatCollector.translateToLocalFormatted("fmusic.log.core.api_registered", api.getId()));
        } catch (Exception e) {
            FMusic.log.data(StatCollector.translateToLocal("fmusic.log.core.api_reg_qq_fail"));
            e.printStackTrace();
        }
        try {
            IMusicApi api = new KugouApiMain();
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
            BiliMusicBridge.start();
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
        BiliMusicBridge.stop();
        side.sendStop();
        log.data(StatCollector.translateToLocal("fmusic.log.core.stopped"));
    }

    /**
     * 加载配置文件
     */
    private static void loadConfig() {
        try {
            config = gson.fromJson(openUtf8(configFile), ConfigObj.class);
            configCheck();

            Type listType = new TypeToken<ArrayList<CookieObj>>() {}.getType();
            cookie = gson.fromJson(openUtf8(cookieFile), listType);
            if (cookie == null) {
                cookie = new ArrayList<>();
                saveCookie();
            }

            // 刷新 Kugou/QQ 独立 cookie 文件缓存 (/music reload 时生效)
            KugouHttpClient.clearCookieCache();
            QQMusicHttpClient.clearCookieCache();

            if (config != null && !configVersion.equalsIgnoreCase(config.version)) {
                log.data(StatCollector.translateToLocal("fmusic.log.core.config_update"));
            }

            replacer = new StringReplacer();
            if (config != null && !config.lyricReplace.isEmpty()) {
                for (Map.Entry<String, String> item : config.lyricReplace.entrySet()) {
                    replacer.put(item.getKey(), item.getValue());
                }
            }

            BanSave.loadBan();
            HudSave.loadHud();
            MusicListSave.loadMusic();
        } catch (Exception e) {
            // JSON 语法/编码错误 (常见: 手改时多了/少了逗号引号, 或编辑器存成 UTF-8 with BOM):
            // 用默认配置继续运行, 但不写盘 —— 用户手改的文件必须保留, 修好 JSON 后 /music reload 即可生效
            log.data(StatCollector.translateToLocal("fmusic.log.core.config_read_err"));
            e.printStackTrace();
            if (config == null) {
                config = ConfigObj.make();
            }
            replacer = new StringReplacer();
        }
    }

    /**
     * 以 UTF-8 打开文本文件并剥离可能存在的 BOM。
     *
     * <p>
     * Windows 记事本等编辑器会把文件保存成 UTF-8 with BOM, 而 UTF-8 的 InputStreamReader
     * 不会剥掉 \uFEFF, Gson 遇到它直接解析失败 (issues/2 "手改 config.json 不生效"的帮凶)。
     */
    static Reader openUtf8(File file) throws IOException {
        PushbackReader reader = new PushbackReader(
            new InputStreamReader(Files.newInputStream(file.toPath()), StandardCharsets.UTF_8),
            1);
        int first = reader.read();
        // 0xFEFF = BOM; 用数值避免源码里出现不可见字符
        if (first != -1 && first != 0xFEFF) {
            reader.unread(first);
        }
        return reader;
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
            cookieFile = new File(file, "cookie.json");
            if (!configFile.exists()) {
                configFile.createNewFile();
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
