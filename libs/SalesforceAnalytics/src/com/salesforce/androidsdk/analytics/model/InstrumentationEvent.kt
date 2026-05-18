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
package com.salesforce.androidsdk.analytics.model

import android.text.TextUtils
import com.salesforce.androidsdk.analytics.util.SalesforceAnalyticsLogger
import org.json.JSONException
import org.json.JSONObject

/**
 * Represents a typical instrumentation event. Transforms can be used to
 * convert this event into a specific library's event format.
 *
 * @author bhariharan
 */
class InstrumentationEvent internal constructor(
    val eventId: String,
    val startTime: Long,
    val endTime: Long,
    val name: String,
    val attributes: JSONObject?,
    val sessionId: String?,
    val sequenceId: Int,
    val senderId: String?,
    val senderContext: JSONObject?,
    val schemaType: SchemaType?,
    val eventType: EventType?,
    val errorType: ErrorType?,
    val deviceAppAttributes: DeviceAppAttributes?,
    val connectionType: String?,
    val senderParentId: String?,
    val sessionStartTime: Long,
    val page: JSONObject?,
    val previousPage: JSONObject?,
    val marks: JSONObject?
) {

    /**
     * Constructs an event from its JSON representation.
     * This is meant for internal use. Apps should use InstrumentationEventBuilder
     * to build InstrumentationEvent objects.
     *
     * @param json JSON object.
     */
    constructor(json: JSONObject?) : this(
        eventId = json?.optString(EVENT_ID_KEY) ?: "",
        startTime = json?.optLong(START_TIME_KEY) ?: 0L,
        endTime = json?.optLong(END_TIME_KEY) ?: 0L,
        name = json?.optString(NAME_KEY) ?: "",
        attributes = json?.optJSONObject(ATTRIBUTES_KEY),
        sessionId = json?.optString(SESSION_ID_KEY),
        sequenceId = json?.optInt(SEQUENCE_ID_KEY) ?: 0,
        senderId = json?.optString(SENDER_ID_KEY),
        senderContext = json?.optJSONObject(SENDER_CONTEXT_KEY),
        schemaType = json?.optString(SCHEMA_TYPE_KEY)?.let { if (!TextUtils.isEmpty(it)) SchemaType.valueOf(it) else null },
        eventType = json?.optString(EVENT_TYPE_KEY)?.let { if (!TextUtils.isEmpty(it)) EventType.valueOf(it) else null },
        errorType = json?.optString(ERROR_TYPE_KEY)?.let { if (!TextUtils.isEmpty(it)) ErrorType.valueOf(it) else null },
        deviceAppAttributes = json?.optJSONObject(DEVICE_APP_ATTRIBUTES_KEY)?.let { DeviceAppAttributes(it) },
        connectionType = json?.optString(CONNECTION_TYPE_KEY),
        senderParentId = json?.optString(SENDER_PARENT_ID_KEY),
        sessionStartTime = json?.optLong(SESSION_START_TIME_KEY) ?: 0L,
        page = json?.optJSONObject(PAGE_KEY),
        previousPage = json?.optJSONObject(PREVIOUS_PAGE_KEY),
        marks = json?.optJSONObject(MARKS_KEY)
    )

    /**
     * Returns a JSON representation of this event.
     *
     * @return JSON object.
     */
    fun toJson(): JSONObject {
        val json = JSONObject()
        try {
            json.put(EVENT_ID_KEY, eventId)
            json.put(START_TIME_KEY, startTime)
            json.put(END_TIME_KEY, endTime)
            json.put(NAME_KEY, name)
            if (attributes != null) {
                json.put(ATTRIBUTES_KEY, attributes)
            }
            if (sessionId != null) {
                json.put(SESSION_ID_KEY, sessionId)
            }
            json.put(SEQUENCE_ID_KEY, sequenceId)
            json.put(SENDER_ID_KEY, senderId)
            if (senderContext != null) {
                json.put(SENDER_CONTEXT_KEY, senderContext)
            }
            if (schemaType != null) {
                json.put(SCHEMA_TYPE_KEY, schemaType.name)
            }
            if (eventType != null) {
                json.put(EVENT_TYPE_KEY, eventType.name)
            }
            if (errorType != null) {
                json.put(ERROR_TYPE_KEY, errorType.name)
            }
            if (deviceAppAttributes != null) {
                json.put(DEVICE_APP_ATTRIBUTES_KEY, deviceAppAttributes.toJson())
            }
            json.put(CONNECTION_TYPE_KEY, connectionType)
            json.put(SENDER_PARENT_ID_KEY, senderParentId)
            json.put(SESSION_START_TIME_KEY, sessionStartTime)
            if (page != null) {
                json.put(PAGE_KEY, page)
            }
            if (previousPage != null) {
                json.put(PREVIOUS_PAGE_KEY, previousPage)
            }
            if (marks != null) {
                json.put(MARKS_KEY, marks)
            }
        } catch (e: JSONException) {
            SalesforceAnalyticsLogger.e(null, TAG, "Exception thrown while attempting to convert to JSON", e)
        }
        return json
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is InstrumentationEvent) {
            return false
        }
        if (TextUtils.isEmpty(eventId)) {
            return false
        }

        /*
         * Since event ID is globally unique and is set during construction of the event,
         * if the event IDs of both events are equal, the events themselves are the same.
         */
        return eventId == other.eventId
    }

    override fun hashCode(): Int = eventId.hashCode()

    /**
     * Represents the type of event being logged.
     */
    enum class EventType {
        user,
        system,
        error,
        crud
    }

    /**
     * Represents the type of schema being logged.
     */
    enum class SchemaType {
        LightningInteraction,
        LightningPageView,
        LightningPerformance,
        LightningError
    }

    /**
     * Represents the type of error being logged.
     */
    enum class ErrorType {
        info,
        warn,
        error
    }

    companion object {

        private const val TAG = "InstrumentationEvent"

        @JvmField val EVENT_ID_KEY = "eventId"
        @JvmField val START_TIME_KEY = "startTime"
        @JvmField val END_TIME_KEY = "endTime"
        @JvmField val NAME_KEY = "name"
        @JvmField val ATTRIBUTES_KEY = "attributes"
        @JvmField val SESSION_ID_KEY = "sessionId"
        @JvmField val SEQUENCE_ID_KEY = "sequenceId"
        @JvmField val SENDER_ID_KEY = "senderId"
        @JvmField val SENDER_CONTEXT_KEY = "senderContext"
        @JvmField val SCHEMA_TYPE_KEY = "schemaType"
        @JvmField val EVENT_TYPE_KEY = "eventType"
        @JvmField val ERROR_TYPE_KEY = "errorType"
        @JvmField val CONNECTION_TYPE_KEY = "connectionType"
        @JvmField val DEVICE_APP_ATTRIBUTES_KEY = "deviceAppAttributes"
        @JvmField val SENDER_PARENT_ID_KEY = "senderParentId"
        @JvmField val SESSION_START_TIME_KEY = "sessionStartTime"
        @JvmField val PAGE_KEY = "page"
        @JvmField val PREVIOUS_PAGE_KEY = "previousPage"
        @JvmField val MARKS_KEY = "marks"
    }
}
