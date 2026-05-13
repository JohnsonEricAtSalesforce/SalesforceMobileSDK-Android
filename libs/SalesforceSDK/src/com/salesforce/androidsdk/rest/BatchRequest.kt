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

import com.salesforce.androidsdk.rest.RestRequest.Companion.BATCH_REQUESTS
import com.salesforce.androidsdk.rest.RestRequest.Companion.HALT_ON_ERROR
import com.salesforce.androidsdk.rest.RestRequest.Companion.METHOD
import com.salesforce.androidsdk.rest.RestRequest.Companion.RICH_INPUT
import com.salesforce.androidsdk.rest.RestRequest.Companion.SERVICES_DATA
import com.salesforce.androidsdk.rest.RestRequest.Companion.URL
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * BatchRequest: Class to represent a batch request.
 */
class BatchRequest @Throws(JSONException::class) constructor(
    apiVersion: String,
    @JvmField val haltOnError: Boolean,
    @JvmField val requests: List<RestRequest>
) : RestRequest(
    RestMethod.POST,
    RestAction.BATCH.getPath(apiVersion),
    computeBatchRequestJson(haltOnError, requests)
) {

    companion object {
        @JvmStatic
        @Throws(JSONException::class)
        private fun computeBatchRequestJson(haltOnError: Boolean, requests: List<RestRequest>): JSONObject {
            val requestsArrayJson = JSONArray()
            for (request in requests) {
                // Note: unfortunately batch sub request and composite sub request differ
                if (!request.path.startsWith(SERVICES_DATA)) {
                    throw RuntimeException("Request not supported in batch: $request")
                }
                val requestJson = JSONObject()
                requestJson.put(METHOD, request.method.toString())
                requestJson.put(URL, request.path.substring(SERVICES_DATA.length))
                requestJson.put(RICH_INPUT, request.requestBodyAsJson)
                requestsArrayJson.put(requestJson)
            }
            val batchRequestJson = JSONObject()
            batchRequestJson.put(BATCH_REQUESTS, requestsArrayJson)
            batchRequestJson.put(HALT_ON_ERROR, haltOnError)
            return batchRequestJson
        }
    }

    /**
     * Builder class for BatchRequest
     */
    class BatchRequestBuilder {
        private var requests = mutableListOf<RestRequest>()
        private var haltOnError: Boolean = false

        fun addRequest(request: RestRequest): BatchRequestBuilder {
            requests.add(request)
            return this
        }

        fun setHaltOnError(b: Boolean): BatchRequestBuilder {
            haltOnError = b
            return this
        }

        @Throws(JSONException::class)
        fun build(apiVersion: String): BatchRequest {
            return BatchRequest(apiVersion, haltOnError, requests)
        }
    }
}
