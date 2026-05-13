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

import com.salesforce.androidsdk.analytics.model.InstrumentationEvent
import com.salesforce.androidsdk.analytics.transform.AILTNTransform
import com.salesforce.androidsdk.analytics.transform.Transform
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SalesforceAnalyticsManagerTest {
    private lateinit var manager: SalesforceAnalyticsManager

    @Before
    fun setup() {
        manager = SalesforceAnalyticsManager.getUnauthenticatedInstance()
        manager.removeRemotePublisher(AILTNTransform::class.java)
        manager.addRemotePublisher(TestTransform::class.java, TestPublisher::class.java)
        SalesforceAnalyticsManager.setEventPublishBatchSize(2)
    }

    @After
    fun teardown() {
        TestPublisher.reset()
        manager.removeRemotePublisher(TestTransform::class.java)
        manager.addRemotePublisher(AILTNTransform::class.java, AILTNPublisher::class.java)
    }

    @Test
    fun testBatchPublish() {
        storeEvents(3)
        // Sanity Check
        assertEquals(3, manager.eventStoreManager.numStoredEvents)

        manager.publishAllEvents()

        assertEquals(0, manager.eventStoreManager.numStoredEvents)
        assertEquals(2, TestPublisher.publishedEvents.size)
        assertEquals(2, TestPublisher.publishedEvents[0].length())
        assertEquals(1, TestPublisher.publishedEvents[1].length())
    }

    @Test
    fun testFailedBatchPublish() {
        storeEvents(3)
        // Sanity Check
        assertEquals(3, manager.eventStoreManager.numStoredEvents)

        TestPublisher.publishSuccessResult = false
        manager.publishAllEvents()

        assertEquals(3, manager.eventStoreManager.numStoredEvents)
        assertEquals(1, TestPublisher.publishedEvents.size)
        assertEquals(2, TestPublisher.publishedEvents[0].length())
        manager.eventStoreManager.deleteAllEvents()
    }

    private fun storeEvents(count: Int) {
        for (i in 0 until count) {
            manager.eventStoreManager.storeEvent(
                InstrumentationEvent(JSONObject("{\"eventId\": \"$i\"}"))
            )
        }
    }

    class TestTransform : Transform {
        override fun transform(event: InstrumentationEvent?): JSONObject? {
            return try {
                JSONObject().apply {
                    put("id", event?.eventId)
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    class TestPublisher : AnalyticsPublisher {
        override fun publish(events: JSONArray): Boolean {
            publishedEvents.add(events)
            return publishSuccessResult
        }

        companion object {
            @JvmField
            var publishedEvents: MutableList<JSONArray> = ArrayList()
            @JvmField
            var publishSuccessResult = true

            fun reset() {
                publishedEvents = ArrayList()
                publishSuccessResult = true
            }
        }
    }
}
