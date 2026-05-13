/*
 * Copyright (c) 2014-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.mobilesync.manager

import android.app.Application
import android.app.Instrumentation
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.analytics.logger.SalesforceLogger
import com.salesforce.androidsdk.auth.HttpAccess
import com.salesforce.androidsdk.auth.OAuth2
import com.salesforce.androidsdk.mobilesync.TestForceApp
import com.salesforce.androidsdk.mobilesync.app.MobileSyncSDKManager
import com.salesforce.androidsdk.mobilesync.util.Constants
import com.salesforce.androidsdk.mobilesync.util.MobileSyncLogger
import com.salesforce.androidsdk.rest.ApiVersionStrings
import com.salesforce.androidsdk.rest.ClientManager
import com.salesforce.androidsdk.rest.RestClient
import com.salesforce.androidsdk.rest.RestClient.ClientInfo
import com.salesforce.androidsdk.rest.RestRequest
import com.salesforce.androidsdk.rest.RestResponse
import com.salesforce.androidsdk.smartstore.store.SmartStore
import com.salesforce.androidsdk.util.EventsObservable.EventType
import com.salesforce.androidsdk.util.test.EventsListenerQueue
import com.salesforce.androidsdk.util.test.TestCredentials
import com.salesforce.androidsdk.util.test.TestCredentials.ACCOUNT_NAME
import com.salesforce.androidsdk.util.test.TestCredentials.CLIENT_ID
import com.salesforce.androidsdk.util.test.TestCredentials.IDENTITY_URL
import com.salesforce.androidsdk.util.test.TestCredentials.INSTANCE_URL
import com.salesforce.androidsdk.util.test.TestCredentials.LANGUAGE
import com.salesforce.androidsdk.util.test.TestCredentials.LOCALE
import com.salesforce.androidsdk.util.test.TestCredentials.LOGIN_URL
import com.salesforce.androidsdk.util.test.TestCredentials.ORG_ID
import com.salesforce.androidsdk.util.test.TestCredentials.USERNAME
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert
import java.net.HttpURLConnection
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Abstract super class for manager test classes.
 */
abstract class ManagerTestCase {

    companion object {
        private val TEST_SCOPES = arrayOf("web")
        private const val TEST_CALLBACK_URL = "test://callback"
        private const val TEST_AUTH_TOKEN = "test_auth_token"
        private const val LID = "id" // lower case id in create response
    }

    protected lateinit var targetContext: Context
    protected var eq: EventsListenerQueue? = null
    protected lateinit var sdkManager: MobileSyncSDKManager
    protected lateinit var syncManager: SyncManager
    protected lateinit var globalSyncManager: SyncManager
    protected lateinit var restClient: RestClient
    protected lateinit var httpAccess: HttpAccess
    protected lateinit var smartStore: SmartStore
    protected lateinit var globalSmartStore: SmartStore
    protected lateinit var apiVersion: String

    @Throws(Exception::class)
    open fun setUp() {
        targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        apiVersion = ApiVersionStrings.getVersionNumber(targetContext)
        val app = Instrumentation.newApplication(
            TestForceApp::class.java,
            targetContext
        )
        InstrumentationRegistry.getInstrumentation().callApplicationOnCreate(app)
        TestCredentials.init(InstrumentationRegistry.getInstrumentation().context)
        eq = EventsListenerQueue()
        if (MobileSyncSDKManager.getInstance() == null) {
            eq!!.waitForEvent(EventType.AppCreateComplete, 5000)
        }
        val clientManager = ClientManager(
            targetContext,
            TestCredentials.ACCOUNT_TYPE,
            true
        )
        clientManager.createNewAccount(
            ACCOUNT_NAME,
            USERNAME, TestCredentials.REFRESH_TOKEN,
            TEST_AUTH_TOKEN, INSTANCE_URL,
            LOGIN_URL, IDENTITY_URL,
            CLIENT_ID, ORG_ID,
            TestCredentials.USER_ID, null, null, null,
            null, null, null, TestCredentials.PHOTO_URL, null,
            null, null, null, null, null,
            null, null, null, false,
            LANGUAGE, LOCALE
        )
        SyncManager.reset()
        sdkManager = MobileSyncSDKManager.getInstance()
        smartStore = sdkManager.getSmartStore()
        globalSmartStore = sdkManager.getGlobalSmartStore()
        syncManager = SyncManager.getInstance()
        globalSyncManager = SyncManager.getInstance(null, null, globalSmartStore)
        restClient = initRestClient()
        syncManager.restClient = restClient
        MobileSyncLogger.setLogLevel(SalesforceLogger.Level.DEBUG)
    }

