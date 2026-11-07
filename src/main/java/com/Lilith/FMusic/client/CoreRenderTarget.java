package com.Lilith.FMusic.client;

import java.nio.IntBuffer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;

import com.Lilith.FMusic.client.core.FMusicHud;
import com.Lilith.FMusic.client.core.Point2f;
import com.Lilith.FMusic.client.core.render.TextFrameBuffer;
import com.Lilith.FMusic.codec.HudPosType;

public class CoreRenderTarget extends TextFrameBuffer<String> {

    /**
     * glGetInteger 的临时缓冲。只在渲染线程上 pushScissor/popScissor 之间用, 不跨线程。
     */
    private static final IntBuffer SCRATCH = BufferUtils.createIntBuffer(4);

    private final boolean isState;

    public CoreRenderTarget(String name) {
        isState = name.equals("state");
    }

    @Override
    public void putText(String text, int y, int color, boolean shadow) {
        color = (color & 0x00FFFFFF) | 0xFF000000;
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        int width = font.getStringWidth(text);
        if (width == 0) {
            return;
        }

        int height = font.FONT_HEIGHT + (shadow ? 1 : 0);
        if (isState) {
            y = 0;
        }
        texts.add(new TextItem<>(width, height, y, text, shadow, color));
    }

    @Override
    public void draw(float alpha, int x, int y, int maxWidth, HudPosType dir) {
        if (texts.isEmpty()) {
            return;
        }

        FontRenderer font = Minecraft.getMinecraft().fontRenderer;

        for (TextItem<String> entry : texts) {
            int displayWidth = maxWidth != -1 ? Math.min(entry.width, maxWidth) : entry.width;
            Point2f point = FMusicHud.getPos(displayWidth, entry.height, x, y, dir);

            int drawX = (int) point.x;
            int drawY = (int) (point.y + entry.y);
            int finalColor = applyAlpha(entry.color, alpha);

            GL11.glPushMatrix();
            GL11.glTranslatef(drawX, drawY, 0);

            if (maxWidth != -1 && entry.width > maxWidth) {
                int scrollOffset = (int) getOffset(entry, maxWidth);
                int[] saved = pushScissor(drawX, drawY, maxWidth, entry.height);
                drawString(font, entry, -scrollOffset, 0, finalColor);
                if (scrollOffset > 0) {
                    drawString(font, entry, -scrollOffset + entry.width, 0, finalColor);
                }
                popScissor(saved);
            } else {
                drawString(font, entry, 0, 0, finalColor);
            }

            GL11.glPopMatrix();
        }
    }

    @Override
    public void drawLine(float x, float y, float alpha, int line) {
        if (texts.isEmpty()) {
            return;
        }
        if (line >= texts.size()) {
            return;
        }
        TextItem<String> entry = texts.get(line);
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;
        GL11.glPushMatrix();
        GL11.glTranslatef((float) x, (float) (y + entry.y), 0);
        drawString(font, entry, 0, 0, applyAlpha(entry.color, alpha));
        GL11.glPopMatrix();
    }

    @Override
    public void drawWithState(float alpha, int x, int y, int maxWidth, float state, HudPosType dir) {
        if (texts.isEmpty()) {
            return;
        }
        FontRenderer font = Minecraft.getMinecraft().fontRenderer;

        for (TextItem<String> entry : texts) {
            int displayWidth = maxWidth != -1 ? Math.min(entry.width, maxWidth) : entry.width;
            Point2f point = FMusicHud.getPos(displayWidth, entry.height, x, y, dir);

            int drawX = (int) point.x;
            int drawY = (int) (point.y + entry.y);
            int finalColor = applyAlpha(entry.color, alpha);

            GL11.glPushMatrix();
            GL11.glTranslatef(drawX, drawY, 0);

            if (maxWidth != -1 && entry.width > maxWidth) {
                float maxOffset = entry.width - maxWidth;
                float texOffset = maxOffset * state;
                int revealWidth = (int) (maxWidth * state);

                int[] saved = pushScissor(drawX, drawY, revealWidth, entry.height);
                drawString(font, entry, -(int) texOffset, 0, finalColor);
                popScissor(saved);
            } else {
                int revealWidth = (int) (entry.width * state);
                int[] saved = pushScissor(drawX, drawY, revealWidth, entry.height);
                drawString(font, entry, 0, 0, finalColor);
                popScissor(saved);
            }

            GL11.glPopMatrix();
        }
    }

    @Override
    public Point2f getLine(int line) {
        if (line >= texts.size()) {
            return new Point2f(0, 0);
        }
        TextItem<String> entry = texts.get(line);
        return new Point2f(entry.width, entry.height);
    }

    private static void drawString(FontRenderer font, TextItem<String> entry, int x, int y, int color) {
        if (entry.shadow) {
            font.drawStringWithShadow(entry.component, x, y, color);
        } else {
            font.drawString(entry.component, x, y, color);
        }
    }

    /**
     * 开一个裁剪框, 并把**进来时**的裁剪状态记下来 (给 {@link #popScissor(int[])} 用)。
     *
     * <p>
     * 两处和原来不同:
     * <ul>
     * <li>缩放因子改用 {@link ScaledResolution#getScaleFactor()}: 原版算界面的缩放是
     * "从 1 往上试, 直到 scaledWidth/scale >= 320 且 scaledHeight/scale >= 240",
     * 和 {@code gameSettings.guiScale} 并不是一回事 (guiScale=0 自动时差得最多, 1080p 上是 4 vs 3),
     * 用错的话滚动歌词的裁剪框会比文字小一圈, 字直接被切掉。</li>
     * <li>关裁剪不再是无条件 {@code glDisable}, 而是恢复成进来时的样子: HUD 画在原版
     * overlay 中间, 外面可能已经有裁剪状态 (聊天/TAB 列表/别的 mod), 直接关掉会破坏它们。</li>
     * </ul>
     */
    private static int[] pushScissor(int x, int y, int width, int height) {
        Minecraft mc = Minecraft.getMinecraft();
        int scale = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight).getScaleFactor();

        SCRATCH.clear();
        GL11.glGetInteger(GL11.GL_SCISSOR_BOX, SCRATCH);
        int[] saved = new int[] { GL11.glIsEnabled(GL11.GL_SCISSOR_TEST) ? 1 : 0, SCRATCH.get(0), SCRATCH.get(1),
            SCRATCH.get(2), SCRATCH.get(3) };

        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(
            x * scale,
            mc.displayHeight - (y + height) * scale,
            Math.max(0, width * scale),
            Math.max(0, height * scale));
        return saved;
    }

    /**
     * 还原 {@link #pushScissor(int, int, int, int)} 记下的裁剪状态。
     */
    private static void popScissor(int[] saved) {
        GL11.glScissor(saved[1], saved[2], saved[3], saved[4]);
        if (saved[0] != 0) {
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
        } else {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }
    }

    private static int applyAlpha(int color, float alpha) {
        int a = (int) (alpha * 255);
        if (a < 0) a = 0;
        if (a > 255) a = 255;
        return (color & 0x00FFFFFF) | (a << 24);
    }
}
