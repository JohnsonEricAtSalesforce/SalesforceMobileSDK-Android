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
package com.salesforce.androidsdk.analytics.transform

import android.text.TextUtils
import com.salesforce.androidsdk.analytics.model.InstrumentationEvent
import com.salesforce.androidsdk.analytics.util.SalesforceAnalyticsLogger
import org.json.JSONException
import org.json.JSONObject

/**
 * Represents a transformation of generic event to the AILTN format.
 *
 * @author bhariharan
 */
class AILTNTransform : Transform {

    override fun transform(event: InstrumentationEvent): JSONObject? {
        var logLine: JSONObject? = buildPayload(event) ?: return null
        try {
            logLine?.put(DEVICE_ATTRIBUTES_KEY, buildDeviceAttributes(event))
        } catch (e: JSONException) {
            logLine = null
            SalesforceAnalyticsLogger.e(null, TAG, "Exception occurred while transforming JSON", e)
        }
        return logLine
    }

    private fun buildDeviceAttributes(event: InstrumentationEvent): JSONObject {
        var deviceAttributes = JSONObject()
        try {
            val deviceAppAttributes = event.deviceAppAttributes
            if (deviceAppAttributes != null) {
                deviceAttributes = deviceAppAttributes.toJson()
            }
            deviceAttributes.put(CONNECTION_TYPE_KEY, event.connectionType)
        } catch (e: JSONException) {
            SalesforceAnalyticsLogger.e(null, TAG, "Exception occurred while transforming JSON", e)
        }
        return deviceAttributes
    }

    private fun buildPayload(event: InstrumentationEvent): JSONObject? {
        var payload: JSONObject? = JSONObject()
        try {
            payload!!.put(VERSION_KEY, VERSION_VALUE)
            val schemaType = event.schemaType
            payload.put(SCHEMA_TYPE_KEY, schemaType?.name)
            payload.put(ID_KEY, event.eventId)
            payload.put(EVENT_SOURCE_KEY, event.name)
            val startTime = event.startTime
            payload.put(TS_KEY, startTime)
            payload.put(PAGE_START_TIME_KEY, event.sessionStartTime)
            val endTime = event.endTime
            val duration = endTime - startTime
            if (duration > 0) {
                if (schemaType == InstrumentationEvent.SchemaType.LightningInteraction
                    || schemaType == InstrumentationEvent.SchemaType.LightningPerformance
                ) {
                    payload.put(DURATION_KEY, duration)
                } else if (schemaType == InstrumentationEvent.SchemaType.LightningPageView) {
                    payload.put(EPT_KEY, duration)
                }
            }
            val sessionId = event.sessionId
            if (!TextUtils.isEmpty(sessionId)) {
                payload.put(CLIENT_SESSION_ID_KEY, sessionId)
            }
            if (schemaType != InstrumentationEvent.SchemaType.LightningPerformance) {
                payload.put(SEQUENCE_KEY, event.sequenceId)
            }
            val attributes = event.attributes
            if (attributes != null) {
                payload.put(ATTRIBUTES_KEY, attributes)
            }
            if (schemaType != InstrumentationEvent.SchemaType.LightningPerformance) {
                payload.put(PAGE_KEY, event.page)
            }
            val previousPage = event.previousPage
            if (previousPage != null && schemaType == InstrumentationEvent.SchemaType.LightningPageView) {
                payload.put(PREVIOUS_PAGE_KEY, previousPage)
            }
            val marks = event.marks
            if (marks != null && (schemaType == InstrumentationEvent.SchemaType.LightningPageView
                        || schemaType == InstrumentationEvent.SchemaType.LightningPerformance)
            ) {
                payload.put(MARKS_KEY, marks)
            }
            if (schemaType == InstrumentationEvent.SchemaType.LightningInteraction
                || schemaType == InstrumentationEvent.SchemaType.LightningPageView
            ) {
                val locator = buildLocator(event)
                if (locator != null) {
                    payload.put(LOCATOR_KEY, locator)
                }
            }
            val eventType = event.eventType
            var eventTypeString: String? = null
            if (schemaType == InstrumentationEvent.SchemaType.LightningPerformance) {
                eventTypeString = PERF_EVENT_TYPE
            } else if (schemaType == InstrumentationEvent.SchemaType.LightningInteraction
                && eventType != null
            ) {
                eventTypeString = eventType.name
            }
            if (!TextUtils.isEmpty(eventTypeString)) {
                payload.put(EVENT_TYPE_KEY, eventTypeString)
            }
            val errorType = event.errorType
            if (errorType != null && schemaType == InstrumentationEvent.SchemaType.LightningError) {
                payload.put(ERROR_TYPE_KEY, errorType.name)
            }
        } catch (e: JSONException) {
            payload = null
            SalesforceAnalyticsLogger.e(null, TAG, "Exception occurred while transforming JSON", e)
        }
        return payload
    }

    private fun buildLocator(event: InstrumentationEvent): JSONObject? {
        var locator: JSONObject? = JSONObject()
        try {
            val senderId = event.senderId
            val senderParentId = event.senderParentId
            if (TextUtils.isEmpty(senderId) || TextUtils.isEmpty(senderParentId)) {
                return null
            }
            locator!!.put(TARGET_KEY, senderId)
            locator.put(SCOPE_KEY, senderParentId)
            val senderContext = event.senderContext
            if (senderContext != null) {
                locator.put(CONTEXT_KEY, senderContext)
            }
        } catch (e: JSONException) {
            locator = null
            SalesforceAnalyticsLogger.e(null, TAG, "Exception occurred while transforming JSON", e)
        }
        return locator
    }

    companion object {
        private const val TAG = "AILTNTransform"
        private const val CONNECTION_TYPE_KEY = "connectionType"
        private const val VERSION_KEY = "version"
        private const val VERSION_VALUE = "0.2"
        private const val SCHEMA_TYPE_KEY = "schemaType"
        private const val ID_KEY = "id"
        private const val EVENT_SOURCE_KEY = "eventSource"
        private const val TS_KEY = "ts"
        private const val PAGE_START_TIME_KEY = "pageStartTime"
        private const val DURATION_KEY = "duration"
        private const val EPT_KEY = "ept"
        private const val CLIENT_SESSION_ID_KEY = "clientSessionId"
        private const val SEQUENCE_KEY = "sequence"
        private const val ATTRIBUTES_KEY = "attributes"
        private const val LOCATOR_KEY = "locator"
        private const val PAGE_KEY = "page"
        private const val PREVIOUS_PAGE_KEY = "previousPage"
        private const val MARKS_KEY = "marks"
        private const val EVENT_TYPE_KEY = "eventType"
        private const val ERROR_TYPE_KEY = "errorType"
        private const val TARGET_KEY = "target"
        private const val SCOPE_KEY = "scope"
        private const val CONTEXT_KEY = "context"
        private const val DEVICE_ATTRIBUTES_KEY = "deviceAttributes"
        private const val PERF_EVENT_TYPE = "defs"
    }
}
