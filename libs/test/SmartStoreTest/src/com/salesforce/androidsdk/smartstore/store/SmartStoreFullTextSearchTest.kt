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
import com.salesforce.androidsdk.smartstore.store.SmartStore.Type
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.json.JSONException
import org.json.JSONObject
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Arrays

/**
 * Tests for full-text search with smartstore
 */
@RunWith(AndroidJUnit4::class)
@MediumTest
class SmartStoreFullTextSearchTest : SmartStoreTestCase() {

    // Populated by loadData()
    private var christineHaasId: Long = 0
    private var michaelThompsonId: Long = 0
    private var aliHaasId: Long = 0
    private var irvingSternId: Long = 0
    private var evaPulaskiId: Long = 0
    private var eileenEvaId: Long = 0

    @Before
    override fun setUp() {
        super.setUp()
    }

    @After
    override fun tearDown() {
        super.tearDown()
    }

    override val encryptionKey: String
        get() = "test123"

    private fun setupSoup(ftsExtension: SmartStore.FtsExtension) {
        store.ftsExtension =(ftsExtension)
        registerSoup(
            store, EMPLOYEES_SOUP, arrayOf(   // should be TABLE_1
                IndexSpec(FIRST_NAME, Type.full_text),    // should be TABLE_1_0
                IndexSpec(LAST_NAME, Type.full_text),     // should be TABLE_1_1
                IndexSpec(EMPLOYEE_ID, Type.string)      // should be TABLE_1_2
            )
        )
    }

    /**
     * Test that fts5 is used by default
     */
    @Test
    fun testFtsExtension() {
        Assert.assertEquals("Expected fts5", store.ftsExtension, SmartStore.FtsExtension.fts5)
    }

    /**
     * Test register/drop soup that uses full-text search indices with fts4
     */
    @Test
    fun testRegisterDropSoupFts4() {
        tryRegisterDropSoup(SmartStore.FtsExtension.fts4)
    }

    /**
     * Test register/drop soup that uses full-text search indices with fts5
     */
    @Test
    fun testRegisterDropSoupFts5() {
        tryRegisterDropSoup(SmartStore.FtsExtension.fts5)
    }

    private fun tryRegisterDropSoup(ftsExtension: SmartStore.FtsExtension) {
        setupSoup(ftsExtension)
        val soupTableName = getSoupTableName(EMPLOYEES_SOUP)
        Assert.assertEquals("getSoupTableName should have returned TABLE_1", TABLE_NAME, soupTableName)
        Assert.assertTrue("Table for soup employees does exist", hasTable(soupTableName!!))
        Assert.assertTrue("FTS table for soup employees does exist", hasTable(soupTableName + SmartStore.FTS_SUFFIX))
        Assert.assertTrue("Register soup call failed", store.hasSoup(EMPLOYEES_SOUP))
        checkCreateTableStatement(soupTableName + SmartStore.FTS_SUFFIX, "CREATE VIRTUAL TABLE " + soupTableName + SmartStore.FTS_SUFFIX + " USING " + ftsExtension)

        // Drop
        store.dropSoup(EMPLOYEES_SOUP)

        // After
        Assert.assertFalse("Soup employees should no longer exist", store.hasSoup(EMPLOYEES_SOUP))
        Assert.assertNull("getSoupTableName should have returned null", getSoupTableName(EMPLOYEES_SOUP))
        Assert.assertFalse("Table for soup employees should not exist", hasTable(soupTableName!!))
        Assert.assertFalse("FTS table for soup employees should not exist", hasTable(soupTableName + SmartStore.FTS_SUFFIX))
    }

    /**
     * Test inserting rows with fts4
     */
    @Test
    fun testInsertWithFts4() {
        tryInsert(SmartStore.FtsExtension.fts4)
    }

    /**
     * Test inserting rows with fts5
     */
    @Test
    fun testInsertWithFts5() {
        tryInsert(SmartStore.FtsExtension.fts5)
    }