    @Throws(Exception::class)
    open fun tearDown() {
        if (eq != null) {
            eq!!.tearDown()
            eq = null
        }
    }

    @Throws(Exception::class)
    private fun initRestClient(): RestClient {
        httpAccess = HttpAccess(null, "dummy-agent")
        val refreshResponse = OAuth2.refreshAuthToken(
            httpAccess,
            URI(LOGIN_URL), CLIENT_ID,
            TestCredentials.REFRESH_TOKEN, null
        )
        val authToken = refreshResponse.authToken
        val clientInfo = ClientInfo(
            URI(INSTANCE_URL),
            URI(LOGIN_URL),
            URI(IDENTITY_URL),
            ACCOUNT_NAME, USERNAME,
            TestCredentials.USER_ID, ORG_ID, null, null,
            null, null, null, null, TestCredentials.PHOTO_URL,
            null, null, null, null,
            null, null, null, null, null
        )
        return RestClient(clientInfo, authToken, httpAccess, null)
    }

    /**
     * Helper methods to create "count" of test records
     * @param count
     * @return map of id to name for the created records
     * @throws Exception
     */
    @Throws(Exception::class)
    protected fun createRecordsOnServer(count: Int, objectType: String): Map<String, String> {
        val idToFields = createRecordsOnServerReturnFields(count, objectType, null)
        val idToNames = mutableMapOf<String, String>()
        for (id in idToFields.keys) {
            idToNames[id] = idToFields[id]!![if (objectType == Constants.CONTACT) Constants.LAST_NAME else Constants.NAME] as String
        }
        return idToNames
    }

    /**
     * Helper methods to create "count" of test records
     * @param count
     * @param additionalFields
     * @return map of id to map of field name to field value for the created records
     * @throws Exception
     */
    @Throws(Exception::class)
    protected fun createRecordsOnServerReturnFields(
        count: Int,
        objectType: String,
        additionalFields: Map<String, Any>?
    ): Map<String, Map<String, Any>> {
        val listFields = buildFieldsMapForRecords(count, objectType, additionalFields)

        // Prepare request
        val requests = mutableListOf<RestRequest>()
        for (fields in listFields) {
            requests.add(RestRequest.getRequestForCreate(apiVersion, objectType, fields))
        }
        val batchRequest = RestRequest.getBatchRequest(apiVersion, false, requests)

        // Go to server
        val response = restClient.sendSync(batchRequest)!!
        Assert.assertTrue(
            "Creates failed",
            response.isSuccess() && !response.asJSONObject().getBoolean("hasErrors")
        )
        val idToFields = mutableMapOf<String, Map<String, Any>>()
        val results = response.asJSONObject().getJSONArray("results")
        for (i in 0 until results.length()) {
            val result = results.getJSONObject(i)
            Assert.assertEquals(
                "Status should be HTTP_CREATED",
                HttpURLConnection.HTTP_CREATED,
                result.getInt("statusCode")
            )
            val id = result.getJSONObject("result").getString(LID)
            val fields = listFields[i]

            idToFields[id] = fields
        }
        return idToFields
    }

