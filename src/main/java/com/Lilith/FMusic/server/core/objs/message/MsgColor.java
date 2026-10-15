package com.Lilith.FMusic.server.core.objs.message;

import net.kyori.adventure.text.format.NamedTextColor;

/**
 * MC 1.7.10 命名色 (adventure {@link NamedTextColor} 的稳定子集)。
 *
 * <p>
 * 消息样式在服务端组件上表达, 正文由客户端按自己的语言渲染 (translate 组件),
 * 因此语言文件里<b>不允许</b>出现 MiniMessage 标签 (客户端只会原样显示)。
 */
public enum MsgColor {

    BLACK(NamedTextColor.BLACK),
    DARK_BLUE(NamedTextColor.DARK_BLUE),
    DARK_GREEN(NamedTextColor.DARK_GREEN),
    DARK_AQUA(NamedTextColor.DARK_AQUA),
    DARK_RED(NamedTextColor.DARK_RED),
    DARK_PURPLE(NamedTextColor.DARK_PURPLE),
    GOLD(NamedTextColor.GOLD),
    GRAY(NamedTextColor.GRAY),
    DARK_GRAY(NamedTextColor.DARK_GRAY),
    BLUE(NamedTextColor.BLUE),
    GREEN(NamedTextColor.GREEN),
    AQUA(NamedTextColor.AQUA),
    RED(NamedTextColor.RED),
    LIGHT_PURPLE(NamedTextColor.LIGHT_PURPLE),
    YELLOW(NamedTextColor.YELLOW),
    WHITE(NamedTextColor.WHITE);

    private final NamedTextColor color;

    MsgColor(NamedTextColor color) {
        this.color = color;
    }

    public NamedTextColor color() {
        return color;
    }

    /** 按命名色名解析 (大小写不敏感), 未知返回 WHITE。 */
    public static MsgColor of(String name) {
        if (name == null) {
            return WHITE;
        }
        for (MsgColor value : values()) {
            if (value.name()
                .equalsIgnoreCase(name)) {
                return value;
            }
        }
        return WHITE;
    }
}
