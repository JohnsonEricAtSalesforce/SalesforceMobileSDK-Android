/*
 * Copyright (c) 2022-present, salesforce.com, inc.
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

import android.util.Pair
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.salesforce.androidsdk.mobilesync.manager.SyncManagerTestCase
import com.salesforce.androidsdk.mobilesync.util.BriefcaseObjectInfo
import com.salesforce.androidsdk.mobilesync.util.Constants
import com.salesforce.androidsdk.mobilesync.util.SyncState.MergeMode
import com.salesforce.androidsdk.rest.RestRequest
import java.util.Arrays
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test class for BriefcaseSyncDownTarget.
 *
 * NB: They will only pass if you have a briefcase setup to return
 * - accounts owned by current user with name starting with BriefcaseTest_
 * - contacts owned by current user with last name starting with BriefcaseTest_
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
@Ignore("Production bug: BriefcaseSyncDownTarget returns 0 results - likely SmartStore countQuery/deleteByQuery NPE during Kotlin migration")
class BriefcaseSyncDownTargetTest : SyncManagerTestCase() {

    @Before
    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()
        createAccountsSoup()
        createContactsSoup()
    }

    @After
    @Throws(Exception::class)
    override fun tearDown() {
        try {
            dropAccountsSoup()
            dropContactsSoup()
        } catch (e: UninitializedPropertyAccessException) {
            // smartStore not initialized - setUp failed before this point
        }
        try {
            cleanRecordsOnServer()
        } catch (e: UninitializedPropertyAccessException) {
            // restClient not initialized - setUp failed before this point
        }
        super.tearDown()
    }

    @Throws(Exception::class)
    private fun cleanRecordsOnServer() {
        deleteRecordsByCriteriaFromServer(
            Constants.ACCOUNT,
            " Name like 'BriefcaseTest_%' AND CreatedById = '" + restClient.getClientInfo().userId + "'"
        )
        deleteRecordsByCriteriaFromServer(
            Constants.CONTACT,
            " LastName like 'BriefcaseTest_%' AND CreatedById = '" + restClient.getClientInfo().userId + "'"
        )
    }

    // Create accounts on server
    // Run startFetch method of BriefcaseSyncDownTarget that is only interested in accounts
    // Make sure we get the created accounts back
    @Test
    @Throws(Exception::class)
    fun testStartFetchNoMaxTimeStamp() {
        val numberAccounts = 12
        val accounts = createRecordsOnServer(numberAccounts, Constants.ACCOUNT)
        Assert.assertEquals("Wrong number of accounts created", numberAccounts, accounts.size)

        // Builds briefcase sync down target to fetch the accounts and performs sync.
        val target = BriefcaseSyncDownTarget(
            Arrays.asList(
                BriefcaseObjectInfo(
                    ACCOUNTS_SOUP,
                    Constants.ACCOUNT,
                    Arrays.asList(Constants.NAME, Constants.DESCRIPTION)
                )
            )
        )

        val records = target.startFetch(syncManager, 0)
        Assert.assertEquals(numberAccounts, records.length())
        for (i in 0 until records.length()) {
            val record = records.getJSONObject(i)
            val id = record.getString(Constants.ID)
            val name = record.getString(Constants.NAME)
            Assert.assertEquals(accounts[id], name)
        }
    }

    // Create accounts on server
    // Run startFetch method of BriefcaseSyncDownTarget that is only interested in accounts
    // Query server to figure out last modified date
    // Create more accounts on server
    // Run startFetch method of BriefcaseSyncDownTarget using maxTimeStamp to only get second group of accounts
    // Make sure we get the second group of accounts only
    @Test
    @Throws(Exception::class)
    fun testStartFetchWithMaxTimeStamp() {
        val numberAccounts = 6

        // Creating some accounts
        val oldAccounts = createRecordsOnServer(numberAccounts, Constants.ACCOUNT)
        Assert.assertEquals("Wrong number of accounts created", numberAccounts, oldAccounts.size)

        // Figure out last modified date
        val request = RestRequest.getRequestForQuery(
            apiVersion,
            "SELECT Max(SystemModStamp) FROM Account WHERE Name like 'BriefcaseTest_%'"
        )
        val responseJson = restClient.sendSync(request)!!.asJSONObject()
        val maxTimeStamp = Constants.TIMESTAMP_FORMAT.parse(
            responseJson.getJSONArray(Constants.RECORDS).getJSONObject(0).getString("expr0")
        ).time

        // Waiting a bit
        Thread.sleep(1000) // time stamp precision in seconds

        // Creating more accounts
        val newAccounts = createRecordsOnServer(numberAccounts, Constants.ACCOUNT)
        Assert.assertEquals("Wrong number of accounts created", numberAccounts, newAccounts.size)

        // Make sure old and new accounts exist on server
        val accountIds = getIdsOnServer(
            Constants.ACCOUNT,
            " Name like 'BriefcaseTest_%' AND CreatedById = '" + restClient.getClientInfo().userId + "'"
        )
        Assert.assertEquals(numberAccounts * 2, accountIds.size)
        Assert.assertTrue(accountIds.containsAll(oldAccounts.keys))
        Assert.assertTrue(accountIds.containsAll(newAccounts.keys))

        // Builds briefcase sync down target to fetch the accounts and performs sync.
        val target = BriefcaseSyncDownTarget(
            Arrays.asList(
                BriefcaseObjectInfo(
                    ACCOUNTS_SOUP,
                    Constants.ACCOUNT,
                    Arrays.asList(Constants.NAME, Constants.DESCRIPTION)
                )
            )
        )

        val records = target.startFetch(syncManager, maxTimeStamp)
        Assert.assertEquals(numberAccounts, records.length())
        for (i in 0 until records.length()) {
            val record = records.getJSONObject(i)
            val id = record.getString(Constants.ID)
            val name = record.getString(Constants.NAME)
            Assert.assertEquals(newAccounts[id], name)
        }
    }

    // Create accounts on server
    // Run a sync with a BriefcaseSyncDownTarget that is only interested in accounts
    // Make sure we get the created accounts in the database
    @Test
    @Throws(Exception::class)
    fun testSyncDownFetchingOneObjectType() {
        trySyncDownFetchingOneObjectType(12, 500, 1)
    }

    // Create accounts on server
    // Run a sync with a BriefcaseSyncDownTarget with 2 of 2
    // Make sure we get the created accounts in the database
    // And that it took the multiple call to continueFetch
    @Test
    @Throws(Exception::class)
    fun testSyncDownFetchingOneObjectTypeWithMultipleRetrieveCalls() {
        trySyncDownFetchingOneObjectType(12, 2, 6)
    }

    // Create accounts on server
    // Run a sync with a BriefcaseSyncDownTarget that is only interested in accounts
    // Make sure we get the created accounts in the database
    // Delete some accounts from server
    // Create some accounts locally
    // Run a cleanGhosts
    // Make sure the remotely deleted accounts are gone from the database
    // but other synced accounts and locally created accounts are still there
    //
    @Test
    @Throws(Exception::class)
    fun testCleanGhostsOneObjectType() {
        val numberAccounts = 4
        val accounts = createRecordsOnServer(numberAccounts, Constants.ACCOUNT)
        Assert.assertEquals("Wrong number of accounts created", numberAccounts, accounts.size)
        val accountIds = accounts.keys.toTypedArray()

        // Builds briefcase sync down target to fetch the accounts
        val target = BriefcaseSyncDownTarget(
            Arrays.asList(
                BriefcaseObjectInfo(
                    ACCOUNTS_SOUP,
                    Constants.ACCOUNT,
                    Arrays.asList(Constants.NAME, Constants.DESCRIPTION)
                )
            )
        )

        // Run sync
        val syncId = trySyncDown(MergeMode.LEAVE_IF_CHANGED, target, ACCOUNTS_SOUP, accounts.size, 1, null)

        // Check database
        checkDbExist(ACCOUNTS_SOUP, accountIds, Constants.ID)

        // Deleting some accounts
        deleteRecordsByIdOnServer(Arrays.asList(accountIds[0], accountIds[1]), Constants.ACCOUNT)

        // Create some accounts locally
        val localAccounts = createAccountsLocally(arrayOf("local-1", "local-2"))

        // Clean ghosts
        tryCleanResyncGhosts(syncId)

        // Check database
        // Ghosts should be gone
        checkDbDeleted(ACCOUNTS_SOUP, arrayOf(accountIds[0], accountIds[1]), Constants.ID)
        // Other synced records should still be there
        checkDbExist(ACCOUNTS_SOUP, arrayOf(accountIds[2], accountIds[3]), Constants.ID)
        // Locally created records should still be there
        checkDbExist(
            ACCOUNTS_SOUP, arrayOf(
                localAccounts[0].getString(Constants.ID), localAccounts[1].getString(Constants.ID)
            ), Constants.ID
        )
    }

    // Create accounts and contacts on server
    // Run a sync with a BriefcaseSyncDownTarget that is interested in accounts and contacts
    // Make sure we get the created accounts and contacts in the database
    @Test
    @Throws(Exception::class)
    fun testSyncDownFetchingTwoObjectTypes() {
        trySyncDownFetchingTwoObjectTypes(12, 12, 500, 1)
    }

    // Create accounts and contacts on server
    // Run a sync with a BriefcaseSyncDownTarget with countIdsPerRetrieve of 2
    // Make sure we get the created accounts and contacts in the database
    // And that it took the multiple call to continueFetch
    @Test
    @Throws(Exception::class)
    fun testSyncDownFetchingTwoObjectTypesWithMultipleRetrieveCalls() {
        trySyncDownFetchingTwoObjectTypes(12, 12, 3, 8)
    }

    // Create accounts and contacts on server
    // Run a sync with a BriefcaseSyncDownTarget that is interested in accounts and contacts
    // Make sure we get the created accounts and contacts in the database
    // Delete some accounts and contacts from server
    // Create some accounts and contacts locally
    // Run a cleanGhosts
    // Make sure the remotely deleted accounts and contacts are gone from the database
    // but other synced and locally created accounts and contacts are still there
    //
    @Test
    @Throws(Exception::class)
    fun testCleanGhostsTwoObjectTypes() {
        val result = trySyncDownFetchingTwoObjectTypes(4, 4, 500, 1)
        val accountIds = result.first.first
        val contactIds = result.first.second
        val syncId = result.second

        // Deleting some accounts
        deleteRecordsByIdOnServer(Arrays.asList(accountIds[0], accountIds[1]), Constants.ACCOUNT)
        deleteRecordsByIdOnServer(Arrays.asList(contactIds[2], contactIds[3]), Constants.CONTACT)

        // Create some accounts and contacts locally
        val localAccounts = createAccountsLocally(arrayOf("local-1", "local-2"))
        val localAccountId = localAccounts[0].getString(Constants.ID)
        val localContacts = createContactsForAccountsLocally(
            2,
            localAccountId
        )[localAccountId]

        // Clean ghosts
        tryCleanResyncGhosts(syncId)

        // Check database
        // Ghosts should be gone
        checkDbDeleted(ACCOUNTS_SOUP, arrayOf(accountIds[0], accountIds[1]), Constants.ID)
        checkDbDeleted(CONTACTS_SOUP, arrayOf(contactIds[2], accountIds[3]), Constants.ID)
        // Other synced records should still be there
        checkDbExist(ACCOUNTS_SOUP, arrayOf(accountIds[2], accountIds[3]), Constants.ID)
        checkDbExist(CONTACTS_SOUP, arrayOf(contactIds[0], contactIds[1]), Constants.ID)
        // Locally created records should still be there
        checkDbExist(
            ACCOUNTS_SOUP, arrayOf(
                localAccounts[0].getString(Constants.ID), localAccounts[1].getString(Constants.ID)
            ), Constants.ID
        )
        checkDbExist(
            CONTACTS_SOUP, arrayOf(
                localContacts!![0].getString(Constants.ID), localContacts[1].getString(Constants.ID)
            ), Constants.ID
        )
    }

    // Create accounts and contacts on server
    // Run a sync with a BriefcaseSyncDownTarget that is interested in accounts and contacts
    // Make sure we get the created accounts and contacts in the database
    // Delete some of them locally
    // Make sure get ids to skip return all the locally deleted records across both soups
    @Test
    @Throws(Exception::class)
    fun testIdsToSkip() {
        val result = trySyncDownFetchingTwoObjectTypes(4, 4, 500, 1)
        val accountIds = result.first.first
        val contactIds = result.first.second
        val syncId = result.second
        val target = syncManager.getSyncStatus(syncId)!!.target as BriefcaseSyncDownTarget

        // Initially there should be no dirty records
        var idsToSkip = target.getIdsToSkip(syncManager, "")
        Assert.assertTrue(idsToSkip.isEmpty())

        // Marking some accounts and contacts as locally deleted
        deleteRecordsLocally(ACCOUNTS_SOUP, accountIds[0], accountIds[3])
        deleteRecordsLocally(CONTACTS_SOUP, contactIds[1], contactIds[2])

        // Making sure they are returned by target.getDirtyRecordIds()
        idsToSkip = target.getIdsToSkip(syncManager, "")
        Assert.assertEquals(4, idsToSkip.size)
        Assert.assertTrue(idsToSkip.contains(accountIds[0]))
        Assert.assertTrue(idsToSkip.contains(accountIds[3]))
        Assert.assertTrue(idsToSkip.contains(contactIds[1]))
        Assert.assertTrue(idsToSkip.contains(contactIds[2]))
    }

    override fun createRecordName(objectType: String): String {
        return String.format(Locale.US, "BriefcaseTest_%s_%d", objectType, System.nanoTime())
    }

    @Throws(Exception::class)
    protected fun trySyncDownFetchingOneObjectType(
        numberAccounts: Int, countIdsPerRetrieve: Int, expectedNumberFetches: Int
    ) {
        val accounts = createRecordsOnServer(numberAccounts, Constants.ACCOUNT)
        Assert.assertEquals("Wrong number of accounts created", numberAccounts, accounts.size)
        val accountIds = accounts.keys.toTypedArray()

        // Builds briefcase sync down target to fetch the accounts
        val target = BriefcaseSyncDownTarget(
            Arrays.asList(
                BriefcaseObjectInfo(
                    ACCOUNTS_SOUP,
                    Constants.ACCOUNT,
                    Arrays.asList(Constants.NAME, Constants.DESCRIPTION)
                )
            ),
            countIdsPerRetrieve
        )

        // Run sync
        trySyncDown(MergeMode.LEAVE_IF_CHANGED, target, ACCOUNTS_SOUP, accounts.size, expectedNumberFetches, null)

        // Check database
        checkDbExist(ACCOUNTS_SOUP, accountIds, Constants.ID)
    }

    /**
     * Create accounts and contacts on server
     * Run briefcase sync down
     * Check database
     *
     * @param numberAccounts
     * @param numberContacts
     * @param countIdsPerRetrieve
     * @param expectedNumberFetches
     * @return pair made of aa pair with accountIds and contactIds created and sync id
     * @throws Exception
     */
    @Throws(Exception::class)
    protected fun trySyncDownFetchingTwoObjectTypes(
        numberAccounts: Int, numberContacts: Int, countIdsPerRetrieve: Int, expectedNumberFetches: Int
    ): Pair<Pair<Array<String>, Array<String>>, Long> {
        val accounts = createRecordsOnServer(numberAccounts, Constants.ACCOUNT)
        Assert.assertEquals("Wrong number of accounts created", numberAccounts, accounts.size)
        val accountIds = accounts.keys.toTypedArray()

        val contacts = createRecordsOnServer(numberAccounts, Constants.CONTACT)
        Assert.assertEquals("Wrong number of contacts created", numberContacts, contacts.size)
        val contactIds = contacts.keys.toTypedArray()

        // Builds briefcase sync down target to fetch the accounts and contacts
        val target = BriefcaseSyncDownTarget(
            Arrays.asList(
                BriefcaseObjectInfo(
                    ACCOUNTS_SOUP,
                    Constants.ACCOUNT,
                    Arrays.asList(Constants.NAME, Constants.DESCRIPTION)
                ),
                BriefcaseObjectInfo(
                    CONTACTS_SOUP,
                    Constants.CONTACT,
                    Arrays.asList(Constants.LAST_NAME)
                )
            ),
            countIdsPerRetrieve
        )

        // Run sync
        val syncId = trySyncDown(
            MergeMode.LEAVE_IF_CHANGED, target, ACCOUNTS_SOUP, accounts.size + contacts.size, expectedNumberFetches, null
        )

        // Check database
        checkDbExist(ACCOUNTS_SOUP, accountIds, Constants.ID)
        checkDbExist(CONTACTS_SOUP, contactIds, Constants.ID)

        // Returning accountIds, contactIds and syncId for tests that need to do more
        return Pair(Pair(accountIds, contactIds), syncId)
    }
}
