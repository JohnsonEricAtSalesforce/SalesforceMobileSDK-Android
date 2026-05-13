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

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.salesforce.androidsdk.mobilesync.target.ParentChildrenSyncTargetHelper.RelationshipType
import com.salesforce.androidsdk.mobilesync.util.ChildrenInfo
import com.salesforce.androidsdk.mobilesync.util.Constants
import com.salesforce.androidsdk.mobilesync.util.ParentInfo
import com.salesforce.androidsdk.mobilesync.util.SyncOptions
import com.salesforce.androidsdk.mobilesync.util.SyncState
import com.salesforce.androidsdk.mobilesync.util.SyncUpdateCallbackQueue
import com.salesforce.androidsdk.smartstore.store.SmartStore
import com.salesforce.androidsdk.util.JSONObjectHelper
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import java.util.ArrayList
import java.util.Arrays
import java.util.Collections
import java.util.Date
import java.util.HashMap
import java.util.HashSet

/**
 * Test class for ParentChildrenSyncDownTarget and ParentChildrenSyncUpTarget.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class ParentChildrenSyncTest : ParentChildrenSyncTestCase() {

    /**
     * Test getQuery for ParentChildrenSyncDownTarget
     */
    @Test
    fun testGetQuery() {
        var target = ParentChildrenSyncDownTarget(
            ParentInfo("Parent", "parentsSoup", "ParentId", "ParentModifiedDate"),
            Arrays.asList("ParentName", "Title"),
            "School = 'MIT'",
            ChildrenInfo("Child", "Children", "childrenSoup", "parentId", "ChildId", "ChildLastModifiedDate"),
            Arrays.asList("ChildName", "School"),
            RelationshipType.LOOKUP
        )
        Assert.assertEquals("select ParentName, Title, ParentId, ParentModifiedDate, (select ChildName, School, ChildId, ChildLastModifiedDate from Children) from Parent where School = 'MIT' order by ParentModifiedDate", target.getQuery())

        // With default id and modification date fields
        target = ParentChildrenSyncDownTarget(
            ParentInfo("Parent", "parentsSoup"),
            Arrays.asList("ParentName", "Title"),
            "School = 'MIT'",
            ChildrenInfo("Child", "Children", "childrenSoup", "parentId"),
            Arrays.asList("ChildName", "School"),
            RelationshipType.LOOKUP
        )
        Assert.assertEquals("select ParentName, Title, Id, LastModifiedDate, (select ChildName, School, Id, LastModifiedDate from Children) from Parent where School = 'MIT' order by LastModifiedDate", target.getQuery())
    }

    /**
     * Test query for reSync by calling getQuery with maxTimeStamp for ParentChildrenSyncDownTarget
     */
    @Test
    fun testGetQueryWithMaxTimeStamp() {
        val date = Date()
        val dateStr = Constants.TIMESTAMP_FORMAT.format(date)
        val dateLong = date.time
        var target = ParentChildrenSyncDownTarget(
            ParentInfo("Parent", "parentsSoup", "ParentId", "ParentModifiedDate"),
            Arrays.asList("ParentName", "Title"),
            "School = 'MIT'",
            ChildrenInfo("Child", "Children", "childrenSoup", "parentId", "ChildId", "ChildLastModifiedDate"),
            Arrays.asList("ChildName", "School"),
            RelationshipType.LOOKUP
        )
        Assert.assertEquals("select ParentName, Title, ParentId, ParentModifiedDate, (select ChildName, School, ChildId, ChildLastModifiedDate from Children where ChildLastModifiedDate > $dateStr) from Parent where ParentModifiedDate > $dateStr and School = 'MIT' order by ParentModifiedDate", target.getQuery(dateLong))

        // With default id and modification date fields
        target = ParentChildrenSyncDownTarget(
            ParentInfo("Parent", "parentsSoup"),
            Arrays.asList("ParentName", "Title"),
            "School = 'MIT'",
            ChildrenInfo("Child", "Children", "childrenSoup", "parentId"),
            Arrays.asList("ChildName", "School"),
            RelationshipType.LOOKUP
        )
        Assert.assertEquals("select ParentName, Title, Id, LastModifiedDate, (select ChildName, School, Id, LastModifiedDate from Children where LastModifiedDate > $dateStr) from Parent where LastModifiedDate > $dateStr and School = 'MIT' order by LastModifiedDate", target.getQuery(dateLong))
    }

    /**
     * Test getSoqlForRemoteIds for ParentChildrenSyncDownTarget
     */
    @Test
    fun testGetSoqlForRemoteIds() {
        var target = ParentChildrenSyncDownTarget(
            ParentInfo("Parent", "parentsSoup", "ParentId", "ParentModifiedDate"),
            Arrays.asList("ParentName", "Title"),
            "School = 'MIT'",
            ChildrenInfo("Child", "Children", "childrenSoup", "ChildParentId", "ChildId", "ChildLastModifiedDate"),
            Arrays.asList("ChildName", "School"),
            RelationshipType.LOOKUP
        )
        Assert.assertEquals("select ParentId from Parent where School = 'MIT'", target.soqlForRemoteIds)

        // With default id and modification date fields
        target = ParentChildrenSyncDownTarget(
            ParentInfo("Parent", "parentsSoup"),
            Arrays.asList("ParentName", "Title"),
            "School = 'MIT'",
            ChildrenInfo("Child", "Children", "childrenSoup", "ChildParentId"),
            Arrays.asList("ChildName", "School"),
            RelationshipType.LOOKUP
        )
        Assert.assertEquals("select Id from Parent where School = 'MIT'", target.soqlForRemoteIds)
    }

    /**
     * Test getDirtyRecordIdsSql for ParentChildrenSyncDownTarget
     */
    @Test
    fun testGetDirtyRecordIdsSql() {
        val target = ParentChildrenSyncDownTarget(
            ParentInfo("Parent", "parentsSoup", "ParentId", "ParentModifiedDate"),
            Arrays.asList("ParentName", "Title"),
            "School = 'MIT'",
            ChildrenInfo("Child", "Children", "childrenSoup", "ChildParentId", "ChildId", "ChildLastModifiedDate"),
            Arrays.asList("ChildName", "School"),
            RelationshipType.LOOKUP
        )
        Assert.assertEquals(
            "SELECT DISTINCT {parentsSoup:IdForQuery} FROM {parentsSoup} WHERE {parentsSoup:__local__} = 'true' OR EXISTS (SELECT {childrenSoup:ChildId} FROM {childrenSoup} WHERE {childrenSoup:ChildParentId} = {parentsSoup:ParentId} AND {childrenSoup:__local__} = 'true')",
            target.getDirtyRecordIdsSql("parentsSoup", "IdForQuery")
        )
    }

    /**
     * Test getNonDirtyRecordIdsSql for ParentChildrenSyncDownTarget
     */
    @Test
    fun testGetNonDirtyRecordIdsSql() {
        val parentInfo = ParentInfo("Parent", "parentsSoup", "ParentId", "ParentModifiedDate")
        val childrenInfo = ChildrenInfo("Child", "Children", "childrenSoup", "ChildParentId", "ChildId", "ChildLastModifiedDate")
        val target = ParentChildrenSyncDownTarget(
            parentInfo,
            Arrays.asList("ParentName", "Title"),
            "School = 'MIT'",
            childrenInfo,
            Arrays.asList("ChildName", "School"),
            RelationshipType.LOOKUP
        )
        Assert.assertEquals(
            "SELECT DISTINCT {parentsSoup:IdForQuery} FROM {parentsSoup} WHERE {parentsSoup:__local__} = 'false' AND {parentsSoup:__sync_id__} = 123 AND NOT EXISTS (SELECT {childrenSoup:ChildId} FROM {childrenSoup} WHERE {childrenSoup:ChildParentId} = {parentsSoup:ParentId} AND {childrenSoup:__local__} = 'true')",
            ParentChildrenSyncTargetHelper.getNonDirtyRecordIdsSql(parentInfo, childrenInfo, "IdForQuery", "AND {parentsSoup:__sync_id__} = 123")
        )
    }

    /**
     * Test getDirtyRecordIds and getNonDirtyRecordIds for ParentChildrenSyncDownTarget when parent and/or all and/or some children are dirty
     */
    @Test
    @Throws(JSONException::class)
    fun testGetDirtyAndNonDirtyRecordIds() {
        val accountNames = arrayOf(
            createRecordName(Constants.ACCOUNT),
            createRecordName(Constants.ACCOUNT),
            createRecordName(Constants.ACCOUNT),
            createRecordName(Constants.ACCOUNT),
            createRecordName(Constants.ACCOUNT),
            createRecordName(Constants.ACCOUNT)
        )
        val mapAccountToContacts = createAccountsAndContactsLocally(accountNames, 3)
        val accounts = mapAccountToContacts.keys.toTypedArray()

        // All Accounts should be returned
        tryGetDirtyRecordIds(accounts)

        // No accounts should be returned
        tryGetNonDirtyRecordIds(arrayOf())

        // Cleaning up:
        // accounts[0]: dirty account and dirty contacts
        // accounts[1]: clean account and dirty contacts
        // accounts[2]: dirty account and clean contacts
        // accounts[3]: clean account and clean contacts
        // accounts[4]: dirty account and some dirty contacts
        // accounts[5]: clean account and some dirty contacts
        cleanRecord(ACCOUNTS_SOUP, accounts[1])
        cleanRecords(CONTACTS_SOUP, mapAccountToContacts[accounts[2]]!!)
        cleanRecord(ACCOUNTS_SOUP, accounts[3])
        cleanRecords(CONTACTS_SOUP, mapAccountToContacts[accounts[3]]!!)
        cleanRecord(CONTACTS_SOUP, mapAccountToContacts[accounts[4]]!![0])
        cleanRecord(ACCOUNTS_SOUP, accounts[5])
        cleanRecord(CONTACTS_SOUP, mapAccountToContacts[accounts[5]]!![0])

        // Only clean account with clean contacts should not be returned
        tryGetDirtyRecordIds(arrayOf(accounts[0], accounts[1], accounts[2], accounts[4], accounts[5]))

        // Only clean account with clean contacts should be returned
        tryGetNonDirtyRecordIds(arrayOf(accounts[3]))
    }

    /**
     * Test saveRecordsToLocalStore
     */
    @Test
    @Throws(JSONException::class)
    fun testSaveRecordsToLocalStore() {
        // Putting together a JSONArray of accounts with contacts
        // looking like what we would get back from startFetch/continueFetch
        // - not having local fields
        // - not have _soupEntryId field
        val numberAccounts = 4
        val numberContactsPerAccount = 3
        val syncId = 123L
        val accountAttributes = JSONObject()
        accountAttributes.put(TYPE, Constants.ACCOUNT)
        val contactAttributes = JSONObject()
        contactAttributes.put(TYPE, Constants.CONTACT)
        val accounts = arrayOfNulls<JSONObject>(numberAccounts)
        val mapAccountContacts: MutableMap<JSONObject, Array<JSONObject>> = HashMap()
        for (i in 0 until numberAccounts) {
            val account = JSONObject()
            account.put(Constants.ID, SyncTarget.createLocalId())
            account.put(Constants.ATTRIBUTES, accountAttributes)
            val contacts = arrayOfNulls<JSONObject>(numberContactsPerAccount)
            for (j in 0 until numberContactsPerAccount) {
                val contact = JSONObject()
                contact.put(Constants.ID, SyncTarget.createLocalId())
                contact.put(Constants.ATTRIBUTES, contactAttributes)
                contact.put(ACCOUNT_ID, account.get(Constants.ID))
                contacts[j] = contact
            }
            mapAccountContacts[account] = contacts.requireNoNulls()
            accounts[i] = account
        }
        val records = JSONArray()
        for (account in accounts.requireNoNulls()) {
            val record = JSONObject(account.toString())
            val contacts = JSONArray()
            for (contact in mapAccountContacts[account]!!) {
                contacts.put(contact)
            }
            record.put("Contacts", contacts)
            records.put(record)
        }

        // Now calling saveRecordsToLocalStore
        val target = getAccountContactsSyncDownTarget()
        target.saveRecordsToLocalStore(syncManager, ACCOUNTS_SOUP, records, syncId)

        // Checking accounts and contacts soup
        // Making sure local fields are populated
        // Making sure accountId and accountLocalId fields are populated on contacts
        val accountsFromDb = queryWithInClause(ACCOUNTS_SOUP, Constants.ID, JSONObjectHelper.pluck<String>(accounts.requireNoNulls(), Constants.ID).toTypedArray(), null)
        Assert.assertEquals("Wrong number of accounts in db", accounts.size, accountsFromDb.size)
        for (i in accountsFromDb.indices) {
            val account = accounts[i]!!
            val accountFromDb = accountsFromDb[i]
            Assert.assertEquals(account.getString(Constants.ID), accountFromDb.getString(Constants.ID))
            Assert.assertEquals(Constants.ACCOUNT, accountFromDb.getJSONObject(Constants.ATTRIBUTES).getString(TYPE))
            Assert.assertEquals(false, accountFromDb.getBoolean(SyncTarget.LOCAL))
            Assert.assertEquals(false, accountFromDb.getBoolean(SyncTarget.LOCALLY_CREATED))
            Assert.assertEquals(false, accountFromDb.getBoolean(SyncTarget.LOCALLY_DELETED))
            Assert.assertEquals(false, accountFromDb.getBoolean(SyncTarget.LOCALLY_UPDATED))
            Assert.assertEquals(syncId, accountFromDb.getLong(SyncTarget.SYNC_ID))
            val contactsFromDb = queryWithInClause(CONTACTS_SOUP, ACCOUNT_ID, arrayOf(account.getString(Constants.ID)), SmartStore.SOUP_ENTRY_ID)
            val contacts = mapAccountContacts[account]!!
            Assert.assertEquals("Wrong number of contacts in db", contacts.size, contactsFromDb.size)
            for (j in contactsFromDb.indices) {
                val contact = contacts[j]
                val contactFromDb = contactsFromDb[j]
                Assert.assertEquals(contact.getString(Constants.ID), contactFromDb.getString(Constants.ID))
                Assert.assertEquals(Constants.CONTACT, contactFromDb.getJSONObject(Constants.ATTRIBUTES).getString(TYPE))
                Assert.assertEquals(false, contactFromDb.getBoolean(SyncTarget.LOCAL))
                Assert.assertEquals(false, contactFromDb.getBoolean(SyncTarget.LOCALLY_CREATED))
                Assert.assertEquals(false, contactFromDb.getBoolean(SyncTarget.LOCALLY_DELETED))
                Assert.assertEquals(false, contactFromDb.getBoolean(SyncTarget.LOCALLY_UPDATED))
                Assert.assertEquals(syncId, contactFromDb.getLong(SyncTarget.SYNC_ID))
                Assert.assertEquals(accountFromDb.getString(Constants.ID), contactFromDb.getString(ACCOUNT_ID))
            }
        }
    }

    /**
     * Test getLatestModificationTimeStamp
     */
    @Test
    @Throws(JSONException::class)
    fun testGetLatestModificationTimeStamp() {
        // Putting together a JSONArray of accounts with contacts
        // looking like what we would get back from startFetch/continueFetch
        // with different fields for last modified time
        val numberAccounts = 4
        val numberContactsPerAccount = 3
        val timeStamps = longArrayOf(
            100000000,
            200000000,
            300000000,
            400000000
        )
        val timeStampStrs = arrayOf(
            Constants.TIMESTAMP_FORMAT.format(Date(timeStamps[0])),
            Constants.TIMESTAMP_FORMAT.format(Date(timeStamps[1])),
            Constants.TIMESTAMP_FORMAT.format(Date(timeStamps[2])),
            Constants.TIMESTAMP_FORMAT.format(Date(timeStamps[3]))
        )
        val accountAttributes = JSONObject()
        accountAttributes.put(TYPE, Constants.ACCOUNT)
        val contactAttributes = JSONObject()
        contactAttributes.put(TYPE, Constants.CONTACT)
        val accounts = arrayOfNulls<JSONObject>(numberAccounts)
        val mapAccountContacts: MutableMap<JSONObject, Array<JSONObject>> = HashMap()
        for (i in 0 until numberAccounts) {
            val account = JSONObject()
            account.put(Constants.ID, SyncTarget.createLocalId())
            account.put("AccountTimeStamp1", timeStampStrs[i % timeStampStrs.size])
            account.put("AccountTimeStamp2", timeStampStrs[0])
            val contacts = arrayOfNulls<JSONObject>(numberContactsPerAccount)
            for (j in 0 until numberContactsPerAccount) {
                val contact = JSONObject()
                contact.put(Constants.ID, SyncTarget.createLocalId())
                contact.put(ACCOUNT_ID, account.get(Constants.ID))
                contact.put("ContactTimeStamp1", timeStampStrs[1])
                contact.put("ContactTimeStamp2", timeStampStrs[j % timeStampStrs.size])
                contacts[j] = contact
            }
            mapAccountContacts[account] = contacts.requireNoNulls()
            accounts[i] = account
        }
        val records = JSONArray()
        for (account in accounts.requireNoNulls()) {
            val record = JSONObject(account.toString())
            val contacts = JSONArray()
            for (contact in mapAccountContacts[account]!!) {
                contacts.put(contact)
            }
            record.put("Contacts", contacts)
            records.put(record)
        }

        // Maximums

        // Get max time stamps based on fields AccountTimeStamp1 / ContactTimeStamp1
        Assert.assertEquals(
            timeStamps[3],
            getAccountContactsSyncDownTarget("AccountTimeStamp1", "ContactTimeStamp1", "").getLatestModificationTimeStamp(records)
        )

        // Get max time stamps based on fields AccountTimeStamp1 / ContactTimeStamp2
        Assert.assertEquals(
            timeStamps[3],
            getAccountContactsSyncDownTarget("AccountTimeStamp1", "ContactTimeStamp2", "").getLatestModificationTimeStamp(records)
        )

        // Get max time stamps based on fields AccountTimeStamp2 / ContactTimeStamp1
        Assert.assertEquals(
            timeStamps[1],
            getAccountContactsSyncDownTarget("AccountTimeStamp2", "ContactTimeStamp1", "").getLatestModificationTimeStamp(records)
        )

        // Get max time stamps based on fields AccountTimeStamp2 / ContactTimeStamp2
        Assert.assertEquals(
            timeStamps[2],
            getAccountContactsSyncDownTarget("AccountTimeStamp2", "ContactTimeStamp2", "").getLatestModificationTimeStamp(records)
        )
    }

    /**
     * Sync down the test accounts and contacts, check smart store, check status during sync
     */
    @Test
    @Throws(Exception::class)
    fun testSyncDown() {
        val numberAccounts = 4
        val numberContactsPerAccount = 3

        // Creating test accounts and contacts on server
        createAccountsAndContactsOnServer(numberAccounts, numberContactsPerAccount)

        // Sync down
        val localAccountIdToFields = accountIdToFields!!
        val target = getAccountContactsSyncDownTarget(
            String.format("%s IN %s", Constants.ID, makeInClause(localAccountIdToFields.keys))
        )
        trySyncDown(SyncState.MergeMode.OVERWRITE, target, ACCOUNTS_SOUP, numberAccounts, 1)

        // Check that db was correctly populated
        checkDb(localAccountIdToFields, ACCOUNTS_SOUP)
        for (accountId in localAccountIdToFields.keys) {
            checkDb(accountIdContactIdToFields!![accountId]!!, CONTACTS_SOUP)
        }
    }

    /**
     * Sync down the test accounts that do not have children contacts, check smart store, check status during sync
     */
    @Test
    @Throws(Exception::class)
    fun testSyncDownNoChildren() {
        // Creating test accounts on server
        val numberAccounts = 4
        accountIdToFields = createRecordsOnServerReturnFields(numberAccounts, Constants.ACCOUNT, null) as MutableMap<String, Map<String, Any>>

        // Sync down
        val localAccountIdToFields = accountIdToFields!!
        val target = getAccountContactsSyncDownTarget(
            String.format("%s IN %s", Constants.ID, makeInClause(localAccountIdToFields.keys))
        )
        trySyncDown(SyncState.MergeMode.OVERWRITE, target, ACCOUNTS_SOUP, numberAccounts, 1)

        // Check that db was correctly populated
        checkDb(localAccountIdToFields, ACCOUNTS_SOUP)
    }

    /**
     * Sync down the test accounts and contacts, make some local changes,
     * then sync down again with merge mode LEAVE_IF_CHANGED then sync down with merge mode OVERWRITE
     */
    @Test
    @Throws(Exception::class)
    fun testSyncDownWithoutOverwrite() {
        val numberAccounts = 4
        val numberContactsPerAccount = 3

        // Creating test accounts and contacts on server
        createAccountsAndContactsOnServer(numberAccounts, numberContactsPerAccount)

        // Sync down
        val target = getAccountContactsSyncDownTarget(
            String.format("%s IN %s", Constants.ID, makeInClause(accountIdToFields!!.keys))
        )
        trySyncDown(SyncState.MergeMode.OVERWRITE, target, ACCOUNTS_SOUP, numberAccounts, 1)

        // Make some local changes
        val localAccountIdToFields = accountIdToFields!!
        val accountIds = localAccountIdToFields.keys.toTypedArray()
        val accountIdUpdated = accountIds[0] // account that will updated along with some of the children
        val accountIdToFieldsUpdated = makeLocalChanges(localAccountIdToFields, ACCOUNTS_SOUP, arrayOf(accountIdUpdated))
        val contactIdToFieldsUpdated = makeLocalChanges(accountIdContactIdToFields!![accountIdUpdated]!!, CONTACTS_SOUP)
        val otherAccountId = accountIds[1] // account that will not be updated but will have updated children
        val otherContactIdToFieldsUpdated = makeLocalChanges(accountIdContactIdToFields!![otherAccountId]!!, CONTACTS_SOUP)

        // Sync down again with MergeMode.LEAVE_IF_CHANGED
        trySyncDown(SyncState.MergeMode.LEAVE_IF_CHANGED, target, ACCOUNTS_SOUP, numberAccounts, 1)

        // Check db - if an account and/or its children was locally modified then that account and all its children should be left alone
        val accountIdToFieldsExpected: MutableMap<String, Map<String, Any>> = HashMap(localAccountIdToFields)
        accountIdToFieldsExpected.putAll(accountIdToFieldsUpdated)
        checkDb(accountIdToFieldsExpected, ACCOUNTS_SOUP)
        for (accountId in localAccountIdToFields.keys) {
            if (accountId == accountIdUpdated) {
                checkDbStateFlags(Arrays.asList(accountId), false, true, false, ACCOUNTS_SOUP)
                checkDb(contactIdToFieldsUpdated, CONTACTS_SOUP)
                checkDbStateFlags(contactIdToFieldsUpdated.keys, false, true, false, CONTACTS_SOUP)
            } else if (accountId == otherAccountId) {
                checkDbStateFlags(Arrays.asList(accountId), false, false, false, ACCOUNTS_SOUP)
                checkDb(otherContactIdToFieldsUpdated, CONTACTS_SOUP)
                checkDbStateFlags(otherContactIdToFieldsUpdated.keys, false, true, false, CONTACTS_SOUP)
            } else {
                checkDbStateFlags(Arrays.asList(accountId), false, false, false, ACCOUNTS_SOUP)
                checkDb(accountIdContactIdToFields!![accountId]!!, CONTACTS_SOUP)
                checkDbStateFlags(accountIdContactIdToFields!![accountId]!!.keys, false, false, false, CONTACTS_SOUP)
            }
        }

        // Sync down again with MergeMode.OVERWRITE
        trySyncDown(SyncState.MergeMode.OVERWRITE, target, ACCOUNTS_SOUP, numberAccounts, 1)

        // Check db - all local changes should have been written over
        checkDb(localAccountIdToFields, ACCOUNTS_SOUP)
        checkDbStateFlags(localAccountIdToFields.keys, false, false, false, ACCOUNTS_SOUP)

        for (accountId in localAccountIdToFields.keys) {
            checkDb(accountIdContactIdToFields!![accountId]!!, CONTACTS_SOUP)
            checkDbStateFlags(accountIdContactIdToFields!![accountId]!!.keys, false, false, false, CONTACTS_SOUP)
        }
    }

    /**
     * Sync down the test accounts and contacts, modify accounts, re-sync, make sure only the updated ones are downloaded
     */
    @Test
    @Throws(Exception::class)
    fun testReSyncWithUpdatedParents() {
        val numberAccounts = 4
        val numberContactsPerAccount = 3

        // Creating up test accounts and contacts on server
        createAccountsAndContactsOnServer(numberAccounts, numberContactsPerAccount)

        // Sync down
        val target = getAccountContactsSyncDownTarget(
            String.format("%s IN %s", Constants.ID, makeInClause(accountIdToFields!!.keys))
        )
        val syncId = trySyncDown(SyncState.MergeMode.OVERWRITE, target, ACCOUNTS_SOUP, numberAccounts, 1)

        // Check sync time stamp
        val sync = syncManager.getSyncStatus(syncId)!!
        val options = sync.options
        val maxTimeStamp = sync.maxTimeStamp
        Assert.assertTrue("Wrong time stamp", maxTimeStamp > 0)

        // Make some remote change to accounts
        val localAccountIdToFields = accountIdToFields!!
        val idToFieldsUpdated = makeRemoteChanges(localAccountIdToFields, Constants.ACCOUNT)

        // Call reSync
        val queue = SyncUpdateCallbackQueue(syncId)
        syncManager.reSync(syncId, queue)

        // Check status updates
        checkStatus(queue.getNextSyncUpdate(syncId), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 0, -1)
        checkStatus(queue.getNextSyncUpdate(syncId), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 0, idToFieldsUpdated.size)
        checkStatus(queue.getNextSyncUpdate(syncId), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.DONE, 100, idToFieldsUpdated.size)

        // Check db
        checkDb(idToFieldsUpdated, ACCOUNTS_SOUP)

        // Check sync time stamp
        Assert.assertTrue("Wrong time stamp", syncManager.getSyncStatus(syncId)!!.maxTimeStamp > maxTimeStamp)
    }

    /**
     * Sync down the test accounts and contacts
     * Modify an account and some of its contacts and modify other contacts (without changing parent account)
     * Make sure only the modified account and its modified contacts are re-synced
     */
    @Test
    @Throws(Exception::class)
    fun testReSyncWithUpdatedChildren() {
        val numberAccounts = 4
        val numberContactsPerAccount = 3

        // Creating up test accounts and contacts on server
        createAccountsAndContactsOnServer(numberAccounts, numberContactsPerAccount)

        // Sync down
        val target = getAccountContactsSyncDownTarget(
            String.format("%s IN %s", Constants.ID, makeInClause(accountIdToFields!!.keys))
        )
        val syncId = trySyncDown(SyncState.MergeMode.OVERWRITE, target, ACCOUNTS_SOUP, numberAccounts, 1)

        // Check sync time stamp
        val sync = syncManager.getSyncStatus(syncId)!!
        val options = sync.options
        val maxTimeStamp = sync.maxTimeStamp
        Assert.assertTrue("Wrong time stamp", maxTimeStamp > 0)

        // Make some remote changes
        val localAccountIdToFields = accountIdToFields!!
        val accountIds = localAccountIdToFields.keys.toTypedArray()
        val accountId = accountIds[0] // account that will updated along with some of the children
        val accountIdToFieldsUpdated = makeRemoteChanges(localAccountIdToFields, Constants.ACCOUNT, arrayOf(accountId))
        val contactIdToFieldsUpdated = makeRemoteChanges(accountIdContactIdToFields!![accountId]!!, Constants.CONTACT)
        val otherAccountId = accountIds[1] // account that will not be updated but will have updated children
        val otherContactIdToFieldsUpdated = makeRemoteChanges(accountIdContactIdToFields!![otherAccountId]!!, Constants.CONTACT)

        // Call reSync
        val queue = SyncUpdateCallbackQueue(syncId)
        syncManager.reSync(syncId, queue)

        // Check status updates
        checkStatus(queue.getNextSyncUpdate(syncId), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 0, -1)
        checkStatus(queue.getNextSyncUpdate(syncId), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 0, 1)
        checkStatus(queue.getNextSyncUpdate(syncId), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.DONE, 100, 1)

        // Check db
        checkDb(accountIdToFieldsUpdated, ACCOUNTS_SOUP) // updated account should be updated in db
        checkDb(contactIdToFieldsUpdated, CONTACTS_SOUP) // updated contacts of updated account should be updated in db
        checkDb(accountIdContactIdToFields!![otherAccountId]!!, CONTACTS_SOUP) // updated contacts of non-updated account should not be updated in db

        // Check sync time stamp
        Assert.assertTrue("Wrong time stamp", syncManager.getSyncStatus(syncId)!!.maxTimeStamp > maxTimeStamp)
    }

    /**
     * Sync down the test accounts and contacts
     * Delete account from server - run cleanResyncGhosts
     */
    @Test
    @Throws(Exception::class)
    fun testCleanResyncGhostsForParentChildrenTarget() {
        val numberAccounts = 4
        val numberContactsPerAccount = 3

        // Creating up test accounts and contacts on server
        createAccountsAndContactsOnServer(numberAccounts, numberContactsPerAccount)

        // Sync down
        val target = getAccountContactsSyncDownTarget(
            String.format("%s IN %s", Constants.ID, makeInClause(accountIdToFields!!.keys))
        )
        val syncId = trySyncDown(SyncState.MergeMode.OVERWRITE, target, ACCOUNTS_SOUP, numberAccounts, 1)

        // Deletes 1 account on the server and verifies the ghost record is cleared from the soup.
        val accountIdDeleted = accountIdToFields!!.keys.toTypedArray()[0]
        deleteRecordsByIdOnServer(HashSet(Arrays.asList(accountIdDeleted)), Constants.ACCOUNT)
        tryCleanResyncGhosts(syncId)

        // Accounts and contacts expected to still be in db
        val accountIdToFieldsLeft: MutableMap<String, Map<String, Any>> = HashMap(accountIdToFields)
        accountIdToFieldsLeft.remove(accountIdDeleted)

        // Checking db
        checkDb(accountIdToFieldsLeft, ACCOUNTS_SOUP)
        checkDbDeleted(ACCOUNTS_SOUP, arrayOf(accountIdDeleted), Constants.ID)
        for (accountId in accountIdContactIdToFields!!.keys) {
            if (accountId == accountIdDeleted) {
                checkDbDeleted(CONTACTS_SOUP, accountIdContactIdToFields!![accountId]!!.keys.toTypedArray(), Constants.ID)
            } else {
                checkDb(accountIdContactIdToFields!![accountId]!!, CONTACTS_SOUP)
            }
        }
    }

    /**
     * Tests clean ghosts when soup is populated through more than one sync down
     */
    @Test
    @Throws(Exception::class)
    fun testCleanResyncGhostsForParentChildrenWithMultipleSyncs() {
        val numberAccounts = 6
        val numberContactsPerAccount = 3

        // Creating up test accounts and contacts on server
        createAccountsAndContactsOnServer(numberAccounts, numberContactsPerAccount)

        val accountIds = accountIdToFields!!.keys.toTypedArray()
        val accountIdsFirstSubset = Arrays.copyOfRange(accountIds, 0, 3) // id0, id1, id2
        val accountIdsSecondSubset = Arrays.copyOfRange(accountIds, 2, 6) //          id2, id3, id4, id5

        // Runs a first sync down (bringing down accounts id0, id1, id2 and their contacts)
        val firstTarget = getAccountContactsSyncDownTarget(
            String.format("%s IN %s", Constants.ID, makeInClause(accountIdsFirstSubset.asList()))
        )
        val firstSyncId = trySyncDown(SyncState.MergeMode.OVERWRITE, firstTarget, ACCOUNTS_SOUP, accountIdsFirstSubset.size, 1)
        checkDbExist(ACCOUNTS_SOUP, accountIdsFirstSubset, Constants.ID)
        checkDbSyncIdField(accountIdsFirstSubset, firstSyncId, ACCOUNTS_SOUP)

        // Runs a second sync down (bringing down accounts id2, id3, id4, id5 and their contacts)
        val secondTarget = getAccountContactsSyncDownTarget(
            String.format("%s IN %s", Constants.ID, makeInClause(accountIdsSecondSubset.asList()))
        )
        val secondSyncId = trySyncDown(SyncState.MergeMode.OVERWRITE, secondTarget, ACCOUNTS_SOUP, accountIdsSecondSubset.size, 1)
        checkDbExist(ACCOUNTS_SOUP, accountIdsSecondSubset, Constants.ID)
        checkDbSyncIdField(accountIdsSecondSubset, secondSyncId, ACCOUNTS_SOUP)

        // Deletes id0, id2, id5 on the server
        deleteRecordsByIdOnServer(HashSet(Arrays.asList(accountIds[0], accountIds[2], accountIds[5])), Constants.ACCOUNT)

        // Cleaning ghosts of first sync (should only remove id0 and its contacts)
        tryCleanResyncGhosts(firstSyncId)
        checkDbExist(ACCOUNTS_SOUP, arrayOf(accountIds[1], accountIds[2], accountIds[3], accountIds[4], accountIds[5]), Constants.ID)
        checkDbDeleted(ACCOUNTS_SOUP, arrayOf(accountIds[0]), Constants.ID)
        for (accountId in accountIdContactIdToFields!!.keys) {
            if (accountId == accountIds[0]) {
                checkDbDeleted(CONTACTS_SOUP, accountIdContactIdToFields!![accountId]!!.keys.toTypedArray(), Constants.ID)
            } else {
                checkDb(accountIdContactIdToFields!![accountId]!!, CONTACTS_SOUP)
            }
        }

        // Cleaning ghosts of second sync (should remove id2 and id5 and their contacts)
        tryCleanResyncGhosts(secondSyncId)
        checkDbExist(ACCOUNTS_SOUP, arrayOf(accountIds[1], accountIds[3], accountIds[4]), Constants.ID)
        checkDbDeleted(ACCOUNTS_SOUP, arrayOf(accountIds[2], accountIds[5]), Constants.ID)
        for (accountId in accountIdContactIdToFields!!.keys) {
            if (accountId == accountIds[0] || accountId == accountIds[2] || accountId == accountIds[5]) {
                checkDbDeleted(CONTACTS_SOUP, accountIdContactIdToFields!![accountId]!!.keys.toTypedArray(), Constants.ID)
            } else {
                checkDb(accountIdContactIdToFields!![accountId]!!, CONTACTS_SOUP)
            }
        }
    }

    /**
     * Create accounts and contacts locally, sync up with merge mode OVERWRITE, check smartstore and server afterwards
     */
    @Test
    @Throws(Exception::class)
    fun testSyncUpWithLocallyCreatedRecords() {
        trySyncUpWithLocallyCreatedRecords(SyncState.MergeMode.OVERWRITE)
    }

    /**
     * Create accounts and contacts locally, sync up with mege mode LEAVE_IF_CHANGED, check smartstore and server afterwards
     */
    @Test
    @Throws(Exception::class)
    fun testSyncUpWithLocallyCreatedRecordsWithoutOverwrite() {
        trySyncUpWithLocallyCreatedRecords(SyncState.MergeMode.LEAVE_IF_CHANGED)
    }

    /**
     * Create contacts on server, sync down
     * Create accounts locally, update contacts locally to be associated with them
     * Run sync up
     * Check smartstore and server afterwards
     */
    @Test
    @Throws(Exception::class)
    fun testSyncUpWithLocallyCreatedParentRecords() {
        // Create contacts on server
        val contactIdToName = createRecordsOnServer(6, Constants.CONTACT)

        // Sync down remote contacts
        val contactSyncDownTarget = SoqlSyncDownTarget("SELECT Id, LastName, LastModifiedDate FROM Contact WHERE Id IN " + makeInClause(contactIdToName.keys))
        trySyncDown(SyncState.MergeMode.OVERWRITE, contactSyncDownTarget, CONTACTS_SOUP, contactIdToName.size, 1)

        // Create a few accounts locally
        val accountNames = arrayOf(
            createRecordName(Constants.ACCOUNT),
            createRecordName(Constants.ACCOUNT)
        )
        val localAccounts = createAccountsLocally(accountNames)

        // Build account name to id map
        val accountNameToServerId: MutableMap<String, String> = HashMap()
        for (localAccount in localAccounts) {
            accountNameToServerId[localAccount.getString(Constants.NAME)] = localAccount.getString(Constants.ID)
        }

        // Update contacts locally to use locally created accounts
        val contactIdToAccountName: MutableMap<String, String> = HashMap()
        val idToFieldsLocallyUpdated: MutableMap<String, Map<String, Any>> = HashMap()
        var i = 0
        for (contactId in contactIdToName.keys) {
            val fieldsLocallyUpdated: MutableMap<String, Any> = HashMap()
            val accountName = accountNames[i % accountNames.size]
            fieldsLocallyUpdated[ACCOUNT_ID] = accountNameToServerId[accountName]!!
            idToFieldsLocallyUpdated[contactId] = fieldsLocallyUpdated
            contactIdToAccountName[contactId] = accountName
            i++
        }
        updateRecordsLocally(idToFieldsLocallyUpdated, CONTACTS_SOUP)

        // Sync up
        val target = getAccountContactsSyncUpTarget()
        trySyncUp(target, accountNames.size, SyncState.MergeMode.OVERWRITE)

        // Check that db doesn't show account entries as locally created anymore and that they use sfdc id
        val accountIdToFieldsCreated = getIdToFieldsByName(ACCOUNTS_SOUP, arrayOf(Constants.NAME, Constants.DESCRIPTION), Constants.NAME, accountNames)
        checkDbStateFlags(accountIdToFieldsCreated.keys, false, false, false, ACCOUNTS_SOUP)

        // Updated account name to server id map
        for (accountId in accountIdToFieldsCreated.keys) {
            accountNameToServerId[accountIdToFieldsCreated[accountId]!![Constants.NAME] as String] = accountId
        }

        // Check accounts on server
        checkServer(accountIdToFieldsCreated, Constants.ACCOUNT)

        // Check that db doesn't show contact entries as locally updated anymore
        val contactIdToFieldsUpdated = getIdToFieldsByName(CONTACTS_SOUP, arrayOf(Constants.LAST_NAME, ACCOUNT_ID), Constants.LAST_NAME, contactIdToName.values.toTypedArray())
        checkDbStateFlags(contactIdToFieldsUpdated.keys, false, false, false, CONTACTS_SOUP)

        // Check that contact use server account id in accountId field
        for (contactId in contactIdToFieldsUpdated.keys) {
            Assert.assertEquals("Wrong accountId", accountNameToServerId[contactIdToAccountName[contactId]], contactIdToFieldsUpdated[contactId]!![ACCOUNT_ID])
        }

        // Check contacts on server
        checkServer(contactIdToFieldsUpdated, Constants.CONTACT)

        // Cleanup
        deleteRecordsByIdOnServer(accountIdToFieldsCreated.keys, Constants.ACCOUNT)
        deleteRecordsByIdOnServer(contactIdToFieldsUpdated.keys, Constants.CONTACT)
    }

    /**
     * Create accounts on server, sync down
     * Create contacts locally, associates them with the accounts and run sync up
     * Check smartstore and server afterwards
     */
    @Test
    @Throws(Exception::class)
    fun testSyncUpWithLocallyCreatedChildrenRecords() {
        // Create accounts on server
        val accountIdToName = createRecordsOnServer(2, Constants.ACCOUNT)
        val accountNames = accountIdToName.values.toTypedArray()

        // Sync down remote accounts
        val accountSyncDownTarget = SoqlSyncDownTarget("SELECT Id, Name, LastModifiedDate FROM Account WHERE Id IN " + makeInClause(accountIdToName.keys))
        trySyncDown(SyncState.MergeMode.OVERWRITE, accountSyncDownTarget, ACCOUNTS_SOUP, accountIdToName.size, 1)

        // Create a few contacts locally associated with existing accounts
        val accountIdToFieldsCreated = getIdToFieldsByName(ACCOUNTS_SOUP, arrayOf(Constants.NAME), Constants.NAME, accountNames)
        val accountIdsArray = accountIdToFieldsCreated.keys.toTypedArray()
        val contactsForAccountsLocally = createContactsForAccountsLocally(3, *accountIdsArray)
        val contactNamesList: MutableList<String> = ArrayList()
        for (contacts in contactsForAccountsLocally.values) {
            for (contact in contacts) {
                contactNamesList.add(contact.getString(Constants.LAST_NAME))
            }
        }
        val contactNames = contactNamesList.toTypedArray()

        // Sync up
        val target = getAccountContactsSyncUpTarget()
        trySyncUp(target, accountNames.size, SyncState.MergeMode.OVERWRITE)

        // Check that db doesn't show contact entries as locally created anymore
        val contactIdToFieldsCreated = getIdToFieldsByName(CONTACTS_SOUP, arrayOf(Constants.LAST_NAME, ACCOUNT_ID), Constants.LAST_NAME, contactNames)
        checkDbStateFlags(contactIdToFieldsCreated.keys, false, false, false, CONTACTS_SOUP)

        // Check contacts on server
        checkServer(contactIdToFieldsCreated, Constants.CONTACT)

        // Cleanup
        deleteRecordsByIdOnServer(accountIdToFieldsCreated.keys, Constants.ACCOUNT)
        deleteRecordsByIdOnServer(contactIdToFieldsCreated.keys, Constants.CONTACT)
    }

    /**
     * Create account on server, sync down
     * Remotely delete account
     * Create contacts locally, associates them with the account and run sync up
     * Check smartstore and server afterwards
     * The account should be recreated and the contacts should be associated to the new account id
     */
    @Test
    @Throws(Exception::class)
    fun testSyncUpWithLocallyCreatedChildrenRemotelyDeletedParent() {
        // Create account on server
        val accountIdToName = createRecordsOnServer(1, Constants.ACCOUNT)
        val accountId = accountIdToName.keys.toTypedArray()[0]
        val accountName = accountIdToName.values.toTypedArray()[0]

        // Sync down remote accounts
        val accountSyncDownTarget = SoqlSyncDownTarget("SELECT Id, Name, LastModifiedDate FROM Account WHERE Id = '$accountId'")
        trySyncDown(SyncState.MergeMode.OVERWRITE, accountSyncDownTarget, ACCOUNTS_SOUP, accountIdToName.size, 1)

        // Create a few contacts locally associated with account
        val contactsForAccountsLocally = createContactsForAccountsLocally(3, accountId)
        val contactNamesList: MutableList<String> = ArrayList()
        for (contacts in contactsForAccountsLocally.values) {
            for (contact in contacts) {
                contactNamesList.add(contact.getString(Constants.LAST_NAME))
            }
        }
        val contactNames = contactNamesList.toTypedArray()

        // Delete account remotely
        deleteRecordsByIdOnServer(HashSet(Arrays.asList(accountId)), Constants.ACCOUNT)

        // Sync up
        val target = getAccountContactsSyncUpTarget()
        trySyncUp(target, 1, SyncState.MergeMode.OVERWRITE)

        // Make sure account got recreated
        val accountFields: MutableMap<String, Any> = HashMap()
        accountFields[Constants.NAME] = accountName
        val newAccountId = checkRecordRecreated(accountId, accountFields, Constants.NAME, ACCOUNTS_SOUP, Constants.ACCOUNT, null, null)

        // Check that db doesn't show contact entries as locally created anymore
        val contactIdToFieldsCreated = getIdToFieldsByName(CONTACTS_SOUP, arrayOf(Constants.LAST_NAME, ACCOUNT_ID), Constants.LAST_NAME, contactNames)
        checkDbStateFlags(contactIdToFieldsCreated.keys, false, false, false, CONTACTS_SOUP)

        // Check contacts on server
        checkServer(contactIdToFieldsCreated, Constants.CONTACT)

        // Check that contact use new account id in accountId field
        for (contactId in contactIdToFieldsCreated.keys) {
            Assert.assertEquals("Wrong accountId", newAccountId, contactIdToFieldsCreated[contactId]!![ACCOUNT_ID])
        }

        // Cleanup
        deleteRecordsByIdOnServer(HashSet(Arrays.asList(newAccountId)), Constants.ACCOUNT)
        deleteRecordsByIdOnServer(contactIdToFieldsCreated.keys, Constants.CONTACT)
    }

    /**
     * Create accounts and contacts on server, sync down
     * Update some of the accounts and contacts - using bad names (too long) for some
     * Sync up
     * Check smartstore and server afterwards
     */
    @Test
    @Throws(Exception::class)
    fun testSyncUpWithErrors() {
        // Creating test accounts and contacts on server
        createAccountsAndContactsOnServer(3, 3)

        // Sync down
        val syncDownTarget = getAccountContactsSyncDownTarget(
            String.format("%s IN %s", Constants.ID, makeInClause(accountIdToFields!!.keys))
        )
        trySyncDown(SyncState.MergeMode.OVERWRITE, syncDownTarget, ACCOUNTS_SOUP, 3, 1)

        // Picking accounts / contacts
        val accountIds = accountIdToFields!!.keys.toTypedArray()
        val account1Id = accountIds[0]
        val contactIdsOfAccount1 = accountIdContactIdToFields!![account1Id]!!.keys.toTypedArray()
        val contact11Id = contactIdsOfAccount1[0]
        val contact12Id = contactIdsOfAccount1[1]

        val account2Id = accountIds[1]
        val contactIdsOfAccount2 = accountIdContactIdToFields!![account2Id]!!.keys.toTypedArray()
        val contact21Id = contactIdsOfAccount2[0]
        val contact22Id = contactIdsOfAccount2[1]

        // Build long suffix
        val buffer = StringBuffer(255)
        for (i in 0 until 255) buffer.append("x")
        val suffixTooLong = buffer.toString()

        // Updating with valid values
        val updatedAccount1Fields = updateRecordLocally(ACCOUNTS_SOUP, account1Id, accountIdToFields!![account1Id]!!)[account1Id]
        val updatedContact11Fields = updateRecordLocally(CONTACTS_SOUP, contact11Id, accountIdContactIdToFields!![account1Id]!![contact11Id]!!)[contact11Id]
        val updatedContact21Fields = updateRecordLocally(CONTACTS_SOUP, contact21Id, accountIdContactIdToFields!![account2Id]!![contact21Id]!!)[contact21Id]

        // Updating with invalid values
        updateRecordLocally(ACCOUNTS_SOUP, account2Id, accountIdToFields!![account2Id]!!, suffixTooLong)
        updateRecordLocally(CONTACTS_SOUP, contact12Id, accountIdContactIdToFields!![account1Id]!![contact12Id]!!, suffixTooLong)
        updateRecordLocally(CONTACTS_SOUP, contact22Id, accountIdContactIdToFields!![account2Id]!![contact22Id]!!, suffixTooLong)

        // Sync up
        trySyncUp(getAccountContactsSyncUpTarget(), 2, SyncState.MergeMode.OVERWRITE)

        // Check valid records in db: should no longer be marked as dirty
        checkDbStateFlags(Arrays.asList(account1Id), false, false, false, ACCOUNTS_SOUP)
        checkDbStateFlags(Arrays.asList(contact11Id, contact21Id), false, false, false, CONTACTS_SOUP)

        // Check invalid records in db
        // Should still be marked as dirty
        checkDbStateFlags(Arrays.asList(account2Id), false, true, false, ACCOUNTS_SOUP)
        checkDbStateFlags(Arrays.asList(contact12Id, contact22Id), false, true, false, CONTACTS_SOUP)
        // Should have populated last error fields
        checkDbLastErrorField(arrayOf(account2Id), "Account Name: data value too large", ACCOUNTS_SOUP)
        checkDbLastErrorField(arrayOf(contact12Id, contact22Id), "Last Name: data value too large", CONTACTS_SOUP)

        // Check server
        val accountIdToFieldsExpectedOnServer: MutableMap<String, Map<String, Any>> = HashMap()
        for (id in accountIds) {
            // Only update to account1 should have gone through
            if (id == account1Id) {
                accountIdToFieldsExpectedOnServer[id] = updatedAccount1Fields!!
            } else {
                accountIdToFieldsExpectedOnServer[id] = accountIdToFields!![id]!!
            }
        }
        checkServer(accountIdToFieldsExpectedOnServer, Constants.ACCOUNT)
        val contactIdToFieldsExpectedOnServer: MutableMap<String, Map<String, Any>> = HashMap()
        for (id in accountIds) {
            val contactIdToFields = accountIdContactIdToFields!![id]
            for (cid in contactIdToFields!!.keys) {
                // Only update to contact11 and contact21 should have gone through
                if (cid == contact11Id) {
                    contactIdToFieldsExpectedOnServer[cid] = updatedContact11Fields!!
                } else if (cid == contact21Id) {
                    contactIdToFieldsExpectedOnServer[cid] = updatedContact21Fields!!
                } else {
                    contactIdToFieldsExpectedOnServer[cid] = contactIdToFields[cid]!!
                }
            }
        }
        checkServer(contactIdToFieldsExpectedOnServer, Constants.CONTACT)
    }

    /**
     * Create accounts and contacts on server.
     * Create accounts and contacts locally - some with external id matching server record:
     * - account with external id populated with contact with no external id
     * - account with external id populated with contact with external id
     * - account with no external id with contact with external id
     *
     * Sync up with external id field name provided, check smartstore and server afterwards.
     */
    @Test
    @Throws(Exception::class)
    fun testParentChildrenSyncUpWithExternalId() {
        val externalIdFieldName = "Id"

        // Creating test accounts and contacts on server
        createAccountsAndContactsOnServer(3, 1)

        // Get id of accounts on the server
        val accountIds = accountIdToFields!!.keys.toTypedArray()
        Arrays.sort(accountIds)
        val accountId0 = accountIds[0]
        val accountId1 = accountIds[1]
        val accountId2 = accountIds[2]

        // Get name of third account on server
        val originalAccountName2 = accountIdToFields!![accountId2]!![Constants.NAME] as String

        // Get id of contacts on the server
        val contactId0 = accountIdContactIdToFields!![accountId0]!!.keys.toTypedArray()[0]
        val contactId1 = accountIdContactIdToFields!![accountId1]!!.keys.toTypedArray()[0]
        val contactId2 = accountIdContactIdToFields!![accountId2]!!.keys.toTypedArray()[0]

        // Get name of first contact on server
        val originalContactName0 = accountIdContactIdToFields!![accountId0]!![contactId0]!![Constants.LAST_NAME] as String

        // Create accounts and contacts locally
        val accountToContactMap = createAccountsAndContactsLocally(
            arrayOf(createRecordName(Constants.ACCOUNT), createRecordName(Constants.ACCOUNT), createRecordName(Constants.ACCOUNT)), 1
        )
        val localAccounts = accountToContactMap.keys.toTypedArray()
        val localContacts = arrayOf(
            accountToContactMap[localAccounts[0]]!![0],
            accountToContactMap[localAccounts[1]]!![0],
            accountToContactMap[localAccounts[2]]!![0]
        )

        // Local account names
        val accountName0 = localAccounts[0].getString(Constants.NAME)
        val accountName1 = localAccounts[1].getString(Constants.NAME)
        val accountName2 = localAccounts[2].getString(Constants.NAME)

        // Local contact names
        val contactName0 = localContacts[0].getString(Constants.LAST_NAME)
        val contactName1 = localContacts[1].getString(Constants.LAST_NAME)
        val contactName2 = localContacts[2].getString(Constants.LAST_NAME)

        // Update Id field to match existing id for account 0 and 1
        localAccounts[0].put(externalIdFieldName, accountId0)
        smartStore.upsert(ACCOUNTS_SOUP, localAccounts[0])
        localAccounts[1].put(externalIdFieldName, accountId1)
        smartStore.upsert(ACCOUNTS_SOUP, localAccounts[1])

        // Update Id field to match existing id for contact 1 and 2
        // Update accountId field also for contact 0 and 1
        localContacts[0].put(ACCOUNT_ID, accountId0)
        smartStore.upsert(CONTACTS_SOUP, localContacts[0])
        localContacts[1].put(externalIdFieldName, contactId1)
        localContacts[1].put(ACCOUNT_ID, accountId1)
        smartStore.upsert(CONTACTS_SOUP, localContacts[1])
        localContacts[2].put(externalIdFieldName, contactId2)
        smartStore.upsert(CONTACTS_SOUP, localContacts[2])

        // Sync up
        trySyncUp(getAccountContactsSyncUpTarget(Constants.LAST_MODIFIED_DATE, Constants.LAST_MODIFIED_DATE, externalIdFieldName, externalIdFieldName), 3, SyncState.MergeMode.OVERWRITE)

        // Getting id for third account upserted - the one without an valid external id
        val newAccountId = getIdToFieldsByName(ACCOUNTS_SOUP, arrayOf(), Constants.NAME, arrayOf(accountName2)).keys.toTypedArray()[0]

        // Getting id for first contact upserted - the one without an valid external id
        val newContactId = getIdToFieldsByName(CONTACTS_SOUP, arrayOf(), Constants.LAST_NAME, arrayOf(contactName0)).keys.toTypedArray()[0]

        // Expected accounts records locally
        val expectedAccountsDbIdToFields: MutableMap<String, Map<String, Any>> = HashMap()
        expectedAccountsDbIdToFields[accountId0] = createFieldsMap(Constants.NAME, accountName0)
        expectedAccountsDbIdToFields[accountId1] = createFieldsMap(Constants.NAME, accountName1)
        expectedAccountsDbIdToFields[newAccountId] = createFieldsMap(Constants.NAME, accountName2)

        // Check db
        checkDbStateFlags(expectedAccountsDbIdToFields.keys, false, false, false, ACCOUNTS_SOUP)
        checkDb(expectedAccountsDbIdToFields, ACCOUNTS_SOUP)

        // Expected contacts records locally
        val expectedContactsDbIdToFields: MutableMap<String, Map<String, Any>> = HashMap()
        expectedContactsDbIdToFields[newContactId] = createFieldsMap(Constants.LAST_NAME, contactName0)
        expectedContactsDbIdToFields[contactId1] = createFieldsMap(Constants.LAST_NAME, contactName1)
        expectedContactsDbIdToFields[contactId2] = createFieldsMap(Constants.LAST_NAME, contactName2)

        // Check db
        checkDbStateFlags(expectedContactsDbIdToFields.keys, false, false, false, CONTACTS_SOUP)
        checkDb(expectedContactsDbIdToFields, CONTACTS_SOUP)

        // Expected accounts on server
        val expectedAccountsServerIdToFields: MutableMap<String, Map<String, Any>> = HashMap()
        expectedAccountsServerIdToFields[accountId0] = createFieldsMap(Constants.NAME, accountName0)
        expectedAccountsServerIdToFields[accountId1] = createFieldsMap(Constants.NAME, accountName1)
        expectedAccountsServerIdToFields[accountId2] = createFieldsMap(Constants.NAME, originalAccountName2)
        expectedAccountsServerIdToFields[newAccountId] = createFieldsMap(Constants.NAME, accountName2)

        // Check server
        checkServer(expectedAccountsServerIdToFields, Constants.ACCOUNT)

        // Expected contacts on server
        val expectedContactsServerIdToFields: MutableMap<String, Map<String, Any>> = HashMap()
        expectedContactsServerIdToFields[newContactId] = createFieldsMap(Constants.LAST_NAME, contactName0)
        expectedContactsServerIdToFields[contactId0] = createFieldsMap(Constants.LAST_NAME, originalContactName0)
        expectedContactsServerIdToFields[contactId1] = createFieldsMap(Constants.LAST_NAME, contactName1)
        expectedContactsServerIdToFields[contactId2] = createFieldsMap(Constants.LAST_NAME, contactName2)

        // Check server
        checkServer(expectedContactsServerIdToFields, Constants.CONTACT)

        // Clean up
        deleteRecordsByIdOnServer(Collections.singletonList(newAccountId), Constants.ACCOUNT)
        deleteRecordsByIdOnServer(Collections.singletonList(newContactId), Constants.CONTACT)
    }
}
