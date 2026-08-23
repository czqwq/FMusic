package com.Lilith.FMusic.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiScreen;

import com.Lilith.FMusic.Config;
import com.Lilith.FMusic.client.core.FMusicCore;
import com.Lilith.FMusic.client.core.FMusicHud;
import com.Lilith.FMusic.client.core.Point2f;
import com.Lilith.FMusic.codec.HudBasePosObj;
import com.Lilith.FMusic.codec.HudPosObj;
import com.Lilith.FMusic.codec.HudPosType;

/**
 * HUD 可视化配置界面 (照 PowerGoggles 的 PowerGogglesGuiHudConfig 模式):
 * - 实时渲染 HUD (drawScreen 中手动调用 hudUpdate, GUI 打开时 overlay 事件不触发)
 * - 每个启用的 HUD 模块绘制一个拖拽手柄, 拖拽实时调整位置
 * - 松开鼠标时保存到 config/FMusic.cfg (Forge Configuration)
 * - doesGuiPauseGame = false, 配置界面不暂停游戏
 */
public class FMusicHudConfigGui extends GuiScreen {

    /** 拖拽手柄半宽 (照 PowerGoggles 的 10x10 手柄) */
    private static final int HANDLE = 5;

    private final HudPosObj hudPos;
    private final FMusicHud hud;
    private DragTarget dragging;

    private enum DragTarget {
        INFO,
        LYRIC,
        STATE,
        PIC
    }

    public FMusicHudConfigGui() {
        this.hud = FMusicCore.getHud();
        if (hud != null && hud.getHudPos() != null) {
            this.hudPos = hud.getHudPos()
                .copy();
        } else {
            this.hudPos = HudPosObj.make();
        }
    }

    // ============ 渲染 ============

    @Override
    public void drawScreen(int x, int y, float partial) {
        // 实时渲染 HUD: GUI 打开时 RenderGameOverlayEvent 不触发, 这里手动渲染
        if (hud != null) {
            FMusicCore.hudUpdate();
        }

        // 每个启用的模块绘制一个拖拽手柄
        if (hudPos.info.enable) {
            drawHandle(moduleAnchor(hudPos.info, 0, 0), "info");
        }
        if (hudPos.lyric.enable) {
            drawHandle(moduleAnchor(hudPos.lyric, 0, 0), "lyric");
        }
        if (hudPos.state.enable) {
            drawHandle(moduleAnchor(hudPos.state, 0, 0), "state");
        }
        if (hudPos.pic.enable) {
            drawHandle(moduleAnchor(hudPos.pic, 0, 0), "pic");
        }

        // 顶部提示
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        String tip = "\u00a7e拖拽手柄调整 HUD 位置, 松开鼠标保存, Esc 关闭";
        font.drawStringWithShadow(tip, (width - font.getStringWidth(tip)) / 2, 5, 0xFFFFFF);

        super.drawScreen(x, y, partial);
    }

    /**
     * 模块锚点 (配置 x/y 对应的屏幕位置, 0 尺寸参考点)
     */
    private Point2f moduleAnchor(HudBasePosObj pos, int w, int h) {
        return FMusicHud.getPos(w, h, pos.x, pos.y, pos.pos);
    }

    /**
     * 绘制拖拽手柄: 红色方块 + 白色十字 (照 PowerGoggles)
     */
    private void drawHandle(Point2f anchor, String label) {
        int cx = (int) anchor.x;
        int cy = (int) anchor.y;
        drawRect(cx - HANDLE, cy - HANDLE, cx + HANDLE + 1, cy + HANDLE + 1, 0xFFFF3232);
        drawHorizontalLine(cx - HANDLE, cx + HANDLE, cy, 0xFFFFFFFF);
        drawVerticalLine(cx, cy + HANDLE, cy - HANDLE, 0xFFFFFFFF);
        Minecraft.getMinecraft().fontRenderer.drawStringWithShadow(label, cx + HANDLE + 2, cy - 4, 0xFFFFFF);
    }

    // ============ 交互 ============

    @Override
    protected void mouseClicked(int x, int y, int button) {
        if (hudPos.info.enable && isOnHandle(x, y, moduleAnchor(hudPos.info, 0, 0))) {
            dragging = DragTarget.INFO;
        } else if (hudPos.lyric.enable && isOnHandle(x, y, moduleAnchor(hudPos.lyric, 0, 0))) {
            dragging = DragTarget.LYRIC;
        } else if (hudPos.state.enable && isOnHandle(x, y, moduleAnchor(hudPos.state, 0, 0))) {
            dragging = DragTarget.STATE;
        } else if (hudPos.pic.enable && isOnHandle(x, y, moduleAnchor(hudPos.pic, 0, 0))) {
            dragging = DragTarget.PIC;
        }
        super.mouseClicked(x, y, button);
    }

    private boolean isOnHandle(int x, int y, Point2f anchor) {
        return x >= anchor.x - HANDLE && x <= anchor.x + HANDLE
            && y >= anchor.y - HANDLE - 4
            && y <= anchor.y + HANDLE + 4;
    }

    @Override
    protected void mouseClickMove(int x, int y, int button, long time) {
        if (dragging != null) {
            HudBasePosObj pos = moduleOf(dragging);
            Point2f point = inversePos(x, y, pos.pos);
            pos.x = (int) point.x;
            pos.y = (int) point.y;
            // 实时应用, HUD 立即跟随
            if (hud != null) {
                hud.setPos(hudPos);
            }
        }
        super.mouseClickMove(x, y, button, time);
    }

    @Override
    protected void mouseMovedOrUp(int x, int y, int action) {
        if (dragging != null) {
            // 松手时保存到 config/FMusic.cfg
            Config.saveHudPos(hudPos);
            dragging = null;
        }
        super.mouseMovedOrUp(x, y, action);
    }

    /**
     * 鼠标屏幕坐标 -> 模块配置偏移 (按当前 pos 方向反推 FMusicHud.getPos)
     */
    private Point2f inversePos(int mx, int my, HudPosType dir) {
        float sw = FMusicCore.bridge.getScreenWidth();
        float sh = FMusicCore.bridge.getScreenHeight();
        float x = mx;
        float y = my;
        if (dir == null) {
            return new Point2f(x, y);
        }
        switch (dir) {
            case TOP_CENTER:
                x = mx - sw / 2;
                break;
            case TOP_RIGHT:
                x = sw - mx;
                break;
            case LEFT:
                y = my - sh / 2;
                break;
            case CENTER:
                x = mx - sw / 2;
                y = my - sh / 2;
                break;
            case RIGHT:
                x = sw - mx;
                y = my - sh / 2;
                break;
            case BOTTOM_LEFT:
                y = sh - my;
                break;
            case BOTTOM_CENTER:
                x = mx - sw / 2;
                y = sh - my;
                break;
            case BOTTOM_RIGHT:
                x = sw - mx;
                y = sh - my;
                break;
            default:
                break;
        }
        return new Point2f(x, y);
    }

    private HudBasePosObj moduleOf(DragTarget target) {
        switch (target) {
            case INFO:
                return hudPos.info;
            case LYRIC:
                return hudPos.lyric;
            case STATE:
                return hudPos.state;
            case PIC:
                return hudPos.pic;
            default:
                return null;
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    @Override
    protected void keyTyped(char keyChar, int keyInt) {
        if (keyInt == 1) {
            Minecraft.getMinecraft()
                .displayGuiScreen(null);
        }
    }
}
