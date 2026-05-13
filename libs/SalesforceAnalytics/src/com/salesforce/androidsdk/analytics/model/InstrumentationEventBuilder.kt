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

import android.content.Context
import android.net.ConnectivityManager
import android.text.TextUtils
import com.salesforce.androidsdk.analytics.manager.AnalyticsManager
import org.json.JSONObject
import java.util.UUID

/**
 * Builder class that helps create a new InstrumentationEvent object.
 *
 * @author bhariharan
 */
class InstrumentationEventBuilder private constructor(
    private val analyticsManager: AnalyticsManager,
    private val context: Context
) {

    private var startTime: Long = 0
    private var endTime: Long = 0
    private var name: String? = null
    private var attributes: JSONObject? = null
    private var sessionId: String? = null
    private var senderId: String? = null
    private var senderContext: JSONObject? = null
    private var schemaType: InstrumentationEvent.SchemaType? = null
    private var eventType: InstrumentationEvent.EventType? = null
    private var errorType: InstrumentationEvent.ErrorType? = null
    private var senderParentId: String? = null
    private var sessionStartTime: Long = 0
    private var page: JSONObject? = null
    private var previousPage: JSONObject? = null
    private var marks: JSONObject? = null

    /**
     * Sets start time.
     *
     * @param startTime Start time.
     * @return Instance of this class.
     */
    fun startTime(startTime: Long): InstrumentationEventBuilder {
        this.startTime = startTime
        return this
    }

    /**
     * Sets end time.
     *
     * @param endTime End time.
     * @return Instance of this class.
     */
    fun endTime(endTime: Long): InstrumentationEventBuilder {
        this.endTime = endTime
        return this
    }

    /**
     * Sets name.
     *
     * @param name Name.
     * @return Instance of this class.
     */
    fun name(name: String?): InstrumentationEventBuilder {
        this.name = name
        return this
    }

    /**
     * Sets attributes.
     *
     * @param attributes Attributes.
     * @return Instance of this class.
     */
    fun attributes(attributes: JSONObject?): InstrumentationEventBuilder {
        this.attributes = attributes
        return this
    }

    /**
     * Sets session ID.
     *
     * @param sessionId Session ID.
     * @return Instance of this class.
     */
    fun sessionId(sessionId: String?): InstrumentationEventBuilder {
        this.sessionId = sessionId
        return this
    }

    /**
     * Sets sender ID.
     *
     * @param senderId Sender ID.
     * @return Instance of this class.
     */
    fun senderId(senderId: String?): InstrumentationEventBuilder {
        this.senderId = senderId
        return this
    }

    /**
     * Sets sender context.
     *
     * @param senderContext Sender context.
     * @return Instance of this class.
     */
    fun senderContext(senderContext: JSONObject?): InstrumentationEventBuilder {
        this.senderContext = senderContext
        return this
    }

    /**
     * Sets schema type.
     *
     * @param schemaType Schema type.
     * @return Instance of this class.
     */
    fun schemaType(schemaType: InstrumentationEvent.SchemaType?): InstrumentationEventBuilder {
        this.schemaType = schemaType
        return this
    }

    /**
     * Sets event type.
     *
     * @param eventType Event type.
     * @return Instance of this class.
     */
    fun eventType(eventType: InstrumentationEvent.EventType?): InstrumentationEventBuilder {
        this.eventType = eventType
        return this
    }

    /**
     * Sets error type.
     *
     * @param errorType Error type.
     * @return Instance of this class.
     */
    fun errorType(errorType: InstrumentationEvent.ErrorType?): InstrumentationEventBuilder {
        this.errorType = errorType
        return this
    }

    /**
     * Sets sender parent ID.
     *
     * @param senderParentId Sender parent ID.
     * @return Instance of this class.
     */
    fun senderParentId(senderParentId: String?): InstrumentationEventBuilder {
        this.senderParentId = senderParentId
        return this
    }

    /**
     * Sets session start time.
     *
     * @param sessionStartTime Session start time.
     * @return Instance of this class.
     */
    fun sessionStartTime(sessionStartTime: Long): InstrumentationEventBuilder {
        this.sessionStartTime = sessionStartTime
        return this
    }

    /**
     * Sets page.
     *
     * @param page Page.
     * @return Instance of this class.
     */
    fun page(page: JSONObject?): InstrumentationEventBuilder {
        this.page = page
        return this
    }

    /**
     * Sets previous page.
     *
     * @param previousPage Previous page.
     * @return Instance of this class.
     */
    fun previousPage(previousPage: JSONObject?): InstrumentationEventBuilder {
        this.previousPage = previousPage
        return this
    }

    /**
     * Sets marks.
     *
     * @param marks Marks.
     * @return Instance of this class.
     */
    fun marks(marks: JSONObject?): InstrumentationEventBuilder {
        this.marks = marks
        return this
    }

    /**
     * Validates and builds an InstrumentationEvent object. Throws EventBuilderException
     * if mandatory fields are not set.
     *
     * @return InstrumentationEvent object.
     * @throws EventBuilderException
     */
    @Throws(EventBuilderException::class)
    fun buildEvent(): InstrumentationEvent {
        val eventId = UUID.randomUUID().toString()
        var errorMessage: String? = null
        if (schemaType == null) {
            errorMessage = "Mandatory field 'schema type' not set!"
        }
        if (TextUtils.isEmpty(name)) {
            errorMessage = "Mandatory field 'name' not set!"
        }
        val deviceAppAttributes = analyticsManager.deviceAppAttributes
        if (deviceAppAttributes == null) {
            errorMessage = "Mandatory field 'device app attributes' not set!"
        }
        if ((schemaType == InstrumentationEvent.SchemaType.LightningInteraction ||
                    schemaType == InstrumentationEvent.SchemaType.LightningPerformance) &&
            eventType == null
        ) {
            errorMessage = "Mandatory field 'event type' not set!"
        }
        if (schemaType != InstrumentationEvent.SchemaType.LightningPerformance && page == null) {
            errorMessage = "Mandatory field 'page' not set!"
        }
        if (errorMessage != null) {
            throw EventBuilderException(errorMessage)
        }
        val sequenceId = analyticsManager.globalSequenceId + 1
        analyticsManager.globalSequenceId = sequenceId

        // Defaults to current time if not explicitly set.
        val curTime = System.currentTimeMillis()
        val finalStartTime = if (startTime == 0L) curTime else startTime
        val finalSessionStartTime = if (sessionStartTime == 0L) curTime else sessionStartTime
        return InstrumentationEvent(
            eventId, finalStartTime, endTime, name, attributes, sessionId,
            sequenceId, senderId, senderContext, schemaType, eventType, errorType,
            deviceAppAttributes, connectionType, senderParentId, finalSessionStartTime, page,
            previousPage, marks
        )
    }

    private val connectionType: String
        get() {
            val connectionType = StringBuilder()
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (connectivityManager != null) {
                @Suppress("DEPRECATION")
                val networkInfo = connectivityManager.activeNetworkInfo
                if (networkInfo != null) {
                    val type = networkInfo.typeName
                    val subtype = networkInfo.subtypeName
                    if (!TextUtils.isEmpty(type)) {
                        connectionType.append(type)
                    }
                    if (!TextUtils.isEmpty(subtype)) {
                        connectionType.append(";")
                        connectionType.append(subtype)
                    }
                }
            }
            return connectionType.toString()
        }

    /**
     * Exception thrown if the event can not be built.
     */
    class EventBuilderException(message: String?) : Exception(message) {
        companion object {
            private const val serialVersionUID = 1L
        }
    }

    companion object {
        /**
         * Returns an instance of this class.
         *
         * @param analyticsManager Instance of AnalyticsManager.
         * @param context Context.
         * @return Instance of this class.
         */
        @JvmStatic
        fun getInstance(
            analyticsManager: AnalyticsManager,
            context: Context
        ): InstrumentationEventBuilder {
            return InstrumentationEventBuilder(analyticsManager, context)
        }
    }
}
