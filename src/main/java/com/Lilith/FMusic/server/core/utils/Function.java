package com.Lilith.FMusic.server.core.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class Function {

    public static boolean isInteger(String str) {
        Pattern pattern = Pattern.compile("^[-+]?\\d*\\.?\\d+$");
        return pattern.matcher(str)
            .matches();
    }

    public static String getString(String a, String b, String c) {
        int x = a.indexOf(b) + b.length();
        int y;
        if (c != null) y = a.indexOf(c);
        else y = a.length();
        if (x < 0 || y < 0) return a;
        return a.substring(x, y);
    }

    public static int countChar(String a, char b) {
        int count = 0;
        for (char t : a.toCharArray()) {
            if (t == b) count++;
        }
        return count;
    }

    /**
     * 保留以 prefix 开头的候选 (忽略大小写), 语义等价于 vanilla
     * {@code CommandBase#doesStringStartWith} / {@code getListOfStringsMatchingLastWord}。
     *
     * <p>
     * 用于 Tab 补全: 客户端 GuiChat 会拿服务端返回的候选列表计算公共前缀来自动补全,
     * 因此必须只返回与当前输入词匹配的候选; 返回未过滤的整表会导致公共前缀为空,
     * 按 Tab 时退化成 "从列表第一项开始逐个轮换"。
     *
     * @param list   候选列表
     * @param prefix 当前已输入的内容
     * @return 过滤后的候选列表 (新列表, 不修改入参)
     */
    public static List<String> filterStartsWith(List<String> list, String prefix) {
        List<String> result = new ArrayList<>();
        if (list == null) {
            return result;
        }
        if (prefix == null || prefix.isEmpty()) {
            result.addAll(list);
            return result;
        }
        for (String item : list) {
            if (item != null && item.regionMatches(true, 0, prefix, 0, prefix.length())) {
                result.add(item);
            }
        }
        return result;
    }
}
