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

import android.net.Uri
import android.text.TextUtils
import com.salesforce.androidsdk.rest.BatchRequest.BatchRequestBuilder
import com.salesforce.androidsdk.rest.CompositeRequest.CompositeRequestBuilder
import com.salesforce.androidsdk.rest.files.ConnectUriBuilder
import com.salesforce.androidsdk.util.JSONObjectHelper
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.UnsupportedEncodingException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.HashMap
import java.util.LinkedHashMap
import java.util.Locale
import java.util.TimeZone

/**
 * RestRequest: Class to represent any REST request.
 *
 * The class offers factory methods to build RestRequest objects for all REST API actions:
 * - userinfo
 * - tokeninfo
 * - singleaccess
 * - versions
 * - resources
 * - describeGlobal
 * - metadata
 * - describe
 * - create
 * - retrieve
 * - upsert
 * - update
 * - delete
 * - query
 * - search
 * - searchScopeAndOrder
 * - searchResultLayout
 * - objectLayout
 * - composite
 * - batch
 * - tree
 * - notifications
 * - priming records
 * - sobject collection create
 * - sobject collection retrieve
 * - sobject collection update
 * - sobject collection upsert
 * - sobject collection delete
 *
 * It also has constructors to build any arbitrary request.
 */
open class RestRequest {

    val method: RestMethod
    val endpoint: RestEndpoint
    val path: String
    val requestBody: RequestBody?
    val additionalHttpHeaders: Map<String, String>?
    val requestBodyAsJson: JSONObject? // needed for composite and batch requests

    /**
     * Generic constructor for arbitrary requests without a body.
     *
     * @param method HTTP method used in the request (GET/POST/DELETE etc).
     * @param path   URI path. This is automatically resolved against the user's current instance host.
     */
    constructor(method: RestMethod, path: String) : this(method, path, null as RequestBody?, null)

    /**
     * Generic constructor for arbitrary requests without a body.
     *
     * @param method                HTTP method used for the request (GET/POST/DELETE etc).
     * @param path                  URI path. This will automatically be resolved against the user's current instance host.
     * @param additionalHttpHeaders Additional headers.
     */
    constructor(method: RestMethod, path: String, additionalHttpHeaders: Map<String, String>?) :
            this(method, path, null as RequestBody?, additionalHttpHeaders)

    /**
     * Generic constructor for arbitrary requests.
     *
     * @param method      HTTP method used for the request (GET/POST/DELETE etc).
     * @param path        URI path. This will automatically be resolved against the user's current instance host.
     * @param requestBody Request body, if one exists. Can be null.
     *
     *                    Note: Do not use this constructor if requestBody is not null and you want to build a batch or composite request.
     */
    constructor(method: RestMethod, path: String, requestBody: RequestBody?) :
            this(method, path, requestBody, null)

    /**
     * Generic constructor for arbitrary requests.
     *
     * @param method            HTTP method used for the request (GET/POST/DELETE etc).
     * @param path              URI path. This will automatically be resolved against the user's current instance host.
     * @param requestBodyAsJson Request body as JSON, if one exists. Can be null.
     *
     *                          Note: Use this constructor if requestBody is not null and you want to build a batch or composite request.
     */
    constructor(method: RestMethod, path: String, requestBodyAsJson: JSONObject?) :
            this(method, path, requestBodyAsJson, null)

    /**
     * Generic constructor for arbitrary requests.
     *
     * @param method                HTTP method used for the request (GET/POST/DELETE etc).
     * @param path                  URI path. This will automatically be resolved against the user's current instance host.
     * @param requestBody           Request body, if one exists. Can be null.
     * @param additionalHttpHeaders Additional headers.
     *
     *                              Note: Do not use this constructor if requestBody is not null and you want to build a batch or composite request.
     */
    constructor(method: RestMethod, path: String, requestBody: RequestBody?, additionalHttpHeaders: Map<String, String>?) :
            this(method, RestEndpoint.INSTANCE, path, requestBody, additionalHttpHeaders)

    /**
     * Generic constructor for arbitrary requests.
     *
     * @param method                HTTP method used for the request (GET/POST/DELETE etc).
     * @param path                  URI path. This will automatically be resolved against the user's current instance host.
     * @param requestBodyAsJson     Request body as JSON, if one exists. Can be null.
     * @param additionalHttpHeaders Additional headers.
     *
     *                              Note: Use this constructor if requestBody is not null and you want to build a batch or composite request.
     */
    constructor(method: RestMethod, path: String, requestBodyAsJson: JSONObject?, additionalHttpHeaders: Map<String, String>?) :
            this(method, RestEndpoint.INSTANCE, path, requestBodyAsJson, additionalHttpHeaders)

    /**
     * Generic constructor for arbitrary requests.
     *
     * @param method                HTTP method used for the request (GET/POST/DELETE etc).
     * @param endpoint              The endpoint associated with the request.
     * @param path                  URI path. This will be resolved against the user's current
     *                              Rest endpoint, as specified by the endpoint parameter.
     * @param requestBody           Request body, if one exists. Can be null.
     * @param additionalHttpHeaders Additional headers.
     */
    constructor(method: RestMethod, endpoint: RestEndpoint, path: String, requestBody: RequestBody?, additionalHttpHeaders: Map<String, String>?) {
        this.method = method
        this.endpoint = endpoint
        this.path = path
        this.requestBody = requestBody
        this.additionalHttpHeaders = additionalHttpHeaders
        this.requestBodyAsJson = null
    }

