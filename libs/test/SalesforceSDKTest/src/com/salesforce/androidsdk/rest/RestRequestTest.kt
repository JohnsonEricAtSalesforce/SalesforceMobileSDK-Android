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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.salesforce.androidsdk.rest.RestRequest.RestMethod
import com.salesforce.androidsdk.util.JSONTestHelper
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.Collections
import java.util.LinkedHashMap

@RunWith(AndroidJUnit4::class)
@LargeTest
class RestRequestTest {

    companion object {
        private const val TEST_API_VERSION = "v99.0"
        private const val TEST_OBJECT_TYPE = "testObjectType"
        private const val TEST_LAYOUT_TYPE_COMPACT = "Compact"
        private const val TEST_FORM_FACTOR_MEDIUM = "Medium"
        private const val TEST_MODE_EDIT = "Edit"
        private const val TEST_RECORD_TYPE_ID = "test_record_type_id"
        private const val TEST_OTHER_OBJECT_TYPE = "testOtherObjectType"
        private const val TEST_OBJECT_ID = "testObjectId"
        private const val TEST_EXTERNAL_ID_FIELD = "testExternalIdField"
        private const val TEST_EXTERNAL_ID = "testExternalId"
        private const val TEST_QUERY = "testQuery"
        private const val TEST_SEARCH = "testSearch"
        private const val TEST_FIELDS_STRING = """{"fieldX":"value with spaces","name":"testAccount"}"""
        private val TEST_FIELDS_LIST: List<String> = Collections.unmodifiableList(listOf("name", "fieldX"))
        private const val TEST_FIELDS_LIST_STRING = "name%2CfieldX"
        private val TEST_OBJECTS_LIST: List<String> = Collections.unmodifiableList(listOf("Account", "Contact"))
        private const val TEST_OBJECTS_LIST_STRING = "Account%2CContact"
        private const val TEST_OTHER_OBJECT_TYPE_PLURAL = "testOtherObjectTypes"
        const val TEST_REF_PARENT = "testRefParent"
        const val TEST_REF_CHILD = "testRefChild"

        private val TEST_FIELDS: Map<String, Any> = Collections.unmodifiableMap(
            hashMapOf(
                "name" to "testAccount",
                "fieldX" to "value with spaces"
            )
        )

        private val TEST_OTHER_FIELDS: Map<String, Any> = Collections.unmodifiableMap(
            hashMapOf(
                "name" to "testContact",
                "fieldY" to "value with spaces"
            )
        )
    }

    /**
     * Test for getRequestForUserInfo
     */
    @Test
    fun testGetRequestForUserInfo() {
        val request = RestRequest.getRequestForUserInfo()
        Assert.assertEquals("Wrong method", RestMethod.GET, request.method)
        Assert.assertEquals("Wrong path", "/services/oauth2/userinfo", request.path)
        Assert.assertEquals("Wrong endpoint", RestRequest.RestEndpoint.LOGIN, request.endpoint)
        Assert.assertNull("Wrong request entity", request.requestBody)
        Assert.assertNull("Wrong additional headers", request.additionalHttpHeaders)
    }

    /**
     * Test for getRequestForSingleAccess
     */
    @Test
    fun testGetRequestForSingleAccess() {
        val request = RestRequest.getRequestForSingleAccess("abc/def")
        val expectedRequestBody = "redirect_uri=abc%2Fdef".toRequestBody(
            "application/x-www-form-urlencoded".toMediaType()
        )
        Assert.assertEquals("Wrong method", RestMethod.POST, request.method)
        Assert.assertEquals("Wrong path", "/services/oauth2/singleaccess", request.path)
        Assert.assertEquals("Wrong endpoint", RestRequest.RestEndpoint.INSTANCE, request.endpoint)
        Assert.assertEquals("Wrong request body", bodyToString(expectedRequestBody), bodyToString(request))
        Assert.assertNull("Wrong additional headers", request.additionalHttpHeaders)
    }

    /**
     * Test for getRequestForVersions
     */
    @Test
    fun testGetRequestForVersions() {
        val request = RestRequest.getRequestForVersions()
        Assert.assertEquals("Wrong method", RestMethod.GET, request.method)
        Assert.assertEquals("Wrong path", "/services/data/", request.path)
        Assert.assertNull("Wrong request entity", request.requestBody)
        Assert.assertNull("Wrong additional headers", request.additionalHttpHeaders)
    }

