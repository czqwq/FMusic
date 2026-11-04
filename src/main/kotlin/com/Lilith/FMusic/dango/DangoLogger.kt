package com.Lilith.FMusic.dango

import org.apache.logging.log4j.LogManager
import org.apache.logging.log4j.Logger

class DangoLogger {
    companion object {
        private val logger: Logger = LogManager.getLogger("DangoMusic")

        @JvmStatic
        fun info(msg: String, vararg params: Any) {
            logger.info(msg, *params)
        }

        @JvmStatic
        fun warn(msg: String, vararg params: Any) {
            logger.warn(msg, *params)
        }

        @JvmStatic
        fun error(msg: String, vararg params: Any) {
            logger.error(msg, *params)
        }
    }
}