/*
 * Copyright (c) 2022-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.rest

import com.salesforce.androidsdk.util.JSONObjectHelper
import org.json.JSONException
import org.json.JSONObject
import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * PrimingRecordsResponse: Class to represent response for a priming records request.
 */
class PrimingRecordsResponse @Throws(JSONException::class, ParseException::class) constructor(responseJson: JSONObject) {

    @JvmField
    val primingRecords: MutableMap<String, MutableMap<String, MutableList<PrimingRecord>>> = HashMap()
    @JvmField
    val relayToken: String?
    @JvmField
    val ruleErrors: MutableList<PrimingRuleError> = ArrayList()
    @JvmField
    val stats: PrimingStats

    init {
        // Parsing priming records
        val apiNameToTypeToPrimingRecordsJson = responseJson.getJSONObject(PRIMING_RECORDS)
        val iterator = apiNameToTypeToPrimingRecordsJson.keys()
        while (iterator.hasNext()) {
            val objectApiName = iterator.next()
            val typeToPrimingRecordsJson = apiNameToTypeToPrimingRecordsJson.getJSONObject(objectApiName)
            val typeToPrimingRecords = HashMap<String, MutableList<PrimingRecord>>()
            val innerIterator = typeToPrimingRecordsJson.keys()
            while (innerIterator.hasNext()) {
                val recordType = innerIterator.next()
                val primingRecordsJson = typeToPrimingRecordsJson.getJSONArray(recordType)
                val primingRecordsList = ArrayList<PrimingRecord>()
                for (i in 0 until primingRecordsJson.length()) {
                    primingRecordsList.add(PrimingRecord(primingRecordsJson.getJSONObject(i)))
                }
                typeToPrimingRecords[recordType] = primingRecordsList
            }
            primingRecords[objectApiName] = typeToPrimingRecords
        }

        // Getting relay token
        relayToken = JSONObjectHelper.optString(responseJson, RELAY_TOKEN)

        // Parsing rule errors
        val ruleErrorsJson = responseJson.getJSONArray(RULE_ERRORS)
        for (i in 0 until ruleErrorsJson.length()) {
            ruleErrors.add(PrimingRuleError(ruleErrorsJson.getJSONObject(i)))
        }

        // Parsing stats
        stats = PrimingStats(responseJson.getJSONObject(STATS))
    }

    class PrimingRecord @Throws(JSONException::class, ParseException::class) constructor(json: JSONObject) {
        @JvmField
        val id: String
        @JvmField
        val systemModstamp: Date

        init {
            id = json.getString(ID)
            systemModstamp = TIMESTAMP_FORMAT.parse(json.getString(SYSTEM_MODSTAMP))!!
        }

        companion object {
            const val ID = "id"
            const val SYSTEM_MODSTAMP = "systemModstamp"
        }
    }

    class PrimingRuleError @Throws(JSONException::class) constructor(json: JSONObject) {
        @JvmField
        var ruleId: String

        init {
            ruleId = json.getString(RULE_ID)
        }

        companion object {
            const val RULE_ID = "ruleId"
        }
    }

    class PrimingStats @Throws(JSONException::class) constructor(json: JSONObject) {
        @JvmField
        var ruleCountTotal: Int
        @JvmField
        var recordCountTotal: Int
        @JvmField
        var ruleCountServed: Int
        @JvmField
        var recordCountServed: Int

        init {
            ruleCountTotal = json.getInt(RULE_COUNT_TOTAL)
            recordCountTotal = json.getInt(RECORD_COUNT_TOTAL)
            ruleCountServed = json.getInt(RULE_COUNT_SERVED)
            recordCountServed = json.getInt(RECORD_COUNT_SERVED)
        }

        companion object {
            const val RULE_COUNT_TOTAL = "ruleCountTotal"
            const val RECORD_COUNT_TOTAL = "recordCountTotal"
            const val RULE_COUNT_SERVED = "ruleCountServed"
            const val RECORD_COUNT_SERVED = "recordCountServed"
        }
    }

    companion object {
        @JvmField
        val TIMESTAMP_FORMAT: DateFormat

        init {
            // NB can't use RestRequest.ISO8601_DATE_FORMAT it's for timestamp of the form 2001-07-04T12:08:56.235-0700
            val tz = TimeZone.getTimeZone("UTC")
            TIMESTAMP_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            TIMESTAMP_FORMAT.timeZone = tz
        }

        const val PRIMING_RECORDS = "primingRecords"
        const val RELAY_TOKEN = "relayToken"
        const val RULE_ERRORS = "ruleErrors"
        const val STATS = "stats"
    }
}