    /**
     * Test for getRequestForResources
     */
    @Test
    fun testGetRequestForResources() {
        val request = RestRequest.getRequestForResources(TEST_API_VERSION)
        Assert.assertEquals("Wrong method", RestMethod.GET, request.method)
        Assert.assertEquals("Wrong path", "/services/data/$TEST_API_VERSION/", request.path)
        Assert.assertNull("Wrong request entity", request.requestBody)
        Assert.assertNull("Wrong additional headers", request.additionalHttpHeaders)
    }

    /**
     * Test for getRequestForDescribeGlobal
     */
    @Test
    fun testGetRequestForDescribeGlobal() {
        val request = RestRequest.getRequestForDescribeGlobal(TEST_API_VERSION)
        Assert.assertEquals("Wrong method", RestMethod.GET, request.method)
        Assert.assertEquals("Wrong path", "/services/data/$TEST_API_VERSION/sobjects/", request.path)
        Assert.assertNull("Wrong request entity", request.requestBody)
        Assert.assertNull("Wrong additional headers", request.additionalHttpHeaders)
    }

    /**
     * Test for getRequestForMetadata
     */
    @Test
    fun testGetRequestForMetadata() {
        val request = RestRequest.getRequestForMetadata(TEST_API_VERSION, TEST_OBJECT_TYPE)
        Assert.assertEquals("Wrong method", RestMethod.GET, request.method)
        Assert.assertEquals("Wrong path", "/services/data/$TEST_API_VERSION/sobjects/$TEST_OBJECT_TYPE/", request.path)
        Assert.assertNull("Wrong request entity", request.requestBody)
        Assert.assertNull("Wrong additional headers", request.additionalHttpHeaders)
    }

    /**
     * Test for getRequestForDescribe
     */
    @Test
    fun testGetRequestForDescribe() {
        val request = RestRequest.getRequestForDescribe(TEST_API_VERSION, TEST_OBJECT_TYPE)
        Assert.assertEquals("Wrong method", RestMethod.GET, request.method)
        Assert.assertEquals("Wrong path", "/services/data/$TEST_API_VERSION/sobjects/$TEST_OBJECT_TYPE/describe/", request.path)
        Assert.assertNull("Wrong request entity", request.requestBody)
        Assert.assertNull("Wrong additional headers", request.additionalHttpHeaders)
    }

    /**
     * Test for getRequestForObjectLayout without formFactor.
     */
    @Test
    fun testGetRequestForObjectLayoutWithoutFormFactor() {
        val request = RestRequest.getRequestForObjectLayout(
            TEST_API_VERSION, TEST_OBJECT_TYPE, null, null, null, null
        )
        Assert.assertEquals("Wrong method", RestMethod.GET, request.method)
        Assert.assertEquals("Wrong path", "/services/data/$TEST_API_VERSION/ui-api/layout/$TEST_OBJECT_TYPE", request.path)
        Assert.assertNull("Wrong request entity", request.requestBody)
        Assert.assertNull("Wrong additional headers", request.additionalHttpHeaders)
    }

    /**
     * Test for getRequestForObjectLayout with formFactor.
     */
    @Test
    fun testGetRequestForObjectLayoutWithFormFactor() {
        val request = RestRequest.getRequestForObjectLayout(
            TEST_API_VERSION, TEST_OBJECT_TYPE, TEST_FORM_FACTOR_MEDIUM, null, null, null
        )
        Assert.assertEquals("Wrong method", RestMethod.GET, request.method)
        Assert.assertEquals("Wrong path", "/services/data/$TEST_API_VERSION/ui-api/layout/$TEST_OBJECT_TYPE?formFactor=$TEST_FORM_FACTOR_MEDIUM", request.path)
        Assert.assertNull("Wrong request entity", request.requestBody)
        Assert.assertNull("Wrong additional headers", request.additionalHttpHeaders)
    }

    /**
     * Test for getRequestForObjectLayout without layoutType.
     */
    @Test
    fun testGetRequestForObjectLayoutWithoutLayoutType() {
        val request = RestRequest.getRequestForObjectLayout(
            TEST_API_VERSION, TEST_OBJECT_TYPE, null, null, null, null
        )
        Assert.assertEquals("Wrong method", RestMethod.GET, request.method)
        Assert.assertEquals("Wrong path", "/services/data/$TEST_API_VERSION/ui-api/layout/$TEST_OBJECT_TYPE", request.path)
        Assert.assertNull("Wrong request entity", request.requestBody)
        Assert.assertNull("Wrong additional headers", request.additionalHttpHeaders)
    }

