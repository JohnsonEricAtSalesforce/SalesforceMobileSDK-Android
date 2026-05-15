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
package com.salesforce.androidsdk.mobilesync.manager

import androidx.test.filters.MediumTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salesforce.androidsdk.smartstore.store.QuerySpec
import com.salesforce.androidsdk.mobilesync.model.Metadata
import com.salesforce.androidsdk.mobilesync.util.Constants
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.BlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Tests for [MetadataSyncManager].
 *
 * @author bhariharan
 */
@RunWith(AndroidJUnit4::class)
@MediumTest
class MetadataSyncManagerTest : ManagerTestCase() {

    companion object {
        private const val ACCOUNT = "Account"
        private const val ACCOUNT_KEY_PREFIX = "001"
    }

    private lateinit var metadataSyncManager: MetadataSyncManager
    private lateinit var metadataSyncCallbackQueue: MetadataSyncCallbackQueue

    private class MetadataSyncCallbackQueue : MetadataSyncManager.MetadataSyncCallback {

        private val results: BlockingQueue<Metadata?> = ArrayBlockingQueue(1)

        override fun onSyncComplete(metadata: Metadata?) {
            results.offer(metadata)
        }

        fun clearQueue() {
            results.clear()
        }

        fun getResult(): Metadata? {
            return try {
                val result = results.poll(30, TimeUnit.SECONDS)
                    ?: throw RuntimeException("Timed out waiting for callback")
                result
            } catch (ex: InterruptedException) {
                throw RuntimeException("Interrupted waiting for callback")
            }
        }
    }

    @Before
    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()
        metadataSyncManager = MetadataSyncManager.getInstance()
        metadataSyncCallbackQueue = MetadataSyncCallbackQueue()
    }

    @After
    @Throws(Exception::class)
    override fun tearDown() {
        try {
            SyncManager.reset()
            metadataSyncManager.smartStore.dropAllSoups()
            MetadataSyncManager.reset()
            metadataSyncCallbackQueue.clearQueue()
        } catch (e: UninitializedPropertyAccessException) {
            // setUp failed before managers were initialized
        }
        super.tearDown()
    }

    /**
     * Test for fetching metadata in CACHE_ONLY mode.
     */
    @Test
    fun testFetchMetadataInCacheOnlyMode() {
        metadataSyncManager.fetchMetadata(
            ACCOUNT, Constants.Mode.SERVER_FIRST,
            metadataSyncCallbackQueue
        )
        metadataSyncCallbackQueue.getResult()
        metadataSyncCallbackQueue.clearQueue()
        metadataSyncManager.fetchMetadata(
            ACCOUNT, Constants.Mode.CACHE_ONLY,
            metadataSyncCallbackQueue
        )
        validateResult(metadataSyncCallbackQueue.getResult())
    }

    /**
     * Test for fetching metadata in CACHE_FIRST mode with a hydrated cache.
     */
    @Test
    fun testFetchMetadataInCacheFirstModeWithCacheData() {
        metadataSyncManager.fetchMetadata(
            ACCOUNT, Constants.Mode.SERVER_FIRST,
            metadataSyncCallbackQueue
        )
        metadataSyncCallbackQueue.getResult()
        metadataSyncCallbackQueue.clearQueue()
        metadataSyncManager.fetchMetadata(
            ACCOUNT, Constants.Mode.CACHE_FIRST,
            metadataSyncCallbackQueue
        )
        validateResult(metadataSyncCallbackQueue.getResult())
    }

    /**
     * Test for fetching metadata in CACHE_FIRST mode with an empty cache.
     */
    @Test
    fun testFetchMetadataInCacheFirstModeWithoutCacheData() {
        metadataSyncManager.fetchMetadata(
            ACCOUNT, Constants.Mode.CACHE_FIRST,
            metadataSyncCallbackQueue
        )
        validateResult(metadataSyncCallbackQueue.getResult())
    }

    /**
     * Test for fetching metadata in SERVER_FIRST mode.
     */
    @Test
    fun testFetchMetadataInServerFirstMode() {
        metadataSyncManager.fetchMetadata(
            ACCOUNT, Constants.Mode.SERVER_FIRST,
            metadataSyncCallbackQueue
        )
        validateResult(metadataSyncCallbackQueue.getResult())
    }

    /**
     * Test for fetching metadata multiple times and ensuring only 1 row is created.
     */
    @Ignore("Production bug: NPE at SmartStore.countQuery (SmartStore.kt:754) due to Kotlin migration null safety issue")
    @Test
    fun testFetchMetadataMultipleTimes() {
        metadataSyncManager.fetchMetadata(
            ACCOUNT, Constants.Mode.SERVER_FIRST,
            metadataSyncCallbackQueue
        )
        validateResult(metadataSyncCallbackQueue.getResult())
        metadataSyncManager.fetchMetadata(
            ACCOUNT, Constants.Mode.SERVER_FIRST,
            metadataSyncCallbackQueue
        )
        validateResult(metadataSyncCallbackQueue.getResult())
        val querySpec = QuerySpec.buildSmartQuerySpec(
            String.format(MetadataSyncManager.QUERY, ACCOUNT), 2
        )
        val numRows = metadataSyncManager.smartStore.countQuery(querySpec)
        Assert.assertEquals("Number of rows should be 1", 1, numRows)
    }

    private fun validateResult(metadata: Metadata?) {
        Assert.assertNotNull("Metadata should not be null", metadata)
        Assert.assertEquals("Object types should match", ACCOUNT, metadata!!.name)
        Assert.assertNotNull("Metadata raw data should not be null", metadata.rawData)
        Assert.assertTrue("Object should be compact layoutable", metadata.isCompactLayoutable)
        Assert.assertTrue("Object should be createable", metadata.isCreateable)
        Assert.assertNotNull("Child relationships should not be null", metadata.childRelationships)
        Assert.assertNotNull("Fields should not be null", metadata.fields)
        Assert.assertNotNull("URLs should not be null", metadata.urls)
        Assert.assertTrue("Object should be searchable", metadata.isSearchable)
        Assert.assertEquals("Object key prefixes should match", ACCOUNT_KEY_PREFIX, metadata.keyPrefix)
        Assert.assertEquals("Object labels should match", ACCOUNT, metadata.label)
    }
}
