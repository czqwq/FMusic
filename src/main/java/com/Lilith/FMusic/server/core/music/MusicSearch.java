package com.Lilith.FMusic.server.core.music;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.Lilith.FMusic.server.core.FMusic;
import com.Lilith.FMusic.server.core.IMusicApi;
import com.Lilith.FMusic.server.core.objs.SearchMusicObj;
import com.Lilith.FMusic.server.core.objs.message.ARG;
import com.Lilith.FMusic.server.core.objs.music.PlayerAddMusicObj;
import com.Lilith.FMusic.server.core.objs.music.SearchPageObj;

public class MusicSearch {

    /**
     * 搜歌结果
     * 玩家名 结果
     */
    private static final Map<String, SearchPageObj> searchSave = new HashMap<>();

    private static final Queue<PlayerAddMusicObj> tasks = new ConcurrentLinkedQueue<>();

    private static void task() {
        FMusic.log.data("歌曲搜索线程启动");
        while (FMusic.isRun) {
            try {
                PlayerAddMusicObj obj = tasks.poll();
                if (obj != null) {
                    IMusicApi api = FMusic.MUSIC_APIS.get(obj.api);
                    if (api == null) {
                        FMusic.side.sendMessageTask(obj.sender, FMusic.getMessage().musicPlay.error2);
                        continue;
                    }
                    SearchPageObj search = api.search(obj.args, obj.isDefault);
                    if (search == null) FMusic.side.sendMessageTask(
                        obj.sender,
                        FMusic.getMessage().search.cantSearch
                            .replace(ARG.name, obj.isDefault ? obj.args[0] : obj.args[1]));
                    else {
                        FMusic.side.sendMessageTask(obj.sender, FMusic.getMessage().search.res);
                        addSearch(obj.name, search);
                        FMusic.side.runTask(() -> showSearch(obj.sender, search));
                    }
                }
                Thread.sleep(100);
            } catch (Exception e) {
                FMusic.log.data("搜歌出现问题");
                e.printStackTrace();
            }
        }
        searchSave.clear();
        tasks.clear();
        FMusic.log.data("歌曲搜索线程停止");
    }

    public static void start() {
        new Thread(MusicSearch::task, "fmusic_search").start();
    }

    public static void addSearch(PlayerAddMusicObj obj) {
        tasks.add(obj);
    }

    /**
     * 展示搜歌结果
     *
     * @param sender 发送者
     * @param search 搜歌结果
     */
    public static void showSearch(Object sender, SearchPageObj search) {
        int index = search.getIndex();
        SearchMusicObj item;
        String info;
        FMusic.side.sendMessage(sender, "");
        if (search.haveLastPage()) {
            FMusic.side.sendMessage(
                sender,
                FMusic.side.miniMessage(FMusic.getMessage().search.lastPage)
                    .append(FMusic.side.miniMessageRun(FMusic.getMessage().page.last, "/music lastpage")));
        }
        for (int a = 0; a < index; a++) {
            item = search.getRes(a + search.getPage() * 10);
            info = FMusic.getMessage().page.choice;
            info = info.replace(ARG.index, "" + (a + 1))
                .replace(ARG.musicName, item.name)
                .replace(ARG.musicAuthor, item.author)
                .replace(ARG.musicAl, item.al);
            FMusic.side.sendMessage(
                sender,
                FMusic.side.miniMessage(info)
                    .append(
                        FMusic.side.miniMessageRun(FMusic.getMessage().click.clickRun, "/music select " + (a + 1))));
        }
        if (search.haveNextPage()) {
            FMusic.side.sendMessage(
                sender,
                FMusic.side.miniMessage(FMusic.getMessage().search.nextPage)
                    .append(FMusic.side.miniMessageRun(FMusic.getMessage().page.next, "/music nextpage")));
        }
        FMusic.side.sendMessage(sender, "");
    }

    /**
     * 添加搜歌结果
     *
     * @param player 用户名
     * @param page   结果
     */
    public static void addSearch(String player, SearchPageObj page) {
        player = player.toLowerCase();
        searchSave.put(player, page);
    }

    /**
     * 获取搜歌结果
     *
     * @param player 用户名
     * @return 结果
     */
    public static SearchPageObj getSearch(String player) {
        player = player.toLowerCase();
        return searchSave.get(player);
    }

    /**
     * 删除搜歌结果
     *
     * @param player 用户名
     */
    public static void removeSearch(String player) {
        player = player.toLowerCase();
        searchSave.remove(player);
    }
}
