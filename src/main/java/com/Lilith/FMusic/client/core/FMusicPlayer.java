package com.Lilith.FMusic.client.core;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Stack;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import net.minecraft.client.Minecraft;
import net.minecraft.util.StatCollector;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.io.CloseMode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;

import com.Lilith.FMusic.Config;
import com.Lilith.FMusic.client.core.objs.PlayTaskObj;
import com.Lilith.FMusic.client.core.player.decoder.BuffPack;
import com.Lilith.FMusic.client.core.player.decoder.IDecoder;
import com.Lilith.FMusic.client.core.player.decoder.m4a.M4ADecoder;
import com.Lilith.FMusic.client.core.player.decoder.mp3.Mp3Decoder;
import com.Lilith.FMusic.client.core.player.decoder.ogg.OggDecoder;
import com.Lilith.FMusic.server.core.FMusic;

public class FMusicPlayer extends InputStream {

    private static final Logger LOGGER = LogManager.getLogger("FMusic Player");

    private final Stack<PlayTaskObj> tasks = new Stack<>();
    private final Semaphore semaphore = new Semaphore(0);
    private final Semaphore semaphoreReload = new Semaphore(0);

    private PlayTaskObj nowTask;
    private CloseableHttpResponse response;
    private BufferedInputStream content;
    private boolean isClose = false;
    private boolean reload = false;
    private IDecoder decoder;
    private boolean isPlay = false;
    private boolean wait = false;
    private int index = -1;
    private final IntBuffer source;
    private long local;
    private long contentLength = -1;
    /** 文件总长度 (首次连接记录, 用于断流判断; contentLength 是本次响应的剩余长度, 坐标系不同) */
    private long totalLength = -1;
    private int reconnectCount = 0;
    private int decodeErrorCount = 0;
    private boolean isRun;
    private boolean isChat;
    private int chatCount = 0;
    /**
     * 游戏暂停(单人按Esc, pause_at_freeze=true)时音乐是否被冻结
     */
    private boolean frozen;
    /**
     * POS 先于 PLAY 到达时缓存的跳转时间 (ms), 任务开始时应用
     */
    private int pendingTime = 0;

