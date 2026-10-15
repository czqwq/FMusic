package com.Lilith.FMusic.server.core.objs.message;

import com.Lilith.FMusic.codec.HudPosType;
import com.Lilith.FMusic.codec.HudType;

public class HudNameObj {

    public Msg all;
    public Msg disable;
    public Msg enable;
    public Msg info;
    public Msg list;
    public Msg lyric;
    public Msg pic;
    public Msg picRoute;
    public Msg picSpeed;
    public Msg pos1;
    public Msg pos2;
    public Msg pos3;
    public Msg pos4;
    public Msg pos5;
    public Msg pos6;
    public Msg pos7;
    public Msg pos8;
    public Msg pos9;
    public Msg state;

    public static HudNameObj make() {
        HudNameObj obj = new HudNameObj();
        obj.init();

        return obj;
    }

    public void init() {
        list = Msg.plain("fmusic.message.hudName.list", MsgColor.WHITE);
        state = Msg.plain("fmusic.message.hudName.state", MsgColor.WHITE);
        lyric = Msg.plain("fmusic.message.hudName.lyric", MsgColor.WHITE);
        info = Msg.plain("fmusic.message.hudName.info", MsgColor.WHITE);
        pic = Msg.plain("fmusic.message.hudName.pic", MsgColor.WHITE);
        all = Msg.plain("fmusic.message.hudName.all", MsgColor.WHITE);
        picRoute = Msg.plain("fmusic.message.hudName.picRoute", MsgColor.WHITE);
        picSpeed = Msg.plain("fmusic.message.hudName.picSpeed", MsgColor.WHITE);
        enable = Msg.plain("fmusic.message.hudName.enable", MsgColor.WHITE);
        disable = Msg.plain("fmusic.message.hudName.disable", MsgColor.WHITE);
        pos1 = Msg.plain("fmusic.message.hudName.pos1", MsgColor.WHITE);
        pos2 = Msg.plain("fmusic.message.hudName.pos2", MsgColor.WHITE);
        pos3 = Msg.plain("fmusic.message.hudName.pos3", MsgColor.WHITE);
        pos4 = Msg.plain("fmusic.message.hudName.pos4", MsgColor.WHITE);
        pos5 = Msg.plain("fmusic.message.hudName.pos5", MsgColor.WHITE);
        pos6 = Msg.plain("fmusic.message.hudName.pos6", MsgColor.WHITE);
        pos7 = Msg.plain("fmusic.message.hudName.pos7", MsgColor.WHITE);
        pos8 = Msg.plain("fmusic.message.hudName.pos8", MsgColor.WHITE);
        pos9 = Msg.plain("fmusic.message.hudName.pos9", MsgColor.WHITE);
    }

    /** HUD 模块显示名 (作为消息参数时保持可翻译) */
    public Msg getHud(HudType data) {
        if (data == null) {
            return all;
        }
        switch (data) {
            case PIC:
                return pic;
            case INFO:
                return info;
            case LYRIC:
                return lyric;
            case STATE:
                return state;
            default:
                return all;
        }
    }

    /** 对齐方式显示名 (作为消息参数时保持可翻译) */
    public Msg getDir(HudPosType data) {
        if (data == null) {
            return all;
        }
        switch (data) {
            case TOP_LEFT:
                return pos1;
            case TOP_CENTER:
                return pos2;
            case TOP_RIGHT:
                return pos3;
            case LEFT:
                return pos4;
            case CENTER:
                return pos5;
            case RIGHT:
                return pos6;
            case BOTTOM_LEFT:
                return pos7;
            case BOTTOM_CENTER:
                return pos8;
            case BOTTOM_RIGHT:
                return pos9;
        }
        return null;
    }
}