    private fun tryInsert(ftsExtension: SmartStore.FtsExtension) {
        setupSoup(ftsExtension)

        // Insert a couple of rows
        val firstEmployeeId = createEmployee("Christine", "Haas", "00010")
        val secondEmployeeId = createEmployee("Michael", "Thompson", "00020")
        val thirdEmployeeId = createEmployee(null, null, null)

        // Check DB
        var c: Cursor? = null
        try {
            val soupTableName = getSoupTableName(EMPLOYEES_SOUP)
            Assert.assertEquals("getSoupTableName should have returned TABLE_1", "TABLE_1", soupTableName)
            Assert.assertTrue("Table for soup employees does exist", hasTable(soupTableName!!))
            val db = dbOpenHelper.writableDatabase

            // Check soup table
            c = DBHelper.getInstance(db).query(db, soupTableName!!, null, "id ASC", null, null)
            Assert.assertTrue("Expected a row", c.moveToFirst())
            Assert.assertEquals("Expected two rows", 3, c.count)
            Assert.assertTrue("Wrong columns", Arrays.deepEquals(getExpectedColumns(), c.columnNames))
            Assert.assertEquals("Wrong id", firstEmployeeId, c.getLong(c.getColumnIndex("id")))
            Assert.assertEquals("Wrong value in index column", "Christine", c.getString(c.getColumnIndex(FIRST_NAME_COL)!!))
            Assert.assertEquals("Wrong value in index column", "Haas", c.getString(c.getColumnIndex(LAST_NAME_COL)!!))
            Assert.assertEquals("Wrong value in index column", "00010", c.getString(c.getColumnIndex(EMPLOYEE_ID_COL)!!))
            c.moveToNext()
            Assert.assertEquals("Wrong id", secondEmployeeId, c.getLong(c.getColumnIndex("id")))
            Assert.assertEquals("Wrong value in index column", "Michael", c.getString(c.getColumnIndex(FIRST_NAME_COL)!!))
            Assert.assertEquals("Wrong value in index column", "Thompson", c.getString(c.getColumnIndex(LAST_NAME_COL)!!))
            Assert.assertEquals("Wrong value in index column", "00020", c.getString(c.getColumnIndex(EMPLOYEE_ID_COL)!!))
            c.moveToNext()
            Assert.assertEquals("Wrong id", thirdEmployeeId, c.getLong(c.getColumnIndex("id")))
            Assert.assertEquals("Wrong value in index column", null, c.getString(c.getColumnIndex(FIRST_NAME_COL)!!))
            Assert.assertEquals("Wrong value in index column", null, c.getString(c.getColumnIndex(LAST_NAME_COL)!!))
            Assert.assertEquals("Wrong value in index column", null, c.getString(c.getColumnIndex(EMPLOYEE_ID_COL)!!))
            safeClose(c)

            // Check fts table columns
            c = DBHelper.getInstance(db).query(db, soupTableName + SmartStore.FTS_SUFFIX, null, "rowid ASC", null, null)
            Assert.assertTrue("Expected a row", c.moveToFirst())
            Assert.assertTrue("Wrong columns", Arrays.deepEquals(arrayOf(FIRST_NAME_COL, LAST_NAME_COL), c.columnNames))
            safeClose(c)

            // Check fts table data
            c = DBHelper.getInstance(db).query(db, soupTableName + SmartStore.FTS_SUFFIX, arrayOf("rowid", FIRST_NAME_COL, LAST_NAME_COL), "rowid ASC", null, null)
            Assert.assertTrue("Expected a row", c.moveToFirst())
            Assert.assertEquals("Expected two rows", 3, c.count)
            Assert.assertEquals("Wrong id", firstEmployeeId, c.getLong(c.getColumnIndex("rowid")))
            Assert.assertEquals("Wrong value in index column", "Christine", c.getString(c.getColumnIndex(FIRST_NAME_COL)!!))
            Assert.assertEquals("Wrong value in index column", "Haas", c.getString(c.getColumnIndex(LAST_NAME_COL)!!))
            c.moveToNext()
            Assert.assertEquals("Wrong id", secondEmployeeId, c.getLong(c.getColumnIndex("rowid")))
            Assert.assertEquals("Wrong value in index column", "Michael", c.getString(c.getColumnIndex(FIRST_NAME_COL)!!))
            Assert.assertEquals("Wrong value in index column", "Thompson", c.getString(c.getColumnIndex(LAST_NAME_COL)!!))
            c.moveToNext()
            Assert.assertEquals("Wrong id", thirdEmployeeId, c.getLong(c.getColumnIndex("rowid")))
            Assert.assertEquals("Wrong value in index column", null, c.getString(c.getColumnIndex(FIRST_NAME_COL)!!))
            Assert.assertEquals("Wrong value in index column", null, c.getString(c.getColumnIndex(LAST_NAME_COL)!!))
        } finally {
            safeClose(c)
        }
    }