    public FMusicPlayer(IntBuffer source) {
        this.source = source;
        new Thread(this::run, "fmusic_run").start();
        FMusicCore.service.scheduleAtFixedRate(this::timerTick, 0, 10, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        isRun = false;
        isClose = true;
        semaphore.release();
        semaphoreReload.release();
    }

    public void setChat() {
        isChat = true;
    }

    public void timerTick() {
        if (isPlay && nowTask != null) {
            nowTask.time += 10;
        }
    }

    public boolean isPlay() {
        return isPlay;
    }

    public void setTime(int time) {
        if (nowTask == null) {
            // 播放任务尚未开始 (PLAY/POS 同批到达时 POS 先于任务弹出), 缓存待任务开始时应用
            FMusicLog.debug(LOGGER, StatCollector.translateToLocal("fmusic.log.player.seek_cached"));
            pendingTime = time;
            return;
        }
        FMusicLog.debug(LOGGER, StatCollector.translateToLocal("fmusic.log.player.seek_request"));
        isClose = true;
        nowTask.time = time;
        tasks.clear();
        tasks.push(nowTask);
        semaphore.release();
    }

    public void connect() throws IOException {
        streamClose();
        HttpGet request = new HttpGet(nowTask.url);
        request.setHeader("Range", "bytes=" + local + "-");
        response = FMusicCore.client.execute(request);
        int statusCode = response.getCode();
        if (statusCode < 200 || statusCode >= 400) {
            throw new IOException("Unexpected code " + statusCode);
        }
        HttpEntity entity = response.getEntity();
        if (entity == null) {
            throw new IOException("Response entity is null");
        }
        contentLength = entity.getContentLength();
        reconnectCount = 0;
        if (local == 0) {
            totalLength = contentLength;
        }
        if (local > 0 && statusCode == 200) {
            // 服务器忽略 Range 返回全量: 继续读会从头播放 (卡开头音), 拒绝并交由上层处理
            throw new IOException("server ignored Range request (200), local=" + local);
        }
        content = new BufferedInputStream(entity.getContent());
        FMusicLog.debug(
            LOGGER,
            StatCollector
                .translateToLocalFormatted("fmusic.log.player.connect", local, statusCode, contentLength, totalLength));
    }

    private void resetSource() {
        AL10.alSourceStop(index);
        AL10.alSourcei(index, AL10.AL_BUFFER, AL10.AL_NONE);

        int queued;
        do {
            queued = AL10.alGetSourcei(index, AL10.AL_BUFFERS_QUEUED);
            if (queued > 0) {
                int buffer = AL10.alSourceUnqueueBuffers(index);
                if (buffer != 0) {
                    AL10.alDeleteBuffers(buffer);
                }
            }
        } while (queued > 0);
    }

    private void checkChat() {
        if (isChat) {
            chatCount++;
            if (chatCount >= 200) {
                isChat = false;
                chatCount = 0;
            }
        }
    }

    private void dequeue() {
        int processed = AL10.alGetSourcei(index, AL10.AL_BUFFERS_PROCESSED);
        for (int i = 0; i < processed; i++) {
            int buffer = AL10.alSourceUnqueueBuffers(index);
            if (buffer != 0) {
                AL10.alDeleteBuffers(buffer);
            }
        }
    }

    private void checkVolume() {
        float temp = FMusicCore.bridge.getVolume();
        float now = AL10.alGetSourcef(index, AL10.AL_GAIN);
        if (isChat) {
            temp *= 0.5F;
        }
        if (temp < 0.05F) {
            temp = 0.05F;
        }
        if (now != temp) {
            AL10.alSourcef(index, AL10.AL_GAIN, temp);
        }
    }

    private void run() {
        if (isRun) {
            return;
        }
        isRun = true;
        while (true) {
            try {
                semaphore.acquire();
                if (!isRun) {
                    return;
                }

                if (index == -1) {
                    index = AL10.alGenSources();
                    if (index == 0 && source != null) {
                        index = source.get(0);
                        if (index == 0) {
                            FMusicCore.bridge.sendMessage(StatCollector.translateToLocal("fmusic.player.source_fail"));
                            return;
                        }
                    }
                }

                dequeue();
                resetSource();

                if (tasks.isEmpty()) {
                    continue;
                }

                nowTask = tasks.pop();
                if (nowTask == null || nowTask.url == null || nowTask.url.isEmpty()) {
                    continue;
                }
                tasks.clear();
                if (pendingTime > 0) {
                    // 应用 PLAY 前到达的 POS 缓存, 避免从头播放
                    nowTask.time = pendingTime;
                    FMusicLog.debug(
                        LOGGER,
                        StatCollector.translateToLocalFormatted("fmusic.log.player.seek_applied", pendingTime));
                    pendingTime = 0;
                }
                FMusicLog.debug(
                    LOGGER,
                    StatCollector.translateToLocalFormatted("fmusic.log.player.task_start", nowTask.url, nowTask.time));
                try {
                    local = 0;
                    connect();
                } catch (Exception e) {
                    FMusicLog.warn(
                        LOGGER,
                        StatCollector.translateToLocalFormatted("fmusic.log.player.fetch_fail", nowTask.url));
                    FMusicCore.bridge.sendMessage(StatCollector.translateToLocal("fmusic.player.fetch_fail"));
                    continue;
                }

                byte[] head = new byte[4];
                content.mark(4);
                content.read(head);
                content.reset();

                if (head[0] == 0 && head[1] == 0 && head[2] == 0 && head[3] == 0x1c) {
                    decoder = new M4ADecoder(this);
                    FMusicLog.debug(LOGGER, StatCollector.translateToLocal("fmusic.log.player.fmt_m4a"));
                } else if (head[0] == 'I' && head[1] == 'D' && head[2] == '3') {
                    decoder = new Mp3Decoder(this);
                    FMusicLog.debug(LOGGER, StatCollector.translateToLocal("fmusic.log.player.fmt_mp3_id3"));
                } else if (head[0] == (byte) 0xFF && (head[1] & 0xE0) == 0xE0) {
                    // MPEG 音频同步字 (0xFFEx/0xFFFx), 不限 128kbps
                    decoder = new Mp3Decoder(this);
                    FMusicLog.debug(
                        LOGGER,
                        StatCollector.translateToLocalFormatted(
                            "fmusic.log.player.fmt_mp3_sync",
                            Integer.toHexString(head[1] & 0xFF)));
                } else {
                    decoder = new OggDecoder(this);
                    FMusicLog.debug(LOGGER, StatCollector.translateToLocal("fmusic.log.player.fmt_ogg"));
                }

                if (!decoder.set()) {
                    streamClose();
                    FMusicCore.bridge.sendMessage(StatCollector.translateToLocal("fmusic.player.unsupported"));
                    continue;
                }

                isPlay = true;

                int frequency = decoder.getOutputFrequency();
                int channels = decoder.getOutputChannels();
                if (channels != 1 && channels != 2) continue;
                if (nowTask.time != 0) {
                    FMusicLog.debug(
                        LOGGER,
                        StatCollector.translateToLocalFormatted("fmusic.log.player.seek_exec", nowTask.time));
                    decoder.set(nowTask.time);
                }

                isClose = false;
                boolean eof = false;

                while (true) {
                    if (!isRun) {
                        return;
                    }
                    if (isClose) {
                        break;
                    }
                    if (!AL10.alIsSource(index)) {
                        FMusicLog.warn(LOGGER, StatCollector.translateToLocal("fmusic.log.player.source_lost"));
                        setReload();
                        break;
                    }
                    try {
                        while (!eof && AL10.alGetSourcei(index, AL10.AL_BUFFERS_QUEUED) < FMusicCore.config.queueSize) {
                            if (!isRun) {
                                return;
                            }
                            if (isClose) {
                                break;
                            }
                            BuffPack output = decoder.decodeFrame();
                            if (output == null) {
                                eof = true;
                                break;
                            }
                            decodeErrorCount = 0;
                            ByteBuffer byteBuffer = BufferUtils.createByteBuffer(output.len)
                                .put(output.buff, 0, output.len);
                            ((Buffer) byteBuffer).flip();

                            IntBuffer intBuffer = BufferUtils.createIntBuffer(1);
                            AL10.alGenBuffers(intBuffer);
                            int buffer = intBuffer.get(0);

                            if (buffer == 0) continue;

                            AL10.alBufferData(
                                buffer,
                                channels == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16,
                                byteBuffer,
                                frequency);

                            AL10.alSourceQueueBuffers(index, buffer);

                            if (AL10.alGetSourcei(index, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING && !frozen) {
                                AL10.alSourcePlay(index);
                            }
                        }

                        Thread.sleep(5);

                        checkChat();
                        checkVolume();
                        dequeue();

                        if (eof && !frozen && AL10.alGetSourcei(index, AL10.AL_SOURCE_STATE) == AL10.AL_STOPPED) {
                            // 解码完毕且缓冲队列真正播完(AL_STOPPED), 自然结束;
                            // 注意不能用 != AL_PLAYING: 暂停(PAUSED)状态下不能视为结束
                            FMusicLog.debug(
                                LOGGER,
                                StatCollector.translateToLocalFormatted("fmusic.log.player.play_end", nowTask.time));
                            break;
                        }
                    } catch (Exception e) {
                        if (!isClose) {
                            decodeErrorCount++;
                            FMusicLog.warn(
                                LOGGER,
                                StatCollector.translateToLocalFormatted(
                                    "fmusic.log.player.decode_err",
                                    decodeErrorCount,
                                    e.toString()));
                            if (decodeErrorCount >= 3) {
                                FMusicLog
                                    .error(LOGGER, StatCollector.translateToLocal("fmusic.log.player.decode_fail"), e);
                                break;
                            }
                            // 单帧解码失败, 尝试继续下一帧
                            Thread.sleep(50);
                        } else {
                            break;
                        }
                    }
                }

                streamClose();
                decodeClose();

                if (AL10.alIsSource(index)) {
                    while (!isClose && AL10.alGetSourcei(index, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING) {
                        Thread.sleep(50);
                        checkChat();
                        checkVolume();
                        dequeue();
                    }

                    dequeue();
                    resetSource();
                }

                if (reload) {
                    FMusicLog.warn(
                        LOGGER,
                        StatCollector
                            .translateToLocalFormatted("fmusic.log.player.reload_replay", nowTask.time, local));
                    wait = true;
                    if (semaphoreReload.tryAcquire(1, TimeUnit.SECONDS)) {
                        if (reload) {
                            reload = false;
                            index = -1;
                            tasks.push(nowTask);
                            semaphore.release();
                            continue;
                        }
                    }
                }

                isPlay = false;
                frozen = false;
                decodeErrorCount = 0;
                FMusicLog.debug(
                    LOGGER,
                    StatCollector.translateToLocalFormatted("fmusic.log.player.task_end", reload, isClose));

                if (!isRun) {
                    return;
                }
            } catch (Exception e) {
                FMusicLog.error(
                    LOGGER,
                    StatCollector.translateToLocalFormatted("fmusic.log.player.task_exception", e.toString()),
                    e);
                e.printStackTrace();
            }
        }
    }

    public void tick() {
        if (wait) {
            wait = false;
            semaphoreReload.release();
        }
        if (Config.pauseAtFreeze) {
            boolean paused = Minecraft.getMinecraft()
                .isGamePaused();
            // 同步冻结服务端计时与歌词 (与音乐同生共死)
            FMusic.frozen = paused;
            if (paused && !frozen && isPlay) {
                frozen = true;
                if (AL10.alIsSource(index)) {
                    AL10.alSourcePause(index);
                }
            } else if (!paused && frozen) {
                frozen = false;
                // 恢复游戏: 立即恢复播放
                if (isPlay && AL10.alIsSource(index)
                    && AL10.alGetSourcei(index, AL10.AL_SOURCE_STATE) != AL10.AL_PLAYING) {
                    AL10.alSourcePlay(index);
                }
            }
        } else if (frozen) {
            frozen = false;
            FMusic.frozen = false;
        }
    }

    public void closePlayer() {
        isClose = true;
        nowTask = null;
        tasks.clear();
        pendingTime = 0;
    }

    public void setMusic(String url) {
        if (nowTask != null && nowTask.url.equalsIgnoreCase(url)) {
            return;
        }
        for (PlayTaskObj item : tasks) {
            if (item.url.equalsIgnoreCase(url)) {
                return;
            }
        }

        closePlayer();
        PlayTaskObj taskObj = new PlayTaskObj();
        taskObj.time = 0;
        taskObj.url = url;
        tasks.push(taskObj);
        semaphore.release();
    }

    private void streamClose() throws IOException {
        if (response != null) {
            response.close(CloseMode.IMMEDIATE);
            response = null;
        }
        if (content != null) {
            content.close();
            content = null;
        }
    }

    private void decodeClose() throws Exception {
        if (decoder != null) {
            decoder.close();
            decoder = null;
        }
    }

    public void setReload() {
        if (isPlay) {
            reload = true;
            isClose = true;
        }
    }

    @Override
    public int read() throws IOException {
        int temp = content.read();
        if (temp >= 0) {
            local++;
        }
        return temp;
    }

    @Override
    public int read(byte[] buf) throws IOException {
        return read(buf, 0, buf.length);
    }

    @Override
    public long skip(long n) throws IOException {
        if (n <= 2048) {
            long temp = content.skip(n);
            local += temp;
            return temp;
        } else {
            local += n;
            connect();
        }
        return n;
    }

    @Override
    public synchronized int read(byte[] buf, int off, int len) throws IOException {
        try {
            int temp = content.read(buf, off, len);
            if (temp > 0) {
                local += temp;
                reconnectCount = 0;
                return temp;
            }
            if (temp == 0) {
                return 0;
            }
            // temp == -1: 流被服务器提前关闭?
            // 若已知文件总长且未读满, 视为连接被截断, 断点续传重连
            if (totalLength > 0 && local < totalLength && reconnectCount < 5) {
                reconnectCount++;
                connect();
                return read(buf, off, len);
            }
            return -1;
        } catch (IOException e) {
            // 连接异常, 断点续传重连 (限制次数, 防止无限递归)
            if (reconnectCount < 5) {
                reconnectCount++;
                connect();
                return read(buf, off, len);
            }
            throw e;
        }
    }

    @Override
    public synchronized int available() throws IOException {
        return content.available();
    }

    @Override
    public void close() throws IOException {
        streamClose();
    }

    public void setLocal(long local) throws IOException {
        streamClose();
        this.local = local;
        connect();
    }
}
