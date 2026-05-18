package com.salesforce.androidsdk.mobilesync.util
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salesforce.androidsdk.mobilesync.app.MobileSyncSDKManager
import com.salesforce.androidsdk.mobilesync.target.SoqlSyncDownTarget
import com.salesforce.androidsdk.mobilesync.target.SyncUpTarget
import com.salesforce.androidsdk.mobilesync.util.SyncState.MergeMode
import com.salesforce.androidsdk.smartstore.store.IndexSpec
import com.salesforce.androidsdk.smartstore.store.SmartStore
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections

@RunWith(AndroidJUnit4::class)
class SyncStateTest {
    companion object { private const val DB_NAME = "testDb" }
    private lateinit var store: SmartStore

    @Before fun setUp() { store = MobileSyncSDKManager.getInstance().getGlobalSmartStore(DB_NAME) }
    @After fun tearDown() { MobileSyncSDKManager.getInstance().removeGlobalSmartStore(DB_NAME) }

    @Test fun testSetupSyncsSoupFirstTime() { SyncState.setupSyncsSoupIfNeeded(store); checkSyncsSoupIndexSpecs(store) }
    @Test fun testSetupSyncsSoupUpgradeTo71() { val indexSpecs = arrayOf(IndexSpec(SyncState.SYNC_TYPE, SmartStore.Type.string), IndexSpec(SyncState.SYNC_NAME, SmartStore.Type.string)); store.registerSoup(SyncState.SYNCS_SOUP, indexSpecs); SyncState.setupSyncsSoupIfNeeded(store); checkSyncsSoupIndexSpecs(store) }
    @Test fun testCleanupSyncsSoupIfNeeded() { SyncState.setupSyncsSoupIfNeeded(store); createSyncChangeStatus("newSyncUp", true, SyncState.Status.NEW); createSyncChangeStatus("stoppedSyncUp", true, SyncState.Status.STOPPED); createSyncChangeStatus("runningSyncUp", true, SyncState.Status.RUNNING); createSyncChangeStatus("failedSyncUp", true, SyncState.Status.FAILED); createSyncChangeStatus("doneSyncUp", true, SyncState.Status.DONE); createSyncChangeStatus("newSyncDown", false, SyncState.Status.NEW); createSyncChangeStatus("stoppedSyncDown", false, SyncState.Status.STOPPED); createSyncChangeStatus("runningSyncDown", false, SyncState.Status.RUNNING); createSyncChangeStatus("failedSyncDown", false, SyncState.Status.FAILED); createSyncChangeStatus("doneSyncDown", false, SyncState.Status.DONE); SyncState.cleanupSyncsSoupIfNeeded(store); checkSyncStatus("newSyncUp", SyncState.Status.NEW); checkSyncStatus("stoppedSyncUp", SyncState.Status.STOPPED); checkSyncStatus("runningSyncUp", SyncState.Status.STOPPED); checkSyncStatus("failedSyncUp", SyncState.Status.FAILED); checkSyncStatus("doneSyncUp", SyncState.Status.DONE); checkSyncStatus("newSyncDown", SyncState.Status.NEW); checkSyncStatus("stoppedSyncDown", SyncState.Status.STOPPED); checkSyncStatus("runningSyncDown", SyncState.Status.STOPPED); checkSyncStatus("failedSyncDown", SyncState.Status.FAILED); checkSyncStatus("doneSyncDown", SyncState.Status.DONE) }

    private fun createSyncChangeStatus(name: String, isSyncUp: Boolean, status: SyncState.Status) { val sync = if (isSyncUp) SyncState.createSyncUp(store, SyncUpTarget(), SyncOptions.optionsForSyncUp(Collections.singletonList("Name")), "Accounts", name) else SyncState.createSyncDown(store, SoqlSyncDownTarget("SELECT Id, Name from Account"), SyncOptions.optionsForSyncDown(MergeMode.LEAVE_IF_CHANGED), "Accounts", name); sync.status = status; sync.save(store) }
    private fun checkSyncStatus(name: String, expectedStatus: SyncState.Status) { val sync = SyncState.byName(store, name)!!; Assert.assertEquals("Wrong status for $name", expectedStatus, sync.status) }
    private fun checkSyncsSoupIndexSpecs(store: SmartStore) { val indexSpecs = store.getSoupIndexSpecs(SyncState.SYNCS_SOUP); Assert.assertEquals("Wrong number of index specs", 3, indexSpecs.size); val expectedPaths = listOf(SyncState.SYNC_NAME, SyncState.SYNC_TYPE, SyncState.SYNC_STATUS); for (indexSpec in indexSpecs) { Assert.assertTrue("Wrong index spec path", expectedPaths.contains(indexSpec.path)); Assert.assertEquals("Wrong index spec type", SmartStore.Type.json1, indexSpec.type) } }
}
