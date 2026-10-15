package com.Lilith.FMusic.client.command;

import net.minecraft.util.StatCollector;

import com.Lilith.FMusic.server.core.command.CommandHint;
import com.Lilith.FMusic.server.core.command.CommandRegistry;

/**
 * 客户端"输入即提示": 用客户端语言把 {@link CommandHint} 的纯逻辑渲染成一行文本。
 *
 * <p>
 * 指令树来自 {@link CommandRegistry} (只依赖静态结构), 文本 key 由客户端自己的
 * 语言文件解析, 因此提示跟随玩家语言设置。
 */
public final class ClientCommandHint {

    private ClientCommandHint() {}

    /**
     * @param input 聊天输入框文本 (含前导 '/')
     * @return 提示文本; 不需要提示返回 null
     */
    public static String hintFor(String input) {
        try {
            return CommandHint.hintFor(input, CommandRegistry.roots(true), ClientCommandHint::translate);
        } catch (Throwable t) {
            // 提示只是锦上添花: 任何异常都不能影响聊天界面渲染
            return null;
        }
    }

    private static String translate(String key, Object... args) {
        if (!StatCollector.canTranslate(key)) {
            return null;
        }
        if (args == null || args.length == 0) {
            return StatCollector.translateToLocal(key);
        }
        return StatCollector.translateToLocalFormatted(key, args);
    }
}
