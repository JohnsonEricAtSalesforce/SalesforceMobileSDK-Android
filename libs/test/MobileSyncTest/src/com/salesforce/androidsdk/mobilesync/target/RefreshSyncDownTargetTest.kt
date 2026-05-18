package com.salesforce.androidsdk.mobilesync.target
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.salesforce.androidsdk.mobilesync.manager.SyncManagerTestCase
import com.salesforce.androidsdk.mobilesync.util.Constants
import com.salesforce.androidsdk.mobilesync.util.SyncOptions
import com.salesforce.androidsdk.mobilesync.util.SyncState
import com.salesforce.androidsdk.mobilesync.util.SyncState.MergeMode
import com.salesforce.androidsdk.mobilesync.util.SyncUpdateCallbackQueue
import org.json.JSONObject
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Arrays
import java.util.HashSet

@RunWith(AndroidJUnit4::class) @SmallTest
class RefreshSyncDownTargetTest : SyncManagerTestCase() {
    companion object { private const val COUNT_TEST_ACCOUNTS = 10; val REFRESH_FIELDLIST = listOf(Constants.ID, Constants.NAME, Constants.DESCRIPTION, Constants.LAST_MODIFIED_DATE) }
    protected lateinit var idToFields: MutableMap<String, Map<String, Any>>

    @Before override fun setUp() { super.setUp(); createAccountsSoup(); idToFields = createRecordsOnServerReturnFields(COUNT_TEST_ACCOUNTS, Constants.ACCOUNT, null) }
    @After override fun tearDown() { if (::idToFields.isInitialized) { deleteRecordsByIdOnServer(idToFields.keys, Constants.ACCOUNT) }; dropAccountsSoup(); super.tearDown() }

    @Test fun testRefreshSyncDown() { for (id in idToFields.keys) { val soupElement = JSONObject(); soupElement.put(Constants.ID, id); smartStore.create(ACCOUNTS_SOUP, soupElement) }; val target = RefreshSyncDownTarget(REFRESH_FIELDLIST, Constants.ACCOUNT, ACCOUNTS_SOUP); trySyncDown(MergeMode.OVERWRITE, target, ACCOUNTS_SOUP, idToFields.size, 1, null); checkDb(idToFields, ACCOUNTS_SOUP) }
    @Test fun testRefreshSyncDownWithMultipleRoundTrips() { for (id in idToFields.keys) { val soupElement = JSONObject(); soupElement.put(Constants.ID, id); smartStore.create(ACCOUNTS_SOUP, soupElement) }; val target = RefreshSyncDownTarget(REFRESH_FIELDLIST, Constants.ACCOUNT, ACCOUNTS_SOUP, 2); trySyncDown(MergeMode.OVERWRITE, target, ACCOUNTS_SOUP, idToFields.size, idToFields.size / 2, null); checkDb(idToFields, ACCOUNTS_SOUP) }
    @Test fun testRefreshReSyncWithMultipleRoundTrips() { for (id in idToFields.keys) { val soupElement = JSONObject(); soupElement.put(Constants.ID, id); smartStore.create(ACCOUNTS_SOUP, soupElement) }; val target = RefreshSyncDownTarget(REFRESH_FIELDLIST, Constants.ACCOUNT, ACCOUNTS_SOUP, 1); val syncId = trySyncDown(MergeMode.OVERWRITE, target, ACCOUNTS_SOUP, idToFields.size, 10, null); val sync = syncManager.getSyncStatus(syncId)!!; val options = sync.options; val maxTimeStamp = sync.maxTimeStamp; Assert.assertTrue("Wrong time stamp", maxTimeStamp > 0); checkDb(idToFields, ACCOUNTS_SOUP); val idToFieldsUpdated = makeRemoteChanges(idToFields, Constants.ACCOUNT); val queue = SyncUpdateCallbackQueue(syncId); syncManager.reSync(syncId, queue); for (i in 0 until 13) { queue.getNextSyncUpdate(syncId) }; checkDb(idToFieldsUpdated, ACCOUNTS_SOUP); Assert.assertTrue("Wrong time stamp", syncManager.getSyncStatus(syncId)!!.maxTimeStamp > maxTimeStamp) }
    @Test fun testCleanResyncGhostsForRefreshTarget() { for (id in idToFields.keys) { val soupElement = JSONObject(); soupElement.put(Constants.ID, id); smartStore.create(ACCOUNTS_SOUP, soupElement) }; val target = RefreshSyncDownTarget(REFRESH_FIELDLIST, Constants.ACCOUNT, ACCOUNTS_SOUP); val syncId = trySyncDown(MergeMode.OVERWRITE, target, ACCOUNTS_SOUP, idToFields.size, 1, null); checkDb(idToFields, ACCOUNTS_SOUP); val ids = idToFields.keys.toTypedArray(); val idDeleted = ids[0]; deleteRecordsByIdOnServer(HashSet(listOf(idDeleted)), Constants.ACCOUNT); tryCleanResyncGhosts(syncId); val idToFieldsLeft = HashMap(idToFields); idToFieldsLeft.remove(idDeleted); checkDb(idToFieldsLeft, ACCOUNTS_SOUP); checkDbDeleted(ACCOUNTS_SOUP, arrayOf(idDeleted), Constants.ID) }
}
