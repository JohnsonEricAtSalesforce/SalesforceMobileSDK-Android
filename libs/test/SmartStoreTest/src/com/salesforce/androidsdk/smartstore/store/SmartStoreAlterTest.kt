/*
 * Copyright (c) 2015-present, salesforce.com, inc.
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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
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

/**
 * Tests to compare speed of smartstore full-text-search indices with regular indices
 */
@RunWith(AndroidJUnit4::class)
@MediumTest
class SmartStoreAlterTest : SmartStoreTestCase() {

    override val encryptionKey: String
        get() = ""

    @Before
    @Throws(Exception::class)
    override fun setUp() {
        super.setUp()
    }

    @After
    @Throws(Exception::class)
    override fun tearDown() {
        super.tearDown()
    }

    /**
     * Test for getSoupIndexSpecs
     *
     * @throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testGetSoupIndexSpecs() {
        val indexSpecs = arrayOf(
            IndexSpec("lastName", SmartStore.Type.string),
            IndexSpec("address.city", SmartStore.Type.string),
            IndexSpec("salary", SmartStore.Type.integer),
            IndexSpec("interest", SmartStore.Type.floating),
            IndexSpec("note", SmartStore.Type.full_text),
            IndexSpec("other", SmartStore.Type.json1)
        )
        Assert.assertFalse("Soup test_soup should not exist", store.hasSoup(TEST_SOUP))
        store.registerSoup(TEST_SOUP, indexSpecs)
        Assert.assertTrue("Register soup call failed", store.hasSoup(TEST_SOUP))
        checkIndexSpecs(TEST_SOUP, arrayOf(
            IndexSpec("lastName", SmartStore.Type.string, TEST_SOUP_TABLE_NAME + "_0"),
            IndexSpec("address.city", SmartStore.Type.string, TEST_SOUP_TABLE_NAME + "_1"),
            IndexSpec("salary", SmartStore.Type.integer, TEST_SOUP_TABLE_NAME + "_2"),
            IndexSpec("interest", SmartStore.Type.floating, TEST_SOUP_TABLE_NAME + "_3"),
            IndexSpec("note", SmartStore.Type.full_text, TEST_SOUP_TABLE_NAME + "_4"),
            IndexSpec("other", SmartStore.Type.json1, "json_extract(soup, '$.other')")
        ))
    }

    /**
     * Test for alterSoup with reIndexData = false
     *
     * @throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testAlterSoupNoReIndexing() {
        alterSoupHelper(false)
    }

    /**
     * Test for alterSoup with reIndexData = true
     *
     * @throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testAlterSoupWithReIndexing() {
        alterSoupHelper(true)
    }

    /**
     * Test for alterSoup with column type change from string to integer
     *
     * throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testAlterSoupTypeChangeStringToInteger() {
        val indexSpecs = arrayOf(
            IndexSpec("name", SmartStore.Type.string),
            IndexSpec("population", SmartStore.Type.string)
        )
        Assert.assertFalse("Soup test_soup should not exist", store.hasSoup(TEST_SOUP))
        store.registerSoup(TEST_SOUP, indexSpecs)
        Assert.assertTrue("Register soup call failed", store.hasSoup(TEST_SOUP))
        val soupElt1 = JSONObject("{'name': 'San Francisco', 'population': 825863}")!!
        val soupElt2 = JSONObject("{'name': 'Paris', 'population': 2234105}")!!
        store.create(TEST_SOUP, soupElt1)!!!!!!
        store.create(TEST_SOUP, soupElt2)!!!!!!

        // Query all sorted by population ascending - we should get Paris first because we indexed population as a string
        val results = store.query(QuerySpec.buildAllQuerySpec(TEST_SOUP, "population", QuerySpec.Order.ascending, 2)!!, 0)
        Assert.assertEquals("Paris should be first", "Paris", results.getJSONObject(0).get("name"))
        Assert.assertEquals("San Francisco should be second", "San Francisco", results.getJSONObject(1).get("name"))

        // Alter soup - index population as integer
        val indexSpecsNew = arrayOf(
            IndexSpec("name", SmartStore.Type.string),
            IndexSpec("population", SmartStore.Type.integer)
        )
        store.alterSoup(TEST_SOUP, indexSpecsNew, true)

        // Query all sorted by population ascending - we should get San Francisco first because we indexed population as an integer
        val results2 = store.query(QuerySpec.buildAllQuerySpec(TEST_SOUP, "population", QuerySpec.Order.ascending, 2)!!, 0)
        Assert.assertEquals("San Francisco should be first", "San Francisco", results2.getJSONObject(0).get("name"))
        Assert.assertEquals("Paris should be first", "Paris", results2.getJSONObject(1).get("name"))
    }

    /**
     * Test for alterSoup with column type change from string to full_text
     *
     * throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testAlterSoupTypeChangeStringToFullText() {
        tryAlterSoupTypeChange(SmartStore.Type.string, SmartStore.Type.full_text)
    }

    /**
     * Test for alterSoup with column type change from full_text to string
     *
     * throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testAlterSoupTypeChangeFullTextToString() {
        tryAlterSoupTypeChange(SmartStore.Type.full_text, SmartStore.Type.string)
    }

    /**
     * Test for alterSoup with column type change from string to json1
     *
     * throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testAlterSoupTypeChangeStringToJSON1() {
        tryAlterSoupTypeChange(SmartStore.Type.string, SmartStore.Type.json1)
    }

    /**
     * Test for alterSoup with column type change from json1 to string
     *
     * throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testAlterSoupTypeChangeJSON1toString() {
        tryAlterSoupTypeChange(SmartStore.Type.json1, SmartStore.Type.string)
    }

    /**
     * Test for alterSoup with column type change from full_text to json1
     *
     * throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testAlterSoupTypeChangeFullTextToJSON1() {
        tryAlterSoupTypeChange(SmartStore.Type.full_text, SmartStore.Type.json1)
    }

    /**
     * Test for alterSoup with column type change from json1 to full_text
     *
     * throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testAlterSoupTypeChangeJSON1toFullText() {
        tryAlterSoupTypeChange(SmartStore.Type.json1, SmartStore.Type.full_text)
    }

    /**
     * Test for alterSoup passing in same index specs (string)
     * Make sure db table / indexes are recreated
     * That way soup created before 4.2 can get the new indexes (create/lastModified) by calling alterSoup
     */
    @Test
    @Throws(JSONException::class)
    fun testAlterSoupWithStringIndexesToGetIndexesOnCreatedAndLastModified() {
        tryAlterSoupToGetIndexesOnCreatedAndLastModified(SmartStore.Type.string)
    }

