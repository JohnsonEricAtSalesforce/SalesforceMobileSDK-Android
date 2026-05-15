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

import com.salesforce.androidsdk.rest.RestRequest
import com.salesforce.androidsdk.rest.RestResponse
import com.salesforce.androidsdk.smartstore.store.IndexSpec
import com.salesforce.androidsdk.smartstore.store.QuerySpec
import com.salesforce.androidsdk.smartstore.store.SmartSqlHelper
import com.salesforce.androidsdk.smartstore.store.SmartStore
import com.salesforce.androidsdk.mobilesync.manager.SyncManagerTestCase
import com.salesforce.androidsdk.mobilesync.target.ParentChildrenSyncTargetHelper.RelationshipType
import com.salesforce.androidsdk.mobilesync.util.ChildrenInfo
import com.salesforce.androidsdk.mobilesync.util.Constants
import com.salesforce.androidsdk.mobilesync.util.ParentInfo
import com.salesforce.androidsdk.mobilesync.util.SyncState
import com.salesforce.androidsdk.util.JSONObjectHelper
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.junit.After
import org.junit.Assert
import org.junit.Before
import java.io.IOException
import java.util.ArrayList
import java.util.Arrays
import java.util.Collections
import java.util.HashMap
import java.util.HashSet
import java.util.SortedSet

/**
 * Test class for ParentChildrenSyncDownTarget and ParentChildrenSyncUpTarget.
 */
open class ParentChildrenSyncTestCase : SyncManagerTestCase() {

    protected var accountIdToFields: MutableMap<String, Map<String, Any>>? = null
    protected var accountIdContactIdToFields: MutableMap<String, MutableMap<String, Map<String, Any>>>? = null

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
        super.tearDown()
        dropContactsSoup()
        dropAccountsSoup()

