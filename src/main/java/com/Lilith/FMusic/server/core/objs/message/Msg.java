package com.Lilith.FMusic.server.core.objs.message;

import java.util.ArrayList;
import java.util.IllegalFormatException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.util.StatCollector;

/**
 * 一条可发送消息 = i18n key + 样式 + 有序参数槽。
 *
 * <p>
 * 文本<b>不再</b>由服务端拼好 (原先走 message.json): 服务端只发出
 * {@code {"translate":"<key>","with":[args]}} 组件, 由各客户端按自己的语言渲染,
 * 因此多语言玩家能各自看到母语文本。服务端侧自身渲染 (控制台日志等) 固定英文。
 *
 * <p>
 * 参数以 {@code %s} 顺序对应语言文件里的占位符; 旧的调用点写法
 * {@code FMusic.getMessage().cost.addMusic.replace(ARG.cost, String.valueOf(cost))}
 * 保持不变 —— {@link #replace(String, Object)} 返回绑定了该槽位的新实例 (不可变, 线程安全)。
 */
public final class Msg {

    /** [FMusic] 前缀颜色 (与原 MiniMessage 配色一致) */
    public static final MsgColor PREFIX_COLOR = MsgColor.GOLD;
    public static final String PREFIX = "[FMusic]";

    private final String key;
    private final boolean prefix;
    private final MsgColor color;
    private final Map<String, Slot> slots;

    private Msg(String key, boolean prefix, MsgColor color, Map<String, Slot> slots) {
        this.key = key;
        this.prefix = prefix;
        this.color = color;
        this.slots = slots;
    }

    /** 带 [FMusic] 前缀的消息 (绝大多数玩家可见消息) */
    public static Msg prefixed(String key, MsgColor color) {
        return new Msg(key, true, color, new LinkedHashMap<>());
    }

    /** 不带前缀的消息 (帮助正文/HUD 名等) */
    public static Msg plain(String key, MsgColor color) {
        return new Msg(key, false, color, new LinkedHashMap<>());
    }

    /**
     * 声明一个参数槽 (顺序即语言文件 %s 顺序)。
     *
     * @param name  ARG 常量 (如 ARG.hud)
     * @param color 该参数的颜色; null 表示跟随正文颜色
     */
    public Msg arg(String name, MsgColor color) {
        Map<String, Slot> copy = new LinkedHashMap<>(slots);
        copy.put(name, new Slot(color, null));
        return new Msg(key, prefix, this.color, copy);
    }

    /** 声明一个跟随正文颜色的参数槽 */
    public Msg arg(String name) {
        return arg(name, null);
    }

    /**
     * 绑定参数值 (与旧的 {@code String#replace(ARG.x, value)} 调用点同名同形)。
     *
     * @param name  ARG 常量
     * @param value String / Number / Component / Msg
     * @return 绑定后的新实例, 原实例不变
     */
    public Msg replace(String name, Object value) {
        Map<String, Slot> copy = new LinkedHashMap<>(slots);
        Slot slot = copy.get(name);
        if (slot == null) {
            // 语言文件里没有该占位符时忽略绑定, 避免打乱 %s 顺序
            return this;
        }
        copy.put(name, new Slot(slot.color, value));
        return new Msg(key, prefix, this.color, copy);
    }

    /** i18n key */
    public String key() {
        return key;
    }

    /** 已声明的参数槽数量 (= 语言文件里的 %s 个数) */
    public int argCount() {
        return slots.size();
    }

    /** 参数槽是否都已绑定 (迁移自检用) */
    public boolean isComplete() {
        for (Slot slot : slots.values()) {
            if (slot.value == null) {
                return false;
            }
        }
        return true;
    }

    /** 转成 adventure 组件 (translate 组件, 客户端侧翻译)。 */
    public Component component() {
        List<Component> args = new ArrayList<>(slots.size());
        for (Slot slot : slots.values()) {
            args.add(slot.asComponent(color));
        }
        Component body = Component.translatable(key, args)
            .color(color.color());
        if (!prefix) {
            return body;
        }
        return Component.text(PREFIX)
            .color(PREFIX_COLOR.color())
            .append(body);
    }

    /**
     * 服务端解析成纯文本 (服务端语言, 专用服务器即英文)。
     *
     * <p>
     * 只用于无法走客户端翻译的场景: HUD 数据组装 (纯字符串下发)、日志、作为普通名称使用。
     * 玩家可见的聊天消息一律走 {@link #component()}。
     */
    public String resolve() {
        Object[] args = new Object[slots.size()];
        int i = 0;
        for (Slot slot : slots.values()) {
            args[i++] = slot.asPlain();
        }
        String format = StatCollector.translateToLocal(key);
        if (args.length == 0) {
            return format;
        }
        try {
            return String.format(format, args);
        } catch (IllegalFormatException e) {
            return format;
        }
    }

    @Override
    public String toString() {
        return "Msg(" + key + ", args=" + slots.size() + ")";
    }

    /** 单个参数槽: 声明颜色 + 绑定的值 */
    private static final class Slot {

        private final MsgColor color;
        private final Object value;

        private Slot(MsgColor color, Object value) {
            this.color = color;
            this.value = value;
        }

        private Component asComponent(MsgColor bodyColor) {
            if (value == null) {
                return Component.empty();
            }
            if (value instanceof Msg) {
                return ((Msg) value).component();
            }
            if (value instanceof Component) {
                return (Component) value;
            }
            String text = String.valueOf(value);
            if (text.isEmpty()) {
                // 空参数不能带颜色: 否则序列化成 {"color":"..."}, 1.7.10 解析不了
                return Component.empty();
            }
            NamedTextColor argColor = (color == null ? bodyColor : color).color();
            return Component.text(text)
                .color(argColor);
        }

        /** 服务端纯文本渲染用的参数值 */
        private Object asPlain() {
            if (value instanceof Msg) {
                return ((Msg) value).resolve();
            }
            if (value instanceof Component) {
                return "";
            }
            return value == null ? "" : String.valueOf(value);
        }
    }
}