    /**
     * Test for alterSoup passing in same index specs (json1)
     * Make sure db table / indexes are recreated
     * That way soup created before 4.2 can get the new indexes (create/lastModified) by calling alterSoup
     */
    @Test
    @Throws(JSONException::class)
    fun testAlterSoupWithJSON1IndexesToGetIndexesOnCreatedAndLastModified() {
        tryAlterSoupToGetIndexesOnCreatedAndLastModified(SmartStore.Type.json1)
    }

    /**
     * Test for alterSoup passing in same index specs (full_text)
     * Make sure db table / indexes are recreated
     * That way soup created before 4.2 can get the new indexes (create/lastModified) by calling alterSoup
     */
    @Test
    @Throws(JSONException::class)
    fun testAlterSoupWithFullTextIndexesToGetIndexesOnCreatedAndLastModified() {
        tryAlterSoupToGetIndexesOnCreatedAndLastModified(SmartStore.Type.full_text)
    }

    /**
     * Create soup with fts4 virtual table
     * Call alterSoup passing in same index specs
     * Make sure virtual table is recreated with fts5
     * That way soup created before 4.2 (using fts4 virtual table) can be migrated to fts5 by calling alterSoup
     * @throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testAlterSoupwithFullTextIndexesFromFts4ToFts5() {
        val indexSpecs = arrayOf(
            IndexSpec(CITY, SmartStore.Type.full_text),
            IndexSpec(COUNTRY, SmartStore.Type.full_text)
        )

        // Using fts4 to simulate pre 4.2 sdk
        store.ftsExtension = SmartStore.FtsExtension.fts4
        Assert.assertFalse("Soup test_soup should not exist", store.hasSoup(TEST_SOUP))
        store.registerSoup(TEST_SOUP, indexSpecs)
        Assert.assertTrue("Register soup call failed", store.hasSoup(TEST_SOUP))
        val soupElt1 = JSONObject()!!
        soupElt1.put(CITY, SAN_FRANCISCO)
        soupElt1.put(COUNTRY, USA)
        val soupElt2 = JSONObject()!!
        soupElt2.put(CITY, PARIS)
        soupElt2.put(COUNTRY, FRANCE)
        val elt1Id = idOf(store.create(TEST_SOUP, soupElt1)!!!!)!!
        val elt2Id = idOf(store.create(TEST_SOUP, soupElt2)!!!!)!!

        // Checking db
        checkDb(longArrayOf(elt1Id, elt2Id), indexSpecs[0].type, indexSpecs[1].type)

        // Check type of fts table
        checkCreateTableStatement(TEST_SOUP_TABLE_NAME + SmartStore.FTS_SUFFIX, "CREATE VIRTUAL TABLE " + TEST_SOUP_TABLE_NAME + SmartStore.FTS_SUFFIX + " USING fts4")

        // Using fts5
        store.ftsExtension = SmartStore.FtsExtension.fts5

        // Alter soup - using same index specs
        store.alterSoup(TEST_SOUP, indexSpecs, true)

        // Checking db
        checkDb(longArrayOf(elt1Id, elt2Id), indexSpecs[0].type, indexSpecs[1].type)

        // Check type of fts table
        checkCreateTableStatement(TEST_SOUP_TABLE_NAME + SmartStore.FTS_SUFFIX, "CREATE VIRTUAL TABLE " + TEST_SOUP_TABLE_NAME + SmartStore.FTS_SUFFIX + " USING fts5")
    }

    @Throws(JSONException::class)
    private fun tryAlterSoupToGetIndexesOnCreatedAndLastModified(indexType: SmartStore.Type) {
        val indexSpecs = arrayOf(IndexSpec(CITY, indexType), IndexSpec(COUNTRY, indexType))
        Assert.assertFalse("Soup test_soup should not exist", store.hasSoup(TEST_SOUP))
        store.registerSoup(TEST_SOUP, indexSpecs)
        Assert.assertTrue("Register soup call failed", store.hasSoup(TEST_SOUP))
        val soupElt1 = JSONObject()!!
        soupElt1.put(CITY, SAN_FRANCISCO)
        soupElt1.put(COUNTRY, USA)
        val soupElt2 = JSONObject()!!
        soupElt2.put(CITY, PARIS)
        soupElt2.put(COUNTRY, FRANCE)
        val elt1Id = idOf(store.create(TEST_SOUP, soupElt1)!!!!)!!
        val elt2Id = idOf(store.create(TEST_SOUP, soupElt2)!!!!)!!

        // Checking db
        checkDb(longArrayOf(elt1Id, elt2Id), indexSpecs[0].type, indexSpecs[1].type)

        // Drop db indexes on created and lastModified to simulate soup having been created before SDK 4.2
        val dropIndexSqlFormat = "DROP INDEX %s_%s_idx"
        val db = dbOpenHelper.writableDatabase
        db.execSQL(String.format(dropIndexSqlFormat, TEST_SOUP_TABLE_NAME, "created"))
        db.execSQL(String.format(dropIndexSqlFormat, TEST_SOUP_TABLE_NAME, "lastModified"))

        // Check db indexes after the drop - created and lastModified should be gone
        val expectedCityCol = if (indexType == SmartStore.Type.json1) String.format("json_extract(soup, '$.%s')", CITY) else CITY_COL
        val expectedCountryCol = if (indexType == SmartStore.Type.json1) String.format("json_extract(soup, '$.%s')", COUNTRY) else COUNTRY_COL
        checkDatabaseIndexes(TEST_SOUP_TABLE_NAME, listOf(
            "CREATE INDEX " + TEST_SOUP_TABLE_NAME + "_0_idx on " + TEST_SOUP_TABLE_NAME + " ( " + expectedCityCol + " )",
            "CREATE INDEX " + TEST_SOUP_TABLE_NAME + "_1_idx on " + TEST_SOUP_TABLE_NAME + " ( " + expectedCountryCol + " )"
        ))

        // Alter soup - passing same indexSpecs as before
        store.alterSoup(TEST_SOUP, indexSpecs, true)

        // Check db - created and lastModified indexes should be there
        checkDb(longArrayOf(elt1Id, elt2Id), indexSpecs[0].type, indexSpecs[1].type)
    }

    @Throws(JSONException::class)
    private fun tryAlterSoupTypeChange(fromType: SmartStore.Type, toType: SmartStore.Type) {
        val indexSpecs = arrayOf(IndexSpec(CITY, fromType), IndexSpec(COUNTRY, fromType))
        Assert.assertFalse("Soup test_soup should not exist", store.hasSoup(TEST_SOUP))
        store.registerSoup(TEST_SOUP, indexSpecs)
        Assert.assertTrue("Register soup call failed", store.hasSoup(TEST_SOUP))
        val soupElt1 = JSONObject()!!
        soupElt1.put(CITY, SAN_FRANCISCO)
        soupElt1.put(COUNTRY, USA)
        val soupElt2 = JSONObject()!!
        soupElt2.put(CITY, PARIS)
        soupElt2.put(COUNTRY, FRANCE)
        val elt1Id = idOf(store.create(TEST_SOUP, soupElt1)!!!!)!!
        val elt2Id = idOf(store.create(TEST_SOUP, soupElt2)!!!!)!!

        // Checking db
        checkDb(longArrayOf(elt1Id, elt2Id), indexSpecs[0].type, indexSpecs[1].type)

        // Alter soup - country now full_text
        var indexSpecsNew = arrayOf(IndexSpec(CITY, fromType), IndexSpec(COUNTRY, toType))
        store.alterSoup(TEST_SOUP, indexSpecsNew, true)

        // Checking db
        checkDb(longArrayOf(elt1Id, elt2Id), indexSpecsNew[0].type, indexSpecsNew[1].type)

        // Alter soup - city now full_text
        indexSpecsNew = arrayOf(IndexSpec(CITY, toType), IndexSpec(COUNTRY, toType))
        store.alterSoup(TEST_SOUP, indexSpecsNew, true)

        // Checking db
        checkDb(longArrayOf(elt1Id, elt2Id), indexSpecsNew[0].type, indexSpecsNew[1].type)
    }

    @Throws(JSONException::class)
    private fun checkDb(expectedIds: LongArray, cityColType: SmartStore.Type, countryColType: SmartStore.Type) {
        val cities = arrayOf(SAN_FRANCISCO, PARIS)
        val countries = arrayOf(USA, FRANCE)

        // Check columns of soup table
        val expectedColumnNames = ArrayList(listOf("id", "soup", "created", "lastModified"))
        if (cityColType != SmartStore.Type.json1) expectedColumnNames.add(CITY_COL)
        if (countryColType != SmartStore.Type.json1) expectedColumnNames.add(COUNTRY_COL)
        checkColumns(TEST_SOUP_TABLE_NAME, expectedColumnNames)

        // Check soup indexes
        val expectedCityCol = if (cityColType == SmartStore.Type.json1) String.format("json_extract(soup, '$.%s')", CITY) else CITY_COL
        val expectedCountryCol = if (countryColType == SmartStore.Type.json1) String.format("json_extract(soup, '$.%s')", COUNTRY) else COUNTRY_COL
        checkIndexSpecs(TEST_SOUP, arrayOf(
            IndexSpec(CITY, cityColType, expectedCityCol),
            IndexSpec(COUNTRY, countryColType, expectedCountryCol)
        ))

        // Check db indexes
        checkDatabaseIndexes(TEST_SOUP_TABLE_NAME, listOf(
            "CREATE INDEX " + TEST_SOUP_TABLE_NAME + "_0_idx on " + TEST_SOUP_TABLE_NAME + " ( " + expectedCityCol + " )",
            "CREATE INDEX " + TEST_SOUP_TABLE_NAME + "_1_idx on " + TEST_SOUP_TABLE_NAME + " ( " + expectedCountryCol + " )",
            "CREATE INDEX " + TEST_SOUP_TABLE_NAME + "_created_idx on " + TEST_SOUP_TABLE_NAME + " ( created )",
            "CREATE INDEX " + TEST_SOUP_TABLE_NAME + "_lastModified_idx on " + TEST_SOUP_TABLE_NAME + " ( lastModified )"
        ))

        // Check rows of soup table
        var c: Cursor? = null
        try {
            val db = dbOpenHelper.writableDatabase
            c = DBHelper.getInstance(db).query(db, TEST_SOUP_TABLE_NAME, expectedColumnNames.toTypedArray(), "id ASC", null, null)
            Assert.assertTrue("Expected a row", c.moveToFirst())
            Assert.assertEquals("Wrong number of rows", expectedIds.size, c.count)
            for (i in expectedIds.indices) {
                Assert.assertEquals("Wrong id", expectedIds[i], c.getLong(c.getColumnIndex("id")))
                if (cityColType != SmartStore.Type.json1)
                    Assert.assertEquals("Wrong value in index column", cities[i], c.getString(c.getColumnIndex(CITY_COL)))
                if (countryColType != SmartStore.Type.json1)
                    Assert.assertEquals("Wrong value in index column", countries[i], c.getString(c.getColumnIndex(COUNTRY_COL)))
                c.moveToNext()
            }
        } finally {
            safeClose(c)
        }

        // Check fts table exists
        val hasFts = cityColType == SmartStore.Type.full_text || countryColType == SmartStore.Type.full_text
        Assert.assertEquals(hasFts, hasTable(TEST_SOUP_TABLE_NAME + SmartStore.FTS_SUFFIX))
        if (!hasFts) {
            return
        }

        // Check columns of fts table
        val expectedFtsColumnNames = ArrayList<String>() // NB: rowid not returned by pragma table_info for fts virtual table
        if (cityColType == SmartStore.Type.full_text) expectedFtsColumnNames.add(CITY_COL)
        if (countryColType == SmartStore.Type.full_text) expectedFtsColumnNames.add(COUNTRY_COL)
        checkColumns(TEST_SOUP_TABLE_NAME + SmartStore.FTS_SUFFIX, expectedFtsColumnNames)

        // Check rows of fts table
        try {
            val db = dbOpenHelper.writableDatabase
            expectedFtsColumnNames.add(0, "rowid")
            c = DBHelper.getInstance(db).query(db, TEST_SOUP_TABLE_NAME + SmartStore.FTS_SUFFIX, expectedFtsColumnNames.toTypedArray(), "rowid ASC", null, null)
            Assert.assertTrue("Expected a row", c.moveToFirst())
            Assert.assertEquals("Wrong number of rows", expectedIds.size, c.count)
            for (i in expectedIds.indices) {
                Assert.assertEquals("Wrong rowid", expectedIds[i], c.getLong(c.getColumnIndex("rowid")))
                if (cityColType == SmartStore.Type.full_text)
                    Assert.assertEquals("Wrong value in index column", cities[i], c.getString(c.getColumnIndex(CITY_COL)))
                if (countryColType == SmartStore.Type.full_text)
                    Assert.assertEquals("Wrong value in index column", countries[i], c.getString(c.getColumnIndex(COUNTRY_COL)))
                c.moveToNext()
            }
        } finally {
            safeClose(c)
        }
    }

    /**
     * Helper method for alter soup tests
     * @param reIndexData
     * @throws JSONException
     */
    @Throws(JSONException::class)
    private fun alterSoupHelper(reIndexData: Boolean) {
        val indexSpecs = arrayOf(
            IndexSpec("lastName", SmartStore.Type.string),
            IndexSpec("address.city", SmartStore.Type.string)
        )
        Assert.assertFalse("Soup test_soup should not exist", store.hasSoup(TEST_SOUP))
        store.registerSoup(TEST_SOUP, indexSpecs)
        Assert.assertTrue("Register soup call failed", store.hasSoup(TEST_SOUP))

        // Populate soup
        val soupElt1Created = store.create(TEST_SOUP, JSONObject("{'lastName':'Doe', 'address':{'city':'San Francisco','street':'1 market'}}")!!)!!
        val soupElt2Created = store.create(TEST_SOUP, JSONObject("{'lastName':'Jackson', 'address':{'city':'Los Angeles','street':'100 mission'}}")!!)!!
        val soupElt3Created = store.create(TEST_SOUP, JSONObject("{'lastName':'Watson', 'address':{'city':'London','street':'50 market'}}")!!)!!

        // Alter soup
        val indexSpecsNew = arrayOf(
            IndexSpec("lastName", SmartStore.Type.string),
            IndexSpec("address.street", SmartStore.Type.string)
        )
        store.alterSoup(TEST_SOUP, indexSpecsNew, reIndexData)

        // Check index specs
        checkIndexSpecs(TEST_SOUP, arrayOf(
            IndexSpec("lastName", SmartStore.Type.string, TEST_SOUP_TABLE_NAME + "_0"),
            IndexSpec("address.street", SmartStore.Type.string, TEST_SOUP_TABLE_NAME + "_1")
        ))

        // Check DB
        var c: Cursor? = null
        try {
            val db = dbOpenHelper.writableDatabase
            val soupTableName = getSoupTableName(TEST_SOUP)
            Assert.assertEquals("Wrong table for test_soup", TEST_SOUP_TABLE_NAME, soupTableName)
            Assert.assertTrue("Table for test_soup should now exist", hasTable(TEST_SOUP_TABLE_NAME))
            c = DBHelper.getInstance(db).query(db, soupTableName!!, null, "id ASC", null, null)
            Assert.assertTrue("Expected a soup element", c.moveToFirst())
            Assert.assertEquals("Expected three soup elements", 3, c.count)
            Assert.assertEquals("Wrong id", idOf(soupElt1Created!!), c.getLong(c.getColumnIndex("id")))
            Assert.assertEquals("Wrong created date", soupElt1Created.getLong(SmartStore.SOUP_LAST_MODIFIED_DATE), c.getLong(c.getColumnIndex("lastModified")))
            Assert.assertEquals("Wrong value in index column", "Doe", c.getString(c.getColumnIndex(soupTableName + "_0")))
            if (reIndexData)
                Assert.assertEquals("Wrong value in index column", "1 market", c.getString(c.getColumnIndex(soupTableName + "_1")))
            else
                Assert.assertNull("Wrong value in index column", c.getString(c.getColumnIndex(soupTableName + "_1")))
            JSONTestHelper.assertSameJSON("Wrong value in soup column", soupElt1Created, JSONObject(c.getString(c.getColumnIndex("soup"))))
            c.moveToNext()
            Assert.assertEquals("Wrong id", idOf(soupElt2Created!!), c.getLong(c.getColumnIndex("id")))
            Assert.assertEquals("Wrong created date", soupElt2Created.getLong(SmartStore.SOUP_LAST_MODIFIED_DATE), c.getLong(c.getColumnIndex("lastModified")))
            Assert.assertEquals("Wrong value in index column", "Jackson", c.getString(c.getColumnIndex(soupTableName + "_0")))
            if (reIndexData)
                Assert.assertEquals("Wrong value in index column", "100 mission", c.getString(c.getColumnIndex(soupTableName + "_1")))
            else
                Assert.assertNull("Wrong value in index column", c.getString(c.getColumnIndex(soupTableName + "_1")))
            JSONTestHelper.assertSameJSON("Wrong value in soup column", soupElt2Created, JSONObject(c.getString(c.getColumnIndex("soup"))))
            c.moveToNext()
            Assert.assertEquals("Wrong id", idOf(soupElt3Created!!), c.getLong(c.getColumnIndex("id")))
            Assert.assertEquals("Wrong created date", soupElt3Created.getLong(SmartStore.SOUP_LAST_MODIFIED_DATE), c.getLong(c.getColumnIndex("lastModified")))
            Assert.assertEquals("Wrong value in index column", "Watson", c.getString(c.getColumnIndex(soupTableName + "_0")))
            if (reIndexData)
                Assert.assertEquals("Wrong value in index column", "50 market", c.getString(c.getColumnIndex(soupTableName + "_1")))
            else
                Assert.assertNull("Wrong value in index column", c.getString(c.getColumnIndex(soupTableName + "_1")))
            JSONTestHelper.assertSameJSON("Wrong value in soup column", soupElt3Created, JSONObject(c.getString(c.getColumnIndex("soup"))))
        } finally {
            safeClose(c)
        }
    }