        // accountIdToFields and accountIdContactIdToFields are not used by all tests
        if (accountIdToFields != null) {
            deleteRecordsByIdOnServer(accountIdToFields!!.keys, Constants.ACCOUNT)
        }
        if (accountIdContactIdToFields != null) {
            for (accountId in accountIdContactIdToFields!!.keys) {
                val contactIdToFields = accountIdContactIdToFields!![accountId]
                deleteRecordsByIdOnServer(contactIdToFields!!.keys, Constants.CONTACT)
            }
        }
    }

    /**
     * Helper for various sync up test
     *
     * Create accounts and contacts on server
     * Run sync down
     * Then locally and/or remotely delete and/or update an account or contact
     * Run sync up with leave-if-changed (if requested)
     * Check db and server
     * Run sync up with overwrite
     * Check db and server
     *
     * @param numberAccounts
     * @param numberContactsPerAccount
     * @param localChangeForAccount
     * @param remoteChangeForAccount
     * @param localChangeForContact
     * @param remoteChangeForContact
     */
    @Throws(Exception::class)
    protected fun trySyncUpsWithVariousChanges(
        numberAccounts: Int,
        numberContactsPerAccount: Int,
        localChangeForAccount: Change,
        remoteChangeForAccount: Change,
        localChangeForContact: Change,
        remoteChangeForContact: Change
    ) {
        // Creating test accounts and contacts on server
        createAccountsAndContactsOnServer(numberAccounts, numberContactsPerAccount)

        // Sync down
        val syncDownTarget = getAccountContactsSyncDownTarget(
            String.format("%s IN %s", Constants.ID, makeInClause(accountIdToFields!!.keys))
        )
        trySyncDown(SyncState.MergeMode.OVERWRITE, syncDownTarget, ACCOUNTS_SOUP, numberAccounts, 1)

        // Pick an account and contact
        val accountIds = accountIdToFields!!.keys.toTypedArray()
        val accountId = accountIds[0]
        val accountFields = accountIdToFields!![accountId]
        val contactIdsOfAccount = if (numberContactsPerAccount > 0) accountIdContactIdToFields!![accountId]!!.keys.toTypedArray() else null
        val contactId = contactIdsOfAccount?.get(0)
        val otherContactId = contactIdsOfAccount?.get(1)
        val contactFields = if (contactId != null) accountIdContactIdToFields!![accountId]!![contactId] else null

        // Build sync up target
        val syncUpTarget = getAccountContactsSyncUpTarget()

        // Apply localChangeForAccount
        var localUpdatesAccount: Map<String, Map<String, Any>>? = null
        when (localChangeForAccount) {
            Change.NONE -> {}
            Change.UPDATE -> localUpdatesAccount = updateRecordLocally(ACCOUNTS_SOUP, accountId, accountFields!!)
            Change.DELETE -> deleteRecordsLocally(ACCOUNTS_SOUP, accountId)
        }

        // Apply localChangeForContact
        var localUpdatesContact: Map<String, Map<String, Any>>? = null
        if (contactId != null) {
            when (localChangeForContact) {
                Change.NONE -> {}
                Change.UPDATE -> localUpdatesContact = updateRecordLocally(CONTACTS_SOUP, contactId, contactFields!!)
                Change.DELETE -> deleteRecordsLocally(CONTACTS_SOUP, contactId)
            }
        }

        // Sleep before doing remote changes
        if (remoteChangeForAccount != Change.NONE || remoteChangeForContact != Change.NONE) {
            Thread.sleep(1000) // time stamp precision is in seconds
        }

        // Apply remoteChangeForAccount
        var remoteUpdatesAccount: Map<String, Map<String, Any>>? = null
        when (remoteChangeForAccount) {
            Change.NONE -> {}
            Change.UPDATE -> remoteUpdatesAccount = updateRecordOnServer(Constants.ACCOUNT, accountId, accountFields!!)
            Change.DELETE -> deleteRecordsByIdOnServer(Collections.singleton(accountId), Constants.ACCOUNT)
        }

        var remoteUpdatesContact: Map<String, Map<String, Any>>? = null
        if (contactId != null) {
            when (remoteChangeForContact) {
                Change.NONE -> {}
                Change.UPDATE -> remoteUpdatesContact = updateRecordOnServer(Constants.CONTACT, contactId, contactFields!!)
                Change.DELETE -> deleteRecordsByIdOnServer(Collections.singleton(contactId), Constants.CONTACT)
            }
        }

        // Sync up

        // In some cases, leave-if-changed will succeed
        if ((remoteChangeForAccount == Change.NONE || (remoteChangeForAccount == Change.DELETE && localChangeForAccount == Change.DELETE))          // no remote parent change or it's a delete and we did a local delete also
            && (remoteChangeForContact == Change.NONE || (remoteChangeForContact == Change.DELETE && localChangeForContact == Change.DELETE))
        ) {  // no remote child change  or it's a delete and we did a local delete also
            // Sync up with leave-if-changed
            trySyncUp(syncUpTarget, 1, SyncState.MergeMode.LEAVE_IF_CHANGED)

            // Check db and server - local changes should have made it over
            checkDbAndServerAfterCompletedSyncUp(accountId, contactId, otherContactId, remoteChangeForAccount, localChangeForContact, remoteChangeForContact, localUpdatesAccount, localUpdatesContact, localChangeForAccount)

            // Sync up with overwrite - there should be no dirty records found
            trySyncUp(syncUpTarget, 0, SyncState.MergeMode.OVERWRITE)
        } else {
            // In all other cases, leave-if-changed will fail

            // Sync up with leave-if-changed
            trySyncUp(syncUpTarget, 1, SyncState.MergeMode.LEAVE_IF_CHANGED)

            // Check db and server - nothing should have changed
            checkDbAndServerAfterBlockedSyncUp(accountId, contactId, localChangeForAccount, remoteChangeForAccount, localChangeForContact, remoteChangeForContact, localUpdatesAccount, remoteUpdatesAccount, localUpdatesContact, remoteUpdatesContact)

            // Sync up with overwrite
            trySyncUp(syncUpTarget, 1, SyncState.MergeMode.OVERWRITE)

            // Check db and server - local changes should have made it over
            checkDbAndServerAfterCompletedSyncUp(accountId, contactId, otherContactId, remoteChangeForAccount, localChangeForContact, remoteChangeForContact, localUpdatesAccount, localUpdatesContact, localChangeForAccount)
        }
    }

    @Throws(JSONException::class, IOException::class)
    private fun checkDbAndServerAfterBlockedSyncUp(
        accountId: String, contactId: String?, localChangeForAccount: Change, remoteChangeForAccount: Change, localChangeForContact: Change, remoteChangeForContact: Change, localUpdatesAccount: Map<String, Map<String, Any>>?, remoteUpdatesAccount: Map<String, Map<String, Any>>?, localUpdatesContact: Map<String, Map<String, Any>>?, remoteUpdatesContact: Map<String, Map<String, Any>>?
    ) {

        //
        // Check parent
        //

        // Check db
        if (localChangeForAccount == Change.UPDATE) {
            checkDb(localUpdatesAccount!!, ACCOUNTS_SOUP)
        }

        checkDbStateFlags(Arrays.asList(accountId), false, localChangeForAccount == Change.UPDATE, localChangeForAccount == Change.DELETE, ACCOUNTS_SOUP)

        // Check server
        when (remoteChangeForAccount) {
            Change.NONE -> {}
            Change.UPDATE -> checkServer(remoteUpdatesAccount!!, Constants.ACCOUNT)
            Change.DELETE -> checkServerDeleted(arrayOf(accountId), Constants.ACCOUNT)
        }

        //
        // Check children if any
        //

        if (contactId != null) {

            val contactIdsOfAccount = accountIdContactIdToFields!![accountId]!!.keys
            val otherContactIdsOfAccount: MutableSet<String> = HashSet(contactIdsOfAccount)
            otherContactIdsOfAccount.remove(contactId)

            // Check db

            if (localChangeForContact == Change.UPDATE) {
                checkDb(localUpdatesContact!!, CONTACTS_SOUP)
            }

            checkDbStateFlags(Arrays.asList(contactId), false, localChangeForContact == Change.UPDATE, localChangeForContact == Change.DELETE, CONTACTS_SOUP)
            checkDbRelationships(contactIdsOfAccount, accountId, CONTACTS_SOUP, Constants.ID, ACCOUNT_ID)

            // Check server

            if (remoteChangeForAccount == Change.DELETE) {
                // Master delete => deletes children
                checkServerDeleted(contactIdsOfAccount.toTypedArray(), Constants.CONTACT)
            } else {
                when (remoteChangeForContact) {
                    Change.NONE -> {}
                    Change.UPDATE -> checkServer(remoteUpdatesContact!!, Constants.CONTACT)
                    Change.DELETE -> checkServerDeleted(arrayOf(contactId), Constants.CONTACT)
                }
            }
        }
    }

    @Throws(Exception::class)
    private fun checkDbAndServerAfterCompletedSyncUp(
        accountId: String, contactId: String?, otherContactId: String?, remoteChangeForAccount: Change, localChangeForContact: Change, remoteChangeForContact: Change, localUpdatesAccount: Map<String, Map<String, Any>>?, localUpdatesContact: Map<String, Map<String, Any>>?, localChangeForAccount: Change
    ) {
        var newAccountId: String? = null
        var newContactId: String? = null
        var newOtherContactId: String? = null

        try {

            //
            // Check parent
            //

            when (localChangeForAccount) {
                Change.NONE -> checkRecordAfterSync(accountId, accountIdToFields!![accountId], ACCOUNTS_SOUP, Constants.ACCOUNT, null, null)
                Change.UPDATE -> {
                    if (remoteChangeForAccount == Change.DELETE) {
                        newAccountId = checkRecordRecreated(accountId, localUpdatesAccount!![accountId], Constants.NAME, ACCOUNTS_SOUP, Constants.ACCOUNT, null, null)
                    } else {
                        checkRecordAfterSync(accountId, localUpdatesAccount!![accountId], ACCOUNTS_SOUP, Constants.ACCOUNT, null, null)
                    }
                }
                Change.DELETE -> checkDeletedRecordAfterSync(accountId, ACCOUNTS_SOUP, Constants.ACCOUNT)
            }

            //
            // Check children if any
            //

            if (contactId != null) {

                if (localChangeForAccount == Change.DELETE) {
                    // Master delete => deletes children
                    val contactIdsOfAcccount = accountIdContactIdToFields!![accountId]!!.keys.toTypedArray()
                    checkDbDeleted(CONTACTS_SOUP, contactIdsOfAcccount, Constants.ID)
                    checkServerDeleted(contactIdsOfAcccount, Constants.CONTACT)
                } else {
                    when (localChangeForContact) {
                        Change.NONE -> {
                            if (remoteChangeForAccount == Change.DELETE || remoteChangeForContact == Change.DELETE) {
                                newContactId = checkRecordRecreated(contactId, accountIdContactIdToFields!![accountId]!![contactId], Constants.LAST_NAME, CONTACTS_SOUP, Constants.CONTACT, newAccountId ?: accountId, ACCOUNT_ID)
                            } else {
                                checkRecordAfterSync(contactId, accountIdContactIdToFields!![accountId]!![contactId], CONTACTS_SOUP, Constants.CONTACT, accountId, ACCOUNT_ID)
                            }
                        }
                        Change.UPDATE -> {
                            if (remoteChangeForAccount == Change.DELETE || remoteChangeForContact == Change.DELETE) {
                                newContactId = checkRecordRecreated(contactId, localUpdatesContact!![contactId], Constants.LAST_NAME, CONTACTS_SOUP, Constants.CONTACT, newAccountId ?: accountId, ACCOUNT_ID)
                            } else {
                                checkRecordAfterSync(contactId, localUpdatesContact!![contactId], CONTACTS_SOUP, Constants.CONTACT, accountId, ACCOUNT_ID)
                            }
                        }
                        Change.DELETE -> checkDeletedRecordAfterSync(contactId, CONTACTS_SOUP, Constants.CONTACT)
                    }

                    if (remoteChangeForAccount == Change.DELETE) {
                        // Check that other contact was recreated also
                        newOtherContactId = checkRecordRecreated(otherContactId!!, accountIdContactIdToFields!![accountId]!![otherContactId], Constants.LAST_NAME, CONTACTS_SOUP, Constants.CONTACT, newAccountId, ACCOUNT_ID)
                    }
                }
            }
        } finally {
            // Cleaning "recreated" records
            if (newAccountId != null) deleteRecordsByIdOnServer(Collections.singleton(newAccountId), Constants.ACCOUNT)
            if (newContactId != null) deleteRecordsByIdOnServer(Collections.singleton(newContactId), Constants.CONTACT)
            if (newOtherContactId != null) deleteRecordsByIdOnServer(Collections.singleton(newOtherContactId), Constants.CONTACT)
        }
    }

    /**
     * Check record that were "recreated"
     * A record is "recreated" when synced up locally updated and remotely deleted
     *
     * Make sure old record is gone
     * Make sure sync flags are false
     * Make sure fields are as expected on db and server (including parent id field if provided)
     *
     * @param recordId
     * @param fields
     * @param nameField
     * @param soupName
     * @param objectType
     * @param parentId
     * @param parentIdField
     *
     * @return new record id
     * @throws JSONException
     * @throws IOException
     */
    @Throws(JSONException::class, IOException::class)
    protected fun checkRecordRecreated(
        recordId: String, fields: Map<String, Any>?, nameField: String, soupName: String, objectType: String, parentId: String?, parentIdField: String?
    ): String {
        val updatedName = fields!![nameField] as String
        val newIdToFields = getIdToFieldsByName(soupName, arrayOf(nameField), nameField, arrayOf(updatedName))
        val newRecordId = newIdToFields.keys.toTypedArray()[0]

        // Make sure new id is really new
        Assert.assertFalse("Record should have new id", newRecordId == recordId)

        // Make sure old id is gone from db and server
        checkDbDeleted(soupName, arrayOf(recordId), Constants.ID)
        checkServerDeleted(arrayOf(recordId), objectType)

        // Make sure record with new id is correct in db and server
        checkRecordAfterSync(newRecordId, newIdToFields[newRecordId], soupName, objectType, parentId, parentIdField)

        return newRecordId
    }

    /**
     * Check record after a sync
     *
     * Make sure sync flags are false
     * Make sure fields are as expected on db and server
     * Make sure parent id field has correct value on db and server (if provided)
     *
     * @param recordId
     * @param fields
     * @param soupName
     * @param objectType
     * @param parentId
     * @param parentIdField @return
     * @throws JSONException
     * @throws IOException
     */
    @Throws(JSONException::class, IOException::class)
    private fun checkRecordAfterSync(
        recordId: String, fields: Map<String, Any>?, soupName: String, objectType: String, parentId: String?, parentIdField: String?
    ) {

        // Check record is no longer marked as dirty
        checkDbStateFlags(Arrays.asList(recordId), false, false, false, soupName)

        // Prepare fields map to check (add parentId if provided)
        val fieldsCopy: MutableMap<String, Any> = HashMap(fields!!)
        if (parentId != null) {
            fieldsCopy[parentIdField!!] = parentId
        }
        val idToFields: MutableMap<String, Map<String, Any>> = HashMap()
        idToFields[recordId] = fieldsCopy

        // Check db
        checkDb(idToFields, soupName)

        // Check server
        checkServer(idToFields, objectType)
    }

    /**
     * Check that deleted record is truly gone from db and server
     *
     * @param recordId
     * @param soupName
     * @param objectType
     * @return
     * @throws JSONException
     * @throws IOException
     */
    @Throws(JSONException::class, IOException::class)
    private fun checkDeletedRecordAfterSync(recordId: String, soupName: String, objectType: String) {
        checkDbDeleted(soupName, arrayOf(recordId), Constants.ID)
        checkServerDeleted(arrayOf(recordId), objectType)
    }

    /**
     * Useful enum for trySyncUpsWithVariousChanges
     */
    enum class Change {
        NONE,
        UPDATE,
        DELETE
    }

    /**
     * Helper method for testSyncUpWithLocallyCreatedRecords*
     *
     * @param syncUpMergeMode
     * @throws Exception
     */
    @Throws(Exception::class)
    protected fun trySyncUpWithLocallyCreatedRecords(syncUpMergeMode: SyncState.MergeMode) {
        val numberContactsPerAccount = 3

        // Create a few entries locally
        val accountNames = arrayOf(
            createRecordName(Constants.ACCOUNT),
            createRecordName(Constants.ACCOUNT),
            createRecordName(Constants.ACCOUNT),
            createRecordName(Constants.ACCOUNT),
            createRecordName(Constants.ACCOUNT),
            createRecordName(Constants.ACCOUNT)
        )
        val mapAccountToContacts = createAccountsAndContactsLocally(accountNames, numberContactsPerAccount)
        val contactNames = arrayOfNulls<String>(numberContactsPerAccount * accountNames.size)
        var i = 0
        for (contacts in mapAccountToContacts.values) {
            for (contact in contacts) {
                contactNames[i] = contact.getString(Constants.LAST_NAME)
                i++
            }
        }

        // Sync up
        val target = getAccountContactsSyncUpTarget()
        trySyncUp(target, accountNames.size, syncUpMergeMode)

        // Check that db doesn't show account entries as locally created anymore and that they use sfdc id
        val accountIdToFieldsCreated = getIdToFieldsByName(ACCOUNTS_SOUP, arrayOf(Constants.NAME, Constants.DESCRIPTION), Constants.NAME, accountNames)
        checkDbStateFlags(accountIdToFieldsCreated.keys, false, false, false, ACCOUNTS_SOUP)

        // Check accounts on server
        checkServer(accountIdToFieldsCreated, Constants.ACCOUNT)

        // Check that db doesn't show contact entries as locally created anymore and that they use sfc id
        val contactIdToFieldsCreated = getIdToFieldsByName(CONTACTS_SOUP, arrayOf(Constants.LAST_NAME, ACCOUNT_ID), Constants.LAST_NAME, contactNames.requireNoNulls())
        checkDbStateFlags(contactIdToFieldsCreated.keys, false, false, false, CONTACTS_SOUP)

        // Check contacts on server
        checkServer(contactIdToFieldsCreated, Constants.CONTACT)

        // Cleanup
        deleteRecordsByIdOnServer(accountIdToFieldsCreated.keys, Constants.ACCOUNT)
        deleteRecordsByIdOnServer(contactIdToFieldsCreated.keys, Constants.CONTACT)
    }

    @Throws(JSONException::class)
    protected fun tryGetDirtyRecordIds(expectedRecords: Array<JSONObject>) {
        val target = getAccountContactsSyncDownTarget()
        val dirtyRecordIds = target.getDirtyRecordIds(syncManager, ACCOUNTS_SOUP, Constants.ID)
        Assert.assertEquals("Wrong number of dirty records", expectedRecords.size, dirtyRecordIds.size)
        for (expectedRecord in expectedRecords) {
            Assert.assertTrue(dirtyRecordIds.contains(expectedRecord.getString(Constants.ID)))
        }
    }

    @Throws(JSONException::class)
    protected fun tryGetNonDirtyRecordIds(expectedRecords: Array<JSONObject>) {
        val target = getAccountContactsSyncDownTarget()
        val nonDirtyRecordIds = target.getNonDirtyRecordIds(syncManager, ACCOUNTS_SOUP, Constants.ID, "")
        Assert.assertEquals("Wrong number of non-dirty records", expectedRecords.size, nonDirtyRecordIds.size)
        for (expectedRecord in expectedRecords) {
            Assert.assertTrue(nonDirtyRecordIds.contains(expectedRecord.getString(Constants.ID)))
        }
    }

    @Throws(JSONException::class)
    protected fun cleanRecords(soupName: String, records: Array<JSONObject>) {
        for (record in records) {
            cleanRecord(soupName, record)
        }
    }

    @Throws(JSONException::class)
    protected fun cleanRecord(soupName: String, record: JSONObject) {
        record.put(SyncTarget.LOCAL, false)
        record.put(SyncTarget.LOCALLY_CREATED, false)
        record.put(SyncTarget.LOCALLY_UPDATED, false)
        record.put(SyncTarget.LOCALLY_DELETED, false)
        syncManager.smartStore.upsert(soupName, record)
    }

    protected fun getAccountContactsSyncDownTarget(): ParentChildrenSyncDownTarget {
        return getAccountContactsSyncDownTarget("")
    }

    protected fun getAccountContactsSyncDownTarget(parentSoqlFilter: String): ParentChildrenSyncDownTarget {
        return getAccountContactsSyncDownTarget(Constants.LAST_MODIFIED_DATE, Constants.LAST_MODIFIED_DATE, parentSoqlFilter)
    }

    protected fun getAccountContactsSyncDownTarget(
        accountModificationDateFieldName: String, contactModificationDateFieldName: String, parentSoqlFilter: String
    ): ParentChildrenSyncDownTarget {
        return ParentChildrenSyncDownTarget(
            ParentInfo(Constants.ACCOUNT, ACCOUNTS_SOUP, Constants.ID, accountModificationDateFieldName),
            Arrays.asList(Constants.ID, Constants.NAME, Constants.DESCRIPTION),
            parentSoqlFilter,
            ChildrenInfo(Constants.CONTACT, Constants.CONTACT + "s", CONTACTS_SOUP, ACCOUNT_ID, Constants.ID, contactModificationDateFieldName),
            Arrays.asList(Constants.LAST_NAME, ACCOUNT_ID),
            RelationshipType.MASTER_DETAIL
        ) // account-contacts are master-detail
    }

    protected fun getAccountContactsSyncUpTarget(): ParentChildrenSyncUpTarget {
        return getAccountContactsSyncUpTarget(Constants.LAST_MODIFIED_DATE, Constants.LAST_MODIFIED_DATE)
    }

    protected fun getAccountContactsSyncUpTarget(
        accountModificationDateFieldName: String, contactModificationDateFieldName: String
    ): ParentChildrenSyncUpTarget {
        return getAccountContactsSyncUpTarget(accountModificationDateFieldName, contactModificationDateFieldName, null, null)
    }

    protected fun getAccountContactsSyncUpTarget(
        accountModificationDateFieldName: String, contactModificationDateFieldName: String, accountExternalIdFieldName: String?, contactExternalIdFieldName: String?
    ): ParentChildrenSyncUpTarget {
        return ParentChildrenSyncUpTarget(
            ParentInfo(Constants.ACCOUNT, ACCOUNTS_SOUP, Constants.ID, accountModificationDateFieldName, accountExternalIdFieldName),
            Arrays.asList(Constants.ID, Constants.NAME, Constants.DESCRIPTION),
            Arrays.asList(Constants.NAME, Constants.DESCRIPTION),
            ChildrenInfo(Constants.CONTACT, Constants.CONTACT + "s", CONTACTS_SOUP, ACCOUNT_ID, Constants.ID, contactModificationDateFieldName, contactExternalIdFieldName),
            Arrays.asList(Constants.LAST_NAME, ACCOUNT_ID),
            Arrays.asList(Constants.LAST_NAME, ACCOUNT_ID),
            RelationshipType.MASTER_DETAIL
        ) // account-contacts are master-detail
    }

    @Throws(JSONException::class)
    protected fun queryWithInClause(soupName: String, fieldName: String, values: Array<String>, orderBy: String?): Array<JSONObject> {
        val sql = String.format(
            "SELECT {%s:%s} FROM {%s} WHERE {%s:%s} IN %s %s",
            soupName, SmartSqlHelper.SOUP, soupName, soupName, fieldName,
            makeInClause(values),
            if (orderBy == null) "" else String.format(" ORDER BY {%s:%s} ASC", soupName, orderBy)
        )
        val querySpec = QuerySpec.buildSmartQuerySpec(sql, Int.MAX_VALUE)
        val rows = smartStore.query(querySpec, 0)
        val arr = arrayOfNulls<JSONObject>(rows.length())
        for (i in 0 until rows.length()) {
            arr[i] = rows.getJSONArray(i).getJSONObject(0)
        }
        return arr.requireNoNulls()
    }

    @Throws(Exception::class)
    protected fun createAccountsAndContactsOnServer(numberAccounts: Int, numberContactsPerAccount: Int) {
        accountIdToFields = HashMap()
        accountIdContactIdToFields = HashMap()
        val refIdToFields: MutableMap<String, Map<String, Any>> = HashMap()
        val accountTrees: MutableList<RestRequest.SObjectTree> = ArrayList()
        val listAccountFields = buildFieldsMapForRecords(numberAccounts, Constants.ACCOUNT, null)
        for (i in 0 until listAccountFields.size) {
            val listContactFields = buildFieldsMapForRecords(numberContactsPerAccount, Constants.CONTACT, emptyMap())
            val refIdAccount = "refAccount_$i"
            val accountFields = listAccountFields[i]
            refIdToFields[refIdAccount] = accountFields
            val contactTrees: MutableList<RestRequest.SObjectTree> = ArrayList()
            for (j in 0 until listContactFields.size) {
                val refIdContact = refIdAccount + "__refContact_" + j
                val contactFields = listContactFields[j]
                refIdToFields[refIdContact] = contactFields
                contactTrees.add(RestRequest.SObjectTree(Constants.CONTACT, Constants.CONTACTS, refIdContact, contactFields, null))
            }
            accountTrees.add(RestRequest.SObjectTree(Constants.ACCOUNT, null, refIdAccount, accountFields, contactTrees))
        }
        val request = RestRequest.getRequestForSObjectTree(apiVersion, Constants.ACCOUNT, accountTrees)

        // Send request
        val response = restClient.sendSync(request)

        // Parse response
        val refIdToId: MutableMap<String, String> = HashMap()
        val results = response!!.asJSONObject().getJSONArray("results")
        for (i in 0 until results.length()) {
            val result = results.getJSONObject(i)
            val refId = result.getString(RestRequest.REFERENCE_ID)
            val id = result.getString(Constants.LID)
            refIdToId[refId] = id
        }

        // Populate accountIdToFields and accountIdContactIdToFields
        for (refId in refIdToId.keys) {
            val fields = refIdToFields[refId]
            val parts = refId.split("__").toTypedArray()
            val accountId = refIdToId[parts[0]]!!
            val contactId = if (parts.size > 1) refIdToId[refId] else null
            if (contactId == null) {
                accountIdToFields!![accountId] = fields!!
            } else {
                if (!accountIdContactIdToFields!!.containsKey(accountId))
                    accountIdContactIdToFields!![accountId] = HashMap()
                accountIdContactIdToFields!![accountId]!![contactId] = fields!!
            }
        }
    }
}
