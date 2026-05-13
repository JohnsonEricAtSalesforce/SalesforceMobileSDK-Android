/*
 * Copyright (c) 2019-present, salesforce.com, inc.
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
import androidx.test.filters.SmallTest

import com.salesforce.androidsdk.mobilesync.manager.SyncManagerTestCase
import com.salesforce.androidsdk.mobilesync.util.Constants
import com.salesforce.androidsdk.mobilesync.util.SyncOptions
import com.salesforce.androidsdk.mobilesync.util.SyncState.MergeMode

import org.json.JSONException
import org.json.JSONObject
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

import java.util.ArrayList
import java.util.Arrays
import java.util.HashMap
import java.util.HashSet

/**
 * Test class for SyncUpTarget.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
open class SyncUpTargetTest : SyncManagerTestCase() {

    // Misc
    protected lateinit var idToFields: MutableMap<String, Map<String, Any>>

    @Before
    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()
        createAccountsSoup()
        idToFields = createRecordsOnServerReturnFields(COUNT_TEST_ACCOUNTS, Constants.ACCOUNT, null).toMutableMap()
    }

    @After
    @Throws(Exception::class)
    override fun tearDown() {
        deleteRecordsByIdOnServer(idToFields.keys, Constants.ACCOUNT)
        dropAccountsSoup()
        super.tearDown()
    }

    /**
     * Sync down the test accounts, modify a few, sync up specifying update field list, check smartstore and server afterwards
     */
    @Test
    @Throws(Exception::class)
    fun testSyncUpWithUpdateFieldList() {
        // First sync down
        trySyncDown(MergeMode.OVERWRITE)

        // Update a few entries locally
        val idToFieldsLocallyUpdated = makeLocalChanges(idToFields, ACCOUNTS_SOUP)

        // Sync up with update field list including only name
        trySyncUp(idToFieldsLocallyUpdated.size, MergeMode.OVERWRITE, null, listOf(Constants.NAME))

        // Check that db doesn't show entries as locally modified anymore
        val ids = idToFieldsLocallyUpdated.keys
        checkDbStateFlags(ids, false, false, false, ACCOUNTS_SOUP)

        // Check server - make sure only name was updated
        val idToFieldsExpectedOnServer = HashMap<String, Map<String, Any>>()
        for (id in idToFieldsLocallyUpdated.keys) {
            // Should have modified name but original description
            val expectedFields = HashMap<String, Any>()
            expectedFields[Constants.NAME] = idToFieldsLocallyUpdated[id]!![Constants.NAME]!!
            expectedFields[Constants.DESCRIPTION] = idToFields[id]!![Constants.DESCRIPTION]!!
            idToFieldsExpectedOnServer[id] = expectedFields
        }
        checkServer(idToFieldsExpectedOnServer, Constants.ACCOUNT)
    }

    /**
     * Create accounts locally, sync up specifying create field list, check smartstore and server afterwards
     */
    @Test
    @Throws(Exception::class)
    fun testSyncUpWithCreateFieldList() {
        // Create a few entries locally
        val names = arrayOf(
            createRecordName(Constants.ACCOUNT),
            createRecordName(Constants.ACCOUNT),
            createRecordName(Constants.ACCOUNT)
        )
        createAccountsLocally(names)

        // Sync up with create field list including only name
        trySyncUp(3, MergeMode.OVERWRITE, listOf(Constants.NAME), null)

        // Check that db doesn't show entries as locally created anymore and that they use sfdc id
        val idToFieldsCreated = getIdToFieldsByName(ACCOUNTS_SOUP, arrayOf(Constants.NAME, Constants.DESCRIPTION), Constants.NAME, names)
        checkDbStateFlags(idToFieldsCreated.keys, false, false, false, ACCOUNTS_SOUP)

        // Check server - make sure only name was set
        val idToFieldsExpectedOnServer = HashMap<String, Map<String, Any>>()
        for (id in idToFieldsCreated.keys) {
            // Should have name but no description
            val expectedFields = HashMap<String, Any?>()
            expectedFields[Constants.NAME] = idToFieldsCreated[id]!![Constants.NAME]!!
            expectedFields[Constants.DESCRIPTION] = null
            @Suppress("UNCHECKED_CAST")
            idToFieldsExpectedOnServer[id] = expectedFields as Map<String, Any>
        }
        checkServer(idToFieldsExpectedOnServer, Constants.ACCOUNT)

        // Adding to idToFields so that they get deleted in tearDown.
        idToFields.putAll(idToFieldsCreated)
    }

    /**
     * Create a few records - some with bad names (too long or empty)
     * Sync up
     * Make sure the records with bad names are still marked as locally created and have the last error field populated
     * @throws Exception
     */
    @Test
    @Throws(Exception::class)
    fun testSyncUpWithErrors() {
        // Build name too long
        val buffer = StringBuffer(256)
        for (i in 0 until 256) buffer.append("x")
        val nameTooLong = buffer.toString()

        // Create a few entries locally
        val goodNames = arrayOf(
            createRecordName(Constants.ACCOUNT),
            createRecordName(Constants.ACCOUNT),
            createRecordName(Constants.ACCOUNT)
        )

        val badNames = arrayOf(
            nameTooLong,
            "" // empty
        )
        createAccountsLocally(goodNames)
        createAccountsLocally(badNames)

        // Sync up
        trySyncUp(5, MergeMode.OVERWRITE)

        // Check db for records with good names
        val idToFieldsGoodNames = getIdToFieldsByName(ACCOUNTS_SOUP, arrayOf(Constants.NAME, Constants.DESCRIPTION), Constants.NAME, goodNames)
        checkDbStateFlags(idToFieldsGoodNames.keys, false, false, false, ACCOUNTS_SOUP)

        // Check db for records with bad names
        val idToFieldsBadNames = getIdToFieldsByName(ACCOUNTS_SOUP, arrayOf(Constants.NAME, Constants.DESCRIPTION, SyncTarget.LAST_ERROR), Constants.NAME, badNames)
        checkDbStateFlags(idToFieldsBadNames.keys, true, false, false, ACCOUNTS_SOUP)

        for (fields in idToFieldsBadNames.values) {
            val name = fields[Constants.NAME] as String
            val lastError = fields[SyncTarget.LAST_ERROR] as String
            if (name == nameTooLong) {
                Assert.assertTrue("Name too large error expected", lastError.contains("Account Name: data value too large"))
            } else if (name == "") {
                Assert.assertTrue("Missing name error expected", lastError.contains("Required fields are missing: [Name]"))
            } else {
                Assert.fail("Unexpected record found: $name")
            }
        }

        // Check server for records with good names
        checkServer(idToFieldsGoodNames, Constants.ACCOUNT)

        // Adding to idToFields so that they get deleted in tearDown
        idToFields.putAll(idToFieldsGoodNames)
    }

    /**
     * Create a few records - some with no sobject type
     * Sync up
     * Make sure the records with no sobject type fail to sync and that last error reflects the problem
     * @throws Exception
     */
    @Test
    @Throws(Exception::class)
    fun testSyncUpWithNoType() {
        trySyncUpBadTypeOrNoType(true)
    }


    /**
     * Create a few records - some with invalid sobject type
     * Sync up
     * Make sure the records with invalid types fail to sync and that last error reflects the problem
     * @throws Exception
     */
    @Test
    @Throws(Exception::class)
    fun testSyncUpWithBadType() {
        trySyncUpBadTypeOrNoType(false)
    }

    @Throws(Exception::class)
    private fun trySyncUpBadTypeOrNoType(noType: Boolean) {
        // Create a few entries locally
        val namesGoodRecords = arrayOf(
            createRecordName(Constants.ACCOUNT),
            createRecordName(Constants.ACCOUNT),
            createRecordName(Constants.ACCOUNT)
        )
        val namesBadRecords = arrayOf(
            createRecordName(Constants.ACCOUNT),
            createRecordName(Constants.ACCOUNT)
        )
        val setNamesBadRecords = HashSet(listOf(*namesBadRecords))
        createAccountsLocally(namesGoodRecords)
        createAccountsLocally(namesBadRecords, object : SyncManagerTestCase.Mutator {
            @Throws(JSONException::class)
            override fun mutate(record: JSONObject): JSONObject {
                if (noType) {
                    record.remove(Constants.ATTRIBUTES)
                } else {
                    val attributes = JSONObject()
                    attributes.put(TYPE, "badType")
                    record.put(Constants.ATTRIBUTES, attributes)
                }
                return record
            }
        })

        // Sync up
        trySyncUp(5, MergeMode.OVERWRITE)

        // Check db for records with good names
        val idToFieldsGoodNames = getIdToFieldsByName(ACCOUNTS_SOUP, arrayOf(Constants.NAME, Constants.DESCRIPTION), Constants.NAME, namesGoodRecords)
        checkDbStateFlags(idToFieldsGoodNames.keys, false, false, false, ACCOUNTS_SOUP)

        // Check db for records with bad names
        val idToFieldsBadNames = getIdToFieldsByName(ACCOUNTS_SOUP, arrayOf(Constants.NAME, Constants.DESCRIPTION, SyncTarget.LAST_ERROR), Constants.NAME, namesBadRecords)
        checkDbStateFlags(idToFieldsBadNames.keys, true, false, false, ACCOUNTS_SOUP)

        Assert.assertEquals("Wrong number of bad records found", namesBadRecords.size, idToFieldsBadNames.size)
        for (fields in idToFieldsBadNames.values) {
            val name = fields[Constants.NAME] as String
            val lastError = fields[SyncTarget.LAST_ERROR] as String
            if (setNamesBadRecords.contains(name)) {
                Assert.assertTrue("Wrong error: $lastError",
                    lastError.contains("The requested resource does not exist") // older end point error
                            || lastError.contains("sObject type 'badType' is not supported.") // sobject collection error with bad type
                            || lastError.contains("sObject type 'null' is not supported.")    // sobject collection error with no type
                )
            } else {
                Assert.fail("Unexpected record found: $name")
            }
        }

        // Check server for records with good names
        checkServer(idToFieldsGoodNames, Constants.ACCOUNT)

        // Adding to idToFields so that they get deleted in tearDown
        idToFields.putAll(idToFieldsGoodNames)
    }

    /**
     * Sync down the test accounts, modify a few, sync up, check smartstore and server afterwards
     */
    @Test
    @Throws(Exception::class)
    fun testSyncUpWithLocallyUpdatedRecords() {
        // First sync down
        trySyncDown(MergeMode.OVERWRITE)

        // Update a few entries locally
        val idToFieldsLocallyUpdated = makeLocalChanges(idToFields, ACCOUNTS_SOUP)

        // Sync up
        trySyncUp(3, MergeMode.OVERWRITE)

        // Check that db doesn't show entries as locally modified anymore
        val ids = idToFieldsLocallyUpdated.keys
        checkDbStateFlags(ids, false, false, false, ACCOUNTS_SOUP)

        // Check server
        checkServer(idToFieldsLocallyUpdated, Constants.ACCOUNT)
    }

    /**
     * Sync down the test accounts, update a few locally,
     * update a few on server,
     * Sync up with merge mode LEAVE_IF_CHANGED, check smartstore and server
     * Then sync up again with merge mode OVERWRITE, check smartstore and server
     */
    @Test
    @Throws(Exception::class)
    fun testSyncUpWithLocallyUpdatedRemotelyUpdatedRecordsWithoutOverwrite() {
        // First sync down
        trySyncDown(MergeMode.LEAVE_IF_CHANGED)

        // Update a few entries locally
        val idToFieldsLocallyUpdated = makeLocalChanges(idToFields, ACCOUNTS_SOUP)

        // Update entries on server
        Thread.sleep(1000) // time stamp precision is in seconds
        val idToFieldsRemotelyUpdated = HashMap<String, Map<String, Any>>()
        val ids = idToFieldsLocallyUpdated.keys
        Assert.assertNotNull("List of IDs should not be null", ids)
        for (id in ids) {
            val fields = idToFieldsLocallyUpdated[id]!!
            val updatedFields = HashMap<String, Any>()
            for (fieldName in fields.keys) {
                updatedFields[fieldName] = fields[fieldName]!!.toString() + "_updated_again"
            }
            idToFieldsRemotelyUpdated[id] = updatedFields
        }
        updateRecordsOnServer(idToFieldsRemotelyUpdated, Constants.ACCOUNT)

        // Sync up with leave-if-changed
        trySyncUp(3, MergeMode.LEAVE_IF_CHANGED)

        // Check that db shows entries as locally modified
        checkDbStateFlags(ids, false, true, false, ACCOUNTS_SOUP)

        // Check server still has remote updates
        checkServer(idToFieldsRemotelyUpdated, Constants.ACCOUNT)

        // Sync up with overwrite
        trySyncUp(3, MergeMode.OVERWRITE)

        // Check that db no longer shows entries as locally modified
        checkDbStateFlags(ids, false, false, false, ACCOUNTS_SOUP)

        // Check server has local updates
        checkServer(idToFieldsLocallyUpdated, Constants.ACCOUNT)
    }

    /**
     * Create accounts locally, sync up with merge mode OVERWRITE, check smartstore and server afterwards
     */
    @Test
    @Throws(Exception::class)
    fun testSyncUpWithLocallyCreatedRecords() {
        trySyncUpWithLocallyCreatedRecords(3, MergeMode.OVERWRITE)
    }


    /**
     * Create accounts locally, sync up with merge mode LEAVE_IF_CHANGED, check smartstore and server afterwards
     */
    @Test
    @Throws(Exception::class)
    fun testSyncUpWithLocallyCreatedRecordsWithoutOverwrite() {
        trySyncUpWithLocallyCreatedRecords(3, MergeMode.LEAVE_IF_CHANGED)
    }

    @Throws(Exception::class)
    private fun trySyncUpWithLocallyCreatedRecords(countRecords: Int, syncUpMergeMode: MergeMode) {
        // Create a few entries locally
        val listNames = ArrayList<String>()
        for (i in 0 until countRecords) {
            listNames.add(createRecordName(Constants.ACCOUNT))
        }
        val names = listNames.toTypedArray()
        createAccountsLocally(names)

        // Sync up
        trySyncUp(countRecords, syncUpMergeMode)

        // Check that db doesn't show entries as locally created anymore and that they use sfdc id
        val idToFieldsCreated = getIdToFieldsByName(ACCOUNTS_SOUP, arrayOf(Constants.NAME, Constants.DESCRIPTION), Constants.NAME, names)
        checkDbStateFlags(idToFieldsCreated.keys, false, false, false, ACCOUNTS_SOUP)

        // Check server
        checkServer(idToFieldsCreated, Constants.ACCOUNT)

        // Adding to idToFields so that they get deleted in tearDown
        idToFields.putAll(idToFieldsCreated)
    }

    /**
     * Sync down the test accounts, delete a few, sync up, check smartstore and server afterwards
     */
    @Test
    @Throws(Exception::class)
    fun testSyncUpWithLocallyDeletedRecords() {
        // First sync down
        trySyncDown(MergeMode.OVERWRITE)

        // Delete a few entries locally
        val allIds = idToFields.keys.toTypedArray()
        val idsLocallyDeleted = arrayOf(allIds[0], allIds[1], allIds[2])
        deleteRecordsLocally(ACCOUNTS_SOUP, *idsLocallyDeleted)

        // Sync up
        trySyncUp(3, MergeMode.OVERWRITE)

        // Check that db doesn't contain those entries anymore
        checkDbDeleted(ACCOUNTS_SOUP, idsLocallyDeleted, Constants.ID)

        // Check server
        checkServerDeleted(idsLocallyDeleted, Constants.ACCOUNT)
    }

    /**
     * Create accounts locally, delete them locally, sync up with merge mode LEAVE_IF_CHANGED, check smartstore
     *
     * Ideally an application that deletes locally created records should simply remove them from the smartstore
     * But if records are kept in the smartstore and are flagged as created and deleted (or just deleted), then
     * sync up should not throw any error and the records should end up being removed from the smartstore
     */
    @Test
    @Throws(Exception::class)
    fun testSyncUpWithLocallyCreatedAndDeletedRecords() {
        // Create a few entries locally
        val names = arrayOf(createRecordName(Constants.ACCOUNT), createRecordName(Constants.ACCOUNT), createRecordName(Constants.ACCOUNT))
        createAccountsLocally(names)
        val idToFieldsCreated = getIdToFieldsByName(ACCOUNTS_SOUP, arrayOf(Constants.NAME, Constants.DESCRIPTION), Constants.NAME, names)

        val allIds = idToFieldsCreated.keys.toTypedArray()
        val idsLocallyDeleted = arrayOf(allIds[0], allIds[1], allIds[2])
        deleteRecordsLocally(ACCOUNTS_SOUP, *idsLocallyDeleted)

        // Sync up
        trySyncUp(3, MergeMode.LEAVE_IF_CHANGED)

        // Check that db doesn't contain those entries anymore
        checkDbDeleted(ACCOUNTS_SOUP, idsLocallyDeleted, Constants.ID)
    }

    /**
     * Sync down the test accounts, delete a few locally,
     * update a few on server,
     * Sync up with merge mode LEAVE_IF_CHANGED, check smartstore and server
     * Then sync up again with merge mode OVERWRITE, check smartstore and server
     */
    @Test
    @Throws(Exception::class)
    fun testSyncUpWithLocallyDeletedRemotelyUpdatedRecordsWithoutOverwrite() {
        // First sync down
        trySyncDown(MergeMode.LEAVE_IF_CHANGED)

        // Delete a few entries locally
        val allIds = idToFields.keys.toTypedArray()
        val idsLocallyDeleted = arrayOf(allIds[0], allIds[1], allIds[2])
        deleteRecordsLocally(ACCOUNTS_SOUP, *idsLocallyDeleted)

        // Update entries on server
        Thread.sleep(1000) // time stamp precision is in seconds
        val idToFieldsRemotelyUpdated = HashMap<String, Map<String, Any>>()
        for (i in idsLocallyDeleted.indices) {
            val id = idsLocallyDeleted[i]
            val updatedFields = updatedFields(idToFields[id]!!, REMOTELY_UPDATED)
            idToFieldsRemotelyUpdated[id] = updatedFields
        }
        updateRecordsOnServer(idToFieldsRemotelyUpdated, Constants.ACCOUNT)

        // Sync up with leave-if-changed
        trySyncUp(3, MergeMode.LEAVE_IF_CHANGED)

        // Check that db still contains those entries
        checkDbStateFlags(listOf(*idsLocallyDeleted), false, false, true, ACCOUNTS_SOUP)

        // Check server
        checkServer(idToFieldsRemotelyUpdated, Constants.ACCOUNT)

        // Sync up with overwrite
        trySyncUp(3, MergeMode.OVERWRITE)

        // Check that db no longer contains deleted records
        checkDbDeleted(ACCOUNTS_SOUP, idsLocallyDeleted, Constants.ID)

        // Check server no longer contains deleted record
        checkServerDeleted(idsLocallyDeleted, Constants.ACCOUNT)
    }

    /**
     * Sync down the test accounts, delete record on server and locally, sync up, check smartstore and server afterwards
     */
    @Test
    @Throws(Exception::class)
    fun testSyncUpWithLocallyDeletedRemotelyDeletedRecords() {
        trySyncUpWithLocallyDeletedRemotelyDeletedRecords(MergeMode.OVERWRITE)
    }

    /**
     * Sync down the test accounts, delete record on server and locally,
     * sync up with merge mode LEAVE_IF_CHANGED,
     * check smartstore and server afterwards
     */
    @Test
    @Throws(Exception::class)
    fun testSyncUpWithLocallyDeletedRemotelyDeletedRecordsWithoutOverwrite() {
        trySyncUpWithLocallyDeletedRemotelyDeletedRecords(MergeMode.LEAVE_IF_CHANGED)
    }

    @Throws(Exception::class)
    fun trySyncUpWithLocallyDeletedRemotelyDeletedRecords(mergeMode: MergeMode) {
        // First sync down
        trySyncDown(MergeMode.OVERWRITE)

        // Delete record locally
        val allIds = idToFields.keys.toTypedArray()
        val idsLocallyDeleted = arrayOf(allIds[0], allIds[1], allIds[2])
        deleteRecordsLocally(ACCOUNTS_SOUP, *idsLocallyDeleted)

        // Delete same records on server
        deleteRecordsByIdOnServer(idToFields.keys, Constants.ACCOUNT)

        // Sync up
        trySyncUp(3, mergeMode)

        // Check that db doesn't contain those entries anymore
        checkDbDeleted(ACCOUNTS_SOUP, idsLocallyDeleted, Constants.ID)

        // Check server
        checkServerDeleted(idsLocallyDeleted, Constants.ACCOUNT)
    }

    /**
     * Sync down the test accounts, delete record on server and update same record locally, sync up, check smartstore and server afterwards
     */
    @Test
    @Throws(Exception::class)
    fun testSyncUpWithLocallyUpdatedRemotelyDeletedRecords() {
        // First sync down
        trySyncDown(MergeMode.OVERWRITE)

        // Update a few entries locally
        val idToFieldsLocallyUpdated = makeLocalChanges(idToFields, ACCOUNTS_SOUP)

        // Delete record on server
        val remotelyDeletedId = idToFieldsLocallyUpdated.keys.toTypedArray()[0]
        deleteRecordsByIdOnServer(HashSet(listOf(remotelyDeletedId)), Constants.ACCOUNT)

        // Name of locally recorded record that was deleted on server
        val locallyUpdatedRemotelyDeletedName = idToFieldsLocallyUpdated[remotelyDeletedId]!![Constants.NAME] as String

        // Sync up
        trySyncUp(3, MergeMode.OVERWRITE)

        // Getting id / fields of updated records looking up by name
        val idToFieldsUpdated = getIdToFieldsByName(ACCOUNTS_SOUP, arrayOf(Constants.NAME, Constants.DESCRIPTION), Constants.NAME, getNamesFromIdToFields(idToFieldsLocallyUpdated))

        // Check db
        checkDb(idToFieldsUpdated, ACCOUNTS_SOUP)

        // Expect 3 records
        Assert.assertEquals(3, idToFieldsUpdated.size)

        // Expect remotely deleted record to have a new id
        Assert.assertFalse(idToFieldsUpdated.containsKey(remotelyDeletedId))
        for (accountId in idToFieldsUpdated.keys) {
            val accountName = idToFieldsUpdated[accountId]!![Constants.NAME] as String

            // Check that locally updated / remotely deleted record has new id (not in idToFields)
            if (accountName == locallyUpdatedRemotelyDeletedName) {
                Assert.assertFalse(idToFields.containsKey(accountId))

                //update the record entry using the new id
                idToFields.remove(remotelyDeletedId)
                idToFields[accountId] = idToFieldsUpdated[accountId]!!
            }
            // Otherwise should be a known id (in idToFields)
            else {
                Assert.assertTrue(idToFields.containsKey(accountId))
            }
        }

        // Check server
        checkServer(idToFieldsUpdated, Constants.ACCOUNT)
    }

    /**
     * Sync down the test accounts, delete record on server and update same record locally, sync up with merge mode LEAVE_IF_CHANGED, check smartstore and server afterwards
     */
    @Test
    @Throws(Exception::class)
    fun testSyncUpWithLocallyUpdatedRemotelyDeletedRecordsWithoutOverwrite() {
        // First sync down
        trySyncDown(MergeMode.OVERWRITE)

        // Update a few entries locally
        val idToFieldsLocallyUpdated = makeLocalChanges(idToFields, ACCOUNTS_SOUP)

        // Delete record on server
        val remotelyDeletedId = idToFieldsLocallyUpdated.keys.toTypedArray()[0]
        deleteRecordsByIdOnServer(HashSet(listOf(remotelyDeletedId)), Constants.ACCOUNT)

        // Sync up
        trySyncUp(3, MergeMode.LEAVE_IF_CHANGED)

        // Getting id / fields of updated records looking up by name
        val idToFieldsUpdated = getIdToFieldsByName(ACCOUNTS_SOUP, arrayOf(Constants.NAME, Constants.DESCRIPTION), Constants.NAME, getNamesFromIdToFields(idToFieldsLocallyUpdated))

        // Expect 3 records
        Assert.assertEquals(3, idToFieldsUpdated.size)

        // Expect remotely deleted record to be there
        Assert.assertTrue(idToFieldsUpdated.containsKey(remotelyDeletedId))

        // Checking the remotely deleted record locally
        checkDbStateFlags(listOf(remotelyDeletedId), false, true, false, ACCOUNTS_SOUP)

        // Check the other 2 records in db
        val otherIdtoFields = HashMap(idToFieldsLocallyUpdated)
        otherIdtoFields.remove(remotelyDeletedId)
        checkDb(otherIdtoFields, ACCOUNTS_SOUP)

        // Check server
        checkServer(otherIdtoFields, Constants.ACCOUNT)
        checkServerDeleted(arrayOf(remotelyDeletedId), Constants.ACCOUNT)
    }

    /**
     * Sync down the test accounts, modify a few, create accounts locally, sync up specifying different create and update field list,
     * check smartstore and server afterwards
     */
    @Test
    @Throws(Exception::class)
    fun testSyncUpWithCreateAndUpdateFieldList() {
        // First sync down
        trySyncDown(MergeMode.OVERWRITE)

        // Update a few entries locally
        val idToFieldsLocallyUpdated = makeLocalChanges(idToFields, ACCOUNTS_SOUP)
        val namesOfUpdated = getNamesFromIdToFields(idToFieldsLocallyUpdated)

        // Create a few entries locally
        val namesOfCreated = arrayOf(
            createRecordName(Constants.ACCOUNT),
            createRecordName(Constants.ACCOUNT),
            createRecordName(Constants.ACCOUNT)
        )
        createAccountsLocally(namesOfCreated)

        // Sync up with different create and update field lists
        trySyncUp(namesOfCreated.size + namesOfUpdated.size, MergeMode.OVERWRITE, listOf(Constants.NAME), listOf(Constants.DESCRIPTION))

        // Check that db doesn't show created entries as locally created anymore and that they use sfdc id
        val idToFieldsCreated = getIdToFieldsByName(ACCOUNTS_SOUP, arrayOf(Constants.NAME, Constants.DESCRIPTION), Constants.NAME, namesOfCreated)
        checkDbStateFlags(idToFieldsCreated.keys, false, false, false, ACCOUNTS_SOUP)

        // Check that db doesn't show updated entries as locally modified anymore
        checkDbStateFlags(idToFieldsLocallyUpdated.keys, false, false, false, ACCOUNTS_SOUP)

        // Check server - make updated records only have updated description - make sure created records only have name
        val idToFieldsExpectedOnServer = HashMap<String, Map<String, Any>>()
        for (id in idToFieldsCreated.keys) {
            // Should have name but no description
            val expectedFields = HashMap<String, Any?>()
            expectedFields[Constants.NAME] = idToFieldsCreated[id]!![Constants.NAME]!!
            expectedFields[Constants.DESCRIPTION] = null
            @Suppress("UNCHECKED_CAST")
            idToFieldsExpectedOnServer[id] = expectedFields as Map<String, Any>
        }
        for (id in idToFieldsLocallyUpdated.keys) {
            // Should have modified name but original description
            val expectedFields = HashMap<String, Any>()
            expectedFields[Constants.NAME] = idToFields[id]!![Constants.NAME]!!
            expectedFields[Constants.DESCRIPTION] = idToFieldsLocallyUpdated[id]!![Constants.DESCRIPTION]!!
            idToFieldsExpectedOnServer[id] = expectedFields
        }
        checkServer(idToFieldsExpectedOnServer, Constants.ACCOUNT)

        // Adding to idToFields so that they get deleted in tearDown.
        idToFields.putAll(idToFieldsCreated)
    }

    /**
     * Create accounts locally but with external id field populated, sync up with external id field name provided, check smartstore and server afterwards
     * @throws Exception
     */
    @Test
    @Throws(Exception::class)
    fun testSyncUpWithExternalId() {
        val externalIdFieldName = "Id"

        // Creating 3 new names
        val name1 = createRecordName(Constants.ACCOUNT)
        val name2 = createRecordName(Constants.ACCOUNT)
        val name3 = createRecordName(Constants.ACCOUNT)

        // Get id of two records on the server
        val allIds = idToFields.keys.toTypedArray()
        Arrays.sort(allIds)
        val id1 = allIds[0]
        val id2 = allIds[1]

        // Create accounts locally
        val localAccounts = createAccountsLocally(arrayOf(name1, name2, name3))
        val localRecord1 = localAccounts[0]
        val localRecord2 = localAccounts[1]
        val localRecord3 = localAccounts[2]

        // Update Id field to match and existing id for record 1 and 2
        localRecord1.put(externalIdFieldName, id1)
        smartStore.upsert(ACCOUNTS_SOUP, localRecord1)
        localRecord2.put(externalIdFieldName, id2)
        smartStore.upsert(ACCOUNTS_SOUP, localRecord2)
        localRecord3.put(externalIdFieldName, null)
        smartStore.upsert(ACCOUNTS_SOUP, localRecord3)

        // Sync up with external id field name - NB: only syncing up name field not description
        val options = SyncOptions.optionsForSyncUp(listOf(Constants.NAME))
        trySyncUp(3, options, null, null, externalIdFieldName)

        // Getting id for third record upserted - the one without an valid external id
        val id3 = getIdToFieldsByName(ACCOUNTS_SOUP, arrayOf<String>(), Constants.NAME, arrayOf(name3)).keys.toTypedArray()[0]

        // Expected records locally
        val expectedDbIdToFields = HashMap<String, Map<String, Any>>()
        expectedDbIdToFields[id1] = createFieldsMapFromNameDescription(name1, localRecord1.getString(Constants.DESCRIPTION))
        expectedDbIdToFields[id2] = createFieldsMapFromNameDescription(name2, localRecord2.getString(Constants.DESCRIPTION))
        expectedDbIdToFields[id3] = createFieldsMapFromNameDescription(name3, localRecord3.getString(Constants.DESCRIPTION))

        // Check db
        checkDbStateFlags(expectedDbIdToFields.keys, false, false, false, ACCOUNTS_SOUP)
        checkDb(expectedDbIdToFields, ACCOUNTS_SOUP)

        // Expected records on server
        val expectedServerIdToFields = HashMap<String, Map<String, Any>>()
        expectedServerIdToFields[id1] = createFieldsMapFromNameDescription(name1, idToFields[id1]!![Constants.DESCRIPTION] as String?)
        expectedServerIdToFields[id2] = createFieldsMapFromNameDescription(name2, idToFields[id2]!![Constants.DESCRIPTION] as String?)
        expectedServerIdToFields[id3] = createFieldsMapFromNameDescription(name3, null)

        // Check server
        checkServer(expectedServerIdToFields, Constants.ACCOUNT)

        // Adding to idToFields so that they get deleted in tearDown
        idToFields.putAll(expectedServerIdToFields)
    }

    /**
     * Create many accounts locally, sync up with merge mode OVERWRITE, check smartstore and server afterwards
     */
    @Test
    @Throws(Exception::class)
    fun testSyncUpManyLocallyCreatedRecords() {
        trySyncUpWithLocallyCreatedRecords(500, MergeMode.OVERWRITE)
    }

    /**
     * Sync up helper
     * @param numberChanges
     * @param mergeMode
     * @throws JSONException
     */
    @Throws(JSONException::class)
    protected fun trySyncUp(numberChanges: Int, mergeMode: MergeMode) {
        trySyncUp(numberChanges, mergeMode, null, null)
    }

    /**
     * Sync up helper
     * @param numberChanges
     * @param mergeMode
     * @throws JSONException
     */
    @Throws(JSONException::class)
    protected fun trySyncUp(numberChanges: Int, mergeMode: MergeMode, createFieldlist: List<String>?, updateFieldlist: List<String>?) {
        val options = SyncOptions.optionsForSyncUp(listOf(Constants.NAME, Constants.DESCRIPTION), mergeMode)
        trySyncUp(numberChanges, options, createFieldlist, updateFieldlist, null)
    }

    /**
     * Sync up helper
     * @param numberChanges
     * @param options
     * @param createFieldlist
     * @param updateFieldlist
     * @param externalIdFieldName
     * @throws JSONException
     */
    @Throws(JSONException::class)
    protected open fun trySyncUp(numberChanges: Int, options: SyncOptions, createFieldlist: List<String>?, updateFieldlist: List<String>?, externalIdFieldName: String?) {
        trySyncUp(SyncUpTarget(createFieldlist, updateFieldlist, null, null, externalIdFieldName), numberChanges, options, false)
    }

    /**
     * Sync down helper
     * @throws JSONException
     * @param mergeMode
     */
    @Throws(JSONException::class)
    protected fun trySyncDown(mergeMode: MergeMode): Long {
        return trySyncDown(mergeMode, null)
    }

    /**
     * Sync down helper
     * @throws JSONException
     * @param mergeMode
     */
    @Throws(JSONException::class)
    protected fun trySyncDown(mergeMode: MergeMode, syncName: String?): Long {
        val target = SoqlSyncDownTarget("SELECT Id, Name, Description, LastModifiedDate FROM Account WHERE Id IN " + makeInClause(idToFields.keys))
        return trySyncDown(mergeMode, target, ACCOUNTS_SOUP, idToFields.size, 1, syncName)

    }

    companion object {
        // Misc
        protected const val COUNT_TEST_ACCOUNTS = 10
    }
}
