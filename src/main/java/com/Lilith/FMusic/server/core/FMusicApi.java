package com.Lilith.FMusic.server.core;

import com.Lilith.FMusic.codec.HudPosObj;
import com.Lilith.FMusic.codec.MusicPack;
import com.Lilith.FMusic.server.core.saves.HudSave;

public class FMusicApi {

    /**
     * 注册音乐API
     *
     * @param api 音乐API
     * @return 返回序号
     */
    public static int registerApi(IMusicApi api) {
        FMusic.MUSIC_APIS.put(api.getId(), api);
        return FMusic.MUSIC_APIS.size() - 1;
    }

    /**
     * 获取默认音乐API
     *
     * @return 音乐API
     */
    public static IMusicApi getApiMusic() {
        return FMusic.MUSIC_APIS.get(FMusic.getConfig().defaultApi);
    }

    /**
     * 获取某个音乐API
     *
     * @param api 音乐API
     * @return 音乐API
     */
    public static IMusicApi getApiMusic(String api) {
        return FMusic.MUSIC_APIS.get(api);
    }

    /**
     * 发送播放音乐
     *
     * @param name 用户名
     * @param url  链接
     */
    public static void playMusic(String name, String url) {
        FMusic.side.sendMusic(name, url);
    }

    /**
     * 更新玩家Hud数据
     *
     * @param name 用户名
     * @param data 数据
     */
    public static void sendInfo(String name, String data) {
        FMusic.side.sendInfo(name, data);
    }

    /**
     * 更新玩家Hud歌词
     *
     * @param name 用户名
     * @param pack 位置
     */
    public static void sendLyric(String name, MusicPack.LyricMusicPack pack) {
        FMusic.side.sendLyric(name, pack);
    }

    /**
     * 发送图片
     *
     * @param name 用户名
     * @param url  图片地址
     */
    public static void sendPic(String name, String url) {
        FMusic.side.sendPic(name, url);
    }

    /**
     * 停止播放
     *
     * @param name 用户名
     */
    public static void sendStop(String name) {
        FMusic.side.sendStop(name);
    }

    /**
     * 获取玩家Hud信息
     *
     * @param player 玩家
     * @return Hud信息
     */
    public static HudPosObj getHud(String player) {
        return HudSave.getOrNew(player);
    }

    /**
     * 设置玩家Hud
     *
     * @param player 玩家
     * @param hud    Hud信息
     */
    public static void setHud(String player, HudPosObj hud) {
        HudSave.update(player, hud);
    }
}