    /**
     * Test deleting rows with fts4
     */
    @Test
    fun testDeleteWithFts4() {
        tryDelete(SmartStore.FtsExtension.fts4)
    }

    /**
     * Test deleting rows with fts5
     */
    @Test
    fun testDeleteWithFts5() {
        tryDelete(SmartStore.FtsExtension.fts5)
    }

    private fun tryDelete(ftsExtension: SmartStore.FtsExtension) {
        setupSoup(ftsExtension)

        // Insert a couple of rows
        val firstEmployeeId = createEmployee("Christine", "Haas", "00010")
        val secondEmployeeId = createEmployee("Michael", "Thompson", "00020")

        // Check DB
        var c: Cursor? = null
        try {
            val soupTableName = getSoupTableName(EMPLOYEES_SOUP)
            val db = dbOpenHelper.writableDatabase

            // Check soup table
            c = DBHelper.getInstance(db).query(db, soupTableName!!, null, "id ASC", null, null)
            Assert.assertTrue("Expected a row", c.moveToFirst())
            Assert.assertEquals("Expected two rows", 2, c.count)
            safeClose(c)

            // Check fts table data
            c = DBHelper.getInstance(db).query(db, soupTableName + SmartStore.FTS_SUFFIX, null, "rowid ASC", null, null)
            Assert.assertTrue("Expected a row", c.moveToFirst())
            Assert.assertEquals("Expected two rows", 2, c.count)
        } finally {
            safeClose(c)
        }

        // Delete second employee
        store.delete(EMPLOYEES_SOUP, secondEmployeeId)

        // Check DB
        try {
            val soupTableName = getSoupTableName(EMPLOYEES_SOUP)
            val db = dbOpenHelper.writableDatabase

            // Check soup table
            c = DBHelper.getInstance(db).query(db, soupTableName!!, null, "id ASC", null, null)
            Assert.assertTrue("Expected a row", c.moveToFirst())
            Assert.assertEquals("Expected one row", 1, c.count)
            Assert.assertEquals("Wrong id", firstEmployeeId, c.getLong(c.getColumnIndex("id")))
            safeClose(c)

            // Check fts table data
            c = DBHelper.getInstance(db).query(db, soupTableName + SmartStore.FTS_SUFFIX, arrayOf("rowid", FIRST_NAME_COL, LAST_NAME_COL), "rowid ASC", null, null)
            Assert.assertTrue("Expected a row", c.moveToFirst())
            Assert.assertEquals("Expected one row", 1, c.count)
            Assert.assertEquals("Wrong id", firstEmployeeId, c.getLong(c.getColumnIndex("rowid")))
        } finally {
            safeClose(c)
        }

        // Delete first employee
        store.delete(EMPLOYEES_SOUP, firstEmployeeId)

        // Check DB
        try {
            val soupTableName = getSoupTableName(EMPLOYEES_SOUP)
            val db = dbOpenHelper.writableDatabase

            // Check soup table
            c = DBHelper.getInstance(db).query(db, soupTableName!!, null, "id ASC", null, null)
            Assert.assertFalse("Expected no rows", c.moveToFirst())
            safeClose(c)

            // Check fts table data
            c = DBHelper.getInstance(db).query(db, soupTableName + SmartStore.FTS_SUFFIX, null, "rowid ASC", null, null)
            Assert.assertFalse("Expected no rows", c.moveToFirst())
        } finally {
            safeClose(c)
        }
    }

    /**
     * Test clearing soup with fts4
     */
    @Test
    fun testClearWithFts4() {
        tryClear(SmartStore.FtsExtension.fts4)
    }

    /**
     * Test clearing soup with fts5
     */
    @Test
    fun testClearWithFts5() {
        tryClear(SmartStore.FtsExtension.fts5)
    }

