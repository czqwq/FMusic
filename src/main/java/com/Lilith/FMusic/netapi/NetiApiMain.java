package com.Lilith.FMusic.netapi;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.util.StatCollector;

import com.Lilith.FMusic.netapi.obj.music.info.InfoObj;
import com.Lilith.FMusic.netapi.obj.music.list.DataObj;
import com.Lilith.FMusic.netapi.obj.music.lyric.WLyricObj;
import com.Lilith.FMusic.netapi.obj.music.search.SearchDataObj;
import com.Lilith.FMusic.netapi.obj.music.search.songs;
import com.Lilith.FMusic.netapi.obj.music.trialinfo.TrialInfoObj;
import com.Lilith.FMusic.netapi.obj.program.info.PrInfoObj;
import com.Lilith.FMusic.server.core.FMusic;
import com.Lilith.FMusic.server.core.IMusicApi;
import com.Lilith.FMusic.server.core.music.LyricSave;
import com.Lilith.FMusic.server.core.objs.HttpResObj;
import com.Lilith.FMusic.server.core.objs.SearchMusicObj;
import com.Lilith.FMusic.server.core.objs.message.ARG;
import com.Lilith.FMusic.server.core.objs.music.SearchPageObj;
import com.Lilith.FMusic.server.core.objs.music.SongInfoObj;
import com.Lilith.FMusic.server.core.saves.MusicListSave;
import com.Lilith.FMusic.server.core.utils.Function;
import com.google.gson.JsonObject;

public class NetiApiMain implements IMusicApi {

    private boolean isUpdate;

    public NetiApiMain() {
        FMusic.log.data(StatCollector.translateToLocal("fmusic.log.netapi.init"));

        HttpResObj res = NetApiHttpClient.get("https://music.163.com", "");
        if (res == null || !res.ok) {
            FMusic.log.data(StatCollector.translateToLocal("fmusic.log.netapi.init_fail"));
        }
    }

    @Override
    public boolean isBusy() {
        return isUpdate;
    }

    @Override
    public String getMusicId(String arg) {
        if (arg.contains("id=") && !arg.contains("/?userid")) {
            if (arg.contains("&uct2")) {
                return Function.getString(arg, "id=", "&uct2");
            } else if (arg.contains("&user")) return Function.getString(arg, "id=", "&user");
            else return Function.getString(arg, "id=", null);
        } else if (arg.contains("song/")) {
            if (arg.contains("/?userid")) return Function.getString(arg, "song/", "/?userid");
            else return Function.getString(arg, "song/", null);
        } else return arg;
    }

    @Override
    public boolean checkId(String id) {
        return Function.isInteger(id);
    }

    /**
     * 获取音乐详情
     *
     * @param id     音乐ID
     * @param player 用户名
     * @param isList 是否是空闲列表
     * @return 结果
     */
    private SongInfoObj getMusicDetail(String id, String player, boolean isList) {
        JsonObject params = new JsonObject();
        params.addProperty("c", "[{\"id\":" + id + "}]");

        HttpResObj res = NetApiHttpClient
            .post("https://music.163.com/api/v3/song/detail", params, EncryptType.WEAPI, null);
        if (res != null && res.ok) {
            InfoObj temp = FMusic.gson.fromJson(res.data, InfoObj.class);
            if (temp.isOk()) {
                params = new JsonObject();
                params.addProperty("ids", "[" + id + "]");
                params.addProperty("br", "320000");
                res = NetApiHttpClient
                    .post("https://music.163.com/weapi/song/enhance/player/url", params, EncryptType.WEAPI, null);
                if (res == null || !res.ok) {
                    FMusic.log.data(StatCollector.translateToLocal("fmusic.log.netapi.copyright_fail"));
                    return null;
                }
                TrialInfoObj obj = FMusic.gson.fromJson(res.data, TrialInfoObj.class);
                com.Lilith.FMusic.server.core.objs.music.TrialInfoObj info = new com.Lilith.FMusic.server.core.objs.music.TrialInfoObj();
                info.start = obj.getFreeTrialInfo()
                    .getStart();
                info.end = obj.getFreeTrialInfo()
                    .getEnd();
                return new SongInfoObj(
                    temp.getAuthor(),
                    temp.getName(),
                    id,
                    temp.getAlia(),
                    player,
                    temp.getAl(),
                    isList,
                    temp.getLength(),
                    temp.getPicUrl(),
                    obj.isTrial(),
                    info,
                    getId());
            }
        }
        return null;
    }

    @Override
    public String getId() {
        return "netapi";
    }

    /**
     * 获取音乐数据
     *
     * @param id     音乐ID
     * @param player 用户名
     * @param isList 是否是空闲列表
     * @return 结果
     */
    public SongInfoObj getMusic(String id, String player, boolean isList) {
        SongInfoObj info = getMusicDetail(id, player, isList);
        if (info != null) return info;
        JsonObject params = new JsonObject();
        params.addProperty("id", id);
        HttpResObj res = NetApiHttpClient
            .post("https://music.163.com/api/dj/program/detail", params, EncryptType.WEAPI, null);
        if (res != null && res.ok) {
            PrInfoObj temp = FMusic.gson.fromJson(res.data, PrInfoObj.class);
            if (temp.isOk()) {
                return new SongInfoObj(
                    temp.getAuthor(),
                    temp.getName(),
                    temp.getId(),
                    temp.getAlia(),
                    player,
                    "电台",
                    isList,
                    temp.getLength(),
                    null,
                    false,
                    null,
                    getId());
            } else {
                FMusic.log.data(StatCollector.translateToLocal("fmusic.log.netapi.song_empty"));
            }
        }
        return info;
    }

