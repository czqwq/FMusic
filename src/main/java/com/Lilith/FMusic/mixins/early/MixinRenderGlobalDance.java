package com.Lilith.FMusic.mixins.early;

import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.EntityLivingBase;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.Lilith.FMusic.dango.mc1710.DanceRender;

/**
 * 给「生物跟着节奏跳」划出“只在世界实体渲染里生效”的范围，见 {@link DanceRender}。
 *
 * <p>
 * 实体分发 {@code RenderManager.func_147939_a} 不只世界渲染会用：物品栏纸娃娃
 * （{@code GuiInventory.func_147046_a} 里 {@code RenderManager.instance.renderEntityWithPosYaw}）、
 * 各种 GUI 实体预览也走它。用 {@code RenderGlobal.renderEntities} 的 HEAD/RETURN 记深度，
 * 这些“看模型”的界面就不会跟着抖；世界里的实体（包括开着界面时背景里仍在渲染的）照常抖。
 * 与 FriendSwine 的 WorldRenderScope / WorldRenderMixin 同形。
 */
@Mixin(RenderGlobal.class)
public class MixinRenderGlobalDance {

    @Inject(
        method = "renderEntities(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/client/renderer/culling/ICamera;F)V",
        at = @At("HEAD"))
    private void fmusic$danceBeginWorld(EntityLivingBase viewer, ICamera camera, float partial, CallbackInfo ci) {
        DanceRender.beginWorld();
    }

    @Inject(
        method = "renderEntities(Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/client/renderer/culling/ICamera;F)V",
        at = @At("RETURN"))
    private void fmusic$danceEndWorld(EntityLivingBase viewer, ICamera camera, float partial, CallbackInfo ci) {
        DanceRender.endWorld();
    }
}
