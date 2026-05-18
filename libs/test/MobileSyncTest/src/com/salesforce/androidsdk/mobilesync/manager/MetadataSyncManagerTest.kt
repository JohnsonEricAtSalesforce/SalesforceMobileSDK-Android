package com.salesforce.androidsdk.mobilesync.manager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.salesforce.androidsdk.mobilesync.model.Metadata
import com.salesforce.androidsdk.mobilesync.util.Constants
import com.salesforce.androidsdk.smartstore.store.QuerySpec
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class) @MediumTest
class MetadataSyncManagerTest : ManagerTestCase() {
    companion object { private const val ACCOUNT = "Account"; private const val ACCOUNT_KEY_PREFIX = "001" }
    private lateinit var metadataSyncManager: MetadataSyncManager
    private lateinit var metadataSyncCallbackQueue: MetadataSyncCallbackQueue

    private class MetadataSyncCallbackQueue : MetadataSyncManager.MetadataSyncCallback {
        private val results = ArrayBlockingQueue<Metadata>(1)
        override fun onSyncComplete(metadata: Metadata?) { if (metadata != null) results.offer(metadata) }
        fun clearQueue() { results.clear() }
        fun getResult(): Metadata { val result = results.poll(30, TimeUnit.SECONDS); if (result == null) throw RuntimeException("Timed out waiting for callback"); return result }
    }

    @Before override fun setUp() { super.setUp(); metadataSyncManager = MetadataSyncManager.getInstance(); metadataSyncCallbackQueue = MetadataSyncCallbackQueue() }
    @After override fun tearDown() { SyncManager.reset(); metadataSyncManager.smartStore.dropAllSoups(); MetadataSyncManager.reset(); metadataSyncCallbackQueue.clearQueue(); super.tearDown() }

    @Test fun testFetchMetadataInCacheOnlyMode() { metadataSyncManager.fetchMetadata(ACCOUNT, Constants.Mode.SERVER_FIRST, metadataSyncCallbackQueue); metadataSyncCallbackQueue.getResult(); metadataSyncCallbackQueue.clearQueue(); metadataSyncManager.fetchMetadata(ACCOUNT, Constants.Mode.CACHE_ONLY, metadataSyncCallbackQueue); validateResult(metadataSyncCallbackQueue.getResult()) }
    @Test fun testFetchMetadataInCacheFirstModeWithCacheData() { metadataSyncManager.fetchMetadata(ACCOUNT, Constants.Mode.SERVER_FIRST, metadataSyncCallbackQueue); metadataSyncCallbackQueue.getResult(); metadataSyncCallbackQueue.clearQueue(); metadataSyncManager.fetchMetadata(ACCOUNT, Constants.Mode.CACHE_FIRST, metadataSyncCallbackQueue); validateResult(metadataSyncCallbackQueue.getResult()) }
    @Test fun testFetchMetadataInCacheFirstModeWithoutCacheData() { metadataSyncManager.fetchMetadata(ACCOUNT, Constants.Mode.CACHE_FIRST, metadataSyncCallbackQueue); validateResult(metadataSyncCallbackQueue.getResult()) }
    @Test fun testFetchMetadataInServerFirstMode() { metadataSyncManager.fetchMetadata(ACCOUNT, Constants.Mode.SERVER_FIRST, metadataSyncCallbackQueue); validateResult(metadataSyncCallbackQueue.getResult()) }
    @Test fun testFetchMetadataMultipleTimes() { metadataSyncManager.fetchMetadata(ACCOUNT, Constants.Mode.SERVER_FIRST, metadataSyncCallbackQueue); validateResult(metadataSyncCallbackQueue.getResult()); metadataSyncManager.fetchMetadata(ACCOUNT, Constants.Mode.SERVER_FIRST, metadataSyncCallbackQueue); validateResult(metadataSyncCallbackQueue.getResult()); val querySpec = QuerySpec.buildSmartQuerySpec(String.format(MetadataSyncManager.QUERY, ACCOUNT), 2); Assert.assertEquals("Number of rows should be 1", 1, metadataSyncManager.smartStore.countQuery(querySpec)) }
    private fun validateResult(metadata: Metadata) { Assert.assertNotNull("Metadata should not be null", metadata); Assert.assertEquals("Object types should match", ACCOUNT, metadata.name); Assert.assertNotNull("Metadata raw data should not be null", metadata.rawData); Assert.assertTrue("Object should be searchable", metadata.isSearchable); Assert.assertEquals("Object key prefixes should match", ACCOUNT_KEY_PREFIX, metadata.keyPrefix); Assert.assertEquals("Object labels should match", ACCOUNT, metadata.label) }
}
