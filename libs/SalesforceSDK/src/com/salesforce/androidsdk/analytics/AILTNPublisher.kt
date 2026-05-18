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
package com.salesforce.androidsdk.analytics

import com.salesforce.androidsdk.analytics.model.InstrumentationEvent
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.rest.ApiVersionStrings
import com.salesforce.androidsdk.rest.ClientManager
import com.salesforce.androidsdk.rest.RestRequest
import com.salesforce.androidsdk.rest.RestResponse
import com.salesforce.androidsdk.util.SalesforceSDKLogger
import okhttp3.MediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import okio.BufferedSink
import okio.GzipSink
import okio.buffer
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

/**
 * Network publisher for the AILTN endpoint.
 *
 * @author bhariharan
 */
class AILTNPublisher : AnalyticsPublisher {

    override fun publish(events: JSONArray?): Boolean {
        if (events == null || events.length() == 0) {
            return true
        }

        // Builds the POST body of the request.
        val logLines = JSONArray()
        try {
            for (i in 0 until events.length()) {
                val event = events.optJSONObject(i)
                if (event != null) {
                    val trackingInfo = JSONObject()
                    trackingInfo.put(CODE, AILTN)
                    val data = JSONObject()
                    val schemaType = event.optString(InstrumentationEvent.SCHEMA_TYPE_KEY)
                    data.put(InstrumentationEvent.SCHEMA_TYPE_KEY, schemaType)
                    event.remove(InstrumentationEvent.SCHEMA_TYPE_KEY)
                    data.put(PAYLOAD, event.toString())
                    trackingInfo.put(DATA, data)
                    logLines.put(trackingInfo)
                }
            }
        } catch (e: JSONException) {
            SalesforceSDKLogger.e(TAG, "Exception thrown while constructing event payload", e)
            return false
        }
        return publishLogLines(logLines)
    }

    fun publishLogLines(logLines: JSONArray): Boolean {
        val body = JSONObject()
        try {
            body.put(LOG_LINES, logLines)
        } catch (e: JSONException) {
            SalesforceSDKLogger.e(TAG, "Exception thrown while constructing event payload", e)
            return false
        }
        var restResponse: RestResponse? = null
        try {
            val apiPath = String.format(
                API_PATH,
                ApiVersionStrings.getVersionNumber(SalesforceSDKManager.getInstance().appContext)
            )
            val restClient = SalesforceSDKManager.getInstance().clientManager.peekRestClient()

            // Note: OkHttpClient is always non-null on RestClient in Kotlin.
            // The publisher relies on the RestClient being properly initialized.

            /*
             * There's no easy way to get content length using GZIP interceptors. Some trickery is
             * required to achieve this by adding an additional interceptor to determine content length.
             * See this post for more details: https://github.com/square/okhttp/issues/350#issuecomment-123105641.
             */
            val requestBody = setContentLength(
                gzipCompressedBody(
                    body.toString().toRequestBody(RestRequest.MEDIA_TYPE_JSON)
                )
            )
            val requestHeaders = HashMap<String, String>()
            requestHeaders[CONTENT_ENCODING] = GZIP
            requestHeaders[CONTENT_LENGTH] = requestBody.contentLength().toString()
            val restRequest = RestRequest(
                RestRequest.RestMethod.POST, apiPath,
                requestBody, requestHeaders
            )
            restResponse = restClient.sendSync(restRequest)
        } catch (e: ClientManager.AccountInfoNotFoundException) {
            SalesforceSDKLogger.e(TAG, "Exception thrown while constructing rest client", e)
        } catch (e: IOException) {
            SalesforceSDKLogger.e(TAG, "Exception thrown while making network request", e)
        }
        return restResponse != null && restResponse.isSuccess
    }

    @Throws(IOException::class)
    private fun setContentLength(requestBody: RequestBody): RequestBody {
        val buffer = Buffer()
        requestBody.writeTo(buffer)
        return object : RequestBody() {
            override fun contentType(): MediaType? = requestBody.contentType()

            override fun contentLength(): Long = buffer.size

            @Throws(IOException::class)
            override fun writeTo(sink: BufferedSink) {
                sink.write(buffer.snapshot())
            }
        }
    }

    private fun gzipCompressedBody(body: RequestBody): RequestBody {
        return object : RequestBody() {
            override fun contentType(): MediaType? = body.contentType()

            override fun contentLength(): Long = -1

            @Throws(IOException::class)
            override fun writeTo(sink: BufferedSink) {
                val gzipSink = GzipSink(sink).buffer()
                body.writeTo(gzipSink)
                gzipSink.close()
            }
        }
    }

    companion object {
        private const val TAG = "AILTNPublisher"
        private const val CODE = "code"
        private const val AILTN = "ailtn"
        private const val DATA = "data"
        private const val LOG_LINES = "logLines"
        private const val PAYLOAD = "payload"
        private const val API_PATH = "/services/data/%s/connect/proxy/app-analytics-logging"
        private const val CONTENT_ENCODING = "Content-Encoding"
        private const val CONTENT_LENGTH = "Content-Length"
        private const val GZIP = "gzip"
    }
}
