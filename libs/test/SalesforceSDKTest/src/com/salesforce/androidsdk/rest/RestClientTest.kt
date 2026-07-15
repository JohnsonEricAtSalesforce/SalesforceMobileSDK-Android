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
package com.salesforce.androidsdk.rest

import com.salesforce.androidsdk.auth.OAuth2.Companion.FRONTDOOR_URL_KEY
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.accounts.UserAccountBuilder
import com.salesforce.androidsdk.analytics.security.Encryptor
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.HttpAccess
import com.salesforce.androidsdk.auth.OAuth2
import com.salesforce.androidsdk.rest.RestClient.AuthTokenProvider
import com.salesforce.androidsdk.rest.RestClient.ClientInfo
import com.salesforce.androidsdk.rest.RestRequest.RestMethod
import com.salesforce.androidsdk.util.test.TestCredentials
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.LinkedHashMap
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Tests for RestClient
 *
 * Does live calls to a test org
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class RestClientTest {

    companion object {
        private const val ENTITY_NAME_PREFIX = "RestClientTest"
        private const val BAD_TOKEN = "bad-token"

        const val ACCOUNT = "account"
        const val LAST_MODIFIED_DATE = "LastModifiedDate"
        const val NAME = "Name"
        const val LNAME = "name"

        const val TEST_FIRST_NAME = "firstName"
        const val TEST_LAST_NAME = "lastName"
        const val TEST_DISPLAY_NAME = "displayName"
        const val TEST_EMAIL = "test@email.com"
        const val TEST_THUMBNAIL_URL = "http://some.thumbnail.url"
        const val TEST_CUSTOM_KEY = "test_custom_key"
        const val TEST_CUSTOM_VALUE = "test_custom_value"
    }

    private lateinit var clientInfo: ClientInfo
    private lateinit var httpAccess: HttpAccess
    private lateinit var restClient: RestClient
    private lateinit var authToken: String
    private lateinit var instanceUrl: String
    private lateinit var testOauthKeys: MutableList<String>
    private lateinit var testOauthValues: MutableMap<String, String>

    @Before
    fun setUp() {
        TestCredentials.init(InstrumentationRegistry.getInstrumentation().context)
        httpAccess = HttpAccess(null, "dummy-agent")
        val refreshResponse = OAuth2.refreshAuthToken(
            httpAccess,
            URI(TestCredentials.LOGIN_URL), TestCredentials.CLIENT_ID!!,
            TestCredentials.REFRESH_TOKEN!!, null
        )
        authToken = refreshResponse.authToken!!
        instanceUrl = refreshResponse.instanceUrl!!
        testOauthKeys = mutableListOf()
        testOauthKeys.add(TEST_CUSTOM_KEY)
        testOauthValues = mutableMapOf()
        testOauthValues[TEST_CUSTOM_KEY] = TEST_CUSTOM_VALUE
        SalesforceSDKManager.getInstance().additionalOauthKeys = testOauthKeys
        clientInfo = ClientInfo(
            URI(TestCredentials.INSTANCE_URL),
            URI(TestCredentials.LOGIN_URL),
            URI(TestCredentials.IDENTITY_URL),
            TestCredentials.ACCOUNT_NAME, TestCredentials.USERNAME,
            TestCredentials.USER_ID, TestCredentials.ORG_ID, null, null,
            TEST_FIRST_NAME, TEST_LAST_NAME, TEST_DISPLAY_NAME, TEST_EMAIL, TestCredentials.PHOTO_URL,
            TEST_THUMBNAIL_URL, testOauthValues, null, null, null,
            null, null, null, null
        )
        restClient = RestClient(clientInfo, authToken, httpAccess, null)
    }

    @After
    fun tearDown() {
        cleanup()
        SalesforceSDKManager.getInstance().additionalOauthKeys = null
    }

    @Test
    fun testNewWebSocket() {
        val restRequest = RestRequest(
            RestMethod.GET,
            RestRequest.RestEndpoint.LOGIN,
            "/a",
            null as JSONObject?,
            null
        )

        val webSocket = restClient.newWebSocket(
            restClient.buildRequest(restRequest)!!,
            object : WebSocketListener() {}
        )

        Assert.assertNotNull(webSocket)
    }

    /**
     * Testing getClientInfo
     */
    @Test
    fun testGetClientInfo() {
        Assert.assertEquals("Wrong instance url", URI(TestCredentials.INSTANCE_URL), restClient.clientInfo.instanceUrl)
        Assert.assertEquals("Wrong login url", URI(TestCredentials.LOGIN_URL), restClient.clientInfo.loginUrl)
        Assert.assertEquals("Wrong account name", TestCredentials.ACCOUNT_NAME, restClient.clientInfo.accountName)
        Assert.assertEquals("Wrong username", TestCredentials.USERNAME, restClient.clientInfo.username)
        Assert.assertEquals("Wrong userId", TestCredentials.USER_ID, restClient.clientInfo.userId)
        Assert.assertEquals("Wrong orgId", TestCredentials.ORG_ID, restClient.clientInfo.orgId)
        Assert.assertEquals("Wrong firstName", TEST_FIRST_NAME, restClient.clientInfo.firstName)
        Assert.assertEquals("Wrong lastName", TEST_LAST_NAME, restClient.clientInfo.lastName)
        Assert.assertEquals("Wrong displayName", TEST_DISPLAY_NAME, restClient.clientInfo.displayName)
        Assert.assertEquals("Wrong email", TEST_EMAIL, restClient.clientInfo.email)
        Assert.assertEquals("Wrong photoUrl", TestCredentials.PHOTO_URL, restClient.clientInfo.photoUrl)
        Assert.assertEquals("Wrong thumbnailUrl", TEST_THUMBNAIL_URL, restClient.clientInfo.thumbnailUrl)
        Assert.assertEquals("Wrong additional OAuth value", testOauthValues, restClient.clientInfo.additionalOauthValues)
    }

    @Test
    fun testClientInfoResolveUrl() {
        Assert.assertEquals("Wrong url", TestCredentials.INSTANCE_URL + "/a/b/", clientInfo.resolveUrl("a/b/").toString())
        Assert.assertEquals("Wrong url", TestCredentials.INSTANCE_URL + "/a/b/", clientInfo.resolveUrl("/a/b/").toString())
    }

    @Test
    fun testClientInfoResolveUrlForHttpsUrl() {
        Assert.assertEquals("Wrong url", "https://testurl", clientInfo.resolveUrl("https://testurl").toString())
        Assert.assertEquals("Wrong url", "http://testurl", clientInfo.resolveUrl("http://testurl").toString())
        Assert.assertEquals("Wrong url", "HTTPS://testurl", clientInfo.resolveUrl("HTTPS://testurl").toString())
        Assert.assertEquals("Wrong url", "HTTP://testurl", clientInfo.resolveUrl("HTTP://testurl").toString())
    }

    @Test
    fun testClientInfoResolveUrlForCommunityUrl() {
        val info = ClientInfo(
            URI(TestCredentials.INSTANCE_URL),
            URI(TestCredentials.LOGIN_URL),
            URI(TestCredentials.IDENTITY_URL),
            TestCredentials.ACCOUNT_NAME, TestCredentials.USERNAME,
            TestCredentials.USER_ID, TestCredentials.ORG_ID, null,
            TestCredentials.COMMUNITY_URL, null, null, null,
            null, null, null, testOauthValues, null,
            null, null, null, null, null, null
        )
        Assert.assertEquals("Wrong url", TestCredentials.COMMUNITY_URL + "/a/b/", info.resolveUrl("a/b/").toString())
        Assert.assertEquals("Wrong url", TestCredentials.COMMUNITY_URL + "/a/b/", info.resolveUrl("/a/b/").toString())
    }

    @Test
    fun testClientInfoResolveRequestWithLoginEndpoint() {
        val r = RestRequest(RestMethod.GET, RestRequest.RestEndpoint.LOGIN, "/a", null as JSONObject?, null)
        Assert.assertEquals("URL should have login host endpoint", TestCredentials.LOGIN_URL + "/a", clientInfo.resolveUrl(r).toString())
    }

    @Test
    fun testClientInfoResolveRequestWithInstanceEndpoint() {
        val r = RestRequest(RestMethod.GET, RestRequest.RestEndpoint.INSTANCE, "/a", null as JSONObject?, null)
        Assert.assertEquals("URL should have instance host endpoint", TestCredentials.INSTANCE_URL + "/a", clientInfo.resolveUrl(r).toString())
    }

    @Test
    fun testClientInfoResolveRequestWithCommunity() {
        val info = ClientInfo(
            URI(TestCredentials.INSTANCE_URL),
            URI(TestCredentials.LOGIN_URL),
            URI(TestCredentials.IDENTITY_URL),
            TestCredentials.ACCOUNT_NAME, TestCredentials.USERNAME,
            TestCredentials.USER_ID, TestCredentials.ORG_ID, null,
            TestCredentials.COMMUNITY_URL, null, null, null,
            null, null, null, testOauthValues, null,
            null, null, null, null, null, null
        )
        val r = RestRequest(RestMethod.GET, RestRequest.RestEndpoint.LOGIN, "/a", null as JSONObject?, null)
        Assert.assertEquals(
            "Community URL should take precedence over login or instance endpoint",
            TestCredentials.COMMUNITY_URL + "/a", info.resolveUrl(r).toString()
        )
    }

    @Test
    fun testGetInstanceUrlForCommunity() {
        val info = ClientInfo(
            URI(TestCredentials.INSTANCE_URL),
            URI(TestCredentials.LOGIN_URL),
            URI(TestCredentials.IDENTITY_URL),
            TestCredentials.ACCOUNT_NAME, TestCredentials.USERNAME,
            TestCredentials.USER_ID, TestCredentials.ORG_ID, null,
            TestCredentials.COMMUNITY_URL, null, null, null,
            null, null, null, testOauthValues, null,
            null, null, null, null, null, null
        )
        Assert.assertEquals("Wrong url", TestCredentials.COMMUNITY_URL, info.getInstanceUrlAsString())
    }

    @Test
    fun testGetInstanceUrl() {
        Assert.assertEquals("Wrong url", TestCredentials.INSTANCE_URL, clientInfo.getInstanceUrlAsString())
    }

    /**
     * Testing getAuthToken
     */
    @Test
    fun testGetAuthToken() {
        Assert.assertEquals("Wrong auth token", authToken, restClient.authToken)
    }

    /**
     * Testing a call with a bad auth token when restClient has no token provider
     * Expect a 401.
     */
    @Test
    fun testCallWithBadAuthToken() {
        RestClient.clearCaches()
        val restClient = RestClient(clientInfo, BAD_TOKEN, httpAccess, null)
        val response = restClient.sendSync(RestRequest.getRequestForResources(TestCredentials.API_VERSION!!))
        Assert.assertFalse("Expected error", response.isSuccess)
        checkResponse(response, HttpURLConnection.HTTP_UNAUTHORIZED, true)
    }

    /**
     * Testing a call with a bad auth token when restClient has a token provider
     * Expect token provider to be invoked and new token to be used.
     */
    @Test
    fun testCallWithBadTokenAndTokenProvider() {
        RestClient.clearCaches()
        val authTokenProvider = object : AuthTokenProvider {
            override fun getNewAuthToken(): String {
                return authToken
            }

            override fun getRefreshToken(): String? {
                return null
            }

            override fun getLastRefreshTime(): Long {
                return -1
            }

            override fun getInstanceUrl(): String { return instanceUrl }
        }
        val restClient = RestClient(clientInfo, BAD_TOKEN, httpAccess, authTokenProvider)
        Assert.assertEquals("RestClient should be using the bad token initially", BAD_TOKEN, restClient.authToken)
        val response = restClient.sendSync(RestRequest.getRequestForResources(TestCredentials.API_VERSION!!))
        Assert.assertEquals("RestClient should now be using the good token", authToken, restClient.authToken)
        Assert.assertTrue("Expected success", response.isSuccess)
        checkResponse(response, HttpURLConnection.HTTP_OK, false)
    }

    /**
     * Testing a call with a bad auth token when restClient has a token provider
     * Expect token provider to be invoked and new token to be used and a new instance url to be returned.
     */
    @Test
    fun testCallWithBadInstanceUrl() {
        RestClient.clearCaches()
        val authTokenProvider = object : AuthTokenProvider {
            override fun getNewAuthToken(): String {
                return authToken
            }

            override fun getRefreshToken(): String? {
                return null
            }

            override fun getLastRefreshTime(): Long {
                return -1
            }

            override fun getInstanceUrl(): String { return instanceUrl }
        }
        val restClient = RestClient(clientInfo, BAD_TOKEN, httpAccess, authTokenProvider)
        Assert.assertEquals("RestClient has bad instance url", URI(TestCredentials.INSTANCE_URL), restClient.clientInfo.instanceUrl)
        val response = restClient.sendSync(RestRequest.getRequestForResources(TestCredentials.API_VERSION!!))
        Assert.assertEquals("RestClient should now have the correct instance url", URI(instanceUrl), restClient.clientInfo.instanceUrl)
        Assert.assertTrue("Expected success", response.isSuccess)
        checkResponse(response, HttpURLConnection.HTTP_OK, false)
    }

    /**
     * Testing RestResponse:getRawResponse
     */
    @Test
    fun testGetRawResponse() {
        val restClient = RestClient(clientInfo, BAD_TOKEN, httpAccess, null)
        val response = restClient.sendSync(RestRequest.getRequestForVersions())
        val rawResponse = response.rawResponse!!
        Assert.assertEquals(200, rawResponse.code)
        Assert.assertEquals("application/json;charset=UTF-8", rawResponse.header("Content-Type"))
        checkKeys(JSONArray(rawResponse.body!!.string()).getJSONObject(0), "label", "url", "version")
        rawResponse.close()
    }

    /**
     * Testing a get single access call to the server - check response
     */
    @Test
    fun testGetSingleAccess() {
        val response = restClient.sendSync(RestRequest.getRequestForSingleAccess("abc/def"))
        checkResponse(response, HttpURLConnection.HTTP_OK, false)
        checkKeys(response.asJSONObject(), FRONTDOOR_URL_KEY)
    }

    /**
     * Testing a get versions call to the server - check response
     */
    @Test
    fun testGetVersions() {
        // We don't need to be authenticated
        val restClient = RestClient(clientInfo, BAD_TOKEN, httpAccess, null)
        val response = restClient.sendSync(RestRequest.getRequestForVersions())
        checkResponse(response, HttpURLConnection.HTTP_OK, true)
        checkKeys(response.asJSONArray().getJSONObject(0), "label", "url", "version")
    }

    /**
     * Testing a get resources call to the server - check response
     */
    @Test
    fun testGetResources() {
        val response = restClient.sendSync(RestRequest.getRequestForResources(TestCredentials.API_VERSION!!))
        checkResponse(response, HttpURLConnection.HTTP_OK, false)
        checkKeys(response.asJSONObject(), "sobjects", "search", "recent")
    }

    /**
     * Testing a get resources async call to the server - check response
     */
    @Test
    fun testGetResourcesAsync() {
        val response = sendAsync(restClient, RestRequest.getRequestForResources(TestCredentials.API_VERSION!!))
        checkResponse(response, HttpURLConnection.HTTP_OK, false)
        checkKeys(response!!.asJSONObject(), "sobjects", "search", "recent")
    }

    /**
     * Testing a describe global call to the server - check response
     */
    @Test
    fun testDescribeGlobal() {
        val response = restClient.sendSync(RestRequest.getRequestForDescribeGlobal(TestCredentials.API_VERSION!!))
        checkResponse(response, HttpURLConnection.HTTP_OK, false)
        val jsonResponse = response.asJSONObject()
        checkKeys(jsonResponse, "encoding", "maxBatchSize", "sobjects")
        checkKeys(jsonResponse.getJSONArray("sobjects").getJSONObject(0), LNAME, "label", "custom", "keyPrefix")
    }

    /**
     * Testing a describe global async call to the server - check response
     */
    @Test
    fun testDescribeGlobalAsync() {
        val response = sendAsync(restClient, RestRequest.getRequestForDescribeGlobal(TestCredentials.API_VERSION!!))
        checkResponse(response, HttpURLConnection.HTTP_OK, false)
        val jsonResponse = response!!.asJSONObject()
        checkKeys(jsonResponse, "encoding", "maxBatchSize", "sobjects")
        checkKeys(jsonResponse.getJSONArray("sobjects").getJSONObject(0), LNAME, "label", "custom", "keyPrefix")
    }

    /**
     * Testing a metadata call to the server - check response
     */
    @Test
    fun testMetadata() {
        val response = restClient.sendSync(RestRequest.getRequestForMetadata(TestCredentials.API_VERSION!!, ACCOUNT))
        checkResponse(response, HttpURLConnection.HTTP_OK, false)
        val jsonResponse = response.asJSONObject()
        checkKeys(jsonResponse, "objectDescribe", "recentItems")
        checkKeys(jsonResponse.getJSONObject("objectDescribe"), LNAME, "label", "keyPrefix")
        Assert.assertEquals("Wrong object name", "Account", jsonResponse.getJSONObject("objectDescribe").getString(LNAME))
    }

    /**
     * Testing a describe call to the server - check response
     */
    @Test
    fun testDescribe() {
        val response = restClient.sendSync(RestRequest.getRequestForDescribe(TestCredentials.API_VERSION!!, ACCOUNT))
        checkResponse(response, HttpURLConnection.HTTP_OK, false)
        val jsonResponse = response.asJSONObject()
        checkKeys(jsonResponse, LNAME, "fields", "urls", "label")
        Assert.assertEquals("Wrong object name", "Account", jsonResponse.getString(LNAME))
    }

    /**
     * Testing a create call to the server - check response
     */
    @Test
    fun testCreate() {
        val fields = hashMapOf<String, Any>()
        val newAccountName = ENTITY_NAME_PREFIX + System.nanoTime()
        fields[NAME] = newAccountName
        val response = restClient.sendSync(RestRequest.getRequestForCreate(TestCredentials.API_VERSION!!, ACCOUNT, fields))
        val jsonResponse = response.asJSONObject()
        checkKeys(jsonResponse, "id", "errors", "success")
        Assert.assertTrue("Create failed", jsonResponse.getBoolean("success"))
    }

    /**
     * Testing a retrieve call to the server.
     * Create new account then retrieve it.
     */
    @Test
    fun testRetrieve() {
        val fields = listOf(NAME, "ownerId")
        val newAccountIdName = createAccount()
        val response = restClient.sendSync(RestRequest.getRequestForRetrieve(TestCredentials.API_VERSION!!, ACCOUNT, newAccountIdName.id, fields))
        checkResponse(response, HttpURLConnection.HTTP_OK, false)
        val jsonResponse = response.asJSONObject()
        checkKeys(jsonResponse, "attributes", NAME, "OwnerId", "Id")
        Assert.assertEquals("Wrong row returned", newAccountIdName.name, jsonResponse.getString(NAME))
    }

    /**
     * Testing an update call to the server.
     * Create new account then update it then get it back
     */
    @Test
    fun testUpdate() {
        // Create
        val newAccountIdName = createAccount()

        // Update
        val fields = hashMapOf<String, Any>()
        val updatedAccountName = ENTITY_NAME_PREFIX + "-" + System.nanoTime()
        fields[NAME] = updatedAccountName
        val updateResponse = restClient.sendSync(RestRequest.getRequestForUpdate(TestCredentials.API_VERSION!!, ACCOUNT, newAccountIdName.id, fields))
        Assert.assertTrue("Update failed", updateResponse.isSuccess)

        // Retrieve - expect updated name
        val response = restClient.sendSync(RestRequest.getRequestForRetrieve(TestCredentials.API_VERSION!!, ACCOUNT, newAccountIdName.id, listOf(NAME)))
        Assert.assertEquals("Wrong row returned", updatedAccountName, response.asJSONObject().getString(NAME))
    }

    /**
     * Testing upsert calls to the server.
     * Create new account using a first upsert call then update it with a second upsert call then get it back
     */
    @Test
    fun testUpsert() {
        // Create with upsert call
        var fields = hashMapOf<String, Any>()
        val accountName = ENTITY_NAME_PREFIX + "-" + System.nanoTime()
        fields[NAME] = accountName
        var response = restClient.sendSync(RestRequest.getRequestForUpsert(TestCredentials.API_VERSION!!, ACCOUNT, "Id", null, fields))
        Assert.assertTrue("Create with upsert failed", response.isSuccess)
        val accountId = response.asJSONObject().getString("id")

        // Update with upsert call
        fields = hashMapOf()
        val updatedAccountName = ENTITY_NAME_PREFIX + "-" + System.nanoTime()
        fields[NAME] = updatedAccountName
        response = restClient.sendSync(RestRequest.getRequestForUpsert(TestCredentials.API_VERSION!!, ACCOUNT, "Id", accountId, fields))
        Assert.assertTrue("Update with upsert failed", response.isSuccess)

        // Retrieve - expect updated name
        response = restClient.sendSync(RestRequest.getRequestForRetrieve(TestCredentials.API_VERSION!!, ACCOUNT, accountId, listOf(NAME)))
        Assert.assertEquals("Wrong row returned", updatedAccountName, response.asJSONObject().getString(NAME))
    }

    /**
     * Testing update calls to the server with if-unmodified-since.
     * Create new account,
     * then update it with created date for unmodified since date (should update)
     * then update it again with created date for unmodified since date (should not update)
     */
    @Test
    fun testUpdateWithIfUnmodifiedSince() {
        val fields = hashMapOf<String, Any>()
        val pastDate = Date(Date().time - 3600 * 1000) // an hour ago

        // Create
        val newAccountIdName = createAccount()
        val originalName = newAccountIdName.name

        // Retrieve to get created date
        var retrieveResponse = restClient.sendSync(RestRequest.getRequestForRetrieve(TestCredentials.API_VERSION!!, ACCOUNT, newAccountIdName.id, listOf(LAST_MODIFIED_DATE)))
        val createdDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").parse(retrieveResponse.asJSONObject().getString(LAST_MODIFIED_DATE))

        // Wait a bit
        Thread.sleep(1000)

        // Update with if-unmodified-since with createdDate - should update
        val updatedName = originalName + "_upd"
        fields[NAME] = updatedName
        val updateResponse = restClient.sendSync(RestRequest.getRequestForUpdate(TestCredentials.API_VERSION!!, ACCOUNT, newAccountIdName.id, fields, createdDate))
        Assert.assertTrue("Update failed", updateResponse.isSuccess)

        // Retrieve - expect updated name
        retrieveResponse = restClient.sendSync(RestRequest.getRequestForRetrieve(TestCredentials.API_VERSION!!, ACCOUNT, newAccountIdName.id, listOf(NAME)))
        Assert.assertEquals("Wrong row returned", updatedName, retrieveResponse.asJSONObject().getString(NAME))

        // Second update with if-unmodified-since with created date - should not update
        val blockedUpdatedName = originalName + "_blocked_upd"
        fields[NAME] = blockedUpdatedName
        val blockedUpdateResponse = restClient.sendSync(RestRequest.getRequestForUpdate(TestCredentials.API_VERSION!!, ACCOUNT, newAccountIdName.id, fields, createdDate))
        Assert.assertEquals("Expected 412", HttpURLConnection.HTTP_PRECON_FAILED, blockedUpdateResponse.statusCode)

        // Retrieve - expect name from first update
        retrieveResponse = restClient.sendSync(RestRequest.getRequestForRetrieve(TestCredentials.API_VERSION!!, ACCOUNT, newAccountIdName.id, listOf(NAME)))
        Assert.assertEquals("Wrong row returned", updatedName, retrieveResponse.asJSONObject().getString(NAME))
    }

    /**
     * Testing a delete call to the server.
     * Create new account then delete it then try to retrieve it again (expect 404).
     */
    @Test
    fun testDelete() {
        // Create
        val newAccountIdName = createAccount()

        // Delete
        val deleteResponse = restClient.sendSync(RestRequest.getRequestForDelete(TestCredentials.API_VERSION!!, ACCOUNT, newAccountIdName.id))
        Assert.assertTrue("Delete failed", deleteResponse.isSuccess)

        // Retrieve - expect 404
        val fields = listOf(NAME)
        val response = restClient.sendSync(RestRequest.getRequestForRetrieve(TestCredentials.API_VERSION!!, ACCOUNT, newAccountIdName.id, fields))
        Assert.assertEquals("404 was expected", HttpURLConnection.HTTP_NOT_FOUND, response.statusCode)
    }

    /**
     * Testing a query call to the server.
     * Create new account then look for it using soql.
     */
    @Test
    fun testQuery() {
        val newAccountIdName = createAccount()
        val response = restClient.sendSync(RestRequest.getRequestForQuery(TestCredentials.API_VERSION!!, "select name from account where id = '${newAccountIdName.id}'"))
        checkResponse(response, HttpURLConnection.HTTP_OK, false)
        val jsonResponse = response.asJSONObject()
        checkKeys(jsonResponse, "done", "totalSize", "records")
        Assert.assertEquals("Expected one row", 1, jsonResponse.getInt("totalSize"))
        Assert.assertEquals("Wrong row returned", newAccountIdName.name, jsonResponse.getJSONArray("records").getJSONObject(0).get(NAME))
    }

    /**
     * Testing a query all call to the server.
     * Create new account then look for it using soql.
     */
    @Test
    fun testQueryAll() {
        // Create 3 accounts
        val idNames = createAccounts(3, "-testQueryAll-")
        // Delete 1 account
        restClient.sendSync(RestRequest.getRequestForDelete(TestCredentials.API_VERSION!!, ACCOUNT, idNames[0].id))

        val soql = "select name from account where Name like '$ENTITY_NAME_PREFIX-testQueryAll-%' order by Name"

        // Query - expect 2 records
        val responseQuery = restClient.sendSync(RestRequest.getRequestForQuery(TestCredentials.API_VERSION!!, soql))
        checkResponse(responseQuery, HttpURLConnection.HTTP_OK, false)
        val jsonResponseQuery = responseQuery.asJSONObject()
        checkKeys(jsonResponseQuery, "done", "totalSize", "records")
        Assert.assertEquals("Expected three rows", 2, jsonResponseQuery.getInt("totalSize"))
        Assert.assertEquals("Wrong row returned", idNames[1].name, jsonResponseQuery.getJSONArray("records").getJSONObject(0).get(NAME))
        Assert.assertEquals("Wrong row returned", idNames[2].name, jsonResponseQuery.getJSONArray("records").getJSONObject(1).get(NAME))

        // Query all - expect 3 records
        val responseQueryAll = restClient.sendSync(RestRequest.getRequestForQueryAll(TestCredentials.API_VERSION!!, soql))
        checkResponse(responseQueryAll, HttpURLConnection.HTTP_OK, false)
        val jsonResponseQueryAll = responseQueryAll.asJSONObject()
        checkKeys(jsonResponseQueryAll, "done", "totalSize", "records")
        Assert.assertTrue("Expected three rows (or more)", jsonResponseQueryAll.getInt("totalSize") >= 3)
        val records = jsonResponseQueryAll.getJSONArray("records")
        val names = mutableListOf<String>()
        for (i in 0 until records.length()) {
            names.add(records.getJSONObject(i).getString(NAME))
        }

        Assert.assertTrue("Row not foumd", names.contains(idNames[0].name))
        Assert.assertTrue("Row not foumd", names.contains(idNames[1].name))
        Assert.assertTrue("Row not foumd", names.contains(idNames[2].name))
    }

    /**
     * Testing a query call to the server which specifies a batch size.
     * Create new account then look for it using soql.
     */
    @Test(timeout = 180000) // 3 minutes - test creates 201 accounts which takes time, especially in Firebase Test Lab
    fun testQueryWithBatchSize() {
        cleanup()
        val idNames = createAccounts(201, "-testWithBatchSize-")
        val soql = "select name from account where Name like '$ENTITY_NAME_PREFIX-testWithBatchSize-%'"

        // SOQL without batch size
        val requestNoBatchSizeSpecified = RestRequest.getRequestForQuery(TestCredentials.API_VERSION!!, soql)
        Assert.assertNull(requestNoBatchSizeSpecified.additionalHttpHeaders)
        val responseNoBatchSizeSpecified = restClient.sendSync(requestNoBatchSizeSpecified)
        checkResponse(responseNoBatchSizeSpecified, HttpURLConnection.HTTP_OK, false)
        val jsonResponseNoBatchSizeSpecified = responseNoBatchSizeSpecified.asJSONObject()
        checkKeys(jsonResponseNoBatchSizeSpecified, "done", "totalSize", "records")
        Assert.assertEquals("201 rows should match", 201, jsonResponseNoBatchSizeSpecified.getInt("totalSize"))
        Assert.assertEquals("201 rows should have been returned", 201, jsonResponseNoBatchSizeSpecified.getJSONArray("records").length())

        // SOQL with batch size
        val requestWithBatchSizeSpecified = RestRequest.getRequestForQuery(TestCredentials.API_VERSION!!, soql, 200)
        Assert.assertEquals("batchSize=200", requestWithBatchSizeSpecified.additionalHttpHeaders?.get(RestRequest.SFORCE_QUERY_OPTIONS))
        val responseWithBatchSizeSpecified = restClient.sendSync(requestWithBatchSizeSpecified)
        checkResponse(responseWithBatchSizeSpecified, HttpURLConnection.HTTP_OK, false)
        val jsonResponseWithBatchSizeSpecified = responseWithBatchSizeSpecified.asJSONObject()
        checkKeys(jsonResponseWithBatchSizeSpecified, "done", "totalSize", "records")
        Assert.assertEquals("201 rows should match", 201, jsonResponseWithBatchSizeSpecified.getInt("totalSize"))
        Assert.assertEquals("200 rows should have been returned", 200, jsonResponseWithBatchSizeSpecified.getJSONArray("records").length())
    }

    /**
     * Testing that calling resume more than once on a RestResponse doesn't throw an exception
     */
    @Test
    fun testDoubleConsume() {
        val response = restClient.sendSync(RestRequest.getRequestForMetadata(TestCredentials.API_VERSION!!, ACCOUNT))
        checkResponse(response, HttpURLConnection.HTTP_OK, false)
        try {
            response.consume()
            response.consume()
        } catch (e: IllegalStateException) {
            Assert.fail("Calling consume should not have thrown an exception")
        }
    }

    /**
     * Testing a search call to the server.
     * Create new account then ensure the results of SOSL don't have an error.
     */
    @Test
    fun testSearch() {
        createAccount()
        createAccount()
        val response = restClient.sendSync(RestRequest.getRequestForSearch(TestCredentials.API_VERSION!!, "find {$ENTITY_NAME_PREFIX}"))
        val jsonResults = response.asJSONObject().getJSONArray("searchRecords")
        Assert.assertNotNull("Results expected", jsonResults)
    }

    /**
     * Testing doing a sync request with a RestClient that uses an UnauthenticatedClientInfo
     */
    @Test
    fun testRestClientUnauthenticatedClientInfo() {
        val unauthenticatedRestClient = RestClient(RestClient.UnauthenticatedClientInfo(), null, HttpAccess.DEFAULT!!, null)
        val request = RestRequest(RestMethod.GET, "https://na1.salesforce.com/services/data")
        val response = unauthenticatedRestClient.sendSync(request)
        Assert.assertNull(
            "Unauthenticated requests should not have auth header.",
            response.rawResponse!!.request.headers["Authentication"]
        )
        checkResponse(response, HttpURLConnection.HTTP_OK, true)
        val jsonResponse = response.asJSONArray()
        checkKeys(jsonResponse.getJSONObject(0), "label", "url", "version")
    }

    /**
     * Testing doing an async request with a RestClient that uses an UnauthenticatedClientInfo
     */
    @Test
    fun testRestClientUnauthenticatedClientInfoAsync() {
        val unauthenticatedRestClient = RestClient(RestClient.UnauthenticatedClientInfo(), null, HttpAccess.DEFAULT!!, null)
        val request = RestRequest(RestMethod.GET, "https://na1.salesforce.com/services/data")
        val response = sendAsync(unauthenticatedRestClient, request)
        Assert.assertNull(
            "Unauthenticated requests should not have auth header.",
            response!!.rawResponse!!.request.headers["Authentication"]
        )
        checkResponse(response, HttpURLConnection.HTTP_OK, true)
        val jsonResponse = response.asJSONArray()
        checkKeys(jsonResponse.getJSONObject(0), "label", "url", "version")
    }

    /**
     * Tests if a stream from RestResponse.asInputStream() is readable.
     */
    @Test
    fun testResponseStreamIsReadable() {
        val response = getStreamTestResponse()
        try {
            val inputStream = response.asInputStream()
            assertStreamTestResponseStreamIsValid(inputStream)
        } catch (e: IOException) {
            Assert.fail("The InputStream should be readable and an IOException should not have been thrown")
        } catch (e: JSONException) {
            Assert.fail("Valid JSON data should have been returned")
        } finally {
            response.consumeQuietly()
        }
    }

    /**
     * Tests if a stream from RestResponse.asInputStream() is consumed (according to the REST client) by fully reading the stream.
     */
    @Test
    fun testResponseStreamConsumedByReadingStream() {
        val response = getStreamTestResponse()
        try {
            val inputStream = response.asInputStream()
            Encryptor.getStringFromStream(inputStream)
        } catch (e: IOException) {
            Assert.fail("The InputStream should be readable and an IOException should not have been thrown")
        }

        // We read the entire stream but forgot to call consume() or consumeQuietly() - can another REST call be made?
        val anotherResponse = getStreamTestResponse()
        Assert.assertNotNull(anotherResponse)
    }

    /**
     * Tests that a stream from RestResponse.asInputStream() cannot be read from twice.
     */
    @Test
    fun testResponseStreamCannotBeReadTwice() {
        val response = getStreamTestResponse()
        try {
            val inputStream = response.asInputStream()
            Encryptor.getStringFromStream(inputStream)
        } catch (e: IOException) {
            Assert.fail("The InputStream should be readable and an IOException should not have been thrown")
        }
        try {
            response.asInputStream()
            Assert.fail("An IOException should have been thrown while trying to read the InputStream a second time")
        } catch (e: IOException) {
            // Expected
        } finally {
            response.consumeQuietly()
        }
    }

    /**
     * Tests that RestResponse's accessor methods (like RestResponse.asBytes()) do not return valid data if the response is streamed first.
     */
    @Test
    fun testOtherAccessorsNotAvailableAfterResponseStreaming() {
        val response = getStreamTestResponse()
        val testAccessorsNotAccessible = Runnable {
            try {
                // The other accessors should not return valid data as soon as the stream is opened
                Assert.assertNotNull(response.asBytes())
                Assert.assertEquals("asBytes() array should be empty", 0, response.asBytes()!!.size)
                Assert.assertEquals("asString() should return the empty string", "", response.asString())

                try {
                    Assert.assertNull(response.asJSONObject())
                    Assert.fail("asJSONObject() should fail")
                } catch (e: JSONException) {
                    // Expected
                }
                try {
                    Assert.assertNull(response.asJSONArray())
                    Assert.fail("asJSONArray() should fail")
                } catch (e: JSONException) {
                    // Expected
                }
            } catch (e: IOException) {
                Assert.fail("IOException not expected")
            }
        }
        try {
            response.asInputStream()
            testAccessorsNotAccessible.run()
        } catch (e: IOException) {
            Assert.fail("The InputStream should be readable and an IOException should not have been thrown")
        } finally {
            response.consumeQuietly()
        }

        // Ensure that consuming the stream doesn't make the accessors accessible again
        testAccessorsNotAccessible.run()
    }

    /**
     * Tests that any call to RestResponse's accessor methods prevent the response data from being streamed via RestResponse.asInputStream().
     */
    @Test
    fun testAccessorMethodsPreventResponseStreaming() {
        val response = getStreamTestResponse()
        response.asBytes()
        try {
            response.asInputStream()
            Assert.fail("The InputStream should not be readable after an accessor method is called")
        } catch (e: IOException) {
            // Expected
        } finally {
            response.consumeQuietly()
        }
    }

    /**
     * Test for batch request
     *
     * Run a batch request that:
     * - creates an account,
     * - creates a contact,
     * - run a query that should return newly created account
     * - run a query that should return newly created contact
     */
    @Test
    fun testBatchRequest() {
        val accountFields = hashMapOf<String, Any>()
        val accountName = ENTITY_NAME_PREFIX + System.nanoTime()
        accountFields[NAME] = accountName
        val firstRequest = RestRequest.getRequestForCreate(TestCredentials.API_VERSION!!, ACCOUNT, accountFields)
        val contactFields = hashMapOf<String, Any>()
        val contactName = ENTITY_NAME_PREFIX + System.nanoTime()
        contactFields["LastName"] = contactName
        val secondRequest = RestRequest.getRequestForCreate(TestCredentials.API_VERSION!!, "contact", contactFields)
        val thirdRequest = RestRequest.getRequestForQuery(TestCredentials.API_VERSION!!, "select Id from Account where Name = '$accountName'")
        val fourthRequest = RestRequest.getRequestForQuery(TestCredentials.API_VERSION!!, "select Id from Contact where Name = '$contactName'")

        // Build batch request
        val batchRequest = RestRequest.getBatchRequest(TestCredentials.API_VERSION!!, false, listOf(firstRequest, secondRequest, thirdRequest, fourthRequest))

        // Send batch request
        val response = restClient.sendSync(batchRequest)

        // Checking response
        val jsonResponse = response.asJSONObject()
        checkKeys(jsonResponse, "hasErrors", "results")
        Assert.assertFalse("Batch had errors", jsonResponse.getBoolean("hasErrors"))
        val jsonResults = jsonResponse.getJSONArray("results")
        Assert.assertEquals("Wrong number of results", 4, jsonResults.length())
        Assert.assertEquals("Wrong status for first request", HttpURLConnection.HTTP_CREATED, jsonResults.getJSONObject(0).getInt("statusCode"))
        Assert.assertEquals("Wrong status for second request", HttpURLConnection.HTTP_CREATED, jsonResults.getJSONObject(1).getInt("statusCode"))
        Assert.assertEquals("Wrong status for third request", HttpURLConnection.HTTP_OK, jsonResults.getJSONObject(2).getInt("statusCode"))
        Assert.assertEquals("Wrong status for fourth request", HttpURLConnection.HTTP_OK, jsonResults.getJSONObject(3).getInt("statusCode"))

        // Queries should have returned ids of newly created account and contact
        val accountId = jsonResults.getJSONObject(0).getJSONObject("result").getString("id")
        val contactId = jsonResults.getJSONObject(1).getJSONObject("result").getString("id")
        val idFromFirstQuery = jsonResults.getJSONObject(2).getJSONObject("result").getJSONArray("records").getJSONObject(0).getString("Id")
        val idFromSecondQuery = jsonResults.getJSONObject(3).getJSONObject("result").getJSONArray("records").getJSONObject(0).getString("Id")
        Assert.assertEquals("Account id not returned by query", accountId, idFromFirstQuery)
        Assert.assertEquals("Contact id not returned by query", contactId, idFromSecondQuery)
    }

    /**
     * Test for composite request
     *
     * Run a composite request that:
     * - creates an account,
     * - creates a contact (with newly created account as parent),
     * - run a query that should return newly created account and contact
     */
    @Test
    fun testCompositeRequest() {
        val accountFields = hashMapOf<String, Any>()
        val accountName = ENTITY_NAME_PREFIX + System.nanoTime()
        accountFields[NAME] = accountName
        val firstRequest = RestRequest.getRequestForCreate(TestCredentials.API_VERSION!!, ACCOUNT, accountFields)
        val contactFields = hashMapOf<String, Any>()
        val contactName = ENTITY_NAME_PREFIX + System.nanoTime()
        contactFields["LastName"] = contactName
        contactFields["AccountId"] = "@{refAccount.id}"
        val secondRequest = RestRequest.getRequestForCreate(TestCredentials.API_VERSION!!, "contact", contactFields)
        val thirdRequest = RestRequest.getRequestForQuery(TestCredentials.API_VERSION!!, "select Id, AccountId from Contact where LastName = '$contactName'")
        val refIdToRequests = LinkedHashMap<String, RestRequest>()
        refIdToRequests["refAccount"] = firstRequest
        refIdToRequests["refContact"] = secondRequest
        refIdToRequests["refSearch"] = thirdRequest

        // Build composite request
        val compositeRequest = RestRequest.getCompositeRequest(TestCredentials.API_VERSION!!, false, refIdToRequests)

        // Send composite request
        val response = restClient.sendSync(compositeRequest)

        // Checking response
        val jsonResponse = response.asJSONObject()
        val jsonResults = jsonResponse.getJSONArray("compositeResponse")
        Assert.assertEquals("Wrong number of results", 3, jsonResults.length())
        Assert.assertEquals("Wrong status for first request", HttpURLConnection.HTTP_CREATED, jsonResults.getJSONObject(0).getInt("httpStatusCode"))
        Assert.assertEquals("Wrong status for second request", HttpURLConnection.HTTP_CREATED, jsonResults.getJSONObject(1).getInt("httpStatusCode"))
        Assert.assertEquals("Wrong status for third request", HttpURLConnection.HTTP_OK, jsonResults.getJSONObject(2).getInt("httpStatusCode"))

        // Query should have returned ids of newly created account and contact
        val accountId = jsonResults.getJSONObject(0).getJSONObject("body").getString("id")
        val contactId = jsonResults.getJSONObject(1).getJSONObject("body").getString("id")
        val queryRecords = jsonResults.getJSONObject(2).getJSONObject("body").getJSONArray("records")
        Assert.assertEquals("wrong number of results for query request", 1, queryRecords.length())
        Assert.assertEquals("Account id not returned by query", accountId, queryRecords.getJSONObject(0).getString("AccountId"))
        Assert.assertEquals("Contact id not returned by query", contactId, queryRecords.getJSONObject(0).getString("Id"))
    }

    /**
     * Test for sobject tree request
     *
     * Run a sobject tree request that:
     * - creates an account,
     * - creates two children contacts
     *
     * Then run queries that should return newly created account and contacts
     */
    @Test
    fun testSObjectTreeRequest() {
        val accountFields = hashMapOf<String, Any>()
        val accountName = ENTITY_NAME_PREFIX + System.nanoTime()
        accountFields[NAME] = accountName
        val contactFields = hashMapOf<String, Any>()
        val contactName = ENTITY_NAME_PREFIX + System.nanoTime()
        contactFields["LastName"] = contactName
        val otherContactFields = hashMapOf<String, Any>()
        val otherContactName = ENTITY_NAME_PREFIX + System.nanoTime()
        otherContactFields["LastName"] = otherContactName
        val childrenTrees = mutableListOf<RestRequest.SObjectTree>()
        @Suppress("UNCHECKED_CAST")
        childrenTrees.add(RestRequest.SObjectTree("contact", "Contacts", "refContact", contactFields as Map<String, Object>, null))
        @Suppress("UNCHECKED_CAST")
        childrenTrees.add(RestRequest.SObjectTree("contact", "Contacts", "refOtherContact", otherContactFields as Map<String, Object>, null))
        val recordTrees = mutableListOf<RestRequest.SObjectTree>()
        @Suppress("UNCHECKED_CAST")
        recordTrees.add(RestRequest.SObjectTree(ACCOUNT, "", "refAccount", accountFields as Map<String, Object>, childrenTrees))

        // Build sobject tree request
        val sobjectTreeRequest = RestRequest.getRequestForSObjectTree(TestCredentials.API_VERSION!!, ACCOUNT, recordTrees)

        // Send sobject tree request
        val response = restClient.sendSync(sobjectTreeRequest)

        // Checking response
        val jsonResponse = response.asJSONObject()
        checkKeys(jsonResponse, "hasErrors", "results")
        Assert.assertFalse("SObject tree request had errors", jsonResponse.getBoolean("hasErrors"))
        val jsonResults = jsonResponse.getJSONArray("results")
        Assert.assertEquals("Wrong number of results", 3, jsonResults.length())
        val accountId = jsonResults.getJSONObject(0).getString("id")
        val contactId = jsonResults.getJSONObject(1).getString("id")
        val otherContactId = jsonResults.getJSONObject(2).getString("id")

        // Running query that should match first contact and its parent
        val queryRequest = RestRequest.getRequestForQuery(TestCredentials.API_VERSION!!, "select Id, AccountId from Contact where LastName = '$contactName'")
        val queryResponse = restClient.sendSync(queryRequest)
        val queryRecords = queryResponse.asJSONObject().getJSONArray("records")
        Assert.assertEquals("wrong number of results for query request", 1, queryRecords.length())
        Assert.assertEquals("Account id not returned by query", accountId, queryRecords.getJSONObject(0).getString("AccountId"))
        Assert.assertEquals("Contact id not returned by query", contactId, queryRecords.getJSONObject(0).getString("Id"))

        // Running other query that should match other contact and its parent
        val otherQueryRequest = RestRequest.getRequestForQuery(TestCredentials.API_VERSION!!, "select Id, AccountId from Contact where LastName = '$otherContactName'")
        val otherQueryResponse = restClient.sendSync(otherQueryRequest)
        val otherQueryRecords = otherQueryResponse.asJSONObject().getJSONArray("records")
        Assert.assertEquals("wrong number of results for query request", 1, otherQueryRecords.length())
        Assert.assertEquals("Account id not returned by query", accountId, otherQueryRecords.getJSONObject(0).getString("AccountId"))
        Assert.assertEquals("Contact id not returned by query", otherContactId, otherQueryRecords.getJSONObject(0).getString("Id"))
    }

    @Test
    fun testGetNotificationsStatus() {
        val request = RestRequest.getRequestForNotificationsStatus(TestCredentials.API_VERSION!!)
        val response = restClient.sendSync(request)
        checkResponse(response, HttpURLConnection.HTTP_OK, false)
        checkKeys(response.asJSONObject(), "lastActivity", "oldestUnread", "oldestUnseen", "unreadCount", "unseenCount")
    }

    @Test
    fun testGetNotifications() {
        val yesterday = Date(Date().time - 24 * 60 * 60 * 1000)
        val request = RestRequest.getRequestForNotifications(TestCredentials.API_VERSION!!, 10, null, yesterday)
        val response = restClient.sendSync(request)
        checkResponse(response, HttpURLConnection.HTTP_OK, false)
        checkKeys(response.asJSONObject(), "notifications")
    }

    @Test
    fun testUpdateReadNotifications() {
        val request = RestRequest.getRequestForNotificationsUpdate(TestCredentials.API_VERSION!!, null, Date(), true, null)
        val response = restClient.sendSync(request)
        checkResponse(response, HttpURLConnection.HTTP_OK, false)
    }

    @Test
    fun testUpdateSeenNotifications() {
        val request = RestRequest.getRequestForNotificationsUpdate(TestCredentials.API_VERSION!!, null, Date(), null, true)
        val response = restClient.sendSync(request)
        checkResponse(response, HttpURLConnection.HTTP_OK, false)
    }

    @Test
    fun testGetPrimingRecords() {
        val request = RestRequest.getRequestForPrimingRecords(TestCredentials.API_VERSION!!, null, null)
        val response = restClient.sendSync(request)
        checkResponse(response, HttpURLConnection.HTTP_OK, false)
        checkKeys(response.asJSONObject(), "primingRecords", "relayToken", "ruleErrors", "stats")
    }

    @Test
    fun testGetPrimingRecordsWithChangedAfterTimestampParameter() {
        val request = RestRequest.getRequestForPrimingRecords(TestCredentials.API_VERSION!!, null, Date().time)
        val response = restClient.sendSync(request)
        checkResponse(response, HttpURLConnection.HTTP_OK, false)
        checkKeys(response.asJSONObject(), "primingRecords", "relayToken", "ruleErrors", "stats")
    }

    @Test
    fun testParsePrimingRecordsResponse() {
        val request = RestRequest.getRequestForPrimingRecords(TestCredentials.API_VERSION!!, null, null)
        val response = restClient.sendSync(request)
        checkResponse(response, HttpURLConnection.HTTP_OK, false)
        try {
            PrimingRecordsResponse(response.asJSONObject())
        } catch (e: Exception) {
            Assert.fail("Fail to parse priming records response: ${e.message}")
        }
    }

    @Test
    fun testCollectionCreate() {
        val firstAccountName = ENTITY_NAME_PREFIX + "_account_1_" + System.nanoTime()
        val secondAccountName = ENTITY_NAME_PREFIX + "_account_2_" + System.nanoTime()
        val contactName = ENTITY_NAME_PREFIX + "_contact_" + System.nanoTime()

        val records = makeRecords(
            listOf("Account", "Name", firstAccountName),
            listOf("Contact", "LastName", contactName),
            listOf("Account", "Name", secondAccountName)
        )

        // Doing a collection create
        val createResponse = restClient.sendSync(RestRequest.getRequestForCollectionCreate(TestCredentials.API_VERSION!!, true, records))

        // Parsing response
        val parsedCreateResponse = CollectionResponse(createResponse.asJSONArray())

        // Checking response
        Assert.assertEquals(3, parsedCreateResponse.subResponses.size)
        Assert.assertTrue(parsedCreateResponse.subResponses[0].id!!.startsWith("001"))
        Assert.assertTrue(parsedCreateResponse.subResponses[0].success)
        Assert.assertTrue(parsedCreateResponse.subResponses[0].errors.isEmpty())
        Assert.assertTrue(parsedCreateResponse.subResponses[1].id!!.startsWith("003"))
        Assert.assertTrue(parsedCreateResponse.subResponses[1].success)
        Assert.assertTrue(parsedCreateResponse.subResponses[1].errors.isEmpty())
        Assert.assertTrue(parsedCreateResponse.subResponses[2].id!!.startsWith("001"))
        Assert.assertTrue(parsedCreateResponse.subResponses[2].success)
        Assert.assertTrue(parsedCreateResponse.subResponses[2].errors.isEmpty())
    }

    @Test
    fun testCollectionRetrieve() {
        val firstAccountName = ENTITY_NAME_PREFIX + "_account_1_" + System.nanoTime()
        val secondAccountName = ENTITY_NAME_PREFIX + "_account_2_" + System.nanoTime()
        val contactName = ENTITY_NAME_PREFIX + "_contact_" + System.nanoTime()

        val records = makeRecords(
            listOf("Account", "Name", firstAccountName),
            listOf("Contact", "LastName", contactName),
            listOf("Account", "Name", secondAccountName)
        )

        // Doing a collection create
        val createResponse = restClient.sendSync(RestRequest.getRequestForCollectionCreate(TestCredentials.API_VERSION!!, true, records))

        // Parsing response
        val parsedCreateResponse = CollectionResponse(createResponse.asJSONArray())
        val firstAccountId = parsedCreateResponse.subResponses[0].id
        val contactId = parsedCreateResponse.subResponses[1].id
        val secondAccountId = parsedCreateResponse.subResponses[2].id

        // Doing a collection retrieve for the accounts
        val accountsRetrieveRequest = RestRequest.getRequestForCollectionRetrieve(TestCredentials.API_VERSION!!, "Account", listOf(firstAccountId!!, secondAccountId!!), listOf("Id", "Name"))
        val accountsRetrieved = restClient.sendSync(accountsRetrieveRequest).asJSONArray()

        // Checking response
        Assert.assertEquals(2, accountsRetrieved.length())
        Assert.assertEquals(firstAccountName, accountsRetrieved.getJSONObject(0).getString("Name"))
        Assert.assertEquals(secondAccountName, accountsRetrieved.getJSONObject(1).getString("Name"))

        // Doing a collection retrieve for the contact
        val contactsRetrievedRequest = RestRequest.getRequestForCollectionRetrieve(TestCredentials.API_VERSION!!, "Contact", listOf(contactId!!), listOf("Id", "LastName"))
        val contactsRetrieved = restClient.sendSync(contactsRetrievedRequest).asJSONArray()

        // Checking response
        Assert.assertEquals(1, contactsRetrieved.length())
        Assert.assertEquals(contactName, contactsRetrieved.getJSONObject(0).getString("LastName"))
    }

    @Test
    fun testCollectionUpsertNewRecords() {
        val firstAccountName = ENTITY_NAME_PREFIX + "_account_1_" + System.nanoTime()
        val secondAccountName = ENTITY_NAME_PREFIX + "_account_2_" + System.nanoTime()

        val records = makeRecords(
            listOf("Account", "Name", firstAccountName),
            listOf("Account", "Name", secondAccountName)
        )

        // Doing a collection upsert
        val upsertResponse = restClient.sendSync(RestRequest.getRequestForCollectionUpsert(TestCredentials.API_VERSION!!, true, "Account", "Id", records))

        // Parsing response
        val parsedUpsertResponse = CollectionResponse(upsertResponse.asJSONArray())

        // Checking response
        Assert.assertEquals(2, parsedUpsertResponse.subResponses.size)
        Assert.assertTrue(parsedUpsertResponse.subResponses[0].id!!.startsWith("001"))
        Assert.assertTrue(parsedUpsertResponse.subResponses[0].success)
        Assert.assertTrue(parsedUpsertResponse.subResponses[0].errors.isEmpty())
        Assert.assertTrue(parsedUpsertResponse.subResponses[1].id!!.startsWith("001"))
        Assert.assertTrue(parsedUpsertResponse.subResponses[1].success)
        Assert.assertTrue(parsedUpsertResponse.subResponses[1].errors.isEmpty())
    }

    @Test
    fun testCollectionUpsertExistingRecords() {
        val firstAccountName = ENTITY_NAME_PREFIX + "_account_1_" + System.nanoTime()
        val secondAccountName = ENTITY_NAME_PREFIX + "_account_2_" + System.nanoTime()

        val records = makeRecords(
            listOf("Account", "Name", firstAccountName),
            listOf("Account", "Name", secondAccountName)
        )

        // Doing a collection create
        val createResponse = restClient.sendSync(RestRequest.getRequestForCollectionCreate(TestCredentials.API_VERSION!!, true, records))

        // Parsing response
        val parsedCreateResponse = CollectionResponse(createResponse.asJSONArray())
        val firstAccountId = parsedCreateResponse.subResponses[0].id
        val secondAccountId = parsedCreateResponse.subResponses[1].id

        // Doing a collection upsert to update the accounts
        val firstAccountNameUpdated = firstAccountName + "_updated"
        val secondAccountNameUpdated = secondAccountName + "_updated"
        val updatedAccounts = makeRecords(
            listOf("Account", "Name", firstAccountNameUpdated, "Id", firstAccountId!!),
            listOf("Account", "Name", secondAccountNameUpdated, "Id", secondAccountId!!)
        )

        val upsertResponse = restClient.sendSync(RestRequest.getRequestForCollectionUpsert(TestCredentials.API_VERSION!!, true, "Account", "Id", updatedAccounts))

        // Parsing response
        val parsedUpsertResponse = CollectionResponse(upsertResponse.asJSONArray())

        // Checking response
        Assert.assertEquals(2, parsedUpsertResponse.subResponses.size)
        Assert.assertTrue(parsedUpsertResponse.subResponses[0].id!!.startsWith("001"))
        Assert.assertTrue(parsedUpsertResponse.subResponses[0].success)
        Assert.assertTrue(parsedUpsertResponse.subResponses[0].errors.isEmpty())
        Assert.assertTrue(parsedUpsertResponse.subResponses[1].id!!.startsWith("001"))
        Assert.assertTrue(parsedUpsertResponse.subResponses[1].success)
        Assert.assertTrue(parsedUpsertResponse.subResponses[1].errors.isEmpty())

        // Checking account on server to make sure they were updated
        val accountsRetrieveRequest = RestRequest.getRequestForCollectionRetrieve(TestCredentials.API_VERSION!!, "Account", listOf(firstAccountId!!, secondAccountId!!), listOf("Id", "Name"))
        val accountsRetrieved = restClient.sendSync(accountsRetrieveRequest).asJSONArray()
        Assert.assertEquals(2, accountsRetrieved.length())
        Assert.assertEquals(firstAccountNameUpdated, accountsRetrieved.getJSONObject(0).getString("Name"))
        Assert.assertEquals(secondAccountNameUpdated, accountsRetrieved.getJSONObject(1).getString("Name"))
    }

    @Test
    fun testCollectionUpdate() {
        val firstAccountName = ENTITY_NAME_PREFIX + "_account_1_" + System.nanoTime()
        val secondAccountName = ENTITY_NAME_PREFIX + "_account_2_" + System.nanoTime()
        val contactName = ENTITY_NAME_PREFIX + "_contact_" + System.nanoTime()

        val records = makeRecords(
            listOf("Account", "Name", firstAccountName),
            listOf("Contact", "LastName", contactName),
            listOf("Account", "Name", secondAccountName)
        )

        // Doing a collection create
        val createResponse = restClient.sendSync(RestRequest.getRequestForCollectionCreate(TestCredentials.API_VERSION!!, true, records))

        // Parsing response
        val parsedCreateResponse = CollectionResponse(createResponse.asJSONArray())
        val firstAccountId = parsedCreateResponse.subResponses[0].id
        val contactId = parsedCreateResponse.subResponses[1].id
        val secondAccountId = parsedCreateResponse.subResponses[2].id

        // Doing a collection update for one account and the contact
        val firstAccountNameUpdated = firstAccountName + "_updated"
        val contactNameUpdated = contactName + "_updated"
        val updatedRecords = makeRecords(
            listOf("Account", "Name", firstAccountNameUpdated, "Id", firstAccountId!!),
            listOf("Contact", "LastName", contactNameUpdated, "Id", contactId!!)
        )

        val updateResponse = restClient.sendSync(RestRequest.getRequestForCollectionUpdate(TestCredentials.API_VERSION!!, true, updatedRecords))

        // Parsing response
        val parsedUpdateResponse = CollectionResponse(updateResponse.asJSONArray())

        // Checking response
        Assert.assertEquals(2, parsedUpdateResponse.subResponses.size)
        Assert.assertTrue(parsedUpdateResponse.subResponses[0].id!!.startsWith("001"))
        Assert.assertTrue(parsedUpdateResponse.subResponses[0].success)
        Assert.assertTrue(parsedUpdateResponse.subResponses[0].errors.isEmpty())
        Assert.assertTrue(parsedUpdateResponse.subResponses[1].id!!.startsWith("003"))
        Assert.assertTrue(parsedUpdateResponse.subResponses[1].success)
        Assert.assertTrue(parsedUpdateResponse.subResponses[1].errors.isEmpty())

        // Checking accounts on server
        val accountsRetrieveRequest = RestRequest.getRequestForCollectionRetrieve(TestCredentials.API_VERSION!!, "Account", listOf(firstAccountId!!, secondAccountId!!), listOf("Id", "Name"))
        val accountsRetrieved = restClient.sendSync(accountsRetrieveRequest).asJSONArray()
        Assert.assertEquals(2, accountsRetrieved.length())
        Assert.assertEquals(firstAccountNameUpdated, accountsRetrieved.getJSONObject(0).getString("Name"))
        Assert.assertEquals(secondAccountName, accountsRetrieved.getJSONObject(1).getString("Name"))

        // Checking contact on server
        val contactsRetrieveRequest = RestRequest.getRequestForCollectionRetrieve(TestCredentials.API_VERSION!!, "Contact", listOf(contactId!!), listOf("Id", "LastName"))
        val contactsRetrieved = restClient.sendSync(contactsRetrieveRequest).asJSONArray()
        Assert.assertEquals(1, contactsRetrieved.length())
        Assert.assertEquals(contactNameUpdated, contactsRetrieved.getJSONObject(0).getString("LastName"))
    }

    @Test
    fun testCollectionDelete() {
        val firstAccountName = ENTITY_NAME_PREFIX + "_account_1_" + System.nanoTime()
        val secondAccountName = ENTITY_NAME_PREFIX + "_account_2_" + System.nanoTime()
        val contactName = ENTITY_NAME_PREFIX + "_contact_" + System.nanoTime()

        val records = makeRecords(
            listOf("Account", "Name", firstAccountName),
            listOf("Contact", "LastName", contactName),
            listOf("Account", "Name", secondAccountName)
        )

        // Doing a collection create
        val createResponse = restClient.sendSync(RestRequest.getRequestForCollectionCreate(TestCredentials.API_VERSION!!, true, records))

        // Parsing response
        val parsedCreateResponse = CollectionResponse(createResponse.asJSONArray())
        val firstAccountId = parsedCreateResponse.subResponses[0].id
        val contactId = parsedCreateResponse.subResponses[1].id
        val secondAccountId = parsedCreateResponse.subResponses[2].id

        // Doing a collection delete for one account and the contact
        val deleteResponse = restClient.sendSync(RestRequest.getRequestForCollectionDelete(TestCredentials.API_VERSION!!, false, listOf(firstAccountId!!, contactId!!)))
        val parsedDeleteResponse = CollectionResponse(deleteResponse.asJSONArray())

        // Checking response
        Assert.assertEquals(2, parsedDeleteResponse.subResponses.size)
        Assert.assertEquals(firstAccountId, parsedDeleteResponse.subResponses[0].id)
        Assert.assertTrue(parsedDeleteResponse.subResponses[0].success)
        Assert.assertTrue(parsedDeleteResponse.subResponses[0].errors.isEmpty())
        Assert.assertEquals(contactId, parsedDeleteResponse.subResponses[1].id)
        Assert.assertTrue(parsedDeleteResponse.subResponses[1].success)
        Assert.assertTrue(parsedDeleteResponse.subResponses[1].errors.isEmpty())

        // Making sure deleted account is gone using retrieve
        var response = restClient.sendSync(RestRequest.getRequestForRetrieve(TestCredentials.API_VERSION!!, "Account", firstAccountId!!, listOf("Id")))
        Assert.assertEquals("404 was expected", HttpURLConnection.HTTP_NOT_FOUND, response.statusCode)

        // Making sure only deleted account is gone using collection retrieve
        val accountsRetrieveRequest = RestRequest.getRequestForCollectionRetrieve(TestCredentials.API_VERSION!!, "Account", listOf(firstAccountId!!, secondAccountId!!), listOf("Id", "Name"))
        val accountsRetrieved = restClient.sendSync(accountsRetrieveRequest).asJSONArray()
        Assert.assertEquals(2, accountsRetrieved.length())
        Assert.assertTrue(accountsRetrieved.isNull(0))
        Assert.assertEquals(secondAccountName, accountsRetrieved.getJSONObject(1).getString("Name"))

        // Making sure deleted contact is gone using retrieve
        response = restClient.sendSync(RestRequest.getRequestForRetrieve(TestCredentials.API_VERSION!!, "Contact", contactId!!, listOf("Id")))
        Assert.assertEquals("404 was expected", HttpURLConnection.HTTP_NOT_FOUND, response.statusCode)

        // Making sure contact is gone using collection retrieve
        val contactsRetrievedRequest = RestRequest.getRequestForCollectionRetrieve(TestCredentials.API_VERSION!!, "Contact", listOf(contactId!!), listOf("Id", "LastName"))
        var contactsRetrieved = restClient.sendSync(contactsRetrievedRequest).asJSONArray()
        Assert.assertEquals(1, contactsRetrieved.length())
        contactsRetrieved = restClient.sendSync(contactsRetrievedRequest).asJSONArray()
        Assert.assertTrue(contactsRetrieved.isNull(0))
    }

    @Test
    fun testCollectionCreateWithBadRecordAndAllOrNoneFalse() {
        val accountName = ENTITY_NAME_PREFIX + "_account_" + System.nanoTime()
        val contactName = ENTITY_NAME_PREFIX + "_contact_" + System.nanoTime()

        val records = makeRecords(
            listOf("Account", "BadField", accountName),
            listOf("Contact", "LastName", contactName)
        )

        val response = restClient.sendSync(RestRequest.getRequestForCollectionCreate(TestCredentials.API_VERSION!!, false, records))
        val jsonResponse = response.asJSONArray()
        val parsedResponse = CollectionResponse(jsonResponse)
        Assert.assertEquals(2, parsedResponse.subResponses.size)
        Assert.assertNull(parsedResponse.subResponses[0].id)
        Assert.assertFalse(parsedResponse.subResponses[0].success)
        Assert.assertFalse(parsedResponse.subResponses[0].errors.isEmpty())
        Assert.assertEquals("INVALID_FIELD", parsedResponse.subResponses[0].errors[0].statusCode)
        Assert.assertTrue(parsedResponse.subResponses[1].id!!.startsWith("003"))
        Assert.assertTrue(parsedResponse.subResponses[1].success)
        Assert.assertTrue(parsedResponse.subResponses[1].errors.isEmpty())
    }

    @Test
    fun testCollectionCreateWithBadRecordAndAllOrNoneTrue() {
        val accountName = ENTITY_NAME_PREFIX + "_account_" + System.nanoTime()
        val contactName = ENTITY_NAME_PREFIX + "_contact_" + System.nanoTime()

        val records = makeRecords(
            listOf("Account", "BadField", accountName),
            listOf("Contact", "LastName", contactName)
        )

        val response = restClient.sendSync(RestRequest.getRequestForCollectionCreate(TestCredentials.API_VERSION!!, true, records))
        val jsonResponse = response.asJSONArray()
        val parsedResponse = CollectionResponse(jsonResponse)
        Assert.assertEquals(2, parsedResponse.subResponses.size)
        Assert.assertNull(parsedResponse.subResponses[0].id)
        Assert.assertFalse(parsedResponse.subResponses[0].success)
        Assert.assertFalse(parsedResponse.subResponses[0].errors.isEmpty())
        Assert.assertEquals("INVALID_FIELD", parsedResponse.subResponses[0].errors[0].statusCode)
        Assert.assertNull(parsedResponse.subResponses[1].id)
        Assert.assertFalse(parsedResponse.subResponses[1].success)
        Assert.assertFalse(parsedResponse.subResponses[1].errors.isEmpty())
        Assert.assertEquals("ALL_OR_NONE_OPERATION_ROLLED_BACK", parsedResponse.subResponses[1].errors[0].statusCode)
    }

    /**
     * Testing a limits call to the server - check response
     */
    @Test
    fun testLimits() {
        val response = restClient.sendSync(RestRequest.getRequestForLimits(TestCredentials.API_VERSION!!))
        checkResponse(response, HttpURLConnection.HTTP_OK, false)
        val jsonResponse = response.asJSONObject()
        checkKeys(jsonResponse, "DailyApiRequests")
    }

    //
    // Helper methods
    //

    /**
     * @return a RestResponse for testing streaming. It should contain some JSON data.
     */
    private fun getStreamTestResponse(): RestResponse {
        val response = restClient.sendSync(RestRequest.getRequestForResources(TestCredentials.API_VERSION!!))
        Assert.assertEquals("Response code should be HTTP OK", HttpURLConnection.HTTP_OK, response.statusCode)
        return response
    }

    /**
     * Assert that the RestResponse returned from getStreamTestResponse() is valid.
     * @param inputStream the InputStream of response data
     */
    @Throws(IOException::class, JSONException::class)
    private fun assertStreamTestResponseStreamIsValid(inputStream: InputStream) {
        val responseData = Encryptor.getStringFromStream(inputStream)
        Assert.assertNotNull("The response should contain data", responseData)
        val responseJson = JSONObject(responseData)
        checkKeys(responseJson, "sobjects", "search", "recent")
    }

    /**
     * Send request using sendAsync method
     */
    private fun sendAsync(client: RestClient, request: RestRequest): RestResponse? {
        val responseBlockingQueue = ArrayBlockingQueue<RestResponse?>(1)
        client.sendAsync(request, object : RestClient.AsyncRequestCallback {
            override fun onSuccess(request: RestRequest, response: RestResponse) {
                responseBlockingQueue.add(response)
            }

            override fun onError(exception: Exception) {
                responseBlockingQueue.add(null)
            }
        })
        return responseBlockingQueue.poll(30, TimeUnit.SECONDS)
    }

    /**
     * Helper method to create an account with a unique name and returns its name and id
     */
    private fun createAccount(): IdName {
        val fields = hashMapOf<String, Any>()
        val newAccountName = ENTITY_NAME_PREFIX + System.nanoTime()
        fields[NAME] = newAccountName
        val response = restClient.sendSync(RestRequest.getRequestForCreate(TestCredentials.API_VERSION!!, ACCOUNT, fields))
        val newAccountId = response.asJSONObject().getString("id")
        return IdName(newAccountId, newAccountName)
    }

    /**
     * Helper method to create multiple accounts with a unique name and returns their name and id
     */
    private fun createAccounts(count: Int, additionalPrefix: String): List<IdName> {
        val fields = hashMapOf<String, Any>()
        val ids = mutableListOf<String>()
        val names = mutableListOf<String>()

        // Creating names
        for (i in 0 until count) {
            names.add(ENTITY_NAME_PREFIX + additionalPrefix + System.nanoTime())
        }

        // Creating accounts collecting ids
        val requests = mutableListOf<RestRequest>()
        for (i in 0 until count) {
            fields[NAME] = names[i]
            requests.add(RestRequest.getRequestForCreate(TestCredentials.API_VERSION!!, ACCOUNT, fields))
            if (requests.size == 25) {
                ids.addAll(getCreatedIds(requests))
                requests.clear()
            }
        }
        if (requests.size > 0) {
            ids.addAll(getCreatedIds(requests))
        }

        // Build IdName's
        val idNames = mutableListOf<IdName>()
        for (i in ids.indices) {
            idNames.add(IdName(ids[i], names[i]))
        }
        return idNames
    }

    private fun getCreatedIds(createRequests: List<RestRequest>): List<String> {
        val ids = mutableListOf<String>()
        val batchResponse = BatchResponse(restClient.sendSync(RestRequest.getBatchRequest(TestCredentials.API_VERSION!!, false, createRequests)).asJSONObject())
        for (response in batchResponse.results) {
            ids.add(response.getJSONObject("result").getString("id"))
        }
        return ids
    }

    /**
     * Helper method to delete any entities created by one of the test
     */
    private fun cleanup() {
        try {
            val response = restClient.sendSync(RestRequest.getRequestForSearch(TestCredentials.API_VERSION!!, "find {$ENTITY_NAME_PREFIX}"))
            val jsonResults = response.asJSONObject().getJSONArray("searchRecords")
            val requests = mutableListOf<RestRequest>()
            for (i in 0 until jsonResults.length()) {
                val jsonResult = jsonResults.getJSONObject(i)
                val objectType = jsonResult.getJSONObject("attributes").getString("type")
                val id = jsonResult.getString("Id")
                val deleteRequest = RestRequest.getRequestForDelete(TestCredentials.API_VERSION!!, objectType, id)
                requests.add(deleteRequest)
                if (requests.size == 25) {
                    restClient.sendSync(RestRequest.getBatchRequest(TestCredentials.API_VERSION!!, false, requests))
                    requests.clear()
                }
            }
            if (requests.size > 0) {
                restClient.sendSync(RestRequest.getBatchRequest(TestCredentials.API_VERSION!!, false, requests))
            }
        } catch (e: Exception) {
            // We tried our best :-(
        }
    }

    /**
     * Helper method to validate responses
     */
    private fun checkResponse(response: RestResponse?, expectedStatusCode: Int, isJsonArray: Boolean) {
        // Check status code
        Assert.assertEquals("$expectedStatusCode response expected", expectedStatusCode, response!!.statusCode)

        // Try to parse as json
        try {
            if (isJsonArray) {
                response.asJSONArray()
            } else {
                response.asJSONObject()
            }
        } catch (e: Exception) {
            Assert.fail("Failed to parse response body")
        }
    }

    /**
     * Helper method to check if a jsonObject has all the expected keys
     */
    private fun checkKeys(jsonObject: JSONObject, vararg expectedKeys: String) {
        for (expectedKey in expectedKeys) {
            Assert.assertTrue("Object should have key: $expectedKey", jsonObject.has(expectedKey))
        }
    }

    /**
     * Make JSONArray of JSONObject's representing records with provided type and field
     * @param typeFieldNameValues a list of list containing objectType, fieldName, otherFieldName, otherFieldValue etc
     */
    private fun makeRecords(vararg typeFieldNameValues: List<String>): JSONArray {
        val records = JSONArray()
        for (entry in typeFieldNameValues) {
            val record = JSONObject()
            val recordAttributes = JSONObject()
            recordAttributes.put("type", entry[0])
            record.put("attributes", recordAttributes)
            var i = 1
            while (i < entry.size) {
                record.put(entry[i], entry[i + 1])
                i += 2
            }
            records.put(record)
        }
        return records
    }

    /**
     * Helper class to hold name and id
     */
    private data class IdName(val id: String, val name: String)

    // -------------------------------------------------------------------------
    // User-Agent tests (W-23278653)
    // -------------------------------------------------------------------------

    /**
     * Verifies that getJSONCredentials() uses the clientInfo user (not currentUser) when
     * building the userAgent field.
     *
     * Strategy: register a global flag and a per-user flag on a synthetic user whose
     * orgId/userId differs from this restClient's clientInfo. getJSONCredentials() must
     * include the global flag (always present) but exclude the other user's per-user flag.
     * This proves the lookup uses clientInfo.orgId/userId, not currentUser — and always
     * runs regardless of AccountManager state.
     */
    @Test
    fun test_givenGlobalFlagAndOtherUserPerUserFlag_whenGetJSONCredentials_thenUserAgentContainsGlobalButNotOtherUserFlag() {
        val globalFlag = "GZ"
        val otherUserFlag = "OZ"
        val otherUser = UserAccountBuilder.getInstance()
            .authToken("tok").refreshToken("rtok")
            .loginServer("https://login.salesforce.com")
            .idUrl("https://login.salesforce.com/id/otherOrg2/otherUser2")
            .instanceServer("https://cs1.salesforce.com")
            .orgId("otherOrg2").userId("otherUser2")
            .username("other2@example.com")
            .accountName("other2 (SalesforceSDKTest)")
            .build()

        SalesforceSDKManager.getInstance().registerUsedAppFeature(globalFlag)
        SalesforceSDKManager.getInstance().registerUsedAppFeature(otherUserFlag, otherUser)
        try {
            val creds = restClient.getJSONCredentials()
            val userAgent = creds.getString("userAgent")
            Assert.assertTrue(
                "userAgent should contain global flag GZ",
                userAgent.contains(globalFlag)
            )
            Assert.assertFalse(
                "userAgent should NOT contain other user's per-user flag OZ",
                userAgent.contains(otherUserFlag)
            )
        } finally {
            SalesforceSDKManager.getInstance().unregisterUsedAppFeature(globalFlag)
            SalesforceSDKManager.getInstance().unregisterUsedAppFeature(otherUserFlag, otherUser)
        }
    }

    /**
     * Verifies that getJSONCredentials() does NOT bleed per-user flags from
     * an unrelated user into a different RestClient's credentials.
     */
    @Test
    fun test_givenPerUserFlagOnDifferentUser_whenGetJSONCredentials_thenUserAgentExcludesFlag() {
        val isolatedFlag = "W3"
        // Build a synthetic user with IDs that differ from the test RestClient's clientInfo.
        val otherUser = UserAccountBuilder.getInstance()
            .authToken("tok").refreshToken("rtok")
            .loginServer("https://login.salesforce.com")
            .idUrl("https://login.salesforce.com/id/otherOrg/otherUser")
            .instanceServer("https://cs1.salesforce.com")
            .orgId("otherOrg").userId("otherUser")
            .username("other@example.com")
            .accountName("other (SalesforceSDKTest)")
            .build()

        SalesforceSDKManager.getInstance().registerUsedAppFeature(isolatedFlag, otherUser)
        try {
            val creds = restClient.getJSONCredentials()
            val userAgent = creds.getString("userAgent")
            Assert.assertFalse(
                "userAgent for restClient should NOT contain flag registered for a different user",
                userAgent.contains(isolatedFlag)
            )
        } finally {
            SalesforceSDKManager.getInstance().unregisterUsedAppFeature(isolatedFlag, otherUser)
        }
    }
}
