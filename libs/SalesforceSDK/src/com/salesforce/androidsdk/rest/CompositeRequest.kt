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

import com.salesforce.androidsdk.rest.RestRequest.Companion.ALL_OR_NONE
import com.salesforce.androidsdk.rest.RestRequest.Companion.COMPOSITE_REQUEST
import com.salesforce.androidsdk.rest.RestRequest.Companion.REFERENCE_ID
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.LinkedHashMap

/**
 * CompositeRequest: Class to represent a composite request.
 */
class CompositeRequest @Throws(JSONException::class) constructor(
    apiVersion: String,
    @JvmField val allOrNone: Boolean,
    @JvmField val refIdToRequests: LinkedHashMap<String, RestRequest>
) : RestRequest(
    RestMethod.POST,
    RestAction.COMPOSITE.getPath(apiVersion),
    computeCompositeRequestJson(allOrNone, refIdToRequests)
) {

    companion object {
        @JvmStatic
        @Throws(JSONException::class)
        fun computeCompositeRequestJson(
            allOrNone: Boolean,
            refIdToRequests: LinkedHashMap<String, RestRequest>
        ): JSONObject {
            val requestsArrayJson = JSONArray()
            for ((referenceId, request) in refIdToRequests) {
                val requestJson = request.asJSON()
                requestJson.put(REFERENCE_ID, referenceId)
                requestsArrayJson.put(requestJson)
            }
            val compositeRequestJson = JSONObject()
            compositeRequestJson.put(COMPOSITE_REQUEST, requestsArrayJson)
            compositeRequestJson.put(ALL_OR_NONE, allOrNone)
            return compositeRequestJson
        }
    }

    /**
     * Builder class for CompositeRequest
     */
    class CompositeRequestBuilder {
        private var refIdToRequests = LinkedHashMap<String, RestRequest>()
        private var allOrNone: Boolean = false

        fun addRequest(refId: String, request: RestRequest): CompositeRequestBuilder {
            refIdToRequests[refId] = request
            return this
        }

        fun setAllOrNone(b: Boolean): CompositeRequestBuilder {
            allOrNone = b
            return this
        }

        @Throws(JSONException::class)
        fun build(apiVersion: String): CompositeRequest {
            return CompositeRequest(apiVersion, allOrNone, refIdToRequests)
        }
    }
}
