/*
 * Copyright (c) 2011-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.phonegap.plugin

import com.salesforce.androidsdk.phonegap.util.SalesforceHybridLogger
import org.apache.cordova.CallbackContext
import org.json.JSONArray
import org.json.JSONException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue

/**
 * PhoneGap plugin to run javascript tests.
 */
class TestRunnerPlugin : ForcePlugin() {

    /**
     * Supported plugin actions that the client can take.
     */
    private enum class Action {
        onReadyForTests,
        onTestComplete
    }

    @Throws(JSONException::class)
    override fun execute(
        actionStr: String,
        jsVersion: JavaScriptPluginVersion,
        args: JSONArray,
        callbackContext: CallbackContext
    ): Boolean {
        // Figure out action
        return try {
            val action = Action.valueOf(actionStr)
            when (action) {
                Action.onReadyForTests -> {
                    onReadyForTests(args, callbackContext)
                    true
                }
                Action.onTestComplete -> {
                    onTestComplete(args, callbackContext)
                    true
                }
            }
        } catch (e: IllegalArgumentException) {
            false
        } catch (e: InterruptedException) {
            callbackContext.error(e.message)
            true
        }
    }

    /**
     * Native implementation of onTestComplete
     * @param args
     * @param callbackContext
     * @throws JSONException
     * @throws InterruptedException
     */
    @Throws(JSONException::class, InterruptedException::class)
    private fun onTestComplete(args: JSONArray, callbackContext: CallbackContext) {
        // Parse args
        val arg0 = args.getJSONObject(0)
        val testName = arg0.getString(TEST_NAME)
        val success = arg0.getBoolean(SUCCESS)
        val message = stripHtml(arg0.getString(MESSAGE))
        val durationMsec = arg0.getInt(DURATION)
        val duration = durationMsec / 1000.0
        val testResult = TestResult(testName, success, message, duration)
        testResults.put(testResult)
        SalesforceHybridLogger.w(TAG, "${testResult.testName} completed in ${testResult.duration}")
        callbackContext.success()
    }

    private fun stripHtml(message: String): String {
        return message.replace(Regex("<[^>]+>"), "|").replace(Regex("[|]+"), " ")
    }

    /**
     * Native implementation of onReadyForTests
     * @param args
     * @param callbackContext
     */
    private fun onReadyForTests(args: JSONArray, callbackContext: CallbackContext) {
        readyForTests.add(java.lang.Boolean.TRUE)
        callbackContext.success()
    }

    class TestResult(
        @JvmField val testName: String,
        @JvmField val success: Boolean,
        @JvmField val message: String,
        @JvmField val duration: Double // time in seconds
    )

    companion object {
        private const val TAG = "TestRunnerPlugin"

        // Keys in json from/to javascript
        private const val TEST_NAME = "testName"
        private const val SUCCESS = "success"
        private const val MESSAGE = "message"
        private const val DURATION = "testDuration"

        // To synchronize with the tests
        @JvmField
        val readyForTests: BlockingQueue<Boolean> = ArrayBlockingQueue(1)

        @JvmField
        val testResults: BlockingQueue<TestResult> = ArrayBlockingQueue(1)
    }
}
