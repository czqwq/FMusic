package com.Lilith.FMusic.client.core.render;

import java.nio.IntBuffer;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;

/**
 * FMusic HUD 的 GL 状态护栏。
 *
 * <p>
 * HUD 是在 {@code RenderGameOverlayEvent.Pre(PORTAL)} 里画的 —— 也就是原版
 * {@code GuiIngameForge.renderGameOverlay} 的**正中间**: 暗角已经画完, 快捷栏 / 血量 /
 * 聊天 / TAB 列表都还没画, 而且它们全部**共用同一套 GL 状态**, 谁都不重新设一遍。
 * 所以 HUD 只要把某个状态改了不还原, 受害的不是 HUD 自己那一小块, 而是这一帧剩下的
 * 整个画面 (最典型的是全屏合成 quad: 暗角 / 传送门遮罩 / 睡眠黑屏都被画成整屏黑)。
 *
 * <p>
 * 更麻烦的是 GTNH 装的 <b>Angelica</b> (1.7.10 的 GL 3.3 core 渲染层): 它用
 * {@code GlStateManager} 缓存 GL 状态, 把立即模式几何**先攒后刷**。攒着的几何按刷新时的
 * 状态出图, 所以 HUD 在中途改掉 BLEND / SCISSOR / 纹理绑定, 会"回溯"影响之前已经排队的
 * 全屏 quad —— 表现就是播放音乐时整个游戏黑屏, 日志里什么都没有。
 *
 * <p>
 * 因此: 画 HUD 之前 push() 一份状态, 画完 restore()。{@link #diff()} 用来在真的漏状态时
 * 打一条日志 (只打一次), 免得再出现"静默失败"。
 *
 * <p>
 * 这里面只用 GL 1.1 / 1.4 core 就有的入口 (glEnable/glDisable/glIsEnabled/glGetBoolean/
 * glGetInteger/glDepthMask/glBlendFuncSeparate/glActiveTexture/glBindTexture/glScissor/
 * glMatrixMode/glColor4f)。**不要**在这里加兼容 profile 专用的函数
 * (glPushAttrib / glPopAttrib / glShadeModel / glMultiTexCoord*f / glEnableClientState):
 * Angelica 跑的是 core profile, 重定向表里没有的调用会直接落到原生 GL 上, 轻则状态缓存
 * 和真实状态对不上 (同样黑屏), 重则 LWJGL 在原生层 abort 整个 JVM。
 */
public final class HudGlState {

    /**
     * HUD 期间可能被改到的开关。顺序和 {@link #names()} 一致, 只为了 diff 的日志可读。
     */
    private static final int[] CAPS = { GL11.GL_BLEND, GL11.GL_ALPHA_TEST, GL11.GL_DEPTH_TEST, GL11.GL_CULL_FACE,
        GL11.GL_LIGHTING, GL11.GL_FOG, GL11.GL_SCISSOR_TEST };

    private static final String[] CAP_NAMES = { "BLEND", "ALPHA_TEST", "DEPTH_TEST", "CULL_FACE", "LIGHTING", "FOG",
        "SCISSOR_TEST" };

    /**
     * 原版只用 0 号 (主纹理) 和 1 号 (光照贴图) 两个纹理单元, HUD 也只碰这两个。
     */
    private static final int TEXTURE_UNITS = 2;

    /**
     * glGet* 的临时缓冲。只会在渲染线程上 push()/restore() 之间用, 不跨线程。
     */
    private static final IntBuffer SCRATCH = BufferUtils.createIntBuffer(16);

    private final boolean[] caps = new boolean[CAPS.length];
    private final boolean depthMask;
    private final int blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha;
    private final int activeTexture;
    private final boolean[] tex2d = new boolean[TEXTURE_UNITS];
    private final int[] texBinding = new int[TEXTURE_UNITS];
    private final int matrixMode;
    private final int[] scissorBox = new int[4];

    private HudGlState() {
        for (int i = 0; i < CAPS.length; i++) {
            caps[i] = GL11.glIsEnabled(CAPS[i]);
        }

        depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        blendSrcRgb = getInteger(GL14.GL_BLEND_SRC_RGB);
        blendDstRgb = getInteger(GL14.GL_BLEND_DST_RGB);
        blendSrcAlpha = getInteger(GL14.GL_BLEND_SRC_ALPHA);
        blendDstAlpha = getInteger(GL14.GL_BLEND_DST_ALPHA);
        matrixMode = getInteger(GL11.GL_MATRIX_MODE);

        activeTexture = getInteger(GL13.GL_ACTIVE_TEXTURE);
        for (int i = 0; i < TEXTURE_UNITS; i++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + i);
            tex2d[i] = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
            texBinding[i] = getInteger(GL11.GL_TEXTURE_BINDING_2D);
        }
        GL13.glActiveTexture(activeTexture);

