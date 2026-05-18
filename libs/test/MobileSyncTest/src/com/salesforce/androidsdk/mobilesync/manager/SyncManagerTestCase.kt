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
package com.salesforce.androidsdk.mobilesync.manager

import android.text.TextUtils
import com.salesforce.androidsdk.mobilesync.target.SyncDownTarget
import com.salesforce.androidsdk.mobilesync.target.SyncTarget
import com.salesforce.androidsdk.mobilesync.target.SyncUpTarget
import com.salesforce.androidsdk.mobilesync.util.Constants
import com.salesforce.androidsdk.mobilesync.util.JSONTestHelper
import com.salesforce.androidsdk.mobilesync.util.SyncOptions
import com.salesforce.androidsdk.mobilesync.util.SyncState
import com.salesforce.androidsdk.mobilesync.util.SyncUpdateCallbackQueue
import com.salesforce.androidsdk.rest.ApiVersionStrings
import com.salesforce.androidsdk.rest.RestRequest
import com.salesforce.androidsdk.smartstore.store.IndexSpec
import com.salesforce.androidsdk.smartstore.store.QuerySpec
import com.salesforce.androidsdk.smartstore.store.SmartStore
import com.salesforce.androidsdk.util.JSONObjectHelper
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert
import java.util.Arrays
import java.util.concurrent.ArrayBlockingQueue

/**
 * Abstract super class for all SyncManager test classes.
 */
abstract open class SyncManagerTestCase : ManagerTestCase() {

    companion object {
        @JvmStatic protected val TYPE = "type"
        @JvmStatic protected val RECORDS = "records"
        @JvmStatic protected val ACCOUNTS_SOUP = "accounts"
        @JvmStatic protected val TOTAL_SIZE_UNKNOWN = -2
        @JvmStatic protected val REMOTELY_UPDATED = "_r_upd"
        @JvmStatic protected val LOCALLY_UPDATED = "_l_upd"
        @JvmStatic protected val CONTACTS_SOUP = "contacts"
        @JvmStatic protected val ACCOUNT_ID = "AccountId"
    }

    override fun tearDown() {
        deleteSyncs()
        deleteGlobalSyncs()
        super.tearDown()
    }

    open fun createAccountsSoup() {
        createAccountsSoup(ACCOUNTS_SOUP)
    }

    open fun createAccountsSoup(soupName: String) {
        val indexSpecs = arrayOf(
            IndexSpec(Constants.ID, SmartStore.Type.string),
            IndexSpec(Constants.NAME, SmartStore.Type.string),
            IndexSpec(Constants.DESCRIPTION, SmartStore.Type.string),
            IndexSpec(SyncTarget.LOCAL, SmartStore.Type.string),
            IndexSpec(SyncTarget.SYNC_ID, SmartStore.Type.integer)
        )
        smartStore.registerSoup(soupName, indexSpecs)
    }

    open fun dropAccountsSoup() {
        dropAccountsSoup(ACCOUNTS_SOUP)
    }

    open fun dropAccountsSoup(soupName: String) {
        smartStore.dropSoup(soupName)
    }

    open fun createContactsSoup() {
        val contactsIndexSpecs = arrayOf(
            IndexSpec(Constants.ID, SmartStore.Type.string),
            IndexSpec(Constants.LAST_NAME, SmartStore.Type.string),
            IndexSpec(SyncTarget.LOCAL, SmartStore.Type.string),
            IndexSpec(SyncTarget.SYNC_ID, SmartStore.Type.integer),
            IndexSpec(ACCOUNT_ID, SmartStore.Type.string)
        )
        smartStore.registerSoup(CONTACTS_SOUP, contactsIndexSpecs)
    }

    open fun dropContactsSoup() {
        smartStore.dropSoup(CONTACTS_SOUP)
    }

    open fun deleteSyncs() {
        smartStore.clearSoup(SyncState.SYNCS_SOUP)
    }

    open fun deleteGlobalSyncs() {
        globalSmartStore.clearSoup(SyncState.SYNCS_SOUP)
    }

