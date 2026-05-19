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

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.SQLException
import android.database.sqlite.SQLiteDoneException
import android.util.LruCache
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.smartstore.app.SmartStoreSDKManager
import com.salesforce.androidsdk.smartstore.store.SmartStore.SmartStoreException
import com.salesforce.androidsdk.smartstore.store.SmartStore.Type
import com.salesforce.androidsdk.smartstore.util.SmartStoreLogger
import net.zetetic.database.DatabaseUtils.InsertHelper
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteStatement
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * SmartStore Database Helper
 * Singleton class that provides helpful methods for accessing the database underneath the SmartStore
 * It also caches a number of of things to speed things up (e.g. soup table name, index specs, insert helpers etc)
 */
open class DBHelper private constructor() {

    // Cache of soup name to boolean indicating existence
    private val soupNameToExistMap = LruCache<String, Boolean>(CACHES_COUNT_LIMIT)

    // Cache of soup name to soup table names
    private val soupNameToTableNamesMap = LruCache<String, String>(CACHES_COUNT_LIMIT)

    // Cache of soup name to index specs
    private val soupNameToIndexSpecsMap = LruCache<String, Array<IndexSpec>>(CACHES_COUNT_LIMIT)

    // Cache of soup name to boolean indicating if soup uses FTS
    private val soupNameToHasFTS = LruCache<String, Boolean>(CACHES_COUNT_LIMIT)

    // Cache of table name to get-next-id compiled statements
    private val tableNameToNextIdStatementsMap = object : LruCache<String, SQLiteStatement>(CACHES_COUNT_LIMIT) {
        override fun entryRemoved(evicted: Boolean, key: String, oldValue: SQLiteStatement, newValue: SQLiteStatement?) {
            oldValue.close()
        }
    }

    // Cache of table name to insert helpers
    private val tableNameToInsertHelpersMap = object : LruCache<String, InsertHelper>(CACHES_COUNT_LIMIT) {
        override fun entryRemoved(evicted: Boolean, key: String, oldValue: InsertHelper, newValue: InsertHelper?) {
            oldValue.close()
        }
    }

    // Cache of raw count sql to compiled statements
    private val rawCountSqlToStatementsMap = object : LruCache<String, SQLiteStatement>(CACHES_COUNT_LIMIT) {
        override fun entryRemoved(evicted: Boolean, key: String, oldValue: SQLiteStatement, newValue: SQLiteStatement?) {
            oldValue.close()
        }
    }

    // Boolean to turn explain query plan capture on or off
    private var captureExplainQueryPlan = false

    // Last explain query plan
    private var lastExplainQueryPlan: JSONObject? = null

    /**
     * @param soupName
     * @param tableName
     */
    fun cacheTableName(soupName: String, tableName: String) {
        soupNameToTableNamesMap.put(soupName, tableName)
    }

    /**
     * @param soupName
     * @param hasSoup
     */
    fun cacheHasSoup(soupName: String, hasSoup: Boolean) {
        soupNameToExistMap.put(soupName, hasSoup)
    }

    /**
     * @param soupName
     * @return
     */
    fun getCachedTableName(soupName: String): String? {
        return soupNameToTableNamesMap.get(soupName)
    }

    /**
     * @param soupName
     * @param indexSpecs
     */
    fun cacheIndexSpecs(soupName: String, indexSpecs: Array<IndexSpec>) {
        soupNameToIndexSpecsMap.put(soupName, indexSpecs.clone())
        soupNameToHasFTS.put(soupName, IndexSpec.hasFTS(indexSpecs))
    }

    /**
     * @param soupName
     * @return
     */
    fun getCachedIndexSpecs(soupName: String): Array<IndexSpec>? {
        return soupNameToIndexSpecsMap.get(soupName)
    }

    /**
     * @param soupName
     * @return
     */
    fun getCachedHasFTS(soupName: String): Boolean? {
        return soupNameToHasFTS.get(soupName)
    }

    /**
     * @param soupName
     */
    fun removeFromCache(soupName: String) {
        val tableName = soupNameToTableNamesMap.get(soupName)
        if (tableName != null) {
            val ih = tableNameToInsertHelpersMap.remove(tableName)
            ih?.close()

            val prog = tableNameToNextIdStatementsMap.remove(tableName)
            prog?.close()

            cleanupRawCountSqlToStatementMaps(tableName)
        }
        soupNameToExistMap.remove(soupName)
        soupNameToTableNamesMap.remove(soupName)
        soupNameToIndexSpecsMap.remove(soupName)
        soupNameToHasFTS.remove(soupName)
    }

