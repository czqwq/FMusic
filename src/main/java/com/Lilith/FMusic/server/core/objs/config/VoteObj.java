package com.Lilith.FMusic.server.core.objs.config;

/**
 * 投票配置 (移植自 AllMusic 4.2.0 投票序列)
 */
public class VoteObj {

    /**
     * 最小通过投票数
     */
    public int minVote;
    /**
     * 投票时间
     */
    public int voteTime;
    /**
     * 投票队列最大大小
     */
    public int voteListSize;

    public static VoteObj make() {
        VoteObj obj = new VoteObj();
        obj.minVote = 3;
        obj.voteTime = 30;
        obj.voteListSize = 10;
        return obj;
    }
}
