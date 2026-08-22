package com.Lilith.FMusic.server;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * 将 adventure Component 序列化为 Minecraft 1.7.10 IChatComponent 兼容的旧版 JSON 格式。
 *
 * 背景: adventure 4.17+ 的 GsonComponentSerializer 输出新版格式
 * ("click_event"/"command" 字段), 而 MC 1.7.10 只识别旧格式
 * ("clickEvent"/"value" 字段), 导致聊天消息中的可点击按钮(如 [点我查看])失效。
 * 此序列化器输出 1.7.10 原生格式。
 */
public class ChatComponentSerializer {

    public static String serialize(Component component) {
        StringBuilder sb = new StringBuilder();
        writeComponent(sb, component);
        return sb.toString();
    }

    private static void writeComponent(StringBuilder sb, Component component) {
        sb.append('{');
        Style style = component.style();
        boolean first = true;

        // 颜色 (仅命名色; 1.7.10 不支持 hex 颜色)
        TextColor color = style.color();
        if (color != null) {
            String name = null;
            if (color instanceof NamedTextColor) {
                name = ((NamedTextColor) color).toString();
            }
            if (name != null) {
                prop(sb, first, "color", quote(name));
                first = false;
            }
        }

        // 装饰
        if (style.decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE) {
            prop(sb, first, "bold", "true");
            first = false;
        }
        if (style.decoration(TextDecoration.ITALIC) == TextDecoration.State.TRUE) {
            prop(sb, first, "italic", "true");
            first = false;
        }
        if (style.decoration(TextDecoration.UNDERLINED) == TextDecoration.State.TRUE) {
            prop(sb, first, "underlined", "true");
            first = false;
        }
        if (style.decoration(TextDecoration.STRIKETHROUGH) == TextDecoration.State.TRUE) {
            prop(sb, first, "strikethrough", "true");
            first = false;
        }
        if (style.decoration(TextDecoration.OBFUSCATED) == TextDecoration.State.TRUE) {
            prop(sb, first, "obfuscated", "true");
            first = false;
        }

        // 点击事件 (1.7.10 只支持 run_command / open_url / change_page)
        ClickEvent click = style.clickEvent();
        if (click != null) {
            String action = actionName(click.action());
            if (action != null) {
                prop(sb, first, "clickEvent", "{\"action\":\"" + action + "\",\"value\":" + quote(click.value()) + "}");
                first = false;
            }
        }

        // 文本内容
        String text;
        if (component instanceof TextComponent) {
            text = ((TextComponent) component).content();
        } else {
            text = "";
        }
        if (!text.isEmpty()) {
            prop(sb, first, "text", quote(text));
            first = false;
        }

        // 子组件
        if (!component.children()
            .isEmpty()) {
            StringBuilder extra = new StringBuilder("[");
            boolean firstChild = true;
            for (Component child : component.children()) {
                if (!firstChild) {
                    extra.append(',');
                }
                writeComponent(extra, child);
                firstChild = false;
            }
            extra.append(']');
            prop(sb, first, "extra", extra.toString());
            first = false;
        }

        sb.append('}');
    }

    private static String actionName(ClickEvent.Action action) {
        switch (action) {
            case RUN_COMMAND:
                return "run_command";
            case SUGGEST_COMMAND:
                // 1.7.10 无 suggest_command; 转为 run_command 使按钮可用
                return "run_command";
            case OPEN_URL:
                return "open_url";
            case CHANGE_PAGE:
                return "change_page";
            default:
                return null;
        }
    }

    private static void prop(StringBuilder sb, boolean first, String key, String value) {
        if (!first) {
            sb.append(',');
        }
        sb.append(quote(key))
            .append(':')
            .append(value);
    }

    private static String quote(String s) {
        if (s == null) {
            return "\"\"";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (ch < 0x20) {
                        sb.append(String.format("\\u%04x", (int) ch));
                    } else {
                        sb.append(ch);
                    }
            }
        }
        return sb.append('"')
            .toString();
    }
}
