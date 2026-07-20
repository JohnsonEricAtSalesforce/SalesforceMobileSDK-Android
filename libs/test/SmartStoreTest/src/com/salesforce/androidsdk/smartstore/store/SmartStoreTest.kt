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
import org.json.JSONObject
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections

/**
 * Main test suite for SmartStore
 */
@RunWith(AndroidJUnit4::class)
@MediumTest
open class SmartStoreTest : SmartStoreTestCase() {

    companion object {
        protected const val TEST_SOUP = "test_soup"
        protected const val OTHER_TEST_SOUP = "other_test_soup"
        private const val THIRD_TEST_SOUP = "third_test_soup"
        private const val FOURTH_TEST_SOUP = "fourth_test_soup"
    }

    @Before
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
    override fun tearDown() {
        super.tearDown()
    }

    override fun getEncryptionKey(): String = "test123"

    @Test
    fun testCompileOptions() {
        val compileOptions = store.getCompileOptions()
        Assert.assertTrue("ENABLE_FTS4 flag not found in compile options", compileOptions.contains("ENABLE_FTS4"))
        Assert.assertTrue("ENABLE_FTS3_PARENTHESIS flag not found in compile options", compileOptions.contains("ENABLE_FTS3_PARENTHESIS"))
        Assert.assertTrue("ENABLE_FTS5 flag not found in compile options", compileOptions.contains("ENABLE_FTS5"))
    }

    @Test
    fun testRuntimeSettings() {
        val settings = store.getRuntimeSettings()
        Assert.assertTrue("Wrong kdf_iter", settings.contains("PRAGMA kdf_iter = 4000;"))
        Assert.assertTrue("Wrong cipher_page_size", settings.contains("PRAGMA cipher_page_size = 4096;"))
        Assert.assertTrue("Wrong cipher_user_hmac", settings.contains("PRAGMA cipher_use_hmac = 1;"))
        Assert.assertTrue("Wrong cipher_plaintext_header_size", settings.contains("PRAGMA cipher_plaintext_header_size = 0;"))
        Assert.assertTrue("Wrong cipher_hmac_algorithm", settings.contains("PRAGMA cipher_hmac_algorithm = HMAC_SHA512;"))
        Assert.assertTrue("Wrong cipher_kdf_algorithm", settings.contains("PRAGMA cipher_kdf_algorithm = PBKDF2_HMAC_SHA512;"))
    }

    @Test
    fun testSQLCipherVersion() {
        Assert.assertEquals("Wrong sqlcipher version", "4.17.0 community", store.getSQLCipherVersion())
    }

    @Test
    fun testCipherProviderVersion() {
        Assert.assertEquals("Wrong sqlcipher provider version", "1.18.2", store.getCipherProviderVersion())
    }

    @Test
    fun testCipherFIPSStatus() {
        Assert.assertFalse("Wrong sqlcipher FIPS status", store.getCipherFIPSStatus())
    }

    protected open fun assertSameSoupAsDB(soup: JSONObject, c: Cursor, soupName: String, id: Long?) {
        JSONTestHelper.assertSameJSON("Wrong value in soup column", soup, JSONObject(c.getString(c.getColumnIndex("soup"))))
    }

    @Test
    fun testProjectTopLevel() {
        val json = JSONObject("{'a':'va', 'b':2, 'c':[0,1,2], 'd': {'d1':'vd1', 'd2':'vd2', 'd3':[1,2], 'd4':{'e':5}}}")
        Assert.assertNull("Should have been null", SmartStore.project(null, "path"))
        JSONTestHelper.assertSameJSON("Should have returned whole object", json, SmartStore.project(json, null))
        JSONTestHelper.assertSameJSON("Should have returned whole object", json, SmartStore.project(json, ""))
        Assert.assertEquals("Wrong value for key a", "va", SmartStore.project(json, "a"))
        Assert.assertEquals("Wrong value for key b", 2, SmartStore.project(json, "b"))
        JSONTestHelper.assertSameJSON("Wrong value for key c", JSONArray("[0,1,2]"), SmartStore.project(json, "c"))
        JSONTestHelper.assertSameJSON("Wrong value for key d", JSONObject("{'d1':'vd1','d2':'vd2','d3':[1,2],'d4':{'e':5}}"), SmartStore.project(json, "d") as JSONObject)
    }

    @Test
    fun testProjectNested() {
        val json = JSONObject("{'a':'va', 'b':2, 'c':[0,1,2], 'd': {'d1':'vd1', 'd2':'vd2', 'd3':[1,2], 'd4':{'e':5}}}")
        Assert.assertEquals("Wrong value for key d.d1", "vd1", SmartStore.project(json, "d.d1"))
        Assert.assertEquals("Wrong value for key d.d2", "vd2", SmartStore.project(json, "d.d2"))
        JSONTestHelper.assertSameJSON("Wrong value for key d.d3", JSONArray("[1,2]"), SmartStore.project(json, "d.d3"))
        JSONTestHelper.assertSameJSON("Wrong value for key d.d4", JSONObject("{'e':5}"), SmartStore.project(json, "d.d4"))
        Assert.assertEquals("Wrong value for key d.d4.e", 5, SmartStore.project(json, "d.d4.e"))
    }

    @Test
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

    @Test
    fun testProjectMissingVsSetToNull() {
        val json = JSONObject("{\"a\":null, \"b\":{\"bb\":null}, \"c\":{\"cc\":{\"ccc\":null}}}")
        Assert.assertEquals(JSONObject.NULL, SmartStore.projectReturningNULLObject(json, "a"))
        Assert.assertEquals(JSONObject.NULL, SmartStore.projectReturningNULLObject(json, "b.bb"))
        Assert.assertEquals(JSONObject.NULL, SmartStore.projectReturningNULLObject(json, "c.cc.ccc"))
        Assert.assertEquals(null, SmartStore.projectReturningNULLObject(json, "a1"))
        Assert.assertEquals(null, SmartStore.projectReturningNULLObject(json, "b.bb1"))
        Assert.assertEquals(null, SmartStore.projectReturningNULLObject(json, "c.cc.ccc1"))
    }

    @Test
    fun testMetaDataTableCreated() {
        Assert.assertTrue("Table soup_index_map not found", hasTable("soup_index_map"))
    }

    @Test
    fun testRegisterDropSoup() {
        Assert.assertFalse("Soup third_test_soup should not exist", store.hasSoup(THIRD_TEST_SOUP))
        registerSoup(store, THIRD_TEST_SOUP, arrayOf(IndexSpec("key", Type.string), IndexSpec("value", Type.string)))
        val soupTableName = getSoupTableName(THIRD_TEST_SOUP)
        Assert.assertEquals("getSoupTableName should have returned TABLE_2", "TABLE_2", soupTableName)
        Assert.assertTrue("Table for soup third_test_soup does exist", hasTable(soupTableName))
        Assert.assertTrue("Register soup call failed", store.hasSoup(THIRD_TEST_SOUP))

        val indexSpecs = store.getSoupIndexSpecs(THIRD_TEST_SOUP)
        Assert.assertEquals("Wrong path", "key", indexSpecs[0].path)
        Assert.assertEquals("Wrong type", Type.string, indexSpecs[0].type)
        Assert.assertEquals("Wrong column name", soupTableName + "_0", indexSpecs[0].columnName)
        Assert.assertEquals("Wrong path", "value", indexSpecs[1].path)
        Assert.assertEquals("Wrong type", Type.string, indexSpecs[1].type)
        Assert.assertEquals("Wrong column name", soupTableName + "_1", indexSpecs[1].columnName)

        checkDatabaseIndexes(soupTableName, listOf(
            "CREATE INDEX ${soupTableName}_0_idx on $soupTableName ( ${soupTableName}_0 )",
            "CREATE INDEX ${soupTableName}_1_idx on $soupTableName ( ${soupTableName}_1 )",
            "CREATE INDEX ${soupTableName}_created_idx on $soupTableName ( created )",
            "CREATE INDEX ${soupTableName}_lastModified_idx on $soupTableName ( lastModified )"
        ))

        store.dropSoup(THIRD_TEST_SOUP)
        Assert.assertFalse("Soup third_test_soup should no longer exist", store.hasSoup(THIRD_TEST_SOUP))
        Assert.assertFalse("Table for soup third_test_soup does exist", hasTable(soupTableName))
    }