    private fun cleanupRawCountSqlToStatementMaps(tableName: String) {
        val countSqlToRemove = ArrayList<String>()
        for ((countSql, countProg) in rawCountSqlToStatementsMap.snapshot()) {
            if (countSql.contains(tableName)) {
                countProg?.close()
                countSqlToRemove.add(countSql)
            }
        }
        for (countSql in countSqlToRemove) {
            rawCountSqlToStatementsMap.remove(countSql)
        }
    }

    /**
     * Get next id for a table
     *
     * @param db
     * @param tableName
     * @return long
     */
    fun getNextId(db: SQLiteDatabase, tableName: String): Long {
        var prog = tableNameToNextIdStatementsMap.get(tableName)
        if (prog == null) {
            prog = db.compileStatement(SEQ_SELECT)
            prog.bindString(1, tableName)
            tableNameToNextIdStatementsMap.put(tableName, prog)
        }
        return try {
            prog.simpleQueryForLong() + 1
        } catch (e: SQLiteDoneException) {
            // first time, we don't find any row for the table in the sequence table
            1L
        }
    }

    /**
     * Get insert helper for a table
     * @param table
     * @return
     */
    fun getInsertHelper(db: SQLiteDatabase, table: String): InsertHelper {
        var insertHelper = tableNameToInsertHelpersMap.get(table)
        if (insertHelper == null) {
            insertHelper = InsertHelper(db, table)
            tableNameToInsertHelpersMap.put(table, insertHelper)
        }
        return insertHelper
    }

    /**
     * Does a count query
     * @param db
     * @param table
     * @param whereClause
     * @param whereArgs
     * @return
     */
    fun countQuery(db: SQLiteDatabase, table: String, whereClause: String?, vararg whereArgs: String?): Cursor {
        val selectionStr = if (whereClause == null) "" else " WHERE $whereClause"
        val sql = String.format(COUNT_SELECT, table, selectionStr)
        return db.rawQuery(sql, whereArgs as Array<String?>?)
    }

    /**
     * Does a limit for a raw query
     * @param db
     * @param sql
     * @param limit
     * @param whereArgs
     * @return
     */
    fun limitRawQuery(db: SQLiteDatabase, sql: String, limit: String, vararg whereArgs: String?): Cursor {
        val limitSql = String.format(LIMIT_SELECT, sql, limit)
        val args = whereArgs.filterNotNull().toTypedArray().ifEmpty { null }
        if (captureExplainQueryPlan) {
            runExplainQueryPlan(db, limitSql, args)
        }
        return db.rawQuery(limitSql, args)
    }

    private fun runExplainQueryPlan(db: SQLiteDatabase, sql: String, whereArgs: Array<String>?) {
        val lastExplain = JSONObject()
        var c: Cursor? = null
        try {
            lastExplain.put(EXPLAIN_SQL, sql)
            if (whereArgs != null && whereArgs.isNotEmpty()) lastExplain.put(EXPLAIN_ARGS, JSONArray(whereArgs.toList()))
            val rows = JSONArray()

            c = db.rawQuery("EXPLAIN QUERY PLAN $sql", whereArgs)
            while (c.moveToNext()) {
                val row = JSONObject()
                for (i in 0 until c.columnCount) {
                    row.put(c.getColumnName(i), c.getString(i))
                }
                rows.put(row)
            }
            lastExplain.put(EXPLAIN_ROWS, rows)
            SmartStoreLogger.d(EXPLAIN_TAG, lastExplain.toString(2))
        } catch (e: JSONException) {
            SmartStoreLogger.d(EXPLAIN_TAG, "Exception", e)
        } finally {
            safeClose(c)
        }
        lastExplainQueryPlan = lastExplain
    }

    /**
     * Does a count for a raw count query
     * @param db
     * @param countSql
     * @param whereArgs
     * @return
     */
    fun countRawCountQuery(db: SQLiteDatabase, countSql: String, vararg whereArgs: String?): Int {
        var prog = rawCountSqlToStatementsMap.get(countSql)
        if (prog == null) {
            prog = db.compileStatement(countSql)
            rawCountSqlToStatementsMap.put(countSql, prog)
        }
        val args = whereArgs.filterNotNull()
        for (i in args.indices) {
            prog.bindString(i + 1, args[i])
        }
        return try {
            val count = prog.simpleQueryForLong().toInt()
            prog.clearBindings()
            count
        } catch (e: SQLiteDoneException) {
            -1
        }
    }

    /**
     * Does a count for a raw query
     * @param db
     * @param sql
     * @param whereArgs
     * @return
     */
    fun countRawQuery(db: SQLiteDatabase, sql: String, vararg whereArgs: String?): Int {
        val countSql = String.format(COUNT_SELECT, "", "($sql)")
        return countRawCountQuery(db, countSql, *whereArgs)
    }

