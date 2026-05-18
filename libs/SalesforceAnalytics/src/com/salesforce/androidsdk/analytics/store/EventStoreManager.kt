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
import android.text.TextUtils
import com.salesforce.androidsdk.analytics.model.InstrumentationEvent
import com.salesforce.androidsdk.analytics.security.Encryptor
import com.salesforce.androidsdk.analytics.util.SalesforceAnalyticsLogger
import io.paperdb.Book
import io.paperdb.Paper
import org.json.JSONException
import org.json.JSONObject

/**
 * Provides APIs to store events in an encrypted store on the filesystem.
 * Each event is stored in a separate file on the filesystem.
 *
 * @author bhariharan
 */
class EventStoreManager(
    filenameSuffix: String,
    private val context: Context,
    private val encryptionKey: String
) {

    private val book: Book = Paper.bookOn(context.filesDir.absolutePath, FILENAME + filenameSuffix)
    private var isLoggingEnabled = true
    private var maxEvents = 10000
    private var countEvents: Int? = null // null means not calculated yet

    /**
     * Stores an event to the filesystem. A combination of event's unique ID and
     * filename suffix is used to generate a unique filename per event.
     *
     * @param event Event to be persisted.
     */
    fun storeEvent(event: InstrumentationEvent?) {
        if (event == null || TextUtils.isEmpty(event.toJson().toString())) {
            SalesforceAnalyticsLogger.d(context, TAG, "Invalid event")
            return
        }
        if (!shouldStoreEvent()) {
            return
        }
        val encrypted = encrypt(event.toJson().toString()) ?: return
        book.write(event.eventId, encrypted)
        // getNumStoredEvents() returns countEvents if known
        // otherwise it counts the keys in the book (and store the count in countEvents)
        countEvents = getNumStoredEvents() + 1
    }

    /**
     * Stores a list of events to the filesystem.
     *
     * @param events List of events.
     */
    fun storeEvents(events: List<InstrumentationEvent>?) {
        if (events == null || events.isEmpty()) {
            SalesforceAnalyticsLogger.d(context, TAG, "No events to store")
            return
        }
        if (!shouldStoreEvent()) {
            return
        }
        for (event in events) {
            storeEvent(event)
        }
    }

    /**
     * Returns a specific event stored on the filesystem for that unique identifier.
     *
     * @param eventId Unique identifier for the event.
     * @return Event.
     */
    fun fetchEvent(eventId: String?): InstrumentationEvent? {
        if (TextUtils.isEmpty(eventId)) {
            SalesforceAnalyticsLogger.e(context, TAG, "Invalid event ID supplied: $eventId")
            return null
        }
        var event: InstrumentationEvent? = null
        var encryptedEvent: String? = null
        try {
            encryptedEvent = book.read<String>(eventId ?: "", null)
        } catch (e: Exception) {
            SalesforceAnalyticsLogger.e(context, TAG, "Exception occurred while attempting to read event from PaperDB", e)
        }
        if (!TextUtils.isEmpty(encryptedEvent)) {
            val decryptedEvent = decrypt(encryptedEvent!!)
            if (!TextUtils.isEmpty(decryptedEvent)) {
                try {
                    val jsonObject = JSONObject(decryptedEvent!!)
                    event = InstrumentationEvent(jsonObject)
                } catch (e: JSONException) {
                    SalesforceAnalyticsLogger.e(context, TAG, "Exception occurred while attempting to convert to JSON", e)
                }
            }
        }
        return event
    }

    /**
     * Returns all the events stored on the filesystem.
     *
     * @return List of events.
     */
    fun fetchAllEvents(): List<InstrumentationEvent> {
        val events = mutableListOf<InstrumentationEvent>()
        for (event in iterateAllEvents()) {
            if (event != null) {
                events.add(event)
            }
        }
        return events
    }

    /**
     * Streams all the events stored on the filesystem. Will load each
     * event into memory one at a time to decrease memory impact.
     *
     * @return Iterable of events.
     */
    fun iterateAllEvents(): Iterable<InstrumentationEvent?> {
        return object : Iterable<InstrumentationEvent?> {
            private val eventIds = book.allKeys

            override fun iterator(): Iterator<InstrumentationEvent?> {
                return object : Iterator<InstrumentationEvent?> {
                    private val eventIdIterator = eventIds.iterator()

                    override fun hasNext(): Boolean = eventIdIterator.hasNext()

                    override fun next(): InstrumentationEvent? = fetchEvent(eventIdIterator.next())
                }
            }
        }
    }

    /**
     * Deletes a specific event stored on the filesystem.
     *
     * @param eventId Unique identifier for the event.
     * @return True - if successful, False - otherwise.
     */
    fun deleteEvent(eventId: String): Boolean {
        book.delete(eventId)
        val successfullyDeleted = !book.contains(eventId)
        if (successfullyDeleted) {
            // getNumStoredEvents() returns countEvents if known
            // otherwise it counts the keys in the book (and store the count in countEvents)
            countEvents = getNumStoredEvents() - 1
        }
        return successfullyDeleted
    }

    /**
     * Deletes the events stored on the filesystem for that unique identifier.
     */
    fun deleteEvents(eventIds: Collection<String>?) {
        if (eventIds == null || eventIds.isEmpty()) {
            SalesforceAnalyticsLogger.d(context, TAG, "No events to delete")
            return
        }
        for (eventId in eventIds) {
            deleteEvent(eventId)
        }
    }

    /**
     * Deletes all the events stored on the filesystem for that unique identifier.
     */
    fun deleteAllEvents() {
        book.destroy()
        countEvents = 0
    }

    /**
     * Disables or enables logging of events. If logging is disabled, no events
     * will be stored. However, publishing of events is still possible.
     *
     * @param enabled True - if logging should be enabled, False - otherwise.
     */
    @Synchronized
    fun enableLogging(enabled: Boolean) {
        isLoggingEnabled = enabled
    }

    /**
     * Returns whether logging is enabled or disabled.
     *
     * @return True - if logging is enabled, False - otherwise.
     */
    @Synchronized
    fun isLoggingEnabled(): Boolean = isLoggingEnabled

    /**
     * Sets the maximum number of events that can be stored.
     *
     * @param maxEvents Maximum number of events.
     */
    @Synchronized
    fun setMaxEvents(maxEvents: Int) {
        this.maxEvents = maxEvents
    }

    /**
     * Returns the maximum number of events that can be stored.
     *
     * @return Maximum number of events.
     */
    fun getMaxEvents(): Int = maxEvents

    /**
     * Returns number of stored events.
     *
     * Counts the keys in the book (and store the count in countEvents) if countEvents is null.
     * Returns countEvents directly if known (i.e. not null)
     *
     * @return Number of stored events.
     */
    fun getNumStoredEvents(): Int {
        if (countEvents == null) {
            countEvents = book.allKeys.size
        }
        return countEvents!!
    }

    private fun shouldStoreEvent(): Boolean {
        return isLoggingEnabled && (getNumStoredEvents() < maxEvents)
    }

    private fun encrypt(data: String): String? {
        return Encryptor.encrypt(data, encryptionKey)
    }

    private fun decrypt(data: String): String? {
        return Encryptor.decrypt(data, encryptionKey)
    }

    companion object {
        private const val FILENAME = "event_store"
        private const val TAG = "EventStoreManager"
    }
}
