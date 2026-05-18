/*
 * Copyright (c) 2012-present, salesforce.com, inc.
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
import android.database.Cursor
import androidx.test.platform.app.InstrumentationRegistry
import com.salesforce.androidsdk.analytics.EventBuilderHelper
import com.salesforce.androidsdk.util.JSONTestHelper
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.junit.After
import org.junit.Assert
import org.junit.Before

/**
 * Abstract super class for smart store tests
 */
abstract class SmartStoreTestCase {

    protected lateinit var targetContext: Context
    protected lateinit var dbOpenHelper: SQLiteOpenHelper
    protected lateinit var store: SmartStore
    protected lateinit var dbHelper: DBHelper

    @Before
    open fun setUp() {
        EventBuilderHelper.enableDisable(false)
        targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        dbOpenHelper = DBOpenHelper.getOpenHelper(getEncryptionKey(), targetContext, null)
        dbHelper = DBHelper.getInstance(dbOpenHelper.writableDatabase)
        store = SmartStore(dbOpenHelper)
    }

    protected abstract fun getEncryptionKey(): String

    @After
    open fun tearDown() {
        store.dropAllSoups()
        dbOpenHelper.close()
        dbHelper.clearMemoryCache()
        DBOpenHelper.deleteDatabase(targetContext, null, null)
    }

    /**
     * Helper method to check that a table exists in the database
     */
    protected fun hasTable(tableName: String): Boolean {
        var c: Cursor? = null
        val db: SQLiteDatabase = dbOpenHelper.writableDatabase
        try {
            c = DBHelper.getInstance(db).query(db, "sqlite_master", arrayOf(), null, null, "type = ? and name = ?", "table", tableName)
            return c.count == 1
        } finally {
            safeClose(c)
        }
    }

    /**
     * Helper method to check columns of table
     */
    protected fun checkColumns(tableName: String, expectedColumnNames: List<String>) {
        var c: Cursor? = null
        val db: SQLiteDatabase = dbOpenHelper.writableDatabase
        try {
            val actualColumnNames = mutableListOf<String>()
            c = db.rawQuery(String.format("PRAGMA table_info(%s)", tableName), null)
            while (c.moveToNext()) {
                actualColumnNames.add(c.getString(1))
            }
            JSONTestHelper.assertSameJSONArray("Wrong columns", JSONArray(expectedColumnNames), JSONArray(actualColumnNames))
        } catch (e: Exception) {
            Assert.fail("Failed with error:" + e.message)
        } finally {
            safeClose(c)
        }
    }

    /**
     * Helper method to check index specs on a soup
     */
    protected fun checkIndexSpecs(soupName: String, expectedIndexSpecs: Array<IndexSpec>) {
        val actualIndexSpecs = store.getSoupIndexSpecs(soupName)
        JSONTestHelper.assertSameJSONArray("Wrong index specs", IndexSpec.toJSON(expectedIndexSpecs), IndexSpec.toJSON(actualIndexSpecs))
    }

    /**
     * Helper method to check create table statement that was used to create a table
     */
    protected fun checkCreateTableStatement(tableName: String, subStringExpected: String) {
        var c: Cursor? = null
        val db: SQLiteDatabase = dbOpenHelper.writableDatabase
        try {
            c = db.rawQuery(String.format("SELECT sql FROM sqlite_master WHERE type='table' AND tbl_name='%s' ORDER BY name", tableName), null)
            Assert.assertEquals("Expected one statement", 1, c.count)
            c.moveToFirst()
            val actualStatement = c.getString(0)
            Assert.assertTrue("Wrong statement: $actualStatement", actualStatement.contains(subStringExpected))
        } catch (e: Exception) {
            Assert.fail("Failed with error:" + e.message)
        } finally {
            safeClose(c)
        }
    }

    /**
     * Helper method to check db indexes on a table
     */
    protected fun checkDatabaseIndexes(tableName: String, expectedSqlStatements: List<String>) {
        var c: Cursor? = null
        val db: SQLiteDatabase = dbOpenHelper.writableDatabase
        try {
            val actualSqlStatements = mutableListOf<String>()
            c = db.rawQuery(String.format("SELECT sql FROM sqlite_master WHERE type='index' AND tbl_name='%s' ORDER BY name", tableName), null)
            while (c.moveToNext()) {
                actualSqlStatements.add(c.getString(0))
            }
            JSONTestHelper.assertSameJSONArray("Wrong indexes", JSONArray(expectedSqlStatements), JSONArray(actualSqlStatements))
        } catch (e: Exception) {
            Assert.fail("Failed with error:" + e.message)
        } finally {
            safeClose(c)
        }
    }

    /**
     * Get explain query plan of last query run and make sure the given index was used
     */
    protected fun checkExplainQueryPlan(soupName: String, index: Int, covering: Boolean, dbOperation: String) {
        val explainQueryPlan = store.getLastExplainQueryPlan()
        val soupTableName = getSoupTableName(soupName)
        val indexName = soupTableName + "_" + index + "_idx"
        val expectedDetailPrefix = String.format("%s %s USING %sINDEX %s", dbOperation, soupTableName, if (covering) "COVERING " else "", indexName)
        val detail = explainQueryPlan!!.getJSONArray(DBHelper.EXPLAIN_ROWS).getJSONObject(0).getString("detail")
        Assert.assertTrue("Query plan: $detail - not starting with $expectedDetailPrefix", detail.startsWith(expectedDetailPrefix))
    }

    /**
     * Close cursor if not null
     */
    protected fun safeClose(c: Cursor?) {
        c?.close()
    }

    /**
     * @return table name for soup
     */
    protected fun getSoupTableName(soupName: String): String {
        val db: SQLiteDatabase = dbOpenHelper.writableDatabase
        return DBHelper.getInstance(db).getSoupTableName(db, soupName)!!
    }

    /**
     * Registers a soup with the given name and index specs. Can be overridden if extra features are desired.
     */
    protected open fun registerSoup(store: SmartStore, soupName: String, indexSpecs: Array<IndexSpec>) {
        store.registerSoup(soupName, indexSpecs)
    }

    companion object {
        /**
         * @return _soupEntryId field value
         */
        @JvmStatic
        fun idOf(soupElt: JSONObject): Long {
            return soupElt.getLong(SmartStore.SOUP_ENTRY_ID)
        }
    }
}