    open fun createAccountsLocally(names: Array<String>): Array<JSONObject> {
        return createAccountsLocally(names, null)
    }

    open fun createAccountsLocally(names: Array<String>, mutator: Mutator?): Array<JSONObject> {
        val createdAccounts = Array(names.size) { JSONObject() }
        val attributes = JSONObject()
        attributes.put(TYPE, Constants.ACCOUNT)
        for (i in names.indices) {
            val name = names[i]
            var account = JSONObject()
            account.put(Constants.ID, SyncTarget.createLocalId())
            account.put(Constants.NAME, name)
            account.put(Constants.DESCRIPTION, "Description_$name")
            account.put(Constants.ATTRIBUTES, attributes)
            account.put(SyncTarget.LOCAL, true)
            account.put(SyncTarget.LOCALLY_CREATED, true)
            account.put(SyncTarget.LOCALLY_DELETED, false)
            account.put(SyncTarget.LOCALLY_UPDATED, false)
            if (mutator != null) {
                account = mutator.mutate(account)
            }
            createdAccounts[i] = smartStore.create(ACCOUNTS_SOUP, account)!!
        }
        return createdAccounts
    }

    open fun tryCleanResyncGhosts(syncId: Long): Boolean {
        val queue = ArrayBlockingQueue<Boolean>(1)
        syncManager.cleanResyncGhosts(syncId, object : SyncManager.CleanResyncGhostsCallback {
            override fun onSuccess(numRecords: Int) {
                queue.offer(true)
            }
            override fun onError(e: Exception?) {
                queue.offer(false)
            }
        })
        return queue.take()
    }

    open fun trySyncDown(mergeMode: SyncState.MergeMode, target: SyncDownTarget, soupName: String, totalSize: Int, numberFetches: Int): Long {
        return trySyncDown(mergeMode, target, soupName, totalSize, numberFetches, null)
    }

    open fun trySyncDown(mergeMode: SyncState.MergeMode, target: SyncDownTarget, soupName: String, totalSize: Int, numberFetches: Int, syncName: String?): Long {
        val options = SyncOptions.optionsForSyncDown(mergeMode)
        val sync = SyncState.createSyncDown(smartStore, target, options, soupName, syncName)
        val syncId = sync.id
        checkStatus(sync, SyncState.Type.syncDown, syncId, target, options, SyncState.Status.NEW, 0, -1)

        val queue = SyncUpdateCallbackQueue(syncId)
        syncManager.runSync(sync, queue)

        checkStatus(queue.getNextSyncUpdate(syncId), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 0, -1)
        if (totalSize != TOTAL_SIZE_UNKNOWN) {
            for (i in 0 until numberFetches) {
                checkStatus(queue.getNextSyncUpdate(syncId), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, i * 100 / numberFetches, totalSize)
            }
            checkStatus(queue.getNextSyncUpdate(syncId), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.DONE, 100, totalSize)
        } else {
            checkStatus(queue.getNextSyncUpdate(syncId), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.RUNNING, 0)
            checkStatus(queue.getNextSyncUpdate(syncId), SyncState.Type.syncDown, syncId, target, options, SyncState.Status.DONE, 100)
        }
        return syncId
    }

    open fun checkDbDeleted(soupName: String, ids: Array<String>, idField: String) {
        val smartStoreQuery = QuerySpec.buildSmartQuerySpec("SELECT {$soupName:_soup} FROM {$soupName} WHERE {$soupName:$idField} IN ${makeInClause(ids)}", ids.size)
        val records = smartStore.query(smartStoreQuery, 0)
        Assert.assertEquals("No records should have been returned from smartstore", 0, records.length())
    }

    open fun checkDbExist(soupName: String, ids: Array<String>, idField: String) {
        val smartStoreQuery = QuerySpec.buildSmartQuerySpec("SELECT {$soupName:_soup} FROM {$soupName} WHERE {$soupName:$idField} IN ${makeInClause(ids)}", ids.size)
        val records = smartStore.query(smartStoreQuery, 0)
        Assert.assertEquals("All records should have been returned from smartstore", ids.size, records.length())
    }

