/*
 * Copyright (c) 2012-present, salesforce.com, inc.
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

import com.salesforce.androidsdk.util.SalesforceSDKLogger
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * RestResponse: Class to represent any REST response.
 */
class RestResponse(private val response: Response) {

    // Populated when "consume" is called
    private var consumed = false
    private var responseAsBytes: ByteArray? = null
    private var responseCharSet: Charset? = null

    // Lazily computed
    private var responseAsString: String? = null
    private var responseAsJSONObject: JSONObject? = null
    private var responseAsJSONArray: JSONArray? = null

    /**
     * Returns all headers associated with this response.
     *
     * @return Map containing all headers.
     */
    fun getAllHeaders(): Map<String, List<String>> {
        return response.headers.toMultimap()
    }

    /**
     * @return HTTP status code of the response
     */
    val statusCode: Int
        get() = response.code

    /**
     * @return true for response with 2xx status codes
     */
    val isSuccess: Boolean
        get() = response.isSuccessful

    /**
     * Fully consume response entity content and closes content stream
     * Must be called before returning control to the UI thread
     * @throws IOException
     */
    @Throws(IOException::class)
    fun consume() {
        if (!consumed) {
            try {
                val body = response.body
                if (body != null) {
                    val mType = body.contentType()
                    responseAsBytes = body.bytes()
                    responseCharSet = if (mType == null || mType.charset() == null) StandardCharsets.UTF_8 else mType.charset()
                    if (responseAsBytes != null && responseAsBytes!!.isNotEmpty()) {
                        responseAsString = String(responseAsBytes!!, responseCharSet!!)
                    }
                } else {
                    responseAsBytes = ByteArray(0)
                    responseCharSet = StandardCharsets.UTF_8
                }
            } finally {
                consumed = true
                response.close()
            }
        }
    }

    /**
     * Fully consume a response and swallow any exceptions thrown during the process.
     * @see RestResponse.consume
     */
    fun consumeQuietly() {
        try {
            consume()
        } catch (e: Exception) {
            SalesforceSDKLogger.e(TAG, "Content could not be written to a byte array", e)
        }
    }

    /**
     * @return byte[] for entire response
     * @throws IOException
     */
    @Throws(IOException::class)
    fun asBytes(): ByteArray? {
        if (responseAsBytes == null) {
            consume()
        }
        return responseAsBytes
    }

    /**
     * Return content type
     * @return value of content-type header or null if header not found
     */
    val contentType: String?
        get() = response.header(CONTENT_TYPE_HEADER_KEY)

    /**
     * String is built the first time the method is called.
     *
     * @return string for entire response
     * @throws IOException
     */
    @Throws(IOException::class)
    fun asString(): String {
        if (responseAsString == null) {
            val bytes = asBytes() // will also compute responseCharSet
            responseAsString = String(bytes!!, responseCharSet!!)
        }
        return responseAsString!!
    }

    /**
     * JSONObject is built the first time the method is called.
     *
     * @return JSONObject for response
     * @throws JSONException
     * @throws IOException
     */
    @Throws(JSONException::class, IOException::class)
    fun asJSONObject(): JSONObject {
        if (responseAsJSONObject == null) {
            responseAsJSONObject = JSONObject(asString()!!)
        }
        return responseAsJSONObject!!
    }

    /**
     * JSONArray is built the first time the method is called.
     *
     * @return JSONObject for response
     * @throws JSONException
     * @throws IOException
     */
    @Throws(JSONException::class, IOException::class)
    fun asJSONArray(): JSONArray {
        if (responseAsJSONArray == null) {
            responseAsJSONArray = JSONArray(asString()!!)
        }
        return responseAsJSONArray!!
    }

    /**
     * Streams the response content. This stream **must** be consumed either
     * by reading from it and calling [InputStream.close],
     * or calling [consume] to discard the contents.
     *
     * If the response is consumed as a stream, [asBytes] will return an empty array,
     * [asString] will return an empty string and both [asJSONArray] and
     * [asJSONObject] will throw exceptions.
     *
     * @return an [InputStream] from the response content
     * @throws IOException if the stream could not be created or has already been consumed
     */
    @Throws(IOException::class)
    fun asInputStream(): InputStream {
        if (consumed) {
            throw IOException("Content has been consumed")
        } else {
            responseAsBytes = ByteArray(0)
            responseCharSet = StandardCharsets.UTF_8
            val stream = response.body!!.byteStream()
            consumed = true
            return stream
        }
    }

    /**
     * Method to get the "raw" response (i.e. the underlying OkHttp3.Response object).
     * null is return if the response was already consumed.
     * It is the responsibility of the application to close the response after consuming it.
     * @return raw response as [Response]
     */
    val rawResponse: Response?
        get() = if (!consumed) {
            response
        } else {
            null
        }

    override fun toString(): String {
        return try {
            asString() ?: ""
        } catch (e: Exception) {
            SalesforceSDKLogger.e(TAG, "Exception thrown while converting to string", e)
            response.toString()
        }
    }

    companion object {
        private const val CONTENT_TYPE_HEADER_KEY = "Content-Type"
        private const val TAG = "RestResponse"

        /**
         * @return true for response with 2xx status codes
         */
        @JvmStatic
        fun isSuccess(statusCode: Int): Boolean {
            return statusCode / 100 == 2
        }
    }
}
