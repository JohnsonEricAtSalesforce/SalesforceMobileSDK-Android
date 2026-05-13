/*
 * Copyright (c) 2011-present, salesforce.com, inc.
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

import android.database.Cursor
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.salesforce.androidsdk.smartstore.store.QuerySpec.Order
import com.salesforce.androidsdk.smartstore.store.SmartStore.Type
import com.salesforce.androidsdk.util.JSONTestHelper
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*

/**
 * Main test suite for SmartStore
 */
@RunWith(AndroidJUnit4::class)
@MediumTest
class SmartStoreTest : SmartStoreTestCase() {

    companion object {
        protected const val TEST_SOUP = "test_soup"
        protected const val OTHER_TEST_SOUP = "other_test_soup"
        private const val THIRD_TEST_SOUP = "third_test_soup"
        private const val FOURTH_TEST_SOUP = "fourth_test_soup"
    }

    @Before
    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()
        store.setCaptureExplainQueryPlan(true)
        Assert.assertFalse("Table for test_soup should not exist", hasTable("TABLE_1"))
        Assert.assertFalse("Soup test_soup should not exist", store.hasSoup(TEST_SOUP))
        registerSoup(store, TEST_SOUP, arrayOf(IndexSpec("key", Type.string)))
        Assert.assertEquals("Table for test_soup was expected to be called TABLE_1", "TABLE_1", getSoupTableName(TEST_SOUP))
        Assert.assertTrue("Table for test_soup should now exist", hasTable("TABLE_1"))
        Assert.assertTrue("Soup test_soup should now exist", store.hasSoup(TEST_SOUP))
    }

    @After
    @Throws(Exception::class)
    override fun tearDown() {
        super.tearDown()
    }

    override val encryptionKey: String
        get() = "test123"

    /**
     * Checking compile options
     */
    @Test
    fun testCompileOptions() {
        val compileOptions = store.getCompileOptions()
        Assert.assertTrue("ENABLE_FTS4 flag not found in compile options", compileOptions.contains("ENABLE_FTS4"))
        Assert.assertTrue("ENABLE_FTS3_PARENTHESIS flag not found in compile options", compileOptions.contains("ENABLE_FTS3_PARENTHESIS"))
        Assert.assertTrue("ENABLE_FTS5 flag not found in compile options", compileOptions.contains("ENABLE_FTS5"))
    }

    /**
     * Checking runtime settings
     */
    @Test
    fun testRuntimeSettings() {
        val settings = store.getRuntimeSettings()
        // Make sure run time settings are 4.x settings except for kdf_iter
        Assert.assertTrue("Wrong kdf_iter", settings.contains("PRAGMA kdf_iter = 4000;"))
        Assert.assertTrue("Wrong cipher_page_size", settings.contains("PRAGMA cipher_page_size = 4096;"))
        Assert.assertTrue("Wrong cipher_user_hmac", settings.contains("PRAGMA cipher_use_hmac = 1;"))
        Assert.assertTrue("Wrong cipher_plaintext_header_size", settings.contains("PRAGMA cipher_plaintext_header_size = 0;"))
        Assert.assertTrue("Wrong cipher_hmac_algorithm", settings.contains("PRAGMA cipher_hmac_algorithm = HMAC_SHA512;"))
        Assert.assertTrue("Wrong cipher_kdf_algorithm", settings.contains("PRAGMA cipher_kdf_algorithm = PBKDF2_HMAC_SHA512;"))
    }

    /**
     * Checking sqlcipher version
     */
    @Test
    fun testSQLCipherVersion() {
        Assert.assertEquals("Wrong sqlcipher version", "4.10.0 community", store.getSQLCipherVersion())
    }

    /**
     * Checking sqlcipher provider version
     */
    @Test
    fun testCipherProviderVersion() {
        Assert.assertEquals("Wrong sqlcipher provider version", "OpenSSL 3.0.17 1 Jul 2025", store.getCipherProviderVersion())
    }

    /**
     * Checking sqlcipher FIPS status
     */
    @Test
    fun testCipherFIPSStatus() {
        Assert.assertFalse("Wrong sqlcipher FIPS status", store.getCipherFIPSStatus())
    }


    /**
     * Method to check soup blob with one stored by db. Can be overridden to check external storage if necessary.
     */
    @Throws(JSONException::class)
    protected open fun assertSameSoupAsDB(soup: JSONObject, c: Cursor, soupName: String, id: Long) {
        JSONTestHelper.assertSameJSON("Wrong value in soup column", soup, JSONObject(c.getString(c.getColumnIndex("soup"))))
    }

    /**
     * Testing method with paths to top level string/integer/array/map as well as edge cases (null object/null or empty path)
     *
     * @throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testProjectTopLevel() {
        val json = JSONObject("{'a':'va', 'b':2, 'c':[0,1,2], 'd': {'d1':'vd1', 'd2':'vd2', 'd3':[1,2], 'd4':{'e':5}}}")

        // Null object
        Assert.assertNull("Should have been null", SmartStore.project(null, "path"))

        // Root
        JSONTestHelper.assertSameJSON("Should have returned whole object", json, SmartStore.project(json, ""))
        JSONTestHelper.assertSameJSON("Should have returned whole object", json, SmartStore.project(json, ""))

        // Top-level elements
        Assert.assertEquals("Wrong value for key a", "va", SmartStore.project(json, "a"))
        Assert.assertEquals("Wrong value for key b", 2, SmartStore.project(json, "b"))
        JSONTestHelper.assertSameJSON("Wrong value for key c", JSONArray("[0,1,2]"), SmartStore.project(json, "c"))
        JSONTestHelper.assertSameJSON("Wrong value for key d", JSONObject("{'d1':'vd1','d2':'vd2','d3':[1,2],'d4':{'e':5}}"), SmartStore.project(json, "d") as JSONObject)
    }

    /**
     * Testing method with paths to non-top level string/integer/array/map
     *
     * @throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testProjectNested() {
        val json = JSONObject("{'a':'va', 'b':2, 'c':[0,1,2], 'd': {'d1':'vd1', 'd2':'vd2', 'd3':[1,2], 'd4':{'e':5}}}")

        // Nested elements
        Assert.assertEquals("Wrong value for key d.d1", "vd1", SmartStore.project(json, "d.d1"))
        Assert.assertEquals("Wrong value for key d.d2", "vd2", SmartStore.project(json, "d.d2"))
        JSONTestHelper.assertSameJSON("Wrong value for key d.d3", JSONArray("[1,2]"), SmartStore.project(json, "d.d3"))
        JSONTestHelper.assertSameJSON("Wrong value for key d.d4", JSONObject("{'e':5}"), SmartStore.project(json, "d.d4"))
        Assert.assertEquals("Wrong value for key d.d4.e", 5, SmartStore.project(json, "d.d4.e"))
    }

    /**
     * Testing method with path through arrays
     *
     * @throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testProjectThroughArrays() {
        val json = JSONObject("{\"a\":\"a1\", \"b\":2, \"c\":[{\"cc\":\"cc1\"}, {\"cc\":2}, {\"cc\":[1,2,3]}, {}, {\"cc\":{\"cc5\":5}}], \"d\":[{\"dd\":[{\"ddd\":\"ddd11\"},{\"ddd\":\"ddd12\"}]}, {\"dd\":[{\"ddd\":\"ddd21\"}]}, {\"dd\":[{\"ddd\":\"ddd31\"},{\"ddd3\":\"ddd32\"}]}]}")
        JSONTestHelper.assertSameJSON("Wrong value for key c", JSONArray("[{\"cc\":\"cc1\"}, {\"cc\":2}, {\"cc\":[1,2,3]}, {}, {\"cc\":{\"cc5\":5}}]"), SmartStore.project(json, "c"))
        JSONTestHelper.assertSameJSON("Wrong value for key c.cc", JSONArray("[\"cc1\",2, [1,2,3], {\"cc5\":5}]"), SmartStore.project(json, "c.cc"))
        JSONTestHelper.assertSameJSON("Wrong value for key c.cc.cc5", JSONArray("[5]"), SmartStore.project(json, "c.cc.cc5"))
        JSONTestHelper.assertSameJSON("Wrong value for key d", JSONArray("[{\"dd\":[{\"ddd\":\"ddd11\"},{\"ddd\":\"ddd12\"}]}, {\"dd\":[{\"ddd\":\"ddd21\"}]}, {\"dd\":[{\"ddd\":\"ddd31\"},{\"ddd3\":\"ddd32\"}]}]"), SmartStore.project(json, "d"))
        JSONTestHelper.assertSameJSON("Wrong value for key d.dd", JSONArray("[[{\"ddd\":\"ddd11\"},{\"ddd\":\"ddd12\"}], [{\"ddd\":\"ddd21\"}], [{\"ddd\":\"ddd31\"},{\"ddd3\":\"ddd32\"}]]"), SmartStore.project(json, "d.dd"))
        JSONTestHelper.assertSameJSON("Wrong value for key d.dd.ddd", JSONArray("[[\"ddd11\",\"ddd12\"],[\"ddd21\"],[\"ddd31\"]]"), SmartStore.project(json, "d.dd.ddd"))
        JSONTestHelper.assertSameJSON("Wrong value for key d.dd.ddd3", JSONArray("[[\"ddd32\"]]"), SmartStore.project(json, "d.dd.ddd3"))
    }

    /**
     * Making sure projectReturningNULLObject:
     * - returns JSONObject.NULL if the node is found but has the value null
     * - returns null if the node is not found
     */
    @Test
    @Throws(JSONException::class)
    fun testProjectMissingVsSetToNull() {
        val json = JSONObject("{\"a\":null, \"b\":{\"bb\":null}, \"c\":{\"cc\":{\"ccc\":null}}}")
        Assert.assertEquals(JSONObject.NULL, SmartStore.projectReturningNULLObject(json, "a"))
        Assert.assertEquals(JSONObject.NULL, SmartStore.projectReturningNULLObject(json, "b.bb"))
        Assert.assertEquals(JSONObject.NULL, SmartStore.projectReturningNULLObject(json, "c.cc.ccc"))
        Assert.assertEquals(null, SmartStore.projectReturningNULLObject(json, "a1"))
        Assert.assertEquals(null, SmartStore.projectReturningNULLObject(json, "b.bb1"))
        Assert.assertEquals(null, SmartStore.projectReturningNULLObject(json, "c.cc.ccc1"))
    }

    /**
     * Check that the meta data table (soup index map) has been created
     */
    @Test
    fun testMetaDataTableCreated() {
        Assert.assertTrue("Table soup_index_map not found", hasTable("soup_index_map"))
    }

    /**
     * Test register/drop soup
     */
    @Test
    fun testRegisterDropSoup() {

        // Before
        Assert.assertNull("getSoupTableName should have returned null", getSoupTableName(THIRD_TEST_SOUP))
        Assert.assertFalse("Soup third_test_soup should not exist", store.hasSoup(THIRD_TEST_SOUP))

        // Register
        registerSoup(store, THIRD_TEST_SOUP, arrayOf(IndexSpec("key", Type.string), IndexSpec("value", Type.string)))
        val soupTableName = getSoupTableName(THIRD_TEST_SOUP)
        Assert.assertEquals("getSoupTableName should have returned TABLE_2", "TABLE_2", soupTableName)
        Assert.assertTrue("Table for soup third_test_soup does exist", hasTable(soupTableName!!))
        Assert.assertTrue("Register soup call failed", store.hasSoup(THIRD_TEST_SOUP))

        // Check soup indexes
        val indexSpecs = store.getSoupIndexSpecs(THIRD_TEST_SOUP)
        Assert.assertEquals("Wrong path", "key", indexSpecs[0].path)
        Assert.assertEquals("Wrong type", Type.string, indexSpecs[0].type)
        Assert.assertEquals("Wrong column name", soupTableName + "_0", indexSpecs[0].columnName)
        Assert.assertEquals("Wrong path", "value", indexSpecs[1].path)
        Assert.assertEquals("Wrong type", Type.string, indexSpecs[1].type)
        Assert.assertEquals("Wrong column name", soupTableName + "_1", indexSpecs[1].columnName)

        // Check db indexes
        checkDatabaseIndexes(soupTableName!!, listOf(
            "CREATE INDEX " + soupTableName + "_0_idx on " + soupTableName + " ( " + soupTableName + "_0 )",
            "CREATE INDEX " + soupTableName + "_1_idx on " + soupTableName + " ( " + soupTableName + "_1 )",
            "CREATE INDEX " + soupTableName + "_created_idx on " + soupTableName + " ( created )",
            "CREATE INDEX " + soupTableName + "_lastModified_idx on " + soupTableName + " ( lastModified )"
        ))

        // Drop
        store.dropSoup(THIRD_TEST_SOUP)

        // After
        Assert.assertFalse("Soup third_test_soup should no longer exist", store.hasSoup(THIRD_TEST_SOUP))
        Assert.assertNull("getSoupTableName should have returned null", getSoupTableName(THIRD_TEST_SOUP))
        Assert.assertFalse("Table for soup third_test_soup does exist", hasTable(soupTableName!!))
    }

    /**
     * Testing getAllSoupNames: register a new soup and then drop it and call getAllSoupNames before and after
     */
    @Test
    fun testGetAllSoupNames() {

        // Before
        Assert.assertEquals("One soup name expected", 1, store.getAllSoupNames().size)
        Assert.assertTrue(TEST_SOUP + " should have been returned by getAllSoupNames", store.getAllSoupNames().contains(TEST_SOUP))

        // Register another soup
        registerSoup(store, THIRD_TEST_SOUP, arrayOf(IndexSpec("key", Type.string), IndexSpec("value", Type.string)))
        Assert.assertEquals("Two soup names expected", 2, store.getAllSoupNames().size)
        Assert.assertTrue(TEST_SOUP + " should have been returned by getAllSoupNames", store.getAllSoupNames().contains(TEST_SOUP))
        Assert.assertTrue(THIRD_TEST_SOUP + " should have been returned by getAllSoupNames", store.getAllSoupNames().contains(THIRD_TEST_SOUP))

        // Drop the latest soup
        store.dropSoup(THIRD_TEST_SOUP)
        Assert.assertEquals("One soup name expected", 1, store.getAllSoupNames().size)
        Assert.assertTrue(TEST_SOUP + " should have been returned by getAllSoupNames", store.getAllSoupNames().contains(TEST_SOUP))
    }

    /**
     * Testing dropAllSoups: register a couple of soups then drop them all
     */
    @Test
    fun testDropAllSoups() {

        // Register another soup
        Assert.assertEquals("One soup name expected", 1, store.getAllSoupNames().size)
        registerSoup(store, THIRD_TEST_SOUP, arrayOf(IndexSpec("key", Type.string), IndexSpec("value", Type.string)))
        Assert.assertEquals("Two soup names expected", 2, store.getAllSoupNames().size)

        // Drop all
        store.dropAllSoups()
        Assert.assertEquals("No soup name expected", 0, store.getAllSoupNames().size)
        Assert.assertFalse("Soup " + THIRD_TEST_SOUP + " should no longer exist", store.hasSoup(THIRD_TEST_SOUP))
        Assert.assertFalse("Soup " + TEST_SOUP + " should no longer exist", store.hasSoup(TEST_SOUP))
    }

    /**
     * Testing create: create a single element with a single index pointing to a top level attribute
     *
     * @throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testCreateOne() {
        val soupElt = JSONObject("{'key':'ka', 'value':'va'}")
        val soupEltCreated = store.create(TEST_SOUP, soupElt)!!

        // Check DB
        var c: Cursor? = null
        try {
            val db = dbOpenHelper.writableDatabase
            val soupTableName = getSoupTableName(TEST_SOUP)
            c = DBHelper.getInstance(db).query(db, soupTableName!!, null, null, null, null)
            Assert.assertTrue("Expected a soup element", c.moveToFirst())
            Assert.assertEquals("Expected one soup element only", 1, c.count)
            Assert.assertEquals("Wrong id", idOf(soupEltCreated!!), c.getLong(c.getColumnIndex("id")))
            Assert.assertEquals("Wrong created date", soupEltCreated.getLong(SmartStore.SOUP_LAST_MODIFIED_DATE), c.getLong(c.getColumnIndex("lastModified")))
            Assert.assertEquals("Wrong value in index column", "ka", c.getString(c.getColumnIndex(soupTableName!! + "_0")))
            assertSameSoupAsDB(soupEltCreated, c, soupTableName!!, idOf(soupEltCreated!!))
            Assert.assertEquals("Created date and last modified date should be equal", c.getLong(c.getColumnIndex("created")), c.getLong(c.getColumnIndex("lastModified")))
        } finally {
            safeClose(c)
        }
    }

    /**
     * Testing create: create multiple elements with multiple indices not just pointing to top level attributes
     *
     * @throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testCreateMultiple() {
        Assert.assertFalse("Soup other_test_soup should not exist", store.hasSoup(OTHER_TEST_SOUP))
        registerSoup(store, OTHER_TEST_SOUP, arrayOf(IndexSpec("lastName", Type.string), IndexSpec("address.city", Type.string)))
        Assert.assertTrue("Register soup call failed", store.hasSoup(OTHER_TEST_SOUP))
        val soupElt1 = JSONObject("{'lastName':'Doe', 'address':{'city':'San Francisco','street':'1 market'}}")
        val soupElt2 = JSONObject("{'lastName':'Jackson', 'address':{'city':'Los Angeles','street':'100 mission'}}")
        val soupElt3 = JSONObject("{'lastName':'Watson', 'address':{'city':'London','street':'50 market'}}")
        val soupElt1Created = store.create(OTHER_TEST_SOUP, soupElt1)!!
        val soupElt2Created = store.create(OTHER_TEST_SOUP, soupElt2)!!
        val soupElt3Created = store.create(OTHER_TEST_SOUP, soupElt3)!!

        // Check DB
        var c: Cursor? = null
        try {
            val soupTableName = getSoupTableName(OTHER_TEST_SOUP)
            Assert.assertEquals("Table for other_test_soup was expected to be called TABLE_2", "TABLE_2", soupTableName)
            Assert.assertTrue("Table for other_test_soup should now exist", hasTable("TABLE_2"))
            val db = dbOpenHelper.writableDatabase
            c = DBHelper.getInstance(db).query(db, soupTableName!!, null, "id ASC", null, null)
            Assert.assertTrue("Expected a soup element", c.moveToFirst())
            Assert.assertEquals("Expected three soup elements", 3, c.count)
            Assert.assertEquals("Wrong id", idOf(soupElt1Created!!), c.getLong(c.getColumnIndex("id")))
            Assert.assertEquals("Wrong created date", soupElt1Created.getLong(SmartStore.SOUP_LAST_MODIFIED_DATE), c.getLong(c.getColumnIndex("lastModified")))
            Assert.assertEquals("Wrong value in index column", "Doe", c.getString(c.getColumnIndex(soupTableName!! + "_0")))
            Assert.assertEquals("Wrong value in index column", "San Francisco", c.getString(c.getColumnIndex(soupTableName!! + "_1")))
            assertSameSoupAsDB(soupElt1Created, c, soupTableName!!, idOf(soupElt1Created!!))
            c.moveToNext()
            Assert.assertEquals("Wrong id", idOf(soupElt2Created!!), c.getLong(c.getColumnIndex("id")))
            Assert.assertEquals("Wrong created date", soupElt2Created.getLong(SmartStore.SOUP_LAST_MODIFIED_DATE), c.getLong(c.getColumnIndex("lastModified")))
            Assert.assertEquals("Wrong value in index column", "Jackson", c.getString(c.getColumnIndex(soupTableName!! + "_0")))
            Assert.assertEquals("Wrong value in index column", "Los Angeles", c.getString(c.getColumnIndex(soupTableName!! + "_1")))
            assertSameSoupAsDB(soupElt2Created, c, soupTableName!!, idOf(soupElt2Created!!))
            c.moveToNext()
            Assert.assertEquals("Wrong id", idOf(soupElt3Created!!), c.getLong(c.getColumnIndex("id")))
            Assert.assertEquals("Wrong created date", soupElt3Created.getLong(SmartStore.SOUP_LAST_MODIFIED_DATE), c.getLong(c.getColumnIndex("lastModified")))
            Assert.assertEquals("Wrong value in index column", "Watson", c.getString(c.getColumnIndex(soupTableName!! + "_0")))
            Assert.assertEquals("Wrong value in index column", "London", c.getString(c.getColumnIndex(soupTableName!! + "_1")))
            assertSameSoupAsDB(soupElt3Created, c, soupTableName!!, idOf(soupElt3Created!!))
        } finally {
            safeClose(c)
        }
    }

    /**
     * Testing update: create multiple soup elements and update one of them, check them all
     *
     * @throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testUpdate() {
        val soupElt1 = JSONObject("{'key':'ka1', 'value':'va1'}")
        val soupElt2 = JSONObject("{'key':'ka2', 'value':'va2'}")
        val soupElt3 = JSONObject("{'key':'ka3', 'value':'va3'}")
        val soupElt1Created = store.create(TEST_SOUP, soupElt1)!!
        val soupElt2Created = store.create(TEST_SOUP, soupElt2)!!
        val soupElt3Created = store.create(TEST_SOUP, soupElt3)!!
        SystemClock.sleep(10) // to get a different last modified date
        val soupElt2ForUpdate = JSONObject("{'key':'ka2u', 'value':'va2u'}")
        val soupElt2Updated = store.update(TEST_SOUP, soupElt2ForUpdate, idOf(soupElt2Created!!))
        val soupElt1Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt1Created!!)).getJSONObject(0)
        val soupElt2Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt2Created!!)).getJSONObject(0)
        val soupElt3Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt3Created!!)).getJSONObject(0)
        JSONTestHelper.assertSameJSON("Retrieve mismatch", soupElt1Created!!, soupElt1Retrieved!!)
        JSONTestHelper.assertSameJSON("Retrieve mismatch", soupElt2Updated!!, soupElt2Retrieved!!)
        JSONTestHelper.assertSameJSON("Retrieve mismatch", soupElt3Created!!, soupElt3Retrieved!!)

        // Check DB
        var c: Cursor? = null
        try {
            val db = dbOpenHelper.writableDatabase
            val soupTableName = getSoupTableName(TEST_SOUP)
            c = DBHelper.getInstance(db).query(db, soupTableName!!, null, "id ASC", null, null)
            Assert.assertTrue("Expected a soup element", c.moveToFirst())
            Assert.assertEquals("Expected three soup elements", 3, c.count)
            Assert.assertEquals("Wrong id", idOf(soupElt1Created!!), c.getLong(c.getColumnIndex("id")))
            Assert.assertEquals("Wrong created date", soupElt1Created.getLong(SmartStore.SOUP_LAST_MODIFIED_DATE), c.getLong(c.getColumnIndex("lastModified")))
            Assert.assertEquals("Created date and last modified date should be equal", c.getLong(c.getColumnIndex("created")), c.getLong(c.getColumnIndex("lastModified")))
            c.moveToNext()
            Assert.assertEquals("Wrong id", idOf(soupElt2Created!!), c.getLong(c.getColumnIndex("id")))
            Assert.assertEquals("Wrong created date", soupElt2Updated.getLong(SmartStore.SOUP_LAST_MODIFIED_DATE), c.getLong(c.getColumnIndex("lastModified")))
            Assert.assertTrue("Last modified date should be more recent than created date", c.getLong(c.getColumnIndex("created")) < c.getLong(c.getColumnIndex("lastModified")))
            c.moveToNext()
            Assert.assertEquals("Wrong id", idOf(soupElt3Created!!), c.getLong(c.getColumnIndex("id")))
            Assert.assertEquals("Wrong created date", soupElt3Created.getLong(SmartStore.SOUP_LAST_MODIFIED_DATE), c.getLong(c.getColumnIndex("lastModified")))
            Assert.assertEquals("Created date and last modified date should be equal", c.getLong(c.getColumnIndex("created")), c.getLong(c.getColumnIndex("lastModified")))
        } finally {
            safeClose(c)
        }
    }

    /**
     * Testing upsert: upsert multiple soup elements and re-upsert one of them, check them all
     *
     * @throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testUpsert() {
        val soupElt1 = JSONObject("{'key':'ka1', 'value':'va1'}")
        val soupElt2 = JSONObject("{'key':'ka2', 'value':'va2'}")
        val soupElt3 = JSONObject("{'key':'ka3', 'value':'va3'}")
        val soupElt1Upserted = store.upsert(TEST_SOUP, soupElt1)
        val soupElt2Upserted = store.upsert(TEST_SOUP, soupElt2)
        val soupElt3Upserted = store.upsert(TEST_SOUP, soupElt3)
        SystemClock.sleep(10) // to get a different last modified date
        val soupElt2ForUpdate = JSONObject("{'key':'ka2u', 'value':'va2u', '_soupEntryId': " + idOf(soupElt2Upserted!!) + "}")
        val soupElt2Updated = store.upsert(TEST_SOUP, soupElt2ForUpdate)
        val soupElt1Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt1Upserted!!)).getJSONObject(0)
        val soupElt2Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt2Upserted!!)).getJSONObject(0)
        val soupElt3Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt3Upserted!!)).getJSONObject(0)
        JSONTestHelper.assertSameJSON("Retrieve mismatch", soupElt1Upserted!!, soupElt1Retrieved!!)
        JSONTestHelper.assertSameJSON("Retrieve mismatch", soupElt2Updated!!, soupElt2Retrieved!!)
        JSONTestHelper.assertSameJSON("Retrieve mismatch", soupElt3Upserted!!, soupElt3Retrieved!!)

        // Check DB
        var c: Cursor? = null
        try {
            val db = dbOpenHelper.writableDatabase
            val soupTableName = getSoupTableName(TEST_SOUP)
            c = DBHelper.getInstance(db).query(db, soupTableName!!, null, "id ASC", null, null)
            Assert.assertTrue("Expected a soup element", c.moveToFirst())
            Assert.assertEquals("Expected three soup elements", 3, c.count)
            Assert.assertEquals("Wrong id", idOf(soupElt1Upserted!!), c.getLong(c.getColumnIndex("id")))
            Assert.assertEquals("Wrong created date", soupElt1Upserted.getLong(SmartStore.SOUP_LAST_MODIFIED_DATE), c.getLong(c.getColumnIndex("lastModified")))
            Assert.assertEquals("Created date and last modified date should be equal", c.getLong(c.getColumnIndex("created")), c.getLong(c.getColumnIndex("lastModified")))
            c.moveToNext()
            Assert.assertEquals("Wrong id", idOf(soupElt2Upserted!!), c.getLong(c.getColumnIndex("id")))
            Assert.assertEquals("Wrong created date", soupElt2Updated.getLong(SmartStore.SOUP_LAST_MODIFIED_DATE), c.getLong(c.getColumnIndex("lastModified")))
            Assert.assertTrue("Last modified date should be more recent than created date", c.getLong(c.getColumnIndex("created")) < c.getLong(c.getColumnIndex("lastModified")))
            c.moveToNext()
            Assert.assertEquals("Wrong id", idOf(soupElt3Upserted!!), c.getLong(c.getColumnIndex("id")))
            Assert.assertEquals("Wrong created date", soupElt3Upserted.getLong(SmartStore.SOUP_LAST_MODIFIED_DATE), c.getLong(c.getColumnIndex("lastModified")))
            Assert.assertEquals("Created date and last modified date should be equal", c.getLong(c.getColumnIndex("created")), c.getLong(c.getColumnIndex("lastModified")))
        } finally {
            safeClose(c)
        }
    }

    /**
     * Testing upsert with external id: upsert multiple soup elements and re-upsert one of them, check them all
     *
     * @throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testUpsertWithExternalId() {
        val soupElt1 = JSONObject("{'key':'ka1', 'value':'va1'}")
        val soupElt2 = JSONObject("{'key':'ka2', 'value':'va2'}")
        val soupElt3 = JSONObject("{'key':'ka3', 'value':'va3'}")
        val soupElt1Upserted = store.upsert(TEST_SOUP, soupElt1, "key")
        val soupElt2Upserted = store.upsert(TEST_SOUP, soupElt2, "key")
        val soupElt3Upserted = store.upsert(TEST_SOUP, soupElt3, "key")
        SystemClock.sleep(10) // to get a different last modified date
        val soupElt2ForUpdate = JSONObject("{'key':'ka2', 'value':'va2u'}")
        val soupElt2Updated = store.upsert(TEST_SOUP, soupElt2ForUpdate, "key")
        val soupElt1Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt1Upserted!!)).getJSONObject(0)
        val soupElt2Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt2Upserted!!)).getJSONObject(0)
        val soupElt3Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt3Upserted!!)).getJSONObject(0)
        JSONTestHelper.assertSameJSON("Retrieve mismatch", soupElt1Upserted!!, soupElt1Retrieved!!)
        JSONTestHelper.assertSameJSON("Retrieve mismatch", soupElt2Updated!!, soupElt2Retrieved!!)
        JSONTestHelper.assertSameJSON("Retrieve mismatch", soupElt3Upserted!!, soupElt3Retrieved!!)

        // Check DB
        var c: Cursor? = null
        try {
            val db = dbOpenHelper.writableDatabase
            val soupTableName = getSoupTableName(TEST_SOUP)
            c = DBHelper.getInstance(db).query(db, soupTableName!!, null, "id ASC", null, null)
            Assert.assertTrue("Expected a soup element", c.moveToFirst())
            Assert.assertEquals("Expected three soup elements", 3, c.count)
            Assert.assertEquals("Wrong id", idOf(soupElt1Upserted!!), c.getLong(c.getColumnIndex("id")))
            Assert.assertEquals("Wrong created date", soupElt1Upserted.getLong(SmartStore.SOUP_LAST_MODIFIED_DATE), c.getLong(c.getColumnIndex("lastModified")))
            Assert.assertEquals("Created date and last modified date should be equal", c.getLong(c.getColumnIndex("created")), c.getLong(c.getColumnIndex("lastModified")))
            c.moveToNext()
            Assert.assertEquals("Wrong id", idOf(soupElt2Upserted!!), c.getLong(c.getColumnIndex("id")))
            Assert.assertEquals("Wrong created date", soupElt2Updated.getLong(SmartStore.SOUP_LAST_MODIFIED_DATE), c.getLong(c.getColumnIndex("lastModified")))
            Assert.assertTrue("Last modified date should be more recent than created date", c.getLong(c.getColumnIndex("created")) < c.getLong(c.getColumnIndex("lastModified")))
            c.moveToNext()
            Assert.assertEquals("Wrong id", idOf(soupElt3Upserted!!), c.getLong(c.getColumnIndex("id")))
            Assert.assertEquals("Wrong created date", soupElt3Upserted.getLong(SmartStore.SOUP_LAST_MODIFIED_DATE), c.getLong(c.getColumnIndex("lastModified")))
            Assert.assertEquals("Created date and last modified date should be equal", c.getLong(c.getColumnIndex("created")), c.getLong(c.getColumnIndex("lastModified")))
        } finally {
            safeClose(c)
        }
    }

    /**
     * Testing upsert passing a non-indexed path for the external id (should fail)
     *
     * @throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testUpsertWithNonIndexedExternalId() {
        val soupElt = JSONObject("{'key':'ka1', 'value':'va1'}")
        try {
            store.upsert(TEST_SOUP, soupElt, "value")
            Assert.fail("Exception was expected: value is not an indexed field")
        } catch (e: RuntimeException) {
            Assert.assertTrue("Wrong exception", e.message!!.contains("does not have an index"))
        }
    }

    /**
     * Testing upsert by user-defined external id without value (should fail)
     *
     * @throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testUpsertByUserDefinedExternalIdWithoutValue() {
        val soupElt = JSONObject("{'value':'va1'}")
        try {
            store.upsert(TEST_SOUP, soupElt, "key")
            Assert.fail("Exception was expected: value cannot be empty for upsert by user-defined external id")
        } catch (e: RuntimeException) {
            Assert.assertTrue("Wrong exception",
                e.message!!.contains("For upsert with external ID path")
                        && e.message!!.contains("value cannot be empty for any entries"))
        }
    }

    /**
     * Testing upsert with an external id that is not unique in the soup
     *
     * @throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testUpsertWithNonUniqueExternalId() {
        val soupElt1 = JSONObject("{'key':'ka', 'value':'va1'}")
        val soupElt2 = JSONObject("{'key':'ka', 'value':'va2'}")
        val soupElt3 = JSONObject("{'key':'ka', 'value':'va3'}")
        val soupElt1Upserted = store.upsert(TEST_SOUP, soupElt1)
        val soupElt2Upserted = store.upsert(TEST_SOUP, soupElt2)
        val soupElt1Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt1Upserted!!))!!.getJSONObject(0)
        val soupElt2Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt2Upserted!!))!!.getJSONObject(0)
        JSONTestHelper.assertSameJSON("Retrieve mismatch", soupElt1Upserted!!, soupElt1Retrieved!!)
        JSONTestHelper.assertSameJSON("Retrieve mismatch", soupElt2Upserted!!, soupElt2Retrieved!!)
        try {
            store.upsert(TEST_SOUP, soupElt3, "key")
            Assert.fail("Exception was expected: key is not unique in the soup")
        } catch (e: RuntimeException) {
            Assert.assertTrue("Wrong exception", e.message!!.contains("are more than one soup elements"))
        }
    }

    /**
     * Testing retrieve: create multiple soup elements and retrieves them back
     *
     * @throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testRetrieve() {
        val soupElt1 = JSONObject("{'key':'ka1', 'value':'va1'}")
        val soupElt2 = JSONObject("{'key':'ka2', 'value':'va2'}")
        val soupElt3 = JSONObject("{'key':'ka3', 'value':'va3'}")
        val soupElt1Created = store.create(TEST_SOUP, soupElt1)!!
        val soupElt2Created = store.create(TEST_SOUP, soupElt2)!!
        val soupElt3Created = store.create(TEST_SOUP, soupElt3)!!
        val soupElt1Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt1Created!!)).getJSONObject(0)
        val soupElt2Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt2Created!!)).getJSONObject(0)
        val soupElt3Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt3Created!!)).getJSONObject(0)
        JSONTestHelper.assertSameJSON("Retrieve mismatch", soupElt1Created!!, soupElt1Retrieved!!)
        JSONTestHelper.assertSameJSON("Retrieve mismatch", soupElt2Created!!, soupElt2Retrieved!!)
        JSONTestHelper.assertSameJSON("Retrieve mismatch", soupElt3Created!!, soupElt3Retrieved!!)
    }

    /**
     * Testing delete: create soup elements, delete element by id and check database directly that it is in fact gone
     *
     * @throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testDelete() {
        val soupElt1 = JSONObject("{'key':'ka1', 'value':'va1'}")
        val soupElt2 = JSONObject("{'key':'ka2', 'value':'va2'}")
        val soupElt3 = JSONObject("{'key':'ka3', 'value':'va3'}")
        val soupElt1Created = store.create(TEST_SOUP, soupElt1)!!
        val soupElt2Created = store.create(TEST_SOUP, soupElt2)!!
        val soupElt3Created = store.create(TEST_SOUP, soupElt3)!!
        store.delete(TEST_SOUP, idOf(soupElt2Created!!))
        val soupElt1Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt1Created!!)).getJSONObject(0)
        val soupElt2Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt2Created!!))
        val soupElt3Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt3Created!!)).getJSONObject(0)
        JSONTestHelper.assertSameJSON("Retrieve mismatch", soupElt1Created!!, soupElt1Retrieved!!)
        Assert.assertEquals("Should be empty", 0, soupElt2Retrieved.length())
        JSONTestHelper.assertSameJSON("Retrieve mismatch", soupElt3Created!!, soupElt3Retrieved!!)

        // Check DB
        var c: Cursor? = null
        try {
            val db = dbOpenHelper.writableDatabase
            val soupTableName = getSoupTableName(TEST_SOUP)
            c = DBHelper.getInstance(db).query(db, soupTableName!!, null, "id ASC", null, null)
            Assert.assertTrue("Expected a soup element", c.moveToFirst())
            Assert.assertEquals("Expected two soup elements", 2, c.count)
            Assert.assertEquals("Wrong id", idOf(soupElt1Created!!), c.getLong(c.getColumnIndex("id")))
            c.moveToNext()
            Assert.assertEquals("Wrong id", idOf(soupElt3Created!!), c.getLong(c.getColumnIndex("id")))
        } finally {
            safeClose(c)
        }
    }

    /**
     * Testing delete: create soup elements, delete by query and check database directly that deleted entries are in fact gone
     *
     * @throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testDeleteByQuery() {
        tryDeleteByQuery(null, null)
    }

    /**
     * Testing delete: create soup elements, delete by query and check database directly that deleted entries are in fact gone
     * Populate idsDeleted and idsNotDeleted if not null
     *
     * @param idsDeleted
     * @param idsNotDeleted
     */
    @Throws(JSONException::class)
    protected open fun tryDeleteByQuery(idsDeleted: MutableList<Long>?, idsNotDeleted: MutableList<Long>?) {
        val soupElt1 = JSONObject("{'key':'ka1', 'value':'va1'}")
        val soupElt2 = JSONObject("{'key':'ka2', 'value':'va2'}")
        val soupElt3 = JSONObject("{'key':'ka3', 'value':'va3'}")
        val soupElt1Created = store.create(TEST_SOUP, soupElt1)!!
        val soupElt2Created = store.create(TEST_SOUP, soupElt2)!!
        val soupElt3Created = store.create(TEST_SOUP, soupElt3)!!
        val id1 = soupElt1Created.getLong(SmartStore.SOUP_ENTRY_ID)
        val id2 = soupElt2Created.getLong(SmartStore.SOUP_ENTRY_ID)
        val id3 = soupElt3Created.getLong(SmartStore.SOUP_ENTRY_ID)
        val querySpec = QuerySpec.buildRangeQuerySpec(TEST_SOUP, "key", "ka1", "ka2", "key", Order.ascending, 2)
        store.deleteByQuery(TEST_SOUP, querySpec)
        val soupElt1Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt1Created!!))
        val soupElt2Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt2Created!!))
        val soupElt3Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt3Created!!)).getJSONObject(0)
        Assert.assertEquals("Should be empty", 0, soupElt1Retrieved.length())
        Assert.assertEquals("Should be empty", 0, soupElt2Retrieved.length())
        JSONTestHelper.assertSameJSON("Retrieve mismatch", soupElt3Created!!, soupElt3Retrieved!!)

        // Check DB
        var c: Cursor? = null
        try {
            val db = dbOpenHelper.writableDatabase
            val soupTableName = getSoupTableName(TEST_SOUP)
            c = DBHelper.getInstance(db).query(db, soupTableName!!, null, "id ASC", null, null)
            Assert.assertTrue("Expected a soup element", c.moveToFirst())
            Assert.assertEquals("Expected one soup elements", 1, c.count)
            Assert.assertEquals("Wrong id", idOf(soupElt3Created!!), c.getLong(c.getColumnIndex("id")))
        } finally {
            safeClose(c)
        }

        // Populate idsDeleted
        if (idsDeleted != null) {
            idsDeleted.add(id1)
            idsDeleted.add(id2)
        }

        // Populate idsNotDeleted
        if (idsNotDeleted != null) {
            idsNotDeleted.add(id3)
        }
    }

    /**
     * Testing clear soup: create soup elements, clear soup and check database directly that there are in fact gone
     *
     * @throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testClearSoup() {
        val soupElt1 = JSONObject("{'key':'ka1', 'value':'va1'}")
        val soupElt2 = JSONObject("{'key':'ka2', 'value':'va2'}")
        val soupElt3 = JSONObject("{'key':'ka3', 'value':'va3'}")
        val soupElt1Created = store.create(TEST_SOUP, soupElt1)!!
        val soupElt2Created = store.create(TEST_SOUP, soupElt2)!!
        val soupElt3Created = store.create(TEST_SOUP, soupElt3)!!
        store.clearSoup(TEST_SOUP)
        val soupElt1Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt1Created!!))
        val soupElt2Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt2Created!!))
        val soupElt3Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt3Created!!))
        Assert.assertEquals("Should be empty", 0, soupElt1Retrieved.length())
        Assert.assertEquals("Should be empty", 0, soupElt2Retrieved.length())
        Assert.assertEquals("Should be empty", 0, soupElt3Retrieved.length())

        // Check DB
        var c: Cursor? = null
        try {
            val db = dbOpenHelper.writableDatabase
            val soupTableName = getSoupTableName(TEST_SOUP)
            c = DBHelper.getInstance(db).query(db, soupTableName!!, null, "id ASC", null, null)
            Assert.assertFalse("Expected no soup element", c.moveToFirst())
        } finally {
            safeClose(c)
        }
    }

    /**
     * Test query when looking for all elements when soup has string index
     *
     * @throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testAllQueryWithStringIndex() {
        tryAllQuery(Type.string)
    }

    /**
     * Test query when looking for all elements when soup has json1 index
     *
     * @throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testAllQueryWithJSON1Index() {
        tryAllQuery(Type.json1)
    }

    /**
     * Test query when looking for all elements
     *
     * @throws JSONException
     */
    @Throws(JSONException::class)
    fun tryAllQuery(type: Type) {

        // Before
        Assert.assertFalse("Soup other_test_soup should not exist", store.hasSoup(OTHER_TEST_SOUP))

        // Register
        store.registerSoup(OTHER_TEST_SOUP, arrayOf(IndexSpec("key", type)))
        Assert.assertTrue("Register soup call failed", store.hasSoup(OTHER_TEST_SOUP))
        val soupElt1 = JSONObject("{'key':'ka1', 'value':'va1', 'otherValue':'ova1'}")
        val soupElt2 = JSONObject("{'key':'ka2', 'value':'va2', 'otherValue':'ova2'}")
        val soupElt3 = JSONObject("{'key':'ka3', 'value':'va3', 'otherValue':'ova3'}")
        val soupElt1Created = store.create(OTHER_TEST_SOUP, soupElt1)!!
        val soupElt2Created = store.create(OTHER_TEST_SOUP, soupElt2)!!
        val soupElt3Created = store.create(OTHER_TEST_SOUP, soupElt3)!!

        // Query all - small page
        runQueryCheckResultsAndExplainPlan(OTHER_TEST_SOUP,
            QuerySpec.buildAllQuerySpec(OTHER_TEST_SOUP, "key", Order.ascending, 2),
            0, false, "SCAN", soupElt1Created, soupElt2Created)

        // Query all - next small page
        runQueryCheckResultsAndExplainPlan(OTHER_TEST_SOUP,
            QuerySpec.buildAllQuerySpec(OTHER_TEST_SOUP, "key", Order.ascending, 2),
            1, false, "SCAN", soupElt3Created)

        // Query all - large page
        runQueryCheckResultsAndExplainPlan(OTHER_TEST_SOUP,
            QuerySpec.buildAllQuerySpec(OTHER_TEST_SOUP, "key", Order.ascending, 10),
            0, false, "SCAN", soupElt1Created, soupElt2Created, soupElt3Created)

        // Query all with select paths
        runQueryCheckResultsAndExplainPlan(OTHER_TEST_SOUP,
            QuerySpec.buildAllQuerySpec(OTHER_TEST_SOUP, arrayOf("key"), "key", Order.ascending, 10),
            0, true, "SCAN", JSONArray("['ka1']"), JSONArray("['ka2']"), JSONArray("['ka3']"))
    }

    /**
     * Test query when looking for a specific element with a string index
     *
     * @throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testExactQueryWithStringIndex() {
        tryExactQuery(Type.string)
    }

    /**
     * Test query when looking for a specific element with a json1 index
     *
     * @throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testExactQueryWithJSON1Index() {
        tryExactQuery(Type.json1)
    }

    @Throws(JSONException::class)
    private fun tryExactQuery(type: Type) {

        // Before
        Assert.assertFalse("Soup other_test_soup should not exist", store.hasSoup(OTHER_TEST_SOUP))

        // Register
        store.registerSoup(OTHER_TEST_SOUP, arrayOf(IndexSpec("key", type)))
        Assert.assertTrue("Register soup call failed", store.hasSoup(OTHER_TEST_SOUP))
        val soupElt1 = JSONObject("{'key':'ka1', 'value':'va1', 'otherValue':'ova1'}")
        val soupElt2 = JSONObject("{'key':'ka2', 'value':'va2', 'otherValue':'ova2'}")
        val soupElt3 = JSONObject("{'key':'ka3', 'value':'va3', 'otherValue':'ova3'}")
        store.create(OTHER_TEST_SOUP, soupElt1)!!
        val soupElt2Created = store.create(OTHER_TEST_SOUP, soupElt2)!!
        store.create(OTHER_TEST_SOUP, soupElt3)!!

        // Exact match
        runQueryCheckResultsAndExplainPlan(OTHER_TEST_SOUP,
            QuerySpec.buildExactQuerySpec(OTHER_TEST_SOUP, "key", "ka2", null, null, 10),
            0, false, "SEARCH", soupElt2Created)
    }

    /**
     * Query test looking for a range of elements (with ascending or descending ordering) with a string index
     *
     * @throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testRangeQueryWithStringIndex() {
        tryRangeQuery(Type.string)
    }

    /**
     * Query test looking for a range of elements (with ascending or descending ordering) with a json1 index
     *
     * @throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testRangeQueryWithJSON1Index() {
        tryRangeQuery(Type.json1)
    }

    @Throws(JSONException::class)
    private fun tryRangeQuery(type: Type) {

        // Before
        Assert.assertFalse("Soup other_test_soup should not exist", store.hasSoup(OTHER_TEST_SOUP))

        // Register
        store.registerSoup(OTHER_TEST_SOUP, arrayOf(IndexSpec("key", type)))
        Assert.assertTrue("Register soup call failed", store.hasSoup(OTHER_TEST_SOUP))
        val soupElt1 = JSONObject("{'key':'ka1', 'value':'va1', 'otherValue':'ova1'}")
        val soupElt2 = JSONObject("{'key':'ka2', 'value':'va2', 'otherValue':'ova2'}")
        val soupElt3 = JSONObject("{'key':'ka3', 'value':'va3', 'otherValue':'ova3'}")

        store.create(OTHER_TEST_SOUP, soupElt1)!!
        val soupElt2Created = store.create(OTHER_TEST_SOUP, soupElt2)!!
        val soupElt3Created = store.create(OTHER_TEST_SOUP, soupElt3)!!

        // Range query
        runQueryCheckResultsAndExplainPlan(OTHER_TEST_SOUP,
            QuerySpec.buildRangeQuerySpec(OTHER_TEST_SOUP, "key", "ka2", "ka3", "key", Order.ascending, 10),
            0, false, "SEARCH", soupElt2Created, soupElt3Created)

        // Range query - descending order
        runQueryCheckResultsAndExplainPlan(OTHER_TEST_SOUP,
            QuerySpec.buildRangeQuerySpec(OTHER_TEST_SOUP, "key", "ka2", "ka3", "key", Order.descending, 10),
            0, false, "SEARCH", soupElt3Created, soupElt2Created)

        // Range query with select paths
        runQueryCheckResultsAndExplainPlan(OTHER_TEST_SOUP,
            QuerySpec.buildRangeQuerySpec(OTHER_TEST_SOUP, arrayOf("key"), "key", "ka2", "ka3", "key", Order.descending, 10),
            0, true, "SEARCH", JSONArray("['ka3']"), JSONArray("['ka2']"))
    }

    /**
     * Query test looking using like (with ascending or descending ordering) and a string index
     *
     * @throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testLikeQueryWithStringIndex() {
        tryLikeQuery(Type.string)
    }

    /**
     * Query test looking using like (with ascending or descending ordering) and a json1 index
     *
     * @throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testLikeQueryWithJSON1Index() {
        tryLikeQuery(Type.json1)
    }

    @Throws(JSONException::class)
    private fun tryLikeQuery(type: Type) {
        Assert.assertFalse("Soup other_test_soup should not exist", store.hasSoup(OTHER_TEST_SOUP))
        store.registerSoup(OTHER_TEST_SOUP, arrayOf(IndexSpec("key", type)))
        Assert.assertTrue("Register soup call failed", store.hasSoup(OTHER_TEST_SOUP))
        val soupElt1 = JSONObject("{'key':'abcd', 'value':'va1', 'otherValue':'ova1'}")
        val soupElt2 = JSONObject("{'key':'bbcd', 'value':'va2', 'otherValue':'ova2'}")
        val soupElt3 = JSONObject("{'key':'abcc', 'value':'va3', 'otherValue':'ova3'}")
        val soupElt4 = JSONObject("{'key':'defg', 'value':'va4', 'otherValue':'ova3'}")
        val soupElt1Created = store.create(OTHER_TEST_SOUP, soupElt1)!!
        val soupElt2Created = store.create(OTHER_TEST_SOUP, soupElt2)!!
        val soupElt3Created = store.create(OTHER_TEST_SOUP, soupElt3)!!
        store.create(OTHER_TEST_SOUP, soupElt4)!!

        // Like query (starts with)
        runQueryCheckResultsAndExplainPlan(OTHER_TEST_SOUP, QuerySpec.buildLikeQuerySpec(OTHER_TEST_SOUP, "key", "abc%", "key", Order.ascending, 10), 0, false, "SCAN", soupElt3Created, soupElt1Created)

        // Like query (ends with)
        runQueryCheckResultsAndExplainPlan(OTHER_TEST_SOUP, QuerySpec.buildLikeQuerySpec(OTHER_TEST_SOUP, "key", "%bcd", "key", Order.ascending, 10), 0, false, "SCAN", soupElt1Created, soupElt2Created)

        // Like query (starts with) - descending order
        runQueryCheckResultsAndExplainPlan(OTHER_TEST_SOUP, QuerySpec.buildLikeQuerySpec(OTHER_TEST_SOUP, "key", "abc%", "key", Order.descending, 10), 0, false, "SCAN", soupElt1Created, soupElt3Created)

        // Like query (ends with) - descending order
        runQueryCheckResultsAndExplainPlan(OTHER_TEST_SOUP, QuerySpec.buildLikeQuerySpec(OTHER_TEST_SOUP, "key", "%bcd", "key", Order.descending, 10), 0, false, "SCAN", soupElt2Created, soupElt1Created)

        // Like query (contains)
        runQueryCheckResultsAndExplainPlan(OTHER_TEST_SOUP, QuerySpec.buildLikeQuerySpec(OTHER_TEST_SOUP, "key", "%bc%", "key", Order.ascending, 10), 0, false, "SCAN", soupElt3Created, soupElt1Created, soupElt2Created)

        // Like query (contains) - descending order
        runQueryCheckResultsAndExplainPlan(OTHER_TEST_SOUP, QuerySpec.buildLikeQuerySpec(OTHER_TEST_SOUP, "key", "%bc%", "key", Order.descending, 10), 0, false, "SCAN", soupElt2Created, soupElt1Created, soupElt3Created)

        // Like query (contains) with select paths
        runQueryCheckResultsAndExplainPlan(OTHER_TEST_SOUP,
            QuerySpec.buildLikeQuerySpec(OTHER_TEST_SOUP, arrayOf("key"), "key", "%bc%", "key", Order.descending, 10), 0, true, "SCAN",
            JSONArray("['bbcd']"), JSONArray("['abcd']"), JSONArray("['abcc']"))
    }

    /**
     * Test query against soup with special characters when soup has string index
     *
     * @throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testQueryDataWithSpecialCharactersWithStringIndex() {
        tryQueryDataWithSpecialCharacters(Type.string)
    }

    /**
     * Test query against soup with special characters when soup has json1 index
     *
     * @throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testQueryDataWithSpecialCharactersWithJSON1Index() {
        tryQueryDataWithSpecialCharacters(Type.json1)
    }

    @Throws(JSONException::class)
    private fun tryQueryDataWithSpecialCharacters(type: Type) {
        // Before
        Assert.assertFalse("Soup other_test_soup should not exist", store.hasSoup(OTHER_TEST_SOUP))

        // Register
        store.registerSoup(OTHER_TEST_SOUP, arrayOf(IndexSpec("key", type), IndexSpec("value", type)))
        Assert.assertTrue("Register soup call failed", store.hasSoup(OTHER_TEST_SOUP))

        val value = StringBuffer()
        for (i in 1 until 1000) {
            value.append(Char(i))
        }
        val valueForAbcd = "abcd" + value
        val valueForDefg = "defg" + value

        // Populate soup
        val soupElt1 = JSONObject()
        soupElt1.put("key", "abcd")
        soupElt1.put("value", valueForAbcd)

        val soupElt2 = JSONObject("{'key':'defg'}")
        soupElt2.put("key", "defg")
        soupElt2.put("value", valueForDefg)

        store.create(OTHER_TEST_SOUP, soupElt1)!!
        store.create(OTHER_TEST_SOUP, soupElt2)!!

        // Smart query
        val sql = String.format("SELECT {%1\$s:value} FROM {%1\$s} ORDER BY {%1\$s:key}", OTHER_TEST_SOUP)
        runQueryCheckResultsAndExplainPlan(OTHER_TEST_SOUP, QuerySpec.buildSmartQuerySpec(sql, 10), 0, false, null,
            JSONArray(Collections.singletonList(valueForAbcd)), JSONArray(Collections.singletonList(valueForDefg)))
    }

    @Throws(JSONException::class)
    protected open fun runQueryCheckResultsAndExplainPlan(soupName: String, querySpec: QuerySpec, page: Int, covering: Boolean, expectedDbOperation: String?, vararg expectedResults: JSONObject) {

        // Run query
        val result = store.query(querySpec, page)!!

        // Check results
        Assert.assertEquals("Wrong number of results", expectedResults.size, result.length())
        for (i in expectedResults.indices) {
            JSONTestHelper.assertSameJSON("Wrong result for query", expectedResults[i], result.getJSONObject(i))
        }

        // Check explain plan and make sure index was used
        checkExplainQueryPlan(soupName!!, 0, covering, expectedDbOperation!!)
    }

    @Throws(JSONException::class)
    private fun runQueryCheckResultsAndExplainPlan(soupName: String, querySpec: QuerySpec, page: Int, covering: Boolean, expectedDbOperation: String?, vararg expectedRows: JSONArray) {

        // Run query
        val result = store.query(querySpec, page)!!

        // Check results
        Assert.assertEquals("Wrong number of rows", expectedRows.size, result.length())
        for (i in expectedRows.indices) {
            JSONTestHelper.assertSameJSON("Wrong result for query", expectedRows[i], result.getJSONArray(i))
        }

        // Check explain plan and make sure index was used
        if (expectedDbOperation != null) {
            checkExplainQueryPlan(soupName, 0, covering, expectedDbOperation)
        }
    }

    /**
     * Test smart sql returning entire soup elements (i.e. select {soup:_soup} from {soup})
     *
     * @throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testSelectUnderscoreSoup() {

        // Create soup elements
        val soupElt1 = JSONObject("{'key':'ka1', 'value':'va1'}")
        val soupElt2 = JSONObject("{'key':'ka2', 'value':'va2'}")
        val soupElt3 = JSONObject("{'key':'ka3', 'value':'va3'}")
        val soupElt4 = JSONObject("{'key':'ka4', 'value':'va4'}")
        val soupElt1Created = store.create(TEST_SOUP, soupElt1)!!
        val soupElt2Created = store.create(TEST_SOUP, soupElt2)!!
        val soupElt3Created = store.create(TEST_SOUP, soupElt3)!!
        val soupElt4Created = store.create(TEST_SOUP, soupElt4)!!
        val smartSql = "SELECT {" + TEST_SOUP + ":_soup} FROM {" + TEST_SOUP + "} ORDER BY {" + TEST_SOUP + ":key}"
        val querySpec = QuerySpec.buildSmartQuerySpec(smartSql, 25)
        val result = store.query(querySpec, 0)!!
        Assert.assertNotNull("Result should not be null", result)
        Assert.assertEquals("Four results expected", 4, result.length())
        JSONTestHelper.assertSameJSON("Wrong result for query - row 0", JSONArray(arrayOf(soupElt1Created)), result.get(0))
        JSONTestHelper.assertSameJSON("Wrong result for query - row 1", JSONArray(arrayOf(soupElt2Created)), result.get(1))
        JSONTestHelper.assertSameJSON("Wrong result for query - row 2", JSONArray(arrayOf(soupElt3Created)), result.get(2))
        JSONTestHelper.assertSameJSON("Wrong result for query - row 3", JSONArray(arrayOf(soupElt4Created)), result.get(3))
    }

    /**
     * Test smart sql returning entire soup elements from multiple soups
     *
     * @throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testSelectUnderscoreSoupFromMultipleSoups() {

        val soupElt1 = JSONObject("{'key':'ka', 'value':'va'}")
        val soupElt1Created = store.create(TEST_SOUP, soupElt1)!!

        store.registerSoup(OTHER_TEST_SOUP, arrayOf(IndexSpec("key", Type.string)))
        val soupElt2 = JSONObject("{'key':'abcd', 'value':'va1', 'otherValue':'ova1'}")
        val soupElt2Created = store.create(OTHER_TEST_SOUP, soupElt2)!!

        val smartSql = "SELECT {" + TEST_SOUP + ":_soup}, {" + OTHER_TEST_SOUP + ":_soup} FROM {" + TEST_SOUP + "}, {" + OTHER_TEST_SOUP + "}"
        val querySpec = QuerySpec.buildSmartQuerySpec(smartSql, 25)
        val result = store.query(querySpec, 0)!!
        Assert.assertNotNull("Result should not be null", result)
        Assert.assertEquals("One row expected", 1, result.length())
        val firstRow = result.getJSONArray(0)
        JSONTestHelper.assertSameJSON("Wrong result for query - row 0 - first soup elt", soupElt1Created, firstRow.getJSONObject(0))
        JSONTestHelper.assertSameJSON("Wrong result for query - row 0 - second soup elt", soupElt2Created, firstRow.getJSONObject(1))
    }

    /**
     * Test smart sql select with null value in string indexed field
     *
     * @throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testSelectWithNullInStringIndexedField() {
        trySelectWithNullInIndexedField(Type.string)
    }

    /**
     * Test smart sql select with null value in json1 indexed field
     *
     * @throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testSelectWithNullInJSON1IndexedField() {
        trySelectWithNullInIndexedField(Type.json1)
    }

    @Throws(JSONException::class)
    private fun trySelectWithNullInIndexedField(type: Type) {

        // Before
        Assert.assertFalse("Soup third_test_soup should not exist", store.hasSoup(THIRD_TEST_SOUP))

        // Register
        registerSoup(store, THIRD_TEST_SOUP, arrayOf(IndexSpec("key", type), IndexSpec("value", type)))
        Assert.assertTrue("Register soup call failed", store.hasSoup(THIRD_TEST_SOUP))

        // Upsert
        val soupElt1 = JSONObject("{'key':'ka', 'value':null}")
        val soupElt1Upserted = store.upsert(THIRD_TEST_SOUP, soupElt1)

        // Smart sql
        val smartSql = "SELECT {" + THIRD_TEST_SOUP + ":value}, {" + THIRD_TEST_SOUP + ":key}  FROM {" + THIRD_TEST_SOUP + "} WHERE {" + THIRD_TEST_SOUP + ":key} = 'ka'"
        val querySpec = QuerySpec.buildSmartQuerySpec(smartSql, 25)
        val result = store.query(querySpec, 0)!!

        // Check
        Assert.assertNotNull("Result should not be null", result)
        Assert.assertEquals("One result expected", 1, result.length())
        JSONTestHelper.assertSameJSON("Wrong result for query", JSONArray("[[null, 'ka']]"), result)
    }

    /**
     * Test upsert soup element with null value in string indexed field
     *
     * @throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testUpsertWithNullInStringIndexedField() {
        tryUpsertWithNullInIndexedField(Type.string)
    }

    /**
     * Test upsert soup element with null value in json1 indexed field
     *
     * @throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testUpsertWithNullInJSON1IndexedField() {
        tryUpsertWithNullInIndexedField(Type.json1)
    }

    @Throws(JSONException::class)
    private fun tryUpsertWithNullInIndexedField(type: Type) {

        // Before
        Assert.assertFalse("Soup third_test_soup should not exist", store.hasSoup(THIRD_TEST_SOUP))

        // Register
        registerSoup(store, THIRD_TEST_SOUP, arrayOf(IndexSpec("key", type), IndexSpec("value", type)))
        Assert.assertTrue("Register soup call failed", store.hasSoup(THIRD_TEST_SOUP))

        // Upsert
        val soupElt1 = JSONObject("{'key':'ka', 'value':null}")
        val soupElt1Upserted = store.upsert(THIRD_TEST_SOUP, soupElt1)

        // Check
        val soupElt1Retrieved = store.retrieve(THIRD_TEST_SOUP, idOf(soupElt1Upserted!!)).getJSONObject(0)
        JSONTestHelper.assertSameJSON("Retrieve mismatch", soupElt1Upserted!!, soupElt1Retrieved!!)
    }

    /**
     * Test to verify an aggregate query on floating point values indexed as floating.
     *
     * @throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testAggregateQueryOnFloatingIndexedField() {
        tryAggregateQueryOnIndexedField(Type.floating)
    }

    /**
     * Test to verify an aggregate query on floating point values indexed as JSON1.
     *
     * @throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testAggregateQueryOnJSON1IndexedField() {
        tryAggregateQueryOnIndexedField(Type.json1)
    }

    @Throws(JSONException::class)
    private fun tryAggregateQueryOnIndexedField(type: Type) {
        val soupElt1 = JSONObject("{'amount':10.2}")
        val soupElt2 = JSONObject("{'amount':9.9}")
        val indexSpecs = arrayOf(IndexSpec("amount", type))
        registerSoup(store, FOURTH_TEST_SOUP, indexSpecs)
        Assert.assertTrue("Soup " + FOURTH_TEST_SOUP + " should have been created", store.hasSoup(FOURTH_TEST_SOUP))
        store.upsert(FOURTH_TEST_SOUP, soupElt1)
        store.upsert(FOURTH_TEST_SOUP, soupElt2)
        val smartSql = "SELECT SUM({" + FOURTH_TEST_SOUP + ":amount}) FROM {" + FOURTH_TEST_SOUP + "}"
        val querySpec = QuerySpec.buildSmartQuerySpec(smartSql, 1)
        val result = store.query(querySpec, 0)!!
        Assert.assertNotNull("Result should not be null", result)
        Assert.assertEquals("One result expected", 1, result.length())
        Assert.assertEquals("Incorrect result received", 20.1, result.getJSONArray(0).getDouble(0), 0.0)
        store.dropSoup(FOURTH_TEST_SOUP)
        Assert.assertFalse("Soup " + FOURTH_TEST_SOUP + " should have been deleted", store.hasSoup(FOURTH_TEST_SOUP))
    }

    /**
     * Test to verify an count query for a query with group by when the soup uses string indexes.
     *
     * @throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testCountQueryWithGroupByUsingStringIndexes() {
        tryCountQueryWithGroupBy(Type.string)
    }

    /**
     * Test to verify an count query for a query with group by when the soup uses json1 indexes.
     *
     * @throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testCountQueryWithGroupByUsingJSON1Indexes() {
        tryCountQueryWithGroupBy(Type.json1)
    }

    @Throws(JSONException::class)
    private fun tryCountQueryWithGroupBy(type: Type) {

        // Before
        Assert.assertFalse("Soup third_test_soup should not exist", store.hasSoup(THIRD_TEST_SOUP))

        // Register
        registerSoup(store, THIRD_TEST_SOUP, arrayOf(IndexSpec("key", type), IndexSpec("value", type)))
        Assert.assertTrue("Register soup call failed", store.hasSoup(THIRD_TEST_SOUP))
        val soupElt1 = JSONObject("{'key':'a', 'value':'va1'}")
        val soupElt2 = JSONObject("{'key':'b', 'value':'va1'}")
        val soupElt3 = JSONObject("{'key':'c', 'value':'va2'}")
        val soupElt4 = JSONObject("{'key':'d', 'value':'va3'}")
        val soupElt5 = JSONObject("{'key':'e', 'value':'va3'}")
        store.create(THIRD_TEST_SOUP, soupElt1)!!
        store.create(THIRD_TEST_SOUP, soupElt2)!!
        store.create(THIRD_TEST_SOUP, soupElt3)!!
        store.create(THIRD_TEST_SOUP, soupElt4)!!
        store.create(THIRD_TEST_SOUP, soupElt5)!!
        val smartSql = "SELECT {" + THIRD_TEST_SOUP + ":value}, count(*) FROM {" + THIRD_TEST_SOUP + "} GROUP BY {" + THIRD_TEST_SOUP + ":value} ORDER BY {" + THIRD_TEST_SOUP + ":value}"
        val querySpec = QuerySpec.buildSmartQuerySpec(smartSql, 25)
        val result = store.query(querySpec, 0)!!
        Assert.assertNotNull("Result should not be null", result)
        Assert.assertEquals("Three results expected", 3, result.length())
        JSONTestHelper.assertSameJSON("Wrong result for query", JSONArray("[['va1', 2], ['va2', 1], ['va3', 2]]"), result)
        val count = store.countQuery(querySpec)
        Assert.assertEquals("Incorrect count query", "SELECT count(*) FROM (" + smartSql + ")", querySpec.countSmartSql)
        Assert.assertEquals("Incorrect count", 3, count)
    }

    /**
     * Test to verify proper indexing of integer and longs
     */
    @Test
    @Throws(JSONException::class)
    fun testIntegerIndexedField() {
        registerSoup(store, FOURTH_TEST_SOUP, arrayOf(IndexSpec("amount", Type.integer)))
        tryNumber(Type.integer, Integer.MIN_VALUE, Integer.MIN_VALUE)
        tryNumber(Type.integer, Integer.MAX_VALUE, Integer.MAX_VALUE)
        tryNumber(Type.integer, Long.MIN_VALUE, Long.MIN_VALUE)
        tryNumber(Type.integer, Long.MIN_VALUE, Long.MIN_VALUE)
        tryNumber(Type.integer, Double.MIN_VALUE, Double.MIN_VALUE.toLong())
        tryNumber(Type.integer, Double.MAX_VALUE, Double.MAX_VALUE.toLong())
    }

    /**
     * Test to verify proper indexing of doubles
     */
    @Test
    @Throws(JSONException::class)
    fun testFloatingIndexedField() {
        registerSoup(store, FOURTH_TEST_SOUP, arrayOf(IndexSpec("amount", Type.floating)))
        tryNumber(Type.floating, Integer.MIN_VALUE, Integer.MIN_VALUE.toDouble())
        tryNumber(Type.floating, Integer.MAX_VALUE, Integer.MAX_VALUE.toDouble())
        tryNumber(Type.floating, Long.MIN_VALUE, Long.MIN_VALUE.toDouble())
        tryNumber(Type.floating, Long.MIN_VALUE, Long.MIN_VALUE.toDouble())
        tryNumber(Type.floating, Double.MIN_VALUE, Double.MIN_VALUE)
        tryNumber(Type.floating, Double.MAX_VALUE, Double.MAX_VALUE)
    }

    /**
     * Helper method for testIntegerIndexedField and testFloatingIndexedField
     * Insert soup element with number and check db
     *
     * @param fieldType
     * @param valueIn
     * @param valueOut
     * @throws JSONException
     */
    @Throws(JSONException::class)
    private fun tryNumber(fieldType: Type, valueIn: Number, valueOut: Number) {
        val elt = JSONObject()
        elt.put("amount", valueIn)
        val id = store.upsert(FOURTH_TEST_SOUP, elt)!!.getLong(SmartStore.SOUP_ENTRY_ID)
        var c: Cursor? = null
        try {
            val db = dbOpenHelper.writableDatabase
            val soupTableName = getSoupTableName(FOURTH_TEST_SOUP)
            val amountColumnName = store.getSoupIndexSpecs(FOURTH_TEST_SOUP)[0].columnName
            c = DBHelper.getInstance(db).query(db, soupTableName!!, arrayOf(amountColumnName!!), null, null, "id = $id")
            Assert.assertTrue("Expected a soup element", c.moveToFirst())
            Assert.assertEquals("Expected one soup element", 1, c.count)
            if (fieldType == Type.integer)
                Assert.assertEquals("Not the value expected", valueOut.toLong(), c.getLong(0))
            else if (fieldType == Type.floating)
                Assert.assertEquals("Not the value expected", valueOut.toDouble(), c.getDouble(0), 0.0)
        } finally {
            safeClose(c)
        }
    }

    /**
     * Test using smart sql to retrieve integer indexed fields
     */
    @Test
    @Throws(JSONException::class)
    fun testIntegerIndexedFieldWithSmartSql() {
        registerSoup(store, FOURTH_TEST_SOUP, arrayOf(IndexSpec("amount", Type.integer)))
        tryNumberWithSmartSql(Type.integer, Integer.MIN_VALUE, Integer.MIN_VALUE)
        tryNumberWithSmartSql(Type.integer, Integer.MAX_VALUE, Integer.MAX_VALUE)
        tryNumberWithSmartSql(Type.integer, Long.MIN_VALUE, Long.MIN_VALUE)
        tryNumberWithSmartSql(Type.integer, Long.MIN_VALUE, Long.MIN_VALUE)
        tryNumberWithSmartSql(Type.integer, Double.MIN_VALUE, Double.MIN_VALUE.toLong())
        tryNumberWithSmartSql(Type.integer, Double.MAX_VALUE, Double.MAX_VALUE.toLong())
    }

    /**
     * Test using smart sql to retrieve indexed fields holding doubles
     * NB smart sql will return a long when querying a double field that contains a long
     */
    @Test
    @Throws(JSONException::class)
    fun testFloatingIndexedFieldWithSmartSql() {
        registerSoup(store, FOURTH_TEST_SOUP, arrayOf(IndexSpec("amount", Type.floating)))
        tryNumberWithSmartSql(Type.floating, Integer.MIN_VALUE, Integer.MIN_VALUE)
        tryNumberWithSmartSql(Type.floating, Integer.MAX_VALUE, Integer.MAX_VALUE)
        tryNumberWithSmartSql(Type.floating, Long.MIN_VALUE, Long.MIN_VALUE)
        tryNumberWithSmartSql(Type.floating, Long.MIN_VALUE, Long.MIN_VALUE)
        tryNumberWithSmartSql(Type.floating, Double.MIN_VALUE, Double.MIN_VALUE)
        tryNumberWithSmartSql(Type.floating, Double.MAX_VALUE, Double.MAX_VALUE)
    }

    /**
     * Test using smart sql to retrieve number fields indexed with json1
     */
    @Test
    @Throws(JSONException::class)
    fun testNumberFieldWithJSON1IndexWithSmartSql() {
        store.registerSoup(FOURTH_TEST_SOUP, arrayOf(IndexSpec("amount", Type.json1)))
        tryNumberWithSmartSql(Type.integer, Integer.MIN_VALUE, Integer.MIN_VALUE)
        tryNumberWithSmartSql(Type.integer, Integer.MAX_VALUE, Integer.MAX_VALUE)
        tryNumberWithSmartSql(Type.integer, Long.MIN_VALUE, Long.MIN_VALUE)
        tryNumberWithSmartSql(Type.integer, Long.MIN_VALUE, Long.MIN_VALUE)
        tryNumberWithSmartSql(Type.floating, Math.PI, Math.PI)
    }

    /**
     * Helper method for testIntegerIndexedFieldWithSmartSql and testFloatingIndexedFieldWithSmartSql
     * Insert soup element with number and retrieve it back using smartsql
     *
     * @param fieldType
     * @param valueIn
     * @param valueOut
     * @throws JSONException
     */
    @Throws(JSONException::class)
    private fun tryNumberWithSmartSql(fieldType: Type, valueIn: Number, valueOut: Number) {
        val smartSql = "SELECT {" + FOURTH_TEST_SOUP + ":amount} FROM {" + FOURTH_TEST_SOUP + "} WHERE {" + FOURTH_TEST_SOUP + ":_soupEntryId} = "
        val elt = JSONObject()
        elt.put("amount", valueIn)
        val id = store.upsert(FOURTH_TEST_SOUP, elt)!!.getLong(SmartStore.SOUP_ENTRY_ID)
        val actualValueOut = store.query(QuerySpec.buildSmartQuerySpec(smartSql + id, 1)!!, 0)!!.getJSONArray(0).get(0) as Number
        if (fieldType == Type.integer)
            Assert.assertEquals("Not the value expected", valueOut.toLong(), actualValueOut.toLong())
        else if (fieldType == Type.floating)
            Assert.assertEquals("Not the value expected", valueOut.toDouble(), actualValueOut.toDouble(), 0.0)
    }

    /**
     * Test for getDatabaseSize
     *
     * @throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testGetDatabaseSize() {
        val initialSize = store.getDatabaseSize()
        for (i in 0 until 100) {
            val soupElt = JSONObject("{'key':'abcd" + i + "', 'value':'va" + i + "', 'otherValue':'ova" + i + "'}")
            store.create(TEST_SOUP, soupElt)!!
        }

        Assert.assertTrue("Database should be larger now", store.getDatabaseSize() > initialSize)
    }

    /**
     * Test registerSoup with json1 indexes
     * Register soup with multiple json1 indexes and a string index, check the underlying table and indexes in the database
     */
    @Test
    @Throws(JSONException::class)
    fun testRegisterSoupWithJSON1() {
        Assert.assertFalse("Soup other_test_soup should not exist", store.hasSoup(OTHER_TEST_SOUP))
        store.registerSoup(OTHER_TEST_SOUP, arrayOf(IndexSpec("lastName", Type.json1), IndexSpec("address.city", Type.json1), IndexSpec("address.zipcode", Type.string)))
        Assert.assertTrue("Register soup call failed", store.hasSoup(OTHER_TEST_SOUP))

        // Check columns of soup table
        val soupTableName = getSoupTableName(OTHER_TEST_SOUP)
        checkColumns(soupTableName!!, listOf("id", "soup", "created", "lastModified", soupTableName!! + "_2"))

        // Check soup indexes
        val indexSpecs = store.getSoupIndexSpecs(OTHER_TEST_SOUP)
        Assert.assertEquals("Wrong path", "lastName", indexSpecs[0].path)
        Assert.assertEquals("Wrong type", Type.json1, indexSpecs[0].type)
        Assert.assertEquals("Wrong column name", "json_extract(soup, '$.lastName')", indexSpecs[0].columnName)
        Assert.assertEquals("Wrong path", "address.city", indexSpecs[1].path)
        Assert.assertEquals("Wrong type", Type.json1, indexSpecs[1].type)
        Assert.assertEquals("Wrong column name", "json_extract(soup, '$.address.city')", indexSpecs[1].columnName)
        Assert.assertEquals("Wrong path", "address.zipcode", indexSpecs[2].path)
        Assert.assertEquals("Wrong type", Type.string, indexSpecs[2].type)
        Assert.assertEquals("Wrong column name", soupTableName!! + "_2", indexSpecs[2].columnName)

        // Check db indexes
        checkDatabaseIndexes(soupTableName!!, listOf(
            "CREATE INDEX " + soupTableName + "_0_idx on " + soupTableName + " ( json_extract(soup, '$.lastName') )",
            "CREATE INDEX " + soupTableName + "_1_idx on " + soupTableName + " ( json_extract(soup, '$.address.city') )",
            "CREATE INDEX " + soupTableName + "_2_idx on " + soupTableName + " ( " + soupTableName + "_2 )",
            "CREATE INDEX " + soupTableName + "_created_idx on " + soupTableName + " ( created )",
            "CREATE INDEX " + soupTableName + "_lastModified_idx on " + soupTableName + " ( lastModified )"
        ))
    }

    /**
     * Testing Delete: create multiple soup elements and alter the soup, after that delete a entry, then check them all
     *
     * @throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testDeleteAgainstChangedSoup() {

        //create a new soup with multiple entries
        val soupElt1 = JSONObject("{'key':'ka1', 'value':'va1'}")
        val soupElt2 = JSONObject("{'key':'ka2', 'value':'va2'}")
        val soupElt3 = JSONObject("{'key':'ka3', 'value':'va3'}")
        val soupElt4 = JSONObject("{'key':'ka4', 'value':'va4'}")
        val soupElt1Created = store.create(TEST_SOUP, soupElt1)!!
        val soupElt2Created = store.create(TEST_SOUP, soupElt2)!!
        val soupElt3Created = store.create(TEST_SOUP, soupElt3)!!
        val soupElt4Created = store.create(TEST_SOUP, soupElt4)!!

        //CASE 1: index spec from key to value
        tryAllQueryOnChangedSoupWithUpdate(TEST_SOUP, soupElt2Created, "value",
            arrayOf(IndexSpec("value", Type.string)),
            soupElt1Created, soupElt3Created, soupElt4Created)

        //CASE 2: index spec from string to json1
        tryAllQueryOnChangedSoupWithUpdate(TEST_SOUP, soupElt4Created, "key",
            arrayOf(IndexSpec("key", Type.json1)),
            soupElt1Created, soupElt3Created)

        //CASE 3: add a index spec field
        tryAllQueryOnChangedSoupWithUpdate(TEST_SOUP, soupElt4Created, "key",
            arrayOf(IndexSpec("key", Type.json1), IndexSpec("value", Type.string)),
            soupElt1Created, soupElt3Created)
    }

    @Throws(JSONException::class)
    protected open fun tryAllQueryOnChangedSoupWithUpdate(soupName: String, deletedEntry: JSONObject, orderPath: String,
                                                      newIndexSpecs: Array<IndexSpec>, vararg expectedResults: JSONObject) {

        //alert the soup
        store.alterSoup(soupName, newIndexSpecs, true)

        //delete an entry
        store.delete(soupName, idOf(deletedEntry!!))

        // Query all - small page
        runQueryCheckResultsAndExplainPlan(soupName,
            QuerySpec.buildAllQuerySpec(soupName, orderPath, Order.ascending, 5),
            0, false, "SCAN", *expectedResults)
    }

    /**
     * Testing Upsert: create multiple soup elements and alter the soup, after that upsert a entry, then check them all
     *
     * @throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testUpsertAgainstChangedSoup() {

        //create a new soup with multiple entries
        val soupElt1 = JSONObject("{'key':'ka1', 'value':'va1'}")
        val soupElt2 = JSONObject("{'key':'ka2', 'value':'va2'}")
        val soupElt3 = JSONObject("{'key':'ka3', 'value':'va3'}")
        val soupElt1Created = store.create(TEST_SOUP, soupElt1)!!
        val soupElt2Created = store.create(TEST_SOUP, soupElt2)!!
        val soupElt3Created = store.create(TEST_SOUP, soupElt3)!!
        val soupElt1ForUpsert = JSONObject("{'key':'ka1u', 'value':'va1u'}")
        val soupElt2ForUpsert = JSONObject("{'key':'ka2u', 'value':'va2u'}")
        val soupElt3ForUpsert = JSONObject("{'key':'ka3u', 'value':'va3u'}")

        //CASE 1: index spec from key to value
        store.alterSoup(TEST_SOUP, arrayOf(IndexSpec("value", Type.string)), true)

        //upsert an entry
        val soupElt1Upserted = store.upsert(TEST_SOUP, soupElt1ForUpsert)

        // Query all - small page
        runQueryCheckResultsAndExplainPlan(TEST_SOUP,
            QuerySpec.buildAllQuerySpec(TEST_SOUP, "value", Order.ascending, 10),
            0, false, "SCAN", soupElt1Created!!, soupElt1Upserted!!, soupElt2Created!!, soupElt3Created!!)

        //CASE 2: index spec from string to json1
        store.alterSoup(TEST_SOUP, arrayOf(IndexSpec("key", Type.json1)), true)

        //upsert an entry
        val soupElt2Upserted = store.upsert(TEST_SOUP, soupElt2ForUpsert)

        // Query all - small page
        runQueryCheckResultsAndExplainPlan(TEST_SOUP,
            QuerySpec.buildAllQuerySpec(TEST_SOUP, "key", Order.ascending, 10),
            0, false, "SCAN", soupElt1Created!!, soupElt1Upserted!!, soupElt2Created!!, soupElt2Upserted!!, soupElt3Created!!)

        //CASE 3: add a index spec field
        store.alterSoup(TEST_SOUP, arrayOf(IndexSpec("key", Type.json1), IndexSpec("value", Type.string)), true)

        //upsert an entry
        val soupElt3Upserted = store.upsert(TEST_SOUP, soupElt3ForUpsert)

        // Query all - small page
        runQueryCheckResultsAndExplainPlan(TEST_SOUP,
            QuerySpec.buildAllQuerySpec(TEST_SOUP, "key", Order.ascending, 10),
            0, false, "SCAN", soupElt1Created!!, soupElt1Upserted!!, soupElt2Created!!, soupElt2Upserted!!, soupElt3Created!!, soupElt3Upserted!!)
    }

    /**
     * Testing Delete: create multiple soup elements and alter the soup, after that delete a entry, then check them all
     *
     * @throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testExactQueryAgainstChangedSoup() {

        //create a new soup with multiple entries
        val soupElt1 = JSONObject("{'key':'ka1', 'value':'va1'}")
        val soupElt2 = JSONObject("{'key':'ka1-', 'value':'va1*'}")
        val soupElt3 = JSONObject("{'key':'ka1 ', 'value':'va1%'}")
        val soupElt1Created = store.create(TEST_SOUP, soupElt1)!!
        val soupElt2Created = store.create(TEST_SOUP, soupElt2)!!
        val soupElt3Created = store.create(TEST_SOUP, soupElt3)!!

        //CASE 1: index spec from key to value
        tryExactQueryOnChangedSoup(TEST_SOUP, "value", "va1",
            arrayOf(IndexSpec("value", Type.string)),
            soupElt1Created)

        //CASE 2: index spec from string to json1
        tryExactQueryOnChangedSoup(TEST_SOUP, "key", "ka1",
            arrayOf(IndexSpec("key", Type.json1)),
            soupElt1Created)

        //CASE 3: add a index spec field
        tryExactQueryOnChangedSoup(TEST_SOUP, "key", "ka1 ",
            arrayOf(IndexSpec("key", Type.json1), IndexSpec("value", Type.string)),
            soupElt3Created)
    }

    @Throws(JSONException::class)
    protected open fun tryExactQueryOnChangedSoup(soupName: String, orderPath: String, value: String,
                                              newIndexSpecs: Array<IndexSpec>, expectedResult: JSONObject) {

        // Alter the soup
        store.alterSoup(soupName, newIndexSpecs, true)

        // Exact Query
        runQueryCheckResultsAndExplainPlan(soupName,
            QuerySpec.buildExactQuerySpec(soupName, orderPath, value, null, null, 5),
            0, false, "SEARCH", expectedResult)
    }

    /**
     * Test updateSoupNamesToAttrs
     */
    @Test
    fun testUpdateTableNameAndAddColumns() {

        // Setup db and test values
        val db = dbOpenHelper.writableDatabase
        val TEST_TABLE = "test_table"
        val NEW_TEST_TABLE = "new_test_table"
        val NEW_COLUMN = "new_column"
        db.execSQL("CREATE TABLE " + TEST_TABLE + " (id INTEGER PRIMARY KEY)")

        // Ensure old table doesn't already exist
        var cursor = db.query("sqlite_master", arrayOf("sql"), "name = ?", arrayOf(NEW_TEST_TABLE), null, null, null)
        Assert.assertEquals("New table should not already be in db.", 0, cursor.count)
        cursor.close()

        // Test table renamed and column added
        SmartStore.updateTableNameAndAddColumns(db, TEST_TABLE, NEW_TEST_TABLE, arrayOf(NEW_COLUMN))

        // Ensure new table has replaced old table
        cursor = db.query("sqlite_master", arrayOf("sql"), "name = ?", arrayOf(NEW_TEST_TABLE), null, null, null)
        cursor.moveToFirst()
        val schema = cursor.getString(0)
        cursor.close()
        Assert.assertTrue("New table not found", schema.contains(NEW_TEST_TABLE))
        Assert.assertTrue("New column not found", schema.contains(NEW_COLUMN))

        // Clean up
        db.execSQL("DROP TABLE " + NEW_TEST_TABLE)
    }

    @Test
    fun testHasSoup() {
        Assert.assertTrue(store.hasSoup(TEST_SOUP))
        Assert.assertFalse(store.hasSoup(OTHER_TEST_SOUP))
        Assert.assertFalse(store.hasSoup(THIRD_TEST_SOUP))

        // Register other soup
        registerSoup(store, OTHER_TEST_SOUP, arrayOf(IndexSpec("lastName", Type.string), IndexSpec("address.city", Type.string)))
        Assert.assertTrue(store.hasSoup(TEST_SOUP))
        Assert.assertTrue(store.hasSoup(OTHER_TEST_SOUP))
        Assert.assertFalse(store.hasSoup(THIRD_TEST_SOUP))

        // Register third soup
        registerSoup(store, THIRD_TEST_SOUP, arrayOf(IndexSpec("key", Type.string), IndexSpec("value", Type.string)))
        Assert.assertTrue(store.hasSoup(TEST_SOUP))
        Assert.assertTrue(store.hasSoup(OTHER_TEST_SOUP))
        Assert.assertTrue(store.hasSoup(THIRD_TEST_SOUP))

        // Dropping third soup
        store.dropSoup(THIRD_TEST_SOUP)
        Assert.assertTrue(store.hasSoup(TEST_SOUP))
        Assert.assertTrue(store.hasSoup(OTHER_TEST_SOUP))
        Assert.assertFalse(store.hasSoup(THIRD_TEST_SOUP))

        // Dropping all soups
        store.dropAllSoups()
        Assert.assertFalse(store.hasSoup(TEST_SOUP))
        Assert.assertFalse(store.hasSoup(OTHER_TEST_SOUP))
        Assert.assertFalse(store.hasSoup(THIRD_TEST_SOUP))
    }
}