        getIntegers(GL11.GL_SCISSOR_BOX, scissorBox);
    }

    /**
     * 记下当前 GL 状态。必须在渲染线程调用。
     */
    public static HudGlState push() {
        return new HudGlState();
    }

    /**
     * 原样还原 push() 时的状态, 让这一帧剩下的原版绘制看到的是它自己期望的状态。
     */
    public void restore() {
        // 先把 scissor 的 box 设回去, 再按快照开关 SCISSOR_TEST: 顺序反过来的话中间那一瞬间
        // 会用 HUD 留下的裁剪框 (虽然中间没有绘制, 但状态缓存型的渲染层仍然会记下这个顺序)。
        GL11.glScissor(scissorBox[0], scissorBox[1], scissorBox[2], scissorBox[3]);

        for (int i = 0; i < CAPS.length; i++) {
            set(CAPS[i], caps[i]);
        }

        GL11.glDepthMask(depthMask);
        GL14.glBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);

        // 纹理单元倒着还原, 收尾再设回当前激活的单元
        for (int i = TEXTURE_UNITS - 1; i >= 0; i--) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + i);
            set(GL11.GL_TEXTURE_2D, tex2d[i]);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texBinding[i]);
        }
        GL13.glActiveTexture(activeTexture);

        // Tessellator 画完会把当前颜色留成最后一个顶点的颜色 (alpha 可能不是 1), 原版后面
        // 画全屏合成 quad 时直接吃这个颜色, 所以这里归位 (原版自己在 PORTAL 之后也会设一次白)。
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);

        GL11.glMatrixMode(matrixMode);
    }

    /**
     * 对比"现在的 GL 状态"和 push() 时记下的状态, 返回被改掉的状态名 (逗号分隔)。
     * 空串 = HUD 没漏状态。只用于诊断日志。
     */
    public String diff() {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < CAPS.length; i++) {
            if (GL11.glIsEnabled(CAPS[i]) != caps[i]) {
                append(out, CAP_NAMES[i]);
            }
        }

        if (GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK) != depthMask) {
            append(out, "DEPTH_MASK");
        }
        if (getInteger(GL14.GL_BLEND_SRC_RGB) != blendSrcRgb || getInteger(GL14.GL_BLEND_DST_RGB) != blendDstRgb
            || getInteger(GL14.GL_BLEND_SRC_ALPHA) != blendSrcAlpha
            || getInteger(GL14.GL_BLEND_DST_ALPHA) != blendDstAlpha) {
            append(out, "BLEND_FUNC");
        }
        if (getInteger(GL11.GL_MATRIX_MODE) != matrixMode) {
            append(out, "MATRIX_MODE");
        }

        int[] box = new int[4];
        getIntegers(GL11.GL_SCISSOR_BOX, box);
        if (box[0] != scissorBox[0] || box[1] != scissorBox[1] || box[2] != scissorBox[2] || box[3] != scissorBox[3]) {
            append(out, "SCISSOR_BOX");
        }

        int now = getInteger(GL13.GL_ACTIVE_TEXTURE);
        for (int i = 0; i < TEXTURE_UNITS; i++) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + i);
            if (getInteger(GL11.GL_TEXTURE_BINDING_2D) != texBinding[i]) {
                append(out, "TEX_BINDING_" + i);
            }
            if (GL11.glIsEnabled(GL11.GL_TEXTURE_2D) != tex2d[i]) {
                append(out, "TEX_2D_" + i);
            }
        }
        GL13.glActiveTexture(now);
        if (now != activeTexture) {
            append(out, "ACTIVE_TEXTURE_UNIT");
        }

        return out.toString();
    }

    private static void append(StringBuilder out, String name) {
        if (out.length() > 0) {
            out.append(", ");
        }
        out.append(name);
    }

    private static void set(int cap, boolean on) {
        if (on) {
            GL11.glEnable(cap);
        } else {
            GL11.glDisable(cap);
        }
    }

    private static int getInteger(int pname) {
        SCRATCH.clear();
        GL11.glGetInteger(pname, SCRATCH);
        return SCRATCH.get(0);
    }

    private static void getIntegers(int pname, int[] out) {
        SCRATCH.clear();
        GL11.glGetInteger(pname, SCRATCH);
        for (int i = 0; i < out.length; i++) {
            out[i] = SCRATCH.get(i);
        }
    }
}
