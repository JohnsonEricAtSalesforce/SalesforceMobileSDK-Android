package com.salesforce.androidsdk.mobilesync.target

import com.salesforce.androidsdk.mobilesync.manager.SyncManagerTestCase
import com.salesforce.androidsdk.mobilesync.target.ParentChildrenSyncTargetHelper.RelationshipType
import com.salesforce.androidsdk.mobilesync.util.ChildrenInfo
import com.salesforce.androidsdk.mobilesync.util.Constants
import com.salesforce.androidsdk.mobilesync.util.ParentInfo
import com.salesforce.androidsdk.mobilesync.util.SyncState
import com.salesforce.androidsdk.mobilesync.util.SyncOptions
import com.salesforce.androidsdk.rest.RestRequest
import com.salesforce.androidsdk.smartstore.store.IndexSpec
import com.salesforce.androidsdk.smartstore.store.QuerySpec
import com.salesforce.androidsdk.smartstore.store.SmartSqlHelper
import com.salesforce.androidsdk.smartstore.store.SmartStore
import com.salesforce.androidsdk.util.JSONObjectHelper
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert
import org.junit.Before
import java.util.Arrays
import java.util.Collections
import java.util.SortedSet

open class ParentChildrenSyncTestCase : SyncManagerTestCase() {

    protected var accountIdToFields: MutableMap<String, Map<String, Any>>? = null
    protected var accountIdContactIdToFields: MutableMap<String, MutableMap<String, Map<String, Any>>>? = null

    @Before
    override fun setUp() {
        super.setUp()
        createAccountsSoup()
        createContactsSoup()
    }

    @After
    override fun tearDown() {
        super.tearDown()
        dropContactsSoup()
        dropAccountsSoup()
        accountIdToFields?.let { deleteRecordsByIdOnServer(it.keys, Constants.ACCOUNT) }
        accountIdContactIdToFields?.let { map ->
            for (accountId in map.keys) {
                val contactIdToFields = map[accountId]!!
                deleteRecordsByIdOnServer(contactIdToFields.keys, Constants.CONTACT)
            }
        }
    }

