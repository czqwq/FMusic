package com.Lilith.FMusic.server.core.command.sub;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;

import com.Lilith.FMusic.server.core.FMusic;
import com.Lilith.FMusic.server.core.command.ACommand;
import com.Lilith.FMusic.server.core.command.CommandEX;
import com.Lilith.FMusic.server.core.command.CommandRegistry;
import com.Lilith.FMusic.server.core.command.ICommand;

/**
 * /music help [子指令...] —— 多级帮助。
 *
 * <p>
 * 文本全部走 i18n (translate 组件, 由各客户端按自己的语言渲染):
 * 用法 "fmusic.usage.&lt;指令路径&gt;"、说明 "fmusic.desc.&lt;指令路径&gt;",
 * key 由 {@link CommandRegistry} 从路径推导, 各指令无需自己声明。
 *
 * <p>
 * 每行都可点击把指令<b>填入聊天框</b> (suggest_command), 而不是直接执行。
 */
public class CommandHelp extends ACommand {

    private static final NamedTextColor CMD_COLOR = NamedTextColor.AQUA;
    private static final NamedTextColor USAGE_COLOR = NamedTextColor.GRAY;
    private static final NamedTextColor DESC_COLOR = NamedTextColor.WHITE;
    private static final NamedTextColor HEAD_COLOR = NamedTextColor.GOLD;

    @Override
    public void execute(Object sender, String name, String[] args) {
        boolean admin = CommandEX.checkAdmin(sender, name);

        if (args.length <= 1) {
            overview(sender, admin);
            return;
        }

        List<String> path = new ArrayList<>(
            Arrays.asList(args)
                .subList(1, args.length));
        ICommand command = CommandRegistry.find(admin, path);
        if (command == null) {
            FMusic.side.sendMessage(
                sender,
                Component.translatable("fmusic.help.unknown", Component.text(CommandRegistry.fullPath(path))));
            return;
        }
        detail(sender, path, command);
    }

    /** 总览: 普通指令 + (有权限时) 管理指令 */
    private void overview(Object sender, boolean admin) {
        send(
            sender,
            Component.translatable("fmusic.help.head")
                .color(HEAD_COLOR));
        send(
            sender,
            Component.translatable("fmusic.help.section.normal")
                .color(HEAD_COLOR));
        for (Map.Entry<String, ICommand> entry : CommandRegistry.roots(false)
            .entrySet()) {
            send(sender, line(Collections.singletonList(entry.getKey()), entry.getValue()));
        }

        if (admin) {
            send(
                sender,
                Component.translatable("fmusic.help.section.admin")
                    .color(HEAD_COLOR));
            for (Map.Entry<String, ICommand> entry : CommandRegistry.roots(true)
                .entrySet()) {
                if (!CommandRegistry.isAdminRoot(entry.getKey())) {
                    continue;
                }
                send(sender, line(Collections.singletonList(entry.getKey()), entry.getValue()));
            }
        }

        send(
            sender,
            Component.translatable("fmusic.help.footer")
                .color(USAGE_COLOR));
    }

    /** 某条指令的详情: 用法 + 说明 + 子指令 */
    private void detail(Object sender, List<String> path, ICommand command) {
        send(sender, header(path, command));

        send(
            sender,
            Component.translatable("fmusic.help.desc")
                .color(HEAD_COLOR)
                .append(Component.text(" "))
                .append(
                    Component.translatable(CommandRegistry.descKey(path))
                        .color(DESC_COLOR)));

        Map<String, ICommand> children = CommandRegistry.children(command);
        if (!children.isEmpty()) {
            send(
                sender,
                Component.translatable("fmusic.help.sub")
                    .color(HEAD_COLOR));
            for (Map.Entry<String, ICommand> entry : children.entrySet()) {
                List<String> childPath = new ArrayList<>(path);
                childPath.add(entry.getKey());
                send(sender, line(childPath, entry.getValue()));
            }
        }

        if (path.size() > 1) {
            List<String> parentPath = path.subList(0, path.size() - 1);
            send(
                sender,
                Component.translatable(
                    "fmusic.help.parent",
                    Component.text(CommandRegistry.fullPath(parentPath))
                        .color(CMD_COLOR))
                    .color(USAGE_COLOR)
                    .clickEvent(ClickEvent.suggestCommand(CommandRegistry.fullPath(parentPath) + " ")));
        }
    }

    /** "用法: /music xxx <参数>" */
    private Component header(List<String> path, ICommand command) {
        String full = CommandRegistry.fullPath(path);
        return Component.translatable("fmusic.help.usage")
            .color(HEAD_COLOR)
            .append(Component.text(" "))
            .append(
                Component.text(full)
                    .color(CMD_COLOR))
            .append(usage(path, command))
            .clickEvent(ClickEvent.suggestCommand(full + " "));
    }

    /** "/music <路径> <用法> 说明" 一行, 整行可点击填入聊天框 */
    private Component line(List<String> path, ICommand command) {
        String full = CommandRegistry.fullPath(path);
        return Component.text(full)
            .color(CMD_COLOR)
            .append(usage(path, command))
            .append(Component.text(" "))
            .append(
                Component.translatable(CommandRegistry.descKey(path))
                    .color(DESC_COLOR))
            .append(Component.text(" "))
            .append(
                Component.text("[")
                    .append(Component.translatable("fmusic.help.click"))
                    .append(Component.text("]")))
            .clickEvent(ClickEvent.suggestCommand(full + " "));
    }

    /**
     * 参数部分: 有子指令时用 "&lt;a|b|c&gt;" 占位, 叶子用 fmusic.usage.&lt;路径&gt;。
     */
    private Component usage(List<String> path, ICommand command) {
        if (command.subs()
            .isEmpty()) {
            return Component.text(" ")
                .append(
                    Component.translatable(CommandRegistry.usageKey(path))
                        .color(USAGE_COLOR));
        }
        String placeholder = CommandRegistry.childPlaceholder(command);
        if (placeholder.isEmpty()) {
            return Component.empty();
        }
        return Component.text(" ")
            .append(
                Component.text(placeholder)
                    .color(USAGE_COLOR));
    }

    private void send(Object sender, Component component) {
        FMusic.side.sendMessage(sender, component);
    }
}
