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
package com.salesforce.androidsdk.util

import com.salesforce.androidsdk.analytics.logger.SalesforceLogger
import com.salesforce.androidsdk.app.SalesforceSDKManager

/**
 * A simple logger util class for the SalesforceSDK library. This class simply acts
 * as a wrapper around SalesforceLogger specific to the SalesforceSDK library.
 *
 * @author bhariharan
 */
object SalesforceSDKLogger {

    private const val COMPONENT_NAME = "SalesforceSDK"

    /**
     * Logs an error log line.
     *
     * @param tag Log tag.
     * @param message Log message.
     */
    @JvmStatic
    fun e(tag: String, message: String) {
        getLogger().e(tag, message)
    }

    /**
     * Logs an error log line.
     *
     * @param tag Log tag.
     * @param message Log message.
     * @param e Exception to be logged.
     */
    @JvmStatic
    fun e(tag: String, message: String, e: Throwable?) {
        if (e != null) {
            getLogger().e(tag, message, e)
        } else {
            getLogger().e(tag, message)
        }
    }

    /**
     * Logs a warning log line.
     *
     * @param tag Log tag.
     * @param message Log message.
     */
    @JvmStatic
    fun w(tag: String, message: String) {
        getLogger().w(tag, message)
    }

    /**
     * Logs a warning log line.
     *
     * @param tag Log tag.
     * @param message Log message.
     * @param e Exception to be logged.
     */
    @JvmStatic
    fun w(tag: String, message: String, e: Throwable?) {
        if (e != null) {
            getLogger().w(tag, message, e)
        } else {
            getLogger().w(tag, message)
        }
    }

    /**
     * Logs an info log line.
     *
     * @param tag Log tag.
     * @param message Log message.
     */
    @JvmStatic
    fun i(tag: String, message: String) {
        getLogger().i(tag, message)
    }

    /**
     * Logs an info log line.
     *
     * @param tag Log tag.
     * @param message Log message.
     * @param e Exception to be logged.
     */
    @JvmStatic
    fun i(tag: String, message: String, e: Throwable?) {
        if (e != null) {
            getLogger().i(tag, message, e)
        } else {
            getLogger().i(tag, message)
        }
    }

    /**
     * Logs a debug log line.
     *
     * @param tag Log tag.
     * @param message Log message.
     */
    @JvmStatic
    fun d(tag: String, message: String) {
        getLogger().d(tag, message)
    }

    /**
     * Logs a debug log line.
     *
     * @param tag Log tag.
     * @param message Log message.
     * @param e Exception to be logged.
     */
    @JvmStatic
    fun d(tag: String, message: String, e: Throwable?) {
        if (e != null) {
            getLogger().d(tag, message, e)
        } else {
            getLogger().d(tag, message)
        }
    }

    /**
     * Logs a verbose log line.
     *
     * @param tag Log tag.
     * @param message Log message.
     */
    @JvmStatic
    fun v(tag: String, message: String) {
        getLogger().v(tag, message)
    }

    /**
     * Logs a verbose log line.
     *
     * @param tag Log tag.
     * @param message Log message.
     * @param e Exception to be logged.
     */
    @JvmStatic
    fun v(tag: String, message: String, e: Throwable?) {
        if (e != null) {
            getLogger().v(tag, message, e)
        } else {
            getLogger().v(tag, message)
        }
    }

    /**
     * Sets the log level to be used.
     *
     * @param level Log level.
     */
    @JvmStatic
    fun setLogLevel(level: SalesforceLogger.Level) {
        getLogger().logLevel = level
    }

    private fun getLogger(): SalesforceLogger {
        return SalesforceLogger.getLogger(
            COMPONENT_NAME,
            SalesforceSDKManager.getInstance().appContext
        )
    }
}