    /**
     * Generic constructor for arbitrary requests.
     *
     * @param method                HTTP method used for the request (GET/POST/DELETE etc).
     * @param endpoint              The endpoint associated with the request.
     * @param path                  URI path. This will be resolved against the user's current
     *                              Rest endpoint, as specified by the endpoint parameter.
     * @param requestBodyAsJson     Request body as JSON, if one exists. Can be null.
     * @param additionalHttpHeaders Additional headers.
     */
    constructor(method: RestMethod, endpoint: RestEndpoint, path: String, requestBodyAsJson: JSONObject?, additionalHttpHeaders: Map<String, String>?) {
        this.method = method
        this.endpoint = endpoint
        this.path = path
        this.requestBody = requestBodyAsJson?.toString()?.toRequestBody(MEDIA_TYPE_JSON)
        this.additionalHttpHeaders = additionalHttpHeaders
        this.requestBodyAsJson = requestBodyAsJson
    }

    override fun toString(): String {
        return try {
            asJSON().toString(2)
        } catch (e: JSONException) {
            super.toString()
        }
    }

    internal open fun asJSON(): JSONObject {
        val requestJson = JSONObject()
        requestJson.put(METHOD, method.toString())
        requestJson.put(URL, path)
        requestJson.put(BODY, requestBodyAsJson)
        if (additionalHttpHeaders != null)
            requestJson.put(HTTP_HEADERS, JSONObject(additionalHttpHeaders))
        return requestJson
    }

    /**
     * Enumeration for all HTTP methods.
     */
    enum class RestMethod {
        GET, POST, PUT, DELETE, HEAD, PATCH
    }

    /**
     * Enumeration for all REST API endpoints.
     */
    enum class RestEndpoint {
        LOGIN, INSTANCE
    }

    internal enum class RestAction(private val pathTemplate: String) {

        USERINFO(SERVICES_OAUTH2 + "userinfo"),
        TOKENINFO(SERVICES_OAUTH2 + "introspect"),
        SINGLEACCESS(SERVICES_OAUTH2 + "singleaccess"),
        VERSIONS(SERVICES_DATA),
        RESOURCES(SERVICES_DATA + "%s/"),
        DESCRIBE_GLOBAL(SERVICES_DATA + "%s/sobjects/"),
        METADATA(SERVICES_DATA + "%s/sobjects/%s/"),
        DESCRIBE(SERVICES_DATA + "%s/sobjects/%s/describe/"),
        CREATE(SERVICES_DATA + "%s/sobjects/%s"),
        RETRIEVE(SERVICES_DATA + "%s/sobjects/%s/%s"),
        UPSERT(SERVICES_DATA + "%s/sobjects/%s/%s/%s"),
        UPDATE(SERVICES_DATA + "%s/sobjects/%s/%s"),
        DELETE(SERVICES_DATA + "%s/sobjects/%s/%s"),
        QUERY(SERVICES_DATA + "%s/query"),
        QUERY_ALL(SERVICES_DATA + "%s/queryAll"),
        SEARCH(SERVICES_DATA + "%s/search"),
        SEARCH_SCOPE_AND_ORDER(SERVICES_DATA + "%s/search/scopeOrder"),
        SEARCH_RESULT_LAYOUT(SERVICES_DATA + "%s/search/layout"),
        OBJECT_LAYOUT(SERVICES_DATA + "%s/ui-api/layout/%s"),
        COMPOSITE(SERVICES_DATA + "%s/composite"),
        BATCH(SERVICES_DATA + "%s/composite/batch"),
        SOBJECT_TREE(SERVICES_DATA + "%s/composite/tree/%s"),
        SOBJECT_COLLECTION(SERVICES_DATA + "%s/composite/sobjects"),
        SOBJECT_COLLECTION_RETRIEVE(SERVICES_DATA + "%s/composite/sobjects/%s"),
        SOBJECT_COLLECTION_UPSERT(SERVICES_DATA + "%s/composite/sobjects/%s/%s"),
        NOTIFICATIONS_STATUS(SERVICES_DATA + "%s/connect/notifications/status"),
        NOTIFICATIONS(SERVICES_DATA + "%s/connect/notifications/%s"),
        PRIMING_RECORDS(SERVICES_DATA + "%s/connect/briefcase/priming-records"),
        LIMITS(SERVICES_DATA + "%s/limits");

        fun getPath(vararg args: Any?): String {
            return String.format(pathTemplate, *args)
        }
    }

    /**
     * Helper class for getRequestForSObjectTree.
     */
    class SObjectTree(
        @JvmField val objectType: String,
        @JvmField val objectTypePlural: String,
        @JvmField val referenceId: String,
        @JvmField val fields: Map<String, Object>,
        @JvmField val childrenTrees: List<SObjectTree>?
    ) {

        @Throws(JSONException::class)
        fun asJSON(): JSONObject {
            val parentJson = buildJsonForRecord(objectType, referenceId, fields)
            if (childrenTrees != null) {

                // Grouping children trees by type and figuring out object type to object type plural mapping
                val objectTypeToObjectTypePlural = HashMap<String, String>()
                val objectTypeToChildrenTrees = HashMap<String, MutableList<SObjectTree>>()
                for (childTree in childrenTrees) {
                    val childObjectType = childTree.objectType
                    if (!objectTypeToObjectTypePlural.containsKey(childObjectType)) {
                        objectTypeToObjectTypePlural[childObjectType] = childTree.objectTypePlural
                    }
                    if (!objectTypeToChildrenTrees.containsKey(childObjectType)) {
                        objectTypeToChildrenTrees[childObjectType] = ArrayList()
                    }
                    objectTypeToChildrenTrees[childObjectType]!!.add(childTree)
                }

                // Iterating through children
                for ((childrenObjectType, childrenTreesForType) in objectTypeToChildrenTrees) {
                    val childrenJsonArray = JSONArray()
                    for (childTree in childrenTreesForType) {
                        val childJson = buildJsonForRecord(childrenObjectType, childTree.referenceId, childTree.fields)
                        childrenJsonArray.put(childJson)
                    }
                    parentJson.put(
                        objectTypeToObjectTypePlural[childrenObjectType],
                        JSONObjectHelper.makeJSONObject(RECORDS, childrenJsonArray)
                    )
                }
            }

            // Done
            return parentJson
        }

        @Throws(JSONException::class)
        private fun buildJsonForRecord(objectType: String, referenceId: String, fields: Map<String, Object>): JSONObject {
            val jsonForAttributes = JSONObject()
            jsonForAttributes.put(REFERENCE_ID, referenceId)
            jsonForAttributes.put(TYPE, objectType)
            val jsonForRecord = JSONObject(fields)
            jsonForRecord.put(ATTRIBUTES, jsonForAttributes)
            return jsonForRecord
        }
    }

