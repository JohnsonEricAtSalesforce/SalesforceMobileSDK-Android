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
package com.salesforce.androidsdk.smartstore.store

import android.text.TextUtils
import com.salesforce.androidsdk.smartstore.store.SmartStore.SmartStoreException
import com.salesforce.androidsdk.util.JSONObjectHelper
import org.json.JSONException
import org.json.JSONObject
import java.util.regex.Pattern

/**
 * Simple class to represent a query spec
 */
class QuerySpec private constructor(
    @JvmField val queryType: QueryType,
    @JvmField val pageSize: Int,
    @JvmField val smartSql: String,
    @JvmField val countSmartSql: String,
    @JvmField val idsSmartSql: String,
    @JvmField val soupName: String?,
    @JvmField val selectPaths: Array<String>?,
    @JvmField val path: String?,
    @JvmField val orderPath: String?,
    @JvmField val order: Order?,
    @JvmField val matchKey: String?,
    @JvmField val beginKey: String?,
    @JvmField val endKey: String?,
    @JvmField val likeKey: String?
) {

    // Private constructor for soup query spec
    private constructor(
        soupName: String,
        selectPaths: Array<String>?,
        queryType: QueryType,
        matchKey: String?,
        beginKey: String?,
        endKey: String?,
        likeKey: String?,
        orderPath: String?,
        order: Order?,
        pageSize: Int,
        path: String?
    ) : this(
        queryType = queryType,
        pageSize = pageSize,
        smartSql = computeSmartSqlStatic(soupName, selectPaths, queryType, matchKey, beginKey, endKey, likeKey, orderPath, order, path),
        countSmartSql = computeCountSmartSqlStatic(soupName, queryType, matchKey, beginKey, endKey, likeKey, path),
        idsSmartSql = computeIdsSmartSqlStatic(soupName, queryType, matchKey, beginKey, endKey, likeKey, orderPath, order, path),
        soupName = soupName,
        selectPaths = selectPaths,
        path = path,
        orderPath = orderPath,
        order = order,
        matchKey = matchKey,
        beginKey = beginKey,
        endKey = endKey,
        likeKey = likeKey
    )

    // Private constructor for smart query spec
    private constructor(smartSql: String, pageSize: Int) : this(
        queryType = QueryType.smart,
        pageSize = pageSize,
        smartSql = smartSql,
        countSmartSql = String.format(SELECT_COUNT_FROM, smartSql),
        idsSmartSql = String.format(SELECT_ID_FROM, smartSql),
        soupName = null,
        selectPaths = null,
        path = null,
        orderPath = null,
        order = null,
        matchKey = null,
        beginKey = null,
        endKey = null,
        likeKey = null
    )

    /**
     * @return args going with the sql predicate returned by getKeyPredicate
     */
    fun getArgs(): Array<String>? {
        return when (queryType) {
            QueryType.exact -> arrayOf(matchKey!!)
            QueryType.like -> arrayOf(likeKey!!)
            QueryType.range -> {
                if (beginKey == null && endKey == null) null
                else if (endKey == null) arrayOf(beginKey!!)
                else if (beginKey == null) arrayOf(endKey)
                else arrayOf(beginKey, endKey)
            }
            QueryType.match -> null // baking matchKey into query
            QueryType.smart -> null
        }
    }

    /**
     * Query type enum
     */
    enum class QueryType {
        exact,
        range,
        like,
        match,
        smart
    }

    /**
     * Simple class to represent query order
     */
    enum class Order(@JvmField val sql: String) {
        ascending("ASC"),
        descending("DESC")
    }

    companion object {
        // Constants
        private const val SELECT = "SELECT "
        private const val FROM = "FROM "
        private const val WHERE = "WHERE "
        private const val ORDER_BY = "ORDER BY "

        private const val SELECT_COUNT = SELECT + "count(*) "
        private const val SELECT_COUNT_FROM = "$SELECT_COUNT FROM (%s)"
        private val SELECT_ID = SELECT + SmartStore.ID_COL + " "
        private val SELECT_ID_FROM = "$SELECT_ID FROM (%s)"

        // Keys in json
        const val BEGIN_KEY = "beginKey"
        const val END_KEY = "endKey"
        const val INDEX_PATH = "indexPath"
        const val LIKE_KEY = "likeKey"
        const val MATCH_KEY = "matchKey"
        const val SMART_SQL = "smartSql"
        const val ORDER_PATH = "orderPath"
        const val ORDER = "order"
        const val PAGE_SIZE = "pageSize"
        const val QUERY_TYPE = "queryType"
        const val SELECT_PATHS = "selectPaths"

        /**
         * Return query spec for an all query
         */
        @JvmStatic
        fun buildAllQuerySpec(soupName: String, orderPath: String?, order: Order?, pageSize: Int): QuerySpec {
            return buildAllQuerySpec(soupName, null, orderPath, order, pageSize)
        }

        /**
         * Return query spec for an all query
         */
        @JvmStatic
        fun buildAllQuerySpec(soupName: String, selectPaths: Array<String>?, orderPath: String?, order: Order?, pageSize: Int): QuerySpec {
            return QuerySpec(soupName, selectPaths, QueryType.range, null, null, null, null, orderPath, order, pageSize, null)
        }

        /**
         * Return a query spec for an exact match query
         */
        @JvmStatic
        fun buildExactQuerySpec(soupName: String, path: String, exactMatchKey: String, orderPath: String?, order: Order?, pageSize: Int): QuerySpec {
            return buildExactQuerySpec(soupName, null, path, exactMatchKey, orderPath, order, pageSize)
        }

        /**
         * Return a query spec for an exact match query
         */
        @JvmStatic
        fun buildExactQuerySpec(soupName: String, selectPaths: Array<String>?, path: String, exactMatchKey: String, orderPath: String?, order: Order?, pageSize: Int): QuerySpec {
            return QuerySpec(soupName, selectPaths, QueryType.exact, exactMatchKey, null, null, null, orderPath, order, pageSize, path)
        }

        /**
         * Return a query spec for a range query
         */
        @JvmStatic
        fun buildRangeQuerySpec(soupName: String, path: String?, beginKey: String?, endKey: String?, orderPath: String?, order: Order?, pageSize: Int): QuerySpec {
            return buildRangeQuerySpec(soupName, null, path, beginKey, endKey, orderPath, order, pageSize)
        }

        /**
         * Return a query spec for a range query
         */
        @JvmStatic
        fun buildRangeQuerySpec(soupName: String, selectPaths: Array<String>?, path: String?, beginKey: String?, endKey: String?, orderPath: String?, order: Order?, pageSize: Int): QuerySpec {
            return QuerySpec(soupName, selectPaths, QueryType.range, null, beginKey, endKey, null, orderPath, order, pageSize, path)
        }

        /**
         * Return a query spec for a like query
         */
        @JvmStatic
        fun buildLikeQuerySpec(soupName: String, path: String, likeKey: String, orderPath: String?, order: Order?, pageSize: Int): QuerySpec {
            return buildLikeQuerySpec(soupName, null, path, likeKey, orderPath, order, pageSize)
        }

        /**
         * Return a query spec for a like query
         */
        @JvmStatic
        fun buildLikeQuerySpec(soupName: String, selectPaths: Array<String>?, path: String, likeKey: String, orderPath: String?, order: Order?, pageSize: Int): QuerySpec {
            return QuerySpec(soupName, selectPaths, QueryType.like, null, null, null, likeKey, orderPath, order, pageSize, path)
        }

        /**
         * Return a query spec for a match query (full-text search)
         */
        @JvmStatic
        fun buildMatchQuerySpec(soupName: String, path: String?, matchKey: String, orderPath: String?, order: Order?, pageSize: Int): QuerySpec {
            return buildMatchQuerySpec(soupName, null, path, matchKey, orderPath, order, pageSize)
        }

        /**
         * Return a query spec for a match query (full-text search)
         */
        @JvmStatic
        fun buildMatchQuerySpec(soupName: String, selectPaths: Array<String>?, path: String?, matchKey: String, orderPath: String?, order: Order?, pageSize: Int): QuerySpec {
            return QuerySpec(soupName, selectPaths, QueryType.match, matchKey, null, null, null, orderPath, order, pageSize, path)
        }

        /**
         * Return a query spec for a smart query
         */
        @JvmStatic
        fun buildSmartQuerySpec(smartSql: String, pageSize: Int): QuerySpec {
            return QuerySpec(smartSql, pageSize)
        }

        /**
         * fts5 doesn't allow WHERE column MATCH 'value' - only allows WHERE table MATCH 'column:value'
         * This method changes the matchKey to add field: in the right places
         */
        @JvmStatic
        fun qualifyMatchKey(field: String?, matchKey: String): String {
            if (field == null) {
                return matchKey
            }

            val qualifiedMatchKey = StringBuffer()
            val pattern = Pattern.compile("[^\\(\\) ]+")
            val matcher = pattern.matcher(matchKey)
            while (matcher.find()) {
                val fullMatch = matcher.group()
                val fullMatchLowerCase = fullMatch.lowercase()

                if (fullMatchLowerCase == "and" || fullMatchLowerCase == "or" || fullMatchLowerCase == "not" // operator
                    || fullMatch.startsWith("{") // already qualified
                ) {
                    // Leaving unchanged
                    matcher.appendReplacement(qualifiedMatchKey, fullMatch)
                } else {
                    // Qualifying with {soup:path}: -- which turn into column: in sql
                    matcher.appendReplacement(qualifiedMatchKey, "$field:$fullMatch")
                }
            }
            matcher.appendTail(qualifiedMatchKey)

            return qualifiedMatchKey.toString()
        }

        @JvmStatic
        @Throws(JSONException::class)
        fun fromJSON(soupName: String?, querySpecJson: JSONObject): QuerySpec {
            val queryType = QueryType.valueOf(querySpecJson.getString(QUERY_TYPE))
            val selectPaths = JSONObjectHelper.optStringArray(querySpecJson, SELECT_PATHS)
            val path = JSONObjectHelper.optString(querySpecJson, INDEX_PATH)
            val matchKey = JSONObjectHelper.optString(querySpecJson, MATCH_KEY)
            val beginKey = JSONObjectHelper.optString(querySpecJson, BEGIN_KEY)
            val endKey = JSONObjectHelper.optString(querySpecJson, END_KEY)
            val likeKey = JSONObjectHelper.optString(querySpecJson, LIKE_KEY)
            val smartSql = JSONObjectHelper.optString(querySpecJson, SMART_SQL)
            val orderPath = JSONObjectHelper.optString(querySpecJson, ORDER_PATH)
            val order = Order.valueOf(JSONObjectHelper.optString(querySpecJson, ORDER, "ascending")!!)
            val pageSize = querySpecJson.getInt(PAGE_SIZE)

            // Building query spec
            return when (queryType) {
                QueryType.exact -> buildExactQuerySpec(soupName!!, selectPaths, path!!, matchKey!!, orderPath, order, pageSize)
                QueryType.range -> buildRangeQuerySpec(soupName!!, selectPaths, path, beginKey, endKey, orderPath, order, pageSize)
                QueryType.like -> buildLikeQuerySpec(soupName!!, selectPaths, path!!, likeKey!!, orderPath, order, pageSize)
                QueryType.match -> buildMatchQuerySpec(soupName!!, selectPaths, path, matchKey!!, orderPath, order, pageSize)
                QueryType.smart -> buildSmartQuerySpec(smartSql!!, pageSize)
            }
        }

        // Helper static methods for computing SQL during construction

        private fun computeSmartSqlStatic(
            soupName: String, selectPaths: Array<String>?, queryType: QueryType,
            matchKey: String?, beginKey: String?, endKey: String?, likeKey: String?,
            orderPath: String?, order: Order?, path: String?
        ): String {
            val selectClause = computeSelectClauseStatic(soupName, selectPaths)
            val fromClause = computeFromClauseStatic(soupName)
            val whereClause = computeWhereClauseStatic(soupName, queryType, matchKey, path)
            val orderClause = computeOrderClauseStatic(soupName, orderPath, order)
            return selectClause + fromClause + whereClause + orderClause
        }

        private fun computeCountSmartSqlStatic(
            soupName: String, queryType: QueryType, matchKey: String?,
            beginKey: String?, endKey: String?, likeKey: String?, path: String?
        ): String {
            val fromClause = computeFromClauseStatic(soupName)
            val whereClause = computeWhereClauseStatic(soupName, queryType, matchKey, path)
            return SELECT_COUNT + fromClause + whereClause
        }

        private fun computeIdsSmartSqlStatic(
            soupName: String, queryType: QueryType, matchKey: String?,
            beginKey: String?, endKey: String?, likeKey: String?,
            orderPath: String?, order: Order?, path: String?
        ): String {
            val fromClause = computeFromClauseStatic(soupName)
            val whereClause = computeWhereClauseStatic(soupName, queryType, matchKey, path)
            val orderClause = computeOrderClauseStatic(soupName, orderPath, order)
            return SELECT_ID + fromClause + whereClause + orderClause
        }

        private fun computeSelectClauseStatic(soupName: String, selectPaths: Array<String>?): String {
            val fieldReferences = ArrayList<String>()
            for (selectPath in (selectPaths ?: arrayOf(SmartSqlHelper.SOUP))) {
                fieldReferences.add(computeFieldReferenceStatic(soupName, selectPath))
            }
            return SELECT + TextUtils.join(", ", fieldReferences) + " "
        }

        private fun computeFromClauseStatic(soupName: String): String {
            return FROM + computeSoupReferenceStatic(soupName) + " "
        }

        private fun computeWhereClauseStatic(soupName: String, queryType: QueryType, matchKey: String?, path: String?): String {
            if (path == null && queryType != QueryType.match /* null path allowed for fts match query */) return ""

            var field: String? = null
            if (path != null) {
                field = computeFieldReferenceStatic(soupName, path)
            }

            val pred: String = when (queryType) {
                QueryType.exact -> "$field = ? "
                QueryType.like -> "$field LIKE ? "
                QueryType.range -> {
                    // Note: we don't have beginKey/endKey here, but we need to construct the template
                    // The actual args are handled by getArgs()
                    // This constructs the maximal form - runtime binding handles nulls
                    "$field >= ? AND $field <= ? "
                }
                QueryType.match -> {
                    val soupRef = computeSoupReferenceStatic(soupName)
                    val soupFtsRef = soupRef + SmartStore.FTS_SUFFIX
                    val entryIdRef = computeFieldReferenceStatic(soupName, SmartStore.SOUP_ENTRY_ID)
                    "$entryIdRef IN (${SELECT}${SmartStore.ROWID_COL} ${FROM}$soupFtsRef ${WHERE}$soupFtsRef MATCH '${qualifyMatchKey(field, matchKey!!)}') "
                }
                else -> throw SmartStoreException("Fell through switch: $queryType")
            }
            return if (pred == "") "" else WHERE + pred
        }

        private fun computeOrderClauseStatic(soupName: String, orderPath: String?, order: Order?): String {
            if (orderPath == null || order == null) return ""
            return ORDER_BY + computeFieldReferenceStatic(soupName, orderPath) + " " + order.sql + " "
        }

        private fun computeSoupReferenceStatic(soupName: String): String {
            return "{$soupName}"
        }

        private fun computeFieldReferenceStatic(soupName: String, field: String): String {
            return "{$soupName:$field}"
        }
    }
}
