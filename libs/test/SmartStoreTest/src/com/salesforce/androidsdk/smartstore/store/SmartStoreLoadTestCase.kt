/*
 * Copyright (c) 2018-present, salesforce.com, inc.
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
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.smartstore.store.SmartStore.Type
import org.json.JSONObject
import org.junit.Before
import java.io.File

/**
 * Super class for smartstore load tests
 */
open class SmartStoreLoadTestCase : SmartStoreTestCase() {

    companion object {
        const val TEST_SOUP = "test_soup"
        const val NUMBER_ENTRIES = 1000
        const val NUMBER_ENTRIES_PER_BATCH = 100
        const val NS_IN_MS = 1000000
    }

    @Before
    override fun setUp() {
        val dbPath = InstrumentationRegistry.getInstrumentation().targetContext.applicationInfo.dataDir + "/databases"
        val fileDir = File(dbPath)
        DBOpenHelper.deleteAllUserDatabases(InstrumentationRegistry.getInstrumentation().targetContext)
        DBOpenHelper.deleteDatabase(InstrumentationRegistry.getInstrumentation().targetContext, null, null)
        DBOpenHelper.removeAllFiles(fileDir)
        super.setUp()
    }

    override fun getEncryptionKey(): String {
        return ""
    }

    protected open fun getTag(): String {
        return javaClass.simpleName
    }

    protected fun setupSoup(soupName: String, numberIndexes: Int, indexType: Type) {
        val indexSpecs = Array(numberIndexes) { indexNumber ->
            IndexSpec("k_$indexNumber", indexType)
        }
        registerSoup(store, soupName, indexSpecs)
        Log.i(getTag(), String.format("Creating table with %d %s indexes", numberIndexes, indexType))
    }

    protected fun upsertEntries(numberBatches: Int, numberEntriesPerBatch: Int, numberFieldsPerEntry: Int, numberCharactersPerField: Int) {
        val times = mutableListOf<Long>()
        for (batchNumber in 0 until numberBatches) {
            val start = System.nanoTime()
            store.beginTransaction()
            for (entryNumber in 0 until numberEntriesPerBatch) {
                val entry = JSONObject()
                for (fieldNumber in 0 until numberFieldsPerEntry) {
                    val value = pad("v_${batchNumber}_${entryNumber}_${fieldNumber}_", numberCharactersPerField)
                    entry.put("k_$fieldNumber", value)
                }
                store.upsert(TEST_SOUP, entry, SmartStore.SOUP_ENTRY_ID, false)
            }
            store.setTransactionSuccessful()
            store.endTransaction()
            val end = System.nanoTime()
            times.add(end - start)
        }
        val avgMilliseconds = average(times) / NS_IN_MS
        Log.i(getTag(), String.format("Upserting %d entries with %d per batch with %d fields with %d characters: average time per batch --> %.3f ms",
            numberBatches * numberEntriesPerBatch, numberEntriesPerBatch, numberFieldsPerEntry, numberCharactersPerField, avgMilliseconds))
    }

    protected fun pad(s: String, numberCharacters: Int): String {
        val sb = StringBuilder(numberCharacters)
        sb.append(s)
        for (i in s.length until numberCharacters) {
            sb.append("x")
        }
        return sb.toString()
    }

    protected fun average(times: List<Long>): Double {
        var avg = 0.0
        for (i in times.indices) {
            avg += times[i]
        }
        avg /= times.size
        return avg
    }

    protected fun alterSoup(msg: String, reIndexData: Boolean, indexSpecs: Array<IndexSpec>) {
        val start = System.nanoTime()
        store.alterSoup(TEST_SOUP, indexSpecs, reIndexData)
        val duration = (System.nanoTime() - start).toDouble()
        Log.i(getTag(), String.format("%s completed in: %.3f ms", msg, duration / NS_IN_MS))
    }
}
