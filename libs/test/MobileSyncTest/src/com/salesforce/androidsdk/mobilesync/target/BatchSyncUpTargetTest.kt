package com.salesforce.androidsdk.mobilesync.target

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.salesforce.androidsdk.mobilesync.util.SyncOptions
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class BatchSyncUpTargetTest : SyncUpTargetTest() {
    override fun trySyncUp(numberChanges: Int, options: SyncOptions, createFieldlist: List<String>?, updateFieldlist: List<String>?, externalIdFieldName: String?) {
        trySyncUp(BatchSyncUpTarget(createFieldlist, updateFieldlist, null, null, externalIdFieldName), numberChanges, options, false)
    }
}
