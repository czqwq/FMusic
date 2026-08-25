package com.Lilith.FMusic.server.core.objs.music;

import java.util.List;

import com.Lilith.FMusic.server.core.objs.SearchMusicObj;

public class SearchPageObj {

    private final List<SearchMusicObj> resData;
    private final int maxpage;
    private final String api;
    private int page = 0;

    public SearchPageObj(List<SearchMusicObj> resData, int maxpage, String api) {
        this.resData = resData;
        this.maxpage = maxpage;
        this.api = api;
    }

    public String getSong(int index) {
        return resData.get(index).id;
    }

    public SearchMusicObj getRes(int a) {
        return resData.get(a);
    }

    public boolean nextPage() {
        if (!haveNextPage()) return false;
        page++;
        return true;
    }

    public boolean lastPage() {
        if (!haveLastPage()) return false;
        page--;
        return true;
    }

    public int getIndex() {
        int a = resData.size() - page * 10;
        // 防御: 页码越界 (如某些 API 传了错误 maxpage) 时返回 0, 避免循环负数
        if (a < 0) {
            return 0;
        }
        return Math.min(a, 10);
    }

    public boolean haveNextPage() {
        return page != (maxpage - 1);
    }

    public boolean haveLastPage() {
        return page != 0;
    }

    public int getPage() {
        return page;
    }

    public String getApi() {
        return api;
    }
}