    /**
     * Test for getRequestForObjectLayout with layoutType.
     */
    @Test
    fun testGetRequestForObjectLayoutWithLayoutType() {
        val request = RestRequest.getRequestForObjectLayout(
            TEST_API_VERSION, TEST_OBJECT_TYPE, null, TEST_LAYOUT_TYPE_COMPACT, null, null
        )
        Assert.assertEquals("Wrong method", RestMethod.GET, request.method)
        Assert.assertEquals("Wrong path", "/services/data/$TEST_API_VERSION/ui-api/layout/$TEST_OBJECT_TYPE?layoutType=$TEST_LAYOUT_TYPE_COMPACT", request.path)
        Assert.assertNull("Wrong request entity", request.requestBody)
        Assert.assertNull("Wrong additional headers", request.additionalHttpHeaders)
    }

    /**
     * Test for getRequestForObjectLayout without mode.
     */
    @Test
    fun testGetRequestForObjectLayoutWithoutMode() {
        val request = RestRequest.getRequestForObjectLayout(
            TEST_API_VERSION, TEST_OBJECT_TYPE, null, null, null, null
        )
        Assert.assertEquals("Wrong method", RestMethod.GET, request.method)
        Assert.assertEquals("Wrong path", "/services/data/$TEST_API_VERSION/ui-api/layout/$TEST_OBJECT_TYPE", request.path)
        Assert.assertNull("Wrong request entity", request.requestBody)
        Assert.assertNull("Wrong additional headers", request.additionalHttpHeaders)
    }

    /**
     * Test for getRequestForObjectLayout with mode.
     */
    @Test
    fun testGetRequestForObjectLayoutWithMode() {
        val request = RestRequest.getRequestForObjectLayout(
            TEST_API_VERSION, TEST_OBJECT_TYPE, null, null, TEST_MODE_EDIT, null
        )
        Assert.assertEquals("Wrong method", RestMethod.GET, request.method)
        Assert.assertEquals("Wrong path", "/services/data/$TEST_API_VERSION/ui-api/layout/$TEST_OBJECT_TYPE?mode=$TEST_MODE_EDIT", request.path)
        Assert.assertNull("Wrong request entity", request.requestBody)
        Assert.assertNull("Wrong additional headers", request.additionalHttpHeaders)
    }

    /**
     * Test for getRequestForObjectLayout without recordTypeId.
     */
    @Test
    fun testGetRequestForObjectLayoutWithoutRecordTypeId() {
        val request = RestRequest.getRequestForObjectLayout(
            TEST_API_VERSION, TEST_OBJECT_TYPE, null, null, null, null
        )
        Assert.assertEquals("Wrong method", RestMethod.GET, request.method)
        Assert.assertEquals("Wrong path", "/services/data/$TEST_API_VERSION/ui-api/layout/$TEST_OBJECT_TYPE", request.path)
        Assert.assertNull("Wrong request entity", request.requestBody)
        Assert.assertNull("Wrong additional headers", request.additionalHttpHeaders)
    }

    /**
     * Test for getRequestForObjectLayout with recordTypeId.
     */
    @Test
    fun testGetRequestForObjectLayoutWithRecordTypeId() {
        val request = RestRequest.getRequestForObjectLayout(
            TEST_API_VERSION, TEST_OBJECT_TYPE, null, null, null, TEST_RECORD_TYPE_ID
        )
        Assert.assertEquals("Wrong method", RestMethod.GET, request.method)
        Assert.assertEquals("Wrong path", "/services/data/$TEST_API_VERSION/ui-api/layout/$TEST_OBJECT_TYPE?recordTypeId=$TEST_RECORD_TYPE_ID", request.path)
        Assert.assertNull("Wrong request entity", request.requestBody)
        Assert.assertNull("Wrong additional headers", request.additionalHttpHeaders)
    }

    /**
     * Test for getRequestForCreate
     */
    @Test
    fun testGetRequestForCreate() {
        val request = RestRequest.getRequestForCreate(TEST_API_VERSION, TEST_OBJECT_TYPE, TEST_FIELDS)
        Assert.assertEquals("Wrong method", RestMethod.POST, request.method)
        Assert.assertEquals("Wrong path", "/services/data/$TEST_API_VERSION/sobjects/$TEST_OBJECT_TYPE", request.path)
        JSONTestHelper.assertSameJSON("Wrong request entity", JSONObject(TEST_FIELDS_STRING), JSONObject(bodyToString(request)))
        Assert.assertNull("Wrong additional headers", request.additionalHttpHeaders)
    }