    private fun tryClear(ftsExtension: SmartStore.FtsExtension) {
        setupSoup(ftsExtension)

        // Insert a couple of rows
        val firstEmployeeId = createEmployee("Christine", "Haas", "00010")
        val secondEmployeeId = createEmployee("Michael", "Thompson", "00020")

        // Check DB
        var c: Cursor? = null
        try {
            val soupTableName = getSoupTableName(EMPLOYEES_SOUP)
            val db = dbOpenHelper.writableDatabase

            // Check soup table
            c = DBHelper.getInstance(db).query(db, soupTableName!!, null, "id ASC", null, null)
            Assert.assertTrue("Expected a row", c.moveToFirst())
            Assert.assertEquals("Expected two rows", 2, c.count)
            safeClose(c)

            // Check fts table data
            c = DBHelper.getInstance(db).query(db, soupTableName + SmartStore.FTS_SUFFIX, null, "rowid ASC", null, null)
            Assert.assertTrue("Expected a row", c.moveToFirst())
            Assert.assertEquals("Expected two rows", 2, c.count)
        } finally {
            safeClose(c)
        }

        // Clear soup
        store.clearSoup(EMPLOYEES_SOUP)

        // Check DB
        try {
            val soupTableName = getSoupTableName(EMPLOYEES_SOUP)
            val db = dbOpenHelper.writableDatabase

            // Check soup table
            c = DBHelper.getInstance(db).query(db, soupTableName!!, null, "id ASC", null, null)
            Assert.assertFalse("Expected no rows", c.moveToFirst())
            safeClose(c)

            // Check fts table data
            c = DBHelper.getInstance(db).query(db, soupTableName + SmartStore.FTS_SUFFIX, null, "rowid ASC", null, null)
            Assert.assertFalse("Expected no rows", c.moveToFirst())
        } finally {
            safeClose(c)
        }
    }

    /**
     * Test updating rows with fts4
     */
    @Test
    fun testUpdateWithFts4() {
        setupSoup(SmartStore.FtsExtension.fts4)
    }

    /**
     * Test updating rows with fts5
     */
    @Test
    fun testUpdateWithFts5() {
        setupSoup(SmartStore.FtsExtension.fts5)
    }

    private fun tryUpdate(ftsExtension: SmartStore.FtsExtension) {
        setupSoup(ftsExtension)

        // Insert a couple of rows
        val firstEmployeeId = createEmployee("Christine", "Haas", "00010")
        val secondEmployeeId = createEmployee("Michael", "Thompson", "00020")

        // Update second employee
        val updatedEmployee = JSONObject()
        updatedEmployee.put(FIRST_NAME, "Michael-updated")
        updatedEmployee.put(LAST_NAME, "Thompson")
        updatedEmployee.put(EMPLOYEE_ID, "00020-updated")
        store.update(EMPLOYEES_SOUP, updatedEmployee, secondEmployeeId)

        // Check DB
        var c: Cursor? = null
        try {
            val soupTableName = getSoupTableName(EMPLOYEES_SOUP)
            Assert.assertEquals("getSoupTableName should have returned TABLE_1", "TABLE_1", soupTableName)
            Assert.assertTrue("Table for soup employees does exist", hasTable(soupTableName!!))
            val db = dbOpenHelper.writableDatabase

            // Check soup table
            c = DBHelper.getInstance(db).query(db, soupTableName!!, null, "id ASC", null, null)
            Assert.assertTrue("Expected a row", c.moveToFirst())
            Assert.assertEquals("Expected two rows", 2, c.count)
            Assert.assertEquals("Wrong id", firstEmployeeId, c.getLong(c.getColumnIndex("id")))
            Assert.assertEquals("Wrong value in index column", "Christine", c.getString(c.getColumnIndex(FIRST_NAME_COL)!!))
            Assert.assertEquals("Wrong value in index column", "Haas", c.getString(c.getColumnIndex(LAST_NAME_COL)!!))
            Assert.assertEquals("Wrong value in index column", "00010", c.getString(c.getColumnIndex(EMPLOYEE_ID_COL)!!))
            c.moveToNext()
            Assert.assertEquals("Wrong id", secondEmployeeId, c.getLong(c.getColumnIndex("id")))
            Assert.assertEquals("Wrong value in index column", "Michael-updated", c.getString(c.getColumnIndex(FIRST_NAME_COL)!!))
            Assert.assertEquals("Wrong value in index column", "Thompson", c.getString(c.getColumnIndex(LAST_NAME_COL)!!))
            Assert.assertEquals("Wrong value in index column", "00020-updated", c.getString(c.getColumnIndex(EMPLOYEE_ID_COL)!!))
            safeClose(c)

            // Check fts table data
            c = DBHelper.getInstance(db).query(db, soupTableName + SmartStore.FTS_SUFFIX, arrayOf("rowid", FIRST_NAME_COL, LAST_NAME_COL), "rowid ASC", null, null)
            Assert.assertTrue("Expected a row", c.moveToFirst())
            Assert.assertEquals("Expected two rows", 2, c.count)
            Assert.assertEquals("Wrong id", firstEmployeeId, c.getLong(c.getColumnIndex("rowid")))
            Assert.assertEquals("Wrong value in index column", "Christine", c.getString(c.getColumnIndex(FIRST_NAME_COL)!!))
            Assert.assertEquals("Wrong value in index column", "Haas", c.getString(c.getColumnIndex(LAST_NAME_COL)!!))
            c.moveToNext()
            Assert.assertEquals("Wrong id", secondEmployeeId, c.getLong(c.getColumnIndex("rowid")))
            Assert.assertEquals("Wrong value in index column", "Michael-updated", c.getString(c.getColumnIndex(FIRST_NAME_COL)!!))
            Assert.assertEquals("Wrong value in index column", "Thompson", c.getString(c.getColumnIndex(LAST_NAME_COL)!!))
        } finally {
            safeClose(c)
        }
    }