    open fun checkDbRelationships(childrenIds: Collection<String>, expectedParentId: String, soupName: String, idFieldName: String, parentIdFieldName: String) {
        val smartStoreQuery = QuerySpec.buildSmartQuerySpec("SELECT {$soupName:_soup} FROM {$soupName} WHERE {$soupName:$idFieldName} IN ${makeInClause(childrenIds)}", childrenIds.size)
        val rows = smartStore.query(smartStoreQuery, 0)
        Assert.assertEquals("All records should have been returned from smartstore", childrenIds.size, rows.length())
        for (i in 0 until rows.length()) {
            val childRecord = rows.getJSONArray(i).getJSONObject(0)
            Assert.assertEquals("Wrong parent id", expectedParentId, childRecord.getString(parentIdFieldName))
        }
    }

    open fun makeInClause(values: Array<String>): String {
        return makeInClause(values.toList())
    }

    open fun makeInClause(values: Collection<String>): String {
        return "('" + TextUtils.join("', '", values) + "')"
    }

    open fun trySyncDown(mergeMode: SyncState.MergeMode, target: SyncDownTarget, soupName: String): Long {
        return trySyncDown(mergeMode, target, soupName, TOTAL_SIZE_UNKNOWN, 1)
    }

    open fun checkStatus(sync: SyncState, expectedType: SyncState.Type, expectedId: Long, expectedTarget: SyncTarget?, expectedOptions: SyncOptions?, expectedStatus: SyncState.Status, expectedProgress: Int, expectedTotalSize: Int) {
        Assert.assertEquals("Wrong type", expectedType, sync.type)
        Assert.assertEquals("Wrong id", expectedId, sync.id)
        JSONTestHelper.assertSameJSON("Wrong target", expectedTarget?.asJSON(), sync.target?.asJSON())
        JSONTestHelper.assertSameJSON("Wrong options", expectedOptions?.asJSON(), sync.options?.asJSON())
        Assert.assertEquals("Wrong status", expectedStatus, sync.status)
        Assert.assertEquals("Wrong progress", expectedProgress, sync.progress)
        if (expectedTotalSize != TOTAL_SIZE_UNKNOWN) {
            Assert.assertEquals("Wrong total size", expectedTotalSize, sync.totalSize)
        }
        if (sync.status != SyncState.Status.NEW) {
            Assert.assertTrue("Wrong start time", sync.getStartTime() > 0)
        }
        if (sync.status == SyncState.Status.DONE || sync.status == SyncState.Status.FAILED) {
            Assert.assertTrue("Wrong end time", sync.getEndTime() > 0)
        }
    }

    open fun checkStatus(sync: SyncState, expectedType: SyncState.Type, expectedId: Long, expectedTarget: SyncTarget?, expectedOptions: SyncOptions?, expectedStatus: SyncState.Status, expectedProgress: Int) {
        checkStatus(sync, expectedType, expectedId, expectedTarget, expectedOptions, expectedStatus, expectedProgress, TOTAL_SIZE_UNKNOWN)
    }

    open fun checkDb(expectedIdToFields: Map<String, Map<String, Any>>, soupName: String) {
        val sql = String.format("SELECT {%s:_soup} FROM {%s} WHERE {%s:Id} IN %s", soupName, soupName, soupName, makeInClause(expectedIdToFields.keys))
        val smartStoreQuery = QuerySpec.buildSmartQuerySpec(sql, Int.MAX_VALUE)
        val rows = smartStore.query(smartStoreQuery, 0)
        for (i in 0 until rows.length()) {
            val recordFromDb = rows.getJSONArray(i).getJSONObject(0)
            val recordId = recordFromDb.getString("Id")
            val expectedFields = expectedIdToFields[recordId]!!
            for (fieldName in expectedFields.keys) {
                Assert.assertEquals(
                    String.format("Wrong data in db for field %s on record %s", fieldName, recordId),
                    expectedFields[fieldName].toString(), recordFromDb.get(fieldName).toString()
                )
            }
        }
    }