    /**
     * Test for getRequestForRetrieve
     */
    @Test
    fun testGetRequestForRetrieve() {
        val request = RestRequest.getRequestForRetrieve(TEST_API_VERSION, TEST_OBJECT_TYPE, TEST_OBJECT_ID, TEST_FIELDS_LIST)
        Assert.assertEquals("Wrong method", RestMethod.GET, request.method)
        Assert.assertEquals("Wrong path", "/services/data/$TEST_API_VERSION/sobjects/$TEST_OBJECT_TYPE/$TEST_OBJECT_ID?fields=$TEST_FIELDS_LIST_STRING", request.path)
        Assert.assertNull("Wrong request entity", request.requestBody)
        Assert.assertNull("Wrong additional headers", request.additionalHttpHeaders)
    }

    /**
     * Test for getRequestForUpdate
     */
    @Test
    fun testGetRequestForUpdate() {
        val request = RestRequest.getRequestForUpdate(TEST_API_VERSION, TEST_OBJECT_TYPE, TEST_OBJECT_ID, TEST_FIELDS)
        Assert.assertEquals("Wrong method", RestMethod.PATCH, request.method)
        Assert.assertEquals("Wrong path", "/services/data/$TEST_API_VERSION/sobjects/$TEST_OBJECT_TYPE/$TEST_OBJECT_ID", request.path)
        JSONTestHelper.assertSameJSON("Wrong request entity", JSONObject(TEST_FIELDS_STRING), JSONObject(bodyToString(request)))
        Assert.assertNull("Wrong additional headers", request.additionalHttpHeaders)
    }

    /**
     * Test for getRequestForUpsert
     */
    @Test
    fun testGetRequestForUpsert() {
        val request = RestRequest.getRequestForUpsert(TEST_API_VERSION, TEST_OBJECT_TYPE, TEST_EXTERNAL_ID_FIELD, TEST_EXTERNAL_ID, TEST_FIELDS)
        Assert.assertEquals("Wrong method", RestMethod.PATCH, request.method)
        Assert.assertEquals("Wrong path", "/services/data/$TEST_API_VERSION/sobjects/$TEST_OBJECT_TYPE/$TEST_EXTERNAL_ID_FIELD/$TEST_EXTERNAL_ID", request.path)
        JSONTestHelper.assertSameJSON("Wrong request entity", JSONObject(TEST_FIELDS_STRING), JSONObject(bodyToString(request)))
        Assert.assertNull("Wrong additional headers", request.additionalHttpHeaders)
    }

    /**
     * Test for getRequestForDelete
     */
    @Test
    fun testGetRequestForDelete() {
        val request = RestRequest.getRequestForDelete(TEST_API_VERSION, TEST_OBJECT_TYPE, TEST_OBJECT_ID)
        Assert.assertEquals("Wrong method", RestMethod.DELETE, request.method)
        Assert.assertEquals("Wrong path", "/services/data/$TEST_API_VERSION/sobjects/$TEST_OBJECT_TYPE/$TEST_OBJECT_ID", request.path)
        Assert.assertNull("Wrong request entity", request.requestBody)
        Assert.assertNull("Wrong additional headers", request.additionalHttpHeaders)
    }

    /**
     * Test for getRequestForQuery
     */
    @Test
    fun testGetRequestForQuery() {
        val request = RestRequest.getRequestForQuery(TEST_API_VERSION, TEST_QUERY)
        Assert.assertEquals("Wrong method", RestMethod.GET, request.method)
        Assert.assertEquals("Wrong path", "/services/data/$TEST_API_VERSION/query?q=$TEST_QUERY", request.path)
        Assert.assertNull("Wrong request entity", request.requestBody)
        Assert.assertNull("Wrong additional headers", request.additionalHttpHeaders)
    }

    /**
     * Test for getRequestForQuery specifying a batch size
     */
    @Test
    fun testGetRequestForQueryWithBatchSize() {
        val request500 = RestRequest.getRequestForQuery(TEST_API_VERSION, TEST_QUERY, 500)
        val request199 = RestRequest.getRequestForQuery(TEST_API_VERSION, TEST_QUERY, 199)
        val request2001 = RestRequest.getRequestForQuery(TEST_API_VERSION, TEST_QUERY, 2001)
        Assert.assertEquals("Wrong method", RestMethod.GET, request500.method)
        Assert.assertEquals("Wrong path", "/services/data/$TEST_API_VERSION/query?q=$TEST_QUERY", request500.path)
        Assert.assertNull("Wrong request entity", request500.requestBody)
        Assert.assertEquals("batchSize=500", request500.additionalHttpHeaders?.get(RestRequest.SFORCE_QUERY_OPTIONS))
        Assert.assertEquals("batchSize=200", request199.additionalHttpHeaders?.get(RestRequest.SFORCE_QUERY_OPTIONS))
        Assert.assertNull("Wrong additional headers", request2001.additionalHttpHeaders)
    }

