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
package com.salesforce.androidsdk.phonegap.plugin

import android.text.TextUtils
import android.util.Base64
import com.salesforce.androidsdk.phonegap.ui.SalesforceDroidGapActivity
import com.salesforce.androidsdk.phonegap.util.SalesforceHybridLogger
import com.salesforce.androidsdk.rest.RestClient
import com.salesforce.androidsdk.rest.RestRequest
import com.salesforce.androidsdk.rest.RestResponse
import org.apache.cordova.CallbackContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.net.URI
import java.net.URISyntaxException
import java.net.URLEncoder
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * PhoneGap plugin for native networking.
 *
 * @author bhariharan
 */
class SalesforceNetworkPlugin : ForcePlugin() {

    /**
     * Supported plugin actions that the client can take.
     */
    enum class Action {
        pgSendRequest
    }

    override fun execute(
        actionStr: String,
        jsVersion: JavaScriptPluginVersion,
        args: JSONArray,
        callbackContext: CallbackContext
    ): Boolean {
        return try {
            val action = Action.valueOf(actionStr)
            when (action) {
                Action.pgSendRequest -> {
                    sendRequest(args, callbackContext)
                    true
                }
            }
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    /**
     * Native implementation for "sendRequest" action.
     *
     * @param callbackContext Used when calling back into Javascript.
     */
    protected fun sendRequest(args: JSONArray, callbackContext: CallbackContext) {
        try {
            val request = prepareRestRequest(args)
            val returnBinary = (args.get(0) as JSONObject).optBoolean(RETURN_BINARY, false)
            val doesNotRequireAuth = (args.get(0) as JSONObject).optBoolean(DOES_NOT_REQUIRE_AUTHENTICATION, false)

            // Sends the request.
            val restClient = getRestClient(doesNotRequireAuth)
            if (restClient == null) {
                return
            }
            if (request == null) {
                return
            }
            restClient.sendAsync(request, object : RestClient.AsyncRequestCallback {

                override fun onSuccess(request: RestRequest, response: RestResponse) {
                    try {
                        // Not a 2xx status
                        if (!response.isSuccess()) {
                            val responseObject = JSONObject()
                            responseObject.put("headers", JSONObject(response.getAllHeaders()))
                            responseObject.put("statusCode", response.getStatusCode())
                            responseObject.put("body", parsedResponse(response))
                            val errorObject = JSONObject()
                            errorObject.put("response", responseObject)
                            callbackContext.error(errorObject.toString())
                        }
                        // Binary response
                        else if (returnBinary) {
                            val result = JSONObject()
                            result.put(CONTENT_TYPE, response.getContentType())
                            result.put(ENCODED_BODY, Base64.encodeToString(response.asBytes(), Base64.DEFAULT))
                            callbackContext.success(result)
                        }
                        // Some response
                        else if (response.asBytes()?.isNotEmpty() == true) {
                            val parsedResponse = parsedResponse(response)
                            when (parsedResponse) {
                                is JSONObject -> callbackContext.success(parsedResponse)
                                is JSONArray -> callbackContext.success(parsedResponse)
                                else -> callbackContext.success(parsedResponse as String)
                            }
                        }
                        // No response
                        else {
                            callbackContext.success()
                        }
                    } catch (e: Exception) {
                        SalesforceHybridLogger.e(TAG, "Error while parsing response", e)
                        onError(e)
                    }
                }

                override fun onError(exception: Exception) {
                    val errorObject = JSONObject()
                    try {
                        errorObject.put("error", exception.message)
                    } catch (jsonException: JSONException) {
                        SalesforceHybridLogger.e(TAG, "Error creating error object", jsonException)
                    }
                    callbackContext.error(errorObject.toString())
                }
            })
        } catch (exception: Exception) {
            val errorObject = JSONObject()
            try {
                errorObject.put("error", exception.message)
            } catch (jsonException: JSONException) {
                SalesforceHybridLogger.e(TAG, "Error creating error object", jsonException)
            }
            callbackContext.error(errorObject.toString())
        }
    }

    @Throws(IOException::class)
    private fun parsedResponse(response: RestResponse): Any {
        // Is it a JSONObject?
        val responseAsJSONObject = parseResponseAsJSONObject(response)
        if (responseAsJSONObject != null) {
            return responseAsJSONObject
        }

        // Is it a JSONArray?
        val responseAsJSONArray = parseResponseAsJSONArray(response)
        if (responseAsJSONArray != null) {
            return responseAsJSONArray
        }

        // Otherwise return as string
        return response.asString()
    }

    @Throws(IOException::class)
    private fun parseResponseAsJSONObject(response: RestResponse): JSONObject? {
        return try {
            response.asJSONObject()
        } catch (e: JSONException) {
            // Not a JSON object
            null
        }
    }

    @Throws(IOException::class)
    private fun parseResponseAsJSONArray(response: RestResponse): JSONArray? {
        return try {
            response.asJSONArray()
        } catch (e: JSONException) {
            // Not a JSON array
            null
        }
    }

    @Throws(UnsupportedEncodingException::class, URISyntaxException::class, JSONException::class)
    private fun prepareRestRequest(args: JSONArray): RestRequest? {
        val arg0 = args.optJSONObject(0)
        if (arg0 != null) {
            val method = RestRequest.RestMethod.valueOf(arg0.optString(METHOD_KEY))
            val endPoint = arg0.optString(END_POINT_KEY)
            val path = arg0.optString(PATH_KEY)
            val queryParamString = arg0.optString(QUERY_PARAMS_KEY)
            var queryParams = JSONObject()
            if (!TextUtils.isEmpty(queryParamString)) {
                queryParams = JSONObject(queryParamString)
            }
            val headerParams = arg0.optJSONObject(HEADER_PARAMS_KEY)
            val headerKeys = headerParams.keys()
            val additionalHeaders = HashMap<String, String>()
            while (headerKeys.hasNext()) {
                val headerKeyStr = headerKeys.next()
                if (!TextUtils.isEmpty(headerKeyStr)) {
                    additionalHeaders[headerKeyStr] = headerParams.optString(headerKeyStr)
                }
            }
            val fileParams = arg0.optJSONObject(FILE_PARAMS_KEY)

            // Prepares the request.
            var urlParams = ""
            var requestBody: RequestBody? = null
            if (method == RestRequest.RestMethod.DELETE || method == RestRequest.RestMethod.GET ||
                method == RestRequest.RestMethod.HEAD
            ) {
                urlParams = buildQueryString(queryParams)
            } else {
                requestBody = buildRequestBody(queryParams, fileParams)
            }
            val separator = if (urlParams.isEmpty()) {
                ""
            } else if (path.contains("?")) {
                if (path.endsWith("&")) "" else "&"
            } else {
                "?"
            }
            return RestRequest(method, endPoint + path + separator + urlParams, requestBody, additionalHeaders)
        }
        return null
    }

    private fun getRestClient(doesNotRequireAuth: Boolean): RestClient? {
        val currentActivity = cordova.activity as? SalesforceDroidGapActivity ?: return null
        return if (doesNotRequireAuth) {
            currentActivity.buildClientManager().peekUnauthenticatedRestClient()
        } else {
            currentActivity.restClient
        }
    }

    companion object {
        private const val TAG = "SalesforceNetworkPlugin"
        private const val METHOD_KEY = "method"
        private const val END_POINT_KEY = "endPoint"
        private const val PATH_KEY = "path"
        private const val QUERY_PARAMS_KEY = "queryParams"
        private const val HEADER_PARAMS_KEY = "headerParams"
        private const val FILE_PARAMS_KEY = "fileParams"
        private const val FILE_MIME_TYPE_KEY = "fileMimeType"
        private const val FILE_URL_KEY = "fileUrl"
        private const val FILE_NAME_KEY = "fileName"
        private const val RETURN_BINARY = "returnBinary"
        private const val ENCODED_BODY = "encodedBody"
        private const val CONTENT_TYPE = "contentType"
        private const val DOES_NOT_REQUIRE_AUTHENTICATION = "doesNotRequireAuthentication"

        @Throws(UnsupportedEncodingException::class)
        private fun buildQueryString(params: JSONObject?): String {
            if (params == null || params.length() == 0) {
                return ""
            }
            val sb = StringBuilder()
            val keys = params.keys()
            while (keys.hasNext()) {
                val keyStr = keys.next()
                if (!TextUtils.isEmpty(keyStr)) {
                    sb.append(keyStr).append("=").append(
                        URLEncoder.encode(params.optString(keyStr), RestRequest.UTF_8)
                    ).append("&")
                }
            }
            return sb.toString()
        }

        @Throws(URISyntaxException::class)
        private fun buildRequestBody(params: JSONObject, fileParams: JSONObject?): RequestBody {
            if (fileParams == null || fileParams.length() == 0) {
                return params.toString().toRequestBody(RestRequest.MEDIA_TYPE_JSON)
            } else {
                val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
                val keys = params.keys()
                while (keys.hasNext()) {
                    val keyStr = keys.next()
                    if (!TextUtils.isEmpty(keyStr)) {
                        builder.addFormDataPart(keyStr, params.optString(keyStr))
                    }
                }

                /*
                 * File params expected to be of the form:
                 * {<fileParamNameInPost>: {fileMimeType:<someMimeType>, fileUrl:<fileUrl>, fileName:<fileNameForPost>}}.
                 */
                val fileKeys = fileParams.keys()
                while (fileKeys.hasNext()) {
                    val fileKeyStr = fileKeys.next()
                    if (!TextUtils.isEmpty(fileKeyStr)) {
                        val fileParam = fileParams.optJSONObject(fileKeyStr)
                        if (fileParam != null) {
                            val mimeType = fileParam.optString(FILE_MIME_TYPE_KEY)
                            val name = fileParam.optString(FILE_NAME_KEY)
                            val url = URI(fileParam.optString(FILE_URL_KEY))
                            val file = File(url)
                            val mediaType = mimeType.toMediaTypeOrNull()
                            builder.addFormDataPart(fileKeyStr, name, RequestBody.create(mediaType, file))
                        }
                    }
                }
                return builder.build()
            }
        }
    }
}