    open fun makeRemoteChanges(idToFields: Map<String, Map<String, Any>>, sObjectType: String): Map<String, Map<String, Any>> {
        val allIds = idToFields.keys.toTypedArray()
        Arrays.sort(allIds)
        val idsToUpdate = arrayOf(allIds[0], allIds[2])
        return makeRemoteChanges(idToFields, sObjectType, idsToUpdate)
    }

    open fun makeRemoteChanges(idToFields: Map<String, Map<String, Any>>, sObjectType: String, idsToUpdate: Array<String>): Map<String, Map<String, Any>> {
        val idToFieldsUpdated = prepareSomeChanges(idToFields, idsToUpdate, REMOTELY_UPDATED)
        Thread.sleep(1000)
        updateRecordsOnServer(idToFieldsUpdated, sObjectType)
        return idToFieldsUpdated
    }

    open fun prepareSomeChanges(idToFields: Map<String, Map<String, Any>>, idsToUpdate: Array<String>, suffix: String): Map<String, Map<String, Any>> {
        val idToFieldsUpdated = HashMap<String, Map<String, Any>>()
        for (idToUpdate in idsToUpdate) {
            idToFieldsUpdated[idToUpdate] = updatedFields(idToFields[idToUpdate]!!, suffix)
        }
        return idToFieldsUpdated
    }

    open fun updatedFields(fields: Map<String, Any>, suffix: String): Map<String, Any> {
        val fieldNamesUpdatable = HashSet(listOf(Constants.NAME, Constants.DESCRIPTION, Constants.LAST_NAME))
        val updatedFields = HashMap<String, Any>()
        for (fieldName in fields.keys) {
            if (fieldNamesUpdatable.contains(fieldName)) {
                updatedFields[fieldName] = fields[fieldName].toString() + suffix
            }
        }
        return updatedFields
    }

    open fun updateRecordsLocally(idToFieldsLocallyUpdated: Map<String, Map<String, Any>>, soupName: String) {
        for (id in idToFieldsLocallyUpdated.keys) {
            val updatedFields = idToFieldsLocallyUpdated[id]!!
            val record = smartStore.retrieve(soupName, smartStore.lookupSoupEntryId(soupName, Constants.ID, id)).getJSONObject(0)
            for (fieldName in updatedFields.keys) {
                record.put(fieldName, updatedFields[fieldName])
            }
            record.put(SyncTarget.LOCAL, true)
            record.put(SyncTarget.LOCALLY_CREATED, false)
            record.put(SyncTarget.LOCALLY_DELETED, false)
            record.put(SyncTarget.LOCALLY_UPDATED, true)
            smartStore.upsert(soupName, record)
        }
    }

    open fun makeLocalChanges(idToFields: Map<String, Map<String, Any>>, soupName: String): Map<String, Map<String, Any>> {
        val allIds = idToFields.keys.toTypedArray()
        Arrays.sort(allIds)
        val idsToUpdate = arrayOf(allIds[0], allIds[1], allIds[2])
        return makeLocalChanges(idToFields, soupName, idsToUpdate)
    }

    open fun makeLocalChanges(idToFields: Map<String, Map<String, Any>>, soupName: String, idsToUpdate: Array<String>): Map<String, Map<String, Any>> {
        val idToFieldsUpdated = prepareSomeChanges(idToFields, idsToUpdate, LOCALLY_UPDATED)
        updateRecordsLocally(idToFieldsUpdated, soupName)
        return idToFieldsUpdated
    }

