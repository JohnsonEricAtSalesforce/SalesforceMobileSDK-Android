/*
 * Copyright (c) 2017-present, salesforce.com, inc.
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

package com.salesforce.androidsdk.mobilesync.config

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.salesforce.androidsdk.mobilesync.app.MobileSyncSDKManager
import com.salesforce.androidsdk.mobilesync.manager.SyncManagerTestCase
import com.salesforce.androidsdk.mobilesync.target.BatchSyncUpTarget
import com.salesforce.androidsdk.mobilesync.target.BriefcaseSyncDownTarget
import com.salesforce.androidsdk.mobilesync.target.CollectionSyncUpTarget
import com.salesforce.androidsdk.mobilesync.target.LayoutSyncDownTarget
import com.salesforce.androidsdk.mobilesync.target.MetadataSyncDownTarget
import com.salesforce.androidsdk.mobilesync.target.MruSyncDownTarget
import com.salesforce.androidsdk.mobilesync.target.ParentChildrenSyncDownTarget
import com.salesforce.androidsdk.mobilesync.target.ParentChildrenSyncTargetHelper
import com.salesforce.androidsdk.mobilesync.target.ParentChildrenSyncUpTarget
import com.salesforce.androidsdk.mobilesync.target.RefreshSyncDownTarget
import com.salesforce.androidsdk.mobilesync.target.SoqlSyncDownTarget
import com.salesforce.androidsdk.mobilesync.target.SoslSyncDownTarget
import com.salesforce.androidsdk.mobilesync.target.SyncUpTarget
import com.salesforce.androidsdk.mobilesync.util.BriefcaseObjectInfo
import com.salesforce.androidsdk.mobilesync.util.ChildrenInfo
import com.salesforce.androidsdk.mobilesync.util.ParentInfo
import com.salesforce.androidsdk.mobilesync.util.SyncOptions
import com.salesforce.androidsdk.mobilesync.util.SyncState
import com.salesforce.androidsdk.mobilesync.util.SyncState.MergeMode
import org.json.JSONException
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

@Ignore("Production bug: java.lang.VerifyError - SyncsConfig.kt constructors do not call superclass constructor")
@RunWith(AndroidJUnit4::class)
@SmallTest
class SyncsConfigTest : SyncManagerTestCase() {

    @Before
    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()
    }

    @After
    @Throws(Exception::class)
    override fun tearDown() {
        super.tearDown()
    }

    @Test
    @Throws(JSONException::class)
    fun testSetupGlobalSyncsFromDefaultConfig() {
        Assert.assertFalse(globalSyncManager.hasSyncWithName("globalSync1"))
        Assert.assertFalse(globalSyncManager.hasSyncWithName("globalSync2"))

        // Setting up syncs
        MobileSyncSDKManager.getInstance().setupGlobalSyncsFromDefaultConfig()

        // Checking smartstore
        Assert.assertTrue(globalSyncManager.hasSyncWithName("globalSync1"))
        Assert.assertTrue(globalSyncManager.hasSyncWithName("globalSync2"))

        // Checking first sync in details
        val actualSync1 = globalSyncManager.getSyncStatus("globalSync1")!!
        Assert.assertEquals("Wrong soup name", ACCOUNTS_SOUP, actualSync1.soupName)
        checkStatus(
            actualSync1, SyncState.Type.syncDown, actualSync1.id,
            SoqlSyncDownTarget("SELECT Id, Name, LastModifiedDate FROM Account"),
            SyncOptions.optionsForSyncDown(MergeMode.OVERWRITE),
            SyncState.Status.NEW, 0
        )

        // Checking second sync in details
        val actualSync2 = globalSyncManager.getSyncStatus("globalSync2")!!
        Assert.assertEquals("Wrong soup name", ACCOUNTS_SOUP, actualSync2.soupName)
        checkStatus(
            actualSync2, SyncState.Type.syncUp, actualSync2.id,
            CollectionSyncUpTarget(listOf("Name"), null),
            SyncOptions.optionsForSyncUp(listOf("Id", "Name", "LastModifiedDate"), MergeMode.LEAVE_IF_CHANGED),
            SyncState.Status.NEW, 0
        )
    }

    @Test
    fun testSetupUserSyncsFromDefaultConfig() {
        Assert.assertFalse(syncManager.hasSyncWithName("soqlSyncDown"))
        Assert.assertFalse(syncManager.hasSyncWithName("soslSyncDown"))
        Assert.assertFalse(syncManager.hasSyncWithName("mruSyncDown"))
        Assert.assertFalse(syncManager.hasSyncWithName("refreshSyncDown"))
        Assert.assertFalse(syncManager.hasSyncWithName("layoutSyncDown"))
        Assert.assertFalse(syncManager.hasSyncWithName("metadataSyncDown"))
        Assert.assertFalse(syncManager.hasSyncWithName("parentChildrenSyncDown"))
        Assert.assertFalse(syncManager.hasSyncWithName("briefcaseSyncDown"))
        Assert.assertFalse(syncManager.hasSyncWithName("singleRecordSyncUp"))
        Assert.assertFalse(syncManager.hasSyncWithName("batchSyncUp"))
        Assert.assertFalse(syncManager.hasSyncWithName("collectionSyncUp"))
        Assert.assertFalse(syncManager.hasSyncWithName("parentChildrenSyncUp"))

        // Setting up syncs
        MobileSyncSDKManager.getInstance().setupUserSyncsFromDefaultConfig()

        // Checking smartstore
        Assert.assertTrue(syncManager.hasSyncWithName("soqlSyncDown"))
        Assert.assertTrue(syncManager.hasSyncWithName("soslSyncDown"))
        Assert.assertTrue(syncManager.hasSyncWithName("mruSyncDown"))
        Assert.assertTrue(syncManager.hasSyncWithName("refreshSyncDown"))
        Assert.assertTrue(syncManager.hasSyncWithName("layoutSyncDown"))
        Assert.assertTrue(syncManager.hasSyncWithName("metadataSyncDown"))
        Assert.assertTrue(syncManager.hasSyncWithName("parentChildrenSyncDown"))
        Assert.assertTrue(syncManager.hasSyncWithName("briefcaseSyncDown"))
        Assert.assertTrue(syncManager.hasSyncWithName("singleRecordSyncUp"))
        Assert.assertTrue(syncManager.hasSyncWithName("batchSyncUp"))
        Assert.assertTrue(syncManager.hasSyncWithName("collectionSyncUp"))
        Assert.assertTrue(syncManager.hasSyncWithName("parentChildrenSyncUp"))
    }

    @Test
    @Throws(JSONException::class)
    fun testSoqlSyncDownFromConfig() {
        MobileSyncSDKManager.getInstance().setupUserSyncsFromDefaultConfig()

        val sync = syncManager.getSyncStatus("soqlSyncDown")!!
        Assert.assertEquals("Wrong soup name", ACCOUNTS_SOUP, sync.soupName)
        checkStatus(
            sync, SyncState.Type.syncDown, sync.id,
            SoqlSyncDownTarget(null, null, "SELECT Id, Name, LastModifiedDate FROM Account"),
            SyncOptions.optionsForSyncDown(MergeMode.OVERWRITE),
            SyncState.Status.NEW, 0
        )
    }

    @Test
    @Throws(JSONException::class)
    fun testSoqlSyncDownWithBatchSizeFromConfig() {
        MobileSyncSDKManager.getInstance().setupUserSyncsFromDefaultConfig()

        val sync = syncManager.getSyncStatus("soqlSyncDownWithBatchSize")!!
        Assert.assertEquals("Wrong soup name", ACCOUNTS_SOUP, sync.soupName)
        checkStatus(
            sync, SyncState.Type.syncDown, sync.id,
            SoqlSyncDownTarget(null, null, "SELECT Id, Name, LastModifiedDate FROM Account", 200),
            SyncOptions.optionsForSyncDown(MergeMode.OVERWRITE),
            SyncState.Status.NEW, 0
        )
    }

    @Test
    @Throws(JSONException::class)
    fun testSoslSyncDownFromConfig() {
        MobileSyncSDKManager.getInstance().setupUserSyncsFromDefaultConfig()

        val sync = syncManager.getSyncStatus("soslSyncDown")!!
        Assert.assertEquals("Wrong soup name", ACCOUNTS_SOUP, sync.soupName)
        checkStatus(
            sync, SyncState.Type.syncDown, sync.id,
            SoslSyncDownTarget("FIND {Joe} IN NAME FIELDS RETURNING Account"),
            SyncOptions.optionsForSyncDown(MergeMode.LEAVE_IF_CHANGED),
            SyncState.Status.NEW, 0
        )
    }

    @Test
    @Throws(JSONException::class)
    fun testMruSyncDownFromConfig() {
        MobileSyncSDKManager.getInstance().setupUserSyncsFromDefaultConfig()

        val sync = syncManager.getSyncStatus("mruSyncDown")!!
        Assert.assertEquals("Wrong soup name", ACCOUNTS_SOUP, sync.soupName)
        checkStatus(
            sync, SyncState.Type.syncDown, sync.id,
            MruSyncDownTarget(listOf("Name", "Description"), "Account"),
            SyncOptions.optionsForSyncDown(MergeMode.OVERWRITE),
            SyncState.Status.NEW, 0
        )
    }

    @Test
    @Throws(JSONException::class)
    fun testRefreshSyncDownFromConfig() {
        MobileSyncSDKManager.getInstance().setupUserSyncsFromDefaultConfig()

        val sync = syncManager.getSyncStatus("refreshSyncDown")!!
        Assert.assertEquals("Wrong soup name", ACCOUNTS_SOUP, sync.soupName)
        checkStatus(
            sync, SyncState.Type.syncDown, sync.id,
            RefreshSyncDownTarget(listOf("Name", "Description"), "Account", "accounts"),
            SyncOptions.optionsForSyncDown(MergeMode.LEAVE_IF_CHANGED),
            SyncState.Status.NEW, 0
        )
    }

    @Test
    @Throws(JSONException::class)
    fun testLayoutSyncDownFromConfig() {
        MobileSyncSDKManager.getInstance().setupUserSyncsFromDefaultConfig()

        val sync = syncManager.getSyncStatus("layoutSyncDown")!!
        Assert.assertEquals("Wrong soup name", ACCOUNTS_SOUP, sync.soupName)
        checkStatus(
            sync, SyncState.Type.syncDown, sync.id,
            LayoutSyncDownTarget("Account", "Medium", "Compact", "Edit", null),
            SyncOptions.optionsForSyncDown(MergeMode.OVERWRITE),
            SyncState.Status.NEW, 0
        )
    }

    @Test
    @Throws(JSONException::class)
    fun testMetadataSyncDownFromConfig() {
        MobileSyncSDKManager.getInstance().setupUserSyncsFromDefaultConfig()

        val sync = syncManager.getSyncStatus("metadataSyncDown")!!
        Assert.assertEquals("Wrong soup name", ACCOUNTS_SOUP, sync.soupName)
        checkStatus(
            sync, SyncState.Type.syncDown, sync.id,
            MetadataSyncDownTarget("Account"),
            SyncOptions.optionsForSyncDown(MergeMode.LEAVE_IF_CHANGED),
            SyncState.Status.NEW, 0
        )
    }

    @Test
    @Throws(JSONException::class)
    fun testParentChildrenSyncDownFromConfig() {
        MobileSyncSDKManager.getInstance().setupUserSyncsFromDefaultConfig()

        val sync = syncManager.getSyncStatus("parentChildrenSyncDown")!!
        Assert.assertEquals("Wrong soup name", ACCOUNTS_SOUP, sync.soupName)
        checkStatus(
            sync, SyncState.Type.syncDown, sync.id,
            ParentChildrenSyncDownTarget(
                ParentInfo("Account", "accounts", "IdX", "LastModifiedDateX"),
                listOf("IdX", "Name", "Description"),
                "NameX like 'James%'",
                ChildrenInfo("Contact", "Contacts", "contacts", "AccountId", "IdY", "LastModifiedDateY"),
                listOf("LastName", "AccountId"),
                ParentChildrenSyncTargetHelper.RelationshipType.MASTER_DETAIL
            ),
            SyncOptions.optionsForSyncDown(MergeMode.OVERWRITE),
            SyncState.Status.NEW, 0
        )
    }

    @Test
    @Throws(JSONException::class)
    fun testBriefcaseSyncDownFromConfig() {
        MobileSyncSDKManager.getInstance().setupUserSyncsFromDefaultConfig()

        val sync = syncManager.getSyncStatus("briefcaseSyncDown")!!

        checkStatus(
            sync, SyncState.Type.syncDown, sync.id,
            BriefcaseSyncDownTarget(
                listOf(
                    BriefcaseObjectInfo("accounts", "Account", listOf("Name", "Description")),
                    BriefcaseObjectInfo("contacts", "Contact", listOf("FirstName"), "IdX", "LastModifiedDateX")
                )
            ),
            SyncOptions.optionsForSyncDown(MergeMode.OVERWRITE),
            SyncState.Status.NEW, 0
        )
    }

    @Test
    @Throws(JSONException::class)
    fun testSingleRecordSyncUpFromConfig() {
        MobileSyncSDKManager.getInstance().setupUserSyncsFromDefaultConfig()

        val sync = syncManager.getSyncStatus("singleRecordSyncUp")!!
        Assert.assertEquals("Wrong soup name", ACCOUNTS_SOUP, sync.soupName)
        checkStatus(
            sync, SyncState.Type.syncUp, sync.id,
            SyncUpTarget(listOf("Name"), listOf("Description")),
            SyncOptions.optionsForSyncUp(listOf<String>(), MergeMode.LEAVE_IF_CHANGED),
            SyncState.Status.NEW, 0
        )
    }

    @Test
    @Throws(JSONException::class)
    fun testBatchSyncUpFromConfig() {
        MobileSyncSDKManager.getInstance().setupUserSyncsFromDefaultConfig()

        val sync = syncManager.getSyncStatus("batchSyncUp")!!
        Assert.assertEquals("Wrong soup name", ACCOUNTS_SOUP, sync.soupName)
        checkStatus(
            sync, SyncState.Type.syncUp, sync.id,
            BatchSyncUpTarget(null, null, "IdX", "LastModifiedDateX", "ExternalIdX", BatchSyncUpTarget.MAX_SUB_REQUESTS_COMPOSITE_API),
            SyncOptions.optionsForSyncUp(listOf("Name", "Description"), MergeMode.OVERWRITE),
            SyncState.Status.NEW, 0
        )
    }

    @Test
    @Throws(JSONException::class)
    fun testCollectionSyncUpFromConfig() {
        MobileSyncSDKManager.getInstance().setupUserSyncsFromDefaultConfig()

        val sync = syncManager.getSyncStatus("collectionSyncUp")!!
        Assert.assertEquals("Wrong soup name", ACCOUNTS_SOUP, sync.soupName)
        checkStatus(
            sync, SyncState.Type.syncUp, sync.id,
            CollectionSyncUpTarget(null, null, "IdX", "LastModifiedDateX", "ExternalIdX", CollectionSyncUpTarget.MAX_RECORDS_SOBJECT_COLLECTION_API),
            SyncOptions.optionsForSyncUp(listOf("Name", "Description"), MergeMode.OVERWRITE),
            SyncState.Status.NEW, 0
        )
    }

    @Test
    @Throws(JSONException::class)
    fun testParentChildrenSyncUpFromConfig() {
        MobileSyncSDKManager.getInstance().setupUserSyncsFromDefaultConfig()

        val sync = syncManager.getSyncStatus("parentChildrenSyncUp")!!
        Assert.assertEquals("Wrong soup name", ACCOUNTS_SOUP, sync.soupName)
        checkStatus(
            sync, SyncState.Type.syncUp, sync.id,
            ParentChildrenSyncUpTarget(
                ParentInfo("Account", "accounts", "IdX", "LastModifiedDateX", "ExternalIdX"),
                listOf("IdX", "Name", "Description"),
                listOf("Name", "Description"),
                ChildrenInfo("Contact", "Contacts", "contacts", "AccountId", "IdY", "LastModifiedDateY", "ExternalIdY"),
                listOf("LastName", "AccountId"),
                listOf("FirstName", "AccountId"),
                ParentChildrenSyncTargetHelper.RelationshipType.MASTER_DETAIL
            ),
            SyncOptions.optionsForSyncUp(listOf<String>(), MergeMode.LEAVE_IF_CHANGED),
            SyncState.Status.NEW, 0
        )
    }
}