    /**
     * Test for getRequestForSearch
     */
    @Test
    fun testGetRequestForSeach() {
        val request = RestRequest.getRequestForSearch(TEST_API_VERSION, TEST_SEARCH)
        Assert.assertEquals("Wrong method", RestMethod.GET, request.method)
        Assert.assertEquals("Wrong path", "/services/data/$TEST_API_VERSION/search?q=$TEST_SEARCH", request.path)
        Assert.assertNull("Wrong request entity", request.requestBody)
        Assert.assertNull("Wrong additional headers", request.additionalHttpHeaders)
    }

    /**
     * Test for getRequestForSearchScopeAndOrder
     */
    @Test
    fun testGetRequestForSeachScopeAndOrder() {
        val request = RestRequest.getRequestForSearchScopeAndOrder(TEST_API_VERSION)
        Assert.assertEquals("Wrong method", RestMethod.GET, request.method)
        Assert.assertEquals("Wrong path", "/services/data/$TEST_API_VERSION/search/scopeOrder", request.path)
        Assert.assertNull("Wrong request entity", request.requestBody)
        Assert.assertNull("Wrong additional headers", request.additionalHttpHeaders)
    }

    /**
     * Test for getRequestForSearchResultLayout
     */
    @Test
    fun testGetRequestForSearchResultLayout() {
        val request = RestRequest.getRequestForSearchResultLayout(TEST_API_VERSION, TEST_OBJECTS_LIST)
        Assert.assertEquals("Wrong method", RestMethod.GET, request.method)
        Assert.assertEquals("Wrong path", "/services/data/$TEST_API_VERSION/search/layout?q=$TEST_OBJECTS_LIST_STRING", request.path)
        Assert.assertNull("Wrong request entity", request.requestBody)
        Assert.assertNull("Wrong additional headers", request.additionalHttpHeaders)
    }

    @Test
    fun testAdditionalHeaders() {
        val headers = hashMapOf("X-Foo" to "x-foo-header")
        val req = RestRequest(RestMethod.GET, "/my/foo/", headers)
        Assert.assertEquals("Wrong method", RestMethod.GET, req.method)
        Assert.assertEquals("Wrong path", "/my/foo/", req.path)
        Assert.assertNull("Wrong entity", req.requestBody)
        Assert.assertEquals("Wrong headers", headers, req.additionalHttpHeaders)
    }

    /**
     * Test for getCompositeRequest
     */
    @Test
    fun testGetCompositeRequest() {
        val requests = LinkedHashMap<String, RestRequest>()
        requests["ref1"] = RestRequest.getRequestForUpdate(TEST_API_VERSION, TEST_OBJECT_TYPE, TEST_OBJECT_ID, TEST_FIELDS)
        requests["ref2"] = RestRequest.getRequestForDelete(TEST_API_VERSION, TEST_OBJECT_TYPE, TEST_OBJECT_ID)
        val request = RestRequest.getCompositeRequest(TEST_API_VERSION, true, requests)
        Assert.assertEquals("Wrong method", RestMethod.POST, request.method)
        Assert.assertEquals("Wrong path", "/services/data/$TEST_API_VERSION/composite", request.path)
        Assert.assertNull("Wrong additional headers", request.additionalHttpHeaders)
        val expectedBodyJson = JSONObject()
        expectedBodyJson.put("allOrNone", true)
        expectedBodyJson.put(
            "compositeRequest",
            JSONArray(
                String.format(
                    "["
                            + "  {"
                            + "    \"method\": \"PATCH\","
                            + "    \"url\": \"/services/data/v99.0/sobjects/%s/%s\","
                            + "    \"body\": %s,"
                            + "    \"referenceId\": \"ref1\""
                            + "  },"
                            + "  {"
                            + "    \"method\": \"DELETE\","
                            + "    \"url\": \"/services/data/v99.0/sobjects/%s/%s\","
                            + "    \"referenceId\": \"ref2\""
                            + "  }"
                            + "]",
                    TEST_OBJECT_TYPE, TEST_OBJECT_ID, JSONObject(TEST_FIELDS),
                    TEST_OBJECT_TYPE, TEST_OBJECT_ID
                )
            )
        )
        val actualBodyJson = JSONObject(bodyToString(request))
        JSONTestHelper.assertSameJSON("Wrong request entity", expectedBodyJson, actualBodyJson)
    }