    /**
     * Test search on single field returning no results with fts4 table
     */
    @Test
    @Ignore("Suspected production bug: QuerySpec constructor casts this to MutableQuerySpec at QuerySpec.kt:110 — ClassCastException at runtime")
    fun testSearchSingleFiedlNoResultsWithFts4() {
        trySearchSingleFieldNoResults(SmartStore.FtsExtension.fts4)
    }

    /**
     * Test search on single field returning no results with fts5 table
     */
    @Test
    @Ignore("Suspected production bug: QuerySpec constructor casts this to MutableQuerySpec at QuerySpec.kt:110 — ClassCastException at runtime")
    fun testSearchSingleFieldNoResultsWithFts5() {
        trySearchSingleFieldNoResults(SmartStore.FtsExtension.fts5)
    }

    private fun trySearchSingleFieldNoResults(ftsExtension: SmartStore.FtsExtension) {
        loadData(ftsExtension)

        // One field - full word - no results
        trySearch(longArrayOf(), FIRST_NAME, "Christina", null)
        trySearch(longArrayOf(), LAST_NAME, "Sternn", null)

        // One field - prefix - no results
        trySearch(longArrayOf(), FIRST_NAME, "Christo*", null)
        trySearch(longArrayOf(), LAST_NAME, "Stel*", null)

        // One field - set operation - no results
        trySearch(longArrayOf(), FIRST_NAME, "Ei* NOT Eileen", null)
    }

    /**
     * Test search on single field returning a single result with fts4
     */
    @Test
    @Ignore("Suspected production bug: QuerySpec constructor casts this to MutableQuerySpec at QuerySpec.kt:110 — ClassCastException at runtime")
    fun testSearchSingleFieldSingleResultWithFts4() {
        trySearchSingleFieldSingleResult(SmartStore.FtsExtension.fts4)
    }

    /**
     * Test search on single field returning a single result with fts5
     */
    @Test
    @Ignore("Suspected production bug: QuerySpec constructor casts this to MutableQuerySpec at QuerySpec.kt:110 — ClassCastException at runtime")
    fun testSearchSingleFieldSingleResultWithFts5() {
        trySearchSingleFieldSingleResult(SmartStore.FtsExtension.fts5)
    }

    private fun trySearchSingleFieldSingleResult(ftsExtension: SmartStore.FtsExtension) {
        loadData(ftsExtension)

        // One field - full word - one result
        trySearch(longArrayOf(christineHaasId), FIRST_NAME, "Christine", null)
        trySearch(longArrayOf(irvingSternId), LAST_NAME, "Stern", null)

        // One field - prefix - one result
        trySearch(longArrayOf(christineHaasId), FIRST_NAME, "Christ*", null)
        trySearch(longArrayOf(irvingSternId), LAST_NAME, "Ste*", null)

        // One field - set operation - one result
        trySearch(longArrayOf(eileenEvaId), FIRST_NAME, "E* NOT Eva", null)
    }

