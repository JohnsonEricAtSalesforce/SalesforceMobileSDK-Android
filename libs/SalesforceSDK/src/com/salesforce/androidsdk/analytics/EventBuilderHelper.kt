/*
 * Copyright (c) 2016-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.analytics

import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.androidsdk.analytics.model.InstrumentationEvent
import com.salesforce.androidsdk.analytics.model.InstrumentationEventBuilder
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.util.SalesforceSDKLogger
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * A simple helper class to facilitate creation of common types of events.
 */
object EventBuilderHelper {

    // Event startTime and endTime can be specified by passing values for attributes START_TIME and END_TIME
    const val START_TIME = "startTime"
    const val END_TIME = "endTime"

    private const val TAG = "EventBuilderHelper"
    private var enabled = true

    // background executor
    private val threadPool: ExecutorService = Executors.newFixedThreadPool(2)

    /**
     * This method allows event creation/storage to be disabled across the board.
     *
     * It is meant for tests.
     * It allows tests to be run individually.
     * When running individual tests in Android Studio the SalesforceSDKManager.init() is not called
     * As a result, if the test cause createAndStoreEvent to run, it will fail because
     * the call to UserAccountManager.getInstance() does a SalesforceSDKManager.getInstance().
     */
    @JvmStatic
    fun enableDisable(b: Boolean) {
        enabled = b
    }

    /**
     * Creates and stores an analytics event with the supplied parameters.  By default all createAndStoreEvent's are placed
     * into a background thread pool for posting.
     *
     * @param name Event name.
     * @param userAccount User account.
     * @param className Class name or context where the event was generated.
     * @param attributes Addiitonal attributes.
     */
    @JvmStatic
    fun createAndStoreEvent(
        name: String,
        userAccount: UserAccount?,
        className: String,
        attributes: JSONObject?
    ) {
        // Do nothing if not enabled
        if (!enabled) {
            return
        }

        // don't run on background if this is a test run
        if (SalesforceSDKManager.getInstance().isTestRun) {
            createAndStore(name, userAccount, className, attributes)
        } else {
            threadPool.execute {
                createAndStore(name, userAccount, className, attributes)
            }
        }
    }

    /**
     * Creates and stores an analytics event with the supplied parameters.
     *
     * @param name Event name.
     * @param userAccount User account.
     * @param className Class name or context where the event was generated.
     * @param attributes Addiitonal attributes.
     */
    @JvmStatic
    fun createAndStoreEventSync(
        name: String,
        userAccount: UserAccount?,
        className: String,
        attributes: JSONObject?
    ) {
        createAndStore(name, userAccount, className, attributes)
    }

    private fun createAndStore(
        name: String,
        userAccount: UserAccount?,
        className: String,
        attributes: JSONObject?
    ) {

        // Do nothing if not enabled
        if (!enabled) {
            return
        }

        var account = userAccount
        if (account == null) {
            account = UserAccountManager.getInstance().cachedCurrentUser
        }
        if (account == null) {
            return
        }
        val manager = SalesforceAnalyticsManager.getInstance(account)
        val builder = InstrumentationEventBuilder.getInstance(
            manager.analyticsManager,
            SalesforceSDKManager.getInstance().appContext
        )
        builder.name(name)

        val page = JSONObject()
        try {
            page.put("context", className)
        } catch (e: JSONException) {
            SalesforceSDKLogger.e(TAG, "Exception thrown while building page object", e)
        }
        builder.page(page)
        if (attributes != null) {
            builder.startTime(attributes.optLong(START_TIME))
            if (attributes.has(END_TIME)) builder.endTime(attributes.optLong(END_TIME))
            builder.attributes(attributes)
        }
        builder.schemaType(InstrumentationEvent.SchemaType.LightningInteraction)
        builder.eventType(InstrumentationEvent.EventType.system)
        try {
            val event = builder.buildEvent()
            manager.analyticsManager.eventStoreManager.storeEvent(event)
        } catch (e: InstrumentationEventBuilder.EventBuilderException) {
            SalesforceSDKLogger.e(TAG, "Exception thrown while building event", e)
        }
    }
}