    /**
     * Test for getBatchRequest
     */
    @Test
    fun testGetBatchRequest() {
        val requests = arrayOf(
            RestRequest.getRequestForUpdate(TEST_API_VERSION, TEST_OBJECT_TYPE, TEST_OBJECT_ID, TEST_FIELDS),
            RestRequest.getRequestForDelete(TEST_API_VERSION, TEST_OBJECT_TYPE, TEST_OBJECT_ID)
        )
        val request = RestRequest.getBatchRequest(TEST_API_VERSION, true, listOf(*requests))
        Assert.assertEquals("Wrong method", RestMethod.POST, request.method)
        Assert.assertEquals("Wrong path", "/services/data/$TEST_API_VERSION/composite/batch", request.path)
        Assert.assertNull("Wrong additional headers", request.additionalHttpHeaders)
        val expectedBodyJson = JSONObject()
        expectedBodyJson.put("haltOnError", true)
        expectedBodyJson.put(
            "batchRequests",
            JSONArray(
                String.format(
                    "["
                            + "  {"
                            + "    \"method\": \"PATCH\","
                            + "    \"url\": \"v99.0/sobjects/%s/%s\","
                            + "    \"richInput\": %s"
                            + "  },"
                            + "  {"
                            + "    \"method\": \"DELETE\","
                            + "    \"url\": \"v99.0/sobjects/%s/%s\""
                            + "  }"
                            + "]",
                    TEST_OBJECT_TYPE, TEST_OBJECT_ID, JSONObject(TEST_FIELDS),
                    TEST_OBJECT_TYPE, TEST_OBJECT_ID
                )
            )
        )
        val actualBodyJson = JSONObject(bodyToString(request))
        JSONTestHelper.assertSameJSON("Wrong request entity", expectedBodyJson, actualBodyJson)
    }

    /**
     * Test for getRequestForSObjectTree
     */
    @Test
    fun testGetRequestForSObjectTree() {
        val childrenTrees = mutableListOf<RestRequest.SObjectTree>()
        childrenTrees.add(
            RestRequest.SObjectTree(TEST_OTHER_OBJECT_TYPE, TEST_OTHER_OBJECT_TYPE_PLURAL, TEST_REF_CHILD, TEST_OTHER_FIELDS as Map<String, Object>, null)
        )
        val recordTrees = mutableListOf<RestRequest.SObjectTree>()
        recordTrees.add(
            RestRequest.SObjectTree(TEST_OBJECT_TYPE, "", TEST_REF_PARENT, @Suppress("UNCHECKED_CAST") TEST_FIELDS as Map<String, Object>, childrenTrees)
        )
        val request = RestRequest.getRequestForSObjectTree(TEST_API_VERSION, TEST_OBJECT_TYPE, recordTrees)
        Assert.assertEquals("Wrong method", RestMethod.POST, request.method)
        Assert.assertEquals("Wrong path", "/services/data/$TEST_API_VERSION/composite/tree/$TEST_OBJECT_TYPE", request.path)
        Assert.assertNull("Wrong additional headers", request.additionalHttpHeaders)
        val expectedBodyJson = JSONObject()
        expectedBodyJson.put(
            "records",
            JSONArray(
                String.format(
                    "["
                            + "  {"
                            + "    \"name\": \"testAccount\","
                            + "    \"fieldX\": \"value with spaces\","
                            + "    \"attributes\": {"
                            + "      \"type\": \"%s\","
                            + "      \"referenceId\": \"%s\""
                            + "    },"
                            + "    \"%s\": {"
                            + "      \"records\": ["
                            + "        {"
                            + "          \"name\": \"testContact\","
                            + "          \"fieldY\": \"value with spaces\","
                            + "          \"attributes\": {"
                            + "            \"type\": \"%s\","
                            + "            \"referenceId\": \"%s\""
                            + "          }"
                            + "        }"
                            + "      ]"
                            + "    }"
                            + "  }"
                            + "]",
                    TEST_OBJECT_TYPE,
                    TEST_REF_PARENT,
                    TEST_OTHER_OBJECT_TYPE_PLURAL,
                    TEST_OTHER_OBJECT_TYPE,
                    TEST_REF_CHILD
                )
            )
        )

        val actualBodyJson = JSONObject(bodyToString(request))
        JSONTestHelper.assertSameJSON("Wrong request entity", expectedBodyJson, actualBodyJson)
    }

