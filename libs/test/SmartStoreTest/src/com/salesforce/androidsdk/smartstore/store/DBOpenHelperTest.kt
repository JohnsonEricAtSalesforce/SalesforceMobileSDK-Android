/*
 * Copyright (c) 2014-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.smartstore.store

import android.content.Context
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.analytics.EventBuilderHelper
import com.salesforce.androidsdk.analytics.security.Encryptor
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Tests for obtaining and deleting databases via the DBOpenHelper.
 */
@RunWith(AndroidJUnit4::class)
@SmallTest
class DBOpenHelperTest {

    private lateinit var targetContext: Context

    @Before
    fun setUp() {
        this.targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        EventBuilderHelper.enableDisable(false)
    }

    @After
    fun tearDown() {
        val dbPath = InstrumentationRegistry.getInstrumentation().targetContext.applicationInfo.dataDir + "/databases"
        val fileDir = File(dbPath)
        DBOpenHelper.deleteAllUserDatabases(InstrumentationRegistry.getInstrumentation().targetContext)
        DBOpenHelper.deleteDatabase(InstrumentationRegistry.getInstrumentation().targetContext, DBOpenHelper.DEFAULT_DB_NAME, null, null)
        DBOpenHelper.removeAllFiles(fileDir)
    }

    /**
     * Make sure database name is correct for no account and no communityId.
     */
    @Test
    fun testGetHelperForNullAccountNullCommunityId() {
        val helper = DBOpenHelper.getOpenHelper("", targetContext, "somedb", null, null)
        val db = helper.writableDatabase
        val dbName = getBaseName(db)
        Assert.assertEquals("Database name is not correct.", "somedb.db", dbName)
    }

    /**
     * Make sure database name is correct for account without a communityId.
     */
    @Test
    fun testGetHelperForAccountNullCommunityId() {
        val testAcct = getTestUserAccount()
        val helper = DBOpenHelper.getOpenHelper("", targetContext, "somedb", testAcct, null)
        val db = helper.writableDatabase
        val dbName = getBaseName(db)
        Assert.assertTrue("Database name does not contain org id.", dbName.contains(TEST_ORG_ID))
        Assert.assertTrue("Database name does not contain user id.", dbName.contains(TEST_USER_ID))
        Assert.assertTrue("Database name does not have default internal community id.", dbName.contains(UserAccount.INTERNAL_COMMUNITY_PATH))
    }

    /**
     * Ensure that helpers are cached.
     */
    @Test
    fun testGetHelperIsCached() {
        val helper1 = DBOpenHelper.getOpenHelper("", targetContext, "somedb", null, null)
        val helper2 = DBOpenHelper.getOpenHelper("", targetContext, "somedb", null, null)
        Assert.assertSame("Helpers should be cached.", helper1, helper2)
    }

    /**
     * When a database name is not given, the default name should be used.
     */
    @Test
    fun testGetHelperUsesDefaultDatabaseName() {
        val testAcct = getTestUserAccount()
        val helper = DBOpenHelper.getOpenHelper("", targetContext, testAcct, TEST_COMMUNITY_ID)
        val db = helper.writableDatabase
        val dbName = getBaseName(db)
        Assert.assertTrue("Database name is not correct.", dbName.startsWith(DBOpenHelper.DEFAULT_DB_NAME))
        Assert.assertTrue("Database name does not contain org id.", dbName.contains(TEST_ORG_ID))
        Assert.assertTrue("Database name does not contain user id.", dbName.contains(TEST_USER_ID))
        Assert.assertTrue("Database name does not have default internal community id.", dbName.contains(TEST_COMMUNITY_ID))
    }

    /**
     * Ensures the default database is removed correctly.
     */
    @Test
    fun testDeleteDatabaseDefault() {
        // create db
        val helper = DBOpenHelper.getOpenHelper("", targetContext, DBOpenHelper.DEFAULT_DB_NAME, null, null)
        val db = helper.writableDatabase
        val dbName = getBaseName(db)
        DBOpenHelper.deleteDatabase(targetContext, DBOpenHelper.DEFAULT_DB_NAME, null, null)
        Assert.assertFalse("Database should not exist.", databaseExists(targetContext, dbName))
    }

