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

/**
 * OpenAL 流式音乐播放器。
 * <p>
 * 线程模型: 连接/解码/入队/清理等播放逻辑只在 fmusic_run 线程执行; 网络包线程
 * (FMLNetworkEvent 处理 PLAY/STOP/POS)、客户端 tick 线程和定时器线程通过本类的公开方法
 * 提交命令。共享状态 (nowTask/tasks/isClose/reload/pendingTime) 统一由 {@link #lock} 保护,
 * 播放线程只持有短临界区, 不会在持锁时做网络连接、解码等耗时操作。
 * <p>
 * 播放线程必须通过局部变量 {@code task} 访问当前任务, 不能直接解引用 {@link #nowTask}:
 * STOP/新 PLAY/退出服务器会在任意时刻把 nowTask 置 null, 直接解引用会抛 NullPointerException
 * (历史 bug: 慢速 connect() 期间收到 STOP, run 线程在任务初始化尾部读取 nowTask.time 崩溃)。
 */
public class FMusicPlayer extends InputStream {

    private static final Logger LOGGER = LogManager.getLogger("FMusic Player");

    /** 外部命令线程与 fmusic_run 线程的共享状态锁 */
    private final Object lock = new Object();
    private final Stack<PlayTaskObj> tasks = new Stack<>();
    private final Semaphore semaphore = new Semaphore(0);
    private final Semaphore semaphoreReload = new Semaphore(0);

    /** 当前播放任务 (仅 fmusic_run 线程写入/置空, 外部请求停止时为 null); 播放线程只读局部快照 */
    private volatile PlayTaskObj nowTask;
    /** 当前任务的 URL, 仅 fmusic_run 线程访问 (断点续传重连用) */
    private String taskUrl;
    private CloseableHttpResponse response;
    private BufferedInputStream content;
    private volatile boolean isClose = false;
    private volatile boolean reload = false;
    private IDecoder decoder;
    private volatile boolean isPlay = false;
    private volatile boolean wait = false;
    private volatile int index = -1;
    private final IntBuffer source;
    private long local;
    private long contentLength = -1;
    /** 文件总长度 (首次连接记录, 用于断流判断; contentLength 是本次响应的剩余长度, 坐标系不同) */
    private long totalLength = -1;
    private int reconnectCount = 0;
    private int decodeErrorCount = 0;
    private volatile boolean isRun;
    private volatile boolean isChat;
    private int chatCount = 0;
    /**
     * 游戏暂停(单人按Esc, pause_at_freeze=true)时音乐是否被冻结
     */
    private volatile boolean frozen;
    /**
     * POS 先于 PLAY 到达时缓存的跳转时间 (ms), 任务开始时应用; 由 lock 保护
     */
    private int pendingTime = 0;

