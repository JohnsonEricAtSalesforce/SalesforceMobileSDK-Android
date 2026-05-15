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
import com.salesforce.androidsdk.rest.RestResponse
import com.salesforce.androidsdk.smartstore.store.IndexSpec
import com.salesforce.androidsdk.smartstore.store.QuerySpec
import com.salesforce.androidsdk.smartstore.store.SmartStore
import com.salesforce.androidsdk.util.JSONObjectHelper
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert
import java.io.IOException
import java.util.Arrays
import java.util.concurrent.ArrayBlockingQueue

/**
 * Abstract super class for all SyncManager test classes.
 */
abstract class SyncManagerTestCase : ManagerTestCase() {

    companion object {
        internal const val TYPE = "type"
        internal const val RECORDS = "records"
        internal const val ACCOUNTS_SOUP = "accounts"
        internal const val TOTAL_SIZE_UNKNOWN = -2
        internal const val REMOTELY_UPDATED = "_r_upd"
        internal const val LOCALLY_UPDATED = "_l_upd"
        internal const val CONTACTS_SOUP = "contacts"
        internal const val ACCOUNT_ID = "AccountId"
    }

    @Throws(Exception::class)
    override fun tearDown() {
        try {
            deleteSyncs()
        } catch (e: UninitializedPropertyAccessException) {
            // smartStore not initialized - setUp failed before this point
        }
        try {
            deleteGlobalSyncs()
        } catch (e: UninitializedPropertyAccessException) {
            // globalSmartStore not initialized - setUp failed before this point
        }
        super.tearDown()
    }

    /**
     * Create soup for accounts
     */
    protected fun createAccountsSoup() {
        createAccountsSoup(ACCOUNTS_SOUP)
    }

    protected fun createAccountsSoup(soupName: String) {
        val indexSpecs = arrayOf(
            IndexSpec(Constants.ID, SmartStore.Type.string),
            IndexSpec(Constants.NAME, SmartStore.Type.string),
            IndexSpec(Constants.DESCRIPTION, SmartStore.Type.string),
            IndexSpec(SyncTarget.LOCAL, SmartStore.Type.string),
            IndexSpec(SyncTarget.SYNC_ID, SmartStore.Type.integer)
        )
        smartStore.registerSoup(soupName, indexSpecs)
    }

    /**
     * Drop soup for accounts
     */
    protected fun dropAccountsSoup() {
        dropAccountsSoup(ACCOUNTS_SOUP)
    }

    protected fun dropAccountsSoup(soupName: String) {
        smartStore.dropSoup(soupName)
    }

    protected fun createContactsSoup() {
        val contactsIndexSpecs = arrayOf(
            IndexSpec(Constants.ID, SmartStore.Type.string),
            IndexSpec(Constants.LAST_NAME, SmartStore.Type.string),
            IndexSpec(SyncTarget.LOCAL, SmartStore.Type.string),
            IndexSpec(SyncTarget.SYNC_ID, SmartStore.Type.integer),
            IndexSpec(ACCOUNT_ID, SmartStore.Type.string)
        )
        smartStore.registerSoup(CONTACTS_SOUP, contactsIndexSpecs)
    }

    protected fun dropContactsSoup() {
        smartStore.dropSoup(CONTACTS_SOUP)
    }

    /**
     * Delete all syncs in syncs_soup
     */
    protected fun deleteSyncs() {
        smartStore.clearSoup(SyncState.SYNCS_SOUP)
    }

    /**
     * Delete all syncs in syncs_soup
     */
    protected fun deleteGlobalSyncs() {
        globalSmartStore.clearSoup(SyncState.SYNCS_SOUP)
    }

    /**
     * Create accounts locally
     *
     * @param names
     * @return created accounts records
     * @throws JSONException
     */
    @Throws(JSONException::class)
    protected fun createAccountsLocally(names: Array<String>): Array<JSONObject> {
        return createAccountsLocally(names, null)
    }

