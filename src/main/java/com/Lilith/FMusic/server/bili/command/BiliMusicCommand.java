package com.Lilith.FMusic.server.bili.command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import net.minecraft.util.StatCollector;

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
            sender.sendMessage(StatCollector.translateToLocal("bili.cmd.noperm"));
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
            sender.sendMessage(
                success ? StatCollector.translateToLocal("bili.cmd.reloaded")
                    : StatCollector.translateToLocal("bili.cmd.reload_fail"));
            return;
        }
        if ("reconnect".equals(sub)) {
            BilibiliLiveClient client = plugin.getLiveClient();
            if (client == null) {
                sender.sendMessage(StatCollector.translateToLocal("bili.cmd.client_null"));
            } else {
                client.reconnect();
                sender.sendMessage(StatCollector.translateToLocal("bili.cmd.reconnected"));
            }
            return;
        }
        if ("request".equals(sub)) {
            if (values.length < 2) {
                sender.sendMessage(StatCollector.translateToLocalFormatted("bili.cmd.request_usage", label));
                return;
            }
            SongRequestService service = plugin.getRequestService();
            boolean submitted = service != null && service.submitManual(sender.name(), join(values, 1));
            sender.sendMessage(
                submitted ? StatCollector.translateToLocal("bili.cmd.request_ok")
                    : StatCollector.translateToLocal("bili.cmd.request_fail"));
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
        sender.sendMessage(StatCollector.translateToLocal("bili.cmd.status_title"));
        sender.sendMessage(
            StatCollector.translateToLocalFormatted(
                "bili.cmd.status_platform",
                plugin.getPlatform()
                    .platformName()));
        sender
            .sendMessage(StatCollector.translateToLocalFormatted("bili.cmd.status_room", plugin.getSettings().roomId));
        sender.sendMessage(
            StatCollector
                .translateToLocalFormatted("bili.cmd.status_realroom", client == null ? 0 : client.realRoomId()));
        sender.sendMessage(
            StatCollector.translateToLocalFormatted(
                "bili.cmd.status_conn",
                client == null ? StatCollector.translateToLocal("bili.cmd.not_init")
                    : client.state()
                        .name()));
        sender.sendMessage(
            StatCollector.translateToLocalFormatted(
                "bili.cmd.status_host",
                client == null || client.connectedHost()
                    .isEmpty() ? "-" : client.connectedHost()));
        sender.sendMessage(
            StatCollector.translateToLocalFormatted(
                "bili.cmd.status_allmusic",
                plugin.getAllMusicBridge()
                    .available() ? StatCollector.translateToLocal("bili.cmd.available")
                        : StatCollector.translateToLocal("bili.cmd.unavailable")));
        sender.sendMessage(
            StatCollector.translateToLocalFormatted(
                "bili.cmd.status_cookie",
                plugin.getCookieStore() == null ? 0
                    : plugin.getCookieStore()
                        .snapshot()
                        .size()));
        sender.sendMessage(
            StatCollector
                .translateToLocalFormatted("bili.cmd.status_danmaku", client == null ? 0 : client.danmakuCount()));
        sender.sendMessage(
            StatCollector.translateToLocalFormatted(
                "bili.cmd.status_stats",
                service == null ? 0 : service.receivedCount(),
                service == null ? 0 : service.acceptedCount(),
                service == null ? 0 : service.succeededCount(),
                service == null ? 0 : service.failedCount(),
                service == null ? 0 : service.pending()));
        if (client != null && !client.lastError()
            .isEmpty()) {
            sender.sendMessage(StatCollector.translateToLocalFormatted("bili.cmd.status_error", client.lastError()));
        }
    }

    private static void help(CommandAudience sender, String label) {
        sender.sendMessage(StatCollector.translateToLocalFormatted("bili.cmd.help_status", label));
        sender.sendMessage(StatCollector.translateToLocalFormatted("bili.cmd.help_reconnect", label));
        sender.sendMessage(StatCollector.translateToLocalFormatted("bili.cmd.help_request", label));
        sender.sendMessage(StatCollector.translateToLocalFormatted("bili.cmd.help_reload", label));
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
