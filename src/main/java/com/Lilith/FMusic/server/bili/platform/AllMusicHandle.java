package com.Lilith.FMusic.server.bili.platform;

/**
 * FMusic 模组自身作为 AllMusic 兼容层 (原 BiliMusicBridge 反射查找外部插件,
 * 移植后直接以内置方式集成, 无需类加载器/插件实例)。
 */
public final class AllMusicHandle {

    private final boolean active;

    public AllMusicHandle(boolean active) {
        this.active = active;
    }

    public String displayName() {
        return "FMusic";
    }

    public boolean active() {
        return active;
    }
}