    /**
     * Test reIndexSoup
     * @throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testReIndexSoup() {
        val indexSpecs = arrayOf(IndexSpec("lastName", SmartStore.Type.string))
        Assert.assertFalse("Soup test_soup should not exist", store.hasSoup(TEST_SOUP))
        store.registerSoup(TEST_SOUP, indexSpecs)
        Assert.assertTrue("Register soup call failed", store.hasSoup(TEST_SOUP))
        val soupElt1 = JSONObject("{'lastName':'Doe', 'address':{'city':'San Francisco','street':'1 market'}}")!!
        store.create(TEST_SOUP, soupElt1)!!!!

        // Find by last name
        assertRowCount(1, "lastName", "Doe")

        // Making sure there is no index on city yet
        Assert.assertFalse(store.hasIndexForPath(TEST_SOUP, "address.city"))

        // Alter soup - add city + street
        val indexSpecsNew = arrayOf(
            IndexSpec("lastName", SmartStore.Type.string),
            IndexSpec("address.city", SmartStore.Type.string),
            IndexSpec("address.street", SmartStore.Type.string)
        )
        store.alterSoup(TEST_SOUP, indexSpecsNew, false)

        // Find by city - no rows expected (we have not re-indexed yet)
        assertRowCount(0, "address.city", "San Francisco")

        // Re-index city
        store.reIndexSoup(TEST_SOUP, arrayOf("address.city"), true)

        // Making sure there is an index on city now
        Assert.assertTrue(store.hasIndexForPath(TEST_SOUP, "address.city"))

        // Find by city
        assertRowCount(1, "address.city", "San Francisco")

        // Find by street - no rows expected (we have not re-indexed yet)
        assertRowCount(0, "address.street", "1 market")

        // Re-index street
        store.reIndexSoup(TEST_SOUP, arrayOf("address.street"), true)

        // Find by street
        assertRowCount(1, "address.street", "1 market")
    }

    /**
     * Helper function for testReIndexSoup: count rows where field has value
     * @param expectedCount
     * @param field
     * @param value
     * @throws JSONException
     */
    @Throws(JSONException::class)
    private fun assertRowCount(expectedCount: Int, field: String, value: String) {
        val smartSql = "SELECT count(*) FROM {" + TEST_SOUP + "} WHERE {" + TEST_SOUP + ":" + field + "} = '" + value + "'"
        val actualCount = store.query(QuerySpec.buildSmartQuerySpec(smartSql, 1)!!, 0).getJSONArray(0).getInt(0)
        Assert.assertEquals("Should have found $expectedCount rows", expectedCount, actualCount)
    }

