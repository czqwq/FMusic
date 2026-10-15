package com.Lilith.FMusic.server.core.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 指令树只读视图: 供多级帮助与"输入即提示"遍历。
 *
 * <p>
 * 只依赖 {@link CommandEX} 的静态注册表与各 {@link ICommand#subs()} 的静态结构,
 * 不访问配置/玩家状态, 因此在客户端侧也能安全使用 (提示需要本地遍历)。
 */
public final class CommandRegistry {

    /** 根指令名 (不含斜杠) */
    public static final String ROOT = "music";
    /** 根指令前缀 */
    public static final String ROOT_PREFIX = "/" + ROOT + " ";

    private CommandRegistry() {}

    /** 根指令子指令表 (按名字排序, 便于稳定输出) */
    public static Map<String, ICommand> roots(boolean includeAdmin) {
        Map<String, ICommand> map = new TreeMap<>();
        map.putAll(CommandEX.commandList);
        if (includeAdmin) {
            map.putAll(CommandEX.commandAdminList);
        }
        return map;
    }

    /** 按名字取根指令 */
    public static ICommand root(String name, boolean includeAdmin) {
        if (name == null) {
            return null;
        }
        ICommand command = CommandEX.commandList.get(name);
        if (command == null && includeAdmin) {
            command = CommandEX.commandAdminList.get(name);
        }
        return command;
    }

    /** 该名字是否是管理员指令 */
    public static boolean isAdminRoot(String name) {
        return name != null && CommandEX.commandAdminList.containsKey(name);
    }

    /**
     * 按路径解析指令节点 (路径为子指令名序列, 不含根指令名)。
     *
     * @param path 子指令名序列, 可为空
     * @return 节点; 路径无效返回 null
     */
    public static ICommand find(boolean includeAdmin, List<String> path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        ICommand node = root(path.get(0), includeAdmin);
        for (int i = 1; node != null && i < path.size(); i++) {
            node = node.subs()
                .get(path.get(i));
        }
        return node;
    }

    /** 子指令名列表 (排序) */
    public static List<String> childNames(ICommand command) {
        if (command == null || command.subs()
            .isEmpty()) {
            return Collections.emptyList();
        }
        List<String> names = new ArrayList<>(
            command.subs()
                .keySet());
        Collections.sort(names);
        return names;
    }

    /** 子指令表 (排序) */
    public static Map<String, ICommand> children(ICommand command) {
        Map<String, ICommand> map = new LinkedHashMap<>();
        for (String name : childNames(command)) {
            map.put(
                name,
                command.subs()
                    .get(name));
        }
        return map;
    }

    /**
     * 生成 "<a|b|c>" 形式的参数占位; 无子指令返回空串。
     */
    public static String childPlaceholder(ICommand command) {
        List<String> names = childNames(command);
        if (names.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("<");
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                sb.append('|');
            }
            sb.append(names.get(i));
        }
        return sb.append('>')
            .toString();
    }

    /** 完整指令路径 (含 /music 前缀), 如 ["hud","info","color"] -> "/music hud info color" */
    public static String fullPath(List<String> path) {
        StringBuilder sb = new StringBuilder("/").append(ROOT);
        if (path != null && !path.isEmpty()) {
            sb.append(' ');
            for (int i = 0; i < path.size(); i++) {
                if (i > 0) {
                    sb.append(' ');
                }
                sb.append(path.get(i));
            }
        }
        return sb.toString();
    }

    /**
     * 参数用法的 i18n key (由指令路径推导, 无需各指令自己声明):
     * ["hud","info","color"] -> "fmusic.usage.hud.info.color"。
     */
    public static String usageKey(List<String> path) {
        return "fmusic.usage." + String.join(".", path);
    }

    /**
     * 一句话说明的 i18n key: ["hud","info","color"] -> "fmusic.desc.hud.info.color"。
     */
    public static String descKey(List<String> path) {
        return "fmusic.desc." + String.join(".", path);
    }
}