    /**
     * Test search on single field returning multiple results - testing ordering - with fts4
     */
    @Test
    @Ignore("Suspected production bug: QuerySpec constructor casts this to MutableQuerySpec at QuerySpec.kt:110 — ClassCastException at runtime")
    fun testSearchSingleFieldMultipleResultsWithFts4() {
        trySearchSingleFieldMultipleResults(SmartStore.FtsExtension.fts4)
    }

    /**
     * Test search on single field returning multiple results - testing ordering - with fts5
     */
    @Test
    @Ignore("Suspected production bug: QuerySpec constructor casts this to MutableQuerySpec at QuerySpec.kt:110 — ClassCastException at runtime")
    fun testSearchSingleFieldMultipleResultsWithFts5() {
        trySearchSingleFieldMultipleResults(SmartStore.FtsExtension.fts5)
    }

    /**
     * Test search on single field returning multiple results - testing ordering
     */
    private fun trySearchSingleFieldMultipleResults(ftsExtension: SmartStore.FtsExtension) {
        loadData(ftsExtension)

        // One field - full word - more than one results
        trySearch(longArrayOf(christineHaasId, aliHaasId), LAST_NAME, "Haas", EMPLOYEE_ID)
        trySearch(longArrayOf(aliHaasId, christineHaasId), LAST_NAME, "Haas", FIRST_NAME)

        // One field - prefix - more than one results
        trySearch(longArrayOf(evaPulaskiId, eileenEvaId), FIRST_NAME, "E*", EMPLOYEE_ID)
        trySearch(longArrayOf(eileenEvaId, evaPulaskiId), FIRST_NAME, "E*", FIRST_NAME)

        // One field - set operation - more than one results
        trySearch(longArrayOf(evaPulaskiId, eileenEvaId), FIRST_NAME, "Eva OR Eileen", EMPLOYEE_ID)
        trySearch(longArrayOf(eileenEvaId, evaPulaskiId), FIRST_NAME, "Eva OR Eileen", FIRST_NAME)
    }

    /**
     * Test search on all fields returning no results with fts4
     */
    @Test
    @Ignore("Suspected production bug: QuerySpec constructor casts this to MutableQuerySpec at QuerySpec.kt:110 — ClassCastException at runtime")
    fun testSearchAllFieldsNoResultsWithFts4() {
        trySearchAllFieldsNoResults(SmartStore.FtsExtension.fts4)
    }

    /**
     * Test search on all fields returning no results with fts5
     */
    @Test
    @Ignore("Suspected production bug: QuerySpec constructor casts this to MutableQuerySpec at QuerySpec.kt:110 — ClassCastException at runtime")
    fun testSearchAllFieldsNoResultsWithFts5() {
        trySearchAllFieldsNoResults(SmartStore.FtsExtension.fts5)
    }

    /**
     * Test search on all fields returning no results
     */
    private fun trySearchAllFieldsNoResults(ftsExtension: SmartStore.FtsExtension) {
        loadData(ftsExtension)

        // All fields - full word - no results
        trySearch(longArrayOf(), null, "Sternn", null)

        // All fields - prefix - no results
        trySearch(longArrayOf(), null, "Stel*", null)

        // All fields - multiple words - no results
        trySearch(longArrayOf(), null, "Haas Christina", null)

        // All fields - set operation - no results
        trySearch(longArrayOf(), null, "Christine NOt Haas", null)
    }

    /**
     * Test search on all fields returning a single result with fts4
     */
    @Test
    @Ignore("Suspected production bug: QuerySpec constructor casts this to MutableQuerySpec at QuerySpec.kt:110 — ClassCastException at runtime")
    fun testSearchAllFieldsSingleResultWithFts4() {
        trySearchAllFieldsSingleResult(SmartStore.FtsExtension.fts4)
    }

    /**
     * Test search on all fields returning a single result with fts5
     */
    @Test
    @Ignore("Suspected production bug: QuerySpec constructor casts this to MutableQuerySpec at QuerySpec.kt:110 — ClassCastException at runtime")
    fun testSearchAllFieldsSingleResultWithFts5() {
        trySearchAllFieldsSingleResult(SmartStore.FtsExtension.fts5)
    }