    /**
     * 获取播放链接
     *
     * @param id 音乐ID
     * @return 结果
     */
    public String getPlayUrl(String id) {
        JsonObject params = new JsonObject();
        params.addProperty("ids", "[" + id + "]");
        params.addProperty("level", "exhigh");
        params.addProperty("encodeType", "aac");
        HttpResObj res = NetApiHttpClient
            .post("https://music.163.com/weapi/song/enhance/player/url/v1", params, EncryptType.WEAPI, null);
        if (res != null && res.ok) {
            try {
                TrialInfoObj obj = FMusic.gson.fromJson(res.data, TrialInfoObj.class);
                return obj.getUrl();
            } catch (Exception e) {
                FMusic.log.data(StatCollector.translateToLocalFormatted("fmusic.log.netapi.play_parse_err", res.data));
                e.printStackTrace();
            }
        }
        return null;
    }

    /**
     * 添加空闲歌单
     *
     * @param id     歌单id
     * @param sender 发送者
     */
    public void setList(String id, Object sender) {
        final Thread thread = new Thread(() -> {
            JsonObject params = new JsonObject();
            params.addProperty("id", id);
            params.addProperty("n", 100000);
            params.addProperty("s", 8);
            HttpResObj res = NetApiHttpClient
                .post("https://music.163.com/api/v6/playlist/detail", params, EncryptType.API, null);
            if (res != null && res.ok) try {
                isUpdate = true;
                DataObj obj = FMusic.gson.fromJson(res.data, DataObj.class);
                MusicListSave.addIdleList(obj.getPlaylist(), getId());
                FMusic.side.sendMessageTask(
                    sender,
                    FMusic.getMessage().musicPlay.listMusic.get.replace(ARG.name, obj.getName()));
            } catch (Exception e) {
                FMusic.log.data(StatCollector.translateToLocal("fmusic.log.netapi.list_error"));
                e.printStackTrace();
            }
            isUpdate = false;
        }, "FMusic_setList");
        thread.start();
    }

    /**
     * 获取歌词
     *
     * @param id 歌曲id
     * @return 结果
     */
    public LyricSave getLyric(String id) {
        LyricSave lyric = new LyricSave();
        JsonObject params = new JsonObject();
        params.addProperty("id", id);
        params.addProperty("cp", false);
        params.addProperty("tv", 0);
        params.addProperty("lv", 0);
        params.addProperty("rv", 0);
        params.addProperty("kv", 0);
        params.addProperty("yv", 0);
        params.addProperty("ytv", 0);
        params.addProperty("rtv", 0);
        HttpResObj res = NetApiHttpClient.post(
            "https://interface3.music.163.com/eapi/song/lyric/v1",
            params,
            EncryptType.EAPI,
            "/api/song/lyric/v1");
        if (res != null && res.ok) {
            try {
                WLyricObj obj = FMusic.gson.fromJson(res.data, WLyricObj.class);
                LyricDecoder docoder = new LyricDecoder();
                for (int times = 0; times < 3; times++) {
                    if (docoder.check(obj)) {
                        FMusic.log
                            .data(StatCollector.translateToLocalFormatted("fmusic.log.netapi.lyric_retry", times));
                    } else {
                        if (docoder.isHave) {
                            lyric.setHaveLyric(FMusic.getConfig().sendLyric);
                            lyric.setLyric(docoder.getLyrics());
                            if (docoder.isHaveK) {
                                lyric.setKlyric(docoder.getKLyric());
                            }
                        }
                        return lyric;
                    }
                    Thread.sleep(1000);
                }
                FMusic.log.data(StatCollector.translateToLocal("fmusic.log.netapi.lyric_fail"));
            } catch (Exception e) {
                FMusic.log.data(StatCollector.translateToLocal("fmusic.log.netapi.lyric_parse_err"));
                e.printStackTrace();
            }
        }
        return lyric;
    }

    /**
     * 搜歌
     *
     * @param name      关键字
     * @param isDefault 是否是默认方式
     * @return 结果
     */
    public SearchPageObj search(String[] name, boolean isDefault) {
        List<SearchMusicObj> resData = new ArrayList<>();
        int maxpage;

        StringBuilder name1 = new StringBuilder();
        for (int a = isDefault ? 0 : 1; a < name.length; a++) {
            name1.append(name[a])
                .append(" ");
        }
        String MusicName = name1.toString();
        MusicName = MusicName.substring(0, MusicName.length() - 1);

        JsonObject params = new JsonObject();
        params.addProperty("s", MusicName);
        params.addProperty("type", 1);
        params.addProperty("limit", 30);
        params.addProperty("offset", 0);

        HttpResObj res = NetApiHttpClient
            .post("https://music.163.com/weapi/search/get", params, EncryptType.WEAPI, null);
        if (res != null && res.ok) {
            SearchDataObj obj = FMusic.gson.fromJson(res.data, SearchDataObj.class);
            if (obj != null && obj.isOk()) {
                List<songs> res1 = obj.getResult();
                SearchMusicObj item;
                for (songs temp : res1) {
                    item = new SearchMusicObj(
                        String.valueOf(temp.getId()),
                        temp.getName(),
                        temp.getArtists(),
                        temp.getAlbum());
                    resData.add(item);
                }
                maxpage = Math.max(1, (res1.size() + 9) / 10);
                return new SearchPageObj(resData, maxpage, getId());
            } else {
                FMusic.log.data(StatCollector.translateToLocal("fmusic.log.netapi.search_error"));

            }
        }
        return null;
    }
}