    open fun checkDbStateFlags(ids: Collection<String>, expectLocallyCreated: Boolean, expectLocallyUpdated: Boolean, expectLocallyDeleted: Boolean, soupName: String) {
        val expectDirty = expectLocallyCreated || expectLocallyUpdated || expectLocallyDeleted
        val smartStoreQuery = QuerySpec.buildSmartQuerySpec(String.format("SELECT {%s:_soup} FROM {%s} WHERE {%s:Id} IN %s", soupName, soupName, soupName, makeInClause(ids)), ids.size)
        val accountsFromDb = smartStore.query(smartStoreQuery, 0)
        for (i in 0 until accountsFromDb.length()) {
            val row = accountsFromDb.getJSONArray(i)
            val soupElt = row.getJSONObject(0)
            val id = soupElt.getString(Constants.ID)
            Assert.assertEquals("Wrong local flag", expectDirty, soupElt.getBoolean(SyncTarget.LOCAL))
            Assert.assertEquals("Wrong local flag", expectLocallyCreated, soupElt.getBoolean(SyncTarget.LOCALLY_CREATED))
            Assert.assertEquals("Id was not updated", expectLocallyCreated, id.startsWith(SyncTarget.LOCAL_ID_PREFIX))
            Assert.assertEquals("Wrong local flag", expectLocallyUpdated, soupElt.getBoolean(SyncTarget.LOCALLY_UPDATED))
            Assert.assertEquals("Wrong local flag", expectLocallyDeleted, soupElt.getBoolean(SyncTarget.LOCALLY_DELETED))
            if (!expectDirty) {
                Assert.assertTrue("Last error should be empty", TextUtils.isEmpty(JSONObjectHelper.optString(soupElt, SyncTarget.LAST_ERROR)))
            }
        }
    }

    open fun checkDbSyncIdField(ids: Array<String>, syncId: Long, soupName: String) {
        val smartStoreQuery = QuerySpec.buildSmartQuerySpec(String.format("SELECT {%s:_soup} FROM {%s} WHERE {%s:Id} IN %s", soupName, soupName, soupName, makeInClause(ids)), ids.size)
        val accountsFromDb = smartStore.query(smartStoreQuery, 0)
        for (i in 0 until accountsFromDb.length()) {
            val row = accountsFromDb.getJSONArray(i)
            val soupElt = row.getJSONObject(0)
            Assert.assertEquals("Wrong sync id", syncId, soupElt.getLong(SyncTarget.SYNC_ID))
        }
    }

    open fun checkDbLastErrorField(ids: Array<String>, lastErrorSubString: String, soupName: String) {
        val smartStoreQuery = QuerySpec.buildSmartQuerySpec(String.format("SELECT {%s:_soup} FROM {%s} WHERE {%s:Id} IN %s", soupName, soupName, soupName, makeInClause(ids)), ids.size)
        val accountsFromDb = smartStore.query(smartStoreQuery, 0)
        for (i in 0 until accountsFromDb.length()) {
            val row = accountsFromDb.getJSONArray(i)
            val soupElt = row.getJSONObject(0)
            Assert.assertTrue("Wrong last error", soupElt.getString(SyncTarget.LAST_ERROR).contains(lastErrorSubString))
        }
    }

    open fun checkServer(idToFields: Map<String, Map<String, Any>>, sObjectType: String) {
        val fieldNames = idToFields[idToFields.keys.toTypedArray()[0]]!!.keys.toTypedArray()
        val soql = String.format("SELECT %s, %s FROM %s WHERE %s IN %s", Constants.ID, TextUtils.join(",", fieldNames), sObjectType, Constants.ID, makeInClause(idToFields.keys))
        val request = RestRequest.getRequestForQuery(ApiVersionStrings.getVersionNumber(targetContext), soql)
        val response = restClient.sendSync(request)
        val records = response.asJSONObject().getJSONArray(RECORDS)
        Assert.assertEquals("Wrong number of records", idToFields.size, records.length())
        for (i in 0 until records.length()) {
            val row = records.getJSONObject(i)
            val expectedFields = idToFields[row.get(Constants.ID)]!!
            for (fieldName in fieldNames) {
                Assert.assertEquals("Wrong value for field: $fieldName", expectedFields[fieldName], JSONObjectHelper.opt(row, fieldName))
            }
        }
    }