    /**
     * Runs a query
     * @param db
     * @param table
     * @param columns
     * @param orderBy
     * @param limit
     * @param whereClause
     * @param whereArgs
     * @return
     */
    fun query(db: SQLiteDatabase, table: String, columns: Array<String>, orderBy: String?, limit: String?, whereClause: String?, vararg whereArgs: String?): Cursor {
        val args = if (whereArgs.isEmpty()) null else whereArgs as Array<String?>?
        return db.query(table, columns, whereClause, args, null, null, orderBy, limit)
    }

    /**
     * Does an insert
     * @param db
     * @param table
     * @param contentValues
     * @return row id of inserted row
     */
    fun insert(db: SQLiteDatabase, table: String, contentValues: ContentValues): Long {
        val ih = getInsertHelper(db, table)
        val rowId = ih.insert(contentValues)
        if (rowId == -1L) {
            // In case of failure InsertHelper.insert swallows the SQLException and returns -1
            throw SQLException(String.format("Insert into %s failed", table))
        }
        return rowId
    }

    /**
     * Does an update
     * @param db
     * @param table
     * @param contentValues
     * @param whereClause
     * @param whereArgs
     * @return number of rows affected
     */
    fun update(db: SQLiteDatabase, table: String, contentValues: ContentValues, whereClause: String, vararg whereArgs: String): Int {
        return db.update(table, contentValues, whereClause, whereArgs as Array<String>)
    }

    /**
     * Does a delete (after first logging the delete statement)
     * @param db
     * @param table
     * @param whereClause
     * @param whereArgs
     */
    fun delete(db: SQLiteDatabase, table: String, whereClause: String?, vararg whereArgs: String?) {
        db.delete(table, whereClause, if (whereArgs == null) arrayOf() else whereArgs as Array<String?>)
    }

    /**
     * Resets all cached data and deletes the database for all users.
     *
     * @param ctx Context.
     */
    @Synchronized
    fun reset(ctx: Context) {
        clearMemoryCache()
        val accounts = SmartStoreSDKManager.getInstance().userAccountManager.authenticatedUsers
        if (accounts != null) {
            for (account in accounts) {
                reset(ctx, account)
            }
        }
    }

    /**
     * Resets all cached data and deletes the database for the specified user.
     *
     * @param ctx Context.
     * @param account User account.
     */
    @Synchronized
    fun reset(ctx: Context, account: UserAccount) {
        clearMemoryCache()
        DBOpenHelper.deleteDatabase(ctx, account)
    }

    /**
     * Resets all cached data from memory.
     */
    @Synchronized
    fun clearMemoryCache() {
        soupNameToExistMap.evictAll()
        soupNameToTableNamesMap.evictAll()
        soupNameToIndexSpecsMap.evictAll()
        tableNameToInsertHelpersMap.evictAll()
        tableNameToNextIdStatementsMap.evictAll()
        rawCountSqlToStatementsMap.evictAll()
    }

    /**
     * Return column name in soup table that holds the soup projection for path
     * @param db
     * @param soupName
     * @param path
     * @return
     */
    fun getColumnNameForPath(db: SQLiteDatabase, soupName: String, path: String): String {
        val indexSpecs = getIndexSpecs(db, soupName)
        for (indexSpec in indexSpecs) {
            if (indexSpec.path == path) {
                return indexSpec.columnName!!
            }
        }
        throw SmartStoreException(String.format("%s does not have an index on %s", soupName, path))
    }

    /**
     * Return true if the given path is indexed on the given soup
     * @param db
     * @param soupName
     * @param path
     * @return
     */
    fun hasIndexForPath(db: SQLiteDatabase, soupName: String, path: String): Boolean {
        val indexSpecs = getIndexSpecs(db, soupName)
        for (indexSpec in indexSpecs) {
            if (indexSpec.path == path) {
                return true
            }
        }
        return false
    }

    /**
     * Read index specs back from the soup index map table
     * @param db
     * @param soupName
     * @return
     */
    fun getIndexSpecs(db: SQLiteDatabase, soupName: String): Array<IndexSpec> {
        var indexSpecs = getCachedIndexSpecs(soupName)
        if (indexSpecs == null) {
            indexSpecs = getIndexSpecsFromDb(db, soupName)
            cacheIndexSpecs(soupName, indexSpecs)
        }
        return indexSpecs
    }

