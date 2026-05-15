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

package com.salesforce.androidsdk.mobilesync.target

import androidx.test.filters.LargeTest

import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Test class for ParentChildrenSyncDownTarget and ParentChildrenSyncUpTarget.
 */
@RunWith(Parameterized::class)
@LargeTest
@Ignore("Production bug: Sync operation fails due to SmartStore NPE in countQuery/deleteByQuery during Kotlin migration")
class ParentChildrenOtherSyncTest : ParentChildrenSyncTestCase() {

    @Parameterized.Parameter(0)
    @JvmField
    var testName: String = ""

    @Parameterized.Parameter(1)
    @JvmField
    var numberAccounts: Int = 0

    @Parameterized.Parameter(2)
    @JvmField
    var numberContactsPerAccount: Int = 0

    @Parameterized.Parameter(3)
    @JvmField
    var localChangeForAccount: Change? = null

    @Parameterized.Parameter(4)
    @JvmField
    var remoteChangeForAccount: Change? = null

    @Parameterized.Parameter(5)
    @JvmField
    var localChangeForContact: Change? = null

    @Parameterized.Parameter(6)
    @JvmField
    var remoteChangeForContact: Change? = null

    @Test
    @Throws(Exception::class)
    fun test() {
        trySyncUpsWithVariousChanges(numberAccounts, numberContactsPerAccount, localChangeForAccount!!, remoteChangeForAccount!!, localChangeForContact!!, remoteChangeForContact!!)
    }

    companion object {
        @Parameterized.Parameters(name = "{0}")
        @JvmStatic
        fun data(): Collection<Array<Any?>> {
            return listOf(
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
    }
}
