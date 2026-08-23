package com.Lilith.FMusic.client.core;

import org.apache.logging.log4j.Logger;

/**
 * FMusic 客户端诊断日志开关 (由 config/FMusic.cfg 的 debug 键控制, 默认关闭)。
 * 开启后以 debug 级别输出播放/seek/连接等诊断日志, 便于定位问题。
 * 各模块使用各自的 Logger (FMusic Player / FMusic Core / FMusic MP4 / FMusic Client)。
 */
public class FMusicLog {

    /** 是否输出诊断日志 (Config.debug 同步) */
    public static volatile boolean enabled = false;

    public static void debug(Logger logger, String msg) {
        if (enabled) {
            logger.debug(msg);
        }
    }

    public static void warn(Logger logger, String msg) {
        if (enabled) {
            logger.warn(msg);
        }
    }

    public static void error(Logger logger, String msg, Throwable t) {
        if (enabled) {
            logger.error(msg, t);
        }
    }
}