    @Test
    fun testGetRequestForNotification() {
        val notificationId = "testID"
        val request = RestRequest.getRequestForNotification(TEST_API_VERSION, notificationId)
        Assert.assertEquals("Wrong method", RestMethod.GET, request.method)
        Assert.assertEquals("Wrong path", "/services/data/$TEST_API_VERSION/connect/notifications/$notificationId", request.path)
    }

    @Test
    fun testGetRequestForNotificationUpdate() {
        val notificationId = "testID"
        val request = RestRequest.getRequestForNotificationUpdate(TEST_API_VERSION, notificationId, true, null)
        Assert.assertEquals("Wrong method", RestMethod.PATCH, request.method)
        Assert.assertEquals("Wrong path", "/services/data/$TEST_API_VERSION/connect/notifications/$notificationId", request.path)
    }

    @Test
    fun testGetRequestForNotificationsUpdate() {
        val notificationIds = listOf("testID1", "testID2")
        val request = RestRequest.getRequestForNotificationsUpdate(TEST_API_VERSION, notificationIds, null, true, null)
        Assert.assertEquals("Wrong method", RestMethod.PATCH, request.method)
        Assert.assertEquals("Wrong path", "/services/data/$TEST_API_VERSION/connect/notifications/", request.path)
        val expectedBodyJson = JSONObject()
        expectedBodyJson.put("notificationIds", JSONArray(notificationIds))
        expectedBodyJson.put("read", true)
        val actualBodyJson = JSONObject(bodyToString(request))
        JSONTestHelper.assertSameJSON("Wrong request entity", expectedBodyJson, actualBodyJson)
    }

    @Test
    fun testGetRequestForPrimingRecords() {
        val request = RestRequest.getRequestForPrimingRecords(TEST_API_VERSION, null, null)
        Assert.assertEquals("Wrong method", RestMethod.GET, request.method)
        Assert.assertEquals("Wrong path", "/services/data/$TEST_API_VERSION/connect/briefcase/priming-records", request.path)
    }

    @Test
    fun testGetRequestForPrimingRecordsWithRelayToken() {
        val request = RestRequest.getRequestForPrimingRecords(TEST_API_VERSION, "my-relay-token", null)
        Assert.assertEquals("Wrong method", RestMethod.GET, request.method)
        Assert.assertEquals("Wrong path", "/services/data/$TEST_API_VERSION/connect/briefcase/priming-records?relayToken=my-relay-token", request.path)
    }

    @Test
    fun testGetRequestForPrimingRecordsWithChangedAfterTimestamp() {
        val timestamp = PrimingRecordsResponse.TIMESTAMP_FORMAT.parse("2022-01-31T03:50:10.000Z")!!.time
        val request = RestRequest.getRequestForPrimingRecords(TEST_API_VERSION, null, timestamp)
        Assert.assertEquals("Wrong method", RestMethod.GET, request.method)
        Assert.assertEquals("Wrong path", "/services/data/$TEST_API_VERSION/connect/briefcase/priming-records?changedAfterTimestamp=2022-01-31T03%3A50%3A10.000Z", request.path)
    }

    @Test
    fun testGetRequestForPrimingRecordsWithRelayTokenAndChangedAfterTimestamp() {
        val timestamp = PrimingRecordsResponse.TIMESTAMP_FORMAT.parse("2022-01-31T03:50:10.000Z")!!.time
        val request = RestRequest.getRequestForPrimingRecords(TEST_API_VERSION, "my-relay-token", timestamp)
        Assert.assertEquals("Wrong method", RestMethod.GET, request.method)
        Assert.assertEquals("Wrong path", "/services/data/$TEST_API_VERSION/connect/briefcase/priming-records?relayToken=my-relay-token&changedAfterTimestamp=2022-01-31T03%3A50%3A10.000Z", request.path)
    }