    open fun checkServerDeleted(ids: Array<String>, sObjectType: String) {
        val soql = String.format("SELECT %s FROM %s WHERE %s IN %s", Constants.ID, sObjectType, Constants.ID, makeInClause(ids))
        val request = RestRequest.getRequestForQuery(ApiVersionStrings.getVersionNumber(targetContext), soql)
        val response = restClient.sendSync(request)
        val records = response.asJSONObject().getJSONArray(RECORDS)
        Assert.assertEquals("No accounts should have been returned from server", 0, records.length())
    }

    open fun trySyncUp(target: SyncUpTarget, numberChanges: Int, mergeMode: SyncState.MergeMode) {
        trySyncUp(target, numberChanges, mergeMode, false)
    }

    open fun trySyncUp(target: SyncUpTarget, numberChanges: Int, mergeMode: SyncState.MergeMode, expectSyncFailure: Boolean) {
        val options = SyncOptions.optionsForSyncUp(listOf(Constants.NAME, Constants.DESCRIPTION), mergeMode)
        trySyncUp(target, numberChanges, options, expectSyncFailure)
    }

    open fun trySyncUp(target: SyncUpTarget, numberChanges: Int, options: SyncOptions, expectSyncFailure: Boolean) {
        val sync = SyncState.createSyncUp(smartStore, target, options, ACCOUNTS_SOUP, null)
        val syncId = sync.id
        checkStatus(sync, SyncState.Type.syncUp, syncId, target, options, SyncState.Status.NEW, 0, -1)

        val queue = SyncUpdateCallbackQueue(syncId)
        syncManager.runSync(sync, queue)

        checkStatus(queue.getNextSyncUpdate(syncId), SyncState.Type.syncUp, syncId, target, options, SyncState.Status.RUNNING, 0, -1)
        checkStatus(queue.getNextSyncUpdate(syncId), SyncState.Type.syncUp, syncId, target, options, SyncState.Status.RUNNING, 0, numberChanges)
        if (expectSyncFailure) {
            checkStatus(queue.getNextSyncUpdate(syncId), SyncState.Type.syncUp, syncId, target, options, SyncState.Status.FAILED, 0, numberChanges)
        } else {
            for (i in 1 until numberChanges) {
                checkStatus(queue.getNextSyncUpdate(syncId), SyncState.Type.syncUp, syncId, target, options, SyncState.Status.RUNNING, i * 100 / numberChanges, numberChanges)
            }
            checkStatus(queue.getNextSyncUpdate(syncId), SyncState.Type.syncUp, syncId, target, options, SyncState.Status.DONE, 100, numberChanges)
        }
    }

    open fun getNamesFromIdToFields(idToFields: Map<String, Map<String, Any>>): Array<String> {
        val names = Array(idToFields.size) { "" }
        var i = 0
        for (id in idToFields.keys) {
            names[i] = idToFields[id]!![Constants.NAME] as String
            i++
        }
        return names
    }

    open fun getIdToFieldsByName(soupName: String, fieldNames: Array<String>, nameField: String, names: Array<String>): Map<String, Map<String, Any>> {
        val smartStoreQuery = QuerySpec.buildSmartQuerySpec(String.format("SELECT {%s:_soup} FROM {%s} WHERE {%s:%s} IN %s", soupName, soupName, soupName, nameField, makeInClause(names)), names.size)
        val recordsFromDb = smartStore.query(smartStoreQuery, 0)
        val idToFields = HashMap<String, Map<String, Any>>()
        for (i in 0 until recordsFromDb.length()) {
            val row = recordsFromDb.getJSONArray(i)
            val soupElt = row.getJSONObject(0)
            val id = soupElt.getString(Constants.ID)
            val fields = HashMap<String, Any>()
            for (fieldName in fieldNames) {
                fields[fieldName] = soupElt.get(fieldName)
            }
            idToFields[id] = fields
        }
        return idToFields
    }

    open fun deleteRecordsLocally(soupName: String, vararg ids: String) {
        for (id in ids) {
            val record = smartStore.retrieve(soupName, smartStore.lookupSoupEntryId(soupName, Constants.ID, id)).getJSONObject(0)
            record.put(SyncTarget.LOCAL, true)
            record.put(SyncTarget.LOCALLY_CREATED, record.getBoolean(SyncTarget.LOCALLY_CREATED))
            record.put(SyncTarget.LOCALLY_DELETED, true)
            record.put(SyncTarget.LOCALLY_UPDATED, record.getBoolean(SyncTarget.LOCALLY_UPDATED))
            smartStore.upsert(soupName, record)
        }
    }

