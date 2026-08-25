package com.Lilith.FMusic.server.core.saves;

import java.util.Queue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.Semaphore;

import net.minecraft.util.StatCollector;

import com.Lilith.FMusic.server.core.FMusic;

public class SaveTask {

    private static final Queue<Runnable> tasks = new LinkedBlockingDeque<>();
    private static final Semaphore semaphore = new Semaphore(0);

    public static void start() {
        new Thread(SaveTask::run).start();
    }

    public static void task(Runnable runnable) {
        tasks.add(runnable);
        semaphore.release();
    }

    /**
     * 停止
     */
    public static void stop() {
        semaphore.release();
    }

    private static void run() {
        FMusic.log.data(StatCollector.translateToLocal("fmusic.log.core.db_thread_start"));
        Runnable runnable;
        while (FMusic.isRun) {
            try {
                semaphore.acquire();
                if (!FMusic.isRun) break;
                do {
                    runnable = tasks.poll();
                    if (runnable != null) {
                        runnable.run();
                    }
                } while (runnable != null);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        FMusic.log.data(StatCollector.translateToLocal("fmusic.log.core.db_thread_stop"));
    }
}
