package com.Lilith.FMusic.server.core.saves;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.minecraft.util.StatCollector;

import com.Lilith.FMusic.server.core.FMusic;
import com.Lilith.FMusic.server.core.objs.config.BanObj;

public class BanSave {

    private static BanObj ban = new BanObj();
    private static File banFile;

    public static boolean haveBanPlayer(String player) {
        return ban.banPlayers.contains(player);
    }

    public static boolean haveBanServer(String name) {
        name = name.toLowerCase();
        return ban.banServer.contains(name);
    }

    public static void init(File file) throws IOException {
        banFile = new File(file, "ban.json");
        if (!banFile.exists()) {
            banFile.createNewFile();
        }
    }

    private static void banCheck() {
        if (ban == null || ban.check()) {
            ban = BanObj.make();
            FMusic.log.data(StatCollector.translateToLocal("fmusic.log.core.ban_wrong"));
            saveBan();
        }
    }

    public static void saveBan() {
        try {
            String data = FMusic.gson.toJson(ban);
            FileOutputStream out = new FileOutputStream(banFile);
            OutputStreamWriter write = new OutputStreamWriter(out, StandardCharsets.UTF_8);
            write.write(data);
            write.close();
            out.close();
        } catch (Exception e) {
            FMusic.log.data(StatCollector.translateToLocal("fmusic.log.core.ban_save_err"));
            e.printStackTrace();
        }
    }

    public static void loadBan() throws IOException {
        InputStreamReader reader = new InputStreamReader(
            Files.newInputStream(banFile.toPath()),
            StandardCharsets.UTF_8);
        BufferedReader bf = new BufferedReader(reader);
        ban = FMusic.gson.fromJson(bf, BanObj.class);
        bf.close();
        reader.close();
        banCheck();
    }

    public static void addBanMusic(String music, String api) {
        SaveTask.task(() -> {
            List<String> ids = ban.banMusics.get(api);
            if (ids == null) {
                ids = new ArrayList<>();
            }
            ids.add(music);
            ban.banMusics.put(api, ids);
            saveBan();
        });
    }

    public static void removeBanMusic(String id, String api) {
        SaveTask.task(() -> {
            List<String> ids = ban.banMusics.get(api);
            if (ids == null) {
                ids = new ArrayList<>();
            }
            ids.remove(id);
            ban.banMusics.put(api, ids);
            saveBan();
        });
    }

    /**
     * @param id 歌曲ID
     * @return 结果
     */
    public static boolean checkBanMusic(String id, String api) {
        List<String> ids = ban.banMusics.get(api);
        if (ids == null) {
            return false;
        }

        return ids.contains(id);
    }

    public static void clearBan() {
        SaveTask.task(() -> {
            ban.banMusics.clear();
            saveBan();
        });
    }

    public static void addBanPlayer(String player) {
        SaveTask.task(() -> {
            String player1 = player.toLowerCase(Locale.ROOT);
            ban.banPlayers.add(player1);
            saveBan();
        });
    }

    public static void removeBanPlayer(String player) {
        SaveTask.task(() -> {
            String player1 = player.toLowerCase(Locale.ROOT);
            ban.banPlayers.remove(player1);
            saveBan();
        });
    }

    /**
     * 检查玩家是否在数据库
     *
     * @param name 用户名
     * @return 结果
     */
    public static boolean checkBanPlayer(String name) {
        name = name.toLowerCase(Locale.ROOT);
        return ban.banPlayers.contains(name);
    }

    public static void clearBanPlayer() {
        SaveTask.task(() -> {
            ban.banPlayers.clear();
            saveBan();
        });
    }

    public static void addMutePlayer(String player) {
        SaveTask.task(() -> {
            String player1 = player.toLowerCase(Locale.ROOT);
            ban.mutePlayers.add(player1);
            saveBan();
        });
    }

    public static void removeMutePlayer(String player) {
        SaveTask.task(() -> {
            String player1 = player.toLowerCase(Locale.ROOT);
            ban.mutePlayers.remove(player1);
            saveBan();
        });
    }

    public static boolean checkMutePlayer(String name) {
        name = name.toLowerCase(Locale.ROOT);
        return ban.mutePlayers.contains(name);
    }

    public static void addMuteListPlayer(String player) {
        SaveTask.task(() -> {
            String player1 = player.toLowerCase(Locale.ROOT);
            ban.muteListPlayers.add(player1);
            saveBan();
        });
    }

    public static void removeMuteListPlayer(String player) {
        SaveTask.task(() -> {
            String player1 = player.toLowerCase(Locale.ROOT);
            ban.muteListPlayers.remove(player1);
            saveBan();
        });
    }

    public static boolean checkMuteListPlayer(String name) {
        name = name.toLowerCase(Locale.ROOT);
        return ban.muteListPlayers.contains(name);
    }

    public static Set<String> getBanPlayers() {
        return ban.banPlayers;
    }
}