    @Test
    fun testGetAllSoupNames() {
        Assert.assertEquals("One soup name expected", 1, store.getAllSoupNames().size)
        Assert.assertTrue(TEST_SOUP + " should have been returned by getAllSoupNames", store.getAllSoupNames().contains(TEST_SOUP))
        registerSoup(store, THIRD_TEST_SOUP, arrayOf(IndexSpec("key", Type.string), IndexSpec("value", Type.string)))
        Assert.assertEquals("Two soup names expected", 2, store.getAllSoupNames().size)
        Assert.assertTrue(TEST_SOUP + " should have been returned by getAllSoupNames", store.getAllSoupNames().contains(TEST_SOUP))
        Assert.assertTrue(THIRD_TEST_SOUP + " should have been returned by getAllSoupNames", store.getAllSoupNames().contains(THIRD_TEST_SOUP))
        store.dropSoup(THIRD_TEST_SOUP)
        Assert.assertEquals("One soup name expected", 1, store.getAllSoupNames().size)
        Assert.assertTrue(TEST_SOUP + " should have been returned by getAllSoupNames", store.getAllSoupNames().contains(TEST_SOUP))
    }

    @Test
    fun testDropAllSoups() {
        Assert.assertEquals("One soup name expected", 1, store.getAllSoupNames().size)
        registerSoup(store, THIRD_TEST_SOUP, arrayOf(IndexSpec("key", Type.string), IndexSpec("value", Type.string)))
        Assert.assertEquals("Two soup names expected", 2, store.getAllSoupNames().size)
        store.dropAllSoups()
        Assert.assertEquals("No soup name expected", 0, store.getAllSoupNames().size)
        Assert.assertFalse("Soup $THIRD_TEST_SOUP should no longer exist", store.hasSoup(THIRD_TEST_SOUP))
        Assert.assertFalse("Soup $TEST_SOUP should no longer exist", store.hasSoup(TEST_SOUP))
    }

    @Test
    fun testCreateOne() {
        val soupElt = JSONObject("{'key':'ka', 'value':'va'}")
        val soupEltCreated = store.create(TEST_SOUP, soupElt)!!
        var c: Cursor? = null
        try {
            val db: SQLiteDatabase = dbOpenHelper.writableDatabase
            val soupTableName = getSoupTableName(TEST_SOUP)
            c = DBHelper.getInstance(db).query(db, soupTableName, arrayOf(), null, null, null)
            Assert.assertTrue("Expected a soup element", c.moveToFirst())
            Assert.assertEquals("Expected one soup element only", 1, c.count)
            Assert.assertEquals("Wrong id", idOf(soupEltCreated), c.getLong(c.getColumnIndex("id")))
            Assert.assertEquals("Wrong created date", soupEltCreated.getLong(SmartStore.SOUP_LAST_MODIFIED_DATE), c.getLong(c.getColumnIndex("lastModified")))
            Assert.assertEquals("Wrong value in index column", "ka", c.getString(c.getColumnIndex(soupTableName + "_0")))
            assertSameSoupAsDB(soupEltCreated, c, soupTableName, idOf(soupEltCreated))
            Assert.assertEquals("Created date and last modified date should be equal", c.getLong(c.getColumnIndex("created")), c.getLong(c.getColumnIndex("lastModified")))
        } finally { safeClose(c) }
    }

    @Test
    fun testCreateMultiple() {
        Assert.assertFalse("Soup other_test_soup should not exist", store.hasSoup(OTHER_TEST_SOUP))
        registerSoup(store, OTHER_TEST_SOUP, arrayOf(IndexSpec("lastName", Type.string), IndexSpec("address.city", Type.string)))
        Assert.assertTrue("Register soup call failed", store.hasSoup(OTHER_TEST_SOUP))
        val soupElt1Created = store.create(OTHER_TEST_SOUP, JSONObject("{'lastName':'Doe', 'address':{'city':'San Francisco','street':'1 market'}}"))!!
        val soupElt2Created = store.create(OTHER_TEST_SOUP, JSONObject("{'lastName':'Jackson', 'address':{'city':'Los Angeles','street':'100 mission'}}"))!!
        val soupElt3Created = store.create(OTHER_TEST_SOUP, JSONObject("{'lastName':'Watson', 'address':{'city':'London','street':'50 market'}}"))!!
        var c: Cursor? = null
        try {
            val soupTableName = getSoupTableName(OTHER_TEST_SOUP)
            Assert.assertEquals("Table for other_test_soup was expected to be called TABLE_2", "TABLE_2", soupTableName)
            Assert.assertTrue("Table for other_test_soup should now exist", hasTable("TABLE_2"))
            val db: SQLiteDatabase = dbOpenHelper.writableDatabase
            c = DBHelper.getInstance(db).query(db, soupTableName, arrayOf(), "id ASC", null, null)
            Assert.assertTrue("Expected a soup element", c.moveToFirst())
            Assert.assertEquals("Expected three soup elements", 3, c.count)
            Assert.assertEquals("Wrong id", idOf(soupElt1Created), c.getLong(c.getColumnIndex("id")))
            Assert.assertEquals("Wrong value in index column", "Doe", c.getString(c.getColumnIndex(soupTableName + "_0")))
            Assert.assertEquals("Wrong value in index column", "San Francisco", c.getString(c.getColumnIndex(soupTableName + "_1")))
            assertSameSoupAsDB(soupElt1Created, c, soupTableName, idOf(soupElt1Created))
        } finally { safeClose(c) }
    }