    /**
     * Create accounts locally
     * @param names
     * @param mutator
     * @return created accounts records
     * @throws JSONException
     */
    @Throws(JSONException::class)
    protected fun createAccountsLocally(names: Array<String>, mutator: Mutator?): Array<JSONObject> {
        val createdAccounts = arrayOfNulls<JSONObject>(names.size)
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
            createdAccounts[i] = smartStore.create(ACCOUNTS_SOUP, account)
        }
        @Suppress("UNCHECKED_CAST")
        return createdAccounts as Array<JSONObject>
    }

    @Throws(JSONException::class, InterruptedException::class)
    protected fun tryCleanResyncGhosts(syncId: Long): Boolean {
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

    /**
     * Sync down helper.
     *
     * @param mergeMode
     * @param target
     * @param soupName
     * @param totalSize
     * @param numberFetches
     * @return
     * @throws JSONException
     */
    @Throws(JSONException::class)
    protected fun trySyncDown(
        mergeMode: SyncState.MergeMode,
        target: SyncDownTarget,
        soupName: String,
        totalSize: Int,
        numberFetches: Int
    ): Long {
        return trySyncDown(mergeMode, target, soupName, totalSize, numberFetches, null)
    }

    /**
     * Sync down helper.
     *
     * @param mergeMode     Merge mode.
     * @param target        Sync down target.
     * @param soupName      Soup name.
     * @param totalSize     Expected total size.
     * @param numberFetches Expected number of fetches.
     * @param syncName      Name for sync or null.
     * @return Sync ID.
     */
    @Throws(JSONException::class)
    protected fun trySyncDown(
        mergeMode: SyncState.MergeMode,
        target: SyncDownTarget,
        soupName: String,
        totalSize: Int,
        numberFetches: Int,
        syncName: String?
    ): Long {
        val options = SyncOptions.optionsForSyncDown(mergeMode)
        val sync = SyncState.createSyncDown(smartStore, target, options, soupName, syncName)
        val syncId = sync.id
        checkStatus(
            sync,
            SyncState.Type.syncDown,
            syncId,
            target,
            options,
            SyncState.Status.NEW,
            0,
            -1
        )

        // Runs sync.
        val queue = SyncUpdateCallbackQueue(syncId)
        syncManager.runSync(sync, queue)

        // Checks status updates.
        checkStatus(
            queue.getNextSyncUpdate(syncId),
            SyncState.Type.syncDown,
            syncId,
            target,
            options,
            SyncState.Status.RUNNING,
            0,
            -1
        )
        if (totalSize != TOTAL_SIZE_UNKNOWN) {
            for (i in 0 until numberFetches) {
                checkStatus(
                    queue.getNextSyncUpdate(syncId),
                    SyncState.Type.syncDown,
                    syncId,
                    target,
                    options,
                    SyncState.Status.RUNNING,
                    i * 100 / numberFetches,
                    totalSize
                )
            }
            checkStatus(
                queue.getNextSyncUpdate(syncId),
                SyncState.Type.syncDown,
                syncId,
                target,
                options,
                SyncState.Status.DONE,
                100,
                totalSize
            )
        } else {
            checkStatus(
                queue.getNextSyncUpdate(syncId),
                SyncState.Type.syncDown,
                syncId,
                target,
                options,
                SyncState.Status.RUNNING,
                0
            )
            checkStatus(
                queue.getNextSyncUpdate(syncId),
                SyncState.Type.syncDown,
                syncId,
                target,
                options,
                SyncState.Status.DONE,
                100
            )
        }
        return syncId
    }

    /**
     * Check that records were deleted from db
     *
     * @param soupName
     * @param ids
     * @param idField
     * @throws JSONException
     */
    @Throws(JSONException::class)
    protected fun checkDbDeleted(soupName: String, ids: Array<String>, idField: String) {
        val smartStoreQuery = QuerySpec.buildSmartQuerySpec(
            "SELECT {$soupName:_soup} FROM {$soupName} WHERE {$soupName:$idField} IN ${makeInClause(ids)}",
            ids.size
        )
        val records = smartStore.query(smartStoreQuery, 0)
        Assert.assertEquals("No records should have been returned from smartstore", 0, records.length())
    }

    /**
     * Check that records exist in db
     *
     * @param soupName
     * @param ids
     * @param idField
     * @throws JSONException
     */
    @Throws(JSONException::class)
    protected fun checkDbExist(soupName: String, ids: Array<String>, idField: String) {
        val smartStoreQuery = QuerySpec.buildSmartQuerySpec(
            "SELECT {$soupName:_soup} FROM {$soupName} WHERE {$soupName:$idField} IN ${makeInClause(ids)}",
            ids.size
        )
        val records = smartStore.query(smartStoreQuery, 0)
        Assert.assertEquals("All records should have been returned from smartstore", ids.size, records.length())
    }

    /**
     * Check relationships field of children
     * @param childrenIds
     * @param expectedParentId
     * @param soupName
     * @param idFieldName
     * @param parentIdFieldName
     */
    @Throws(JSONException::class)
    protected fun checkDbRelationships(
        childrenIds: Collection<String>,
        expectedParentId: String,
        soupName: String,
        idFieldName: String,
        parentIdFieldName: String
    ) {
        val smartStoreQuery = QuerySpec.buildSmartQuerySpec(
            "SELECT {$soupName:_soup} FROM {$soupName} WHERE {$soupName:$idFieldName} IN ${makeInClause(childrenIds)}",
            childrenIds.size
        )
        val rows = smartStore.query(smartStoreQuery, 0)
        Assert.assertEquals(
            "All records should have been returned from smartstore",
            childrenIds.size,
            rows.length()
        )
        for (i in 0 until rows.length()) {
            val childRecord = rows.getJSONArray(i).getJSONObject(0)
            Assert.assertEquals("Wrong parent id", expectedParentId, childRecord.getString(parentIdFieldName))
        }
    }

    protected fun makeInClause(values: Array<String>): String {
        return makeInClause(values.asList())
    }

    protected fun makeInClause(values: Collection<String>): String {
        return "('" + TextUtils.join("', '", values) + "')"
    }

    @Throws(JSONException::class)
    protected fun trySyncDown(
        mergeMode: SyncState.MergeMode,
        target: SyncDownTarget,
        soupName: String
    ): Long {
        return trySyncDown(mergeMode, target, soupName, TOTAL_SIZE_UNKNOWN, 1)
    }

    /**
     * Helper method to check sync state
     *
     * @param sync
     * @param expectedType
     * @param expectedId
     * @param expectedTarget
     * @param expectedOptions
     * @param expectedStatus
     * @param expectedProgress
     * @throws JSONException
     */
    @Throws(JSONException::class)
    protected fun checkStatus(
        sync: SyncState,
        expectedType: SyncState.Type,
        expectedId: Long,
        expectedTarget: SyncTarget?,
        expectedOptions: SyncOptions?,
        expectedStatus: SyncState.Status,
        expectedProgress: Int,
        expectedTotalSize: Int
    ) {
        Assert.assertEquals("Wrong type", expectedType, sync.type)
        Assert.assertEquals("Wrong id", expectedId, sync.id)
        JSONTestHelper.assertSameJSON(
            "Wrong target",
            expectedTarget?.asJSON(),
            sync.target?.asJSON()
        )
        val expectedOptionsJson = expectedOptions?.asJSON()
        val actualOptionsJson = sync.options?.asJSON()
        // After Kotlin migration, null fieldlists become empty lists and serialize as "fieldlist":[]
        // Normalize expected to include empty fieldlist if actual has one and expected doesn't
        if (expectedOptionsJson != null && actualOptionsJson != null
            && !expectedOptionsJson.has(SyncOptions.FIELDLIST)
            && actualOptionsJson.has(SyncOptions.FIELDLIST)
            && actualOptionsJson.optJSONArray(SyncOptions.FIELDLIST)?.length() == 0) {
            expectedOptionsJson.put(SyncOptions.FIELDLIST, JSONArray())
        }
        JSONTestHelper.assertSameJSON(
            "Wrong options",
            expectedOptionsJson,
            actualOptionsJson
        )
        Assert.assertEquals("Wrong status", expectedStatus, sync.status)
        Assert.assertEquals("Wrong progress", expectedProgress, sync.progress)
        if (expectedTotalSize != TOTAL_SIZE_UNKNOWN) {
            Assert.assertEquals("Wrong total size", expectedTotalSize, sync.totalSize)
        }
        if (sync.status != SyncState.Status.NEW) {
            Assert.assertTrue("Wrong start time", sync.startTime > 0)
        }
        if (sync.status == SyncState.Status.DONE || sync.status == SyncState.Status.FAILED) {
            Assert.assertTrue("Wrong end time", sync.endTime > 0)
        }
    }

    @Throws(JSONException::class)
    protected fun checkStatus(
        sync: SyncState,
        expectedType: SyncState.Type,
        expectedId: Long,
        expectedTarget: SyncTarget?,
        expectedOptions: SyncOptions?,
        expectedStatus: SyncState.Status,
        expectedProgress: Int
    ) {
        checkStatus(sync, expectedType, expectedId, expectedTarget, expectedOptions, expectedStatus, expectedProgress, TOTAL_SIZE_UNKNOWN)
    }

    /**
     * Check records in db
     * @throws JSONException
     * @param expectedIdToFields
     * @param soupName
     */
    @Throws(JSONException::class)
    protected fun checkDb(expectedIdToFields: Map<String, Map<String, Any>>, soupName: String) {
        val sql = String.format(
            "SELECT {%s:_soup} FROM {%s} WHERE {%s:Id} IN %s",
            soupName, soupName, soupName, makeInClause(expectedIdToFields.keys)
        )
        val smartStoreQuery = QuerySpec.buildSmartQuerySpec(sql, Int.MAX_VALUE)
        val rows = smartStore.query(smartStoreQuery, 0)
        for (i in 0 until rows.length()) {
            val recordFromDb = rows.getJSONArray(i).getJSONObject(0)
            val recordId = recordFromDb.getString("Id")
            val expectedFields = expectedIdToFields[recordId]!!
            for (fieldName in expectedFields.keys) {
                Assert.assertEquals(
                    String.format("Wrong data in db for field %s on record %s", fieldName, recordId),
                    expectedFields[fieldName].toString(),
                    recordFromDb.get(fieldName).toString()
                )
            }
        }
    }

    /**
     * Make remote changes
     * @throws JSONException
     * @param idToFields
     * @param sObjectType
     */
    @Throws(Exception::class)
    protected fun makeRemoteChanges(
        idToFields: Map<String, Map<String, Any>>,
        sObjectType: String
    ): Map<String, Map<String, Any>> {
        val allIds = idToFields.keys.toTypedArray()
        Arrays.sort(allIds) // to make the status updates sequence deterministic
        val idsToUpdate = arrayOf(allIds[0], allIds[2])
        return makeRemoteChanges(idToFields, sObjectType, idsToUpdate)
    }

    /**
     * Make remote changes
     * @param idToFields
     * @param sObjectType
     * @param idsToUpdate
     * @return
     * @throws Exception
     */
    @Throws(Exception::class)
    protected fun makeRemoteChanges(
        idToFields: Map<String, Map<String, Any>>,
        sObjectType: String,
        idsToUpdate: Array<String>
    ): Map<String, Map<String, Any>> {
        val idToFieldsUpdated = prepareSomeChanges(idToFields, idsToUpdate, REMOTELY_UPDATED)
        Thread.sleep(1000) // time stamp precision is in seconds
        updateRecordsOnServer(idToFieldsUpdated, sObjectType)
        return idToFieldsUpdated
    }

    /**
     * Helper method to prepare updated maps of field name to field value
     * @param idToFields
     * @param idsToUpdate
     * @param suffix
     * @return
     */
    protected fun prepareSomeChanges(
        idToFields: Map<String, Map<String, Any>>,
        idsToUpdate: Array<String>,
        suffix: String
    ): Map<String, Map<String, Any>> {
        val idToFieldsUpdated = mutableMapOf<String, Map<String, Any>>()
        for (idToUpdate in idsToUpdate) {
            idToFieldsUpdated[idToUpdate] = updatedFields(idToFields[idToUpdate]!!, suffix)
        }
        return idToFieldsUpdated
    }

    /**
     * Helper method to update fields in a map of field name to field value
     * @param fields
     * @param suffix
     * @return
     */
    protected fun updatedFields(fields: Map<String, Any>, suffix: String): Map<String, Any> {
        val fieldNamesUpdatable = setOf(Constants.NAME, Constants.DESCRIPTION, Constants.LAST_NAME)
        val updatedFields = mutableMapOf<String, Any>()
        for (fieldName in fields.keys) {
            if (fieldNamesUpdatable.contains(fieldName)) {
                updatedFields[fieldName] = fields[fieldName].toString() + suffix
            }
        }
        return updatedFields
    }

    /**
     * Update records locally
     * @param idToFieldsLocallyUpdated
     * @param soupName
     * @throws JSONException
     */
    @Throws(JSONException::class)
    protected fun updateRecordsLocally(
        idToFieldsLocallyUpdated: Map<String, Map<String, Any>>,
        soupName: String
    ) {
        for (id in idToFieldsLocallyUpdated.keys) {
            val updatedFields = idToFieldsLocallyUpdated[id]!!
            val record = smartStore.retrieve(
                soupName,
                smartStore.lookupSoupEntryId(soupName, Constants.ID, id)
            ).getJSONObject(0)
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

    /**
     * Make local changes
     * @throws JSONException
     * @param idToFields
     * @param soupName
     */
    @Throws(JSONException::class)
    protected fun makeLocalChanges(
        idToFields: Map<String, Map<String, Any>>,
        soupName: String
    ): Map<String, Map<String, Any>> {
        val allIds = idToFields.keys.toTypedArray()
        Arrays.sort(allIds)
        val idsToUpdate = arrayOf(allIds[0], allIds[1], allIds[2])
        return makeLocalChanges(idToFields, soupName, idsToUpdate)
    }

    /**
     * Make local changes
     * @param idToFields
     * @param soupName
     * @param idsToUpdate
     * @return
     * @throws JSONException
     */
    @Throws(JSONException::class)
    protected fun makeLocalChanges(
        idToFields: Map<String, Map<String, Any>>,
        soupName: String,
        idsToUpdate: Array<String>
    ): Map<String, Map<String, Any>> {
        val idToFieldsUpdated = prepareSomeChanges(idToFields, idsToUpdate, LOCALLY_UPDATED)
        updateRecordsLocally(idToFieldsUpdated, soupName)
        return idToFieldsUpdated
    }

    /**
     * Check records state in db
     * @param ids
     * @param expectLocallyCreated true if records are expected to be marked as locally created
     * @param expectLocallyUpdated true if records are expected to be marked as locally updated
     * @param expectLocallyDeleted true if records are expected to be marked as locally deleted
     * @param soupName
     * @throws JSONException
     */
    @Throws(JSONException::class)
    protected fun checkDbStateFlags(
        ids: Collection<String>,
        expectLocallyCreated: Boolean,
        expectLocallyUpdated: Boolean,
        expectLocallyDeleted: Boolean,
        soupName: String
    ) {
        val expectDirty = expectLocallyCreated || expectLocallyUpdated || expectLocallyDeleted
        val smartStoreQuery = QuerySpec.buildSmartQuerySpec(
            String.format("SELECT {%s:_soup} FROM {%s} WHERE {%s:Id} IN %s", soupName, soupName, soupName, makeInClause(ids)),
            ids.size
        )
        val accountsFromDb = smartStore.query(smartStoreQuery, 0)
        for (i in 0 until accountsFromDb.length()) {
            val row = accountsFromDb.getJSONArray(i)
            val soupElt = row.getJSONObject(0)
            val id = soupElt.getString(Constants.ID)
            Assert.assertEquals("Wrong local flag", expectDirty, soupElt.getBoolean(SyncTarget.LOCAL))
            Assert.assertEquals(
                "Wrong local flag",
                expectLocallyCreated,
                soupElt.getBoolean(SyncTarget.LOCALLY_CREATED)
            )
            Assert.assertEquals(
                "Id was not updated",
                expectLocallyCreated,
                id.startsWith(SyncTarget.LOCAL_ID_PREFIX)
            )
            Assert.assertEquals(
                "Wrong local flag",
                expectLocallyUpdated,
                soupElt.getBoolean(SyncTarget.LOCALLY_UPDATED)
            )
            Assert.assertEquals(
                "Wrong local flag",
                expectLocallyDeleted,
                soupElt.getBoolean(SyncTarget.LOCALLY_DELETED)
            )
            // Last error field should be empty for a clean record
            if (!expectDirty) {
                Assert.assertTrue(
                    "Last error should be empty",
                    TextUtils.isEmpty(JSONObjectHelper.optString(soupElt, SyncTarget.LAST_ERROR))
                )
            }
        }
    }

    /**
     * Check records syncId field in db
     * @param ids
     * @param syncId value expected in __sync_id__ field
     * @param soupName
     * @throws JSONException
     */
    @Throws(JSONException::class)
    protected fun checkDbSyncIdField(ids: Array<String>, syncId: Long, soupName: String) {
        val smartStoreQuery = QuerySpec.buildSmartQuerySpec(
            String.format("SELECT {%s:_soup} FROM {%s} WHERE {%s:Id} IN %s", soupName, soupName, soupName, makeInClause(ids)),
            ids.size
        )
        val accountsFromDb = smartStore.query(smartStoreQuery, 0)
        for (i in 0 until accountsFromDb.length()) {
            val row = accountsFromDb.getJSONArray(i)
            val soupElt = row.getJSONObject(0)
            Assert.assertEquals("Wrong sync id", syncId, soupElt.getInt(SyncTarget.SYNC_ID).toLong())
        }
    }

    /**
     * Check records last error field in db
     * @param ids
     * @param lastErrorSubString value expected within __last_error__ field
     * @param soupName
     * @throws JSONException
     */
    @Throws(JSONException::class)
    protected fun checkDbLastErrorField(ids: Array<String>, lastErrorSubString: String, soupName: String) {
        val smartStoreQuery = QuerySpec.buildSmartQuerySpec(
            String.format("SELECT {%s:_soup} FROM {%s} WHERE {%s:Id} IN %s", soupName, soupName, soupName, makeInClause(ids)),
            ids.size
        )
        val accountsFromDb = smartStore.query(smartStoreQuery, 0)
        for (i in 0 until accountsFromDb.length()) {
            val row = accountsFromDb.getJSONArray(i)
            val soupElt = row.getJSONObject(0)
            Assert.assertTrue(
                "Wrong last error",
                soupElt.getString(SyncTarget.LAST_ERROR).contains(lastErrorSubString)
            )
        }
    }

    /**
     * Check records on server
     * @param idToFields
     * @param sObjectType
     * @throws IOException
     * @throws JSONException
     */
    @Throws(IOException::class, JSONException::class)
    protected fun checkServer(idToFields: Map<String, Map<String, Any>>, sObjectType: String) {
        val fieldNames = idToFields[idToFields.keys.toTypedArray()[0]]!!.keys.toTypedArray()
        val soql = String.format(
            "SELECT %s, %s FROM %s WHERE %s IN %s",
            Constants.ID,
            TextUtils.join(",", fieldNames),
            sObjectType,
            Constants.ID,
            makeInClause(idToFields.keys)
        )
        val request = RestRequest.getRequestForQuery(ApiVersionStrings.getVersionNumber(targetContext), soql)
        val response = restClient.sendSync(request)!!
        val records = response.asJSONObject().getJSONArray(RECORDS)
        Assert.assertEquals("Wrong number of records", idToFields.size, records.length())
        for (i in 0 until records.length()) {
            val row = records.getJSONObject(i)
            val expectedFields = idToFields[row.get(Constants.ID)]!!
            for (fieldName in fieldNames) {
                Assert.assertEquals(
                    "Wrong value for field: $fieldName",
                    expectedFields[fieldName],
                    JSONObjectHelper.opt(row, fieldName)
                )
            }
        }
    }

    /**
     * Check that records were deleted from server
     * @param ids
     * @param sObjectType
     * @throws IOException
     */
    @Throws(IOException::class, JSONException::class)
    protected fun checkServerDeleted(ids: Array<String>, sObjectType: String) {
        val soql = String.format("SELECT %s FROM %s WHERE %s IN %s", Constants.ID, sObjectType, Constants.ID, makeInClause(ids))
        val request = RestRequest.getRequestForQuery(ApiVersionStrings.getVersionNumber(targetContext), soql)
        val response = restClient.sendSync(request)!!
        val records = response.asJSONObject().getJSONArray(RECORDS)
        Assert.assertEquals("No accounts should have been returned from server", 0, records.length())
    }

    /**
     * Sync up helper
     * @oaram target
     * @param numberChanges
     * @param mergeMode
     * @throws JSONException
     */
    @Throws(JSONException::class)
    protected fun trySyncUp(target: SyncUpTarget, numberChanges: Int, mergeMode: SyncState.MergeMode) {
        trySyncUp(target, numberChanges, mergeMode, false)
    }

    /**
     * Sync up helper
     * @param target
     * @param numberChanges
     * @param mergeMode
     * @param expectSyncFailure - if true, we expect the sync to end up in the FAILED state
     * @throws JSONException
     */
    @Throws(JSONException::class)
    protected fun trySyncUp(
        target: SyncUpTarget,
        numberChanges: Int,
        mergeMode: SyncState.MergeMode,
        expectSyncFailure: Boolean
    ) {
        val options = SyncOptions.optionsForSyncUp(listOf(Constants.NAME, Constants.DESCRIPTION), mergeMode)
        trySyncUp(target, numberChanges, options, expectSyncFailure)
    }

    /**
     * Sync up helper
     * @param target
     * @param numberChanges
     * @param options
     * @param expectSyncFailure - if true, we expect the sync to end up in the FAILED state
     * @throws JSONException
     */
    @Throws(JSONException::class)
    protected fun trySyncUp(
        target: SyncUpTarget,
        numberChanges: Int,
        options: SyncOptions,
        expectSyncFailure: Boolean
    ) {
        // Create sync
        val sync = SyncState.createSyncUp(smartStore, target, options, ACCOUNTS_SOUP, null)
        val syncId = sync.id
        checkStatus(sync, SyncState.Type.syncUp, syncId, target, options, SyncState.Status.NEW, 0, -1)

        // Run sync
        val queue = SyncUpdateCallbackQueue(syncId)
        syncManager.runSync(sync, queue)

        // Check status updates
        checkStatus(
            queue.getNextSyncUpdate(syncId),
            SyncState.Type.syncUp,
            syncId,
            target,
            options,
            SyncState.Status.RUNNING,
            0,
            -1
        ) // we get an update right away before getting records to sync
        checkStatus(
            queue.getNextSyncUpdate(syncId),
            SyncState.Type.syncUp,
            syncId,
            target,
            options,
            SyncState.Status.RUNNING,
            0,
            numberChanges
        )
        if (expectSyncFailure) {
            checkStatus(
                queue.getNextSyncUpdate(syncId),
                SyncState.Type.syncUp,
                syncId,
                target,
                options,
                SyncState.Status.FAILED,
                0,
                numberChanges
            )
        } else {
            for (i in 1 until numberChanges) {
                checkStatus(
                    queue.getNextSyncUpdate(syncId),
                    SyncState.Type.syncUp,
                    syncId,
                    target,
                    options,
                    SyncState.Status.RUNNING,
                    i * 100 / numberChanges,
                    numberChanges
                )
            }
            checkStatus(
                queue.getNextSyncUpdate(syncId),
                SyncState.Type.syncUp,
                syncId,
                target,
                options,
                SyncState.Status.DONE,
                100,
                numberChanges
            )
        }
    }

    /**
     * Return array of names
     * @param idToFields
     */
    protected fun getNamesFromIdToFields(idToFields: Map<String, Map<String, Any>>): Array<String> {
        val names = arrayOfNulls<String>(idToFields.size)
        var i = 0
        for (id in idToFields.keys) {
            names[i] = idToFields[id]!![Constants.NAME] as String
            i++
        }
        @Suppress("UNCHECKED_CAST")
        return names as Array<String>
    }

    /**
     * Return map of id to fields given records names
     * @param soupName
     * @param fieldNames
     * @param nameField
     * @param names
     * @throws JSONException
     */
    @Throws(JSONException::class)
    protected fun getIdToFieldsByName(
        soupName: String,
        fieldNames: Array<String>,
        nameField: String,
        names: Array<String>
    ): Map<String, Map<String, Any>> {
        val smartStoreQuery = QuerySpec.buildSmartQuerySpec(
            String.format("SELECT {%s:_soup} FROM {%s} WHERE {%s:%s} IN %s", soupName, soupName, soupName, nameField, makeInClause(names)),
            names.size
        )
        val recordsFromDb = smartStore.query(smartStoreQuery, 0)
        val idToFields = mutableMapOf<String, Map<String, Any>>()
        for (i in 0 until recordsFromDb.length()) {
            val row = recordsFromDb.getJSONArray(i)
            val soupElt = row.getJSONObject(0)
            val id = soupElt.getString(Constants.ID)
            val fields = mutableMapOf<String, Any>()
            for (fieldName in fieldNames) {
                fields[fieldName] = soupElt.get(fieldName)
            }
            idToFields[id] = fields
        }
        return idToFields
    }

    /**
     * Delete records locally
     *
     * @param soupName
     * @param ids
     * @throws JSONException
     */
    @Throws(JSONException::class)
    protected fun deleteRecordsLocally(soupName: String, vararg ids: String) {
        for (id in ids) {
            val record = smartStore.retrieve(
                soupName,
                smartStore.lookupSoupEntryId(soupName, Constants.ID, id)
            ).getJSONObject(0)
            record.put(SyncTarget.LOCAL, true)
            record.put(SyncTarget.LOCALLY_CREATED, record.getBoolean(SyncTarget.LOCALLY_CREATED))
            record.put(SyncTarget.LOCALLY_DELETED, true)
            record.put(SyncTarget.LOCALLY_UPDATED, record.getBoolean(SyncTarget.LOCALLY_UPDATED))
            smartStore.upsert(soupName, record)
        }
    }

    /**
     * Helper method to update a single record on the server
     *
     * @param objectType
     * @param id
     * @param fields
     * @return
     */
    @Throws(Exception::class)
    protected fun updateRecordOnServer(
        objectType: String,
        id: String,
        fields: Map<String, Any>
    ): Map<String, Map<String, Any>> {
        val idToFieldsRemotelyUpdated = mutableMapOf<String, Map<String, Any>>()
        val updatedFields = updatedFields(fields, REMOTELY_UPDATED)
        idToFieldsRemotelyUpdated[id] = updatedFields
        updateRecordsOnServer(idToFieldsRemotelyUpdated, objectType)
        return idToFieldsRemotelyUpdated
    }

    /**
     * Helper method to update a single record locally
     *
     * @param soupName
     * @param id
     * @param fields
     * @return
     * @throws JSONException
     */
    @Throws(JSONException::class)
    protected fun updateRecordLocally(soupName: String, id: String, fields: Map<String, Any>): Map<String, Map<String, Any>> {
        return updateRecordLocally(soupName, id, fields, LOCALLY_UPDATED)
    }

    /**
     * Helper method to update a single record locally by appending the given prefix to the fields
     *
     * @param soupName
     * @param id
     * @param fields
     * @param suffix
     * @return
     * @throws JSONException
     */
    @Throws(JSONException::class)
    protected fun updateRecordLocally(
        soupName: String,
        id: String,
        fields: Map<String, Any>,
        suffix: String
    ): Map<String, Map<String, Any>> {
        val idToFieldsLocallyUpdated = mutableMapOf<String, Map<String, Any>>()
        val updatedFields = updatedFields(fields, suffix)
        idToFieldsLocallyUpdated[id] = updatedFields
        updateRecordsLocally(idToFieldsLocallyUpdated, soupName)
        return idToFieldsLocallyUpdated
    }

    /**
     * Helper for building fields map
     * @param fieldName
     * @param fieldValue
     * @return
     */
    protected fun createFieldsMap(fieldName: String, fieldValue: String): Map<String, Any> {
        val fields = mutableMapOf<String, Any>()
        fields[fieldName] = fieldValue
        return fields
    }

    /**
     * Helper for building fields map
     * @param name
     * @param description
     * @return
     */
    protected fun createFieldsMapFromNameDescription(name: String?, description: String?): Map<String, Any> {
        val fields = mutableMapOf<String, Any>()
        if (name != null) fields[Constants.NAME] = name
        if (description != null) fields[Constants.DESCRIPTION] = description
        return fields
    }

    @Throws(JSONException::class)
    protected fun createAccountsAndContactsLocally(
        names: Array<String>,
        numberOfContactsPerAccount: Int
    ): Map<JSONObject, Array<JSONObject>> {
        val accounts = createAccountsLocally(names)
        val accountIds = com.salesforce.androidsdk.util.JSONObjectHelper.pluck<String>(accounts, Constants.ID).toTypedArray()
        val accountIdsToContacts = createContactsForAccountsLocally(numberOfContactsPerAccount, *accountIds)
        val accountToContacts = mutableMapOf<JSONObject, Array<JSONObject>>()
        for (account in accounts) {
            accountToContacts[account] = accountIdsToContacts[account.getString(Constants.ID)]!!
        }
        return accountToContacts
    }

    @Throws(JSONException::class)
    protected fun createContactsForAccountsLocally(
        numberOfContactsPerAccount: Int,
        vararg accountIds: String
    ): Map<String, Array<JSONObject>> {
        val accountIdToContacts = mutableMapOf<String, Array<JSONObject>>()
        val attributes = JSONObject()
        attributes.put(TYPE, Constants.CONTACT)
        for (accountId in accountIds) {
            val contacts = arrayOfNulls<JSONObject>(numberOfContactsPerAccount)
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
                contacts[i] = smartStore.create(CONTACTS_SOUP, contact)
            }
            @Suppress("UNCHECKED_CAST")
            accountIdToContacts[accountId] = contacts as Array<JSONObject>
        }
        return accountIdToContacts
    }

    /**
     * Class use to customize json object
     */
    interface Mutator {
        @Throws(JSONException::class)
        fun mutate(record: JSONObject): JSONObject
    }
}