    protected open fun trySyncUpsWithVariousChanges(numberAccounts: Int, numberContactsPerAccount: Int, localChangeForAccount: Change, remoteChangeForAccount: Change, localChangeForContact: Change, remoteChangeForContact: Change) {
        createAccountsAndContactsOnServer(numberAccounts, numberContactsPerAccount)
        val syncDownTarget = getAccountContactsSyncDownTarget(String.format("%s IN %s", Constants.ID, makeInClause(accountIdToFields!!.keys)))
        trySyncDown(SyncState.MergeMode.OVERWRITE, syncDownTarget, ACCOUNTS_SOUP, numberAccounts, 1)

        val accountIds = accountIdToFields!!.keys.toTypedArray()
        val accountId = accountIds[0]
        val accountFields = accountIdToFields!![accountId]!!
        val contactIdsOfAccount = if (numberContactsPerAccount > 0) accountIdContactIdToFields!![accountId]!!.keys.toTypedArray() else null
        val contactId = contactIdsOfAccount?.get(0)
        val otherContactId = contactIdsOfAccount?.get(1)
        val contactFields = if (contactId != null) accountIdContactIdToFields!![accountId]!![contactId] else null

        val syncUpTarget = getAccountContactsSyncUpTarget()

        var localUpdatesAccount: Map<String, Map<String, Any>>? = null
        when (localChangeForAccount) {
            Change.NONE -> {}
            Change.UPDATE -> localUpdatesAccount = updateRecordLocally(ACCOUNTS_SOUP, accountId, accountFields)
            Change.DELETE -> deleteRecordsLocally(ACCOUNTS_SOUP, accountId)
        }

        var localUpdatesContact: Map<String, Map<String, Any>>? = null
        if (contactId != null) {
            when (localChangeForContact) {
                Change.NONE -> {}
                Change.UPDATE -> localUpdatesContact = updateRecordLocally(CONTACTS_SOUP, contactId, contactFields!!)
                Change.DELETE -> deleteRecordsLocally(CONTACTS_SOUP, contactId)
            }
        }

        if (remoteChangeForAccount != Change.NONE || remoteChangeForContact != Change.NONE) {
            Thread.sleep(1000)
        }

        var remoteUpdatesAccount: Map<String, Map<String, Any>>? = null
        when (remoteChangeForAccount) {
            Change.NONE -> {}
            Change.UPDATE -> remoteUpdatesAccount = updateRecordOnServer(Constants.ACCOUNT, accountId, accountFields)
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

        if ((remoteChangeForAccount == Change.NONE || (remoteChangeForAccount == Change.DELETE && localChangeForAccount == Change.DELETE))
            && (remoteChangeForContact == Change.NONE || (remoteChangeForContact == Change.DELETE && localChangeForContact == Change.DELETE))) {
            trySyncUp(syncUpTarget, 1, SyncState.MergeMode.LEAVE_IF_CHANGED)
            checkDbAndServerAfterCompletedSyncUp(accountId, contactId, otherContactId, remoteChangeForAccount, localChangeForContact, remoteChangeForContact, localUpdatesAccount, localUpdatesContact, localChangeForAccount)
            trySyncUp(syncUpTarget, 0, SyncState.MergeMode.OVERWRITE)
        } else {
            trySyncUp(syncUpTarget, 1, SyncState.MergeMode.LEAVE_IF_CHANGED)
            checkDbAndServerAfterBlockedSyncUp(accountId, contactId, localChangeForAccount, remoteChangeForAccount, localChangeForContact, remoteChangeForContact, localUpdatesAccount, remoteUpdatesAccount, localUpdatesContact, remoteUpdatesContact)
            trySyncUp(syncUpTarget, 1, SyncState.MergeMode.OVERWRITE)
            checkDbAndServerAfterCompletedSyncUp(accountId, contactId, otherContactId, remoteChangeForAccount, localChangeForContact, remoteChangeForContact, localUpdatesAccount, localUpdatesContact, localChangeForAccount)
        }
    }

    private fun checkDbAndServerAfterBlockedSyncUp(accountId: String, contactId: String?, localChangeForAccount: Change, remoteChangeForAccount: Change, localChangeForContact: Change, remoteChangeForContact: Change, localUpdatesAccount: Map<String, Map<String, Any>>?, remoteUpdatesAccount: Map<String, Map<String, Any>>?, localUpdatesContact: Map<String, Map<String, Any>>?, remoteUpdatesContact: Map<String, Map<String, Any>>?) {
        if (localChangeForAccount == Change.UPDATE) { checkDb(localUpdatesAccount!!, ACCOUNTS_SOUP) }
        checkDbStateFlags(listOf(accountId), false, localChangeForAccount == Change.UPDATE, localChangeForAccount == Change.DELETE, ACCOUNTS_SOUP)
        when (remoteChangeForAccount) {
            Change.NONE -> {}
            Change.UPDATE -> checkServer(remoteUpdatesAccount!!, Constants.ACCOUNT)
            Change.DELETE -> checkServerDeleted(arrayOf(accountId), Constants.ACCOUNT)
        }
        if (contactId != null) {
            val contactIdsOfAccount = accountIdContactIdToFields!![accountId]!!.keys
            val otherContactIdsOfAccount = HashSet(contactIdsOfAccount); otherContactIdsOfAccount.remove(contactId)
            if (localChangeForContact == Change.UPDATE) { checkDb(localUpdatesContact!!, CONTACTS_SOUP) }
            checkDbStateFlags(listOf(contactId), false, localChangeForContact == Change.UPDATE, localChangeForContact == Change.DELETE, CONTACTS_SOUP)
            checkDbRelationships(contactIdsOfAccount, accountId, CONTACTS_SOUP, Constants.ID, ACCOUNT_ID)
            if (remoteChangeForAccount == Change.DELETE) {
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

    private fun checkDbAndServerAfterCompletedSyncUp(accountId: String, contactId: String?, otherContactId: String?, remoteChangeForAccount: Change, localChangeForContact: Change, remoteChangeForContact: Change, localUpdatesAccount: Map<String, Map<String, Any>>?, localUpdatesContact: Map<String, Map<String, Any>>?, localChangeForAccount: Change) {
        var newAccountId: String? = null; var newContactId: String? = null; var newOtherContactId: String? = null
        try {
            when (localChangeForAccount) {
                Change.NONE -> checkRecordAfterSync(accountId, accountIdToFields!![accountId]!!, ACCOUNTS_SOUP, Constants.ACCOUNT, null, null)
                Change.UPDATE -> {
                    if (remoteChangeForAccount == Change.DELETE) { newAccountId = checkRecordRecreated(accountId, localUpdatesAccount!![accountId]!!, Constants.NAME, ACCOUNTS_SOUP, Constants.ACCOUNT, null, null) }
                    else { checkRecordAfterSync(accountId, localUpdatesAccount!![accountId]!!, ACCOUNTS_SOUP, Constants.ACCOUNT, null, null) }
                }
                Change.DELETE -> checkDeletedRecordAfterSync(accountId, ACCOUNTS_SOUP, Constants.ACCOUNT)
            }
            if (contactId != null) {
                if (localChangeForAccount == Change.DELETE) {
                    val contactIdsOfAcccount = accountIdContactIdToFields!![accountId]!!.keys.toTypedArray()
                    checkDbDeleted(CONTACTS_SOUP, contactIdsOfAcccount, Constants.ID)
                    checkServerDeleted(contactIdsOfAcccount, Constants.CONTACT)
                } else {
                    when (localChangeForContact) {
                        Change.NONE -> {
                            if (remoteChangeForAccount == Change.DELETE || remoteChangeForContact == Change.DELETE) { newContactId = checkRecordRecreated(contactId, accountIdContactIdToFields!![accountId]!![contactId]!!, Constants.LAST_NAME, CONTACTS_SOUP, Constants.CONTACT, newAccountId ?: accountId, ACCOUNT_ID) }
                            else { checkRecordAfterSync(contactId, accountIdContactIdToFields!![accountId]!![contactId]!!, CONTACTS_SOUP, Constants.CONTACT, accountId, ACCOUNT_ID) }
                        }
                        Change.UPDATE -> {
                            if (remoteChangeForAccount == Change.DELETE || remoteChangeForContact == Change.DELETE) { newContactId = checkRecordRecreated(contactId, localUpdatesContact!![contactId]!!, Constants.LAST_NAME, CONTACTS_SOUP, Constants.CONTACT, newAccountId ?: accountId, ACCOUNT_ID) }
                            else { checkRecordAfterSync(contactId, localUpdatesContact!![contactId]!!, CONTACTS_SOUP, Constants.CONTACT, accountId, ACCOUNT_ID) }
                        }
                        Change.DELETE -> checkDeletedRecordAfterSync(contactId, CONTACTS_SOUP, Constants.CONTACT)
                    }
                    if (remoteChangeForAccount == Change.DELETE) {
                        newOtherContactId = checkRecordRecreated(otherContactId!!, accountIdContactIdToFields!![accountId]!![otherContactId]!!, Constants.LAST_NAME, CONTACTS_SOUP, Constants.CONTACT, newAccountId!!, ACCOUNT_ID)
                    }
                }
            }
        } finally {
            if (newAccountId != null) deleteRecordsByIdOnServer(Collections.singleton(newAccountId), Constants.ACCOUNT)
            if (newContactId != null) deleteRecordsByIdOnServer(Collections.singleton(newContactId), Constants.CONTACT)
            if (newOtherContactId != null) deleteRecordsByIdOnServer(Collections.singleton(newOtherContactId), Constants.CONTACT)
        }
    }

    open fun checkRecordRecreated(recordId: String, fields: Map<String, Any>, nameField: String, soupName: String, objectType: String, parentId: String?, parentIdField: String?): String {
        val updatedName = fields[nameField] as String
        val newIdToFields = getIdToFieldsByName(soupName, arrayOf(nameField), nameField, arrayOf(updatedName))
        val newRecordId = newIdToFields.keys.toTypedArray()[0]
        Assert.assertFalse("Record should have new id", newRecordId == recordId)
        checkDbDeleted(soupName, arrayOf(recordId), Constants.ID)
        checkServerDeleted(arrayOf(recordId), objectType)
        checkRecordAfterSync(newRecordId, newIdToFields[newRecordId]!!, soupName, objectType, parentId, parentIdField)
        return newRecordId
    }

    private fun checkRecordAfterSync(recordId: String, fields: Map<String, Any>, soupName: String, objectType: String, parentId: String?, parentIdField: String?) {
        checkDbStateFlags(listOf(recordId), false, false, false, soupName)
        val fieldsCopy = HashMap(fields)
        if (parentId != null) { fieldsCopy[parentIdField!!] = parentId }
        val idToFields = HashMap<String, Map<String, Any>>(); idToFields[recordId] = fieldsCopy
        checkDb(idToFields, soupName)
        checkServer(idToFields, objectType)
    }

    private fun checkDeletedRecordAfterSync(recordId: String, soupName: String, objectType: String) {
        checkDbDeleted(soupName, arrayOf(recordId), Constants.ID)
        checkServerDeleted(arrayOf(recordId), objectType)
    }

    enum class Change { NONE, UPDATE, DELETE }

    protected open fun trySyncUpWithLocallyCreatedRecords(syncUpMergeMode: SyncState.MergeMode) {
        val numberContactsPerAccount = 3
        val accountNames = arrayOf(createRecordName(Constants.ACCOUNT), createRecordName(Constants.ACCOUNT), createRecordName(Constants.ACCOUNT), createRecordName(Constants.ACCOUNT), createRecordName(Constants.ACCOUNT), createRecordName(Constants.ACCOUNT))
        val mapAccountToContacts = createAccountsAndContactsLocally(accountNames, numberContactsPerAccount)
        val contactNames = Array(numberContactsPerAccount * accountNames.size) { "" }
        var i = 0
        for (contacts in mapAccountToContacts.values) { for (contact in contacts) { contactNames[i] = contact.getString(Constants.LAST_NAME); i++ } }
        val target = getAccountContactsSyncUpTarget()
        trySyncUp(target, accountNames.size, syncUpMergeMode)
        val accountIdToFieldsCreated = getIdToFieldsByName(ACCOUNTS_SOUP, arrayOf(Constants.NAME, Constants.DESCRIPTION), Constants.NAME, accountNames)
        checkDbStateFlags(accountIdToFieldsCreated.keys, false, false, false, ACCOUNTS_SOUP)
        checkServer(accountIdToFieldsCreated, Constants.ACCOUNT)
        val contactIdToFieldsCreated = getIdToFieldsByName(CONTACTS_SOUP, arrayOf(Constants.LAST_NAME, ACCOUNT_ID), Constants.LAST_NAME, contactNames)
        checkDbStateFlags(contactIdToFieldsCreated.keys, false, false, false, CONTACTS_SOUP)
        checkServer(contactIdToFieldsCreated, Constants.CONTACT)
        deleteRecordsByIdOnServer(accountIdToFieldsCreated.keys, Constants.ACCOUNT)
        deleteRecordsByIdOnServer(contactIdToFieldsCreated.keys, Constants.CONTACT)
    }

    protected open fun tryGetDirtyRecordIds(expectedRecords: Array<JSONObject>) {
        val target = getAccountContactsSyncDownTarget()
        val dirtyRecordIds: SortedSet<String> = target.getDirtyRecordIds(syncManager, ACCOUNTS_SOUP, Constants.ID)
        Assert.assertEquals("Wrong number of dirty records", expectedRecords.size, dirtyRecordIds.size)
        for (expectedRecord in expectedRecords) { Assert.assertTrue(dirtyRecordIds.contains(expectedRecord.getString(Constants.ID))) }
    }

    protected open fun tryGetNonDirtyRecordIds(expectedRecords: Array<JSONObject>) {
        val target = getAccountContactsSyncDownTarget()
        val nonDirtyRecordIds: SortedSet<String> = target.getNonDirtyRecordIds(syncManager, ACCOUNTS_SOUP, Constants.ID, "")
        Assert.assertEquals("Wrong number of non-dirty records", expectedRecords.size, nonDirtyRecordIds.size)
        for (expectedRecord in expectedRecords) { Assert.assertTrue(nonDirtyRecordIds.contains(expectedRecord.getString(Constants.ID))) }
    }

    protected open fun cleanRecords(soupName: String, records: Array<JSONObject>) { for (record in records) { cleanRecord(soupName, record) } }

    protected open fun cleanRecord(soupName: String, record: JSONObject) {
        record.put(SyncTarget.LOCAL, false); record.put(SyncTarget.LOCALLY_CREATED, false); record.put(SyncTarget.LOCALLY_UPDATED, false); record.put(SyncTarget.LOCALLY_DELETED, false)
        syncManager.smartStore.upsert(soupName, record)
    }

    protected open fun getAccountContactsSyncDownTarget(): ParentChildrenSyncDownTarget = getAccountContactsSyncDownTarget("")
    protected open fun getAccountContactsSyncDownTarget(parentSoqlFilter: String): ParentChildrenSyncDownTarget = getAccountContactsSyncDownTarget(Constants.LAST_MODIFIED_DATE, Constants.LAST_MODIFIED_DATE, parentSoqlFilter)

    protected open fun getAccountContactsSyncDownTarget(accountModificationDateFieldName: String, contactModificationDateFieldName: String, parentSoqlFilter: String?): ParentChildrenSyncDownTarget {
        return ParentChildrenSyncDownTarget(
            ParentInfo(Constants.ACCOUNT, ACCOUNTS_SOUP, Constants.ID, accountModificationDateFieldName),
            listOf(Constants.ID, Constants.NAME, Constants.DESCRIPTION), parentSoqlFilter,
            ChildrenInfo(Constants.CONTACT, Constants.CONTACT + "s", CONTACTS_SOUP, ACCOUNT_ID, Constants.ID, contactModificationDateFieldName),
            listOf(Constants.LAST_NAME, ACCOUNT_ID), RelationshipType.MASTER_DETAIL)
    }

    protected open fun getAccountContactsSyncUpTarget(): ParentChildrenSyncUpTarget = getAccountContactsSyncUpTarget(Constants.LAST_MODIFIED_DATE, Constants.LAST_MODIFIED_DATE)
    protected open fun getAccountContactsSyncUpTarget(accountModificationDateFieldName: String, contactModificationDateFieldName: String): ParentChildrenSyncUpTarget = getAccountContactsSyncUpTarget(accountModificationDateFieldName, contactModificationDateFieldName, null, null)

    protected open fun getAccountContactsSyncUpTarget(accountModificationDateFieldName: String, contactModificationDateFieldName: String, accountExternalIdFieldName: String?, contactExternalIdFieldName: String?): ParentChildrenSyncUpTarget {
        return ParentChildrenSyncUpTarget(
            ParentInfo(Constants.ACCOUNT, ACCOUNTS_SOUP, Constants.ID, accountModificationDateFieldName, accountExternalIdFieldName),
            listOf(Constants.ID, Constants.NAME, Constants.DESCRIPTION), listOf(Constants.NAME, Constants.DESCRIPTION),
            ChildrenInfo(Constants.CONTACT, Constants.CONTACT + "s", CONTACTS_SOUP, ACCOUNT_ID, Constants.ID, contactModificationDateFieldName, contactExternalIdFieldName),
            listOf(Constants.LAST_NAME, ACCOUNT_ID), listOf(Constants.LAST_NAME, ACCOUNT_ID), RelationshipType.MASTER_DETAIL)
    }

    protected open fun queryWithInClause(soupName: String, fieldName: String, values: Array<String>, orderBy: String?): Array<JSONObject> {
        val sql = String.format("SELECT {%s:%s} FROM {%s} WHERE {%s:%s} IN %s %s", soupName, SmartSqlHelper.SOUP, soupName, soupName, fieldName, makeInClause(values), if (orderBy == null) "" else String.format(" ORDER BY {%s:%s} ASC", soupName, orderBy))
        val querySpec = QuerySpec.buildSmartQuerySpec(sql, Int.MAX_VALUE)
        val rows = smartStore.query(querySpec, 0)
        return Array(rows.length()) { i -> rows.getJSONArray(i).getJSONObject(0) }
    }

    protected open fun createAccountsAndContactsOnServer(numberAccounts: Int, numberContactsPerAccount: Int) {
        accountIdToFields = HashMap()
        accountIdContactIdToFields = HashMap()
        val refIdToFields = HashMap<String, Map<String, Any>>()
        val accountTrees = ArrayList<RestRequest.SObjectTree>()
        val listAccountFields = buildFieldsMapForRecords(numberAccounts, Constants.ACCOUNT, null)
        for (i in listAccountFields.indices) {
            val listContactFields = buildFieldsMapForRecords(numberContactsPerAccount, Constants.CONTACT, null)
            val refIdAccount = "refAccount_$i"
            val accountFields = listAccountFields[i]
            refIdToFields[refIdAccount] = accountFields
            val contactTrees = ArrayList<RestRequest.SObjectTree>()
            for (j in listContactFields.indices) {
                val refIdContact = refIdAccount + "__refContact_$j"
                val contactFields = listContactFields[j]
                refIdToFields[refIdContact] = contactFields
                @Suppress("UNCHECKED_CAST")
                contactTrees.add(RestRequest.SObjectTree(Constants.CONTACT, Constants.CONTACTS, refIdContact, contactFields as Map<String, Object>, null))
            }
            @Suppress("UNCHECKED_CAST")
            accountTrees.add(RestRequest.SObjectTree(Constants.ACCOUNT, "", refIdAccount, accountFields as Map<String, Object>, contactTrees))
        }
        val request = RestRequest.getRequestForSObjectTree(apiVersion, Constants.ACCOUNT, accountTrees)
        val response = restClient.sendSync(request)
        val refIdToId = HashMap<String, String>()
        val results = response.asJSONObject().getJSONArray("results")
        for (i in 0 until results.length()) {
            val result = results.getJSONObject(i)
            val refId = result.getString(RestRequest.REFERENCE_ID)
            val id = result.getString(Constants.LID)
            refIdToId[refId] = id
        }
        for (refId in refIdToId.keys) {
            val fields = refIdToFields[refId]!!
            val parts = refId.split("__")
            val accountId = refIdToId[parts[0]]!!
            val contactId = if (parts.size > 1) refIdToId[refId] else null
            if (contactId == null) {
                (accountIdToFields as HashMap)[accountId] = fields
            } else {
                if (!accountIdContactIdToFields!!.containsKey(accountId))
                    (accountIdContactIdToFields as HashMap)[accountId] = HashMap()
                accountIdContactIdToFields!![accountId]!![contactId] = fields
            }
        }
    }
}