    /**
     * Ensures all user databases are removed correctly, and the
     * global databases are retained.
     */
    @Test
    fun testDeleteAllUserDatabases() {
        val testAcct = getTestUserAccount()

        // Create a user database we want to ensure is deleted.
        val helper1 = DBOpenHelper.getOpenHelper("", targetContext, testAcct, TEST_COMMUNITY_ID)
        val db1 = helper1.writableDatabase
        val dbName1 = getBaseName(db1)

        // Create another user database we want to ensure is deleted.
        val helper2 = DBOpenHelper.getOpenHelper("", targetContext, testAcct, "other_community_id")
        val db2 = helper2.writableDatabase
        val dbName2 = getBaseName(db2)

        // Create a global database we want to ensure is not deleted.
        val helper3 = DBOpenHelper.getOpenHelper("", targetContext, DBOpenHelper.DEFAULT_DB_NAME, null, null)
        val db3 = helper3.writableDatabase
        val dbName3 = getBaseName(db3)

        // Delete all user databases.
        DBOpenHelper.deleteAllUserDatabases(targetContext)
        Assert.assertFalse("Database should have been deleted.", databaseExists(targetContext, dbName1))
        Assert.assertFalse("Database should have been deleted.", databaseExists(targetContext, dbName2))
        Assert.assertTrue("Database should not have been deleted.", databaseExists(targetContext, dbName3))

        // 1st and 2nd helpers should not be cached, but 3rd should still be cached.
        val helpers = DBOpenHelper.getOpenHelpers()
        Assert.assertNotNull("List of helpers should not be null.", helpers)
        val dbNames = helpers.keys
        Assert.assertNotNull("List of DB names should not be null.", dbNames)
        Assert.assertFalse("User database should not be cached.", dbNames.contains(dbName1))
        Assert.assertFalse("User database should not be cached.", dbNames.contains(dbName2))
        Assert.assertTrue("Global database should still be cached.", dbNames.contains(dbName3))
    }

    /**
     * Ensures the database is removed from the cache.
     */
    @Test
    fun testDeleteDatabaseRemovesFromCache() {
        val helper = DBOpenHelper.getOpenHelper("", targetContext, DBOpenHelper.DEFAULT_DB_NAME, null, null)
        DBOpenHelper.deleteDatabase(targetContext, DBOpenHelper.DEFAULT_DB_NAME, null, null)
        val helperPostDelete = DBOpenHelper.getOpenHelper("", targetContext, DBOpenHelper.DEFAULT_DB_NAME, null, null)
        Assert.assertNotSame("Helpers should be different instances.", helper, helperPostDelete)
    }

    /**
     * Ensure that only the single community-specific database is deleted.
     */
    @Test
    fun testDeleteDatabaseWithCommunityId() {
        val testAcct = getTestUserAccount()

        // create the database we want to ensure is deleted
        val helper = DBOpenHelper.getOpenHelper("", targetContext, testAcct, TEST_COMMUNITY_ID)
        helper.writableDatabase

        // create another database for this account which should not be deleted.
        val dontDeleteHelper = DBOpenHelper.getOpenHelper("", targetContext, testAcct, "other_community_id")
        val dontDeleteDb = dontDeleteHelper.writableDatabase
        val dontDeleteDbName = getBaseName(dontDeleteDb)

        // now, delete the first database and make sure we didn't remove the second.
        DBOpenHelper.deleteDatabase(targetContext, testAcct, TEST_COMMUNITY_ID)
        Assert.assertTrue("Database should not have been deleted.", databaseExists(targetContext, dontDeleteDbName))

        // 1st helper should not be cached, but 2nd should still be cached
        val helperNew = DBOpenHelper.getOpenHelper("", targetContext, testAcct, TEST_COMMUNITY_ID)
        Assert.assertNotSame("Helper should have been removed from cache.", helper, helperNew)
        val dontDeleteHelperCached = DBOpenHelper.getOpenHelper("", targetContext, testAcct, "other_community_id")
        Assert.assertSame("Helper should be same instance.", dontDeleteHelper, dontDeleteHelperCached)
    }

