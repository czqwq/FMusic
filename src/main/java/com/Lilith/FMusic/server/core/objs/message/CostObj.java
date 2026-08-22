package com.Lilith.FMusic.server.core.objs.message;

public class CostObj {

    public String search;
    public String addMusic;
    public String noMoney;
    public String costFail;

    public static CostObj make() {
        CostObj obj = new CostObj();
        obj.init();

        return obj;
    }

    public boolean check() {
        if (search == null) return true;
        if (addMusic == null) return true;
        if (noMoney == null) return true;
        return costFail == null;
    }

    public void init() {
        if (search == null) search = "<gold>[FMusic]<yellow>你搜歌花费了" + ARG.cost;
        if (addMusic == null) addMusic = "<gold>[FMusic]<yellow>你点歌花费了" + ARG.cost;
        if (noMoney == null) noMoney = "<gold>[FMusic]<red>你没有足够的钱";
        if (costFail == null) costFail = "<gold>[FMusic]<red>扣钱过程中错误";
    }
}
