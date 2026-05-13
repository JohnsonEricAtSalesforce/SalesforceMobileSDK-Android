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
class InstrumentationEvent {

    val eventId: String?
    val startTime: Long
    val endTime: Long
    val name: String?
    val attributes: JSONObject?
    val sessionId: String?
    val sequenceId: Int
    val senderId: String?
    val senderContext: JSONObject?
    val schemaType: SchemaType?
    val eventType: EventType?
    val errorType: ErrorType?
    val deviceAppAttributes: DeviceAppAttributes?
    val connectionType: String?
    val senderParentId: String?
    val sessionStartTime: Long
    val page: JSONObject?
    val previousPage: JSONObject?
    val marks: JSONObject?

    internal constructor(
        eventId: String?,
        startTime: Long,
        endTime: Long,
        name: String?,
        attributes: JSONObject?,
        sessionId: String?,
        sequenceId: Int,
        senderId: String?,
        senderContext: JSONObject?,
        schemaType: SchemaType?,
        eventType: EventType?,
        errorType: ErrorType?,
        deviceAppAttributes: DeviceAppAttributes?,
        connectionType: String?,
        senderParentId: String?,
        sessionStartTime: Long,
        page: JSONObject?,
        previousPage: JSONObject?,
        marks: JSONObject?
    ) {
        this.eventId = eventId
        this.startTime = startTime
        this.endTime = endTime
        this.name = name
        this.attributes = attributes
        this.sessionId = sessionId
        this.sequenceId = sequenceId
        this.senderId = senderId
        this.senderContext = senderContext
        this.schemaType = schemaType
        this.eventType = eventType
        this.errorType = errorType
        this.deviceAppAttributes = deviceAppAttributes
        this.connectionType = connectionType
        this.senderParentId = senderParentId
        this.sessionStartTime = sessionStartTime
        this.page = page
        this.previousPage = previousPage
        this.marks = marks
    }

    /**
     * Constructs an event from its JSON representation.
     * This is meant for internal use. Apps should use InstrumentationEventBuilder
     * to build InstrumentationEvent objects.
     *
     * @param json JSON object.
     */
    constructor(json: JSONObject?) {
        if (json != null) {
            eventId = json.optString(EVENT_ID_KEY)
            startTime = json.optLong(START_TIME_KEY)
            endTime = json.optLong(END_TIME_KEY)
            name = json.optString(NAME_KEY)
            attributes = json.optJSONObject(ATTRIBUTES_KEY)
            sessionId = json.optString(SESSION_ID_KEY)
            sequenceId = json.optInt(SEQUENCE_ID_KEY)
            senderId = json.optString(SENDER_ID_KEY)
            senderContext = json.optJSONObject(SENDER_CONTEXT_KEY)
            val schemaTypeString = json.optString(SCHEMA_TYPE_KEY)
            schemaType = if (!TextUtils.isEmpty(schemaTypeString)) {
                SchemaType.valueOf(schemaTypeString)
            } else null
            val eventTypeString = json.optString(EVENT_TYPE_KEY)
            eventType = if (!TextUtils.isEmpty(eventTypeString)) {
                EventType.valueOf(eventTypeString)
            } else null
            val errorTypeString = json.optString(ERROR_TYPE_KEY)
            errorType = if (!TextUtils.isEmpty(errorTypeString)) {
                ErrorType.valueOf(errorTypeString)
            } else null
            val deviceAttributesJson = json.optJSONObject(DEVICE_APP_ATTRIBUTES_KEY)
            deviceAppAttributes = if (deviceAttributesJson != null) {
                DeviceAppAttributes(deviceAttributesJson)
            } else null
            connectionType = json.optString(CONNECTION_TYPE_KEY)
            senderParentId = json.optString(SENDER_PARENT_ID_KEY)
            sessionStartTime = json.optLong(SESSION_START_TIME_KEY)
            page = json.optJSONObject(PAGE_KEY)
            previousPage = json.optJSONObject(PREVIOUS_PAGE_KEY)
            marks = json.optJSONObject(MARKS_KEY)
        } else {
            eventId = null
            startTime = 0
            endTime = 0
            name = null
            attributes = null
            sessionId = null
            sequenceId = 0
            senderId = null
            senderContext = null
            schemaType = null
            eventType = null
            errorType = null
            deviceAppAttributes = null
            connectionType = null
            senderParentId = null
            sessionStartTime = 0
            page = null
            previousPage = null
            marks = null
        }
    }

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
            attributes?.let { json.put(ATTRIBUTES_KEY, it) }
            sessionId?.let { json.put(SESSION_ID_KEY, it) }
            json.put(SEQUENCE_ID_KEY, sequenceId)
            json.put(SENDER_ID_KEY, senderId)
            senderContext?.let { json.put(SENDER_CONTEXT_KEY, it) }
            schemaType?.let { json.put(SCHEMA_TYPE_KEY, it.name) }
            eventType?.let { json.put(EVENT_TYPE_KEY, it.name) }
            errorType?.let { json.put(ERROR_TYPE_KEY, it.name) }
            deviceAppAttributes?.let { json.put(DEVICE_APP_ATTRIBUTES_KEY, it.toJson()) }
            json.put(CONNECTION_TYPE_KEY, connectionType)
            json.put(SENDER_PARENT_ID_KEY, senderParentId)
            json.put(SESSION_START_TIME_KEY, sessionStartTime)
            page?.let { json.put(PAGE_KEY, it) }
            previousPage?.let { json.put(PREVIOUS_PAGE_KEY, it) }
            marks?.let { json.put(MARKS_KEY, it) }
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

    override fun hashCode(): Int {
        return eventId.hashCode()
    }

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
        const val EVENT_ID_KEY = "eventId"
        const val START_TIME_KEY = "startTime"
        const val END_TIME_KEY = "endTime"
        const val NAME_KEY = "name"
        const val ATTRIBUTES_KEY = "attributes"
        const val SESSION_ID_KEY = "sessionId"
        const val SEQUENCE_ID_KEY = "sequenceId"
        const val SENDER_ID_KEY = "senderId"
        const val SENDER_CONTEXT_KEY = "senderContext"
        const val SCHEMA_TYPE_KEY = "schemaType"
        const val EVENT_TYPE_KEY = "eventType"
        const val ERROR_TYPE_KEY = "errorType"
        const val CONNECTION_TYPE_KEY = "connectionType"
        const val DEVICE_APP_ATTRIBUTES_KEY = "deviceAppAttributes"
        const val SENDER_PARENT_ID_KEY = "senderParentId"
        const val SESSION_START_TIME_KEY = "sessionStartTime"
        const val PAGE_KEY = "page"
        const val PREVIOUS_PAGE_KEY = "previousPage"
        const val MARKS_KEY = "marks"
    }
}