    public FMusicPlayer(IntBuffer source) {
        this.source = source;
        new Thread(this::run, "fmusic_run").start();
        FMusicCore.service.scheduleAtFixedRate(this::timerTick, 0, 10, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        isRun = false;
        semaphore.release();
        semaphoreReload.release();
        synchronized (lock) {
            isClose = true;
            nowTask = null;
            tasks.clear();
        }
    }

    public void setChat() {
        isChat = true;
    }

    public void timerTick() {
        synchronized (lock) {
            if (isPlay && nowTask != null) {
                nowTask.time += 10;
            }
        }
    }

    public boolean isPlay() {
        return isPlay;
    }

    public void setTime(int time) {
        synchronized (lock) {
            PlayTaskObj task = nowTask;
            if (task == null) {
                // 播放任务尚未开始 (PLAY/POS 同批到达时 POS 先于任务弹出), 缓存待任务开始时应用
                FMusicLog.debug(LOGGER, StatCollector.translateToLocal("fmusic.log.player.seek_cached"));
                pendingTime = time;
                return;
            }
            FMusicLog.debug(LOGGER, StatCollector.translateToLocalFormatted("fmusic.log.player.seek_request", time));
            task.time = time;
            if (!isPlay) {
                // 任务仍在启动阶段 (连接/解码器初始化), run 线程会在解码器就绪后应用新的 time, 无需重启
                return;
            }
            isClose = true;
            tasks.clear();
            tasks.push(task);
            semaphore.release();
        }
    }

    public void connect() throws IOException {
        streamClose();
        if (taskUrl == null) {
            throw new IOException("No active play task url");
        }
        HttpGet request = new HttpGet(taskUrl);
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

    /**
     * 从任务队列取出下一个播放任务 (fmusic_run 线程调用)。无任务时返回 null。
     * 只在持锁的短临界区内修改共享状态, 后续耗时操作全部使用返回的局部 task。
     */
    private PlayTaskObj takeTask() {
        synchronized (lock) {
            if (tasks.isEmpty()) {
                return null;
            }
            PlayTaskObj task = tasks.pop();
            tasks.clear();
            if (task == null || task.url == null || task.url.isEmpty()) {
                nowTask = null;
                return null;
            }
            if (pendingTime > 0) {
                // 应用 PLAY 前到达的 POS 缓存, 避免从头播放
                task.time = pendingTime;
                FMusicLog.debug(
                    LOGGER,
                    StatCollector.translateToLocalFormatted("fmusic.log.player.seek_applied", pendingTime));
                pendingTime = 0;
            }
            nowTask = task;
            taskUrl = task.url;
            isClose = false;
            reload = false;
            isPlay = false;
            return task;
        }
    }

    /**
     * 任务是否已被 STOP / 新 PLAY / 退出服务器取消 (nowTask 不再是它)
     */
    private boolean isCancelled(PlayTaskObj task) {
        synchronized (lock) {
            return nowTask != task;
        }
    }

    /**
     * 结束当前任务: 复位播放标志并清空共享的当前任务引用 (只在 nowTask 仍是它时清空)
     */
    private void finishTask(PlayTaskObj task) {
        frozen = false;
        decodeErrorCount = 0;
        taskUrl = null;
        synchronized (lock) {
            isPlay = false;
            if (nowTask == task) {
                nowTask = null;
            }
        }
    }

    /**
     * 未预期异常兜底: 释放流/解码器并复位状态, 避免播放线程带着旧连接/解码器回到等待
     */
    private void cleanupAfterFailure() {
        try {
            streamClose();
        } catch (Exception ignored) {
            // 兜底清理, 忽略二次异常
        }
        try {
            decodeClose();
        } catch (Exception ignored) {
            // 兜底清理, 忽略二次异常
        }
        frozen = false;
        decodeErrorCount = 0;
        taskUrl = null;
        synchronized (lock) {
            isPlay = false;
            nowTask = null;
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

                PlayTaskObj task = takeTask();
                if (task == null) {
                    continue;
                }

                FMusicLog.debug(
                    LOGGER,
                    StatCollector.translateToLocalFormatted("fmusic.log.player.task_start", task.url, task.time));
                try {
                    local = 0;
                    connect();
                } catch (Exception e) {
                    FMusicLog.warn(
                        LOGGER,
                        StatCollector.translateToLocalFormatted("fmusic.log.player.fetch_fail", task.url));
                    FMusicCore.bridge.sendMessage(StatCollector.translateToLocal("fmusic.player.fetch_fail"));
                    finishTask(task);
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
                    decodeClose();
                    FMusicCore.bridge.sendMessage(StatCollector.translateToLocal("fmusic.player.unsupported"));
                    finishTask(task);
                    continue;
                }

                // connect()/decoder.set() 可能耗时数秒 (网络慢), 期间外部可能已发来 STOP 或新的 PLAY,
                // 此时 nowTask 已不是当前 task: 必须放弃本任务, 否则会继续播放/重复初始化旧歌
                if (isCancelled(task)) {
                    FMusicLog.debug(LOGGER, StatCollector.translateToLocal("fmusic.log.player.task_cancelled"));
                    streamClose();
                    decodeClose();
                    finishTask(task);
                    continue;
                }

                int frequency = decoder.getOutputFrequency();
                int channels = decoder.getOutputChannels();
                if (channels != 1 && channels != 2) {
                    streamClose();
                    decodeClose();
                    finishTask(task);
                    continue;
                }

                int seek;
                synchronized (lock) {
                    // 启动阶段到达的 POS 只更新了 task.time, 这里统一读取并应用
                    seek = task.time;
                    isPlay = true;
                }
                if (seek != 0) {
                    FMusicLog
                        .debug(LOGGER, StatCollector.translateToLocalFormatted("fmusic.log.player.seek_exec", seek));
                    decoder.set(seek);
                }

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
                                StatCollector.translateToLocalFormatted("fmusic.log.player.play_end", task.time));
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

                if (reload && !isCancelled(task)) {
                    FMusicLog.warn(
                        LOGGER,
                        StatCollector.translateToLocalFormatted("fmusic.log.player.reload_replay", task.time, local));
                    wait = true;
                    boolean acquired = semaphoreReload.tryAcquire(1, TimeUnit.SECONDS);
                    wait = false;
                    boolean replay = false;
                    synchronized (lock) {
                        // 等待期间可能已被 STOP/新 PLAY 取消, 取消后不能再重放旧任务
                        if (acquired && reload && nowTask == task) {
                            replay = true;
                        }
                        reload = false;
                    }
                    if (replay) {
                        index = -1;
                        synchronized (lock) {
                            tasks.push(task);
                        }
                        semaphore.release();
                        continue;
                    }
                }

                FMusicLog.debug(
                    LOGGER,
                    StatCollector.translateToLocalFormatted("fmusic.log.player.task_end", reload, isClose));
                finishTask(task);

                if (!isRun) {
                    return;
                }
            } catch (Exception e) {
                FMusicLog.error(
                    LOGGER,
                    StatCollector.translateToLocalFormatted("fmusic.log.player.task_exception", e.toString()),
                    e);
                e.printStackTrace();
                cleanupAfterFailure();
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

    /**
     * 停止当前播放 (STOP/新 PLAY/退出服务器); 只清共享状态, 实际资源释放由播放线程完成
     */
    public void closePlayer() {
        synchronized (lock) {
            closePlayerLocked();
        }
    }

    private void closePlayerLocked() {
        isClose = true;
        reload = false;
        nowTask = null;
        tasks.clear();
        pendingTime = 0;
    }

    public void setMusic(String url) {
        synchronized (lock) {
            if (nowTask != null && nowTask.url != null && nowTask.url.equalsIgnoreCase(url)) {
                return;
            }
            for (PlayTaskObj item : tasks) {
                if (item.url != null && item.url.equalsIgnoreCase(url)) {
                    return;
                }
            }

            closePlayerLocked();
            PlayTaskObj taskObj = new PlayTaskObj();
            taskObj.time = 0;
            taskObj.url = url;
            tasks.push(taskObj);
        }
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

    /**
     * 取当前音频流; 流已关闭时抛 IOException。
     * 异步解码线程 (fmusic_ogg) 可能在播放线程关闭流之后还在读, 不能让它拿到 null 后 NPE。
     */
    private BufferedInputStream requireContent() throws IOException {
        BufferedInputStream stream = content;
        if (stream == null) {
            throw new IOException("FMusic player stream is closed");
        }
        return stream;
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
        int temp = requireContent().read();
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
            long temp = requireContent().skip(n);
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
        // 流已关闭 (播放任务被停止/切换) 时直接抛 IOException, 不触发断点重连
        BufferedInputStream stream = requireContent();
        try {
            int temp = stream.read(buf, off, len);
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
        return requireContent().available();
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
