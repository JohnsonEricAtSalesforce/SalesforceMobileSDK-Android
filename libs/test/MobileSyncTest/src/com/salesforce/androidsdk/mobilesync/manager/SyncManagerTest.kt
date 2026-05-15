/*
 * Copyright (c) 2014-present, salesforce.com, inc.
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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.salesforce.androidsdk.mobilesync.manager.SyncManager.MobileSyncException
import com.salesforce.androidsdk.mobilesync.manager.SyncManager.SyncManagerStoppedException
import com.salesforce.androidsdk.mobilesync.target.LayoutSyncDownTarget
import com.salesforce.androidsdk.mobilesync.target.MetadataSyncDownTarget
import com.salesforce.androidsdk.mobilesync.target.MruSyncDownTarget
import com.salesforce.androidsdk.mobilesync.target.SoqlSyncDownTarget
import com.salesforce.androidsdk.mobilesync.target.SoslSyncDownTarget
import com.salesforce.androidsdk.mobilesync.target.SyncDownTarget
import com.salesforce.androidsdk.mobilesync.target.SyncTarget
import com.salesforce.androidsdk.mobilesync.target.SyncUpTarget
import com.salesforce.androidsdk.mobilesync.target.TestSyncDownTarget
import com.salesforce.androidsdk.mobilesync.target.TestSyncUpTarget
import com.salesforce.androidsdk.mobilesync.util.Constants
import com.salesforce.androidsdk.mobilesync.util.JSONTestHelper
import com.salesforce.androidsdk.mobilesync.util.SOSLBuilder
import com.salesforce.androidsdk.mobilesync.util.SOSLReturningBuilder
import com.salesforce.androidsdk.mobilesync.util.SyncOptions
import com.salesforce.androidsdk.mobilesync.util.SyncState
import com.salesforce.androidsdk.mobilesync.util.SyncState.MergeMode
import com.salesforce.androidsdk.mobilesync.util.SyncState.Status.*
import com.salesforce.androidsdk.mobilesync.util.SyncState.Type.syncDown
import com.salesforce.androidsdk.mobilesync.util.SyncUpdateCallbackQueue
import com.salesforce.androidsdk.smartstore.store.QuerySpec
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.Collections.singletonList

/**
 * Test class for SyncManager.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class SyncManagerTest : SyncManagerTestCase() {

    companion object {
        // Misc
        protected const val COUNT_TEST_ACCOUNTS = 10
        val REFRESH_FIELDLIST: List<String> = listOf(Constants.ID, Constants.NAME, Constants.DESCRIPTION, Constants.LAST_MODIFIED_DATE)
    }

    protected lateinit var idToFields: Map<String, Map<String, Any>>

    @Before
    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()
        createAccountsSoup()
        idToFields = createRecordsOnServerReturnFields(COUNT_TEST_ACCOUNTS, Constants.ACCOUNT, null)
    }

    @After
    @Throws(Exception::class)
    override fun tearDown() {
        if (::idToFields.isInitialized) {
            deleteRecordsByIdOnServer(idToFields.keys, Constants.ACCOUNT)
        }
        try {
            dropAccountsSoup()
        } catch (e: UninitializedPropertyAccessException) {
            // smartStore not initialized - setUp failed before this point
        }
        super.tearDown()
    }

    /**
     * getSyncStatus should return null for invalid sync id
     * @throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testGetSyncStatusForInvalidSyncId() {
        val sync = syncManager.getSyncStatus(-1)
        Assert.assertNull("Sync status should be null", sync)
    }

    /**
     * Sync down the test accounts, check smart store, check status during sync
     */
    @Test
    @Throws(Exception::class)
    fun testSyncDown() {

        // first sync down
        trySyncDown(MergeMode.OVERWRITE)

        // Check that db was correctly populated
        checkDb(idToFields, ACCOUNTS_SOUP)
    }

    /**
     * Sync down the test accounts, make some local changes, sync down again with merge mode LEAVE_IF_CHANGED then sync down with merge mode OVERWRITE
     */
    @Test
    @Throws(Exception::class)
    fun testSyncDownWithoutOverwrite() {

        // first sync down
        trySyncDown(MergeMode.OVERWRITE)

        // Make some local change
        val idToFieldsLocallyUpdated = makeLocalChanges(idToFields, ACCOUNTS_SOUP)

        // sync down again with MergeMode.LEAVE_IF_CHANGED
        trySyncDown(MergeMode.LEAVE_IF_CHANGED)

        // Check db
        val idToFieldsExpected = idToFields.toMutableMap()
        idToFieldsExpected.putAll(idToFieldsLocallyUpdated)
        checkDb(idToFieldsExpected, ACCOUNTS_SOUP)

        // sync down again with MergeMode.OVERWRITE
        trySyncDown(MergeMode.OVERWRITE)

        // Check db
        checkDb(idToFields, ACCOUNTS_SOUP)
    }

    /**
     * Test for sync down with metadata target.
     */
    @Test
    @Ignore("Production bug: QuerySpec ClassCastException at QuerySpec.kt:110")
    @Throws(Exception::class)
    fun testSyncDownForMetadataTarget() {

        // Builds metadata sync down target and performs sync.
        trySyncDown(MergeMode.LEAVE_IF_CHANGED, MetadataSyncDownTarget(Constants.ACCOUNT), ACCOUNTS_SOUP)
        val smartStoreQuery = QuerySpec.buildAllQuerySpec(
            ACCOUNTS_SOUP,
            SyncTarget.SYNC_ID, QuerySpec.Order.ascending, 1
        )
        val rows = smartStore.query(smartStoreQuery, 0)
        Assert.assertEquals("Number of rows should be 1", 1, rows.length())
        val metadata = rows.optJSONObject(0)
        Assert.assertNotNull("Metadata should not be null", metadata)
        val keyPrefix = metadata.optString(Constants.KEYPREFIX_FIELD)
        val label = metadata.optString(Constants.LABEL_FIELD)
        Assert.assertEquals("Key prefix should be 001", Constants.ACCOUNT_KEY_PREFIX, keyPrefix)
        Assert.assertEquals("Label should be ${Constants.ACCOUNT}", Constants.ACCOUNT, label)
    }

    /**
     * Test for sync down with layout target.
     */
    @Test
    @Ignore("Production bug: QuerySpec ClassCastException at QuerySpec.kt:110")
    @Throws(Exception::class)
    fun testSyncDownForLayoutTarget() {

        // Builds layout sync down target and performs sync.
        trySyncDown(
            MergeMode.LEAVE_IF_CHANGED, LayoutSyncDownTarget(
                Constants.ACCOUNT, Constants.FORM_FACTOR_MEDIUM,
                Constants.LAYOUT_TYPE_COMPACT, Constants.MODE_EDIT, null
            ), ACCOUNTS_SOUP
        )
        val smartStoreQuery = QuerySpec.buildAllQuerySpec(
            ACCOUNTS_SOUP,
            SyncTarget.SYNC_ID, QuerySpec.Order.ascending, 1
        )
        val rows = smartStore.query(smartStoreQuery, 0)
        Assert.assertEquals("Number of rows should be 1", 1, rows.length())
        val layout = rows.optJSONObject(0)
        Assert.assertNotNull("Layout should not be null", layout)
        val layoutType = layout.optString(LayoutSyncDownTarget.LAYOUT_TYPE)
        Assert.assertEquals(
            "Layout type should be ${Constants.LAYOUT_TYPE_COMPACT}",
            Constants.LAYOUT_TYPE_COMPACT, layoutType
        )
        val mode = layout.optString(LayoutSyncDownTarget.MODE)
        Assert.assertEquals("Mode should be ${Constants.MODE_EDIT}", Constants.MODE_EDIT, mode)
    }

    /**
     * Sync down the test accounts, modify a few on the server, re-sync, make sure only the updated ones are downloaded
     */
    @Test
    @Throws(Exception::class)
    fun testReSync() {
        // first sync down
        val syncId = trySyncDown(MergeMode.OVERWRITE)

        // Check sync time stamp
        var sync = syncManager.getSyncStatus(syncId)!!
        val target = sync.target as SyncDownTarget
        val options = sync.options
        val maxTimeStamp = sync.maxTimeStamp
        Assert.assertTrue("Wrong time stamp", maxTimeStamp > 0)

        // Make some remote change
        val idToFieldsUpdated = makeRemoteChanges(idToFields, Constants.ACCOUNT)

        // Call reSync
        val queue = SyncUpdateCallbackQueue(syncId)
        syncManager.reSync(syncId, queue)

        // Check status updates
        checkStatus(queue.getNextSyncUpdate(syncId), syncDown, syncId, target, options, RUNNING, 0, -1)
        checkStatus(queue.getNextSyncUpdate(syncId), syncDown, syncId, target, options, RUNNING, 0, idToFieldsUpdated.size)
        checkStatus(queue.getNextSyncUpdate(syncId), syncDown, syncId, target, options, DONE, 100, idToFieldsUpdated.size)

        // Check db
        checkDb(idToFieldsUpdated, ACCOUNTS_SOUP)

        // Check sync time stamp
        Assert.assertTrue("Wrong time stamp", syncManager.getSyncStatus(syncId)!!.maxTimeStamp > maxTimeStamp)
    }

    /**
     * Sync down the test accounts, modify a few on the server, re-sync using sync name, make sure only the updated ones are downloaded
     */
    @Test
    @Throws(Exception::class)
    fun testReSyncByName() {
        val syncName = "syncForTestReSyncByName"

        // first sync down
        val syncId = trySyncDown(MergeMode.OVERWRITE, syncName)

        // Check sync time stamp
        var sync = syncManager.getSyncStatus(syncId)!!
        val target = sync.target as SyncDownTarget
        val options = sync.options
        val maxTimeStamp = sync.maxTimeStamp
        Assert.assertTrue("Wrong time stamp", maxTimeStamp > 0)

        // Make some remote change
        val idToFieldsUpdated = makeRemoteChanges(idToFields, Constants.ACCOUNT)

        // Call reSync
        val queue = SyncUpdateCallbackQueue(syncId)
        syncManager.reSync(syncName, queue)

        // Check status updates
        checkStatus(queue.getNextSyncUpdate(syncId), syncDown, syncId, target, options, RUNNING, 0, -1)
        checkStatus(queue.getNextSyncUpdate(syncId), syncDown, syncId, target, options, RUNNING, 0, idToFieldsUpdated.size)
        checkStatus(queue.getNextSyncUpdate(syncId), syncDown, syncId, target, options, DONE, 100, idToFieldsUpdated.size)

        // Check db
        checkDb(idToFieldsUpdated, ACCOUNTS_SOUP)

        // Check sync time stamp
        Assert.assertTrue("Wrong time stamp", syncManager.getSyncStatus(syncId)!!.maxTimeStamp > maxTimeStamp)
    }

    /**
     * Call reSync with the name of non-existing sync, expect exception
     */
    @Test
    @Throws(Exception::class)
    fun testReSyncByNameWithWrongName() {
        val syncName = "testReSyncByNameWithWrongName"
        try {
            syncManager.reSync(syncName, null)
            Assert.fail("Expected exception")
        } catch (e: MobileSyncException) {
            Assert.assertTrue(e.message!!.contains("does not exist"))
        }
    }

    /**
     * Sync down the test accounts, modify a few, sync up using TestSyncUpTarget, check smartstore
     */
    @Test
    @Throws(Exception::class)
    fun testCustomSyncUpWithLocallyUpdatedRecords() {
        // First sync down
        trySyncDown(MergeMode.OVERWRITE)

        // Update a few entries locally
        val idToFieldsLocallyUpdated = makeLocalChanges(idToFields, ACCOUNTS_SOUP)

        // Sync up
        val collector = TestSyncUpTarget.ActionCollector()
        val target = TestSyncUpTarget(TestSyncUpTarget.SyncBehavior.NO_FAIL)
        TestSyncUpTarget.setActionCollector(collector)
        trySyncUp(target, 3, MergeMode.OVERWRITE)

        // Check that db doesn't show entries as locally modified anymore
        val ids = idToFieldsLocallyUpdated.keys
        checkDbStateFlags(ids, false, false, false, ACCOUNTS_SOUP)

        // Check what got synched up
        val idsUpdatedByTarget = collector.updatedRecordIds
        Assert.assertEquals("Wrong number of records updated by target", 3, idsUpdatedByTarget.size)
        for (idUpdatedByTarget in idsUpdatedByTarget) {
            Assert.assertTrue("Unexpected id:$idUpdatedByTarget", idToFieldsLocallyUpdated.containsKey(idUpdatedByTarget))
        }
    }

    /**
     * Create accounts locally, sync up using TestSyncUpTarget, check smartstore
     */
    @Test
    @Throws(Exception::class)
    fun testCustomSyncUpWithLocallyCreatedRecords() {
        // Create a few entries locally
        val names = arrayOf(
            createRecordName(Constants.ACCOUNT),
            createRecordName(Constants.ACCOUNT),
            createRecordName(Constants.ACCOUNT)
        )
        createAccountsLocally(names)

        // Sync up
        val collector = TestSyncUpTarget.ActionCollector()
        val target = TestSyncUpTarget(TestSyncUpTarget.SyncBehavior.NO_FAIL)
        TestSyncUpTarget.setActionCollector(collector)
        trySyncUp(target, 3, MergeMode.OVERWRITE)

        // Check that db doesn't show entries as locally created anymore and that they use sfdc id
        val idToFieldsCreated = getIdToFieldsByName(
            ACCOUNTS_SOUP,
            arrayOf(Constants.NAME, Constants.DESCRIPTION),
            Constants.NAME,
            names
        )
        checkDbStateFlags(idToFieldsCreated.keys, false, false, false, ACCOUNTS_SOUP)

        // Check what got synched up
        val idsCreatedByTarget = collector.createdRecordIds
        Assert.assertEquals("Wrong number of records created by target", 3, idsCreatedByTarget.size)
        for (idCreatedByTarget in idsCreatedByTarget) {
            Assert.assertTrue("Unexpected id:$idCreatedByTarget", idToFieldsCreated.containsKey(idCreatedByTarget))
        }

        // Adding to idToFields so that they get deleted in tearDown.
        (idToFields as MutableMap).putAll(idToFieldsCreated)
    }

    /**
     * Sync down the test accounts, delete a few, sync up using TestSyncUpTarget, check smartstore
     */
    @Test
    @Throws(Exception::class)
    fun testCustomSyncUpWithLocallyDeletedRecords() {
        // First sync down
        trySyncDown(MergeMode.OVERWRITE)

        // Delete a few entries locally
        val allIds = idToFields.keys.toTypedArray()
        val idsLocallyDeleted = arrayOf(allIds[0], allIds[1], allIds[2])
        deleteRecordsLocally(ACCOUNTS_SOUP, *idsLocallyDeleted)

        // Sync up
        val collector = TestSyncUpTarget.ActionCollector()
        val target = TestSyncUpTarget(TestSyncUpTarget.SyncBehavior.NO_FAIL)
        TestSyncUpTarget.setActionCollector(collector)
        trySyncUp(target, 3, MergeMode.OVERWRITE)

        // Check that db doesn't contain those entries anymore
        checkDbDeleted(ACCOUNTS_SOUP, idsLocallyDeleted, Constants.ID)

        // Check what got synched up
        val idsDeletedByTarget = collector.deletedRecordIds
        Assert.assertEquals("Wrong number of records created by target", 3, idsDeletedByTarget.size)
        for (idDeleted in idsLocallyDeleted) {
            Assert.assertTrue("Id not synched up$idDeleted", idsDeletedByTarget.contains(idDeleted))
        }
    }

    /**
     * Sync down the test accounts, modify a few, sync up using a soft failing TestSyncUpTarget, check smartstore
     */
    @Test
    @Throws(Exception::class)
    fun testSoftFailingCustomSyncUpWithLocallyUpdatedRecords() {
        // First sync down
        trySyncDown(MergeMode.OVERWRITE)

        // Update a few entries locally
        val idToFieldsLocallyUpdated = makeLocalChanges(idToFields, ACCOUNTS_SOUP)

        // Sync up
        val collector = TestSyncUpTarget.ActionCollector()
        val target = TestSyncUpTarget(TestSyncUpTarget.SyncBehavior.SOFT_FAIL_ON_SYNC)
        TestSyncUpTarget.setActionCollector(collector)
        trySyncUp(target, 3, MergeMode.OVERWRITE)

        // Check that db still shows entries as locally modified anymore
        val ids = idToFieldsLocallyUpdated.keys
        checkDbStateFlags(ids, false, true, false, ACCOUNTS_SOUP)

        // Check what got synched up
        val idsUpdatedByTarget = collector.updatedRecordIds
        Assert.assertEquals("Wrong number of records updated by target", 0, idsUpdatedByTarget.size)
    }

    /**
     * Sync down the test accounts, modify a few, sync up using a hard failing TestSyncUpTarget, check smartstore
     */
    @Test
    @Throws(Exception::class)
    fun testHardFailingCustomSyncUpWithLocallyUpdatedRecords() {
        // First sync down
        trySyncDown(MergeMode.OVERWRITE)

        // Update a few entries locally
        val idToFieldsLocallyUpdated = makeLocalChanges(idToFields, ACCOUNTS_SOUP)

        // Sync up
        val collector = TestSyncUpTarget.ActionCollector()
        val target = TestSyncUpTarget(TestSyncUpTarget.SyncBehavior.HARD_FAIL_ON_SYNC)
        TestSyncUpTarget.setActionCollector(collector)
        trySyncUp(target, 3, MergeMode.OVERWRITE, true /* expect failure */)

        // Check that db still shows entries as locally modified
        val ids = idToFieldsLocallyUpdated.keys
        checkDbStateFlags(ids, false, true, false, ACCOUNTS_SOUP)

        // Check what got synched up
        val idsUpdatedByTarget = collector.updatedRecordIds
        Assert.assertEquals("Wrong number of records updated by target", 0, idsUpdatedByTarget.size)
    }

    /**
     * Create accounts locally, sync up using soft failing TestSyncUpTarget, check smartstore
     */
    @Test
    @Throws(Exception::class)
    fun testSoftFailingCustomSyncUpWithLocallyCreatedRecords() {
        // Create a few entries locally
        val names = arrayOf(
            createRecordName(Constants.ACCOUNT),
            createRecordName(Constants.ACCOUNT),
            createRecordName(Constants.ACCOUNT)
        )
        createAccountsLocally(names)

        // Sync up
        val collector = TestSyncUpTarget.ActionCollector()
        val target = TestSyncUpTarget(TestSyncUpTarget.SyncBehavior.SOFT_FAIL_ON_SYNC)
        TestSyncUpTarget.setActionCollector(collector)
        trySyncUp(target, 3, MergeMode.OVERWRITE)

        // Check that db still show show entries as locally created
        val idToFieldsCreated = getIdToFieldsByName(
            ACCOUNTS_SOUP,
            arrayOf(Constants.NAME, Constants.DESCRIPTION),
            Constants.NAME,
            names
        )
        checkDbStateFlags(idToFieldsCreated.keys, true, false, false, ACCOUNTS_SOUP)

        // Check what got synched up
        val idsCreatedByTarget = collector.createdRecordIds
        Assert.assertEquals("Wrong number of records created by target", 0, idsCreatedByTarget.size)

        // Adding to idToFields so that they get deleted in tearDown.
        (idToFields as MutableMap).putAll(idToFieldsCreated)
    }

    /**
     * Create accounts locally, sync up using hard failing TestSyncUpTarget, check smartstore
     */
    @Test
    @Throws(Exception::class)
    fun testHardFailingCustomSyncUpWithLocallyCreatedRecords() {
        // Create a few entries locally
        val names = arrayOf(
            createRecordName(Constants.ACCOUNT),
            createRecordName(Constants.ACCOUNT),
            createRecordName(Constants.ACCOUNT)
        )
        createAccountsLocally(names)

        // Sync up
        val collector = TestSyncUpTarget.ActionCollector()
        val target = TestSyncUpTarget(TestSyncUpTarget.SyncBehavior.HARD_FAIL_ON_SYNC)
        TestSyncUpTarget.setActionCollector(collector)
        trySyncUp(target, 3, MergeMode.OVERWRITE, true /* expect failure */)

        // Check that db still show show entries as locally created
        val idToFieldsCreated = getIdToFieldsByName(
            ACCOUNTS_SOUP,
            arrayOf(Constants.NAME, Constants.DESCRIPTION),
            Constants.NAME,
            names
        )
        checkDbStateFlags(idToFieldsCreated.keys, true, false, false, ACCOUNTS_SOUP)

        // Check what got synched up
        val idsCreatedByTarget = collector.createdRecordIds
        Assert.assertEquals("Wrong number of records created by target", 0, idsCreatedByTarget.size)

        // Adding to idToFields so that they get deleted in tearDown.
        (idToFields as MutableMap).putAll(idToFieldsCreated)
    }

    /**
     * Sync down the test accounts, delete a few, sync up using soft failing TestSyncUpTarget, check smartstore
     */
    @Test
    @Throws(Exception::class)
    fun testSoftFailingCustomSyncUpWithLocallyDeletedRecords() {
        // First sync down
        trySyncDown(MergeMode.OVERWRITE)

        // Delete a few entries locally
        val allIds = idToFields.keys.toTypedArray()
        val idsLocallyDeleted = arrayOf(allIds[0], allIds[1], allIds[2])
        deleteRecordsLocally(ACCOUNTS_SOUP, *idsLocallyDeleted)

        // Sync up
        val collector = TestSyncUpTarget.ActionCollector()
        val target = TestSyncUpTarget(TestSyncUpTarget.SyncBehavior.SOFT_FAIL_ON_SYNC)
        TestSyncUpTarget.setActionCollector(collector)
        trySyncUp(target, 3, MergeMode.OVERWRITE)

        // Check that db still contains those entries
        val ids = idsLocallyDeleted.asList()
        checkDbStateFlags(ids, false, false, true, ACCOUNTS_SOUP)

        // Check what got synched up
        val idsDeletedByTarget = collector.deletedRecordIds
        Assert.assertEquals("Wrong number of records created by target", 0, idsDeletedByTarget.size)
    }

    /**
     * Sync down the test accounts, delete a few, sync up using hard failing TestSyncUpTarget, check smartstore
     */
    @Test
    @Throws(Exception::class)
    fun testHardFailingCustomSyncUpWithLocallyDeletedRecords() {
        // First sync down
        trySyncDown(MergeMode.OVERWRITE)

        // Delete a few entries locally
        val allIds = idToFields.keys.toTypedArray()
        val idsLocallyDeleted = arrayOf(allIds[0], allIds[1], allIds[2])
        deleteRecordsLocally(ACCOUNTS_SOUP, *idsLocallyDeleted)

        // Sync up
        val collector = TestSyncUpTarget.ActionCollector()
        val target = TestSyncUpTarget(TestSyncUpTarget.SyncBehavior.HARD_FAIL_ON_SYNC)
        TestSyncUpTarget.setActionCollector(collector)
        trySyncUp(target, 3, MergeMode.OVERWRITE, true /* expect failure */)

        // Check that db still contains those entries
        val ids = idsLocallyDeleted.asList()
        checkDbStateFlags(ids, false, false, true, ACCOUNTS_SOUP)

        // Check what got synched up
        val idsDeletedByTarget = collector.deletedRecordIds
        Assert.assertEquals("Wrong number of records created by target", 0, idsDeletedByTarget.size)
    }

    /**
     * Test reSync while sync is running
     */
    @Test
    @Throws(JSONException::class)
    fun testReSyncRunningSync() {
        // Create sync
        val target = SlowSoqlSyncDownTarget("SELECT Id, Name, LastModifiedDate FROM Account WHERE Id IN ${makeInClause(idToFields.keys)}")
        val options = SyncOptions.optionsForSyncDown(MergeMode.LEAVE_IF_CHANGED)
        val sync = SyncState.createSyncDown(smartStore, target, options, ACCOUNTS_SOUP, null)
        val syncId = sync.id
        checkStatus(sync, syncDown, syncId, target, options, NEW, 0, -1)

        // Run sync - will freeze during fetch
        val queue = SyncUpdateCallbackQueue(syncId)
        syncManager.runSync(sync, queue)

        // Wait for sync to be running
        queue.getNextSyncUpdate(syncId)

        // Calling reSync -- expect exception
        try {
            syncManager.reSync(syncId, null)
            Assert.fail("Re sync should have failed")
        } catch (e: MobileSyncException) {
            Assert.assertTrue("Re sync should have failed because sync is already running", e.message!!.contains("still running"))
        }

        // Wait for sync to complete successfully
        while (!queue.getNextSyncUpdate(syncId).isDone);
        try {
            Thread.sleep(1000)
        } catch (e: InterruptedException) {
        }

        // Calling reSync again -- does not expect exception
        try {
            syncManager.reSync(syncId, queue)
        } catch (e: MobileSyncException) {
            Assert.fail("Re sync should not have failed")
        }

        // Waiting for reSync to complete successfully
        while (!queue.getNextSyncUpdate(syncId).isDone);
    }

    /**
     * Tests if ghost records are cleaned locally for a SOQL target.
     */
    @Test
    @Ignore("Production bug: SmartStore.deleteByQuery NPE at SmartStore.kt:1129")
    @Throws(Exception::class)
    fun testCleanResyncGhostsForSOQLTarget() {

        // Creates 3 accounts on the server.
        val numberAccounts = 3
        val accounts = createRecordsOnServer(numberAccounts, Constants.ACCOUNT)
        Assert.assertEquals("Wrong number of accounts created", numberAccounts, accounts.size)
        val accountIds = accounts.keys.toTypedArray()

        // Builds SOQL sync down target and performs initial sync.
        val soql = "SELECT Id, Name FROM Account WHERE Id IN ${makeInClause(accountIds)}"
        val syncId = trySyncDown(MergeMode.LEAVE_IF_CHANGED, SoqlSyncDownTarget(soql), ACCOUNTS_SOUP, accounts.size, 1, null)
        checkDbExist(ACCOUNTS_SOUP, accountIds, Constants.ID)

        // Deletes 1 account on the server and verifies the ghost record is cleared from the soup.
        deleteRecordsByIdOnServer(setOf(accountIds[0]), Constants.ACCOUNT)
        tryCleanResyncGhosts(syncId)
        checkDbExist(ACCOUNTS_SOUP, arrayOf(accountIds[1], accountIds[2]), Constants.ID)
        checkDbDeleted(ACCOUNTS_SOUP, arrayOf(accountIds[0]), Constants.ID)

        // Deletes the remaining accounts on the server.
        deleteRecordsByIdOnServer(setOf(accountIds[1], accountIds[2]), Constants.ACCOUNT)
    }

    /**
     * Tests clean ghosts when soup is populated through more than one sync down
     */
    @Test
    @Ignore("Production bug: SmartStore.deleteByQuery NPE at SmartStore.kt:1129")
    @Throws(Exception::class)
    fun testCleanResyncGhostsWithMultipleSyncs() {

        // Creates 6 accounts on the server.
        val numberAccounts = 6
        val accounts = createRecordsOnServer(numberAccounts, Constants.ACCOUNT)
        Assert.assertEquals("Wrong number of accounts created", numberAccounts, accounts.size)
        val accountIds = accounts.keys.toTypedArray()
        val accountIdsFirstSubset = accountIds.copyOfRange(0, 3) // id0, id1, id2
        val accountIdsSecondSubset = accountIds.copyOfRange(2, 6) //          id2, id3, id4, id5

        // Runs a first SOQL sync down target (bringing down id0, id1, id2)
        val firstSyncId = trySyncDown(
            MergeMode.LEAVE_IF_CHANGED,
            SoqlSyncDownTarget("SELECT Id, Name FROM Account WHERE Id IN ${makeInClause(accountIdsFirstSubset)}"),
            ACCOUNTS_SOUP,
            accountIdsFirstSubset.size,
            1,
            null
        )
        checkDbExist(ACCOUNTS_SOUP, accountIdsFirstSubset, Constants.ID)
        checkDbSyncIdField(accountIdsFirstSubset, firstSyncId, ACCOUNTS_SOUP)

        // Runs a second SOQL sync down target (bringing down id2, id3, id4, id5)
        val secondSyncId = trySyncDown(
            MergeMode.LEAVE_IF_CHANGED,
            SoqlSyncDownTarget("SELECT Id, Name FROM Account WHERE Id IN ${makeInClause(accountIdsSecondSubset)}"),
            ACCOUNTS_SOUP,
            accountIdsSecondSubset.size,
            1,
            null
        )
        checkDbExist(ACCOUNTS_SOUP, accountIdsSecondSubset, Constants.ID)
        checkDbSyncIdField(accountIdsSecondSubset, secondSyncId, ACCOUNTS_SOUP)

        // Deletes id0, id2, id5 on the server
        deleteRecordsByIdOnServer(setOf(accountIds[0], accountIds[2], accountIds[5]), Constants.ACCOUNT)

        // Cleaning ghosts of first sync (should only remove id0)
        tryCleanResyncGhosts(firstSyncId)
        checkDbExist(ACCOUNTS_SOUP, arrayOf(accountIds[1], accountIds[2], accountIds[3], accountIds[4], accountIds[5]), Constants.ID)
        checkDbDeleted(ACCOUNTS_SOUP, arrayOf(accountIds[0]), Constants.ID)

        // Cleaning ghosts of second sync (should remove id2 and id5)
        tryCleanResyncGhosts(secondSyncId)
        checkDbExist(ACCOUNTS_SOUP, arrayOf(accountIds[1], accountIds[3], accountIds[4]), Constants.ID)
        checkDbDeleted(ACCOUNTS_SOUP, arrayOf(accountIds[2], accountIds[5]), Constants.ID)

        // Deletes the remaining accounts on the server.
        deleteRecordsByIdOnServer(setOf(accountIds[1], accountIds[3], accountIds[4]), Constants.ACCOUNT)
    }

    /**
     * Tests if ghost records are cleaned locally for a MRU target.
     */
    @Test
    @Ignore("Production bug: SmartStore.deleteByQuery NPE at SmartStore.kt:1129")
    @Throws(Exception::class)
    fun testCleanResyncGhostsForMRUTarget() {
        // Creates 3 accounts on the server.
        val numberAccounts = 3
        val accounts = createRecordsOnServer(numberAccounts, Constants.ACCOUNT)
        Assert.assertEquals("Wrong number of accounts created", numberAccounts, accounts.size)
        val accountIds = accounts.keys.toTypedArray()

        // Builds MRU sync down target and performs initial sync.
        val fieldList = listOf(Constants.ID, Constants.NAME)
        val syncId = trySyncDown(MergeMode.LEAVE_IF_CHANGED, MruSyncDownTarget(fieldList, Constants.ACCOUNT), ACCOUNTS_SOUP)
        checkDbExist(ACCOUNTS_SOUP, accountIds, Constants.ID)

        // Deletes 1 account on the server and verifies the ghost record is cleared from the soup.
        deleteRecordsByIdOnServer(singletonList(accountIds[0]), Constants.ACCOUNT)
        tryCleanResyncGhosts(syncId)
        checkDbDeleted(ACCOUNTS_SOUP, arrayOf(accountIds[0]), Constants.ID)

        // Deletes the remaining accounts on the server.
        deleteRecordsByIdOnServer(listOf(accountIds[1], accountIds[2]), Constants.ACCOUNT)
    }

    /**
     * Tests if ghost records are cleaned locally for a SOSL target.
     */
    @Test
    @Ignore("Production bug: SmartStore.deleteByQuery NPE at SmartStore.kt:1129")
    @Throws(Exception::class)
    fun testCleanResyncGhostsForSOSLTarget() {

        // Creates 1 account on the server.
        val accounts = createRecordsOnServer(1, Constants.ACCOUNT)
        Assert.assertEquals("1 account should have been created", 1, accounts.size)
        val accountIds = accounts.keys.toTypedArray()

        // Builds SOSL sync down target and performs initial sync.
        val soslBuilder = SOSLBuilder.getInstanceWithSearchTerm(accounts[accountIds[0]]!!)
        val returningBuilder = SOSLReturningBuilder.getInstanceWithObjectName(Constants.ACCOUNT)
        returningBuilder.fields("Id, Name")
        val sosl = soslBuilder.returning(returningBuilder).searchGroup("NAME FIELDS").build()
        val syncId = trySyncDown(MergeMode.LEAVE_IF_CHANGED, SoslSyncDownTarget(sosl!!), ACCOUNTS_SOUP)
        checkDbExist(ACCOUNTS_SOUP, accountIds, Constants.ID)

        // Deletes 1 account on the server and verifies the ghost record is cleared from the soup.
        deleteRecordsByIdOnServer(setOf(accountIds[0]), Constants.ACCOUNT)
        tryCleanResyncGhosts(syncId)
        checkDbDeleted(ACCOUNTS_SOUP, arrayOf(accountIds[0]), Constants.ID)

        // Deletes the remaining accounts on the server.
        deleteRecordsByIdOnServer(setOf(accountIds[0]), Constants.ACCOUNT)
    }

    /**
     * Create sync down, runs it, runs clean ghosts, re-run sync down
     */
    @Ignore("Production bug: MobileSyncException 'Failed to save sync state' crashes test runner due to SyncState.save issue after Kotlin migration")
    @Test
    @Throws(Exception::class)
    fun testSyncCleanGhostsReSync() {

        // Creates 3 accounts on the server.
        val numberAccounts = 3
        val accounts = createRecordsOnServer(numberAccounts, Constants.ACCOUNT)
        Assert.assertEquals("Wrong number of accounts created", numberAccounts, accounts.size)
        val accountIds = accounts.keys.toTypedArray()

        // Builds SOQL sync down target and performs initial sync.
        val soql = "SELECT Id, Name FROM Account WHERE Id IN ${makeInClause(accountIds)}"
        val syncId = trySyncDown(MergeMode.LEAVE_IF_CHANGED, SoqlSyncDownTarget(soql), ACCOUNTS_SOUP, accounts.size, 1, null)
        checkDbExist(ACCOUNTS_SOUP, accountIds, Constants.ID)

        // Deletes 1 account on the server and verifies the ghost record is cleared from the soup.
        deleteRecordsByIdOnServer(setOf(accountIds[0]), Constants.ACCOUNT)
        tryCleanResyncGhosts(syncId)
        checkDbExist(ACCOUNTS_SOUP, arrayOf(accountIds[1], accountIds[2]), Constants.ID)
        checkDbDeleted(ACCOUNTS_SOUP, arrayOf(accountIds[0]), Constants.ID)

        // Calls reSync
        val queue = SyncUpdateCallbackQueue(syncId)
        try {
            syncManager.reSync(syncId, queue)
        } catch (e: MobileSyncException) {
            Assert.fail("Unexpected exception:$e")
        }

        // Waiting for reSync to complete successfully
        while (!queue.getNextSyncUpdate(syncId).isDone);

        // Deletes the remaining accounts on the server.
        deleteRecordsByIdOnServer(setOf(accountIds[1], accountIds[2]), Constants.ACCOUNT)
    }

    /**
     * Create sync down, get it by id, delete it by id, make sure it's gone
     */
    @Test
    @Throws(JSONException::class)
    fun testCreateGetDeleteSyncDownById() {
        // Create
        val sync = SyncState.createSyncDown(
            smartStore,
            SoqlSyncDownTarget("SELECT Id, Name from Account"),
            SyncOptions.optionsForSyncDown(MergeMode.LEAVE_IF_CHANGED),
            ACCOUNTS_SOUP,
            null
        )
        val syncId = sync.id
        // Get by id
        val fetchedSync = SyncState.byId(smartStore, syncId)
        JSONTestHelper.assertSameJSON("Wrong sync state", sync.asJSON(), fetchedSync!!.asJSON())
        // Delete by id
        SyncState.deleteSync(smartStore, syncId)
        Assert.assertNull("Sync should be gone", SyncState.byId(smartStore, syncId))
    }

    /**
     * Create sync down with a name, get it by name, delete it by name, make sure it's gone
     */
    @Test
    @Throws(JSONException::class)
    fun testCreateGetDeleteSyncDownWithName() {
        // Create a named sync down
        val syncName = "MyNamedSyncDown"
        val sync = SyncState.createSyncDown(
            smartStore,
            SoqlSyncDownTarget("SELECT Id, Name from Account"),
            SyncOptions.optionsForSyncDown(MergeMode.LEAVE_IF_CHANGED),
            ACCOUNTS_SOUP,
            syncName
        )
        val syncId = sync.id
        // Get by name
        val fetchedSync = SyncState.byName(smartStore, syncName)
        JSONTestHelper.assertSameJSON("Wrong sync state", sync.asJSON(), fetchedSync!!.asJSON())
        // Delete by name
        SyncState.deleteSync(smartStore, syncName)
        Assert.assertNull("Sync should be gone", SyncState.byId(smartStore, syncId))
        Assert.assertNull("Sync should be gone", SyncState.byName(smartStore, syncName))
    }

    /**
     * Create sync up, get it by id, delete it by id, make sure it's gone
     */
    @Test
    @Throws(JSONException::class)
    fun testCreateGetDeleteSyncUpById() {
        // Create
        val sync = SyncState.createSyncUp(
            smartStore,
            SyncUpTarget(),
            SyncOptions.optionsForSyncDown(MergeMode.LEAVE_IF_CHANGED),
            ACCOUNTS_SOUP,
            null
        )
        val syncId = sync.id
        // Get by id
        val fetchedSync = SyncState.byId(smartStore, syncId)
        JSONTestHelper.assertSameJSON("Wrong sync state", sync.asJSON(), fetchedSync!!.asJSON())
        // Delete by id
        SyncState.deleteSync(smartStore, syncId)
        Assert.assertNull("Sync should be gone", SyncState.byId(smartStore, syncId))
    }

    /**
     * Create sync up with a name, get it by name, delete it by name, make sure it's gone
     */
    @Test
    @Throws(JSONException::class)
    fun testCreateGetDeleteSyncUpWithName() {
        // Create a named sync up
        val syncName = "MyNamedSyncUp"
        val sync = SyncState.createSyncUp(
            smartStore,
            SyncUpTarget(),
            SyncOptions.optionsForSyncDown(MergeMode.LEAVE_IF_CHANGED),
            ACCOUNTS_SOUP,
            syncName
        )
        val syncId = sync.id
        // Get by name
        val fetchedSync = SyncState.byName(smartStore, syncName)
        JSONTestHelper.assertSameJSON("Wrong sync state", sync.asJSON(), fetchedSync!!.asJSON())
        // Delete by name
        SyncState.deleteSync(smartStore, syncName)
        Assert.assertNull("Sync should be gone", SyncState.byId(smartStore, syncId))
        Assert.assertNull("Sync should be gone", SyncState.byName(smartStore, syncName))
    }

    /**
     * Create sync with a name, make sure a new sync down with the same name cannot be created
     */
    @Test
    @Throws(JSONException::class)
    fun testCreateSyncDownWithExistingName() {
        // Create a named sync
        val syncName = "MyNamedSync"
        SyncState.createSyncUp(
            smartStore,
            SyncUpTarget(),
            SyncOptions.optionsForSyncDown(MergeMode.LEAVE_IF_CHANGED),
            ACCOUNTS_SOUP,
            syncName
        )
        // Try to create a sync down with the same name
        try {
            SyncState.createSyncDown(
                smartStore,
                SoqlSyncDownTarget("SELECT Id, Name from Account"),
                SyncOptions.optionsForSyncDown(MergeMode.LEAVE_IF_CHANGED),
                ACCOUNTS_SOUP,
                syncName
            )
            Assert.fail("MobileSyncException should have been thrown")
        } catch (e: MobileSyncException) {
            Assert.assertTrue(e.message!!.contains("already a sync with name"))
        }
        // Delete by name
        SyncState.deleteSync(smartStore, syncName)
        Assert.assertNull("Sync should be gone", SyncState.byName(smartStore, syncName))
    }

    /**
     * Create sync with a name, make sure a new sync up with the same name cannot be created
     */
    @Test
    @Throws(JSONException::class)
    fun testCreateSyncUpWithExistingName() {
        // Create a named sync
        val syncName = "MyNamedSync"
        SyncState.createSyncDown(
            smartStore,
            SoqlSyncDownTarget("SELECT Id, Name from Account"),
            SyncOptions.optionsForSyncDown(MergeMode.LEAVE_IF_CHANGED),
            ACCOUNTS_SOUP,
            syncName
        )
        // Try to create a sync down with the same name
        try {
            SyncState.createSyncUp(
                smartStore,
                SyncUpTarget(),
                SyncOptions.optionsForSyncDown(MergeMode.LEAVE_IF_CHANGED),
                ACCOUNTS_SOUP,
                syncName
            )
            Assert.fail("MobileSyncException should have been thrown")
        } catch (e: MobileSyncException) {
            Assert.assertTrue(e.message!!.contains("already a sync with name"))
        }
        // Delete by name
        SyncState.deleteSync(smartStore, syncName)
        Assert.assertNull("Sync should be gone", SyncState.byName(smartStore, syncName))
    }

    /**
     * Run sync down using TestSyncDownTarget
     * @throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testCustomSyncDownTarget() {
        val syncName = "testCustomSyncDownTarget"
        val numberOfRecords = 30
        val target = TestSyncDownTarget("test", numberOfRecords, 10, 0)
        val syncId = trySyncDown(MergeMode.LEAVE_IF_CHANGED, target, ACCOUNTS_SOUP, numberOfRecords, 3, syncName)

        // Check sync time stamp
        val sync = syncManager.getSyncStatus(syncId)!!
        Assert.assertEquals("Wrong time stamp", target.dateForPosition(numberOfRecords - 1).time, sync.maxTimeStamp)

        // Check db
        checkDbForAfterTestSyncDown(target, ACCOUNTS_SOUP, numberOfRecords)
    }

    /**
     * Test running and stopping a single sync down (using TestSyncDownTarget)
     * @throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testStopResumeSingleSyncDown() {
        val syncName = "testStopResumeSingleSyncDown"
        val numberOfRecords = 10
        val target = TestSyncDownTarget("test", numberOfRecords, 1, 50)
        val options = SyncOptions.optionsForSyncDown(MergeMode.LEAVE_IF_CHANGED)
        val sync = SyncState.createSyncDown(smartStore, target, options, ACCOUNTS_SOUP, syncName)
        val syncId = sync.id

        // Run sync
        val queue = SyncUpdateCallbackQueue(syncId)
        syncManager.reSync(syncName, queue)

        // Check status updates
        checkStatus(queue.getNextSyncUpdate(syncId), syncDown, syncId, target, options, RUNNING, 0, -1)
        checkStatus(queue.getNextSyncUpdate(syncId), syncDown, syncId, target, options, RUNNING, 0, numberOfRecords)
        checkStatus(queue.getNextSyncUpdate(syncId), syncDown, syncId, target, options, RUNNING, 10, numberOfRecords)
        checkStatus(queue.getNextSyncUpdate(syncId), syncDown, syncId, target, options, RUNNING, 20, numberOfRecords)
        checkStatus(queue.getNextSyncUpdate(syncId), syncDown, syncId, target, options, RUNNING, 30, numberOfRecords)
        checkStatus(queue.getNextSyncUpdate(syncId), syncDown, syncId, target, options, RUNNING, 40, numberOfRecords)
        checkStatus(queue.getNextSyncUpdate(syncId), syncDown, syncId, target, options, RUNNING, 50, numberOfRecords)

        // Stop sync manager
        stopSyncManager(1000)
        checkStatus(queue.getNextSyncUpdate(syncId), syncDown, syncId, target, options, STOPPED, 50, numberOfRecords)
        val numberOfRecordsFetched = (numberOfRecords * 0.5).toInt()
        val numberOfRecordsLeft = numberOfRecords - numberOfRecordsFetched + 1 /* we refetch records at maxTimeStamp when a sync was stopped */

        // Check db
        checkDbForAfterTestSyncDown(target, ACCOUNTS_SOUP, numberOfRecordsFetched)

        // Check sync time stamp and status
        checkSyncState(syncId, target.dateForPosition(numberOfRecordsFetched - 1).time, STOPPED)

        // Try to restart sync while sync manager is paused
        try {
            syncManager.reSync(syncName, queue)
            Assert.fail("Expected exception")
        } catch (e: MobileSyncException) {
            Assert.assertTrue("Wrong exception", e is SyncManagerStoppedException)
        }

        // Restarting sync manager without restarting syncs
        syncManager.restart(false, null)
        Assert.assertFalse("Stopped should be false", syncManager.isStopped)

        // Check sync time stamp and status
        checkSyncState(syncId, target.dateForPosition(numberOfRecordsFetched - 1).time, STOPPED)

        // Stop sync manager
        stopSyncManager(1000)

        // Restarting sync manager restarting syncs
        syncManager.restart(true, queue)
        checkStatus(queue.getNextSyncUpdate(syncId), syncDown, syncId, target, options, RUNNING, 0, -1)
        checkStatus(queue.getNextSyncUpdate(syncId), syncDown, syncId, target, options, RUNNING, 0, numberOfRecordsLeft)
        checkStatus(queue.getNextSyncUpdate(syncId), syncDown, syncId, target, options, RUNNING, 16, numberOfRecordsLeft)
        checkStatus(queue.getNextSyncUpdate(syncId), syncDown, syncId, target, options, RUNNING, 33, numberOfRecordsLeft)
        checkStatus(queue.getNextSyncUpdate(syncId), syncDown, syncId, target, options, RUNNING, 50, numberOfRecordsLeft)
        checkStatus(queue.getNextSyncUpdate(syncId), syncDown, syncId, target, options, RUNNING, 66, numberOfRecordsLeft)
        checkStatus(queue.getNextSyncUpdate(syncId), syncDown, syncId, target, options, RUNNING, 83, numberOfRecordsLeft)
        checkStatus(queue.getNextSyncUpdate(syncId), syncDown, syncId, target, options, DONE, 100, numberOfRecordsLeft)
        checkDbForAfterTestSyncDown(target, ACCOUNTS_SOUP, numberOfRecords)
    }

    /**
     * Test running and stopping multiple (using TestSyncDownTarget)
     * @throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testStopResumeMultipleSyncDowns() {
        val syncName1 = "testStopResumeMultipleSyncDowns1"
        val syncName2 = "testStopResumeMultipleSyncDowns2"

        val numberRecords1 = 5
        val numberRecords2 = 4

        val options = SyncOptions.optionsForSyncDown(MergeMode.LEAVE_IF_CHANGED)
        val target1 = TestSyncDownTarget("test1", numberRecords1, 1, 50)
        val target2 = TestSyncDownTarget("test2", numberRecords2, 1, 50)
        val syncId1 = SyncState.createSyncDown(smartStore, target1, options, ACCOUNTS_SOUP, syncName1).id
        val syncId2 = SyncState.createSyncDown(smartStore, target2, options, ACCOUNTS_SOUP, syncName2).id

        // Run sync
        val queue = SyncUpdateCallbackQueue(syncId1, syncId2)
        syncManager.reSync(syncName1, queue)
        try {
            // Sleeping a bit - to make sure it goes first
            Thread.sleep(25)
        } catch (e: Exception) {
            Assert.fail("Test interrupted")
        }
        syncManager.reSync(syncName2, queue)

        // Check status updates
        checkStatus(queue.getNextSyncUpdate(syncId1), syncDown, syncId1, target1, options, RUNNING, 0, -1)
        checkStatus(queue.getNextSyncUpdate(syncId2), syncDown, syncId2, target2, options, RUNNING, 0, -1)
        checkStatus(queue.getNextSyncUpdate(syncId1), syncDown, syncId1, target1, options, RUNNING, 0, numberRecords1)
        checkStatus(queue.getNextSyncUpdate(syncId1), syncDown, syncId1, target1, options, RUNNING, 20, numberRecords1)

        // Stop sync manager
        stopSyncManager(1000)
        checkStatus(queue.getNextSyncUpdate(syncId1), syncDown, syncId1, target1, options, STOPPED, 20, numberRecords1)
        checkStatus(queue.getNextSyncUpdate(syncId2), syncDown, syncId2, target2, options, STOPPED, 0, -1)
        val numberOfRecordsFetched1 = (numberRecords1 * 0.2).toInt()
        val numberRecordsLeft1 = numberRecords1 - numberOfRecordsFetched1 + 1/* we refetch records at maxTimeStamp when a sync was stopped */

        // Check db
        checkDbForAfterTestSyncDown(target1, ACCOUNTS_SOUP, numberOfRecordsFetched1)
        checkDbForAfterTestSyncDown(target2, ACCOUNTS_SOUP, 0)

        // Check sync time stamp and status
        checkSyncState(syncId1, target1.dateForPosition(numberOfRecordsFetched1 - 1).time, STOPPED)
        checkSyncState(syncId2, -1, STOPPED)

        // Restarting sync manager without restarting syncs
        syncManager.restart(false, queue)
        Assert.assertFalse("Stopped should be false", syncManager.isStopped)

        // Manually restart second sync
        syncManager.reSync(syncName2, queue)
        checkStatus(queue.getNextSyncUpdate(syncId2), syncDown, syncId2, target2, options, RUNNING, 0, -1)
        checkStatus(queue.getNextSyncUpdate(syncId2), syncDown, syncId2, target2, options, RUNNING, 0, numberRecords2)
        checkStatus(queue.getNextSyncUpdate(syncId2), syncDown, syncId2, target2, options, RUNNING, 25, numberRecords2)
        checkStatus(queue.getNextSyncUpdate(syncId2), syncDown, syncId2, target2, options, RUNNING, 50, numberRecords2)

        // Stop sync manager
        stopSyncManager(1000)
        checkStatus(queue.getNextSyncUpdate(syncId2), syncDown, syncId2, target2, options, STOPPED, 50, numberRecords2)
        val numberRecordsFetched2 = (numberRecords2 * 0.50).toInt()
        val numberRecordsLeft2 = numberRecords2 - numberRecordsFetched2 + 1/* we refetch records at maxTimeStamp when a sync was stopped */

        // Check sync time stamp and status
        checkSyncState(syncId1, target1.dateForPosition(numberOfRecordsFetched1 - 1).time, STOPPED)
        checkSyncState(syncId2, target2.dateForPosition(numberRecordsFetched2 - 1).time, STOPPED)

        // Check db
        checkDbForAfterTestSyncDown(target1, ACCOUNTS_SOUP, numberOfRecordsFetched1)
        checkDbForAfterTestSyncDown(target2, ACCOUNTS_SOUP, numberRecordsFetched2)

        // Restarting sync manager restarting syncs
        syncManager.restart(true, queue)
        checkStatus(queue.getNextSyncUpdate(syncId1), syncDown, syncId1, target1, options, RUNNING, 0, -1)
        checkStatus(queue.getNextSyncUpdate(syncId2), syncDown, syncId2, target2, options, RUNNING, 0, -1)
        checkStatus(queue.getNextSyncUpdate(syncId1), syncDown, syncId1, target1, options, RUNNING, 0, numberRecordsLeft1)
        checkStatus(queue.getNextSyncUpdate(syncId1), syncDown, syncId1, target1, options, RUNNING, 20, numberRecordsLeft1)
        checkStatus(queue.getNextSyncUpdate(syncId1), syncDown, syncId1, target1, options, RUNNING, 40, numberRecordsLeft1)
        checkStatus(queue.getNextSyncUpdate(syncId1), syncDown, syncId1, target1, options, RUNNING, 60, numberRecordsLeft1)
        checkStatus(queue.getNextSyncUpdate(syncId1), syncDown, syncId1, target1, options, RUNNING, 80, numberRecordsLeft1)
        checkStatus(queue.getNextSyncUpdate(syncId1), syncDown, syncId1, target1, options, DONE, 100, numberRecordsLeft1)

        // sync1 is done, sync2 should run next
        checkStatus(queue.getNextSyncUpdate(syncId2), syncDown, syncId2, target2, options, RUNNING, 0, numberRecordsLeft2)
        checkStatus(queue.getNextSyncUpdate(syncId2), syncDown, syncId2, target2, options, RUNNING, 33, numberRecordsLeft2)
        checkStatus(queue.getNextSyncUpdate(syncId2), syncDown, syncId2, target2, options, RUNNING, 66, numberRecordsLeft2)
        checkStatus(queue.getNextSyncUpdate(syncId2), syncDown, syncId2, target2, options, DONE, 100, numberRecordsLeft2)

        // Check db
        checkDbForAfterTestSyncDown(target1, ACCOUNTS_SOUP, numberRecords1)
        checkDbForAfterTestSyncDown(target2, ACCOUNTS_SOUP, numberRecords2)
    }

    @Throws(JSONException::class)
    private fun checkSyncState(syncId: Long, expectedTimeStamp: Long, expectedStatus: SyncState.Status) {
        val sync = syncManager.getSyncStatus(syncId)!!
        Assert.assertEquals("Wrong time stamp", expectedTimeStamp, sync.maxTimeStamp)
        Assert.assertEquals("Wrong status", expectedStatus, sync.status)
    }

    private fun stopSyncManager(sleepDuration: Int) {
        Assert.assertFalse("Stopped should be false", syncManager.isStopped)
        Assert.assertFalse("Stopping should be false", syncManager.isStopping)
        syncManager.stop()

        if (sleepDuration > 0) {
            // We expect stopping to take a while
            Assert.assertTrue("Stopped or stopping should be true", syncManager.isStopping || syncManager.isStopped)

            try {
                Thread.sleep(sleepDuration.toLong())
            } catch (e: Exception) {
                Assert.fail("Test interrupted")
            }
        }

        Assert.assertFalse("Stopping should be false", syncManager.isStopping)
        Assert.assertTrue("Stopped should be true", syncManager.isStopped)
    }

    @Throws(JSONException::class)
    private fun checkDbForAfterTestSyncDown(target: TestSyncDownTarget, soupName: String, expectedNumberOfRecords: Int) {
        val query = QuerySpec.buildSmartQuerySpec(
            String.format(
                "SELECT {%1\$s:%2\$s} from {%1\$s} where {%1\$s:%2\$s} like '%3\$s%%' order by {%1\$s:%2\$s}",
                soupName, Constants.ID, target.getIdPrefix()
            ), Int.MAX_VALUE
        )
        val result = smartStore.query(query, 0)
        Assert.assertEquals("Wrong number of records", expectedNumberOfRecords, result.length())
        for (i in 0 until expectedNumberOfRecords) {
            Assert.assertEquals("Wrong id", target.idForPosition(i), result.getJSONArray(i).getString(0))
        }
    }

    /**
     * Sync down helper
     * @throws JSONException
     * @param mergeMode
     */
    @Throws(JSONException::class)
    private fun trySyncDown(mergeMode: MergeMode): Long {
        return trySyncDown(mergeMode, null as String?)
    }

    /**
     * Sync down helper
     * @throws JSONException
     * @param mergeMode
     */
    @Throws(JSONException::class)
    private fun trySyncDown(mergeMode: MergeMode, syncName: String?): Long {
        val target = SoqlSyncDownTarget("SELECT Id, Name, Description, LastModifiedDate FROM Account WHERE Id IN ${makeInClause(idToFields.keys)}")
        return trySyncDown(mergeMode, target, ACCOUNTS_SOUP, idToFields.size, 1, syncName)
    }

    /**
     * Soql sync down target that pauses for a second at the beginning of the fetch
     */
    class SlowSoqlSyncDownTarget : SoqlSyncDownTarget {

        @Throws(JSONException::class)
        constructor(query: String) : super(query) {
            this.queryType = QueryType.custom
        }

        @Throws(JSONException::class)
        constructor(target: JSONObject) : super(target)

        @Throws(IOException::class, JSONException::class)
        override fun startFetch(syncManager: SyncManager, maxTimeStamp: Long): JSONArray? {
            try {
                Thread.sleep(1000)
            } catch (e: InterruptedException) {

            }
            return super.startFetch(syncManager, maxTimeStamp)
        }
    }
}