    @Test
    fun testUpdate() {
        val soupElt1Created = store.create(TEST_SOUP, JSONObject("{'key':'ka1', 'value':'va1'}"))!!
        val soupElt2Created = store.create(TEST_SOUP, JSONObject("{'key':'ka2', 'value':'va2'}"))!!
        val soupElt3Created = store.create(TEST_SOUP, JSONObject("{'key':'ka3', 'value':'va3'}"))!!
        SystemClock.sleep(10)
        val soupElt2Updated = store.update(TEST_SOUP, JSONObject("{'key':'ka2u', 'value':'va2u'}"), idOf(soupElt2Created))!!
        val soupElt1Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt1Created)).getJSONObject(0)
        val soupElt2Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt2Created)).getJSONObject(0)
        val soupElt3Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt3Created)).getJSONObject(0)
        JSONTestHelper.assertSameJSON("Retrieve mismatch", soupElt1Created, soupElt1Retrieved)
        JSONTestHelper.assertSameJSON("Retrieve mismatch", soupElt2Updated, soupElt2Retrieved)
        JSONTestHelper.assertSameJSON("Retrieve mismatch", soupElt3Created, soupElt3Retrieved)
    }

    @Test
    fun testUpsert() {
        val soupElt1Upserted = store.upsert(TEST_SOUP, JSONObject("{'key':'ka1', 'value':'va1'}"))!!
        val soupElt2Upserted = store.upsert(TEST_SOUP, JSONObject("{'key':'ka2', 'value':'va2'}"))!!
        val soupElt3Upserted = store.upsert(TEST_SOUP, JSONObject("{'key':'ka3', 'value':'va3'}"))!!
        SystemClock.sleep(10)
        val soupElt2Updated = store.upsert(TEST_SOUP, JSONObject("{'key':'ka2u', 'value':'va2u', '_soupEntryId': ${idOf(soupElt2Upserted)}}"))!!
        val soupElt1Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt1Upserted)).getJSONObject(0)
        val soupElt2Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt2Upserted)).getJSONObject(0)
        val soupElt3Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt3Upserted)).getJSONObject(0)
        JSONTestHelper.assertSameJSON("Retrieve mismatch", soupElt1Upserted, soupElt1Retrieved)
        JSONTestHelper.assertSameJSON("Retrieve mismatch", soupElt2Updated, soupElt2Retrieved)
        JSONTestHelper.assertSameJSON("Retrieve mismatch", soupElt3Upserted, soupElt3Retrieved)
    }

    @Test
    fun testUpsertWithExternalId() {
        val soupElt1Upserted = store.upsert(TEST_SOUP, JSONObject("{'key':'ka1', 'value':'va1'}"), "key")!!
        val soupElt2Upserted = store.upsert(TEST_SOUP, JSONObject("{'key':'ka2', 'value':'va2'}"), "key")!!
        val soupElt3Upserted = store.upsert(TEST_SOUP, JSONObject("{'key':'ka3', 'value':'va3'}"), "key")!!
        SystemClock.sleep(10)
        val soupElt2Updated = store.upsert(TEST_SOUP, JSONObject("{'key':'ka2', 'value':'va2u'}"), "key")!!
        val soupElt1Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt1Upserted)).getJSONObject(0)
        val soupElt2Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt2Upserted)).getJSONObject(0)
        val soupElt3Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt3Upserted)).getJSONObject(0)
        JSONTestHelper.assertSameJSON("Retrieve mismatch", soupElt1Upserted, soupElt1Retrieved)
        JSONTestHelper.assertSameJSON("Retrieve mismatch", soupElt2Updated, soupElt2Retrieved)
        JSONTestHelper.assertSameJSON("Retrieve mismatch", soupElt3Upserted, soupElt3Retrieved)
    }

    @Test
    fun testUpsertWithNonIndexedExternalId() {
        val soupElt = JSONObject("{'key':'ka1', 'value':'va1'}")
        try {
            store.upsert(TEST_SOUP, soupElt, "value")
            Assert.fail("Exception was expected: value is not an indexed field")
        } catch (e: RuntimeException) {
            Assert.assertTrue("Wrong exception", e.message!!.contains("does not have an index"))
        }
    }

    @Test
    fun testUpsertByUserDefinedExternalIdWithoutValue() {
        val soupElt = JSONObject("{'value':'va1'}")
        try {
            store.upsert(TEST_SOUP, soupElt, "key")
            Assert.fail("Exception was expected: value cannot be empty for upsert by user-defined external id")
        } catch (e: RuntimeException) {
            Assert.assertTrue("Wrong exception",
                e.message!!.contains("For upsert with external ID path") && e.message!!.contains("value cannot be empty for any entries"))
        }
    }

    @Test
    fun testUpsertWithNonUniqueExternalId() {
        val soupElt1Upserted = store.upsert(TEST_SOUP, JSONObject("{'key':'ka', 'value':'va1'}"))!!
        val soupElt2Upserted = store.upsert(TEST_SOUP, JSONObject("{'key':'ka', 'value':'va2'}"))!!
        val soupElt1Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt1Upserted)).getJSONObject(0)
        val soupElt2Retrieved = store.retrieve(TEST_SOUP, idOf(soupElt2Upserted)).getJSONObject(0)
        JSONTestHelper.assertSameJSON("Retrieve mismatch", soupElt1Upserted, soupElt1Retrieved)
        JSONTestHelper.assertSameJSON("Retrieve mismatch", soupElt2Upserted, soupElt2Retrieved)
        try {
            store.upsert(TEST_SOUP, JSONObject("{'key':'ka', 'value':'va3'}"), "key")
            Assert.fail("Exception was expected: key is not unique in the soup")
        } catch (e: RuntimeException) {
            Assert.assertTrue("Wrong exception", e.message!!.contains("are more than one soup elements"))
        }
    }

    @Test
    fun testRetrieve() {
        val soupElt1Created = store.create(TEST_SOUP, JSONObject("{'key':'ka1', 'value':'va1'}"))!!
        val soupElt2Created = store.create(TEST_SOUP, JSONObject("{'key':'ka2', 'value':'va2'}"))!!
        val soupElt3Created = store.create(TEST_SOUP, JSONObject("{'key':'ka3', 'value':'va3'}"))!!
        JSONTestHelper.assertSameJSON("Retrieve mismatch", soupElt1Created, store.retrieve(TEST_SOUP, idOf(soupElt1Created)).getJSONObject(0))
        JSONTestHelper.assertSameJSON("Retrieve mismatch", soupElt2Created, store.retrieve(TEST_SOUP, idOf(soupElt2Created)).getJSONObject(0))
        JSONTestHelper.assertSameJSON("Retrieve mismatch", soupElt3Created, store.retrieve(TEST_SOUP, idOf(soupElt3Created)).getJSONObject(0))
    }

    @Test
    fun testDelete() {
        val soupElt1Created = store.create(TEST_SOUP, JSONObject("{'key':'ka1', 'value':'va1'}"))!!
        val soupElt2Created = store.create(TEST_SOUP, JSONObject("{'key':'ka2', 'value':'va2'}"))!!
        val soupElt3Created = store.create(TEST_SOUP, JSONObject("{'key':'ka3', 'value':'va3'}"))!!
        store.delete(TEST_SOUP, idOf(soupElt2Created))
        JSONTestHelper.assertSameJSON("Retrieve mismatch", soupElt1Created, store.retrieve(TEST_SOUP, idOf(soupElt1Created)).getJSONObject(0))
        Assert.assertEquals("Should be empty", 0, store.retrieve(TEST_SOUP, idOf(soupElt2Created)).length())
        JSONTestHelper.assertSameJSON("Retrieve mismatch", soupElt3Created, store.retrieve(TEST_SOUP, idOf(soupElt3Created)).getJSONObject(0))
    }

    @Test
    fun testDeleteByQuery() {
        tryDeleteByQuery(null, null)
    }

    protected open fun tryDeleteByQuery(idsDeleted: MutableList<Long>?, idsNotDeleted: MutableList<Long>?) {
        val soupElt1Created = store.create(TEST_SOUP, JSONObject("{'key':'ka1', 'value':'va1'}"))!!
        val soupElt2Created = store.create(TEST_SOUP, JSONObject("{'key':'ka2', 'value':'va2'}"))!!
        val soupElt3Created = store.create(TEST_SOUP, JSONObject("{'key':'ka3', 'value':'va3'}"))!!
        val id1 = soupElt1Created.getLong(SmartStore.SOUP_ENTRY_ID)
        val id2 = soupElt2Created.getLong(SmartStore.SOUP_ENTRY_ID)
        val id3 = soupElt3Created.getLong(SmartStore.SOUP_ENTRY_ID)
        val querySpec = QuerySpec.buildRangeQuerySpec(TEST_SOUP, "key", "ka1", "ka2", "key", Order.ascending, 2)
        store.deleteByQuery(TEST_SOUP, querySpec)
        Assert.assertEquals("Should be empty", 0, store.retrieve(TEST_SOUP, idOf(soupElt1Created)).length())
        Assert.assertEquals("Should be empty", 0, store.retrieve(TEST_SOUP, idOf(soupElt2Created)).length())
        JSONTestHelper.assertSameJSON("Retrieve mismatch", soupElt3Created, store.retrieve(TEST_SOUP, idOf(soupElt3Created)).getJSONObject(0))
        idsDeleted?.add(id1)
        idsDeleted?.add(id2)
        idsNotDeleted?.add(id3)
    }

    @Test
    fun testClearSoup() {
        val soupElt1Created = store.create(TEST_SOUP, JSONObject("{'key':'ka1', 'value':'va1'}"))!!
        val soupElt2Created = store.create(TEST_SOUP, JSONObject("{'key':'ka2', 'value':'va2'}"))!!
        val soupElt3Created = store.create(TEST_SOUP, JSONObject("{'key':'ka3', 'value':'va3'}"))!!
        store.clearSoup(TEST_SOUP)
        Assert.assertEquals("Should be empty", 0, store.retrieve(TEST_SOUP, idOf(soupElt1Created)).length())
        Assert.assertEquals("Should be empty", 0, store.retrieve(TEST_SOUP, idOf(soupElt2Created)).length())
        Assert.assertEquals("Should be empty", 0, store.retrieve(TEST_SOUP, idOf(soupElt3Created)).length())
    }

    @Test
    fun testAllQueryWithStringIndex() { tryAllQuery(Type.string) }

    @Test
    fun testAllQueryWithJSON1Index() { tryAllQuery(Type.json1) }

    fun tryAllQuery(type: Type) {
        Assert.assertFalse("Soup other_test_soup should not exist", store.hasSoup(OTHER_TEST_SOUP))
        store.registerSoup(OTHER_TEST_SOUP, arrayOf(IndexSpec("key", type)))
        Assert.assertTrue("Register soup call failed", store.hasSoup(OTHER_TEST_SOUP))
        val soupElt1Created = store.create(OTHER_TEST_SOUP, JSONObject("{'key':'ka1', 'value':'va1', 'otherValue':'ova1'}"))!!
        val soupElt2Created = store.create(OTHER_TEST_SOUP, JSONObject("{'key':'ka2', 'value':'va2', 'otherValue':'ova2'}"))!!
        val soupElt3Created = store.create(OTHER_TEST_SOUP, JSONObject("{'key':'ka3', 'value':'va3', 'otherValue':'ova3'}"))!!

        runQueryCheckResultsAndExplainPlan(OTHER_TEST_SOUP, QuerySpec.buildAllQuerySpec(OTHER_TEST_SOUP, "key", Order.ascending, 2), 0, false, "SCAN", soupElt1Created, soupElt2Created)
        runQueryCheckResultsAndExplainPlan(OTHER_TEST_SOUP, QuerySpec.buildAllQuerySpec(OTHER_TEST_SOUP, "key", Order.ascending, 2), 1, false, "SCAN", soupElt3Created)
        runQueryCheckResultsAndExplainPlan(OTHER_TEST_SOUP, QuerySpec.buildAllQuerySpec(OTHER_TEST_SOUP, "key", Order.ascending, 10), 0, false, "SCAN", soupElt1Created, soupElt2Created, soupElt3Created)
        runQueryCheckResultsAndExplainPlanArrays(OTHER_TEST_SOUP, QuerySpec.buildAllQuerySpec(OTHER_TEST_SOUP, arrayOf("key"), "key", Order.ascending, 10), 0, true, "SCAN", JSONArray("['ka1']"), JSONArray("['ka2']"), JSONArray("['ka3']"))
    }

    @Test
    fun testExactQueryWithStringIndex() { tryExactQuery(Type.string) }

    @Test
    fun testExactQueryWithJSON1Index() { tryExactQuery(Type.json1) }

    private fun tryExactQuery(type: Type) {
        Assert.assertFalse("Soup other_test_soup should not exist", store.hasSoup(OTHER_TEST_SOUP))
        store.registerSoup(OTHER_TEST_SOUP, arrayOf(IndexSpec("key", type)))
        Assert.assertTrue("Register soup call failed", store.hasSoup(OTHER_TEST_SOUP))
        store.create(OTHER_TEST_SOUP, JSONObject("{'key':'ka1', 'value':'va1', 'otherValue':'ova1'}"))
        val soupElt2Created = store.create(OTHER_TEST_SOUP, JSONObject("{'key':'ka2', 'value':'va2', 'otherValue':'ova2'}"))!!
        store.create(OTHER_TEST_SOUP, JSONObject("{'key':'ka3', 'value':'va3', 'otherValue':'ova3'}"))
        runQueryCheckResultsAndExplainPlan(OTHER_TEST_SOUP, QuerySpec.buildExactQuerySpec(OTHER_TEST_SOUP, "key", "ka2", null, null, 10), 0, false, "SEARCH", soupElt2Created)
    }

    @Test
    fun testRangeQueryWithStringIndex() { tryRangeQuery(Type.string) }

    @Test
    fun testRangeQueryWithJSON1Index() { tryRangeQuery(Type.json1) }

    private fun tryRangeQuery(type: Type) {
        Assert.assertFalse("Soup other_test_soup should not exist", store.hasSoup(OTHER_TEST_SOUP))
        store.registerSoup(OTHER_TEST_SOUP, arrayOf(IndexSpec("key", type)))
        Assert.assertTrue("Register soup call failed", store.hasSoup(OTHER_TEST_SOUP))
        store.create(OTHER_TEST_SOUP, JSONObject("{'key':'ka1', 'value':'va1', 'otherValue':'ova1'}"))
        val soupElt2Created = store.create(OTHER_TEST_SOUP, JSONObject("{'key':'ka2', 'value':'va2', 'otherValue':'ova2'}"))!!
        val soupElt3Created = store.create(OTHER_TEST_SOUP, JSONObject("{'key':'ka3', 'value':'va3', 'otherValue':'ova3'}"))!!
        runQueryCheckResultsAndExplainPlan(OTHER_TEST_SOUP, QuerySpec.buildRangeQuerySpec(OTHER_TEST_SOUP, "key", "ka2", "ka3", "key", Order.ascending, 10), 0, false, "SEARCH", soupElt2Created, soupElt3Created)
        runQueryCheckResultsAndExplainPlan(OTHER_TEST_SOUP, QuerySpec.buildRangeQuerySpec(OTHER_TEST_SOUP, "key", "ka2", "ka3", "key", Order.descending, 10), 0, false, "SEARCH", soupElt3Created, soupElt2Created)
        runQueryCheckResultsAndExplainPlanArrays(OTHER_TEST_SOUP, QuerySpec.buildRangeQuerySpec(OTHER_TEST_SOUP, arrayOf("key"), "key", "ka2", "ka3", "key", Order.descending, 10), 0, true, "SEARCH", JSONArray("['ka3']"), JSONArray("['ka2']"))
    }

    @Test
    fun testLikeQueryWithStringIndex() { tryLikeQuery(Type.string) }

    @Test
    fun testLikeQueryWithJSON1Index() { tryLikeQuery(Type.json1) }

    private fun tryLikeQuery(type: Type) {
        Assert.assertFalse("Soup other_test_soup should not exist", store.hasSoup(OTHER_TEST_SOUP))
        store.registerSoup(OTHER_TEST_SOUP, arrayOf(IndexSpec("key", type)))
        Assert.assertTrue("Register soup call failed", store.hasSoup(OTHER_TEST_SOUP))
        val soupElt1Created = store.create(OTHER_TEST_SOUP, JSONObject("{'key':'abcd', 'value':'va1', 'otherValue':'ova1'}"))!!
        val soupElt2Created = store.create(OTHER_TEST_SOUP, JSONObject("{'key':'bbcd', 'value':'va2', 'otherValue':'ova2'}"))!!
        val soupElt3Created = store.create(OTHER_TEST_SOUP, JSONObject("{'key':'abcc', 'value':'va3', 'otherValue':'ova3'}"))!!
        store.create(OTHER_TEST_SOUP, JSONObject("{'key':'defg', 'value':'va4', 'otherValue':'ova3'}"))

        runQueryCheckResultsAndExplainPlan(OTHER_TEST_SOUP, QuerySpec.buildLikeQuerySpec(OTHER_TEST_SOUP, "key", "abc%", "key", Order.ascending, 10), 0, false, "SCAN", soupElt3Created, soupElt1Created)
        runQueryCheckResultsAndExplainPlan(OTHER_TEST_SOUP, QuerySpec.buildLikeQuerySpec(OTHER_TEST_SOUP, "key", "%bcd", "key", Order.ascending, 10), 0, false, "SCAN", soupElt1Created, soupElt2Created)
        runQueryCheckResultsAndExplainPlan(OTHER_TEST_SOUP, QuerySpec.buildLikeQuerySpec(OTHER_TEST_SOUP, "key", "%bc%", "key", Order.ascending, 10), 0, false, "SCAN", soupElt3Created, soupElt1Created, soupElt2Created)
        runQueryCheckResultsAndExplainPlanArrays(OTHER_TEST_SOUP, QuerySpec.buildLikeQuerySpec(OTHER_TEST_SOUP, arrayOf("key"), "key", "%bc%", "key", Order.descending, 10), 0, true, "SCAN", JSONArray("['bbcd']"), JSONArray("['abcd']"), JSONArray("['abcc']"))
    }

    @Test
    fun testQueryDataWithSpecialCharactersWithStringIndex() { tryQueryDataWithSpecialCharacters(Type.string) }

    @Test
    fun testQueryDataWithSpecialCharactersWithJSON1Index() { tryQueryDataWithSpecialCharacters(Type.json1) }

    private fun tryQueryDataWithSpecialCharacters(type: Type) {
        Assert.assertFalse("Soup other_test_soup should not exist", store.hasSoup(OTHER_TEST_SOUP))
        store.registerSoup(OTHER_TEST_SOUP, arrayOf(IndexSpec("key", type), IndexSpec("value", type)))
        Assert.assertTrue("Register soup call failed", store.hasSoup(OTHER_TEST_SOUP))
        val value = StringBuilder()
        for (i in 1 until 1000) { value.append(i.toChar()) }
        val valueForAbcd = "abcd$value"
        val valueForDefg = "defg$value"
        val soupElt1 = JSONObject(); soupElt1.put("key", "abcd"); soupElt1.put("value", valueForAbcd)
        val soupElt2 = JSONObject(); soupElt2.put("key", "defg"); soupElt2.put("value", valueForDefg)
        store.create(OTHER_TEST_SOUP, soupElt1)
        store.create(OTHER_TEST_SOUP, soupElt2)
        val sql = String.format("SELECT {%1\$s:value} FROM {%1\$s} ORDER BY {%1\$s:key}", OTHER_TEST_SOUP)
        runQueryCheckResultsAndExplainPlanArrays(OTHER_TEST_SOUP, QuerySpec.buildSmartQuerySpec(sql, 10), 0, false, null, JSONArray(Collections.singletonList(valueForAbcd)), JSONArray(Collections.singletonList(valueForDefg)))
    }

    protected fun runQueryCheckResultsAndExplainPlan(soupName: String, querySpec: QuerySpec, page: Int, covering: Boolean, expectedDbOperation: String, vararg expectedResults: JSONObject) {
        val result = store.query(querySpec, page)
        Assert.assertEquals("Wrong number of results", expectedResults.size, result.length())
        for (i in expectedResults.indices) {
            JSONTestHelper.assertSameJSON("Wrong result for query", expectedResults[i], result.getJSONObject(i))
        }
        checkExplainQueryPlan(soupName, 0, covering, expectedDbOperation)
    }

    private fun runQueryCheckResultsAndExplainPlanArrays(soupName: String, querySpec: QuerySpec, page: Int, covering: Boolean, expectedDbOperation: String?, vararg expectedRows: JSONArray) {
        val result = store.query(querySpec, page)
        Assert.assertEquals("Wrong number of rows", expectedRows.size, result.length())
        for (i in expectedRows.indices) {
            JSONTestHelper.assertSameJSON("Wrong result for query", expectedRows[i], result.getJSONArray(i))
        }
        if (expectedDbOperation != null) {
            checkExplainQueryPlan(soupName, 0, covering, expectedDbOperation)
        }
    }

    @Test
    fun testSelectUnderscoreSoup() {
        val soupElt1Created = store.create(TEST_SOUP, JSONObject("{'key':'ka1', 'value':'va1'}"))!!
        val soupElt2Created = store.create(TEST_SOUP, JSONObject("{'key':'ka2', 'value':'va2'}"))!!
        val soupElt3Created = store.create(TEST_SOUP, JSONObject("{'key':'ka3', 'value':'va3'}"))!!
        val soupElt4Created = store.create(TEST_SOUP, JSONObject("{'key':'ka4', 'value':'va4'}"))!!
        val smartSql = "SELECT {$TEST_SOUP:_soup} FROM {$TEST_SOUP} ORDER BY {$TEST_SOUP:key}"
        val querySpec = QuerySpec.buildSmartQuerySpec(smartSql, 25)
        val result = store.query(querySpec, 0)
        Assert.assertNotNull("Result should not be null", result)
        Assert.assertEquals("Four results expected", 4, result.length())
        JSONTestHelper.assertSameJSON("Wrong result for query - row 0", JSONArray(arrayOf(soupElt1Created)), result.get(0))
        JSONTestHelper.assertSameJSON("Wrong result for query - row 1", JSONArray(arrayOf(soupElt2Created)), result.get(1))
        JSONTestHelper.assertSameJSON("Wrong result for query - row 2", JSONArray(arrayOf(soupElt3Created)), result.get(2))
        JSONTestHelper.assertSameJSON("Wrong result for query - row 3", JSONArray(arrayOf(soupElt4Created)), result.get(3))
    }

    @Test
    fun testSelectUnderscoreSoupFromMultipleSoups() {
        val soupElt1Created = store.create(TEST_SOUP, JSONObject("{'key':'ka', 'value':'va'}"))!!
        store.registerSoup(OTHER_TEST_SOUP, arrayOf(IndexSpec("key", Type.string)))
        val soupElt2Created = store.create(OTHER_TEST_SOUP, JSONObject("{'key':'abcd', 'value':'va1', 'otherValue':'ova1'}"))!!
        val smartSql = "SELECT {$TEST_SOUP:_soup}, {$OTHER_TEST_SOUP:_soup} FROM {$TEST_SOUP}, {$OTHER_TEST_SOUP}"
        val querySpec = QuerySpec.buildSmartQuerySpec(smartSql, 25)
        val result = store.query(querySpec, 0)
        Assert.assertNotNull("Result should not be null", result)
        Assert.assertEquals("One row expected", 1, result.length())
        val firstRow = result.getJSONArray(0)
        JSONTestHelper.assertSameJSON("Wrong result for query - row 0 - first soup elt", soupElt1Created, firstRow.getJSONObject(0))
        JSONTestHelper.assertSameJSON("Wrong result for query - row 0 - second soup elt", soupElt2Created, firstRow.getJSONObject(1))
    }

    @Test
    fun testSelectWithNullInStringIndexedField() { trySelectWithNullInIndexedField(Type.string) }

    @Test
    fun testSelectWithNullInJSON1IndexedField() { trySelectWithNullInIndexedField(Type.json1) }

    private fun trySelectWithNullInIndexedField(type: Type) {
        Assert.assertFalse("Soup third_test_soup should not exist", store.hasSoup(THIRD_TEST_SOUP))
        registerSoup(store, THIRD_TEST_SOUP, arrayOf(IndexSpec("key", type), IndexSpec("value", type)))
        Assert.assertTrue("Register soup call failed", store.hasSoup(THIRD_TEST_SOUP))
        store.upsert(THIRD_TEST_SOUP, JSONObject("{'key':'ka', 'value':null}"))
        val smartSql = "SELECT {$THIRD_TEST_SOUP:value}, {$THIRD_TEST_SOUP:key}  FROM {$THIRD_TEST_SOUP} WHERE {$THIRD_TEST_SOUP:key} = 'ka'"
        val querySpec = QuerySpec.buildSmartQuerySpec(smartSql, 25)
        val result = store.query(querySpec, 0)
        Assert.assertNotNull("Result should not be null", result)
        Assert.assertEquals("One result expected", 1, result.length())
        JSONTestHelper.assertSameJSON("Wrong result for query", JSONArray("[[null, 'ka']]"), result)
    }

    @Test
    fun testUpsertWithNullInStringIndexedField() { tryUpsertWithNullInIndexedField(Type.string) }

    @Test
    fun testUpsertWithNullInJSON1IndexedField() { tryUpsertWithNullInIndexedField(Type.json1) }

    private fun tryUpsertWithNullInIndexedField(type: Type) {
        Assert.assertFalse("Soup third_test_soup should not exist", store.hasSoup(THIRD_TEST_SOUP))
        registerSoup(store, THIRD_TEST_SOUP, arrayOf(IndexSpec("key", type), IndexSpec("value", type)))
        Assert.assertTrue("Register soup call failed", store.hasSoup(THIRD_TEST_SOUP))
        val soupElt1Upserted = store.upsert(THIRD_TEST_SOUP, JSONObject("{'key':'ka', 'value':null}"))!!
        val soupElt1Retrieved = store.retrieve(THIRD_TEST_SOUP, idOf(soupElt1Upserted)).getJSONObject(0)
        JSONTestHelper.assertSameJSON("Retrieve mismatch", soupElt1Upserted, soupElt1Retrieved)
    }

    @Test
    fun testAggregateQueryOnFloatingIndexedField() { tryAggregateQueryOnIndexedField(Type.floating) }

    @Test
    fun testAggregateQueryOnJSON1IndexedField() { tryAggregateQueryOnIndexedField(Type.json1) }

    private fun tryAggregateQueryOnIndexedField(type: Type) {
        registerSoup(store, FOURTH_TEST_SOUP, arrayOf(IndexSpec("amount", type)))
        Assert.assertTrue("Soup $FOURTH_TEST_SOUP should have been created", store.hasSoup(FOURTH_TEST_SOUP))
        store.upsert(FOURTH_TEST_SOUP, JSONObject("{'amount':10.2}"))
        store.upsert(FOURTH_TEST_SOUP, JSONObject("{'amount':9.9}"))
        val smartSql = "SELECT SUM({$FOURTH_TEST_SOUP:amount}) FROM {$FOURTH_TEST_SOUP}"
        val querySpec = QuerySpec.buildSmartQuerySpec(smartSql, 1)
        val result = store.query(querySpec, 0)
        Assert.assertNotNull("Result should not be null", result)
        Assert.assertEquals("One result expected", 1, result.length())
        Assert.assertEquals("Incorrect result received", 20.1, result.getJSONArray(0).getDouble(0), 0.0)
        store.dropSoup(FOURTH_TEST_SOUP)
        Assert.assertFalse("Soup $FOURTH_TEST_SOUP should have been deleted", store.hasSoup(FOURTH_TEST_SOUP))
    }

    @Test
    fun testCountQueryWithGroupByUsingStringIndexes() { tryCountQueryWithGroupBy(Type.string) }

    @Test
    fun testCountQueryWithGroupByUsingJSON1Indexes() { tryCountQueryWithGroupBy(Type.json1) }

    private fun tryCountQueryWithGroupBy(type: Type) {
        Assert.assertFalse("Soup third_test_soup should not exist", store.hasSoup(THIRD_TEST_SOUP))
        registerSoup(store, THIRD_TEST_SOUP, arrayOf(IndexSpec("key", type), IndexSpec("value", type)))
        Assert.assertTrue("Register soup call failed", store.hasSoup(THIRD_TEST_SOUP))
        store.create(THIRD_TEST_SOUP, JSONObject("{'key':'a', 'value':'va1'}"))
        store.create(THIRD_TEST_SOUP, JSONObject("{'key':'b', 'value':'va1'}"))
        store.create(THIRD_TEST_SOUP, JSONObject("{'key':'c', 'value':'va2'}"))
        store.create(THIRD_TEST_SOUP, JSONObject("{'key':'d', 'value':'va3'}"))
        store.create(THIRD_TEST_SOUP, JSONObject("{'key':'e', 'value':'va3'}"))
        val smartSql = "SELECT {$THIRD_TEST_SOUP:value}, count(*) FROM {$THIRD_TEST_SOUP} GROUP BY {$THIRD_TEST_SOUP:value} ORDER BY {$THIRD_TEST_SOUP:value}"
        val querySpec = QuerySpec.buildSmartQuerySpec(smartSql, 25)
        val result = store.query(querySpec, 0)
        Assert.assertNotNull("Result should not be null", result)
        Assert.assertEquals("Three results expected", 3, result.length())
        JSONTestHelper.assertSameJSON("Wrong result for query", JSONArray("[['va1', 2], ['va2', 1], ['va3', 2]]"), result)
        val count = store.countQuery(querySpec)
        Assert.assertEquals("Incorrect count query", "SELECT count(*) FROM ($smartSql)", querySpec.countSmartSql)
        Assert.assertEquals("Incorrect count", 3, count)
    }

    @Test
    fun testIntegerIndexedField() {
        registerSoup(store, FOURTH_TEST_SOUP, arrayOf(IndexSpec("amount", Type.integer)))
        tryNumber(Type.integer, Int.MIN_VALUE, Int.MIN_VALUE.toLong())
        tryNumber(Type.integer, Int.MAX_VALUE, Int.MAX_VALUE.toLong())
        tryNumber(Type.integer, Long.MIN_VALUE, Long.MIN_VALUE)
        tryNumber(Type.integer, Long.MAX_VALUE, Long.MAX_VALUE)
        tryNumber(Type.integer, Double.MIN_VALUE, Double.MIN_VALUE.toLong())
        tryNumber(Type.integer, Double.MAX_VALUE, Double.MAX_VALUE.toLong())
    }

    @Test
    fun testFloatingIndexedField() {
        registerSoup(store, FOURTH_TEST_SOUP, arrayOf(IndexSpec("amount", Type.floating)))
        tryNumber(Type.floating, Int.MIN_VALUE, Int.MIN_VALUE.toDouble())
        tryNumber(Type.floating, Int.MAX_VALUE, Int.MAX_VALUE.toDouble())
        tryNumber(Type.floating, Long.MIN_VALUE, Long.MIN_VALUE.toDouble())
        tryNumber(Type.floating, Long.MAX_VALUE, Long.MAX_VALUE.toDouble())
        tryNumber(Type.floating, Double.MIN_VALUE, Double.MIN_VALUE)
        tryNumber(Type.floating, Double.MAX_VALUE, Double.MAX_VALUE)
    }

    private fun tryNumber(fieldType: Type, valueIn: Number, valueOut: Number) {
        val elt = JSONObject()
        elt.put("amount", valueIn)
        val id = store.upsert(FOURTH_TEST_SOUP, elt)!!.getLong(SmartStore.SOUP_ENTRY_ID)!!
        var c: Cursor? = null
        try {
            val db: SQLiteDatabase = dbOpenHelper.writableDatabase
            val soupTableName = getSoupTableName(FOURTH_TEST_SOUP)
            val amountColumnName = store.getSoupIndexSpecs(FOURTH_TEST_SOUP)[0].columnName!!
            c = DBHelper.getInstance(db).query(db, soupTableName, arrayOf(amountColumnName), null, null, "id = $id")
            Assert.assertTrue("Expected a soup element", c.moveToFirst())
            Assert.assertEquals("Expected one soup element", 1, c.count)
            if (fieldType == Type.integer)
                Assert.assertEquals("Not the value expected", valueOut.toLong(), c.getLong(0))
            else if (fieldType == Type.floating)
                Assert.assertEquals("Not the value expected", valueOut.toDouble(), c.getDouble(0), 0.0)
        } finally { safeClose(c) }
    }

    /**
     * Test using smart sql to retrieve integer indexed fields
     */
    @Test
    fun testIntegerIndexedFieldWithSmartSql() {
        registerSoup(store, FOURTH_TEST_SOUP, arrayOf(IndexSpec("amount", Type.integer)))
        tryNumberWithSmartSql(Type.integer, Int.MIN_VALUE, Int.MIN_VALUE)
        tryNumberWithSmartSql(Type.integer, Int.MAX_VALUE, Int.MAX_VALUE)
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
    fun testFloatingIndexedFieldWithSmartSql() {
        registerSoup(store, FOURTH_TEST_SOUP, arrayOf(IndexSpec("amount", Type.floating)))
        tryNumberWithSmartSql(Type.floating, Int.MIN_VALUE, Int.MIN_VALUE)
        tryNumberWithSmartSql(Type.floating, Int.MAX_VALUE, Int.MAX_VALUE)
        tryNumberWithSmartSql(Type.floating, Long.MIN_VALUE, Long.MIN_VALUE)
        tryNumberWithSmartSql(Type.floating, Long.MIN_VALUE, Long.MIN_VALUE)
        tryNumberWithSmartSql(Type.floating, Double.MIN_VALUE, Double.MIN_VALUE)
        tryNumberWithSmartSql(Type.floating, Double.MAX_VALUE, Double.MAX_VALUE)
    }

    /**
     * Test using smart sql to retrieve number fields indexed with json1
     */
    @Test
    fun testNumberFieldWithJSON1IndexWithSmartSql() {
        store.registerSoup(FOURTH_TEST_SOUP, arrayOf(IndexSpec("amount", Type.json1)))
        tryNumberWithSmartSql(Type.integer, Int.MIN_VALUE, Int.MIN_VALUE)
        tryNumberWithSmartSql(Type.integer, Int.MAX_VALUE, Int.MAX_VALUE)
        tryNumberWithSmartSql(Type.integer, Long.MIN_VALUE, Long.MIN_VALUE)
        tryNumberWithSmartSql(Type.integer, Long.MIN_VALUE, Long.MIN_VALUE)
        tryNumberWithSmartSql(Type.floating, Math.PI, Math.PI)
    }

    /**
     * Helper method for testIntegerIndexedFieldWithSmartSql and testFloatingIndexedFieldWithSmartSql
     * Insert soup element with number and retrieve it back using smartsql
     */
    private fun tryNumberWithSmartSql(fieldType: Type, valueIn: Number, valueOut: Number) {
        val smartSql = "SELECT {$FOURTH_TEST_SOUP:amount} FROM {$FOURTH_TEST_SOUP} WHERE {$FOURTH_TEST_SOUP:_soupEntryId} = "
        val elt = JSONObject()
        elt.put("amount", valueIn)
        val id = store.upsert(FOURTH_TEST_SOUP, elt)!!.getLong(SmartStore.SOUP_ENTRY_ID)!!
        val actualValueOut = store.query(QuerySpec.buildSmartQuerySpec(smartSql + id, 1), 0).getJSONArray(0).get(0) as Number
        if (fieldType == Type.integer)
            Assert.assertEquals("Not the value expected", valueOut.toLong(), actualValueOut.toLong())
        else if (fieldType == Type.floating)
            Assert.assertEquals("Not the value expected", valueOut.toDouble(), actualValueOut.toDouble(), 0.0)
    }

    @Test
    fun testGetDatabaseSize() {
        val initialSize = store.getDatabaseSize()
        for (i in 0 until 100) {
            store.create(TEST_SOUP, JSONObject("{'key':'abcd$i', 'value':'va$i', 'otherValue':'ova$i'}"))
        }
        Assert.assertTrue("Database should be larger now", store.getDatabaseSize() > initialSize)
    }

    @Test
    fun testRegisterSoupWithJSON1() {
        Assert.assertFalse("Soup other_test_soup should not exist", store.hasSoup(OTHER_TEST_SOUP))
        store.registerSoup(OTHER_TEST_SOUP, arrayOf(IndexSpec("lastName", Type.json1), IndexSpec("address.city", Type.json1), IndexSpec("address.zipcode", Type.string)))
        Assert.assertTrue("Register soup call failed", store.hasSoup(OTHER_TEST_SOUP))
        val soupTableName = getSoupTableName(OTHER_TEST_SOUP)
        checkColumns(soupTableName, listOf("id", "soup", "created", "lastModified", soupTableName + "_2"))
        val indexSpecs = store.getSoupIndexSpecs(OTHER_TEST_SOUP)
        Assert.assertEquals("Wrong path", "lastName", indexSpecs[0].path)
        Assert.assertEquals("Wrong type", Type.json1, indexSpecs[0].type)
        Assert.assertEquals("Wrong column name", "json_extract(soup, '\$.lastName')", indexSpecs[0].columnName)
        Assert.assertEquals("Wrong path", "address.city", indexSpecs[1].path)
        Assert.assertEquals("Wrong type", Type.json1, indexSpecs[1].type)
        Assert.assertEquals("Wrong column name", "json_extract(soup, '\$.address.city')", indexSpecs[1].columnName)
        Assert.assertEquals("Wrong path", "address.zipcode", indexSpecs[2].path)
        Assert.assertEquals("Wrong type", Type.string, indexSpecs[2].type)
        Assert.assertEquals("Wrong column name", soupTableName + "_2", indexSpecs[2].columnName)
        checkDatabaseIndexes(soupTableName, listOf(
            "CREATE INDEX ${soupTableName}_0_idx on $soupTableName ( json_extract(soup, '\$.lastName') )",
            "CREATE INDEX ${soupTableName}_1_idx on $soupTableName ( json_extract(soup, '\$.address.city') )",
            "CREATE INDEX ${soupTableName}_2_idx on $soupTableName ( ${soupTableName}_2 )",
            "CREATE INDEX ${soupTableName}_created_idx on $soupTableName ( created )",
            "CREATE INDEX ${soupTableName}_lastModified_idx on $soupTableName ( lastModified )"
        ))
    }

    @Test
    fun testDeleteAgainstChangedSoup() {
        val soupElt1Created = store.create(TEST_SOUP, JSONObject("{'key':'ka1', 'value':'va1'}"))!!
        val soupElt2Created = store.create(TEST_SOUP, JSONObject("{'key':'ka2', 'value':'va2'}"))!!
        val soupElt3Created = store.create(TEST_SOUP, JSONObject("{'key':'ka3', 'value':'va3'}"))!!
        val soupElt4Created = store.create(TEST_SOUP, JSONObject("{'key':'ka4', 'value':'va4'}"))!!
        tryAllQueryOnChangedSoupWithUpdate(TEST_SOUP, soupElt2Created, "value", arrayOf(IndexSpec("value", Type.string)), soupElt1Created, soupElt3Created, soupElt4Created)
        tryAllQueryOnChangedSoupWithUpdate(TEST_SOUP, soupElt4Created, "key", arrayOf(IndexSpec("key", Type.json1)), soupElt1Created, soupElt3Created)
        tryAllQueryOnChangedSoupWithUpdate(TEST_SOUP, soupElt4Created, "key", arrayOf(IndexSpec("key", Type.json1), IndexSpec("value", Type.string)), soupElt1Created, soupElt3Created)
    }

    protected open fun tryAllQueryOnChangedSoupWithUpdate(soupName: String, deletedEntry: JSONObject, orderPath: String, newIndexSpecs: Array<IndexSpec>, vararg expectedResults: JSONObject) {
        store.alterSoup(soupName, newIndexSpecs, true)
        store.delete(soupName, idOf(deletedEntry))
        runQueryCheckResultsAndExplainPlan(soupName, QuerySpec.buildAllQuerySpec(soupName, orderPath, Order.ascending, 5), 0, false, "SCAN", *expectedResults)
    }

    @Test
    fun testUpsertAgainstChangedSoup() {
        val soupElt1Created = store.create(TEST_SOUP, JSONObject("{'key':'ka1', 'value':'va1'}"))!!
        val soupElt2Created = store.create(TEST_SOUP, JSONObject("{'key':'ka2', 'value':'va2'}"))!!
        val soupElt3Created = store.create(TEST_SOUP, JSONObject("{'key':'ka3', 'value':'va3'}"))!!

        store.alterSoup(TEST_SOUP, arrayOf(IndexSpec("value", Type.string)), true)
        val soupElt1Upserted = store.upsert(TEST_SOUP, JSONObject("{'key':'ka1u', 'value':'va1u'}"))!!
        runQueryCheckResultsAndExplainPlan(TEST_SOUP, QuerySpec.buildAllQuerySpec(TEST_SOUP, "value", Order.ascending, 10), 0, false, "SCAN", soupElt1Created, soupElt1Upserted, soupElt2Created, soupElt3Created)

        store.alterSoup(TEST_SOUP, arrayOf(IndexSpec("key", Type.json1)), true)
        val soupElt2Upserted = store.upsert(TEST_SOUP, JSONObject("{'key':'ka2u', 'value':'va2u'}"))!!
        runQueryCheckResultsAndExplainPlan(TEST_SOUP, QuerySpec.buildAllQuerySpec(TEST_SOUP, "key", Order.ascending, 10), 0, false, "SCAN", soupElt1Created, soupElt1Upserted, soupElt2Created, soupElt2Upserted, soupElt3Created)

        store.alterSoup(TEST_SOUP, arrayOf(IndexSpec("key", Type.json1), IndexSpec("value", Type.string)), true)
        val soupElt3Upserted = store.upsert(TEST_SOUP, JSONObject("{'key':'ka3u', 'value':'va3u'}"))!!
        runQueryCheckResultsAndExplainPlan(TEST_SOUP, QuerySpec.buildAllQuerySpec(TEST_SOUP, "key", Order.ascending, 10), 0, false, "SCAN", soupElt1Created, soupElt1Upserted, soupElt2Created, soupElt2Upserted, soupElt3Created, soupElt3Upserted)
    }

    @Test
    fun testExactQueryAgainstChangedSoup() {
        val soupElt1Created = store.create(TEST_SOUP, JSONObject("{'key':'ka1', 'value':'va1'}"))!!
        val soupElt2Created = store.create(TEST_SOUP, JSONObject("{'key':'ka1-', 'value':'va1*'}"))!!
        val soupElt3Created = store.create(TEST_SOUP, JSONObject("{'key':'ka1 ', 'value':'va1%'}"))!!
        tryExactQueryOnChangedSoup(TEST_SOUP, "value", "va1", arrayOf(IndexSpec("value", Type.string)), soupElt1Created)
        tryExactQueryOnChangedSoup(TEST_SOUP, "key", "ka1", arrayOf(IndexSpec("key", Type.json1)), soupElt1Created)
        tryExactQueryOnChangedSoup(TEST_SOUP, "key", "ka1 ", arrayOf(IndexSpec("key", Type.json1), IndexSpec("value", Type.string)), soupElt3Created)
    }

    protected open fun tryExactQueryOnChangedSoup(soupName: String, orderPath: String, value: String, newIndexSpecs: Array<IndexSpec>, expectedResult: JSONObject) {
        store.alterSoup(soupName, newIndexSpecs, true)
        runQueryCheckResultsAndExplainPlan(soupName, QuerySpec.buildExactQuerySpec(soupName, orderPath, value, null, null, 5), 0, false, "SEARCH", expectedResult)
    }

    @Test
    fun testUpdateTableNameAndAddColumns() {
        val db: SQLiteDatabase = dbOpenHelper.writableDatabase
        val TEST_TABLE = "test_table"
        val NEW_TEST_TABLE = "new_test_table"
        val NEW_COLUMN = "new_column"
        db.execSQL("CREATE TABLE $TEST_TABLE (id INTEGER PRIMARY KEY)")
        var cursor = db.query("sqlite_master", arrayOf("sql"), "name = ?", arrayOf(NEW_TEST_TABLE), null, null, null)
        Assert.assertEquals("New table should not already be in db.", 0, cursor.count)
        cursor.close()
        SmartStore.updateTableNameAndAddColumns(db, TEST_TABLE, NEW_TEST_TABLE, arrayOf(NEW_COLUMN))
        cursor = db.query("sqlite_master", arrayOf("sql"), "name = ?", arrayOf(NEW_TEST_TABLE), null, null, null)
        cursor.moveToFirst()
        val schema = cursor.getString(0)
        cursor.close()
        Assert.assertTrue("New table not found", schema.contains(NEW_TEST_TABLE))
        Assert.assertTrue("New column not found", schema.contains(NEW_COLUMN))
        db.execSQL("DROP TABLE $NEW_TEST_TABLE")
    }

    @Test
    fun testHasSoup() {
        Assert.assertTrue(store.hasSoup(TEST_SOUP))
        Assert.assertFalse(store.hasSoup(OTHER_TEST_SOUP))
        Assert.assertFalse(store.hasSoup(THIRD_TEST_SOUP))
        registerSoup(store, OTHER_TEST_SOUP, arrayOf(IndexSpec("lastName", Type.string), IndexSpec("address.city", Type.string)))
        Assert.assertTrue(store.hasSoup(TEST_SOUP))
        Assert.assertTrue(store.hasSoup(OTHER_TEST_SOUP))
        Assert.assertFalse(store.hasSoup(THIRD_TEST_SOUP))
        registerSoup(store, THIRD_TEST_SOUP, arrayOf(IndexSpec("key", Type.string), IndexSpec("value", Type.string)))
        Assert.assertTrue(store.hasSoup(TEST_SOUP))
        Assert.assertTrue(store.hasSoup(OTHER_TEST_SOUP))
        Assert.assertTrue(store.hasSoup(THIRD_TEST_SOUP))
        store.dropSoup(THIRD_TEST_SOUP)
        Assert.assertTrue(store.hasSoup(TEST_SOUP))
        Assert.assertTrue(store.hasSoup(OTHER_TEST_SOUP))
        Assert.assertFalse(store.hasSoup(THIRD_TEST_SOUP))
        store.dropAllSoups()
        Assert.assertFalse(store.hasSoup(TEST_SOUP))
        Assert.assertFalse(store.hasSoup(OTHER_TEST_SOUP))
        Assert.assertFalse(store.hasSoup(THIRD_TEST_SOUP))
    }
}
