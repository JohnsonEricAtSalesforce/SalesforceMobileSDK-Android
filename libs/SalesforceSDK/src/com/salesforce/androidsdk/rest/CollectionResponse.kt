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
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * CollectionCreateResponse: Class to represent response for a sobject collection create/update/upsert or delete request.
 */
class CollectionResponse @Throws(JSONException::class) constructor(responseJsonArray: JSONArray) {

    @JvmField
    val subResponses: MutableList<CollectionSubResponse> = mutableListOf()

    init {
        for (i in 0 until responseJsonArray.length()) {
            val result = responseJsonArray.getJSONObject(i)
            subResponses.add(CollectionSubResponse(result))
        }
    }

    class CollectionSubResponse @Throws(JSONException::class) constructor(subResponseJson: JSONObject) {
        @JvmField
        val id: String?
        @JvmField
        val success: Boolean
        @JvmField
        val errors: MutableList<ErrorResponse>
        @JvmField
        val json: JSONObject

        init {
            json = subResponseJson
            id = JSONObjectHelper.optString(subResponseJson, ID)
            success = subResponseJson.getBoolean(SUCCESS)
            errors = mutableListOf()
            for (errorJson in JSONObjectHelper.toList<JSONObject>(subResponseJson.getJSONArray(ERRORS))) {
                errors.add(ErrorResponse(errorJson))
            }
        }

        override fun toString(): String {
            return json.toString()
        }

        companion object {
            const val ID = "id"
            const val SUCCESS = "success"
            const val ERRORS = "errors"
        }
    }

    class ErrorResponse @Throws(JSONException::class) constructor(errorJson: JSONObject) {
        @JvmField
        val statusCode: String
        @JvmField
        val message: String
        @JvmField
        val fields: List<String>
        @JvmField
        val json: JSONObject

        init {
            json = errorJson
            statusCode = errorJson.getString(STATUS_CODE)
            message = errorJson.getString(MESSAGE)
            fields = JSONObjectHelper.toList(errorJson.getJSONArray(FIELDS))
        }

        override fun toString(): String {
            return json.toString()
        }

        companion object {
            const val STATUS_CODE = "statusCode"
            const val MESSAGE = "message"
            const val FIELDS = "fields"
        }
    }
}
