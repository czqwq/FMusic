package com.Lilith.FMusic.server.bili.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import com.Lilith.FMusic.server.bili.BiliMusicBridge;
import com.Lilith.FMusic.server.bili.bilibili.BilibiliLiveClient;
import com.Lilith.FMusic.server.bili.request.SongRequestService;

public final class BiliMusicCommand {

    private static final List<String> SUB_COMMANDS = Arrays.asList("status", "reload", "reconnect", "request", "help");
    private final BiliMusicBridge plugin;

    public BiliMusicCommand(BiliMusicBridge plugin) {
        this.plugin = plugin;
    }

    public void execute(CommandAudience sender, String label, String[] args) {
        if (sender == null) {
            return;
        }
        if (!sender.hasPermission("bilimusic.admin")) {
            sender.sendMessage("&c你没有权限。");
            return;
        }
        String[] values = args == null ? new String[0] : args;
        String sub = values.length == 0 ? "status" : values[0].toLowerCase(Locale.ROOT);
        if ("status".equals(sub)) {
            status(sender);
            return;
        }
        if ("reload".equals(sub)) {
            boolean success = plugin.reloadBridge();
            sender.sendMessage(success ? "&aBiliMusicBridge 已重载。" : "&c重载失败，请查看控制台。");
            return;
        }
        if ("reconnect".equals(sub)) {
            BilibiliLiveClient client = plugin.getLiveClient();
            if (client == null) {
                sender.sendMessage("&c直播客户端尚未初始化。");
            } else {
                client.reconnect();
                sender.sendMessage("&a已请求重新连接 B站直播间。");
            }
            return;
        }
        if ("request".equals(sub)) {
            if (values.length < 2) {
                sender.sendMessage("&e用法：/" + label + " request <歌曲名>");
                return;
            }
            SongRequestService service = plugin.getRequestService();
            boolean submitted = service != null && service.submitManual(sender.name(), join(values, 1));
            sender.sendMessage(submitted ? "&a手动点歌已提交。" : "&c无法提交手动点歌。");
            return;
        }
        help(sender, label);
    }

    public List<String> suggest(CommandAudience sender, String[] args) {
        if (sender == null || !sender.hasPermission("bilimusic.admin")) {
            return Collections.emptyList();
        }
        if (args != null && args.length == 1) {
            String input = args[0].toLowerCase(Locale.ROOT);
            List<String> result = new ArrayList<String>();
            for (String item : SUB_COMMANDS) {
                if (item.startsWith(input)) {
                    result.add(item);
                }
            }
            return result;
        }
        return Collections.emptyList();
    }

    private void status(CommandAudience sender) {
        BilibiliLiveClient client = plugin.getLiveClient();
        SongRequestService service = plugin.getRequestService();
        sender.sendMessage("&d&lBiliMusicBridge 状态");
        sender.sendMessage(
            "&7运行平台：&f" + plugin.getPlatform()
                .platformName());
        sender.sendMessage("&7配置房间：&f" + plugin.getSettings().roomId);
        sender.sendMessage("&7真实房间：&f" + (client == null ? 0 : client.realRoomId()));
        sender.sendMessage(
            "&7连接状态：&f" + (client == null ? "未初始化"
                : client.state()
                    .name()));
        sender.sendMessage(
            "&7弹幕节点：&f" + (client == null || client.connectedHost()
                .isEmpty() ? "-" : client.connectedHost()));
        sender.sendMessage(
            "&7AllMusic：&f" + (plugin.getAllMusicBridge()
                .available() ? "已连接" : "不可用"));
        sender.sendMessage(
            "&7Cookie 项：&f" + (plugin.getCookieStore() == null ? 0
                : plugin.getCookieStore()
                    .snapshot()
                    .size()));
        sender.sendMessage("&7弹幕数量：&f" + (client == null ? 0 : client.danmakuCount()));
        sender.sendMessage(
            "&7点歌统计：&f接收 " + (service == null ? 0 : service.receivedCount())
                + " / 提交 "
                + (service == null ? 0 : service.acceptedCount())
                + " / 成功 "
                + (service == null ? 0 : service.succeededCount())
                + " / 失败 "
                + (service == null ? 0 : service.failedCount())
                + " / 等待 "
                + (service == null ? 0 : service.pending()));
        if (client != null && !client.lastError()
            .isEmpty()) {
            sender.sendMessage("&7最近错误：&c" + client.lastError());
        }
    }

    private static void help(CommandAudience sender, String label) {
        sender.sendMessage("&e/" + label + " status &7- 查看状态");
        sender.sendMessage("&e/" + label + " reconnect &7- 重连直播间");
        sender.sendMessage("&e/" + label + " request <歌曲名> &7- 手动点歌");
        sender.sendMessage("&e/" + label + " reload &7- 重载配置与 Cookie");
    }

    private static String join(String[] values, int start) {
        StringBuilder out = new StringBuilder();
        for (int i = start; i < values.length; i++) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(values[i]);
        }
        return out.toString();
    }
}