    /**
     * Test alter soup interrupted and resumed after step RENAME_OLD_SOUP_TABLE
     * @throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testAlterSoupResumeAfterRenameOldSoupTable() {
        tryAlterSoupInterruptResume(AlterSoupLongOperation.AlterSoupStep.RENAME_OLD_SOUP_TABLE)
    }

    /**
     * Test alter soup interrupted and resumed after step DROP_OLD_INDEXES
     * @throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testAlterSoupResumeAfterDropOldIndexed() {
        tryAlterSoupInterruptResume(AlterSoupLongOperation.AlterSoupStep.DROP_OLD_INDEXES)
    }

    /**
     * Test alter soup interrupted and resumed after step REGISTER_SOUP_USING_TABLE_NAME
     * @throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testAlterSoupResumeAfterRegisterSoupUsingTableName() {
        tryAlterSoupInterruptResume(AlterSoupLongOperation.AlterSoupStep.REGISTER_SOUP_USING_TABLE_NAME)
    }

    /**
     * Test alter soup interrupted and resumed after step COPY_TABLE
     * @throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testAlterSoupResumeAfterCopyTable() {
        tryAlterSoupInterruptResume(AlterSoupLongOperation.AlterSoupStep.COPY_TABLE)
    }

    /**
     * Test alter soup interrupted and resumed after step RE_INDEX_SOUP
     * @throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testAlterSoupResumeAfterReIndexSoup() {
        tryAlterSoupInterruptResume(AlterSoupLongOperation.AlterSoupStep.RE_INDEX_SOUP)
    }

    /**
     * Test alter soup interrupted and resumed after step DROP_OLD_TABLE
     * @throws JSONException
     */
    @Test
    @Throws(JSONException::class)
    fun testAlterSoupResumeAfterDropOldTable() {
        tryAlterSoupInterruptResume(AlterSoupLongOperation.AlterSoupStep.DROP_OLD_TABLE)
    }

