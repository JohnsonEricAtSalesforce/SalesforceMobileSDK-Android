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
open class QuerySpec private constructor(
    // Key members
    val queryType: QueryType,
    val pageSize: Int,
    open val smartSql: String?,
    open val countSmartSql: String?,
    open val idsSmartSql: String?,
    // Exact/Range/Like/Match
    val soupName: String?,
    val selectPaths: Array<String>?,
    val path: String?,
    val orderPath: String?,
    val order: Order?,
    // Exact/Match
    val matchKey: String?,
    // Range
    val beginKey: String?,
    val endKey: String?,
    // Like
    val likeKey: String?
) {

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
    enum class Order(val sql: String) {
        ascending("ASC"),
        descending("DESC")
    }

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
        smartSql = null,
        countSmartSql = null,
        idsSmartSql = null,
        soupName = soupName,
        selectPaths = selectPaths,
        path = path,
        orderPath = orderPath,
        order = order,
        matchKey = matchKey,
        beginKey = beginKey,
        endKey = endKey,
        likeKey = likeKey
    ) {
        // Compute SQL after initialization
        (this as MutableQuerySpec).apply {
            this.smartSql = computeSmartSql()
            this.countSmartSql = computeCountSmartSql()
            this.idsSmartSql = computeIdsSmartSql()
        }
    }

    // Private constructor for smart query spec
    private constructor(smartSql: String, pageSize: Int) : this(
        queryType = QueryType.smart,
        pageSize = pageSize,
        smartSql = smartSql,
        countSmartSql = computeCountSmartSql(smartSql),
        idsSmartSql = computeIdsSmartSql(smartSql),
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
            QueryType.range -> when {
                beginKey == null && endKey == null -> null
                endKey == null -> arrayOf(beginKey!!)
                beginKey == null -> arrayOf(endKey)
                else -> arrayOf(beginKey, endKey)
            }
            QueryType.match -> null // baking matchKey into query
            QueryType.smart -> null
        }
    }

    /**
     * Compute smartSql for exact/like/range/match queries
     */
    private fun computeSmartSql(): String {
        val selectClause = computeSelectClause()
        val fromClause = computeFromClause()
        val whereClause = computeWhereClause()
        val orderClause = computeOrderClause()
        return selectClause + fromClause + whereClause + orderClause
    }

    /**
     * Compute countSmartSql for exact/like/range/match queries
     */
    private fun computeCountSmartSql(): String {
        val fromClause = computeFromClause()
        val whereClause = computeWhereClause()
        return SELECT_COUNT + fromClause + whereClause
    }

    /**
     * Compute idsSmartSql for exact/like/range/match queries
     */
    private fun computeIdsSmartSql(): String {
        val fromClause = computeFromClause()
        val whereClause = computeWhereClause()
        val orderClause = computeOrderClause()
        return SELECT_ID + fromClause + whereClause + orderClause
    }

    /**
     * @return select clause for exact/like/range/match queries
     */
    private fun computeSelectClause(): String {
        val fieldReferences = ArrayList<String>()
        for (selectPath in (selectPaths ?: arrayOf(SmartSqlHelper.SOUP))) {
            fieldReferences.add(computeFieldReference(selectPath))
        }
        return SELECT + TextUtils.join(", ", fieldReferences) + " "
    }

    /**
     * @return from clause for exact/like/range/match queries
     */
    private fun computeFromClause(): String {
        return FROM + computeSoupReference() + " "
    }

    /**
     * @return where clause for exact/like/range/match queries
     */
    private fun computeWhereClause(): String {
        if (path == null && queryType != QueryType.match /* null path allowed for fts match query */) return ""

        var field: String? = null

        if (path != null) {
            field = computeFieldReference(path)
        }

        val pred = when (queryType) {
            QueryType.exact -> "$field = ? "
            QueryType.like -> "$field LIKE ? "
            QueryType.range -> when {
                beginKey == null && endKey == null -> ""
                endKey == null -> "$field >= ? "
                beginKey == null -> "$field <= ? "
                else -> "$field >= ? AND $field <= ? "
            }
            QueryType.match -> {
                val soupEntryIdRef = computeFieldReference(SmartStore.SOUP_ENTRY_ID)
                val soupFtsRef = computeSoupFtsReference()
                val rowidCol = "rowid" // ROWID_COL value
                "$soupEntryIdRef IN (" +
                        "$SELECT$rowidCol $FROM$soupFtsRef $WHERE" +
                        "$soupFtsRef MATCH '${qualifyMatchKey(field, matchKey ?: "")}'" +
                        // statement arg binding doesn't seem to work so inlining matchKey
                        ") "
            }
            QueryType.smart -> throw SmartStoreException("Fell through switch: $queryType")
        }
        return if (pred.isEmpty()) "" else WHERE + pred
    }

    /**
     * @return order clause for exact/like/range/match queries
     */
    private fun computeOrderClause(): String {
        if (orderPath == null || order == null) return ""

        return ORDER_BY + computeFieldReference(orderPath) + " " + order.sql + " "
    }

    /**
     * @return soup reference for smart sql query
     */
    private fun computeSoupReference(): String {
        return "{$soupName}"
    }

    /**
     * @return fts soup table reference
     */
    private fun computeSoupFtsReference(): String {
        return computeSoupReference() + SmartStore.FTS_SUFFIX
    }

    /**
     * @param field
     * @return field reference for smart sql query
     */
    private fun computeFieldReference(field: String): String {
        return "{$soupName:$field}"
    }

    // Mutable version for initialization
    private class MutableQuerySpec : QuerySpec(
        queryType = QueryType.smart,
        pageSize = 0,
        smartSql = null,
        countSmartSql = null,
        idsSmartSql = null,
        soupName = null,
        selectPaths = null,
        path = null,
        orderPath = null,
        order = null,
        matchKey = null,
        beginKey = null,
        endKey = null,
        likeKey = null
    ) {
        override var smartSql: String? = null
        override var countSmartSql: String? = null
        override var idsSmartSql: String? = null
    }

    companion object {
        // Constants
        private const val SELECT = "SELECT "
        private const val FROM = "FROM "
        private const val WHERE = "WHERE "
        private const val ORDER_BY = "ORDER BY "

        private const val SELECT_COUNT = "${SELECT}count(*) "
        private const val SELECT_COUNT_FROM = "$SELECT_COUNT${FROM}(%s)"
        private const val SELECT_ID = "${SELECT}id " // ID_COL value
        private const val SELECT_ID_FROM = "$SELECT_ID${FROM}(%s)"

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
         * @param soupName
         * @param orderPath
         * @param order
         * @param pageSize
         * @return
         */
        @JvmStatic
        fun buildAllQuerySpec(soupName: String, orderPath: String?, order: Order?, pageSize: Int): QuerySpec {
            return buildAllQuerySpec(soupName, null, orderPath, order, pageSize)
        }

        /**
         * Return query spec for an all query
         * @param soupName
         * @param selectPaths
         * @param orderPath
         * @param order
         * @param pageSize
         * @return
         */
        @JvmStatic
        fun buildAllQuerySpec(soupName: String, selectPaths: Array<String>?, orderPath: String?, order: Order?, pageSize: Int): QuerySpec {
            return QuerySpec(soupName, selectPaths, QueryType.range, null, null, null, null, orderPath, order, pageSize, null)
        }

        /**
         * Return a query spec for an exact match query
         * @param soupName
         * @param path
         * @param exactMatchKey
         * @param orderPath
         * @param order
         * @param pageSize
         * @return
         */
        @JvmStatic
        fun buildExactQuerySpec(soupName: String, path: String, exactMatchKey: String, orderPath: String?, order: Order?, pageSize: Int): QuerySpec {
            return buildExactQuerySpec(soupName, null, path, exactMatchKey, orderPath, order, pageSize)
        }

        /**
         * Return a query spec for an exact match query
         * @param soupName
         * @param selectPaths
         * @param path
         * @param exactMatchKey
         * @param orderPath
         * @param order
         * @param pageSize
         * @return
         */
        @JvmStatic
        fun buildExactQuerySpec(soupName: String, selectPaths: Array<String>?, path: String, exactMatchKey: String, orderPath: String?, order: Order?, pageSize: Int): QuerySpec {
            return QuerySpec(soupName, selectPaths, QueryType.exact, exactMatchKey, null, null, null, orderPath, order, pageSize, path)
        }

        /**
         * Return a query spec for a range query
         * @param soupName
         * @param path
         * @param beginKey
         * @param endKey
         * @param orderPath
         * @param order
         * @param pageSize
         * @return
         */
        @JvmStatic
        fun buildRangeQuerySpec(soupName: String, path: String?, beginKey: String?, endKey: String?, orderPath: String?, order: Order?, pageSize: Int): QuerySpec {
            return buildRangeQuerySpec(soupName, null, path, beginKey, endKey, orderPath, order, pageSize)
        }

        /**
         * Return a query spec for a range query
         * @param soupName
         * @param selectPaths
         * @param path
         * @param beginKey
         * @param endKey
         * @param orderPath
         * @param order
         * @param pageSize
         * @return
         */
        @JvmStatic
        fun buildRangeQuerySpec(soupName: String, selectPaths: Array<String>?, path: String?, beginKey: String?, endKey: String?, orderPath: String?, order: Order?, pageSize: Int): QuerySpec {
            return QuerySpec(soupName, selectPaths, QueryType.range, null, beginKey, endKey, null, orderPath, order, pageSize, path)
        }

        /**
         * Return a query spec for a like query
         * @param soupName
         * @param path
         * @param likeKey
         * @param orderPath
         * @param order
         * @param pageSize
         * @return
         */
        @JvmStatic
        fun buildLikeQuerySpec(soupName: String, path: String, likeKey: String, orderPath: String?, order: Order?, pageSize: Int): QuerySpec {
            return buildLikeQuerySpec(soupName, null, path, likeKey, orderPath, order, pageSize)
        }

        /**
         * Return a query spec for a like query
         * @param soupName
         * @param selectPaths
         * @param path
         * @param likeKey
         * @param orderPath
         * @param order
         * @param pageSize
         * @return
         */
        @JvmStatic
        fun buildLikeQuerySpec(soupName: String, selectPaths: Array<String>?, path: String, likeKey: String, orderPath: String?, order: Order?, pageSize: Int): QuerySpec {
            return QuerySpec(soupName, selectPaths, QueryType.like, null, null, null, likeKey, orderPath, order, pageSize, path)
        }

        /**
         * Return a query spec for a match query (full-text search)
         * @param soupName
         * @param path
         * @param matchKey
         * @param orderPath
         * @param order
         * @param pageSize
         * @return
         */
        @JvmStatic
        fun buildMatchQuerySpec(soupName: String, path: String?, matchKey: String, orderPath: String?, order: Order?, pageSize: Int): QuerySpec {
            return buildMatchQuerySpec(soupName, null, path, matchKey, orderPath, order, pageSize)
        }

        /**
         * Return a query spec for a match query (full-text search)
         * @param soupName
         * @param selectPaths
         * @param path
         * @param matchKey
         * @param orderPath
         * @param order
         * @param pageSize
         * @return
         */
        @JvmStatic
        fun buildMatchQuerySpec(soupName: String, selectPaths: Array<String>?, path: String?, matchKey: String, orderPath: String?, order: Order?, pageSize: Int): QuerySpec {
            return QuerySpec(soupName, selectPaths, QueryType.match, matchKey, null, null, null, orderPath, order, pageSize, path)
        }

        /**
         * Return a query spec for a smart query
         * @param smartSql
         * @param pageSize
         * @return
         */
        @JvmStatic
        fun buildSmartQuerySpec(smartSql: String, pageSize: Int): QuerySpec {
            return QuerySpec(smartSql, pageSize)
        }

        /**
         * Compute countSmartSql for smart queries
         */
        private fun computeCountSmartSql(smartSql: String): String {
            return String.format(SELECT_COUNT_FROM, smartSql)
        }

        /**
         * Compute idsSmartSql for smart queries
         */
        private fun computeIdsSmartSql(smartSql: String): String {
            return String.format(SELECT_ID_FROM, smartSql)
        }

        /**
         * fts5 doesn't allow WHERE column MATCH 'value' - only allows WHERE table MATCH 'column:value'
         * This method changes the matchKey to add field: in the right places
         * @param field
         * @param matchKey
         * @return
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

        /**
         * @param soupName
         * @param querySpecJson
         * @return
         * @throws JSONException
         */
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
            val order = Order.valueOf(JSONObjectHelper.optString(querySpecJson, ORDER, "ascending") ?: "ascending")
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
    }
}
