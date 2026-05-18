/*
 * Copyright (c) 2019-present, salesforce.com, inc.
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
 * CompositeResponse: Class to represent response for a CompositeRequest.
 */
class CompositeResponse @Throws(JSONException::class) constructor(responseJson: JSONObject) {

    @JvmField
    val subResponses: MutableList<CompositeSubResponse> = ArrayList()

    init {
        val results = responseJson.getJSONArray(COMPOSITE_RESPONSE)
        for (i in 0 until results.length()) {
            val result = results.getJSONObject(i)
            subResponses.add(CompositeSubResponse(result))
        }
    }

    class CompositeSubResponse @Throws(JSONException::class) constructor(
        @JvmField val json: JSONObject
    ) {
        @JvmField
        val httpHeaders: Map<String, String> = JSONObjectHelper.toMap<String>(json.getJSONObject(HTTP_HEADERS))!!

        @JvmField
        val httpStatusCode: Int = json.getInt(HTTP_STATUS_CODE)

        @JvmField
        val referenceId: String = json.getString(REFERENCE_ID)

        fun bodyAsJSONObject(): JSONObject? {
            return json.optJSONObject(BODY)
        }

        @Throws(JSONException::class)
        fun bodyAsJSONArray(): JSONArray {
            return json.getJSONArray(BODY)
        }

        val isSuccess: Boolean
            get() = httpStatusCode in 200..299

        override fun toString(): String {
            return json.toString()
        }
    }

    companion object {
        const val COMPOSITE_RESPONSE: String = "compositeResponse"
        const val REFERENCE_ID: String = "referenceId"
        const val HTTP_STATUS_CODE: String = "httpStatusCode"
        const val HTTP_HEADERS: String = "httpHeaders"
        const val BODY: String = "body"
    }
}
