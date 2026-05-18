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
package com.salesforce.androidsdk.analytics.store

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.analytics.manager.AnalyticsManager
import com.salesforce.androidsdk.analytics.model.DeviceAppAttributes
import com.salesforce.androidsdk.analytics.model.InstrumentationEvent
import com.salesforce.androidsdk.analytics.model.InstrumentationEventBuilder
import com.salesforce.androidsdk.analytics.security.Encryptor
import org.json.JSONObject
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Tests for EventStoreManager.
 *
 * @author bhariharan
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class EventStoreManagerTest {

    companion object {
        private const val TEST_FILENAME_SUFFIX = "_test_filename_suffix"
        private val TEST_ENCRYPTION_KEY = Encryptor.hash("test_encryption_key", "key")!!
        private val TEST_DEVICE_APP_ATTRIBUTES = DeviceAppAttributes(
            "TEST_APP_VERSION",
            "TEST_APP_NAME", "TEST_OS_VERSION", "TEST_OS_NAME", "TEST_NATIVE_APP_TYPE",
            "TEST_MOBILE_SDK_VERSION", "TEST_DEVICE_MODEL", "TEST_DEVICE_ID", "TEST_CLIENT_ID"
        )
        private const val TEST_EVENT_NAME = "TEST_EVENT_NAME_%s"
        private const val TEST_SENDER_ID = "TEST_SENDER_ID"
        private const val TEST_SESSION_ID = "TEST_SESSION_ID"
    }

    private lateinit var targetContext: Context
    private lateinit var storeManager: EventStoreManager
    private lateinit var analyticsManager: AnalyticsManager

    @Before
    fun setUp() {
        targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val uniqueId = UUID.randomUUID().toString()
        analyticsManager = AnalyticsManager(
            uniqueId,
            targetContext, TEST_ENCRYPTION_KEY, TEST_DEVICE_APP_ATTRIBUTES
        )
        storeManager = EventStoreManager(TEST_FILENAME_SUFFIX, targetContext, TEST_ENCRYPTION_KEY)
    }

    @After
    fun tearDown() {
        storeManager.deleteAllEvents()
        analyticsManager.reset()
    }

    /**
     * Test for storing one event and retrieving it.
     */
    @Test
    fun testStoreOneEvent() {
        val event = createTestEvent()
        Assert.assertNotNull("Generated event stored should not be null", event)
        Assert.assertEquals(0, storeManager.getNumStoredEvents())
        storeManager.storeEvent(event)
        Assert.assertEquals(1, storeManager.getNumStoredEvents())
        val events = storeManager.fetchAllEvents()
        Assert.assertNotNull("List of events stored should not be null", events)
        Assert.assertEquals("Number of events stored should be 1", 1, events.size)
        Assert.assertTrue("Stored event should be the same as generated event", event == events[0])
    }

    /**
     * Test for storing many events and retrieving them.
     */
    @Test
    fun testStoreMultipleEvents() {
        val event1 = createTestEvent()
        Assert.assertNotNull("Generated event stored should not be null", event1)
        val event2 = createTestEvent()
        Assert.assertNotNull("Generated event stored should not be null", event2)
        val genEvents = mutableListOf<InstrumentationEvent>()
        genEvents.add(event1)
        genEvents.add(event2)
        Assert.assertEquals(0, storeManager.getNumStoredEvents())
        storeManager.storeEvents(genEvents)
        Assert.assertEquals(2, storeManager.getNumStoredEvents())
        val events = storeManager.fetchAllEvents()
        Assert.assertNotNull("List of events stored should not be null", events)
        Assert.assertEquals("Number of events stored should be 2", 2, events.size)
        Assert.assertTrue("Stored event should be the same as generated event", events.contains(event1))
        Assert.assertTrue("Stored event should be the same as generated event", events.contains(event2))
    }

    /**
     * Test for fetching one event by specifying event ID.
     */
    @Test
    fun testFetchOneEvent() {
        val event = createTestEvent()
        Assert.assertNotNull("Generated event stored should not be null", event)
        val eventId = event.eventId
        storeManager.storeEvent(event)
        val storedEvent = storeManager.fetchEvent(eventId)
        Assert.assertNotNull("Event stored should not be null", storedEvent)
        Assert.assertTrue("Stored event should be the same as generated event", event == storedEvent)
    }

    /**
     * Test for fetching all stored events.
     */
    @Test
    fun testFetchAllEvents() {
        val event1 = createTestEvent()
        Assert.assertNotNull("Generated event stored should not be null", event1)
        storeManager.storeEvent(event1)
        val event2 = createTestEvent()
        Assert.assertNotNull("Generated event stored should not be null", event2)
        storeManager.storeEvent(event2)
        val events = storeManager.fetchAllEvents()
        Assert.assertNotNull("List of events stored should not be null", events)
        Assert.assertEquals("Number of events stored should be 2", 2, events.size)
        Assert.assertTrue("Stored event should be the same as generated event", events.contains(event1))
        Assert.assertTrue("Stored event should be the same as generated event", events.contains(event2))
    }

    /**
     * Test for iterating over all stored events.
     */
    @Test
    fun testIterateAllEvents() {
        val events = mutableSetOf<InstrumentationEvent>()
        events.add(createTestEvent())
        events.add(createTestEvent())
        for (event in events) {
            storeManager.storeEvent(event)
        }

        val eventsIterable = storeManager.iterateAllEvents()
        Assert.assertNotNull("Iterable of events stored should not be null", eventsIterable)
        val iterator = eventsIterable.iterator()
        Assert.assertTrue("Iterator should return the first event", iterator.hasNext())
        Assert.assertTrue("Stored event should be the same as generated event", events.contains(iterator.next()))
        Assert.assertTrue("Iterator should return the second event", iterator.hasNext())
        Assert.assertTrue("Stored event should be the same as generated event", events.contains(iterator.next()))
        Assert.assertFalse("Iterator should only return two events", iterator.hasNext())
    }

    /**
     * Test for deleting one event by specifying event ID.
     */
    @Test
    fun testDeleteOneEvent() {
        val event = createTestEvent()
        Assert.assertNotNull("Generated event stored should not be null", event)
        val eventId = event.eventId
        Assert.assertEquals(0, storeManager.getNumStoredEvents())
        storeManager.storeEvent(event)
        Assert.assertEquals(1, storeManager.getNumStoredEvents())
        val eventsBeforeDel = storeManager.fetchAllEvents()
        Assert.assertNotNull("List of events stored should not be null", eventsBeforeDel)
        Assert.assertEquals("Number of events stored should be 1", 1, eventsBeforeDel.size)
        Assert.assertTrue("Stored event should be the same as generated event", event == eventsBeforeDel[0])
        storeManager.deleteEvent(eventId)
        Assert.assertEquals(0, storeManager.getNumStoredEvents())

        val eventsAfterDel = storeManager.fetchAllEvents()
        Assert.assertNotNull("List of events stored should not be null", eventsAfterDel)
        Assert.assertEquals("Number of events stored should be 0", 0, eventsAfterDel.size)
    }

    /**
     * Test for deleting multiple events by specifying event IDs.
     */
    @Test
    fun testDeleteMultipleEvents() {
        val event1 = createTestEvent()
        Assert.assertNotNull("Generated event stored should not be null", event1)
        val eventId1 = event1.eventId
        val event2 = createTestEvent()
        Assert.assertNotNull("Generated event stored should not be null", event2)
        val eventId2 = event2.eventId
        val event3 = createTestEvent()
        Assert.assertNotNull("Generated event stored should not be null", event3)
        val eventId3 = event3.eventId
        val genEvents = mutableListOf<InstrumentationEvent>()
        genEvents.add(event1)
        genEvents.add(event2)
        genEvents.add(event3)
        Assert.assertEquals(0, storeManager.getNumStoredEvents())
        storeManager.storeEvents(genEvents)
        Assert.assertEquals(3, storeManager.getNumStoredEvents())
        val eventsBeforeDel = storeManager.fetchAllEvents()
        Assert.assertNotNull("List of events stored should not be null", eventsBeforeDel)
        Assert.assertEquals("Number of events stored should be 3", 3, eventsBeforeDel.size)
        Assert.assertTrue("Stored event should be the same as generated event", eventsBeforeDel.contains(event1))
        Assert.assertTrue("Stored event should be the same as generated event", eventsBeforeDel.contains(event2))
        Assert.assertTrue("Stored event should be the same as generated event", eventsBeforeDel.contains(event3))
        val eventIds = mutableListOf<String>()
        eventIds.add(eventId1)
        eventIds.add(eventId3)
        storeManager.deleteEvents(eventIds)
        Assert.assertEquals(1, storeManager.getNumStoredEvents())
        val eventsAfterDel = storeManager.fetchAllEvents()
        Assert.assertNotNull("List of events stored should not be null", eventsAfterDel)
        Assert.assertEquals("Number of events stored should be 1", 1, eventsAfterDel.size)
        Assert.assertTrue("Event2 should not have been deleted", eventsBeforeDel.contains(event2))
    }

    /**
     * Test for deleting all events stored.
     */
    @Test
    fun testDeleteAllEvents() {
        val event1 = createTestEvent()
        Assert.assertNotNull("Generated event stored should not be null", event1)
        val event2 = createTestEvent()
        Assert.assertNotNull("Generated event stored should not be null", event2)
        val genEvents = mutableListOf<InstrumentationEvent>()
        genEvents.add(event1)
        genEvents.add(event2)
        Assert.assertEquals(0, storeManager.getNumStoredEvents())
        storeManager.storeEvents(genEvents)
        Assert.assertEquals(2, storeManager.getNumStoredEvents())
        val eventsBeforeDel = storeManager.fetchAllEvents()
        Assert.assertNotNull("List of events stored should not be null", eventsBeforeDel)
        Assert.assertEquals("Number of events stored should be 2", 2, eventsBeforeDel.size)
        Assert.assertTrue("Stored event should be the same as generated event", eventsBeforeDel.contains(event1))
        Assert.assertTrue("Stored event should be the same as generated event", eventsBeforeDel.contains(event2))
        storeManager.deleteAllEvents()
        Assert.assertEquals(0, storeManager.getNumStoredEvents())
        val eventsAfterDel = storeManager.fetchAllEvents()
        Assert.assertNotNull("List of events stored should not be null", eventsAfterDel)
        Assert.assertEquals("Number of events stored should be 0", 0, eventsAfterDel.size)
    }

    /**
     * Test for disabling logging.
     */
    @Test
    fun testDisablingLogging() {
        val event = createTestEvent()
        Assert.assertNotNull("Generated event stored should not be null", event)
        storeManager.enableLogging(false)
        storeManager.storeEvent(event)
        val events = storeManager.fetchAllEvents()
        Assert.assertNotNull("List of events stored should not be null", events)
        Assert.assertEquals("Number of events stored should be 0", 0, events.size)
    }

    /**
     * Test for enabling logging.
     */
    @Test
    fun testEnablingLogging() {
        val event = createTestEvent()
        Assert.assertNotNull("Generated event stored should not be null", event)
        storeManager.enableLogging(false)
        storeManager.storeEvent(event)
        var events = storeManager.fetchAllEvents()
        Assert.assertNotNull("List of events stored should not be null", events)
        Assert.assertEquals("Number of events stored should be 0", 0, events.size)
        storeManager.enableLogging(true)
        storeManager.storeEvent(event)
        events = storeManager.fetchAllEvents()
        Assert.assertNotNull("List of events stored should not be null", events)
        Assert.assertEquals("Number of events stored should be 1", 1, events.size)
        Assert.assertTrue("Stored event should be the same as generated event", event == events[0])
    }

    /**
     * Test for event limit exceeded.
     */
    @Test
    fun testEventLimitExceeded() {
        val event = createTestEvent()
        Assert.assertNotNull("Generated event stored should not be null", event)
        storeManager.setMaxEvents(0)
        storeManager.storeEvent(event)
        val events = storeManager.fetchAllEvents()
        Assert.assertNotNull("List of events stored should not be null", events)
        Assert.assertEquals("Number of events stored should be 0", 0, events.size)
    }

    /**
     * Test for event limit not exceeded.
     */
    @Test
    fun testEventLimitNotExceeded() {
        val event = createTestEvent()
        Assert.assertNotNull("Generated event stored should not be null", event)
        storeManager.setMaxEvents(0)
        storeManager.storeEvent(event)
        var events = storeManager.fetchAllEvents()
        Assert.assertNotNull("List of events stored should not be null", events)
        Assert.assertEquals("Number of events stored should be 0", 0, events.size)
        storeManager.setMaxEvents(1)
        storeManager.storeEvent(event)
        events = storeManager.fetchAllEvents()
        Assert.assertNotNull("List of events stored should not be null", events)
        Assert.assertEquals("Number of events stored should be 1", 1, events.size)
        Assert.assertTrue("Stored event should be the same as generated event", event == events[0])
    }

    private fun createTestEvent(): InstrumentationEvent {
        val eventBuilder = InstrumentationEventBuilder.getInstance(analyticsManager, targetContext)
        val curTime = System.currentTimeMillis()
        val eventName = String.format(TEST_EVENT_NAME, curTime)
        eventBuilder.startTime(curTime)
        eventBuilder.name(eventName)
        eventBuilder.sessionId(TEST_SESSION_ID)
        eventBuilder.senderId(TEST_SENDER_ID)
        eventBuilder.schemaType(InstrumentationEvent.SchemaType.LightningError)
        eventBuilder.eventType(InstrumentationEvent.EventType.system)
        eventBuilder.errorType(InstrumentationEvent.ErrorType.warn)
        eventBuilder.page(JSONObject())
        return eventBuilder.buildEvent()
    }
}
