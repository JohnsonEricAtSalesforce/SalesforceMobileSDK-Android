package com.salesforce.androidsdk.mobilesync.target

import androidx.test.filters.LargeTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
@LargeTest
class ParentChildrenOtherSyncTest : ParentChildrenSyncTestCase() {
    @Parameterized.Parameter(0) @JvmField var testName: String = ""
    @Parameterized.Parameter(1) @JvmField var numberAccounts: Int = 0
    @Parameterized.Parameter(2) @JvmField var numberContactsPerAccount: Int = 0
    @Parameterized.Parameter(3) @JvmField var localChangeForAccount: Change = Change.NONE
    @Parameterized.Parameter(4) @JvmField var remoteChangeForAccount: Change = Change.NONE
    @Parameterized.Parameter(5) @JvmField var localChangeForContact: Change = Change.NONE
    @Parameterized.Parameter(6) @JvmField var remoteChangeForContact: Change = Change.NONE

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun data(): Collection<Array<Any>> = listOf(
            arrayOf("SyncUpLocallyUpdatedChild", 2, 2, Change.NONE, Change.NONE, Change.UPDATE, Change.NONE),
            arrayOf("SyncUpLocallyUpdatedChildRemotelyUpdatedChild", 2, 2, Change.NONE, Change.NONE, Change.UPDATE, Change.UPDATE),
            arrayOf("SyncUpLocallyUpdatedChildRemotelyDeletedChild", 2, 2, Change.NONE, Change.NONE, Change.UPDATE, Change.DELETE),
            arrayOf("SyncUpLocallyDeletedChild", 2, 2, Change.NONE, Change.NONE, Change.DELETE, Change.NONE),
            arrayOf("SyncUpLocallyDeletedChildRemotelyUpdatedChild", 2, 2, Change.NONE, Change.NONE, Change.DELETE, Change.UPDATE),
            arrayOf("SyncUpLocallyDeletedChildRemotelyDeletedChild", 2, 2, Change.NONE, Change.NONE, Change.DELETE, Change.DELETE),
            arrayOf("SyncUpLocallyUpdatedParent", 2, 2, Change.UPDATE, Change.NONE, Change.NONE, Change.NONE),
            arrayOf("SyncUpLocallyUpdatedParentRemotelyUpdatedParent", 2, 2, Change.UPDATE, Change.UPDATE, Change.NONE, Change.NONE),
            arrayOf("SyncUpLocallyUpdatedParentRemotelyDeletedParent", 2, 2, Change.UPDATE, Change.DELETE, Change.NONE, Change.NONE),
            arrayOf("SyncUpLocallyUpdatedParentUpdatedChild", 2, 2, Change.UPDATE, Change.NONE, Change.UPDATE, Change.NONE),
            arrayOf("SyncUpLocallyUpdatedParentUpdatedChildRemotelyUpdatedChild", 2, 2, Change.UPDATE, Change.NONE, Change.UPDATE, Change.UPDATE),
            arrayOf("SyncUpLocallyUpdatedParentUpdatedChildRemotelyDeletedChild", 2, 2, Change.UPDATE, Change.NONE, Change.UPDATE, Change.DELETE),
            arrayOf("SyncUpLocallyUpdatedParentUpdatedChildRemotelyUpdatedParent", 2, 2, Change.UPDATE, Change.UPDATE, Change.UPDATE, Change.NONE),
            arrayOf("SyncUpLocallyUpdatedParentUpdatedChildRemotelyUpdatedParentUpdatedChild", 2, 2, Change.UPDATE, Change.UPDATE, Change.UPDATE, Change.UPDATE),
            arrayOf("SyncUpLocallyUpdatedParentUpdatedChildRemotelyUpdatedParentDeletedChild", 2, 2, Change.UPDATE, Change.UPDATE, Change.UPDATE, Change.DELETE),
            arrayOf("SyncUpLocallyUpdatedParentUpdatedChildRemotelyDeletedParent", 2, 2, Change.UPDATE, Change.DELETE, Change.UPDATE, Change.NONE),
            arrayOf("SyncUpLocallyUpdatedParentDeletedChild", 2, 2, Change.UPDATE, Change.NONE, Change.DELETE, Change.NONE),
            arrayOf("SyncUpLocallyUpdatedParentDeletedChildRemotelyUpdatedChild", 2, 2, Change.UPDATE, Change.NONE, Change.DELETE, Change.UPDATE),
            arrayOf("SyncUpLocallyUpdatedParentDeletedChildRemotelyDeletedChild", 2, 2, Change.UPDATE, Change.NONE, Change.DELETE, Change.DELETE),
            arrayOf("SyncUpLocallyUpdatedParentDeletedChildRemotelyUpdatedParent", 2, 2, Change.UPDATE, Change.UPDATE, Change.DELETE, Change.NONE),
            arrayOf("SyncUpLocallyUpdatedParentDeletedChildRemotelyUpdatedParentUpdatedChild", 2, 2, Change.UPDATE, Change.UPDATE, Change.DELETE, Change.UPDATE),
            arrayOf("SyncUpLocallyUpdatedParentDeletedChildRemotelyUpdatedParentDeletedChild", 2, 2, Change.UPDATE, Change.UPDATE, Change.DELETE, Change.DELETE),
            arrayOf("SyncUpLocallyUpdatedParentDeletedChildRemotelyDeletedParent", 2, 2, Change.UPDATE, Change.DELETE, Change.DELETE, Change.NONE),
            arrayOf("SyncUpLocallyDeletedParent", 2, 2, Change.DELETE, Change.NONE, Change.NONE, Change.NONE),
            arrayOf("SyncUpLocallyDeletedParentRemotelyUpdatedParent", 2, 2, Change.DELETE, Change.UPDATE, Change.NONE, Change.NONE),
            arrayOf("SyncUpLocallyDeletedParentRemotelyDeletedParent", 2, 2, Change.DELETE, Change.DELETE, Change.NONE, Change.NONE),
            arrayOf("SyncUpLocallyDeletedParentUpdatedChild", 2, 2, Change.DELETE, Change.NONE, Change.UPDATE, Change.NONE),
            arrayOf("SyncUpLocallyDeletedParentUpdatedChildRemotelyUpdatedParent", 2, 2, Change.DELETE, Change.UPDATE, Change.UPDATE, Change.NONE),
            arrayOf("SyncUpLocallyDeletedParentUpdatedChildRemotelyDeletedParent", 2, 2, Change.DELETE, Change.DELETE, Change.UPDATE, Change.NONE),
            arrayOf("SyncUpLocallyDeletedParentDeletedChild", 2, 2, Change.DELETE, Change.NONE, Change.DELETE, Change.NONE),
            arrayOf("SyncUpLocallyDeletedParentDeletedChildRemotelyUpdatedParent", 2, 2, Change.DELETE, Change.UPDATE, Change.DELETE, Change.NONE),
            arrayOf("SyncUpLocallyDeletedParentDeletedChildRemotelyDeletedParent", 2, 2, Change.DELETE, Change.DELETE, Change.DELETE, Change.NONE),
            arrayOf("SyncUpLocallyUpdatedParentNoChildren", 2, 0, Change.UPDATE, Change.NONE, Change.NONE, Change.NONE),
            arrayOf("SyncUpLocallyUpdatedParentRemotelyUpdatedParentNoChildren", 2, 0, Change.UPDATE, Change.UPDATE, Change.NONE, Change.NONE),
            arrayOf("SyncUpLocallyUpdatedParentRemotelyDeletedParentNoChildren", 2, 0, Change.UPDATE, Change.DELETE, Change.NONE, Change.NONE),
            arrayOf("SyncUpLocallyDeletedParentNoChildren", 2, 0, Change.DELETE, Change.NONE, Change.NONE, Change.NONE),
            arrayOf("SyncUpLocallyDeletedParentRemotelyUpdatedParentNoChildren", 2, 0, Change.DELETE, Change.UPDATE, Change.NONE, Change.NONE),
            arrayOf("SyncUpLocallyDeletedParentRemotelyDeletedParentNoChildren", 2, 0, Change.DELETE, Change.DELETE, Change.NONE, Change.NONE)
        )
    }

    @Test fun test() { trySyncUpsWithVariousChanges(numberAccounts, numberContactsPerAccount, localChangeForAccount, remoteChangeForAccount, localChangeForContact, remoteChangeForContact) }
}
