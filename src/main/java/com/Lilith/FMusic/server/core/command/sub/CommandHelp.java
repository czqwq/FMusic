package com.Lilith.FMusic.server.core.command.sub;

import com.Lilith.FMusic.server.core.FMusic;
import com.Lilith.FMusic.server.core.command.ACommand;

public class CommandHelp extends ACommand {

    @Override
    public void execute(Object sender, String name, String[] args) {
        FMusic.side.sendMessage(sender, FMusic.getMessage().help.normal.head);
        FMusic.side.sendMessage(
            sender,
            FMusic.side.miniMessage(FMusic.getMessage().help.normal.base)
                .append(FMusic.side.miniMessageSuggest(FMusic.getMessage().click.clickCheck, "/music ")));
        FMusic.side.sendMessage(
            sender,
            FMusic.side.miniMessage(FMusic.getMessage().help.normal.stop)
                .append(FMusic.side.miniMessageRun(FMusic.getMessage().click.clickRun, "/music stop")));
        FMusic.side.sendMessage(
            sender,
            FMusic.side.miniMessage(FMusic.getMessage().help.normal.list)
                .append(FMusic.side.miniMessageRun(FMusic.getMessage().click.clickRun, "/music list")));
        FMusic.side.sendMessage(
            sender,
            FMusic.side.miniMessage(FMusic.getMessage().help.normal.cancel)
                .append(FMusic.side.miniMessageRun(FMusic.getMessage().click.clickRun, "/music cancel")));
        FMusic.side.sendMessage(
            sender,
            FMusic.side.miniMessage(FMusic.getMessage().help.normal.vote)
                .append(FMusic.side.miniMessageRun(FMusic.getMessage().click.clickRun, "/music vote")));
        FMusic.side.sendMessage(
            sender,
            FMusic.side.miniMessage(FMusic.getMessage().help.normal.vote1)
                .append(FMusic.side.miniMessageRun(FMusic.getMessage().click.clickRun, "/music vote cancel")));
        FMusic.side.sendMessage(
            sender,
            FMusic.side.miniMessage(FMusic.getMessage().help.normal.agree)
                .append(FMusic.side.miniMessageRun(FMusic.getMessage().click.clickRun, "/music agree")));
        FMusic.side.sendMessage(
            sender,
            FMusic.side.miniMessage(FMusic.getMessage().help.normal.push)
                .append(FMusic.side.miniMessageSuggest(FMusic.getMessage().click.clickRun, "/music push ")));
        FMusic.side.sendMessage(
            sender,
            FMusic.side.miniMessage(FMusic.getMessage().help.normal.push1)
                .append(FMusic.side.miniMessageRun(FMusic.getMessage().click.clickRun, "/music push cancel")));
        FMusic.side.sendMessage(
            sender,
            FMusic.side.miniMessage(FMusic.getMessage().help.normal.mute)
                .append(FMusic.side.miniMessageRun(FMusic.getMessage().click.clickRun, "/music mute")));
        FMusic.side.sendMessage(
            sender,
            FMusic.side.miniMessage(FMusic.getMessage().help.normal.mutelist)
                .append(FMusic.side.miniMessageRun(FMusic.getMessage().click.clickRun, "/music mute list")));
        FMusic.side.sendMessage(
            sender,
            FMusic.side.miniMessage(FMusic.getMessage().help.normal.search)
                .append(FMusic.side.miniMessageSuggest(FMusic.getMessage().click.clickCheck, "/music search ")));
        FMusic.side.sendMessage(
            sender,
            FMusic.side.miniMessage(FMusic.getMessage().help.normal.select)
                .append(FMusic.side.miniMessageSuggest(FMusic.getMessage().click.clickCheck, "/music select ")));
        FMusic.side.sendMessage(
            sender,
            FMusic.side.miniMessage(FMusic.getMessage().help.normal.nextpage)
                .append(FMusic.side.miniMessageRun(FMusic.getMessage().click.clickRun, "/music nextpage")));
        FMusic.side.sendMessage(
            sender,
            FMusic.side.miniMessage(FMusic.getMessage().help.normal.lastpage)
                .append(FMusic.side.miniMessageRun(FMusic.getMessage().click.clickRun, "/music lastpage")));
        FMusic.side.sendMessage(
            sender,
            FMusic.side.miniMessage(FMusic.getMessage().help.normal.hud9)
                .append(FMusic.side.miniMessageRun(FMusic.getMessage().click.clickRun, "/music hud enable")));
        FMusic.side.sendMessage(
            sender,
            FMusic.side.miniMessage(FMusic.getMessage().help.normal.hud10)
                .append(FMusic.side.miniMessageRun(FMusic.getMessage().click.clickRun, "/music hud reset")));
        FMusic.side.sendMessage(
            sender,
            FMusic.side.miniMessage(FMusic.getMessage().help.normal.hud1)
                .append(FMusic.side.miniMessageSuggest(FMusic.getMessage().click.clickCheck, "/music hud ")));
        FMusic.side.sendMessage(
            sender,
            FMusic.side.miniMessage(FMusic.getMessage().help.normal.hud2)
                .append(FMusic.side.miniMessageSuggest(FMusic.getMessage().click.clickCheck, "/music hud ")));
        FMusic.side.sendMessage(
            sender,
            FMusic.side.miniMessage(FMusic.getMessage().help.normal.hud6)
                .append(FMusic.side.miniMessageSuggest(FMusic.getMessage().click.clickCheck, "/music hud ")));
        FMusic.side.sendMessage(
            sender,
            FMusic.side.miniMessage(FMusic.getMessage().help.normal.hud7)
                .append(FMusic.side.miniMessageSuggest(FMusic.getMessage().click.clickCheck, "/music hud ")));
        FMusic.side.sendMessage(
            sender,
            FMusic.side.miniMessage(FMusic.getMessage().help.normal.hud8)
                .append(FMusic.side.miniMessageSuggest(FMusic.getMessage().click.clickCheck, "/music hud ")));
        FMusic.side.sendMessage(
            sender,
            FMusic.side.miniMessage(FMusic.getMessage().help.normal.hud11)
                .append(FMusic.side.miniMessageSuggest(FMusic.getMessage().click.clickCheck, "/music hud ")));
        FMusic.side.sendMessage(
            sender,
            FMusic.side.miniMessage(FMusic.getMessage().help.normal.hud12)
                .append(FMusic.side.miniMessageSuggest(FMusic.getMessage().click.clickCheck, "/music hud ")));
        FMusic.side.sendMessage(
            sender,
            FMusic.side.miniMessage(FMusic.getMessage().help.normal.hud13)
                .append(FMusic.side.miniMessageSuggest(FMusic.getMessage().click.clickCheck, "/music hud ")));
        FMusic.side.sendMessage(
            sender,
            FMusic.side.miniMessage(FMusic.getMessage().help.normal.hud14)
                .append(FMusic.side.miniMessageSuggest(FMusic.getMessage().click.clickCheck, "/music hud ")));
        FMusic.side.sendMessage(
            sender,
            FMusic.side.miniMessage(FMusic.getMessage().help.normal.hud3)
                .append(FMusic.side.miniMessageSuggest(FMusic.getMessage().click.clickCheck, "/music hud pic size ")));
        FMusic.side.sendMessage(
            sender,
            FMusic.side.miniMessage(FMusic.getMessage().help.normal.hud4)
                .append(
                    FMusic.side.miniMessageSuggest(FMusic.getMessage().click.clickCheck, "/music hud pic rotate ")));
        FMusic.side.sendMessage(
            sender,
            FMusic.side.miniMessage(FMusic.getMessage().help.normal.hud5)
                .append(FMusic.side.miniMessageSuggest(FMusic.getMessage().click.clickCheck, "/music hud pic speed ")));
        FMusic.side.sendMessage(
            sender,
            FMusic.side.miniMessage(FMusic.getMessage().help.normal.join)
                .append(FMusic.side.miniMessageRun(FMusic.getMessage().click.clickRun, "/music join")));
        if (FMusic.side.checkPermission(sender)) {
            FMusic.side.sendMessage(
                sender,
                FMusic.side.miniMessage(FMusic.getMessage().help.admin.reload)
                    .append(FMusic.side.miniMessageRun(FMusic.getMessage().click.clickRun, "/music reload")));
            FMusic.side.sendMessage(
                sender,
                FMusic.side.miniMessage(FMusic.getMessage().help.admin.next)
                    .append(FMusic.side.miniMessageRun(FMusic.getMessage().click.clickRun, "/music next")));
            FMusic.side.sendMessage(
                sender,
                FMusic.side.miniMessage(FMusic.getMessage().help.admin.ban)
                    .append(FMusic.side.miniMessageSuggest(FMusic.getMessage().click.clickCheck, "/music ban ")));
            FMusic.side.sendMessage(
                sender,
                FMusic.side.miniMessage(FMusic.getMessage().help.admin.ban1)
                    .append(FMusic.side.miniMessageSuggest(FMusic.getMessage().click.clickCheck, "/music ban ")));
            FMusic.side.sendMessage(
                sender,
                FMusic.side.miniMessage(FMusic.getMessage().help.admin.banPlayer)
                    .append(FMusic.side.miniMessageSuggest(FMusic.getMessage().click.clickCheck, "/music banplayer ")));
            FMusic.side.sendMessage(
                sender,
                FMusic.side.miniMessage(FMusic.getMessage().help.admin.unban)
                    .append(FMusic.side.miniMessageSuggest(FMusic.getMessage().click.clickCheck, "/music unban ")));
            FMusic.side.sendMessage(
                sender,
                FMusic.side.miniMessage(FMusic.getMessage().help.admin.unbanPlayer)
                    .append(
                        FMusic.side.miniMessageSuggest(FMusic.getMessage().click.clickCheck, "/music unbanplayer ")));
            FMusic.side.sendMessage(
                sender,
                FMusic.side.miniMessage(FMusic.getMessage().help.admin.delete)
                    .append(FMusic.side.miniMessageSuggest(FMusic.getMessage().click.clickCheck, "/music delete ")));
            FMusic.side.sendMessage(
                sender,
                FMusic.side.miniMessage(FMusic.getMessage().help.admin.addList)
                    .append(FMusic.side.miniMessageSuggest(FMusic.getMessage().click.clickCheck, "/music addlist ")));
            FMusic.side.sendMessage(
                sender,
                FMusic.side.miniMessage(FMusic.getMessage().help.admin.addList1)
                    .append(FMusic.side.miniMessageSuggest(FMusic.getMessage().click.clickCheck, "/music addlist ")));
            FMusic.side.sendMessage(
                sender,
                FMusic.side.miniMessage(FMusic.getMessage().help.admin.clearList)
                    .append(FMusic.side.miniMessageRun(FMusic.getMessage().click.clickRun, "/music clearlist")));
            FMusic.side.sendMessage(
                sender,
                FMusic.side.miniMessage(FMusic.getMessage().help.admin.clearBanList)
                    .append(FMusic.side.miniMessageRun(FMusic.getMessage().click.clickRun, "/music clearban")));
            FMusic.side.sendMessage(
                sender,
                FMusic.side.miniMessage(FMusic.getMessage().help.admin.clearBanPlayerList)
                    .append(FMusic.side.miniMessageRun(FMusic.getMessage().click.clickRun, "/music clearbanplayer")));
            FMusic.side.sendMessage(
                sender,
                FMusic.side.miniMessage(FMusic.getMessage().help.admin.test)
                    .append(FMusic.side.miniMessageSuggest(FMusic.getMessage().click.clickCheck, "/music test ")));
            FMusic.side.sendMessage(
                sender,
                FMusic.side.miniMessage(FMusic.getMessage().help.admin.test1)
                    .append(FMusic.side.miniMessageSuggest(FMusic.getMessage().click.clickCheck, "/music test ")));
        }
    }
}
