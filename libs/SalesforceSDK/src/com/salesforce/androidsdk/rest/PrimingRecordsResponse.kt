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
class PrimingRecordsResponse @Throws(JSONException::class, ParseException::class) constructor(
    responseJson: JSONObject
) {

    @JvmField
    val primingRecords: MutableMap<String, Map<String, List<PrimingRecord>>> = HashMap()

    @JvmField
    val relayToken: String?

    @JvmField
    val ruleErrors: MutableList<PrimingRuleError> = ArrayList()

    @JvmField
    val stats: PrimingStats

    init {
        // Parsing priming records
        val apiNameToTypeToPrimingRecordsJson = responseJson.getJSONObject(PRIMING_RECORDS)
        val keys = apiNameToTypeToPrimingRecordsJson.keys()
        while (keys.hasNext()) {
            val objectApiName = keys.next()
            val typeToPrimingRecordsJson = apiNameToTypeToPrimingRecordsJson.getJSONObject(objectApiName)
            val typeToPrimingRecords = HashMap<String, List<PrimingRecord>>()
            val innerKeys = typeToPrimingRecordsJson.keys()
            while (innerKeys.hasNext()) {
                val recordType = innerKeys.next()
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

    class PrimingRecord @Throws(JSONException::class, ParseException::class) constructor(
        json: JSONObject
    ) {
        @JvmField
        val id: String = json.getString(ID)

        @JvmField
        val systemModstamp: Date? = TIMESTAMP_FORMAT.parse(json.getString(SYSTEM_MODSTAMP))

        companion object {
            const val ID: String = "id"
            const val SYSTEM_MODSTAMP: String = "systemModstamp"
        }
    }

    class PrimingRuleError @Throws(JSONException::class) constructor(json: JSONObject) {
        @JvmField
        var ruleId: String = json.getString(RULE_ID)

        companion object {
            const val RULE_ID: String = "ruleId"
        }
    }

    class PrimingStats @Throws(JSONException::class) constructor(json: JSONObject) {
        @JvmField
        var ruleCountTotal: Int = json.getInt(RULE_COUNT_TOTAL)

        @JvmField
        var recordCountTotal: Int = json.getInt(RECORD_COUNT_TOTAL)

        @JvmField
        var ruleCountServed: Int = json.getInt(RULE_COUNT_SERVED)

        @JvmField
        var recordCountServed: Int = json.getInt(RECORD_COUNT_SERVED)

        companion object {
            const val RULE_COUNT_TOTAL: String = "ruleCountTotal"
            const val RECORD_COUNT_TOTAL: String = "recordCountTotal"
            const val RULE_COUNT_SERVED: String = "ruleCountServed"
            const val RECORD_COUNT_SERVED: String = "recordCountServed"
        }
    }

    companion object {
        @JvmField
        val TIMESTAMP_FORMAT: DateFormat = SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            Locale.US
        ).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        const val PRIMING_RECORDS: String = "primingRecords"
        const val RELAY_TOKEN: String = "relayToken"
        const val RULE_ERRORS: String = "ruleErrors"
        const val STATS: String = "stats"
    }
}
