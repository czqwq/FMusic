package com.Lilith.FMusic.server.core.objs.message;

public class SearchObj {

    public String noPer;
    public String cantSearch;
    public String res;
    public String emptySearch;
    public String errorNum;
    public String choice;
    public String cantNext;
    public String cantLast;
    public String startSearch;
    public String lastPage;
    public String nextPage;

    public static SearchObj make() {
        SearchObj obj = new SearchObj();
        obj.init();

        return obj;
    }

    public boolean check() {
        if (noPer == null) return true;
        if (cantSearch == null) return true;
        if (res == null) return true;
        if (emptySearch == null) return true;
        if (errorNum == null) return true;
        if (choice == null) return true;
        if (cantNext == null) return true;
        if (cantLast == null) return true;
        if (lastPage == null) return true;
        if (nextPage == null) return true;
        return startSearch == null;
    }

    public void init() {
        if (startSearch == null) startSearch = "<gold>[FMusic]<yellow>正在排队搜歌";
        if (noPer == null) noPer = "<gold>[FMusic]<red>你没有权限搜歌";
        if (cantSearch == null) cantSearch = "<gold>[FMusic]<red>无法搜索歌曲：" + ARG.name;
        if (res == null) res = "<gold>[FMusic]<yellow>搜索结果";
        if (emptySearch == null) emptySearch = "<gold>[FMusic]<red>你没有搜索音乐";
        if (errorNum == null) errorNum = "<gold>[FMusic]<red>请输入正确的序号";
        if (choice == null) choice = "<gold>[FMusic]<yellow>你选择了序号：" + ARG.index;
        if (cantNext == null) cantNext = "<gold>[FMusic]<red>无法下一页";
        if (cantLast == null) cantLast = "<gold>[FMusic]<red>无法上一页";
        if (lastPage == null) lastPage = "<gold>[FMusic]<white>输入/music lastpage上一页";
        if (nextPage == null) nextPage = "<gold>[FMusic]<white>输入/music nextpage下一页";
    }
}