    companion object {

        /**
         * application/json media type
         */
        @JvmField
        val MEDIA_TYPE_JSON: MediaType = "application/json; charset=utf-8".toMediaType()

        /**
         * application/x-www-form-urlencoded media type
         */
        @JvmField
        val MEDIA_TYPE_FORM_URLENCODED: MediaType = "application/x-www-form-urlencoded".toMediaType()

        /**
         * utf_8 charset
         */
        @JvmField
        val UTF_8: String = StandardCharsets.UTF_8.name()

        /**
         * Misc keys appearing in requests
         */
        const val RECORDS: String = "records"
        const val METHOD: String = "method"
        const val URL: String = "url"
        const val BODY: String = "body"
        const val HTTP_HEADERS: String = "httpHeaders"
        const val COMPOSITE_REQUEST: String = "compositeRequest"
        const val BATCH_REQUESTS: String = "batchRequests"
        const val ALL_OR_NONE: String = "allOrNone"
        const val HALT_ON_ERROR: String = "haltOnError"
        const val RICH_INPUT: String = "richInput"
        const val SERVICES_DATA: String = "/services/data/"
        const val SERVICES_OAUTH2: String = "/services/oauth2/"
        const val REFERENCE_ID: String = "referenceId"
        const val TYPE: String = "type"
        const val ATTRIBUTES: String = "attributes"
        const val IF_UNMODIFIED_SINCE: String = "If-Unmodified-Since"
        const val SFORCE_QUERY_OPTIONS: String = "Sforce-Query-Options"
        const val BATCH_SIZE_OPTION: String = "batchSize"
        const val MIN_BATCH_SIZE: Int = 200
        const val MAX_BATCH_SIZE: Int = 2000
        const val DEFAULT_BATCH_SIZE: Int = 2000
        const val MAX_COLLECTION_RETRIEVE_SIZE: Int = 2000

        /**
         * HTTP date format
         */
        @JvmField
        val HTTP_DATE_FORMAT: DateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }

