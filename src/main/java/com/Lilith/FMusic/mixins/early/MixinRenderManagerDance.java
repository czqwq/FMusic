package com.Lilith.FMusic.mixins.early;

import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.Lilith.FMusic.dango.mc1710.DanceRender;

/**
 * 「生物跟着节奏跳」的实体分发外层挂点（YSMU / 替换渲染器兼容），具体原因和做法见
 * {@link DanceRender}。
 *
 * <p>
 * 为什么不在 {@code RenderLivingEvent.Pre/Post}（原先的写法）：
 * YSMU 的玩家渲染在 {@code RenderPlayerEvent.Pre} 就取消了原版 {@code RenderPlayer#doRender}，
 * 根本走不到 {@code super.doRender} 里的 {@code RenderLivingEvent.Pre}；NPC / 载具则在
 * {@code RenderLivingEvent.Pre} 取消或直接被 {@code render.doRender} 的 redirect 顶掉，
 * 原写法要么套不上姿势，要么只 push 不 pop。放到 {@code func_147939_a} 外层后，
 * 无论里面是原版渲染器、YSMU 的 GeckoLib 渲染器还是别的 mod 的替换渲染器，都吃同一套变换。
 *
 * <p>
 * 三段注入与 FriendSwine 的 RenderManagerMixin 同形：
 * <ul>
 * <li>{@code HEAD}：套上压扁 / 拉长矩阵和转圈朝向（因为世界渲染的实体多，未抖动时几乎零开销）；</li>
 * <li>{@code doRenderShadowAndFire} 调用前：把变换撤掉，阴影 / 火焰不会被一起压扁；</li>
 * <li>{@code RETURN}：收尾并弹矩阵（走没走到阴影那段都要成对）。</li>
 * </ul>
 *
 * <p>
 * priority 1100：沿用 FriendSwine 在同一个 Forge 环境下的选择；YSMU 在同目标方法上的 redirect 只替换
 * {@code render.doRender} 这一句，与这里的三段注入互不冲突。
 */
@Mixin(value = RenderManager.class, priority = 1100)
public class MixinRenderManagerDance {

    private static final String TARGET = "func_147939_a(Lnet/minecraft/entity/Entity;DDDFFZ)Z";
    private static final String SHADOW_AND_FIRE = "Lnet/minecraft/client/renderer/entity/Render;doRenderShadowAndFire(Lnet/minecraft/entity/Entity;DDDFF)V";

    @Inject(method = TARGET, at = @At("HEAD"))
    private void fmusic$danceBegin(Entity entity, double x, double y, double z, float yaw, float partial,
        boolean hideDebug, CallbackInfoReturnable<Boolean> cir) {
        DanceRender.begin(entity, x, y, z);
    }

    @Inject(method = TARGET, at = @At(value = "INVOKE", target = SHADOW_AND_FIRE))
    private void fmusic$danceBeforeOverlays(Entity entity, double x, double y, double z, float yaw, float partial,
        boolean hideDebug, CallbackInfoReturnable<Boolean> cir) {
        DanceRender.beforeOverlays(entity);
    }

    @Inject(method = TARGET, at = @At("RETURN"))
    private void fmusic$danceEnd(Entity entity, double x, double y, double z, float yaw, float partial,
        boolean hideDebug, CallbackInfoReturnable<Boolean> cir) {
        DanceRender.end();
    }
}
