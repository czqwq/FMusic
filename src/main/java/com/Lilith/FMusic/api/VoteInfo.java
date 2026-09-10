package com.Lilith.FMusic.api;

/**
 * 投票信息 (对外只读视图)
 */
public final class VoteInfo {

    public enum Type {
        /** 切歌投票 */
        SWITCH,
        /** 插歌投票 */
        PUSH
    }

    public final Type type;
    public final String api;
    public final String id;
    /** 发起人 */
    public final String sender;
    /** 已同意人数 */
    public final int agreeCount;
    /** 通过所需人数 */
    public final int needCount;
    /** 剩余时间 (秒) */
    public final int remainSeconds;
    /** 排队中的投票数量 */
    public final int queuedCount;

    public VoteInfo(Type type, String api, String id, String sender, int agreeCount, int needCount, int remainSeconds,
        int queuedCount) {
        this.type = type;
        this.api = api;
        this.id = id;
        this.sender = sender;
        this.agreeCount = agreeCount;
        this.needCount = needCount;
        this.remainSeconds = remainSeconds;
        this.queuedCount = queuedCount;
    }

    @Override
    public String toString() {
        return "VoteInfo{" + type
            + " "
            + api
            + ":"
            + id
            + " by "
            + sender
            + ", "
            + agreeCount
            + "/"
            + needCount
            + ", "
            + remainSeconds
            + "s, queued="
            + queuedCount
            + "}";
    }
}