        /**
         * Salesforce timestamp format.
         */
        @JvmField
        val ISO8601_DATE_FORMAT: DateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)

        private const val TAG = "RestRequest"

        /**
         * Request to get information about the user making the request.
         * @return RestRequest object that requests user info.
         * @see [https://help.salesforce.com/articleView?id=remoteaccess_using_userinfo_endpoint.htm](https://help.salesforce.com/articleView?id=remoteaccess_using_userinfo_endpoint.htm)
         */
        @JvmStatic
        fun getRequestForUserInfo(): RestRequest {
            return RestRequest(RestMethod.GET, RestEndpoint.LOGIN, RestAction.USERINFO.getPath(), null as RequestBody?, null)
        }

        /**
         * Request to generate URL to bridge into UI sessions (a front door URL)
         * Applications should use that API instead of building front door URLs directly
         *
         * @param redirectUri A relative path that points to where the user is redirected when their new session begins.
         * @return RestRequest object that requests single access URL.
         * @see [https://help.salesforce.com/s/articleView?id=sf.frontdoor_singleaccess.htm](https://help.salesforce.com/s/articleView?id=sf.frontdoor_singleaccess.htm)
         */
        @JvmStatic
        @Throws(UnsupportedEncodingException::class)
        fun getRequestForSingleAccess(redirectUri: String): RestRequest {
            val requestBody = ("redirect_uri=" + URLEncoder.encode(redirectUri, UTF_8))
                .toRequestBody(MEDIA_TYPE_FORM_URLENCODED)
            return RestRequest(RestMethod.POST, RestEndpoint.INSTANCE, RestAction.SINGLEACCESS.getPath(), requestBody, null)
        }

        /**
         * Request to get summary information about each Salesforce.com version currently available.
         *
         * @return RestRequest object that requests the list of versions.
         * @see [http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_versions.htm](http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_versions.htm)
         */
        @JvmStatic
        fun getRequestForVersions(): RestRequest {
            return RestRequest(RestMethod.GET, RestAction.VERSIONS.getPath())
        }

        /**
         * Request to list available resources for the specified API version, including resource name and URI.
         *
         * @param apiVersion Salesforce API version.
         * @return RestRequest object that requests resources for the given API version.
         * @see [http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_discoveryresource.htm](http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_discoveryresource.htm)
         */
        @JvmStatic
        fun getRequestForResources(apiVersion: String): RestRequest {
            return RestRequest(RestMethod.GET, RestAction.RESOURCES.getPath(apiVersion))
        }

        /**
         * Request to list the available objects and their metadata for your organization's data.
         *
         * @param apiVersion Salesforce API version.
         * @return RestRequest object that requests objects and metadata for the given API version.
         * @see [http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_describeGlobal.htm](http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_describeGlobal.htm)
         */
        @JvmStatic
        fun getRequestForDescribeGlobal(apiVersion: String): RestRequest {
            return RestRequest(RestMethod.GET, RestAction.DESCRIBE_GLOBAL.getPath(apiVersion))
        }

        /**
         * Request to describe the individual metadata for the specified object.
         *
         * @param apiVersion Salesforce API version.
         * @param objectType Type of object for which the caller is requesting object metadata.
         * @return RestRequest object that requests an object's metadata for the given API version.
         * @see [http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_sobject_basic_info.htm](http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_sobject_basic_info.htm)
         */
        @JvmStatic
        fun getRequestForMetadata(apiVersion: String, objectType: String): RestRequest {
            return RestRequest(RestMethod.GET, RestAction.METADATA.getPath(apiVersion, objectType))
        }

        /**
         * Request to completely describe the individual metadata at all levels for the specified object.
         *
         * @param apiVersion Salesforce API version.
         * @param objectType Type of object for which the caller is requesting the metadata description.
         * @return RestRequest object that requests an object's metadata description for the given API version.
         * @see [http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_sobject_describe.htm](http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_sobject_describe.htm)
         */
        @JvmStatic
        fun getRequestForDescribe(apiVersion: String, objectType: String): RestRequest {
            return RestRequest(RestMethod.GET, RestAction.DESCRIBE.getPath(apiVersion, objectType))
        }

        /**
         * Request to create a record.
         *
         * @param apiVersion Salesforce API version.
         * @param objectType Type of record to be created.
         * @param fields     Map of the new record's fields and their values. Can be null.
         * @return RestRequest object that requests creation of a record.
         * @see [http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_sobject_retrieve.htm](http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_sobject_retrieve.htm)
         */
        @JvmStatic
        fun getRequestForCreate(apiVersion: String, objectType: String, fields: Map<String, Any>?): RestRequest {
            return RestRequest(RestMethod.POST, RestAction.CREATE.getPath(apiVersion, objectType), if (fields == null) null else JSONObject(fields))
        }

        /**
         * Request to retrieve a record by object ID.
         *
         * @param apiVersion Salesforce API version.
         * @param objectType Type of the requested record.
         * @param objectId   Salesforce ID of the requested record.
         * @param fieldList  List of requested field names.
         * @return RestRequest object that requests a record.
         * @throws UnsupportedEncodingException
         * @see [http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_sobject_retrieve.htm](http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_sobject_retrieve.htm)
         */
        @JvmStatic
        @Throws(UnsupportedEncodingException::class)
        fun getRequestForRetrieve(apiVersion: String, objectType: String, objectId: String, fieldList: List<String>?): RestRequest {
            val path = StringBuilder(RestAction.RETRIEVE.getPath(apiVersion, objectType, objectId))
            if (fieldList != null && fieldList.isNotEmpty()) {
                path.append("?fields=")
                path.append(URLEncoder.encode(TextUtils.join(",", fieldList), UTF_8))
            }
            return RestRequest(RestMethod.GET, path.toString())
        }

        /**
         * Request to update a record.
         *
         * @param apiVersion Salesforce API version.
         * @param objectType Type of the record.
         * @param objectId   Salesforce ID of the record.
         * @param fields     Map of the fields to be updated and their new values.
         * @return RestRequest object that requests a record update.
         * @see [http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_sobject_retrieve.htm](http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_sobject_retrieve.htm)
         */
        @JvmStatic
        fun getRequestForUpdate(apiVersion: String, objectType: String, objectId: String, fields: Map<String, Any>?): RestRequest {
            return getRequestForUpdate(apiVersion, objectType, objectId, fields, null)
        }

        /**
         * Request to update a record.
         *
         * @param apiVersion            Salesforce API version.
         * @param objectType            Type of the record.
         * @param objectId              Salesforce ID of the record.
         * @param fields                Map of the fields to be updated and their new values. Can be null.
         * @param ifUnmodifiedSinceDate Fulfill the request only if the record has not been modified since the given date.
         * @return RestRequest object that requests a record update.
         * @see [http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_sobject_retrieve.htm](http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_sobject_retrieve.htm)
         */
        @JvmStatic
        fun getRequestForUpdate(apiVersion: String, objectType: String, objectId: String, fields: Map<String, Any>?, ifUnmodifiedSinceDate: Date?): RestRequest {
            val additionalHttpHeaders = prepareConditionalHeader(IF_UNMODIFIED_SINCE, ifUnmodifiedSinceDate)
            return RestRequest(RestMethod.PATCH, RestAction.UPDATE.getPath(apiVersion, objectType, objectId), if (fields == null) null else JSONObject(fields), additionalHttpHeaders)
        }

        /**
         * Request to upsert (update or insert) a record.
         *
         * @param apiVersion      Salesforce API version.
         * @param objectType      Type of the record.
         * @param externalIdField Name of ID field in source data.
         * @param externalId      ID of source data record. Can be an empty string.
         * @param fields          Map of the fields to be upserted and their new values. Can be null.
         * @return RestRequest object that requests a record upsert.
         * @see [http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_sobject_upsert.htm](http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_sobject_upsert.htm)
         */
        @JvmStatic
        fun getRequestForUpsert(apiVersion: String, objectType: String, externalIdField: String, externalId: String?, fields: Map<String, Any>?): RestRequest {
            return RestRequest(
                if (externalId == null) RestMethod.POST else RestMethod.PATCH,
                RestAction.UPSERT.getPath(
                    apiVersion,
                    objectType,
                    externalIdField,
                    externalId ?: ""
                ),
                if (fields == null) null else JSONObject(fields)
            )
        }

        /**
         * Request to delete a record.
         *
         * @param apiVersion Salesforce API version.
         * @param objectType Type of the record.
         * @param objectId   Salesforce ID of the record.
         * @return RestRequest object that requests a record deletion.
         * @see [http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_sobject_retrieve.htm](http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_sobject_retrieve.htm)
         */
        @JvmStatic
        fun getRequestForDelete(apiVersion: String, objectType: String, objectId: String): RestRequest {
            return RestRequest(RestMethod.DELETE, RestAction.DELETE.getPath(apiVersion, objectType, objectId))
        }

        /**
         * Request to execute the specified SOSL search.
         *
         * @param apiVersion Salesforce API version.
         * @param q          SOSL search string.
         * @return RestRequest object that requests a SOSL search.
         * @throws UnsupportedEncodingException
         * @see [http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_search.htm](http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_search.htm)
         */
        @JvmStatic
        @Throws(UnsupportedEncodingException::class)
        fun getRequestForSearch(apiVersion: String, q: String): RestRequest {
            val path = StringBuilder(RestAction.SEARCH.getPath(apiVersion))
            path.append("?q=")
            path.append(URLEncoder.encode(q, UTF_8))
            return RestRequest(RestMethod.GET, path.toString())
        }

        /**
         * Request to execute the specified SOQL query.
         *
         * @param apiVersion Salesforce API version.
         * @param q          SOQL query string.
         * @return RestRequest object that requests a SOQL query.
         * @throws UnsupportedEncodingException
         * @see [http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_query.htm](http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_query.htm)
         */
        @JvmStatic
        @Throws(UnsupportedEncodingException::class)
        fun getRequestForQuery(apiVersion: String, q: String): RestRequest {
            return getRequestForQuery(apiVersion, q, DEFAULT_BATCH_SIZE)
        }

        /**
         * Request to execute the specified SOQL query.
         *
         * @param apiVersion Salesforce API version.
         * @param q          SOQL query string.
         * @param batchSize  Batch size: number between 200 and 2000 (default).
         * @return RestRequest object that requests a SOQL query.
         * @throws UnsupportedEncodingException
         * @see [http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_query.htm](http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_query.htm)
         */
        @JvmStatic
        @Throws(UnsupportedEncodingException::class)
        fun getRequestForQuery(apiVersion: String, q: String, batchSize: Int): RestRequest {
            val path = StringBuilder(RestAction.QUERY.getPath(apiVersion))
            path.append("?q=")
            path.append(URLEncoder.encode(q, UTF_8))
            val clampedBatchSize = Math.max(Math.min(batchSize, MAX_BATCH_SIZE), MIN_BATCH_SIZE)
            var headers: Map<String, String>? = null
            if (clampedBatchSize != DEFAULT_BATCH_SIZE) {
                headers = HashMap<String, String>()
                headers[SFORCE_QUERY_OPTIONS] = "$BATCH_SIZE_OPTION=$clampedBatchSize"
            }
            return RestRequest(RestMethod.GET, path.toString(), headers)
        }

        /**
         * Request to execute the specified SOQL query which includes deleted records because of a merge or delete in the result set.
         *
         * @param apiVersion Salesforce API version.
         * @param q          SOQL query string.
         * @return RestRequest object that requests a SOQL query that includes deleted records.
         * @throws UnsupportedEncodingException
         * @see [https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/resources_queryall.htm](https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/resources_queryall.htm)
         */
        @JvmStatic
        @Throws(UnsupportedEncodingException::class)
        fun getRequestForQueryAll(apiVersion: String, q: String): RestRequest {
            val path = StringBuilder(RestAction.QUERY_ALL.getPath(apiVersion))
            path.append("?q=")
            path.append(URLEncoder.encode(q, UTF_8))
            return RestRequest(RestMethod.GET, path.toString())
        }

        /**
         * Request to get search scope and order.
         *
         * @param apiVersion Salesforce API version.
         * @return RestRequest object that requests the search scope and order for the given API version.
         * @see [http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_search_scope_order.htm](http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_search_scope_order.htm)
         */
        @JvmStatic
        fun getRequestForSearchScopeAndOrder(apiVersion: String): RestRequest {
            return RestRequest(RestMethod.GET, RestAction.SEARCH_SCOPE_AND_ORDER.getPath(apiVersion))
        }

        /**
         * Request to get search result layouts
         *
         * @param apiVersion Salesforce API version.
         * @param objectList List of objects whose search result layouts are being requested.
         * @return RestRequest object that requests the search result layout for the given list of objects.
         * @throws UnsupportedEncodingException
         * @see [http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_search_layouts.htm](http://www.salesforce.com/us/developer/docs/api_rest/Content/resources_search_layouts.htm)
         */
        @JvmStatic
        @Throws(UnsupportedEncodingException::class)
        fun getRequestForSearchResultLayout(apiVersion: String, objectList: List<String>): RestRequest {
            val path = StringBuilder(RestAction.SEARCH_RESULT_LAYOUT.getPath(apiVersion))
            path.append("?q=")
            path.append(URLEncoder.encode(TextUtils.join(",", objectList).toString(), UTF_8))
            return RestRequest(RestMethod.GET, path.toString())
        }

        /**
         * Request to get object layout data.
         *
         * @param apiVersion    Salesforce API version.
         * @param objectAPIName Object API name.
         * @param formFactor    Form factor. Could be "Large", "Medium" or "Small". Default value is "Large".
         * @param layoutType    Layout type. Could be "Compact" or "Full". Default value is "Full".
         * @param mode          Mode. Could be "Create", "Edit" or "View". Default value is "View".
         * @param recordTypeId  Record type ID. Default will be used if not supplied.
         * @return RestRequest object that requests the object layout for the given parameters.
         * @see [https://developer.salesforce.com/docs/atlas.en-us.uiapi.meta/uiapi/ui_api_resources_record_layout.htm](https://developer.salesforce.com/docs/atlas.en-us.uiapi.meta/uiapi/ui_api_resources_record_layout.htm)
         */
        @JvmStatic
        fun getRequestForObjectLayout(
            apiVersion: String, objectAPIName: String,
            formFactor: String?, layoutType: String?,
            mode: String?, recordTypeId: String?
        ): RestRequest {
            val path = StringBuilder(RestAction.OBJECT_LAYOUT.getPath(apiVersion, objectAPIName))
            path.append("?")
            if (!TextUtils.isEmpty(formFactor)) {
                path.append("formFactor=")
                path.append(formFactor)
                path.append("&")
            }
            if (!TextUtils.isEmpty(layoutType)) {
                path.append("layoutType=")
                path.append(layoutType)
                path.append("&")
            }
            if (!TextUtils.isEmpty(mode)) {
                path.append("mode=")
                path.append(mode)
                path.append("&")
            }
            if (!TextUtils.isEmpty(recordTypeId)) {
                path.append("recordTypeId=")
                path.append(recordTypeId)
            }
            if (path[path.length - 1] == '?' || path[path.length - 1] == '&') {
                path.deleteCharAt(path.length - 1)
            }
            return RestRequest(RestMethod.GET, path.toString())
        }

        /**
         * Composite request
         *
         * @param apiVersion      Salesforce API version.
         * @param allOrNone       Indicates whether the request will accept partially complete results.
         * @param refIdToRequests Linked map of reference IDs to RestRequest objects. The requests will be played in order in which they were added.
         * @return RestRequest object that requests execution of the given composite request.
         * @see [https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/resources_composite_composite.htm](https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/resources_composite_composite.htm)
         */
        @JvmStatic
        @Throws(JSONException::class)
        fun getCompositeRequest(apiVersion: String, allOrNone: Boolean, refIdToRequests: LinkedHashMap<String, RestRequest>): CompositeRequest {
            val builder = CompositeRequestBuilder()
            for ((key, value) in refIdToRequests) {
                builder.addRequest(key, value)
            }
            builder.setAllOrNone(allOrNone)
            return builder.build(apiVersion)
        }

        /**
         * Batch request
         * @param apiVersion  Salesforce API version.
         * @param haltOnError Indicates whether to stop processing the batch if an error occurs.
         * @param requests    List of RestRequest objects.
         * @return RestRequest object that requests execution of the given batch of requests.
         * @see [https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/resources_composite_batch.htm](https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/resources_composite_batch.htm)
         */
        @JvmStatic
        @Throws(JSONException::class)
        fun getBatchRequest(apiVersion: String, haltOnError: Boolean, requests: List<RestRequest>): BatchRequest {
            val builder = BatchRequestBuilder()
            for (request in requests) {
                builder.addRequest(request)
            }
            builder.setHaltOnError(haltOnError)
            return builder.build(apiVersion)
        }

        /**
         * Request to create one or more sObject trees with root records of the specified type.
         *
         * @param apiVersion  Salesforce API version.
         * @param objectType  Type of object requested.
         * @param objectTrees List of [SObjectTree] objects.
         * @return RestRequest object that requests creation of one or more sObject trees.
         * @throws JSONException
         * @see [https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/resources_composite_sobject_tree.htm](https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/resources_composite_sobject_tree.htm)
         */
        @JvmStatic
        @Throws(JSONException::class)
        fun getRequestForSObjectTree(apiVersion: String, objectType: String, objectTrees: List<SObjectTree>): RestRequest {
            val jsonTrees = JSONArray()
            for (objectTree in objectTrees) {
                jsonTrees.put(objectTree.asJSON())
            }
            val body = JSONObjectHelper.makeJSONObject(RECORDS, jsonTrees).toString().toRequestBody(MEDIA_TYPE_JSON)
            return RestRequest(RestMethod.POST, RestAction.SOBJECT_TREE.getPath(apiVersion, objectType), body)
        }

        /**
         * Request to get status of notifications for the user.
         *
         * @param apiVersion Salesforce API version.
         *
         * @see [https://developer.salesforce.com/docs/atlas.en-us.chatterapi.meta/chatterapi/connect_resources_notifications_status.htm](https://developer.salesforce.com/docs/atlas.en-us.chatterapi.meta/chatterapi/connect_resources_notifications_status.htm)
         */
        @JvmStatic
        fun getRequestForNotificationsStatus(apiVersion: String): RestRequest {
            return RestRequest(RestMethod.GET, RestAction.NOTIFICATIONS_STATUS.getPath(apiVersion))
        }

        /**
         * Request to get a notification.
         *
         * @param apiVersion     Salesforce API version.
         * @param notificationId ID of notification.
         *
         * @see [https://developer.salesforce.com/docs/atlas.en-us.chatterapi.meta/chatterapi/connect_resource_notifications_specific.htm](https://developer.salesforce.com/docs/atlas.en-us.chatterapi.meta/chatterapi/connect_resource_notifications_specific.htm)
         */
        @JvmStatic
        fun getRequestForNotification(apiVersion: String, notificationId: String): RestRequest {
            return RestRequest(RestMethod.GET, RestAction.NOTIFICATIONS.getPath(apiVersion, notificationId))
        }

        /**
         * Request for updating a notification.
         *
         * @param apiVersion     Salesforce API version.
         * @param notificationId ID of notification.
         * @param read           Marks notification as read (true) or unread (false). If null, field won't be updated.
         *                       Required if `seen` not provided.
         * @param seen           Marks notification as seen (true) or unseen (false). If null, field won't be updated.
         *                       Required if `read` not provided.
         *
         * @see [https://developer.salesforce.com/docs/atlas.en-us.chatterapi.meta/chatterapi/connect_resource_notifications_specific.htm](https://developer.salesforce.com/docs/atlas.en-us.chatterapi.meta/chatterapi/connect_resource_notifications_specific.htm)
         */
        @JvmStatic
        fun getRequestForNotificationUpdate(apiVersion: String, notificationId: String, read: Boolean?, seen: Boolean?): RestRequest {
            val parameters = HashMap<String, Any>()
            if (read != null) {
                parameters["read"] = read
            }
            if (seen != null) {
                parameters["seen"] = seen
            }
            val path = RestAction.NOTIFICATIONS.getPath(apiVersion, notificationId)
            return RestRequest(RestMethod.PATCH, path, JSONObject(parameters as Map<*, *>))
        }

        /**
         * Request for getting notifications.
         *
         * @param apiVersion Salesforce API version.
         * @param size       Number of notifications to get.
         * @param before     Get notifications occurring before the provided date. Shouldn't be used with `after`.
         * @param after      Get notifications occurring after the provided date. Shouldn't be used with `before`.
         *
         * @see [https://developer.salesforce.com/docs/atlas.en-us.chatterapi.meta/chatterapi/connect_resources_notifications_list.htm](https://developer.salesforce.com/docs/atlas.en-us.chatterapi.meta/chatterapi/connect_resources_notifications_list.htm)
         */
        @JvmStatic
        fun getRequestForNotifications(apiVersion: String, size: Int?, before: Date?, after: Date?): RestRequest {
            val parameters = HashMap<String, String>()
            if (size != null) {
                parameters["size"] = size.toString()
            }
            if (before != null) {
                parameters["before"] = ISO8601_DATE_FORMAT.format(before)
            }
            if (after != null) {
                parameters["after"] = ISO8601_DATE_FORMAT.format(after)
            }

            val builder = ConnectUriBuilder(Uri.parse(RestAction.NOTIFICATIONS.getPath(apiVersion, "")).buildUpon())
            for ((key, value) in parameters) {
                builder.appendQueryParam(key, value)
            }
            return RestRequest(RestMethod.GET, builder.toString())
        }

        /**
         * Request for updating notifications.
         *
         * @param apiVersion      Salesforce API version.
         * @param notificationIds IDs of notifications to get. Shouldn't be used with `before`.
         * @param before          Get notifications before the provided date. Shouldn't be used with `notificationIds`.
         * @param read            Marks notifications as read (true) or unread (false). If null, field won't be updated.
         *                        Required if `seen` not provided.
         * @param seen            Marks notifications as seen (true) or unseen (false). If null, field won't be updated.
         *                        Required if `read` not provided.
         */
        @JvmStatic
        fun getRequestForNotificationsUpdate(apiVersion: String, notificationIds: List<String>?, before: Date?, read: Boolean?, seen: Boolean?): RestRequest {
            val parameters = HashMap<String, Any>()
            if (notificationIds != null) {
                parameters["notificationIds"] = notificationIds
            }
            if (before != null) {
                parameters["before"] = ISO8601_DATE_FORMAT.format(before)
            }
            if (read != null) {
                parameters["read"] = read
            }
            if (seen != null) {
                parameters["seen"] = seen
            }
            val path = RestAction.NOTIFICATIONS.getPath(apiVersion, "")
            return RestRequest(RestMethod.PATCH, path, JSONObject(parameters as Map<*, *>))
        }

        /**
         * Request for getting list of record related to offline briefcase
         *
         * @param apiVersion       Salesforce API version.
         * @param relayToken       Relay token (to get next page of results) - or null
         * @param changedAfterTime To only get ids of records that changed after given time - or null
         *
         * @see [https://developer.salesforce.com/docs/atlas.en-us.chatterapi.meta/chatterapi/connect_resources_briefcase_priming_records.htm](https://developer.salesforce.com/docs/atlas.en-us.chatterapi.meta/chatterapi/connect_resources_briefcase_priming_records.htm)
         */
        @JvmStatic
        @Throws(UnsupportedEncodingException::class)
        fun getRequestForPrimingRecords(apiVersion: String, relayToken: String?, changedAfterTime: Long?): RestRequest {
            val path = StringBuilder(RestAction.PRIMING_RECORDS.getPath(apiVersion))
            if (relayToken != null) {
                path.append("?relayToken=")
                path.append(URLEncoder.encode(relayToken, UTF_8))
            }
            if (changedAfterTime != null) {
                path.append(if (relayToken != null) "&" else "?")
                path.append("changedAfterTimestamp=")
                path.append(URLEncoder.encode(PrimingRecordsResponse.TIMESTAMP_FORMAT.format(Date(changedAfterTime)), UTF_8))
            }
            return RestRequest(RestMethod.GET, path.toString())
        }

        /**
         * Request for creating multiple records with fewer round trips
         *
         * @param apiVersion Salesforce API version.
         * @param allOrNone  Indicates whether to roll back the entire request when the creation of any object fails (true) or to continue with the independent creation of other objects in the request.
         * @param records    A list of sObjects.
         *
         * @see [https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/resources_composite_sobjects_collections_create.htm](https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/resources_composite_sobjects_collections_create.htm)
         */
        @JvmStatic
        @Throws(JSONException::class)
        fun getRequestForCollectionCreate(apiVersion: String, allOrNone: Boolean, records: JSONArray): RestRequest {
            val requestBodyAsJson = JSONObject()
            requestBodyAsJson.put(ALL_OR_NONE, allOrNone)
            requestBodyAsJson.put(RECORDS, records)
            return RestRequest(RestMethod.POST, RestAction.SOBJECT_COLLECTION.getPath(apiVersion), requestBodyAsJson)
        }

        /**
         * Request for retrieving multiple records with fewer round trips
         *
         * @param apiVersion Salesforce API version.
         * @param objectType Type of the requested record.
         * @param objectIds  List of Salesforce IDs of the requested records.
         * @param fieldList  List of requested field names.
         *
         * @see [https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/resources_composite_sobjects_collections_retrieve.htm](https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/resources_composite_sobjects_collections_retrieve.htm)
         */
        @JvmStatic
        @Throws(UnsupportedEncodingException::class, JSONException::class)
        fun getRequestForCollectionRetrieve(apiVersion: String, objectType: String, objectIds: List<String>, fieldList: List<String>): RestRequest {
            val path = StringBuilder(RestAction.SOBJECT_COLLECTION_RETRIEVE.getPath(apiVersion, objectType))
            // Using a post body which is allowed by the end point and allows more ids to be sent up (2000 instead of ~800)
            val body = JSONObject()
            body.put("ids", JSONArray(objectIds))
            body.put("fields", JSONArray(fieldList))
            return RestRequest(RestMethod.POST, path.toString(), body)
        }

        /**
         * Request for updating multiple records with fewer round trips
         *
         * @param apiVersion Salesforce API version.
         * @param allOrNone  Indicates whether to roll back the entire request when the update of any object fails (true) or to continue with the independent update of other objects in the request.
         * @param records    A list of sObjects.
         *
         * @see [https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/resources_composite_sobjects_collections_update.htm](https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/resources_composite_sobjects_collections_update.htm)
         */
        @JvmStatic
        @Throws(JSONException::class)
        fun getRequestForCollectionUpdate(apiVersion: String, allOrNone: Boolean, records: JSONArray): RestRequest {
            val requestBodyAsJson = JSONObject()
            requestBodyAsJson.put(ALL_OR_NONE, allOrNone)
            requestBodyAsJson.put(RECORDS, records)
            return RestRequest(RestMethod.PATCH, RestAction.SOBJECT_COLLECTION.getPath(apiVersion), requestBodyAsJson)
        }

        /**
         * Request for upserting multiple records with fewer round trips
         *
         * @param apiVersion      Salesforce API version.
         * @param allOrNone       Indicates whether to roll back the entire request when the upsert of any object fails (true) or to continue with the independent upsert of other objects in the request.
         * @param objectType      Type of the requested record.
         * @param externalIdField Name of ID field in source data.
         * @param records         A list of sObjects.
         *
         * @see [https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/resources_composite_sobjects_collections_upsert.htm](https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/resources_composite_sobjects_collections_upsert.htm)
         */
        @JvmStatic
        @Throws(JSONException::class)
        fun getRequestForCollectionUpsert(apiVersion: String, allOrNone: Boolean, objectType: String, externalIdField: String, records: JSONArray): RestRequest {
            val requestBodyAsJson = JSONObject()
            requestBodyAsJson.put(ALL_OR_NONE, allOrNone)
            requestBodyAsJson.put(RECORDS, records)
            return RestRequest(RestMethod.PATCH, RestAction.SOBJECT_COLLECTION_UPSERT.getPath(apiVersion, objectType, externalIdField), requestBodyAsJson)
        }

        /**
         * Request for deleting multiple records with fewer round trips
         *
         * @param apiVersion Salesforce API version.
         * @param allOrNone  Indicates whether to roll back the entire request when the delete of any object fails (true) or to continue with the independent delete of other objects in the request.
         * @param objectIds  List of Salesforce IDs of the records to delete.
         * @see [https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/resources_composite_sobjects_collections_delete.htm](https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/resources_composite_sobjects_collections_delete.htm)
         */
        @JvmStatic
        @Throws(UnsupportedEncodingException::class)
        fun getRequestForCollectionDelete(apiVersion: String, allOrNone: Boolean, objectIds: List<String>): RestRequest {
            val path = StringBuilder(RestAction.SOBJECT_COLLECTION.getPath(apiVersion))
            path.append("?allOrNone=$allOrNone&ids=")
            path.append(URLEncoder.encode(TextUtils.join(",", objectIds), UTF_8))
            return RestRequest(RestMethod.DELETE, path.toString())
        }

        /**
         * Request for getting information about limits in your org
         *
         * @param apiVersion Salesforce API version.
         * @see [https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/resources_limits.htm](https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/resources_limits.htm)
         */
        @JvmStatic
        fun getRequestForLimits(apiVersion: String): RestRequest {
            val path = StringBuilder(RestAction.LIMITS.getPath(apiVersion))
            return RestRequest(RestMethod.GET, path.toString())
        }

        /**
         * Cheap request to re-hydrate access token
         * @param apiVersion
         * @return a rest request
         */
        @JvmStatic
        fun getCheapRequest(apiVersion: String): RestRequest {
            return getRequestForResources(apiVersion)
        }

        /**
         * Helper method for creating conditional HTTP header.
         *
         * @param headerName Name of header.
         * @param date       Date of header. If null, this method returns null.
         * @return Map of header name and date, or null if no date is provided.
         */
        private fun prepareConditionalHeader(headerName: String, date: Date?): Map<String, String>? {
            return if (date != null) {
                val additionalHttpHeaders = HashMap<String, String>()
                additionalHttpHeaders[headerName] = HTTP_DATE_FORMAT.format(date)
                additionalHttpHeaders
            } else {
                null
            }
        }
    }
}