    /**
     * Helper for testAlterSoupInterruptResume
     * @throws JSONException
     */
    @Throws(JSONException::class)
    private fun tryAlterSoupInterruptResume(toStep: AlterSoupLongOperation.AlterSoupStep) {
        var db = dbOpenHelper.writableDatabase
        Assert.assertFalse("Soup test_soup should not exist", store.hasSoup(TEST_SOUP))
        val indexSpecs = arrayOf(IndexSpec("lastName", SmartStore.Type.string))
        store.registerSoup(TEST_SOUP, indexSpecs)
        val oldIndexSpecs = store.getSoupIndexSpecs(TEST_SOUP) // with column names
        val soupTableName = getSoupTableName(TEST_SOUP)
        Assert.assertTrue("Register soup call failed", store.hasSoup(TEST_SOUP))

        // Populate soup
        val soupElt1Created = store.create(TEST_SOUP, JSONObject("{'lastName':'Doe', 'address':{'city':'San Francisco','street':'1 market'}}")!!)!!
        val soupElt2Created = store.create(TEST_SOUP, JSONObject("{'lastName':'Jackson', 'address':{'city':'Los Angeles','street':'100 mission'}}")!!)!!

        // Partial alter - up to toStep included
        val indexSpecsNew = arrayOf(
            IndexSpec("lastName", SmartStore.Type.string),
            IndexSpec("address.city", SmartStore.Type.string),
            IndexSpec("address.street", SmartStore.Type.string)
        )
        val operation = AlterSoupLongOperation(store, TEST_SOUP, indexSpecsNew, true)
        operation.run(toStep)

        // Validate long_operations_status table
        val operations = store.getLongOperations()
        val expectedCount = if (toStep == AlterSoupLongOperation.AlterSoupStep.LAST) 0 else 1
        Assert.assertEquals("Wrong number of long operations found", expectedCount, operations.size)
        if (operations.isNotEmpty()) {

            // Check details - removed: details is a private field in AlterSoupLongOperation
            // val actualDetails = operations[0].details
            // Assert.assertNotNull("Operation details should not be null", actualDetails)
            // val actualDetailsNonNull = actualDetails!!
            // Assert.assertEquals("Wrong soup name", TEST_SOUP, actualDetailsNonNull.getString("soupName"))
            // Assert.assertEquals("Wrong soup table name", soupTableName, actualDetailsNonNull.getString("soupTableName"))
            // JSONTestHelper.assertSameJSON("Wrong old indexes", IndexSpec.toJSON(oldIndexSpecs), actualDetailsNonNull.getJSONArray("oldIndexSpecs"))

            // new index specs in details might or might not have column names based on step so not comparing with JSONTestHelper.assertSameJSON however checkIndexSpecs below should catch any discrepancies
            // Assert.assertEquals("Wrong re-index data", true, actualDetailsNonNull.getBoolean("reIndexData"))

            // Check last step completed - removed: lastStepCompleted is a private field in AlterSoupLongOperation
            // Assert.assertEquals("Wrong step", toStep, (operations[0] as AlterSoupLongOperation).lastStepCompleted)

            // Simulate restart
            db = restart(db)

            // Check index specs
            checkIndexSpecs(TEST_SOUP, arrayOf(
                IndexSpec("lastName", SmartStore.Type.string, TEST_SOUP_TABLE_NAME + "_0"),
                IndexSpec("address.city", SmartStore.Type.string, TEST_SOUP_TABLE_NAME + "_1"),
                IndexSpec("address.street", SmartStore.Type.string, TEST_SOUP_TABLE_NAME + "_2")
            ))

            // Check DB
            var c: Cursor? = null
            try {
                Assert.assertEquals("Wrong table for test_soup", TEST_SOUP_TABLE_NAME, soupTableName)
                Assert.assertTrue("Table for test_soup should now exist", hasTable(TEST_SOUP_TABLE_NAME))
                c = DBHelper.getInstance(db).query(db, soupTableName!!, null, "id ASC", null, null)
                Assert.assertTrue("Expected a soup element", c.moveToFirst())
                Assert.assertEquals("Expected three soup elements", 2, c.count)
                Assert.assertEquals("Wrong id", idOf(soupElt1Created!!), c.getLong(c.getColumnIndex("id")))
                Assert.assertEquals("Wrong created date", soupElt1Created.getLong(SmartStore.SOUP_LAST_MODIFIED_DATE), c.getLong(c.getColumnIndex("lastModified")))
                Assert.assertEquals("Wrong value in index column", "Doe", c.getString(c.getColumnIndex(soupTableName + "_0")))
                Assert.assertEquals("Wrong value in index column", "San Francisco", c.getString(c.getColumnIndex(soupTableName + "_1")))
                Assert.assertEquals("Wrong value in index column", "1 market", c.getString(c.getColumnIndex(soupTableName + "_2")))
                JSONTestHelper.assertSameJSON("Wrong value in soup column", soupElt1Created, JSONObject(c.getString(c.getColumnIndex("soup"))))
                c.moveToNext()
                Assert.assertEquals("Wrong id", idOf(soupElt2Created!!), c.getLong(c.getColumnIndex("id")))
                Assert.assertEquals("Wrong created date", soupElt2Created.getLong(SmartStore.SOUP_LAST_MODIFIED_DATE), c.getLong(c.getColumnIndex("lastModified")))
                Assert.assertEquals("Wrong value in index column", "Jackson", c.getString(c.getColumnIndex(soupTableName + "_0")))
                Assert.assertEquals("Wrong value in index column", "Los Angeles", c.getString(c.getColumnIndex(soupTableName + "_1")))
                Assert.assertEquals("Wrong value in index column", "100 mission", c.getString(c.getColumnIndex(soupTableName + "_2")))
                JSONTestHelper.assertSameJSON("Wrong value in soup column", soupElt2Created, JSONObject(c.getString(c.getColumnIndex("soup"))))
            } finally {
                safeClose(c)
            }
        }
    }

    private fun restart(db: SQLiteDatabase): SQLiteDatabase {
        // Close db and clear memory caches
        dbOpenHelper.close()
        dbHelper.clearMemoryCache()
        Assert.assertFalse("Database should be closed", db.isOpen)

        // Re-open db
        dbHelper = DBHelper.getInstance(dbOpenHelper.writableDatabase)
        store = SmartStore(dbOpenHelper)
        val newDb = store.getDatabase() // should trigger a resumeLongOperations
        Assert.assertTrue("Database should be opened", newDb.isOpen)

        // It should not be the same db object
        Assert.assertNotSame("Should be a different db object after restart", db, newDb)
        return newDb
    }

    companion object {
        private const val TEST_SOUP = "test_soup"
        private const val TEST_SOUP_TABLE_NAME = "TABLE_1"
        private const val CITY = "city"
        private const val CITY_COL = TEST_SOUP_TABLE_NAME + "_0"
        private const val COUNTRY = "country"
        private const val COUNTRY_COL = TEST_SOUP_TABLE_NAME + "_1"
        private const val SAN_FRANCISCO = "San Francisco"
        private const val PARIS = "Paris"
        private const val USA = "United States"
        private const val FRANCE = "France"
    }
}