    /**
     * Ensure that all databases related to the account are removed when community id is not specified.
     */
    @Test
    fun testDeleteDatabaseWithoutCommunityId() {
        val testAcct = getTestUserAccount()

        // create the database we want to ensure is deleted
        val helper = DBOpenHelper.getOpenHelper("", targetContext, testAcct, TEST_COMMUNITY_ID)
        val db = helper.writableDatabase
        val dbName = getBaseName(db)

        // create another database for this account which should not be deleted.
        val helper2 = DBOpenHelper.getOpenHelper("", targetContext, testAcct, "other_community_id")
        val db2 = helper2.writableDatabase
        val dbName2 = getBaseName(db2)

        // now, delete all databases related to accounts and ensure they no longer exist
        DBOpenHelper.deleteDatabase(targetContext, testAcct)
        Assert.assertFalse("Database should not exist.", databaseExists(targetContext, dbName))
        Assert.assertFalse("Database should not exist.", databaseExists(targetContext, dbName2))

        // also make sure references to the helpers no longer exist
        val helperNew = DBOpenHelper.getOpenHelper("", targetContext, testAcct, TEST_COMMUNITY_ID)
        val helperNew2 = DBOpenHelper.getOpenHelper("", targetContext, testAcct, "other_community_id")
        Assert.assertNotSame("Helper should have been removed from cache.", helper, helperNew)
        Assert.assertNotSame("Helper should have been removed from cache.", helper2, helperNew2)
    }

    /**
     * Has smart store for given account returns true.
     */
    @Test
    fun testHasSmartStoreIsTrueForDefaultDatabase() {
        val testAcct = getTestUserAccount()
        val helper = DBOpenHelper.getOpenHelper("", targetContext, testAcct)
        helper.writableDatabase
        Assert.assertTrue("SmartStore for account should exist.", DBOpenHelper.smartStoreExists(targetContext, testAcct, null))
    }

    /**
     * Has smart store for given account returns false.
     */
    @Test
    fun testHasSmartStoreIsFalseForDefaultDatabase() {
        Assert.assertFalse("SmartStore for account should not exist.", DBOpenHelper.smartStoreExists(targetContext, null, null))
    }

    /**
     * Has smart store for specified database and account returns true.
     */
    @Test
    fun testHasSmartStoreIsTrueForSpecifiedDatabase() {
        val testAcct = getTestUserAccount()
        val helper = DBOpenHelper.getOpenHelper("", targetContext, "testdb", testAcct, null)
        helper.writableDatabase
        Assert.assertTrue("SmartStore for account should exist.", DBOpenHelper.smartStoreExists(targetContext, "testdb", testAcct, null))
    }

    /**
     * Has smart store for given account returns false.
     */
    @Test
    fun testHasSmartStoreIsFalseForSpecifiedDatabase() {
        Assert.assertFalse("SmartStore for account should not exist.", DBOpenHelper.smartStoreExists(targetContext, "dbdne", null, null))
    }

    private fun databaseExists(ctx: Context, dbName: String): Boolean {
        val dbPath = ctx.applicationInfo.dataDir + "/databases/" + dbName
        val file = File(dbPath)
        return file.exists()
    }

    private fun getTestUserAccount(): UserAccount {
        val bundle = Bundle()
        bundle.putString(UserAccount.USER_ID, TEST_USER_ID)
        bundle.putString(UserAccount.ORG_ID, TEST_ORG_ID)
        return UserAccount(bundle)
    }

    private fun getBaseName(db: SQLiteDatabase): String {
        val pathParts = db.path?.split("/") ?: emptyList()
        return pathParts[pathParts.size - 1]
    }

    companion object {
        // constants used for building a test user account
        private const val TEST_USER_ID = "005123"
        private const val TEST_ORG_ID = "00D123"
        private const val TEST_COMMUNITY_ID = "cid123"

        private const val TEST_SOUP = "test_soup"
        private const val TEST_SOUP_2 = "test_soup_2"
        private const val TEST_DB = "test_db"
        private val PASSCODE = Encryptor.hash("test_key", "hashing-key")
    }
}
