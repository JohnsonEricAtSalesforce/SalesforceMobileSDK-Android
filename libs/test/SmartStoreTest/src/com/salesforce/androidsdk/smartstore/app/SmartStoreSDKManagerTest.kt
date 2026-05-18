/*
 * Copyright (c) 2020-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.smartstore.app

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.smartstore.store.DBOpenHelper
import com.salesforce.androidsdk.smartstore.store.IndexSpec
import com.salesforce.androidsdk.smartstore.store.KeyValueEncryptedFileStore
import com.salesforce.androidsdk.smartstore.store.SmartStore
import com.salesforce.androidsdk.smartstore.store.SmartStore.Type
import com.salesforce.androidsdk.util.ManagedFilesHelper
import org.json.JSONObject
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmartStoreSDKManagerTest {

    private lateinit var context: Context
    private lateinit var manager: SmartStoreSDKManager

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .applicationContext
        SmartStoreSDKManager.initNative(context, com.salesforce.androidsdk.MainActivity::class.java)
        manager = SmartStoreSDKManager.getInstance()
    }

    @After
    fun tearDown() {
        // Nuking the keyvaluestores directory
        ManagedFilesHelper.deleteFile(KeyValueEncryptedFileStore.computeParentDir(context))
        // Nuking user smartstores of all users
        DBOpenHelper.deleteAllUserDatabases(context)
        // Nuking global smartstores
        manager.removeAllGlobalStores()
    }

    @Test
    fun testGetSmartStoreReturnsSameStore() {
        val user = createTestAccount("00Dorg", "user", null, "first@test.com")
        try {
            val store = manager.getSmartStore(user)
            val storeSecondInstance = manager.getSmartStore(user)
            Assert.assertSame("Expect same database", store.database, storeSecondInstance.database)
        } finally {
            manager.removeSmartStore(user)
        }
    }

    @Test
    fun testGetGlobalSmartStoreReturnsSameStore() {
        try {
            val store = manager.getGlobalSmartStore()
            val storeSecondInstance = manager.getGlobalSmartStore()
            Assert.assertSame("Expect same database", store.database, storeSecondInstance.database)
        } finally {
            manager.removeGlobalSmartStore(DBOpenHelper.DEFAULT_DB_NAME)
        }
    }

    @Test
    fun testGetKeyValueStoreReturnsSameStore() {
        val user = createTestAccount("00Dorg", "user", null, "first@test.com")
        val store = manager.getKeyValueStore("store", user)
        Assert.assertTrue("Store should be empty", store.isEmpty())
        store.saveValue("key1", "value1")
        store.saveValue("key2", "value2")
        store.saveValue("key3", "value3")
        Assert.assertTrue("Store should not be empty", !store.isEmpty())
        Assert.assertEquals("Store should have 3 values", 3, store.count())
        val storeSecondInstance = manager.getKeyValueStore("store", user)
        Assert.assertTrue("Store should not be empty", !storeSecondInstance.isEmpty())
        Assert.assertEquals("Store should have 3 values", 3, storeSecondInstance.count())
        Assert.assertEquals("Wrong value", "value1", storeSecondInstance.getValue("key1"))
        Assert.assertEquals("Wrong value", "value2", storeSecondInstance.getValue("key2"))
        Assert.assertEquals("Wrong value", "value3", storeSecondInstance.getValue("key3"))
    }

    @Test
    fun testGlobalGetKeyValueStoreReturnsSameStore() {
        val store = manager.getGlobalKeyValueStore("store")
        Assert.assertTrue("Store should be empty", store.isEmpty())
        store.saveValue("key1", "value1")
        store.saveValue("key2", "value2")
        store.saveValue("key3", "value3")
        Assert.assertTrue("Store should not be empty", !store.isEmpty())
        Assert.assertEquals("Store should have 3 values", 3, store.count())
        val storeSecondInstance = manager.getGlobalKeyValueStore("store")
        Assert.assertTrue("Store should not be empty", !storeSecondInstance.isEmpty())
        Assert.assertEquals("Store should have 3 values", 3, storeSecondInstance.count())
        Assert.assertEquals("Wrong value", "value1", storeSecondInstance.getValue("key1"))
        Assert.assertEquals("Wrong value", "value2", storeSecondInstance.getValue("key2"))
        Assert.assertEquals("Wrong value", "value3", storeSecondInstance.getValue("key3"))
    }

    @Test
    fun testSmartStoreOperationsWithOneUserOneStore() {
        val user = createTestAccount("00Dorg", "user", null, "first@test.com")
        Assert.assertFalse("Store should not be found", manager.hasSmartStore("store", user, null))
        Assert.assertEquals("No stores should be found", 0, manager.getUserStoresPrefixList(user).size)
        val store = createAndPopulateSmartStore("store", user)
        Assert.assertEquals("Wrong db path", computeExpectedSmartStorePath("store", user), store.database.path)
        Assert.assertTrue("Store should be found", manager.hasSmartStore("store", user, null))
        Assert.assertEquals("Wrong store names", 1, manager.getUserStoresPrefixList(user).size)
        Assert.assertEquals("Wrong store names", "store", manager.getUserStoresPrefixList(user)[0])
        manager.removeSmartStore("store", user, null)
        Assert.assertFalse("Store should no longer be found", manager.hasSmartStore("store", user, null))
        Assert.assertEquals("No stores should be found", 0, manager.getUserStoresPrefixList(user).size)
    }

    @Test
    fun testGlobalSmartStoreOperationsWithOneUserOneStore() {
        Assert.assertFalse("Store should not be found", manager.hasGlobalSmartStore("store"))
        Assert.assertEquals("No stores should be found", 0, manager.getGlobalStoresPrefixList().size)
        val store = createAndPopulateGlobalSmartStore("store")
        Assert.assertEquals("Wrong db path", computeExpectedGlobalSmartStorePath("store"), store.database.path)
        Assert.assertTrue("Store should be found", manager.hasGlobalSmartStore("store"))
        Assert.assertEquals("Wrong store names", 1, manager.getGlobalStoresPrefixList().size)
        Assert.assertEquals("Wrong store names", "store", manager.getGlobalStoresPrefixList()[0])
        manager.removeGlobalSmartStore("store")
        Assert.assertFalse("Store should no longer be found", manager.hasGlobalSmartStore("store"))
        Assert.assertEquals("No stores should be found", 0, manager.getGlobalStoresPrefixList().size)
    }

    @Test
    fun testKeyValueStoreOperationsWithOneUserOneStore() {
        val user = createTestAccount("00Dorg", "user", null, "first@test.com")
        Assert.assertFalse("Store should not be found", manager.hasKeyValueStore("store", user))
        Assert.assertEquals("No stores should be found", 0, manager.getKeyValueStoresPrefixList(user).size)
        val store = createAndPopulateKeyValueStore("store", user)
        Assert.assertEquals("Wrong store dir", computeExpectedKeyValueStorePath("store", user), store.storeDir.absolutePath)
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store", user))
        Assert.assertEquals("Wrong store names", 1, manager.getKeyValueStoresPrefixList(user).size)
        Assert.assertEquals("Wrong store names", "store", manager.getKeyValueStoresPrefixList(user)[0])
        manager.removeKeyValueStore("store", user)
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store", user))
        Assert.assertEquals("No stores should be found", 0, manager.getKeyValueStoresPrefixList(user).size)
    }

    @Test
    fun testGlobalKeyValueStoreOperationsWithOneUserOneStore() {
        Assert.assertFalse("Store should not be found", manager.hasGlobalKeyValueStore("store"))
        Assert.assertEquals("No stores should be found", 0, manager.getGlobalKeyValueStoresPrefixList().size)
        val store = createAndPopulateGlobalKeyValueStore("store")
        Assert.assertEquals("Wrong store dir", computeExpectedGlobalKeyValueStorePath("store"), store.storeDir.absolutePath)
        Assert.assertTrue("Store should be found", manager.hasGlobalKeyValueStore("store"))
        Assert.assertEquals("Wrong store names", 1, manager.getGlobalKeyValueStoresPrefixList().size)
        Assert.assertEquals("Wrong store names", "store", manager.getGlobalKeyValueStoresPrefixList()[0])
        manager.removeGlobalKeyValueStore("store")
        Assert.assertFalse("Store should no longer be found", manager.hasGlobalKeyValueStore("store"))
        Assert.assertEquals("No stores should be found", 0, manager.getGlobalKeyValueStoresPrefixList().size)
    }

    @Test
    fun testSmartStoreOperationsWithOneUserMultipleStores() {
        val user = createTestAccount("00Dorg", "user", null, "first@test.com")
        Assert.assertFalse("Store1 should not be found", manager.hasSmartStore("store1", user, null))
        Assert.assertFalse("Store2 should not be found", manager.hasSmartStore("store2", user, null))
        Assert.assertFalse("Store3 should not be found", manager.hasSmartStore("store3", user, null))
        Assert.assertEquals("No stores should be found", 0, manager.getUserStoresPrefixList(user).size)
        val store1 = createAndPopulateSmartStore("store1", user)
        Assert.assertEquals("Wrong db path", computeExpectedSmartStorePath("store1", user), store1.database.path)
        val store2 = createAndPopulateSmartStore("store2", user)
        Assert.assertEquals("Wrong db path", computeExpectedSmartStorePath("store2", user), store2.database.path)
        val store3 = createAndPopulateSmartStore("store3", user)
        Assert.assertEquals("Wrong db path", computeExpectedSmartStorePath("store3", user), store3.database.path)
        Assert.assertTrue("Store should be found", manager.hasSmartStore("store1", user, null))
        Assert.assertTrue("Store should be found", manager.hasSmartStore("store2", user, null))
        Assert.assertTrue("Store should be found", manager.hasSmartStore("store3", user, null))
        Assert.assertEquals("Wrong store names", 3, manager.getUserStoresPrefixList(user).size)
        Assert.assertTrue("Wrong store names", manager.getUserStoresPrefixList(user).contains("store1"))
        Assert.assertTrue("Wrong store names", manager.getUserStoresPrefixList(user).contains("store2"))
        Assert.assertTrue("Wrong store names", manager.getUserStoresPrefixList(user).contains("store3"))
        manager.removeSmartStore("store1", user, null)
        Assert.assertFalse("Store should no longer be found", manager.hasSmartStore("store1", user, null))
        manager.removeAllUserStores(user)
        Assert.assertFalse("Store should no longer be found", manager.hasSmartStore("store2", user, null))
        Assert.assertFalse("Store should no longer be found", manager.hasSmartStore("store3", user, null))
        Assert.assertEquals("No stores should be found", 0, manager.getUserStoresPrefixList(user).size)
    }

    @Test
    fun testGlobalSmartStoreOperationsWithMultipleStores() {
        Assert.assertFalse("Store1 should not be found", manager.hasGlobalSmartStore("store1"))
        Assert.assertFalse("Store2 should not be found", manager.hasGlobalSmartStore("store2"))
        Assert.assertFalse("Store3 should not be found", manager.hasGlobalSmartStore("store3"))
        Assert.assertEquals("No stores should be found", 0, manager.getGlobalStoresPrefixList().size)
        val store1 = createAndPopulateGlobalSmartStore("store1")
        Assert.assertEquals("Wrong store dir", computeExpectedGlobalSmartStorePath("store1"), store1.database.path)
        val store2 = createAndPopulateGlobalSmartStore("store2")
        Assert.assertEquals("Wrong store dir", computeExpectedGlobalSmartStorePath("store2"), store2.database.path)
        val store3 = createAndPopulateGlobalSmartStore("store3")
        Assert.assertEquals("Wrong store dir", computeExpectedGlobalSmartStorePath("store3"), store3.database.path)
        Assert.assertTrue("Store should be found", manager.hasGlobalSmartStore("store1"))
        Assert.assertTrue("Store should be found", manager.hasGlobalSmartStore("store2"))
        Assert.assertTrue("Store should be found", manager.hasGlobalSmartStore("store3"))
        Assert.assertEquals("Wrong store names", 3, manager.getGlobalStoresPrefixList().size)
        Assert.assertTrue("Wrong store names", manager.getGlobalStoresPrefixList().contains("store1"))
        Assert.assertTrue("Wrong store names", manager.getGlobalStoresPrefixList().contains("store2"))
        Assert.assertTrue("Wrong store names", manager.getGlobalStoresPrefixList().contains("store3"))
        manager.removeGlobalSmartStore("store1")
        Assert.assertFalse("Store should no longer be found", manager.hasGlobalSmartStore("store1"))
        manager.removeAllGlobalStores()
        Assert.assertFalse("Store should no longer be found", manager.hasGlobalSmartStore("store2"))
        Assert.assertFalse("Store should no longer be found", manager.hasGlobalSmartStore("store3"))
        Assert.assertEquals("No stores should be found", 0, manager.getGlobalStoresPrefixList().size)
    }

    @Test
    fun testKeyValueStoreOperationsWithOneUserMultipleStores() {
        val user = createTestAccount("00Dorg", "user", null, "first@test.com")
        Assert.assertFalse("Store1 should not be found", manager.hasKeyValueStore("store1", user))
        Assert.assertFalse("Store2 should not be found", manager.hasKeyValueStore("store2", user))
        Assert.assertFalse("Store3 should not be found", manager.hasKeyValueStore("store3", user))
        Assert.assertEquals("No stores should be found", 0, manager.getKeyValueStoresPrefixList(user).size)
        val store1 = createAndPopulateKeyValueStore("store1", user)
        Assert.assertEquals("Wrong store dir", computeExpectedKeyValueStorePath("store1", user), store1.storeDir.absolutePath)
        val store2 = createAndPopulateKeyValueStore("store2", user)
        Assert.assertEquals("Wrong store dir", computeExpectedKeyValueStorePath("store2", user), store2.storeDir.absolutePath)
        val store3 = createAndPopulateKeyValueStore("store3", user)
        Assert.assertEquals("Wrong store dir", computeExpectedKeyValueStorePath("store3", user), store3.storeDir.absolutePath)
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store1", user))
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store2", user))
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store3", user))
        Assert.assertEquals("Wrong store names", 3, manager.getKeyValueStoresPrefixList(user).size)
        Assert.assertTrue("Wrong store names", manager.getKeyValueStoresPrefixList(user).contains("store1"))
        Assert.assertTrue("Wrong store names", manager.getKeyValueStoresPrefixList(user).contains("store2"))
        Assert.assertTrue("Wrong store names", manager.getKeyValueStoresPrefixList(user).contains("store3"))
        manager.removeKeyValueStore("store1", user)
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store1", user))
        manager.removeAllKeyValueStores(user)
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store2", user))
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store3", user))
        Assert.assertEquals("No stores should be found", 0, manager.getKeyValueStoresPrefixList(user).size)
    }

    @Test
    fun testGlobalKeyValueStoreOperationsWithMultipleStores() {
        Assert.assertFalse("Store1 should not be found", manager.hasGlobalKeyValueStore("store1"))
        Assert.assertFalse("Store2 should not be found", manager.hasGlobalKeyValueStore("store2"))
        Assert.assertFalse("Store3 should not be found", manager.hasGlobalKeyValueStore("store3"))
        Assert.assertEquals("No stores should be found", 0, manager.getGlobalKeyValueStoresPrefixList().size)
        val store1 = createAndPopulateGlobalKeyValueStore("store1")
        Assert.assertEquals("Wrong store dir", computeExpectedGlobalKeyValueStorePath("store1"), store1.storeDir.absolutePath)
        val store2 = createAndPopulateGlobalKeyValueStore("store2")
        Assert.assertEquals("Wrong store dir", computeExpectedGlobalKeyValueStorePath("store2"), store2.storeDir.absolutePath)
        val store3 = createAndPopulateGlobalKeyValueStore("store3")
        Assert.assertEquals("Wrong store dir", computeExpectedGlobalKeyValueStorePath("store3"), store3.storeDir.absolutePath)
        Assert.assertTrue("Store should be found", manager.hasGlobalKeyValueStore("store1"))
        Assert.assertTrue("Store should be found", manager.hasGlobalKeyValueStore("store2"))
        Assert.assertTrue("Store should be found", manager.hasGlobalKeyValueStore("store3"))
        Assert.assertEquals("Wrong store names", 3, manager.getGlobalKeyValueStoresPrefixList().size)
        Assert.assertTrue("Wrong store names", manager.getGlobalKeyValueStoresPrefixList().contains("store1"))
        Assert.assertTrue("Wrong store names", manager.getGlobalKeyValueStoresPrefixList().contains("store2"))
        Assert.assertTrue("Wrong store names", manager.getGlobalKeyValueStoresPrefixList().contains("store3"))
        manager.removeGlobalKeyValueStore("store1")
        Assert.assertFalse("Store should no longer be found", manager.hasGlobalKeyValueStore("store1"))
        manager.removeAllGlobalKeyValueStores()
        Assert.assertFalse("Store should no longer be found", manager.hasGlobalKeyValueStore("store2"))
        Assert.assertFalse("Store should no longer be found", manager.hasGlobalKeyValueStore("store3"))
        Assert.assertEquals("No stores should be found", 0, manager.getGlobalKeyValueStoresPrefixList().size)
    }

    @Test
    fun testSmartStoreOperationsWithOneUserMultipleComunities() {
        val user = createTestAccount("00Dorg", "user", null, "first@test.com")
        Assert.assertEquals("No stores should be found", 0, manager.getUserStoresPrefixList(user).size)
        val store1comm1 = createAndPopulateSmartStore("store1", user, "c1")
        Assert.assertEquals("Wrong db path", computeExpectedSmartStorePath("store1", user, "c1"), store1comm1.database.path)
        val store2comm1 = createAndPopulateSmartStore("store2", user, "c1")
        Assert.assertEquals("Wrong db path", computeExpectedSmartStorePath("store2", user, "c1"), store2comm1.database.path)
        val store1comm2 = createAndPopulateSmartStore("store1", user, "c2")
        Assert.assertEquals("Wrong db path", computeExpectedSmartStorePath("store1", user, "c2"), store1comm2.database.path)
        val store2comm2 = createAndPopulateSmartStore("store2", user, "c2")
        Assert.assertEquals("Wrong db path", computeExpectedSmartStorePath("store2", user, "c2"), store2comm2.database.path)
        Assert.assertTrue("Store should be found", manager.hasSmartStore("store1", user, "c1"))
        Assert.assertTrue("Store should be found", manager.hasSmartStore("store2", user, "c1"))
        Assert.assertTrue("Store should be found", manager.hasSmartStore("store1", user, "c2"))
        Assert.assertTrue("Store should be found", manager.hasSmartStore("store2", user, "c2"))
        Assert.assertEquals("Wrong store names", 0, manager.getUserStoresPrefixList(user).size)
        manager.removeSmartStore("store1", user, "c1")
        Assert.assertFalse("Store should no longer be found", manager.hasSmartStore("store1", user, "c1"))
        Assert.assertTrue("Store should be found", manager.hasSmartStore("store2", user, "c1"))
        Assert.assertTrue("Store should be found", manager.hasSmartStore("store1", user, "c2"))
        Assert.assertTrue("Store should be found", manager.hasSmartStore("store2", user, "c2"))
        manager.removeAllUserStores(user)
        Assert.assertFalse("Store should no longer be found", manager.hasSmartStore("store1", user, "c1"))
        Assert.assertFalse("Store should no longer be found", manager.hasSmartStore("store2", user, "c1"))
        Assert.assertFalse("Store should no longer be found", manager.hasSmartStore("store1", user, "c2"))
        Assert.assertFalse("Store should no longer be found", manager.hasSmartStore("store2", user, "c2"))
        Assert.assertEquals("Wrong store names", 0, manager.getUserStoresPrefixList(user).size)
    }

    @Test
    fun testKeyValueStoreOperationsWithOneUserMultipleComunities() {
        val user = createTestAccount("00Dorg", "user", null, "first@test.com")
        Assert.assertEquals("No stores should be found", 0, manager.getKeyValueStoresPrefixList(user).size)
        val store1comm1 = createAndPopulateKeyValueStore("store1", user, "c1")
        Assert.assertEquals("Wrong store dir", computeExpectedKeyValueStorePath("store1", user, "c1"), store1comm1.storeDir.absolutePath)
        val store2comm1 = createAndPopulateKeyValueStore("store2", user, "c1")
        Assert.assertEquals("Wrong store dir", computeExpectedKeyValueStorePath("store2", user, "c1"), store2comm1.storeDir.absolutePath)
        val store1comm2 = createAndPopulateKeyValueStore("store1", user, "c2")
        Assert.assertEquals("Wrong store dir", computeExpectedKeyValueStorePath("store1", user, "c2"), store1comm2.storeDir.absolutePath)
        val store2comm2 = createAndPopulateKeyValueStore("store2", user, "c2")
        Assert.assertEquals("Wrong store dir", computeExpectedKeyValueStorePath("store2", user, "c2"), store2comm2.storeDir.absolutePath)
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store1", user, "c1"))
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store2", user, "c1"))
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store1", user, "c2"))
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store2", user, "c2"))
        Assert.assertEquals("Wrong store names", 0, manager.getKeyValueStoresPrefixList(user).size)
        manager.removeKeyValueStore("store1", user, "c1")
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store1", user, "c1"))
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store2", user, "c1"))
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store1", user, "c2"))
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store2", user, "c2"))
        manager.removeAllKeyValueStores(user)
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store1", user, "c1"))
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store2", user, "c1"))
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store1", user, "c2"))
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store2", user, "c2"))
        Assert.assertEquals("Wrong store names", 0, manager.getKeyValueStoresPrefixList(user).size)
    }

    @Test
    fun testSmartStoreOperationsWithMultipleUsers() {
        val user1 = createTestAccount("00Dorg1", "user1", null, "first@test.com")
        val user2 = createTestAccount("00Dorg2", "user2", null, "second@test.com")
        Assert.assertEquals("No stores should be found", 0, manager.getUserStoresPrefixList(user1).size)
        Assert.assertEquals("No stores should be found", 0, manager.getUserStoresPrefixList(user2).size)
        createAndPopulateSmartStore("store1", user1, null)
        createAndPopulateSmartStore("store2", user1, null)
        createAndPopulateSmartStore("store1", user2, null)
        createAndPopulateSmartStore("store2", user2, null)
        Assert.assertTrue("Store should be found", manager.hasSmartStore("store1", user1, null))
        Assert.assertTrue("Store should be found", manager.hasSmartStore("store2", user1, null))
        Assert.assertTrue("Store should be found", manager.hasSmartStore("store1", user2, null))
        Assert.assertTrue("Store should be found", manager.hasSmartStore("store2", user2, null))
        Assert.assertEquals("Wrong store names", 2, manager.getUserStoresPrefixList(user1).size)
        Assert.assertEquals("Wrong store names", 2, manager.getUserStoresPrefixList(user2).size)
        manager.removeSmartStore("store1", user1, null)
        Assert.assertFalse("Store should no longer be found", manager.hasSmartStore("store1", user1, null))
        Assert.assertTrue("Store should be found", manager.hasSmartStore("store2", user1, null))
        Assert.assertTrue("Store should be found", manager.hasSmartStore("store1", user2, null))
        Assert.assertTrue("Store should be found", manager.hasSmartStore("store2", user2, null))
        Assert.assertEquals("Wrong store names", 1, manager.getUserStoresPrefixList(user1).size)
        Assert.assertEquals("Wrong store names", 2, manager.getUserStoresPrefixList(user2).size)
        manager.removeAllUserStores(user2)
        Assert.assertFalse("Store should no longer be found", manager.hasSmartStore("store1", user1, null))
        Assert.assertTrue("Store should be found", manager.hasSmartStore("store2", user1, null))
        Assert.assertFalse("Store should no longer be found", manager.hasSmartStore("store1", user2, null))
        Assert.assertFalse("Store should no longer be found", manager.hasSmartStore("store2", user2, null))
        Assert.assertEquals("Wrong store names", 1, manager.getUserStoresPrefixList(user1).size)
        Assert.assertEquals("Wrong store names", 0, manager.getUserStoresPrefixList(user2).size)
        manager.removeAllUserStores(user1)
        Assert.assertFalse("Store should no longer be found", manager.hasSmartStore("store1", user1, null))
        Assert.assertFalse("Store should no longer be found", manager.hasSmartStore("store2", user1, null))
        Assert.assertFalse("Store should no longer be found", manager.hasSmartStore("store1", user2, null))
        Assert.assertFalse("Store should no longer be found", manager.hasSmartStore("store2", user2, null))
        Assert.assertEquals("Wrong store names", 0, manager.getUserStoresPrefixList(user1).size)
        Assert.assertEquals("Wrong store names", 0, manager.getUserStoresPrefixList(user2).size)
    }

    @Test
    fun testKeyValueStoreOperationsWithMultipleUsers() {
        val user1 = createTestAccount("00Dorg1", "user1", null, "first@test.com")
        val user2 = createTestAccount("00Dorg2", "user2", null, "second@test.com")
        Assert.assertEquals("No stores should be found", 0, manager.getKeyValueStoresPrefixList(user1).size)
        Assert.assertEquals("No stores should be found", 0, manager.getKeyValueStoresPrefixList(user2).size)
        createAndPopulateKeyValueStore("store1", user1)
        createAndPopulateKeyValueStore("store2", user1)
        createAndPopulateKeyValueStore("store1", user2)
        createAndPopulateKeyValueStore("store2", user2)
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store1", user1))
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store2", user1))
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store1", user2))
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store2", user2))
        Assert.assertEquals("Wrong store names", 2, manager.getKeyValueStoresPrefixList(user1).size)
        Assert.assertEquals("Wrong store names", 2, manager.getKeyValueStoresPrefixList(user2).size)
        manager.removeKeyValueStore("store1", user1)
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store1", user1))
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store2", user1))
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store1", user2))
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store2", user2))
        Assert.assertEquals("Wrong store names", 1, manager.getKeyValueStoresPrefixList(user1).size)
        Assert.assertEquals("Wrong store names", 2, manager.getKeyValueStoresPrefixList(user2).size)
        manager.removeAllKeyValueStores(user2)
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store1", user1))
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store2", user1))
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store1", user2))
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store2", user2))
        Assert.assertEquals("Wrong store names", 1, manager.getKeyValueStoresPrefixList(user1).size)
        Assert.assertEquals("Wrong store names", 0, manager.getKeyValueStoresPrefixList(user2).size)
        manager.removeAllKeyValueStores(user1)
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store1", user1))
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store2", user1))
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store1", user2))
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store2", user2))
        Assert.assertEquals("Wrong store names", 0, manager.getKeyValueStoresPrefixList(user1).size)
        Assert.assertEquals("Wrong store names", 0, manager.getKeyValueStoresPrefixList(user2).size)
    }

    @Test
    fun testSmartStoreOperationsWithUserAndGlobalStores() {
        val user = createTestAccount("00Dorg", "user", null, "first@test.com")
        Assert.assertEquals("No stores should be found", 0, manager.getUserStoresPrefixList(user).size)
        Assert.assertEquals("No global stores should be found", 0, manager.getGlobalKeyValueStoresPrefixList().size)
        createAndPopulateSmartStore("store1", user)
        createAndPopulateSmartStore("store2", user)
        createAndPopulateGlobalSmartStore("store1")
        createAndPopulateGlobalSmartStore("store2")
        Assert.assertTrue("Store should be found", manager.hasSmartStore("store1", user, null))
        Assert.assertTrue("Store should be found", manager.hasSmartStore("store2", user, null))
        Assert.assertTrue("Store should be found", manager.hasGlobalSmartStore("store1"))
        Assert.assertTrue("Store should be found", manager.hasGlobalSmartStore("store2"))
        Assert.assertEquals("Wrong store names", 2, manager.getUserStoresPrefixList(user).size)
        Assert.assertEquals("Wrong store names", 2, manager.getGlobalStoresPrefixList().size)
        manager.removeSmartStore("store1", user, null)
        Assert.assertFalse("Store should no longer be found", manager.hasSmartStore("store1", user, null))
        Assert.assertTrue("Store should be found", manager.hasSmartStore("store2", user, null))
        Assert.assertTrue("Store should be found", manager.hasGlobalSmartStore("store1"))
        Assert.assertTrue("Store should be found", manager.hasGlobalSmartStore("store2"))
        Assert.assertEquals("Wrong store names", 1, manager.getUserStoresPrefixList(user).size)
        Assert.assertEquals("Wrong store names", 2, manager.getGlobalStoresPrefixList().size)
        manager.removeAllGlobalStores()
        Assert.assertFalse("Store should no longer be found", manager.hasSmartStore("store1", user, null))
        Assert.assertTrue("Store should be found", manager.hasSmartStore("store2", user, null))
        Assert.assertFalse("Store should no longer be found", manager.hasGlobalSmartStore("store1"))
        Assert.assertFalse("Store should no longer be found", manager.hasGlobalSmartStore("store2"))
        Assert.assertEquals("Wrong store names", 1, manager.getUserStoresPrefixList(user).size)
        Assert.assertEquals("Wrong store names", 0, manager.getGlobalStoresPrefixList().size)
        manager.removeAllUserStores(user)
        Assert.assertFalse("Store should no longer be found", manager.hasSmartStore("store1", user, null))
        Assert.assertFalse("Store should no longer be found", manager.hasSmartStore("store2", user, null))
        Assert.assertFalse("Store should no longer be found", manager.hasGlobalSmartStore("store1"))
        Assert.assertFalse("Store should no longer be found", manager.hasGlobalSmartStore("store2"))
        Assert.assertEquals("Wrong store names", 0, manager.getUserStoresPrefixList(user).size)
        Assert.assertEquals("Wrong store names", 0, manager.getGlobalStoresPrefixList().size)
    }

    @Test
    fun testKeyValueStoreOperationsWithUserAndGlobalStores() {
        val user = createTestAccount("00Dorg", "user", null, "first@test.com")
        Assert.assertEquals("No stores should be found", 0, manager.getKeyValueStoresPrefixList(user).size)
        Assert.assertEquals("No global stores should be found", 0, manager.getGlobalKeyValueStoresPrefixList().size)
        createAndPopulateKeyValueStore("store1", user)
        createAndPopulateKeyValueStore("store2", user)
        createAndPopulateGlobalKeyValueStore("store1")
        createAndPopulateGlobalKeyValueStore("store2")
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store1", user))
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store2", user))
        Assert.assertTrue("Store should be found", manager.hasGlobalKeyValueStore("store1"))
        Assert.assertTrue("Store should be found", manager.hasGlobalKeyValueStore("store2"))
        Assert.assertEquals("Wrong store names", 2, manager.getKeyValueStoresPrefixList(user).size)
        Assert.assertEquals("Wrong store names", 2, manager.getGlobalKeyValueStoresPrefixList().size)
        manager.removeKeyValueStore("store1", user)
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store1", user))
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store2", user))
        Assert.assertTrue("Store should be found", manager.hasGlobalKeyValueStore("store1"))
        Assert.assertTrue("Store should be found", manager.hasGlobalKeyValueStore("store2"))
        Assert.assertEquals("Wrong store names", 1, manager.getKeyValueStoresPrefixList(user).size)
        Assert.assertEquals("Wrong store names", 2, manager.getGlobalKeyValueStoresPrefixList().size)
        manager.removeAllGlobalKeyValueStores()
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store1", user))
        Assert.assertTrue("Store should be found", manager.hasKeyValueStore("store2", user))
        Assert.assertFalse("Store should no longer be found", manager.hasGlobalKeyValueStore("store1"))
        Assert.assertFalse("Store should no longer be found", manager.hasGlobalKeyValueStore("store2"))
        Assert.assertEquals("Wrong store names", 1, manager.getKeyValueStoresPrefixList(user).size)
        Assert.assertEquals("Wrong store names", 0, manager.getGlobalKeyValueStoresPrefixList().size)
        manager.removeAllKeyValueStores(user)
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store1", user))
        Assert.assertFalse("Store should no longer be found", manager.hasKeyValueStore("store2", user))
        Assert.assertFalse("Store should no longer be found", manager.hasGlobalKeyValueStore("store1"))
        Assert.assertFalse("Store should no longer be found", manager.hasGlobalKeyValueStore("store2"))
        Assert.assertEquals("Wrong store names", 0, manager.getKeyValueStoresPrefixList(user).size)
        Assert.assertEquals("Wrong store names", 0, manager.getGlobalKeyValueStoresPrefixList().size)
    }

    // Helper methods

    private fun computeExpectedSmartStorePath(storeName: String, account: UserAccount): String {
        return context.applicationInfo.dataDir + "/" + DBOpenHelper.DATABASES + "/" + storeName + account.communityLevelFilenameSuffix + ".db"
    }

    private fun computeExpectedSmartStorePath(storeName: String, account: UserAccount, communityId: String?): String {
        return context.applicationInfo.dataDir + "/" + DBOpenHelper.DATABASES + "/" + storeName + account.getCommunityLevelFilenameSuffix(communityId) + ".db"
    }

    private fun computeExpectedGlobalSmartStorePath(storeName: String): String {
        return context.applicationInfo.dataDir + "/" + DBOpenHelper.DATABASES + "/" + storeName + ".db"
    }

    private fun computeExpectedKeyValueStorePath(storeName: String, account: UserAccount): String {
        return context.applicationInfo.dataDir + "/" + KeyValueEncryptedFileStore.KEY_VALUE_STORES + "/" + storeName + account.communityLevelFilenameSuffix
    }

    private fun computeExpectedKeyValueStorePath(storeName: String, account: UserAccount, communityId: String?): String {
        return context.applicationInfo.dataDir + "/" + KeyValueEncryptedFileStore.KEY_VALUE_STORES + "/" + storeName + account.getCommunityLevelFilenameSuffix(communityId)
    }

    private fun computeExpectedGlobalKeyValueStorePath(storeName: String): String {
        return context.applicationInfo.dataDir + "/" + KeyValueEncryptedFileStore.KEY_VALUE_STORES + "/" + storeName + SmartStoreSDKManager.GLOBAL_SUFFIX
    }

    private fun createTestAccount(orgId: String, userId: String, communityId: String?, username: String): UserAccount {
        val jsonObject = JSONObject()
        jsonObject.put(UserAccount.AUTH_TOKEN, "test-auth-token")
        jsonObject.put(UserAccount.REFRESH_TOKEN, "test-refresh-token")
        jsonObject.put(UserAccount.LOGIN_SERVER, "https://test.salesforce.com")
        jsonObject.put(UserAccount.ID_URL, "https://cs1.salesforce.com/idurl")
        jsonObject.put(UserAccount.INSTANCE_SERVER, "https://cs1.salesforce.com")
        jsonObject.put(UserAccount.ORG_ID, orgId)
        jsonObject.put(UserAccount.USER_ID, userId)
        jsonObject.put(UserAccount.USERNAME, username)
        jsonObject.put(UserAccount.COMMUNITY_ID, communityId)
        jsonObject.put(UserAccount.COMMUNITY_URL, "https://mycommunity.salesforce.com")
        jsonObject.put(UserAccount.FIRST_NAME, "first-name-$username")
        jsonObject.put(UserAccount.LAST_NAME, "last-name-$username")
        jsonObject.put(UserAccount.DISPLAY_NAME, "display-name$username")
        jsonObject.put(UserAccount.EMAIL, username)
        jsonObject.put(UserAccount.PHOTO_URL, "https://cs1.salesforce.com/photourl")
        jsonObject.put(UserAccount.THUMBNAIL_URL, "https://cs1.salesforce.com/thumbnailurl")
        return UserAccount(jsonObject)
    }

    private fun createAndPopulateKeyValueStore(storeName: String, user: UserAccount): KeyValueEncryptedFileStore {
        val store = manager.getKeyValueStore(storeName, user)
        populateKeyValueStore(store)
        return store
    }

    private fun createAndPopulateKeyValueStore(storeName: String, user: UserAccount, communityId: String?): KeyValueEncryptedFileStore {
        val store = manager.getKeyValueStore(storeName, user, communityId!!)
        populateKeyValueStore(store)
        return store
    }

    private fun createAndPopulateGlobalKeyValueStore(storeName: String): KeyValueEncryptedFileStore {
        val store = manager.getGlobalKeyValueStore(storeName)
        populateKeyValueStore(store)
        return store
    }

    private fun populateKeyValueStore(store: KeyValueEncryptedFileStore) {
        store.saveValue("key1", "value1")
        store.saveValue("key2", "value2")
        store.saveValue("key3", "value3")
    }

    private fun createAndPopulateSmartStore(storeName: String, user: UserAccount): SmartStore {
        val store = manager.getSmartStore(storeName, user, null)
        populateSmartStore(store)
        return store
    }

    private fun createAndPopulateSmartStore(storeName: String, user: UserAccount, communityId: String?): SmartStore {
        val store = manager.getSmartStore(storeName, user, communityId)
        populateSmartStore(store)
        return store
    }

    private fun createAndPopulateGlobalSmartStore(storeName: String): SmartStore {
        val store = manager.getGlobalSmartStore(storeName)
        populateSmartStore(store)
        return store
    }

    private fun populateSmartStore(store: SmartStore) {
        store.registerSoup("test_soup", arrayOf(IndexSpec("key", Type.string)))
    }
}
