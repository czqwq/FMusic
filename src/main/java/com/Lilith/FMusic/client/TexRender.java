package com.Lilith.FMusic.client;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.data.TextureMetadataSection;
import net.minecraft.util.ResourceLocation;

import org.apache.commons.io.IOUtils;
import org.lwjgl.opengl.GL11;

import com.Lilith.FMusic.client.core.FMusicHud;
import com.Lilith.FMusic.client.core.Point2f;
import com.Lilith.FMusic.client.core.render.TextureRender;
import com.Lilith.FMusic.codec.HudPosType;

public class TexRender extends TextureRender {

    private final Tex sourceTexture;

    public TexRender(String texture) {
        super(texture);

        ResourceLocation location = new ResourceLocation("fmusic", texture);

        sourceTexture = new Tex(location);

        try {
            IResourceManager resourceManager = Minecraft.getMinecraft()
                .getResourceManager();
            sourceTexture.loadTexture(resourceManager, this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void drawPic(float x, float y, float alpha) {
        // 只借状态, 不占状态: 画完把深度写掩码和混合开关恢复成进来时的样子。
        // (原来无条件 glDisable(GL_BLEND) 会把原版后续的全屏合成 quad 画成整屏黑,
        // 见 core/render/HudGlState 的说明)
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, sourceTexture.getGlTextureId());
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_BLEND);

        float w1 = (float) width / 2;
        float h1 = (float) height / 2;

        GL11.glPushMatrix();
        GL11.glTranslatef(x + w1, y + h1, 0.0f);

        float x0 = -w1;
        float x1 = w1;
        float y0 = -h1;
        float y1 = h1;
        float z = 0;
        float u0 = 0;
        float u1 = 1;
        float v0 = 0;
        float v1 = 1;

        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.setColorRGBA_F(1.0f, 1.0f, 1.0f, alpha);
        tessellator.addVertexWithUV(x0, y1, z, u0, v1);
        tessellator.addVertexWithUV(x1, y1, z, u1, v1);
        tessellator.addVertexWithUV(x1, y0, z, u1, v0);
        tessellator.addVertexWithUV(x0, y0, z, u0, v0);
        tessellator.draw();

        // Tessellator 会把当前颜色留成最后一个顶点色 (alpha 未必是 1), 归位免得后面被染暗
        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GL11.glDepthMask(depthMask);
        if (!blend) {
            GL11.glDisable(GL11.GL_BLEND);
        }

        GL11.glPopMatrix();
    }

    @Override
    public void drawPic(float x, float y, float width, float alpha) {
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, sourceTexture.getGlTextureId());
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_BLEND);

        float w1 = (float) (this.width / 2) * width;
        float h1 = (float) height / 2;

        GL11.glPushMatrix();
        GL11.glTranslatef(x + w1, y + h1, 0.0f);

        float x0 = -w1;
        float x1 = w1;
        float y0 = -h1;
        float y1 = h1;
        float z = 0;
        float u0 = 0;
        float u1 = width;
        float v0 = 0;
        float v1 = 1;

        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.setColorRGBA_F(1.0f, 1.0f, 1.0f, alpha);
        tessellator.addVertexWithUV(x0, y1, z, u0, v1);
        tessellator.addVertexWithUV(x1, y1, z, u1, v1);
        tessellator.addVertexWithUV(x1, y0, z, u1, v0);
        tessellator.addVertexWithUV(x0, y0, z, u0, v0);
        tessellator.draw();

        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GL11.glDepthMask(depthMask);
        if (!blend) {
            GL11.glDisable(GL11.GL_BLEND);
        }

        GL11.glPopMatrix();
    }

    @Override
    public void drawPic(float x, float y, float width, float height, HudPosType dir, float alpha) {
        Point2f point = FMusicHud.getPos(width, height, x, y, dir);

        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);

        GL11.glBindTexture(GL11.GL_TEXTURE_2D, sourceTexture.getGlTextureId());
        GL11.glDepthMask(false);
        GL11.glEnable(GL11.GL_BLEND);

        float w1 = width / 2;
        float h1 = height / 2;

        GL11.glPushMatrix();
        GL11.glTranslatef(point.x + w1, point.y + h1, 0.0f);

        float x0 = -w1;
        float x1 = w1;
        float y0 = -h1;
        float y1 = h1;
        float z = 0;
        float u0 = 0;
        float u1 = 1;
        float v0 = 0;
        float v1 = 1;

        Tessellator tessellator = Tessellator.instance;
        tessellator.startDrawingQuads();
        tessellator.setColorRGBA_F(1.0f, 1.0f, 1.0f, alpha);
        tessellator.addVertexWithUV(x0, y1, z, u0, v1);
        tessellator.addVertexWithUV(x1, y1, z, u1, v1);
        tessellator.addVertexWithUV(x1, y0, z, u1, v0);
        tessellator.addVertexWithUV(x0, y0, z, u0, v0);
        tessellator.draw();

        GL11.glColor4f(1.0f, 1.0f, 1.0f, 1.0f);
        GL11.glDepthMask(depthMask);
        if (!blend) {
            GL11.glDisable(GL11.GL_BLEND);
        }

        GL11.glPopMatrix();
    }

    public static class Tex extends SimpleTexture {

        public Tex(ResourceLocation resourceLocation) {
            super(resourceLocation);
        }

        public void loadTexture(IResourceManager resourceManager, TexRender render) throws IOException {
            this.deleteGlTexture();
            IResource iresource = null;
            InputStream inputstream = null;

            try {
                iresource = resourceManager.getResource(this.textureLocation);
                inputstream = iresource.getInputStream();
                BufferedImage bufferedimage = ImageIO.read(inputstream);
                render.width = bufferedimage.getWidth();
                render.height = bufferedimage.getHeight();
                boolean flag = false;
                boolean flag1 = false;

                if (iresource.hasMetadata()) {
                    try {
                        TextureMetadataSection texturemetadatasection = (TextureMetadataSection) iresource
                            .getMetadata("texture");

                        if (texturemetadatasection != null) {
                            flag = texturemetadatasection.getTextureBlur();
                            flag1 = texturemetadatasection.getTextureClamp();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                TextureUtil.uploadTextureImageAllocate(this.getGlTextureId(), bufferedimage, flag, flag1);
            } finally {
                IOUtils.closeQuietly(inputstream);
            }
        }
    }
}