    open fun updateRecordOnServer(objectType: String, id: String, fields: Map<String, Any>): Map<String, Map<String, Any>> {
        val idToFieldsRemotelyUpdated = HashMap<String, Map<String, Any>>()
        val updatedFields = updatedFields(fields, REMOTELY_UPDATED)
        idToFieldsRemotelyUpdated[id] = updatedFields
        updateRecordsOnServer(idToFieldsRemotelyUpdated, objectType)
        return idToFieldsRemotelyUpdated
    }

    open fun updateRecordLocally(soupName: String, id: String, fields: Map<String, Any>): Map<String, Map<String, Any>> {
        return updateRecordLocally(soupName, id, fields, LOCALLY_UPDATED)
    }

    open fun updateRecordLocally(soupName: String, id: String, fields: Map<String, Any>, suffix: String): Map<String, Map<String, Any>> {
        val idToFieldsLocallyUpdated = HashMap<String, Map<String, Any>>()
        val updatedFields = updatedFields(fields, suffix)
        idToFieldsLocallyUpdated[id] = updatedFields
        updateRecordsLocally(idToFieldsLocallyUpdated, soupName)
        return idToFieldsLocallyUpdated
    }

    open fun createFieldsMap(fieldName: String, fieldValue: String): Map<String, Any> {
        val fields = HashMap<String, Any>()
        fields[fieldName] = fieldValue
        return fields
    }

    open fun createFieldsMapFromNameDescription(name: String?, description: String?): Map<String, Any> {
        val fields = HashMap<String, Any>()
        if (name != null) fields[Constants.NAME] = name
        if (description != null) fields[Constants.DESCRIPTION] = description
        return fields
    }

    open fun createAccountsAndContactsLocally(names: Array<String>, numberOfContactsPerAccount: Int): Map<JSONObject, Array<JSONObject>> {
        val accounts = createAccountsLocally(names)
        val accountIds = JSONObjectHelper.pluck<String>(accounts, Constants.ID).toTypedArray()
        val accountIdsToContacts = createContactsForAccountsLocally(numberOfContactsPerAccount, *accountIds)
        val accountToContacts = HashMap<JSONObject, Array<JSONObject>>()
        for (account in accounts) {
            accountToContacts[account] = accountIdsToContacts[account.getString(Constants.ID)]!!
        }
        return accountToContacts
    }

    open fun createContactsForAccountsLocally(numberOfContactsPerAccount: Int, vararg accountIds: String): Map<String, Array<JSONObject>> {
        val accountIdToContacts = HashMap<String, Array<JSONObject>>()
        val attributes = JSONObject()
        attributes.put(TYPE, Constants.CONTACT)
        for (accountId in accountIds) {
            val contacts = Array(numberOfContactsPerAccount) { JSONObject() }
            for (i in 0 until numberOfContactsPerAccount) {
                val contact = JSONObject()
                contact.put(Constants.ID, SyncTarget.createLocalId())
                contact.put(Constants.LAST_NAME, createRecordName(Constants.CONTACT))
                contact.put(Constants.ATTRIBUTES, attributes)
                contact.put(SyncTarget.LOCAL, true)
                contact.put(SyncTarget.LOCALLY_CREATED, true)
                contact.put(SyncTarget.LOCALLY_DELETED, false)
                contact.put(SyncTarget.LOCALLY_UPDATED, false)
                contact.put(ACCOUNT_ID, accountId)
                contacts[i] = smartStore.create(CONTACTS_SOUP, contact)!!
            }
            accountIdToContacts[accountId] = contacts
        }
        return accountIdToContacts
    }

    /**
     * Interface used to customize json object
     */
    interface Mutator {
        fun mutate(record: JSONObject): JSONObject
    }
}
