package com.anksoft.kmpdemo.server.support

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxyUtil
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory

/** Collects everything logged while open: messages, MDC values and throwable text. */
class LogCapture : AutoCloseable {
    private val root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger
    private val appender = ListAppender<ILoggingEvent>().apply { start() }

    init {
        root.addAppender(appender)
    }

    fun text(): String = appender.list.toList().joinToString("\n") { event ->
        buildString {
            append(event.formattedMessage)
            append(' ').append(event.mdcPropertyMap)
            event.throwableProxy?.let { append('\n').append(ThrowableProxyUtil.asString(it)) }
        }
    }

    override fun close() {
        root.detachAppender(appender)
    }
}
