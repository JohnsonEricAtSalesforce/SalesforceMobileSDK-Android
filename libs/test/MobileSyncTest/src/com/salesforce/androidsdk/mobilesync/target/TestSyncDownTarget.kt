/*
 * Copyright (c) 2019-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.mobilesync.target

import com.salesforce.androidsdk.mobilesync.manager.SyncManager
import com.salesforce.androidsdk.mobilesync.util.Constants

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

import java.io.IOException
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.HashSet

/**
 * Custom sync down target for tests.
 */
class TestSyncDownTarget : SyncDownTarget {

    // Target config
    private val prefix: String
    private val numberOfRecords: Int
    private val numberOfRecordsPerPage: Int
    private val sleepPerFetch: Int

    // Target state
    private var position = 0

    // All the records
    private val records: Array<JSONObject>

    @Throws(JSONException::class)
    constructor(target: JSONObject) : this(
        target.getString(PREFIX),
        target.getInt(NUMBER_OF_RECORDS),
        target.getInt(NUMBER_OF_RECORDS_PER_PAGE),
        target.getInt(SLEEP_PER_FETCH)
    )

    @Throws(JSONException::class)
    override fun asJSON(): JSONObject {
        val target = super.asJSON()
        target.put(PREFIX, this.prefix)
        target.put(NUMBER_OF_RECORDS, this.numberOfRecords)
        target.put(NUMBER_OF_RECORDS_PER_PAGE, this.numberOfRecordsPerPage)
        target.put(SLEEP_PER_FETCH, this.sleepPerFetch)
        return target
    }

    @Throws(JSONException::class)
    constructor(prefix: String, numberOfRecords: Int, numberOfRecordsPerPage: Int, sleepPerFetch: Int) {
        this.queryType = QueryType.custom
        this.prefix = prefix
        this.numberOfRecords = numberOfRecords
        this.numberOfRecordsPerPage = numberOfRecordsPerPage
        this.sleepPerFetch = sleepPerFetch
        this.records = Array(numberOfRecords) { i ->
            JSONObject().apply {
                put(Constants.ID, idForPosition(i))
                put(Constants.LAST_MODIFIED_DATE, Constants.TIMESTAMP_FORMAT.format(dateForPosition(i)))
            }
        }
    }

    fun recordsFromPosition(): JSONArray? {
        if (this.position >= this.numberOfRecords) {
            return null
        }

        val arrayForPage = JSONArray()
        var i = this.position
        val limit = minOf(this.position + this.numberOfRecordsPerPage, this.numberOfRecords)
        do {
            arrayForPage.put(records[i])
            i++
        } while (i < limit)
        this.position = i
        return arrayForPage
    }

    override val isSyncDownSortedByLatestModification: Boolean
        get() = true

    override fun startFetch(syncManager: SyncManager, maxTimeStamp: Long): JSONArray? {
        this.position = positionForDate(maxTimeStamp)
        this.totalSize = numberOfRecords - this.position
        sleepIfNeeded()
        return recordsFromPosition()
    }

    private fun sleepIfNeeded() {
        if (sleepPerFetch > 0) {
            try {
                Thread.sleep(sleepPerFetch.toLong())
            } catch (e: InterruptedException) {
            }
        }
    }

    @Throws(IOException::class, JSONException::class)
    override fun continueFetch(syncManager: SyncManager): JSONArray? {
        sleepIfNeeded()
        return recordsFromPosition()
    }

    @Throws(IOException::class, JSONException::class)
    override fun getRemoteIds(syncManager: SyncManager, localIds: Set<String>): Set<String> {
        val remoteIds = HashSet<String>()
        for (record in records) {
            remoteIds.add(record.getString(Constants.ID))
        }
        return remoteIds
    }

    fun idForPosition(i: Int): String {
        return this.prefix + (10000 + i)
    }

    fun dateForPosition(i: Int): Date {
        return GregorianCalendar(2019, Calendar.MARCH, 1, 12, i / 60, i % 60).time
    }

    fun positionForDate(time: Long): Int {
        for (i in records.indices) {
            if (dateForPosition(i).time > time) {
                return i
            }
        }
        return records.size
    }

    fun getIdPrefix(): String {
        return this.prefix
    }

    companion object {
        // Fields in serialized target
        const val PREFIX = "prefix"
        const val NUMBER_OF_RECORDS_PER_PAGE = "numberOfRecordsPerPage"
        const val NUMBER_OF_RECORDS = "numberOfRecords"
        const val SLEEP_PER_FETCH = "sleepPerFetch"
    }
}
