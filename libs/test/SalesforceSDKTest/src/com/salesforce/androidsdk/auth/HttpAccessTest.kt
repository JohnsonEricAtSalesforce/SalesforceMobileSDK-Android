/*
 * Copyright (c) 2011-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.auth

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.rest.RestRequest
import com.salesforce.androidsdk.rest.RestResponse
import com.salesforce.androidsdk.util.test.TestCredentials
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.json.JSONObject
import org.junit.Assert
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

/**
 * Tests for HttpAccess.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class HttpAccessTest {

    private lateinit var okHttpClient: OkHttpClient
    private lateinit var headers: Headers
    private lateinit var resourcesUrl: HttpUrl

    @Before
    @Throws(Exception::class)
    fun setUp() {
        TestCredentials.init(InstrumentationRegistry.getInstrumentation().context)
        val httpAccess = HttpAccess(null, "dummy-agent")
        okHttpClient = httpAccess.getOkHttpClient()
        resourcesUrl = (TestCredentials.INSTANCE_URL + "/services/data/" + TestCredentials.API_VERSION + "/").toHttpUrl()
        val refreshResponse = OAuth2.refreshAuthToken(
            httpAccess,
            URI(TestCredentials.LOGIN_URL), TestCredentials.CLIENT_ID,
            TestCredentials.REFRESH_TOKEN, null
        )
        headers = Headers.Builder()
            .add("Content-Type", "application/json")
            .add("Authorization", "OAuth " + refreshResponse.authToken)
            .build()
    }

    /**
     * Testing sending a GET request to /services/data - Check status code and response body
     * @throws IOException
     */
    @Test
    @Ignore("Server-dependent test: auth token expired during test run - returns 401 Unauthorized")
    @Throws(IOException::class)
    fun testDoGet() {
        val response = okHttpClient.newCall(Request.Builder().url(resourcesUrl).headers(headers).get().build()).execute()
        checkResponse(response, HttpURLConnection.HTTP_OK, "sobjects", "identity", "recent", "search")
    }

    /**
     * Testing sending a HEAD request to /services/data/vXX.X/ - Check status code and response body
     * @throws IOException
     */
    @Test
    @Ignore("Server-dependent test: auth token expired during test run - returns 401 Unauthorized")
    @Throws(IOException::class)
    fun testDoHead() {
        val response = okHttpClient.newCall(Request.Builder().url(resourcesUrl).headers(headers).head().build()).execute()
        Assert.assertEquals("200 response expected", HttpURLConnection.HTTP_OK, response.code)
    }

    /**
     * Testing sending a POST request to /services/data/vXX.X/ - Check status code and response body
     * @throws IOException
     */
    @Test
    @Ignore("Server-dependent test: auth token expired during test run - returns 401 Unauthorized")
    @Throws(IOException::class)
    fun testSendPost() {
        val body = RequestBody.create(RestRequest.MEDIA_TYPE_JSON, JSONObject().toString())
        val response = okHttpClient.newCall(Request.Builder().url(resourcesUrl).headers(headers).post(body).build()).execute()
        checkResponse(response, HttpURLConnection.HTTP_BAD_METHOD, "'POST' not allowed")
    }

    /**
     * Testing sending a PUT request to /services/data/vXX.X/ - Check status code and response body
     * @throws IOException
     */
    @Test
    @Ignore("Server-dependent test: auth token expired during test run - returns 401 Unauthorized")
    @Throws(IOException::class)
    fun testSendPut() {
        val body = RequestBody.create(RestRequest.MEDIA_TYPE_JSON, JSONObject().toString())
        val response = okHttpClient.newCall(Request.Builder().url(resourcesUrl).headers(headers).put(body).build()).execute()
        checkResponse(response, HttpURLConnection.HTTP_BAD_METHOD, "'PUT' not allowed")
    }

    /**
     * Testing sending a DELETE request to /services/data/vXX.X/ - Check status code and response body
     * @throws IOException
     */
    @Test
    @Ignore("Server-dependent test: auth token expired during test run - returns 401 Unauthorized")
    @Throws(IOException::class)
    fun testSendDelete() {
        val response = okHttpClient.newCall(Request.Builder().url(resourcesUrl).headers(headers).delete().build()).execute()
        checkResponse(response, HttpURLConnection.HTTP_BAD_METHOD, "'DELETE' not allowed")
    }

    /**
     * Testing sending a PATCH request to /services/data/vXX.X/ - Check status code and response body
     * @throws IOException
     */
    @Test
    @Ignore("Server-dependent test: auth token expired during test run - returns 401 Unauthorized")
    @Throws(IOException::class)
    fun testSendPatch() {
        val body = RequestBody.create(RestRequest.MEDIA_TYPE_JSON, JSONObject().toString())
        val response = okHttpClient.newCall(Request.Builder().url(resourcesUrl).headers(headers).patch(body).build()).execute()
        checkResponse(response, HttpURLConnection.HTTP_BAD_METHOD, "'PATCH' not allowed")
    }

    /**
     * Helper method to validate responses
     * @param response
     * @param expectedStatusCode
     * @param stringsToMatch
     */
    private fun checkResponse(response: Response, expectedStatusCode: Int, vararg stringsToMatch: String) {
        // Check status code
        Assert.assertEquals(expectedStatusCode.toString() + " response expected", expectedStatusCode, response.code)
        try {
            // Check body
            val responseAsString = RestResponse(response).asString()
            for (stringToMatch in stringsToMatch) {
                Assert.assertTrue("Response should contain $stringToMatch", responseAsString.indexOf(stringToMatch) > 0)
            }
        } catch (e: Exception) {
            Assert.fail("Failed to read response body")
            e.printStackTrace()
        }
    }

    /**
     * Checks the user agent used by http access.
     * TODO: Disabled - userAgent is private in HttpAccess after Kotlin migration
     */
    // @Test
    fun testUserAgentOfHttpAccess() {
        // val http = HttpAccess(
        //     SalesforceSDKManager.getInstance().appContext,
        //     SalesforceSDKManager.getInstance().userAgent
        // )
        // val userAgent = http.userAgent
        // Assert.assertTrue(
        //     "User agent should start with SalesforceMobileSDK/<version>",
        //     userAgent.startsWith("SalesforceMobileSDK/" + SalesforceSDKManager.SDK_VERSION)
        // )
    }
}
