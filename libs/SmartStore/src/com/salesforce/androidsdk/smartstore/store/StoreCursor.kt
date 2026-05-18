/*
 * Copyright (c) 2015-present, salesforce.com, inc.
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

import org.json.JSONException
import org.json.JSONObject
import kotlin.math.ceil

/**
 * Store Cursor
 * We don't actually keep a cursor opened, instead, we wrap the query spec and page index
 */
class StoreCursor(smartStore: SmartStore, private val querySpec: QuerySpec) {

    // Id / soup / query / totalPages immutable
    @JvmField
    val cursorId: Int

    @JvmField
    val totalPages: Int

    @JvmField
    val totalEntries: Int

    // Current page can change - by calling moveToPageIndex
    private var currentPageIndex: Int = 0

    init {
        val countRows = smartStore.countQuery(querySpec)
        cursorId = LAST_ID++
        totalEntries = countRows
        totalPages = ceil(countRows.toDouble() / querySpec.pageSize).toInt()
    }

    /**
     * @param newPageIndex
     */
    fun moveToPageIndex(newPageIndex: Int) {
        // Always between 0 and totalPages-1
        currentPageIndex = when {
            newPageIndex < 0 -> 0
            newPageIndex >= totalPages -> totalPages - 1
            else -> newPageIndex
        }
    }

    /**
     * Returns cursor meta data (page index, size etc) and data (entries in page) as a FakeJSONObject
     * NB: json data is never deserialized
     * @param smartStore
     */
    fun getDataSerialized(smartStore: SmartStore): FakeJSONObject {
        val resultBuilder = StringBuilder()
        resultBuilder.append("{")
            .append("\"").append(CURSOR_ID).append("\":").append(cursorId).append(", ")
            .append("\"").append(CURRENT_PAGE_INDEX).append("\":").append(currentPageIndex).append(", ")
            .append("\"").append(PAGE_SIZE).append("\":").append(querySpec.pageSize).append(", ")
            .append("\"").append(TOTAL_ENTRIES).append("\":").append(totalEntries).append(", ")
            .append("\"").append(TOTAL_PAGES).append("\":").append(totalPages).append(", ")
            .append("\"").append(CURRENT_PAGE_ORDERED_ENTRIES).append("\":")
        smartStore.queryAsString(resultBuilder, querySpec, currentPageIndex)
        resultBuilder.append("}")
        return FakeJSONObject(resultBuilder.toString())
    }

    /**
     * Returns cursor meta data (page index, size etc) and data (entries in page) as a JSONObject
     * @param smartStore
     */
    @Throws(JSONException::class)
    fun getDataDeserialized(smartStore: SmartStore): JSONObject {
        val result = JSONObject()
        result.put(CURSOR_ID, cursorId)
        result.put(CURRENT_PAGE_INDEX, currentPageIndex)
        result.put(PAGE_SIZE, querySpec.pageSize)
        result.put(TOTAL_ENTRIES, totalEntries)
        result.put(TOTAL_PAGES, totalPages)
        result.put(CURRENT_PAGE_ORDERED_ENTRIES, smartStore.query(querySpec, currentPageIndex))
        return result
    }

    companion object {
        // Keys for json
        const val TOTAL_ENTRIES = "totalEntries"
        const val TOTAL_PAGES = "totalPages"
        const val PAGE_SIZE = "pageSize"
        const val CURRENT_PAGE_INDEX = "currentPageIndex"
        const val CURRENT_PAGE_ORDERED_ENTRIES = "currentPageOrderedEntries"
        const val CURSOR_ID = "cursorId"

        private var LAST_ID = 0
    }
}

/**
 * A subclass of JSONObject that doesn't actually parse the stringified json passed to its constructor
 * Use this class to avoid deserialization if you are calling a method that only wants to serialize the JSONObject
 */
class FakeJSONObject(private val json: String) : JSONObject() {
    override fun toString(): String {
        return json
    }
}
