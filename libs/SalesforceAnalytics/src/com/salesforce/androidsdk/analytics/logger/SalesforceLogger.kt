/*
 * Copyright (c) 2017-present, salesforce.com, inc.
 * All rights reserved.
 * Redistribution and use of this software in source and binary forms, with or
 * without modification, are permitted provided that the following conditions
 * are met:
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * - Neither the name of salesforce.com, inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission of salesforce.com, inc.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.androidsdk.analytics.logger

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager.NameNotFoundException
import android.util.Log
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * A simple logger that allows components to log statements of different log levels. This class
 * also provides the ability to break down logs at the component level and set different log
 * levels for different components. The available options for log output are console and file.
 *
 * @author bhariharan
 */
open class SalesforceLogger private constructor(
    private val componentName: String,
    private val context: Context,
    private val logReceiver: SalesforceLogReceiver?
) {

    /**
     * An enumeration of log levels.
     */
    enum class Level(val severity: Int) {
        OFF(6),
        ERROR(5),
        WARN(4),
        INFO(3),
        DEBUG(2),
        VERBOSE(1)
    }

    private var fileLogger: FileLogger? = null

    /**
     * Returns the log level currently being used.
     */
    var logLevel: Level = Level.DEBUG
        set(value) {
            field = value
            val sp = context.getSharedPreferences(SF_LOGGER_PREFS, Context.MODE_PRIVATE)
            sp.edit().putString(componentName, value.toString()).apply()
        }

    init {
        readLoggerPrefs()
        try {
            fileLogger = FileLogger(context, componentName)
        } catch (e: IOException) {
            Log.e(TAG, "Couldn't create file logger", e)
        }
    }

    private fun isDebugMode(): Boolean {
        var debugMode = true
        try {
            val pm = context.packageManager
            if (pm != null) {
                val pi = pm.getPackageInfo(context.packageName, 0)
                if (pi != null) {
                    val ai = pi.applicationInfo
                    if (ai != null && (ai.flags and ApplicationInfo.FLAG_DEBUGGABLE) == 0) {
                        debugMode = false
                    }
                }
            }
        } catch (e: NameNotFoundException) {
            /* Intentionally blank */
        }
        return debugMode
    }

    /**
     * Returns the instance of FileLogger associated with this component.
     *
     * @return FileLogger instance.
     */
    fun getFileLogger(): FileLogger? = fileLogger


    /**
     * Disables file logging.
     */
    @Synchronized
    fun disableFileLogging() {
        fileLogger?.setMaxSize(0)
    }

    /**
     * Enables file logging.
     *
     * @param maxSize Maximum number of log lines allowed to be stored at a time.
     */
    @Synchronized
    fun enableFileLogging(maxSize: Int) {
        fileLogger?.setMaxSize(maxSize)
    }

    /**
     * Returns if file logging is enabled or not.
     *
     * @return True - if enabled, False - otherwise.
     */
    fun isFileLoggingEnabled(): Boolean {
        val maxSize = fileLogger?.getMaxSize() ?: 0
        return maxSize > 0
    }

    /**
     * Logs an error log line.
     *
     * @param tag Log tag.
     * @param message Log message.
     */
    fun e(tag: String, message: String) {
        log(Level.ERROR, tag, message)
    }

    /**
     * Logs an error log line.
     *
     * @param tag Log tag.
     * @param message Log message.
     * @param e Exception to be logged.
     */
    fun e(tag: String, message: String, e: Throwable) {
        log(Level.ERROR, tag, message, e)
    }

    /**
     * Logs a warning log line.
     *
     * @param tag Log tag.
     * @param message Log message.
     */
    fun w(tag: String, message: String) {
        log(Level.WARN, tag, message)
    }

    /**
     * Logs a warning log line.
     *
     * @param tag Log tag.
     * @param message Log message.
     * @param e Exception to be logged.
     */
    fun w(tag: String, message: String, e: Throwable) {
        log(Level.WARN, tag, message, e)
    }

    /**
     * Logs an info log line.
     *
     * @param tag Log tag.
     * @param message Log message.
     */
    fun i(tag: String, message: String) {
        log(Level.INFO, tag, message)
    }

    /**
     * Logs an info log line.
     *
     * @param tag Log tag.
     * @param message Log message.
     * @param e Exception to be logged.
     */
    fun i(tag: String, message: String, e: Throwable) {
        log(Level.INFO, tag, message, e)
    }

    /**
     * Logs a debug log line.
     *
     * @param tag Log tag.
     * @param message Log message.
     */
    fun d(tag: String, message: String) {
        log(Level.DEBUG, tag, message)
    }

    /**
     * Logs a debug log line.
     *
     * @param tag Log tag.
     * @param message Log message.
     * @param e Exception to be logged.
     */
    fun d(tag: String, message: String, e: Throwable) {
        log(Level.DEBUG, tag, message, e)
    }

    /**
     * Logs a verbose log line.
     *
     * @param tag Log tag.
     * @param message Log message.
     */
    fun v(tag: String, message: String) {
        log(Level.VERBOSE, tag, message)
    }

    /**
     * Logs a verbose log line.
     *
     * @param tag Log tag.
     * @param message Log message.
     * @param e Exception to be logged.
     */
    fun v(tag: String, message: String, e: Throwable) {
        log(Level.VERBOSE, tag, message, e)
    }

    /**
     * Logs a log line of the specified level.
     *
     * @param level Log level.
     * @param tag Log tag.
     * @param message Log message.
     */
    fun log(level: Level, tag: String, message: String) {
        if (level.severity >= logLevel.severity) {
            val redactedMessage = redact(message) ?: message
            when (level) {
                Level.OFF -> { /* no-op */ }
                Level.ERROR -> Log.e(tag, redactedMessage)
                Level.WARN -> Log.w(tag, redactedMessage)
                Level.INFO -> Log.i(tag, redactedMessage)
                Level.DEBUG -> Log.d(tag, redactedMessage)
                Level.VERBOSE -> Log.v(tag, redactedMessage)
            }
            logToFile(getTimeFromUTC(), level, tag, redactedMessage, null)
            logReceiver?.receive(level, tag, redactedMessage)
        }
    }

    /**
     * Logs a log line of the specified level.
     *
     * @param level Log level.
     * @param tag Log tag.
     * @param message Log message.
     * @param e Exception to be logged.
     */
    fun log(level: Level, tag: String, message: String, e: Throwable) {
        if (level.severity >= logLevel.severity) {
            val redactedMessage = redact(message) ?: message
            when (level) {
                Level.OFF -> { /* no-op */ }
                Level.ERROR -> Log.e(tag, redactedMessage, e)
                Level.WARN -> Log.w(tag, redactedMessage, e)
                Level.INFO -> Log.i(tag, redactedMessage, e)
                Level.DEBUG -> Log.d(tag, redactedMessage, e)
                Level.VERBOSE -> Log.v(tag, redactedMessage, e)
            }
            logToFile(getTimeFromUTC(), level, tag, redactedMessage, e)
            logReceiver?.receive(level, tag, redactedMessage, e)
        }
    }

    private fun logToFile(curTime: String, level: Level, tag: String, message: String, e: Throwable?) {
        THREAD_POOL.execute {
            fileLogger?.let { logger ->
                val logLine = if (e != null) {
                    String.format(LOG_LINE_FORMAT_WITH_EXCEPTION, curTime, level, tag, message, Log.getStackTraceString(e))
                } else {
                    String.format(LOG_LINE_FORMAT, curTime, level, tag, message)
                }
                logger.addLogLine(logLine)
            }
        }
    }

    private fun getTimeFromUTC(): String {
        val curTime = System.currentTimeMillis()
        val date = Date(curTime)
        val dateFormat = SimpleDateFormat(US_DATE_FORMAT, Locale.US)
        return dateFormat.format(date)
    }

    private fun readLoggerPrefs() {
        val sp = context.getSharedPreferences(SF_LOGGER_PREFS, Context.MODE_PRIVATE)
        var level = Level.DEBUG
        if (!isDebugMode()) {
            level = Level.ERROR
        }
        if (!sp.contains(componentName)) {
            logLevel = level
            return
        }
        val logLevelString = sp.getString(componentName, level.toString())!!
        logLevel = Level.valueOf(logLevelString)
    }

    companion object {

        private const val TAG = "SalesforceLogger"
        private const val LOG_LINE_FORMAT = "TIME: %s, LEVEL: %s, TAG: %s, MESSAGE: %s"
        private const val LOG_LINE_FORMAT_WITH_EXCEPTION = "TIME: %s, LEVEL: %s, TAG: %s, MESSAGE: %s, EXCEPTION: %s"
        private const val US_DATE_FORMAT = "MM-dd HH:mm:ss.SSS"
        private const val SF_LOGGER_PREFS = "sf_logger_prefs"
        private val THREAD_POOL: ExecutorService = Executors.newFixedThreadPool(1)
        private var LOGGERS: MutableMap<String, SalesforceLogger>? = null

        /**
         * The factory for Salesforce log receivers. Defaults to null.
         */
        private var logReceiverFactory: SalesforceLogReceiverFactory? = null

        /**
         * Sets the factory for Salesforce log receivers.
         *
         * @param logReceiverFactory The factory for Salesforce log receivers
         */
        @JvmStatic
        @Synchronized
        fun setLogReceiverFactory(logReceiverFactory: SalesforceLogReceiverFactory?) {
            Companion.logReceiverFactory = logReceiverFactory
        }

        /**
         * Returns the Salesforce logger instance associated with the named component. The logger will
         * be created if needed and without any additional Salesforce log receiver.
         *
         * @param componentName The component name
         * @param context The Android context
         * @return Either a new or existing Salesforce logger for the named component
         */
        @JvmStatic
        @Synchronized
        fun getLogger(componentName: String, context: Context): SalesforceLogger {
            return getLogger(componentName, context, null)
        }

        /**
         * Returns the Salesforce logger instance associated with the named component. The logger will
         * be created if needed and with the specified Salesforce log receiver.
         *
         * @param componentName The component name
         * @param context The Android context
         * @param salesforceLogReceiver The Salesforce log receiver to receive all logs issued by the
         *                              Salesforce logger
         * @return Either a new or existing Salesforce logger for the named component
         */
        @JvmStatic
        @Synchronized
        fun getLogger(
            componentName: String,
            context: Context,
            salesforceLogReceiver: SalesforceLogReceiver?
        ): SalesforceLogger {
            if (LOGGERS == null) {
                LOGGERS = ConcurrentHashMap()
            }

            // Resolve the log receiver from parameters or the factory.
            var salesforceLogReceiverResolved = salesforceLogReceiver
            if (salesforceLogReceiverResolved == null && logReceiverFactory != null) {
                salesforceLogReceiverResolved = logReceiverFactory!!.create(componentName)
            }

            if (!LOGGERS!!.containsKey(componentName)) {
                val logger = SalesforceLogger(
                    componentName,
                    context,
                    salesforceLogReceiverResolved
                )
                LOGGERS!![componentName] = logger
            }
            return LOGGERS!![componentName]!!
        }

        /**
         * Returns the set of components that have loggers associated with them.
         *
         * @return Set of components being logged.
         */
        @JvmStatic
        @Synchronized
        fun getComponents(): Set<String>? {
            if (LOGGERS == null || LOGGERS!!.isEmpty()) {
                return null
            }
            val components = LOGGERS!!.keys
            return if (components.isEmpty()) null else components
        }

        /**
         * Wipes all components currently being logged. Used only by tests.
         */
        @JvmStatic
        @Synchronized
        fun flushComponents() {
            LOGGERS = null
        }

        /**
         * Redacts sensitive tokens and credentials from a log message.
         * Delegates to the Kotlin extension `String.redactSensitiveData()`.
         *
         * @param message The original log message.
         * @return The message with sensitive values masked, showing only the last 4 characters.
         */
        @JvmStatic
        fun redact(message: String?): String? {
            if (message == null) {
                return null
            }
            return message.redactSensitiveData()
        }

        /**
         * Resets the stored logger prefs. Should be used ONLY by tests.
         *
         * @param context Context.
         */
        @JvmStatic
        @Synchronized
        fun resetLoggerPrefs(context: Context) {
            val sp = context.getSharedPreferences(SF_LOGGER_PREFS, Context.MODE_PRIVATE)
            val editor = sp.edit()
            editor.clear()
            editor.apply()
        }
    }
}