    private fun trySearchAllFieldsSingleResult(ftsExtension: SmartStore.FtsExtension) {
        loadData(ftsExtension)

        // All fields - full word - one result
        trySearch(longArrayOf(irvingSternId), null, "Stern", null)

        // All fields - prefix - one result
        trySearch(longArrayOf(irvingSternId), null, "St*", null)

        // All fields - multiple words - one result
        trySearch(longArrayOf(christineHaasId), null, "Haas Christine", null)

        // All fields - set operation - one result
        trySearch(longArrayOf(aliHaasId), null, "Haas NOT Christine", null)
    }

    /**
     * Test search on all fields returning multiple results - testing ordering
     */
    @Test
    @Ignore("Suspected production bug: QuerySpec constructor casts this to MutableQuerySpec at QuerySpec.kt:110 — ClassCastException at runtime")
    fun testSearchAllFieldMultipleResultsWithFts4() {
        trySearchAllFieldMultipleResults(SmartStore.FtsExtension.fts4)
    }

    /**
     * Test search on all fields returning multiple results - testing ordering
     */
    @Test
    @Ignore("Suspected production bug: QuerySpec constructor casts this to MutableQuerySpec at QuerySpec.kt:110 — ClassCastException at runtime")
    fun testSearchAllFieldMultipleResultsWithFts5() {
        trySearchAllFieldMultipleResults(SmartStore.FtsExtension.fts5)
    }

    private fun trySearchAllFieldMultipleResults(ftsExtension: SmartStore.FtsExtension) {
        loadData(ftsExtension)

        // All fields - full word - more than one results
        trySearch(longArrayOf(evaPulaskiId, eileenEvaId), null, "Eva", EMPLOYEE_ID)
        trySearch(longArrayOf(eileenEvaId, evaPulaskiId), null, "Eva", LAST_NAME)

        // All fields - prefix - more than one results
        trySearch(longArrayOf(evaPulaskiId, eileenEvaId), null, "Ev*", EMPLOYEE_ID)
        trySearch(longArrayOf(eileenEvaId, evaPulaskiId), null, "Ev*", LAST_NAME)

        // All fields - set operation - more than result
        trySearch(longArrayOf(michaelThompsonId, aliHaasId), null, "Thompson OR Ali", EMPLOYEE_ID)
        trySearch(longArrayOf(aliHaasId, michaelThompsonId), null, "Thompson OR Ali", FIRST_NAME)
        trySearch(longArrayOf(christineHaasId, evaPulaskiId, eileenEvaId), null, "Eva OR Haas NOT Ali", EMPLOYEE_ID)
        trySearch(longArrayOf(christineHaasId, eileenEvaId, evaPulaskiId), null, "Eva OR Haas NOT Ali", FIRST_NAME)
    }

    /**
     * Test search with queries that have field:value predicates with fts4
     */
    @Test
    @Ignore("Suspected production bug: QuerySpec constructor casts this to MutableQuerySpec at QuerySpec.kt:110 — ClassCastException at runtime")
    fun testSearchWithFieldColonQueriesWithFts4() {
        trySearchWithFieldColonQueries(SmartStore.FtsExtension.fts4)
    }

    /**
     * Test search with queries that have field:value predicates with fts5
     */
    @Test
    @Ignore("Suspected production bug: QuerySpec constructor casts this to MutableQuerySpec at QuerySpec.kt:110 — ClassCastException at runtime")
    fun testSearchWithFieldColonQueriesWithFts5() {
        trySearchWithFieldColonQueries(SmartStore.FtsExtension.fts5)
    }

