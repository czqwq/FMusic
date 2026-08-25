package com.Lilith.FMusic.client;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundCategory;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.resources.IResource;
import net.minecraft.network.NetworkManager;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent17;
import net.minecraftforge.common.MinecraftForge;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.Lilith.FMusic.client.core.FMusicBridge;
import com.Lilith.FMusic.client.core.FMusicCore;
import com.Lilith.FMusic.client.core.render.PictureFrameBuffer;
import com.Lilith.FMusic.client.core.render.TextFrameBuffer;
import com.Lilith.FMusic.client.core.render.TextureRender;

import cpw.mods.fml.client.FMLClientHandler;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLLoadCompleteEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import paulscode.sound.Channel;
import paulscode.sound.Library;
import paulscode.sound.SoundSystem;
import paulscode.sound.libraries.ChannelLWJGLOpenAL;

@SideOnly(Side.CLIENT)
public class FMusic implements FMusicBridge {

    public static final Logger LOGGER = LogManager.getLogger("FMusic Client");
    public static SoundSystem sound;

    public static void runMain(Runnable runnable) {
        FMLClientHandler.instance()
            .getClient()
            .func_152344_a(runnable);
    }

    public void sendMessage(String data) {
        data = "[FMusic Client]" + data;
        LOGGER.warn(data);
        String finalData = data;
        FMLClientHandler.instance()
            .getClient()
            .func_152344_a(
                () -> FMLClientHandler.instance()
                    .getClient().ingameGUI.getChatGUI()
                        .addToSentMessages(finalData));
    }

    public void test(final FMLLoadCompleteEvent event) {
        Minecraft.getMinecraft()
            .getSoundHandler();

        Library library = ((IGetSoundHandler) sound).fMusic_Client$getSoundLibrary();
        IGetSound sound1 = (IGetSound) library;
        List<Channel> list = sound1.fMusic_Client$getStreamingChannels();
        ChannelLWJGLOpenAL channel = (ChannelLWJGLOpenAL) list.get(list.size() - 1);
        FMusicCore.init(new File("config").toPath(), this, channel.ALSource);
        FMusicCore.renderInit();
    }

    public void preload(final FMLPreInitializationEvent evt) {
        MinecraftForge.EVENT_BUS.register(this);
        FMLCommonHandler.instance()
            .bus()
            .register(this);
        NetworkRegistry.INSTANCE.newEventDrivenChannel("fmusic:channel")
            .register(this);
    }

    @SubscribeEvent
    public void onSound(final PlaySoundEvent17 e) {
        if (!FMusicCore.isPlay()) return;
        SoundCategory data = e.category;
        if (data == null) return;
        switch (data) {
            case MUSIC:
            case RECORDS:
                new Thread(() -> {
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException ex) {
                        ex.printStackTrace();
                    }
                    FMLClientHandler.instance()
                        .getClient()
                        .func_152344_a(() -> { e.manager.stopSound(e.sound); });
                }).start();
        }
    }

    @SubscribeEvent
    public void onServerQuit(final FMLNetworkEvent.ClientDisconnectionFromServerEvent e) {
        FMusicCore.onServerQuit();
    }

    public int getScreenWidth() {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution scaledresolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        return scaledresolution.getScaledWidth();
    }

    public int getScreenHeight() {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution scaledresolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        return scaledresolution.getScaledHeight();
    }

    public int getTextWidth(String item) {
        return Minecraft.getMinecraft().fontRenderer.getStringWidth(item);
    }

    public int getFontHeight() {
        return Minecraft.getMinecraft().fontRenderer.FONT_HEIGHT;
    }

    @Override
    public void stopPlayMusic() {
        Minecraft.getMinecraft()
            .getSoundHandler()
            .stopSounds();
        Minecraft.getMinecraft()
            .getSoundHandler()
            .stopSounds();
    }

    @Override
    public TextFrameBuffer makeTextRender(String name) {
        return new CoreRenderTarget(name);
    }

    @Override
    public TextureRender makeTextureRender(String file) {
        return new TexRender(file);
    }

    @Override
    public PictureFrameBuffer makePictureRender(int size) {
        return new PicRender(size);
    }

    @Override
    public String readText(String file) {
        try {
            IResource resource = Minecraft.getMinecraft()
                .getResourceManager()
                .getResource(new ResourceLocation("fmusic", file));
            InputStream inputStream = resource.getInputStream();
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) != -1) {
                result.write(buffer, 0, length);
            }
            inputStream.close();
            return result.toString("UTF-8");
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public InputStream readFile(String file) {
        try {
            IResource resource = Minecraft.getMinecraft()
                .getResourceManager()
                .getResource(new ResourceLocation("fmusic", file));
            return resource.getInputStream();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void onClientPacket(final FMLNetworkEvent.ClientCustomPacketEvent evt) {
        try {
            FMusicCore.packRead(evt.packet.payload());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void onRenderOverlay(final RenderGameOverlayEvent.Pre e) {
        if (e.type == RenderGameOverlayEvent.ElementType.PORTAL) {
            FMusicCore.hudUpdate();
        }
    }

    @SubscribeEvent
    @SideOnly(Side.CLIENT)
    public void onTick(final TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            FMusicCore.tick();
        }
    }

    public float getVolume() {
        return Minecraft.getMinecraft().gameSettings.getSoundLevel(SoundCategory.RECORDS);
    }

    @Override
    public void kick() {
        Minecraft client = Minecraft.getMinecraft();

        NetHandlerPlayClient packetListener = client.getNetHandler();
        if (packetListener != null) {
            NetworkManager connection = packetListener.getNetworkManager();
            connection.closeChannel(new ChatComponentText("Old FMusic server"));
        }
    }
}