    protected open fun getIndexSpecsFromDb(db: SQLiteDatabase, soupName: String): Array<IndexSpec> {
        var cursor: Cursor? = null
        try {
            cursor = query(
                db, SmartStore.SOUP_INDEX_MAP_TABLE,
                arrayOf(SmartStore.PATH_COL, SmartStore.COLUMN_NAME_COL, SmartStore.COLUMN_TYPE_COL),
                null, null, SmartStore.SOUP_NAME_PREDICATE, soupName
            )

            if (!cursor.moveToFirst()) {
                throw SmartStoreException(String.format("%s does not have any indices", soupName))
            }
            val indexSpecs = ArrayList<IndexSpec>()
            do {
                @Suppress("DEPRECATION")
                val path = cursor.getString(cursor.getColumnIndex(SmartStore.PATH_COL))
                @Suppress("DEPRECATION")
                val columnName = cursor.getString(cursor.getColumnIndex(SmartStore.COLUMN_NAME_COL))
                @Suppress("DEPRECATION")
                val columnType = Type.valueOf(cursor.getString(cursor.getColumnIndex(SmartStore.COLUMN_TYPE_COL)))
                indexSpecs.add(IndexSpec(path, columnType, columnName))
            } while (cursor.moveToNext())
            return indexSpecs.toTypedArray()
        } finally {
            safeClose(cursor)
        }
    }

    /**
     * @param db
     * @param soupName
     * @return true if soup has full-text-search index
     */
    fun hasFTS(db: SQLiteDatabase, soupName: String): Boolean {
        getIndexSpecs(db, soupName) // will populate cache if needed
        return getCachedHasFTS(soupName) ?: false
    }

    /**
     * Return table name for a given soup or null if the soup doesn't exist
     * @param db
     * @param soupName
     * @return
     */
    fun getSoupTableName(db: SQLiteDatabase, soupName: String): String? {
        var soupTableName = getCachedTableName(soupName)
        if (soupTableName == null) {
            soupTableName = getSoupTableNameFromDb(db, soupName)
            if (soupTableName != null) {
                cacheTableName(soupName, soupTableName)
            }
            // Note: if you ask twice about a non-existing soup, we go to the database both times
            //       we could optimize for that scenario but it doesn't seem very important
        }
        return soupTableName
    }

    /**
     * Return true if given soup exists
     * @param db
     * @param soupName
     * @return
     */
    fun hasSoup(db: SQLiteDatabase, soupName: String): Boolean {
        val exist = soupNameToExistMap.get(soupName)
        if (exist != null) {
            return exist
        } else {
            val hasSoup = getSoupTableName(db, soupName) != null
            cacheHasSoup(soupName, hasSoup)
            return hasSoup
        }
    }

    /**
     * If turned on, explain query plan is run before executing a query and stored in lastExplainQueryPlan
     * and also get logged
     * @param captureExplainQueryPlan true to turn capture on and false to turn off
     */
    fun setCaptureExplainQueryPlan(captureExplainQueryPlan: Boolean) {
        this.captureExplainQueryPlan = captureExplainQueryPlan
    }

    /**
     * @return explain query plan for last query run (if captureExplainQueryPlan is true)
     */
    fun getLastExplainQueryPlan(): JSONObject? {
        return lastExplainQueryPlan
    }

    protected open fun getSoupTableNameFromDb(db: SQLiteDatabase, soupName: String): String? {
        var cursor: Cursor? = null
        try {
            cursor = query(db, SmartStore.SOUP_ATTRS_TABLE, arrayOf(SmartStore.ID_COL), null, null, SmartStore.SOUP_NAME_PREDICATE, soupName)
            if (!cursor.moveToFirst()) {
                return null
            }
            @Suppress("DEPRECATION")
            return SmartStore.getSoupTableName(cursor.getLong(cursor.getColumnIndex(SmartStore.ID_COL)))
        } finally {
            safeClose(cursor)
        }
    }

    /**
     * @param cursor
     */
    protected fun safeClose(cursor: Cursor?) {
        cursor?.close()
    }

    companion object {
        // Explain support
        const val EXPLAIN_SQL = "sql"
        const val EXPLAIN_ARGS = "args"
        const val EXPLAIN_ROWS = "rows"
        const val EXPLAIN_TAG = "EXPLAIN"

        private var INSTANCES: MutableMap<SQLiteDatabase, DBHelper>? = null

        // Some queries
        private const val COUNT_SELECT = "SELECT count(*) FROM %s %s"
        private const val SEQ_SELECT = "SELECT seq FROM SQLITE_SEQUENCE WHERE name = ?"
        private const val LIMIT_SELECT = "SELECT * FROM (%s) LIMIT %s"

        // Caches count limit
        private const val CACHES_COUNT_LIMIT = 1024

        /**
         * Returns the instance of this class associated with the database specified.
         *
         * @param db Database.
         * @return Instance of this class.
         */
        @JvmStatic
        @Synchronized
        fun getInstance(db: SQLiteDatabase): DBHelper {
            if (INSTANCES == null) {
                INSTANCES = HashMap()
            }
            var instance = INSTANCES!![db]
            if (instance == null) {
                instance = DBHelper()
                INSTANCES!![db] = instance
            }
            return instance
        }
    }
}