    @Test
    fun testParsePrimingRecordsResponse() {
        val json = JSONObject(
            "{\n"
                    + "  \"primingRecords\": {\n"
                    + "    \"Account\": {\n"
                    + "      \"012S00000009B8HIAU\": [\n"
                    + "        {\n"
                    + "          \"id\": \"001S000001QEDnzIAH\",\n"
                    + "          \"systemModstamp\": \"2021-08-23T18:42:32.000Z\"\n"
                    + "        },\n"
                    + "        {\n"
                    + "          \"id\": \"001S000000va6rGIAQ\",\n"
                    + "          \"systemModstamp\": \"2019-02-09T02:19:38.000Z\"\n"
                    + "        }\n"
                    + "      ]\n"
                    + "    },\n"
                    + "    \"Contact\": {\n"
                    + "      \"012000000000000AAA\": [\n"
                    + "        {\n"
                    + "          \"id\": \"003S00000129813IAA\",\n"
                    + "          \"systemModstamp\": \"2018-12-22T06:13:59.000Z\"\n"
                    + "        },\n"
                    + "        {\n"
                    + "          \"id\": \"003S0000012LUhRIAW\",\n"
                    + "          \"systemModstamp\": \"2019-01-12T06:13:11.000Z\"\n"
                    + "        },\n"
                    + "        {\n"
                    + "          \"id\": \"003S0000012hWwRIAU\",\n"
                    + "          \"systemModstamp\": \"2019-01-30T00:59:06.000Z\"\n"
                    + "        }\n"
                    + "      ]\n"
                    + "    }\n"
                    + "  },\n"
                    + "  \"relayToken\": \"fake-token\",\n"
                    + "  \"ruleErrors\": ["
                    + "     {\n"
                    + "       \"ruleId\": \"rule-1\"\n"
                    + "     },\n"
                    + "     {\n"
                    + "       \"ruleId\": \"rule-2\"\n"
                    + "     }\n"
                    + "  ],\n"
                    + "  \"stats\": {\n"
                    + "    \"recordCountServed\": 100,\n"
                    + "    \"recordCountTotal\": 200,\n"
                    + "    \"ruleCountServed\": 2,\n"
                    + "    \"ruleCountTotal\": 3\n"
                    + "  }\n"
                    + "}"
        )
        val primingRecordsResponse = PrimingRecordsResponse(json)

        // Checking priming records
        // We have accounts and contacts
        Assert.assertEquals(2, primingRecordsResponse.primingRecords.size)
        // We have one record type for accounts and two accounts
        Assert.assertEquals(1, primingRecordsResponse.primingRecords["Account"]!!.size)
        Assert.assertEquals(2, primingRecordsResponse.primingRecords["Account"]!!["012S00000009B8HIAU"]!!.size)
        Assert.assertEquals("001S000001QEDnzIAH", primingRecordsResponse.primingRecords["Account"]!!["012S00000009B8HIAU"]!![0].id)
        Assert.assertEquals("001S000000va6rGIAQ", primingRecordsResponse.primingRecords["Account"]!!["012S00000009B8HIAU"]!![1].id)
        Assert.assertEquals(
            1629744152000L, primingRecordsResponse.primingRecords["Account"]!!["012S00000009B8HIAU"]!![0].systemModstamp!!
                .time
        )
        // We have one record type for contacts and three contacts
        Assert.assertEquals(1, primingRecordsResponse.primingRecords["Contact"]!!.size)
        Assert.assertEquals(3, primingRecordsResponse.primingRecords["Contact"]!!["012000000000000AAA"]!!.size)
        Assert.assertEquals("003S00000129813IAA", primingRecordsResponse.primingRecords["Contact"]!!["012000000000000AAA"]!![0].id)
        Assert.assertEquals("003S0000012LUhRIAW", primingRecordsResponse.primingRecords["Contact"]!!["012000000000000AAA"]!![1].id)
        Assert.assertEquals("003S0000012hWwRIAU", primingRecordsResponse.primingRecords["Contact"]!!["012000000000000AAA"]!![2].id)
        Assert.assertEquals(
            1545459239000L, primingRecordsResponse.primingRecords["Contact"]!!["012000000000000AAA"]!![0].systemModstamp!!
                .time
        )

        // Checking relay token
        Assert.assertEquals("fake-token", primingRecordsResponse.relayToken)
        // Checking rule errors
        Assert.assertEquals(2, primingRecordsResponse.ruleErrors.size)
        Assert.assertEquals("rule-1", primingRecordsResponse.ruleErrors[0].ruleId)
        Assert.assertEquals("rule-2", primingRecordsResponse.ruleErrors[1].ruleId)
        // Checking stats
        Assert.assertEquals(100, primingRecordsResponse.stats.recordCountServed)
        Assert.assertEquals(200, primingRecordsResponse.stats.recordCountTotal)
        Assert.assertEquals(2, primingRecordsResponse.stats.ruleCountServed)
        Assert.assertEquals(3, primingRecordsResponse.stats.ruleCountTotal)
    }

    @Throws(IOException::class)
    private fun bodyToString(request: RestRequest): String {
        val buffer = Buffer()
        request.requestBody!!.writeTo(buffer)
        return buffer.readUtf8()
    }

    @Throws(IOException::class)
    private fun bodyToString(requestBody: RequestBody): String {
        val buffer = Buffer()
        requestBody.writeTo(buffer)
        return buffer.readUtf8()
    }
}
