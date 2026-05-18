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
import android.text.TextUtils
import android.util.Log
import com.squareup.tape.QueueFile
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * A simple file logger that works with SalesforceLogger to write log entries to a file.
 *
 * @author bhariharan
 */
class FileLogger @Throws(IOException::class) constructor(
    private val context: Context,
    private val componentName: String
) {

    private val file: QueueFile
    private var maxSize: Int = 0

    init {
        readFileLoggerPrefs()
        val filename = File(context.filesDir, componentName + LOG_SUFFIX)
        file = QueueFile(filename)
    }

    /**
     * Flushes the log file and resets it to its original state.
     */
    fun flushLog() {
        try {
            file.clear()
        } catch (e: IOException) {
            Log.e(TAG, "Failed to flush log file", e)
        }
    }

    /**
     * Returns the number of log lines stored in this file.
     *
     * @return Number of stored log lines.
     */
    fun getSize(): Int = file.size()

    /**
     * Returns the maximum number of log lines that can be stored in this file.
     *
     * @return Maximum number of log lines.
     */
    fun getMaxSize(): Int = maxSize

    /**
     * Sets the maximum number of log lines that can be stored in this file.
     *
     * @param size Maximum number of log lines.
     */
    fun setMaxSize(size: Int) {
        val actualSize = if (size < 0) 0 else size
        storeFileLoggerPrefs(actualSize)
    }

    /**
     * Writes a log line to the file.
     *
     * @param logLine Log line.
     */
    fun addLogLine(logLine: String?) {
        if (TextUtils.isEmpty(logLine)) {
            return
        }
        try {
            while (getSize() >= maxSize) {
                file.remove()
            }
            if (maxSize > 0) {
                file.add(logLine!!.toByteArray(StandardCharsets.UTF_8))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write log line", e)
        }
    }

    /**
     * Writes a list of log lines to the file.
     *
     * @param logLines Log lines.
     */
    fun addLogLines(logLines: List<String>?) {
        if (logLines == null || logLines.isEmpty()) {
            return
        }
        for (logLine in logLines) {
            addLogLine(logLine)
        }
    }

    /**
     * Writes an array of log lines to the file.
     *
     * @param logLines Log lines.
     */
    fun addLogLines(logLines: Array<String>?) {
        if (logLines == null || logLines.isEmpty()) {
            return
        }
        for (logLine in logLines) {
            addLogLine(logLine)
        }
    }

    /**
     * Returns a single log line in FIFO.
     *
     * @return Log line.
     */
    fun readLogLine(): String? {
        var logLine: String? = null
        try {
            val logLineBytes = file.peek()
            if (logLineBytes != null && logLineBytes.isNotEmpty()) {
                logLine = String(logLineBytes, StandardCharsets.US_ASCII)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to read log line", e)
        }
        return logLine
    }

    /**
     * Returns a list of log lines in FIFO and removes them as they are being read.
     *
     * @param numLines Number of lines to be read.
     * @return List of log lines.
     */
    fun readAndRemoveLogLinesAsList(numLines: Int): List<String>? {
        val logLines = mutableListOf<String>()
        val linesToRead = minOf(getSize(), numLines)
        for (i in 0 until linesToRead) {
            val logLine = readLogLine()
            removeLogLine()
            if (logLine != null) {
                logLines.add(logLine)
            }
        }
        return if (logLines.isEmpty()) null else logLines
    }

    /**
     * Returns an array of log lines in FIFO and removes them as they are being read.
     *
     * @param numLines Number of lines to be read.
     * @return Array of log lines.
     */
    fun readAndRemoveLogLinesAsArray(numLines: Int): Array<String>? {
        val logLines = readAndRemoveLogLinesAsList(numLines)
        return if (logLines != null && logLines.isNotEmpty()) {
            logLines.toTypedArray()
        } else {
            null
        }
    }

    /**
     * Returns all log lines stored in FIFO and removes them as they are being read.
     *
     * @return List of all log lines stored in this file.
     */
    fun readAndRemoveFileAsList(): List<String>? = readAndRemoveLogLinesAsList(getSize())

    /**
     * Returns all log lines stored in FIFO and removes them as they are being read.
     *
     * @return Array of all log lines stored in this file.
     */
    fun readAndRemoveFileAsArray(): Array<String>? = readAndRemoveLogLinesAsArray(getSize())

    /**
     * Removes the first log line in the file.
     */
    fun removeLogLine() {
        try {
            file.remove()
        } catch (e: IOException) {
            Log.e(TAG, "Failed to remove log line", e)
        }
    }

    /**
     * Removes the specified number of log lines from the file in FIFO.
     *
     * @param numLines Number of log lines.
     */
    fun removeLogLines(numLines: Int) {
        val linesToRemove = minOf(getSize(), numLines)
        for (i in 0 until linesToRemove) {
            removeLogLine()
        }
    }

    @Synchronized
    private fun storeFileLoggerPrefs(maxSize: Int) {
        val sp = context.getSharedPreferences(FILE_LOGGER_PREFS, Context.MODE_PRIVATE)
        val editor = sp.edit()
        editor.putInt(componentName, maxSize)
        editor.commit()
        this.maxSize = maxSize
    }

    private fun readFileLoggerPrefs() {
        val sp = context.getSharedPreferences(FILE_LOGGER_PREFS, Context.MODE_PRIVATE)
        if (!sp.contains(componentName)) {
            storeFileLoggerPrefs(MAX_SIZE)
        }
        maxSize = sp.getInt(componentName, MAX_SIZE)
    }

    companion object {

        private const val LOG_SUFFIX = "_log"
        private const val FILE_LOGGER_PREFS = "sf_file_logger_prefs"
        private const val TAG = "FileLogger"
        private const val MAX_SIZE = 10000

        /**
         * Resets the stored file logger prefs. Should be used ONLY by tests.
         *
         * @param context Context.
         */
        @JvmStatic
        @Synchronized
        fun resetFileLoggerPrefs(context: Context) {
            val sp = context.getSharedPreferences(FILE_LOGGER_PREFS, Context.MODE_PRIVATE)
            val editor = sp.edit()
            editor.clear()
            editor.commit()
        }
    }
}
