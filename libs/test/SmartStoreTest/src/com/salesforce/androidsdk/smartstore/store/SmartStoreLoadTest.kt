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
package com.salesforce.androidsdk.smartstore.store

import android.util.Log
import androidx.test.filters.LargeTest
import com.salesforce.androidsdk.smartstore.store.SmartStore.Type
import org.json.JSONArray
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Set of tests for the smart store loading numerous and/or large entries and querying them back
 */
@RunWith(Parameterized::class)
@LargeTest
class SmartStoreLoadTest : SmartStoreLoadTestCase() {

    @JvmField @Parameterized.Parameter(0) var testName: String = ""
    @JvmField @Parameterized.Parameter(1) var indexType: Type = Type.string
    @JvmField @Parameterized.Parameter(2) var numberEntries: Int = 0
    @JvmField @Parameterized.Parameter(3) var numberFieldsPerEntry: Int = 0
    @JvmField @Parameterized.Parameter(4) var numberCharactersPerField: Int = 0
    @JvmField @Parameterized.Parameter(5) var numberIndexes: Int = 0

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data(): Collection<Array<Any>> {
            return listOf(
                arrayOf("UpsertQuery1StringIndex1field20characters", Type.string, NUMBER_ENTRIES, 1, 20, 1),
                arrayOf("UpsertQuery1StringIndex1field1000characters", Type.string, NUMBER_ENTRIES, 1, 1000, 1),
                arrayOf("UpsertQuery1StringIndex10fields20characters", Type.string, NUMBER_ENTRIES, 10, 20, 1),
                arrayOf("UpsertQuery10StringIndexes10fields20characters", Type.string, NUMBER_ENTRIES, 10, 20, 10),
                arrayOf("UpsertQuery1JSON1Index1field20characters", Type.json1, NUMBER_ENTRIES, 1, 20, 1),
                arrayOf("UpsertQuery1JSON1Index1field1000characters", Type.json1, NUMBER_ENTRIES, 1, 1000, 1),
                arrayOf("UpsertQuery1JSON1Index10fields20characters", Type.json1, NUMBER_ENTRIES, 10, 20, 1),
                arrayOf("UpsertQuery10JSON1Indexes10fields20characters", Type.json1, NUMBER_ENTRIES, 10, 20, 10)
            )
        }
    }

    @Test
    fun test() {
        tryUpsertQuery(indexType, numberEntries, numberFieldsPerEntry, numberCharactersPerField, numberIndexes)
    }

    private fun tryUpsertQuery(indexType: Type, numberEntries: Int, numberFieldsPerEntry: Int, numberCharactersPerField: Int, numberIndexes: Int) {
        setupSoup(TEST_SOUP, numberIndexes, indexType)
        upsertEntries(numberEntries / NUMBER_ENTRIES_PER_BATCH, NUMBER_ENTRIES_PER_BATCH, numberFieldsPerEntry, numberCharactersPerField)
        queryEntries()
    }

    private fun queryEntries() {
        queryEntries(QuerySpec.buildAllQuerySpec(TEST_SOUP, null, null, 1))
        queryEntries(QuerySpec.buildAllQuerySpec(TEST_SOUP, null, null, 10))
        queryEntries(QuerySpec.buildAllQuerySpec(TEST_SOUP, null, null, 100))
        queryEntries(QuerySpec.buildLikeQuerySpec(TEST_SOUP, "k_0", "v_0_%", null, null, 1))
        queryEntries(QuerySpec.buildLikeQuerySpec(TEST_SOUP, "k_0", "v_0_%", null, null, 10))
        queryEntries(QuerySpec.buildLikeQuerySpec(TEST_SOUP, "k_0", "v_0_%", null, null, 100))
        queryEntries(QuerySpec.buildLikeQuerySpec(TEST_SOUP, "k_0", "v_0_0_%", null, null, 1))
        queryEntries(QuerySpec.buildLikeQuerySpec(TEST_SOUP, "k_0", "v_0_0_%", null, null, 10))
        queryEntries(QuerySpec.buildExactQuerySpec(TEST_SOUP, "k_0", "missing", null, null, 1))
    }

    private fun queryEntries(querySpec: QuerySpec) {
        val times = mutableListOf<Long>()
        var countMatches = 0
        var hasMore = true
        var pageIndex = 0
        while (hasMore) {
            val start = System.nanoTime()
            val results: JSONArray = store.query(querySpec, pageIndex)
            val end = System.nanoTime()
            times.add(end - start)
            hasMore = results.length() == querySpec.pageSize
            countMatches += results.length()
            pageIndex++
        }
        val avgMilliseconds = average(times) / NS_IN_MS
        Log.i(getTag(), String.format("Querying with %s query matching %d entries and %d page size: average time per page --> %.3f ms",
            querySpec.queryType, countMatches, querySpec.pageSize, avgMilliseconds))
    }
}