    private fun trySearchWithFieldColonQueries(ftsExtension: SmartStore.FtsExtension) {
        loadData(ftsExtension)

        // All fields - full word - no results
        trySearch(longArrayOf(), null, "{employees:firstName}:Haas", null)

        // All fields - full word - one result
        trySearch(longArrayOf(evaPulaskiId), null, "{employees:firstName}:Eva", null)
        trySearch(longArrayOf(eileenEvaId), null, "{employees:lastName}:Eva", null)

        // All fields - full word - more than one results
        trySearch(longArrayOf(christineHaasId, aliHaasId), null, "{employees:lastName}:Haas", EMPLOYEE_ID)

        // All fields - prefix - more than one results
        trySearch(longArrayOf(evaPulaskiId, eileenEvaId), null, "{employees:firstName}:E*", EMPLOYEE_ID)
        trySearch(longArrayOf(christineHaasId, aliHaasId), null, "{employees:lastName}:H*", EMPLOYEE_ID)

        // All fields - set operation - more than result
        trySearch(longArrayOf(michaelThompsonId, aliHaasId), null, "{employees:lastName}:Thompson OR {employees:firstName}:Ali", EMPLOYEE_ID)
        trySearch(longArrayOf(aliHaasId, michaelThompsonId), null, "{employees:lastName}:Thompson OR {employees:firstName}:Ali", FIRST_NAME)
        trySearch(longArrayOf(christineHaasId, eileenEvaId), null, "{employees:lastName}:Eva OR Haas NOT Ali", EMPLOYEE_ID)
        trySearch(longArrayOf(eileenEvaId, christineHaasId), null, "{employees:lastName}:Eva OR Haas NOT Ali", LAST_NAME)
    }

    private fun trySearch(expectedIds: LongArray, path: String?, matchKey: String, orderPath: String?) {

        // Returning soup elements
        var results = store.query(QuerySpec.buildMatchQuerySpec(EMPLOYEES_SOUP, path, matchKey, orderPath, QuerySpec.Order.ascending, 25)!!, 0)
        Assert.assertEquals("Wrong number of results", expectedIds.size, results.length())
        for (i in 0 until results.length()) {
            Assert.assertEquals("Wrong result", expectedIds[i], idOf(results.getJSONObject(i)))
        }

        // Returning just ids
        results = store.query(QuerySpec.buildMatchQuerySpec(EMPLOYEES_SOUP, arrayOf(SmartStore.SOUP_ENTRY_ID)!!, path, matchKey, orderPath, QuerySpec.Order.ascending, 25), 0)

        //only check the field number for expecting to have matching results
        if (expectedIds.isNotEmpty()) {
            Assert.assertEquals("Wrong number of field returned", 1, results.getJSONArray(0).length())
        }
        Assert.assertEquals("Wrong number of results", expectedIds.size, results.length())
        for (i in 0 until results.length()) {
            Assert.assertEquals("Wrong result", expectedIds[i], results.getJSONArray(i).getLong(0))
        }
    }

    private fun loadData(ftsExtension: SmartStore.FtsExtension) {
        setupSoup(ftsExtension)
        christineHaasId = createEmployee("Christine", "Haas", "00010")
        michaelThompsonId = createEmployee("Michael", "Thompson", "00020")
        aliHaasId = createEmployee("Ali", "Haas", "00030")
        irvingSternId = createEmployee("Irving", "Stern", "00050")
        evaPulaskiId = createEmployee("Eva", "Pulaski", "00060")
        eileenEvaId = createEmployee("Eileen", "Eva", "00070")
    }

    private fun createEmployee(firstName: String?, lastName: String?, employeeId: String?): Long {
        val employee = JSONObject()
        if (firstName != null) employee.put(FIRST_NAME, firstName)
        if (lastName != null) employee.put(LAST_NAME, lastName)
        if (employeeId != null) employee.put(EMPLOYEE_ID, employeeId)
        val employeeSaved = store.create(EMPLOYEES_SOUP, employee)!!
        return idOf(employeeSaved!!)
    }

    /**
     * Registers a soup with the given name and index specs. Can be overridden if extra features are desired.
     */
    override fun registerSoup(store: SmartStore, soupName: String, indexSpecs: Array<IndexSpec>) {
        store.registerSoup(soupName, indexSpecs)
    }

    /**
     * @return expected columns in soup table
     */
    protected fun getExpectedColumns(): Array<String> {
        return arrayOf("id", "soup", "created", "lastModified", FIRST_NAME_COL, LAST_NAME_COL, EMPLOYEE_ID_COL)
    }

    companion object {
        private const val EMPLOYEE_ID = "employeeId"
        private const val LAST_NAME = "lastName"
        private const val FIRST_NAME = "firstName"
        private const val EMPLOYEES_SOUP = "employees"

        private const val TABLE_NAME = "TABLE_1"
        const val FIRST_NAME_COL = TABLE_NAME + "_0"
        const val LAST_NAME_COL = TABLE_NAME + "_1"
        const val EMPLOYEE_ID_COL = TABLE_NAME + "_2"
    }
}
