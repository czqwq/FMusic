package com.Lilith.FMusic.server.core.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * "输入即提示"的纯逻辑部分: 依据聊天栏里已输入的文本, 生成一行用法提示。
 *
 * <p>
 * 不依赖任何 Minecraft 类, 由客户端侧的混入 (MixinGuiChat) 把
 * {@link Translator} 接到 {@code StatCollector} 上, 这样提示跟随客户端语言。
 */
public final class CommandHint {

    /** 文本翻译器 (客户端接 StatCollector; 返回 null 表示缺少该 key) */
    public interface Translator {

        String translate(String key, Object... args);
    }

    /** 提示行最多列出的候选数 */
    private static final int MAX_CANDIDATES = 12;

    private CommandHint() {}

    /**
     * @param input 聊天输入框当前文本 (含前导 '/')
     * @param roots 根指令表 (名字 -> 节点)
     * @param tr    翻译器
     * @return 一行提示文本; 无提示返回 null
     */
    public static String hintFor(String input, Map<String, ICommand> roots, Translator tr) {
        if (input == null || roots == null || tr == null) {
            return null;
        }
        // 保留行尾空格: "/music " 表示玩家正要输入子指令
        String text = input;
        while (text.startsWith(" ")) {
            text = text.substring(1);
        }
        if (!text.startsWith("/")) {
            return null;
        }
        String[] tokens = text.substring(1)
            .split(" ", -1);
        if (tokens.length == 0 || !CommandRegistry.ROOT.equalsIgnoreCase(tokens[0])) {
            return null;
        }

        ICommand node = null;
        List<String> path = new ArrayList<>();

        for (int i = 1; i < tokens.length; i++) {
            String token = tokens[i];
            boolean last = i == tokens.length - 1;
            Map<String, ICommand> pool = node == null ? roots : node.subs();

            if (token.isEmpty()) {
                // 光标停在空格后: 提示当前节点可选的下一步
                return describe(node, path, pool, null, tr);
            }

            String match = find(pool, token);
            if (match == null) {
                if (!last) {
                    // 中间出现不认识的词: 不提示 (可能是 /music <歌名> 的点歌写法)
                    return null;
                }
                if (node != null && node.subs()
                    .isEmpty()) {
                    // 已走到叶子指令: 当前词是它的参数值 (歌曲ID/玩家名/颜色...), 提示用法
                    return usage(node, path, tr);
                }
                List<String> candidates = candidates(pool, token);
                if (candidates.isEmpty()) {
                    // 根层级的未知词 = 默认点歌写法
                    if (node == null) {
                        return tr.translate("fmusic.hint.default");
                    }
                    return tr.translate("fmusic.hint.none", token);
                }
                return tr
                    .translate("fmusic.hint.candidates", CommandRegistry.ROOT_PREFIX + String.join("|", candidates));
            }

            node = pool.get(match);
            path.add(match);
            if (last) {
                if (node.subs()
                    .isEmpty()) {
                    return usage(node, path, tr);
                }
                return describe(node, path, node.subs(), null, tr);
            }
        }

        // 只有 "/music" (还没输入空格): 给根提示
        return tr.translate("fmusic.hint.root");
    }

    /** 当前节点的下一步提示: 叶子显示用法, 有子指令则列候选 */
    private static String describe(ICommand node, List<String> path, Map<String, ICommand> pool, String typed,
        Translator tr) {
        if (node == null) {
            if (pool.isEmpty()) {
                return null;
            }
            List<String> names = new ArrayList<>(pool.keySet());
            names.sort(String::compareTo);
            return tr.translate("fmusic.hint.root.candidates", join(names));
        }
        if (node.subs()
            .isEmpty()) {
            return usage(node, path, tr);
        }
        List<String> names = new ArrayList<>(
            node.subs()
                .keySet());
        names.sort(String::compareTo);
        return tr.translate("fmusic.hint.candidates", CommandRegistry.fullPath(path) + " " + join(names));
    }

    /** "用法: /music hud info color <颜色HEX> - 设置歌曲信息文字颜色" */
    private static String usage(ICommand node, List<String> path, Translator tr) {
        String full = CommandRegistry.fullPath(path);
        String usage = tr.translate(CommandRegistry.usageKey(path));
        String line = usage == null || usage.isEmpty() ? full : tr.translate("fmusic.hint.usage", full + " " + usage);

        String desc = tr.translate(CommandRegistry.descKey(path));
        if (desc != null && !desc.isEmpty()) {
            line = line + " - " + desc;
        }
        return line;
    }

    /** 精确匹配优先, 其次是唯一的前缀匹配 (大小写不敏感) */
    private static String find(Map<String, ICommand> pool, String token) {
        for (String name : pool.keySet()) {
            if (name.equalsIgnoreCase(token)) {
                return name;
            }
        }
        String found = null;
        for (String name : pool.keySet()) {
            if (startsWithIgnoreCase(name, token)) {
                if (found != null) {
                    return null; // 多个前缀匹配: 交给候选提示
                }
                found = name;
            }
        }
        return found;
    }

    private static List<String> candidates(Map<String, ICommand> pool, String prefix) {
        List<String> result = new ArrayList<>();
        for (String name : pool.keySet()) {
            if (startsWithIgnoreCase(name, prefix)) {
                result.add(name);
            }
        }
        result.sort(String::compareTo);
        return result;
    }

    private static boolean startsWithIgnoreCase(String value, String prefix) {
        return value.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private static String join(List<String> names) {
        return String.join("|", trim(names));
    }

    private static List<String> trim(List<String> names) {
        if (names.size() <= MAX_CANDIDATES) {
            return names;
        }
        return new ArrayList<>(names.subList(0, MAX_CANDIDATES));
    }
}