    /**
     * Helper method to build field name to field value maps
     *
     * @param count
     * @param objectType
     * @param additionalFields
     * @return
     */
    protected fun buildFieldsMapForRecords(
        count: Int,
        objectType: String,
        additionalFields: Map<String, Any>?
    ): List<Map<String, Any>> {
        val listFields = mutableListOf<Map<String, Any>>()
        for (i in 0 until count) {

            // Request.
            val name = createRecordName(objectType)
            val fields = mutableMapOf<String, Any>()

            // Add additional fields if any
            if (additionalFields != null) {
                fields.putAll(additionalFields)
            }

            //add more object type if need to support to use this API
            //to create a new record on server
            when (objectType) {
                Constants.ACCOUNT -> {
                    fields[Constants.NAME] = name
                    fields[Constants.DESCRIPTION] = "Description_$name"
                }
                Constants.CONTACT -> {
                    fields[Constants.LAST_NAME] = name
                }
                Constants.OPPORTUNITY -> {
                    fields[Constants.NAME] = name
                    fields["StageName"] = "Prospecting"
                    val formatter = SimpleDateFormat("yyyy-MM-dd")
                    fields["CloseDate"] = formatter.format(Date())
                }
            }
            listFields.add(fields)
        }
        return listFields
    }

    /**
     * Delete records with given ids from server
     * @param ids
     * @throws Exception
     */
    @Throws(Exception::class)
    protected fun deleteRecordsByIdOnServer(ids: Collection<String>, objectType: String) {
        val idsList = ids.toList()
        val maxIdsPerRequest = 200
        val countIds = idsList.size
        val countSlices = Math.ceil(countIds.toDouble() / maxIdsPerRequest).toInt()
        for (slice in 0 until countSlices) {
            val idsToDelete = idsList.subList(
                slice * maxIdsPerRequest,
                Math.min(countIds, (slice + 1) * maxIdsPerRequest)
            )
            restClient.sendSync(RestRequest.getRequestForCollectionDelete(apiVersion, false, idsToDelete))
        }
    }

    /**
     * @return record name of the form SyncManagerTest<random number left-padded to be 8 digits long>
     */
    protected open fun createRecordName(objectType: String): String {
        return String.format(Locale.US, "ManagerTest_%s_%d", objectType, System.nanoTime())
    }

    /**
     * Update records on server
     * @param idToFieldsUpdated
     * @param sObjectType
     * @throws Exception
     */
    @Throws(Exception::class)
    protected fun updateRecordsOnServer(
        idToFieldsUpdated: Map<String, Map<String, Any>>,
        sObjectType: String
    ) {
        val requests = mutableListOf<RestRequest>()
        for (id in idToFieldsUpdated.keys) {
            requests.add(RestRequest.getRequestForUpdate(apiVersion, sObjectType, id, idToFieldsUpdated[id]))
        }
        val response = restClient.sendSync(RestRequest.getBatchRequest(apiVersion, false, requests))!!
        Assert.assertTrue(
            "Updates failed",
            response.isSuccess() && !response.asJSONObject().getBoolean("hasErrors")
        )
    }

    /**
     * Get ids of records on server matching criteria
     *
     * @param objectType
     * @param criteria criteria to use in where clause
     */
    @Throws(Exception::class)
    protected fun getIdsOnServer(objectType: String, criteria: String): List<String> {
        val query = String.format("SELECT Id FROM %s WHERE %s", objectType, criteria)
        val request = RestRequest.getRequestForQuery(apiVersion, query)
        val response = restClient.sendSync(request)!!
        val records = response.asJSONObject().getJSONArray(Constants.RECORDS)
        val ids = mutableListOf<String>()
        for (i in 0 until records.length()) {
            ids.add(records.getJSONObject(i).getString("Id"))
        }
        return ids
    }

    /**
     * Delete records on server matching criteria
     *
     * @param objectType
     * @param criteria criteria to use in where clause
     */
    @Throws(Exception::class)
    protected fun deleteRecordsByCriteriaFromServer(objectType: String, criteria: String) {
        val ids = getIdsOnServer(objectType, criteria)
        deleteRecordsByIdOnServer(ids, objectType)
    }
}
