package com.Lilith.FMusic.server.core.command;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public interface ICommand {

    void execute(Object sender, String name, String[] args);

    List<String> tab(Object sender, String name, String[] args, int index);

    /**
     * 子指令表 (多级帮助与"输入即提示"遍历用), 无子指令返回空表。
     *
     * <p>
     * 帮助/提示在客户端也要能遍历, 因此这里只能暴露静态结构, 不允许访问配置或玩家状态。
     * 用法/说明文案不在这里声明: {@link CommandRegistry#usageKey(List)} /
     * {@link CommandRegistry#descKey(List)} 按指令路径 (fmusic.usage.&lt;path&gt;) 推导。
     */
    default Map<String, ICommand> subs() {
        return Collections.emptyMap();
    }
}
