/*
 * Copyright (c) 2012-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.phonegap

import android.content.Intent
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.phonegap.plugin.TestRunnerPlugin
import com.salesforce.androidsdk.phonegap.plugin.TestRunnerPlugin.TestResult
import com.salesforce.androidsdk.phonegap.ui.SalesforceDroidGapActivity
import com.salesforce.androidsdk.phonegap.util.SalesforceHybridLogger
import com.salesforce.androidsdk.util.EventsObservable.EventType
import com.salesforce.androidsdk.util.test.EventsListenerQueue
import org.junit.Assert
import java.util.concurrent.TimeUnit

/**
 * Extend this class to run tests written in JavaScript.
 */
open class JSTestCase {

    companion object {
        private const val TAG = "JSTestCase"

        private var testResults: MutableMap<String, MutableMap<String, TestResult>>? = null

        @JvmStatic
        fun runJSTestSuite(jsSuite: String, testNames: Iterable<String>, timeout: Int) {
            if (testResults == null || !testResults!!.containsKey(jsSuite)) {
                if (testResults == null) {
                    testResults = HashMap()
                }
                if (!testResults!!.containsKey(jsSuite)) {
                    testResults!![jsSuite] = HashMap()
                }

                // Wait for app initialization to complete
                val eq = EventsListenerQueue()
                if (!SalesforceSDKManager.hasInstance()) {
                    eq.waitForEvent(EventType.AppCreateComplete, 5000)
                }

                // Start main activity
                val instrumentation = InstrumentationRegistry.getInstrumentation()
                val intent = Intent(Intent.ACTION_MAIN)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                intent.setClassName(
                    instrumentation.targetContext,
                    SalesforceSDKManager.getInstance().mainActivityClass.name
                )
                val activity = instrumentation.startActivitySync(intent) as SalesforceDroidGapActivity

                // Block until the javascript has notified the container that it's ready
                TestRunnerPlugin.readyForTests.take()

                // Now run all the tests and collect the results in testResults
                for (testName in testNames) {
                    val jsCmd = "javascript:" +
                            "navigator.testrunner.setTestSuite('$jsSuite');" +
                            "navigator.testrunner.startTest('$testName');"
                    val appView = activity.cordovaWebView
                    appView.view.post { appView.loadUrl(jsCmd) }
                    SalesforceHybridLogger.i(TAG, "Running test: $testName")

                    // Block until test completes or times out
                    val result: TestResult = try {
                        TestRunnerPlugin.testResults.poll(timeout.toLong(), TimeUnit.SECONDS)
                            ?: TestResult(testName, false, "Timeout ($timeout seconds) exceeded", timeout.toDouble())
                    } catch (e: Exception) {
                        TestResult(testName, false, "Test failed", timeout.toDouble())
                    }
                    SalesforceHybridLogger.i(TAG, "Finished running test: $testName")

                    // Save result
                    testResults!![jsSuite]?.put(testName, result)
                }

                // Cleanup
                eq.tearDown()
                activity.finish()
            }
        }
    }

    /**
     * Helper method: No longer actually run the javascript test; Instead,
     * asserts based on saved results.
     *
     * @param jsSuite  The test suite name
     * @param testName The name of the test method in the test suite
     */
    protected open fun runTest(jsSuite: String, testName: String) {
        val testResult = testResults?.get(jsSuite)
        if (testResult != null) {
            val result = testResult[testName]
            Assert.assertNotNull("No test result", result)
            Assert.assertTrue(result!!.testName + " " + result.message, result.success)
        }
    }
}
