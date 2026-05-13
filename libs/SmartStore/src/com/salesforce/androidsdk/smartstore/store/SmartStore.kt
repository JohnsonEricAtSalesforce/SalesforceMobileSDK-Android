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
import android.database.Cursor
import android.text.TextUtils
import androidx.annotation.VisibleForTesting
import com.salesforce.androidsdk.analytics.EventBuilderHelper
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.smartstore.store.LongOperation.LongOperationType
import com.salesforce.androidsdk.smartstore.store.QuerySpec.QueryType
import com.salesforce.androidsdk.smartstore.util.SmartStoreLogger
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Smart store
 *
 * Provides a secure means for SalesforceMobileSDK Container-based applications to store objects in a persistent
 * and searchable manner. Similar in some ways to CouchDB, SmartStore stores documents as JSON values.
 * SmartStore is inspired by the Apple Newton OS Soup/Store model.
 * The main challenge here is how to effectively store documents with dynamic fields, and still allow indexing and searching.
 */
class SmartStore(
    // Backing database
    protected val dbOpenHelper: SQLiteOpenHelper
) {

    // Flag indicating if database was just opened
    private val dbJustOpened = AtomicBoolean(true)

    // FTS extension to use
    @VisibleForTesting
    internal var ftsExtension = FtsExtension.fts5

    // background executor
    private val threadPool = Executors.newFixedThreadPool(1)

    /**
     * Return db
     */
    fun getDatabase(): SQLiteDatabase {
        val db = this.dbOpenHelper.writableDatabase
        if (dbJustOpened.compareAndSet(true, false)) {
            resumeLongOperations()
        }
        return db
    }

    /**
     * If turned on, explain query plan is run before executing a query and stored in lastExplainQueryPlan
     * and also get logged
     */
    fun setCaptureExplainQueryPlan(captureExplainQueryPlan: Boolean) {
        DBHelper.getInstance(getDatabase()).setCaptureExplainQueryPlan(captureExplainQueryPlan)
    }

    /**
     * @return explain query plan for last query run (if captureExplainQueryPlan is true)
     */
    fun getLastExplainQueryPlan(): JSONObject? {
        return DBHelper.getInstance(getDatabase()).getLastExplainQueryPlan()
    }

    /**
     * Get database size
     */
    fun getDatabaseSize(): Int {
        // With WAL enabled we must force a WAL checkpoint if we want the actual DB file size.
        queryPragma("wal_checkpoint(FULL)")
        return File(getDatabase().path).length().toInt() // XXX That cast will be trouble if the file is more than 2GB
    }

    /**
     * Start transaction
     * NB: to avoid deadlock, caller should have synchronized(store.getDatabase()) around the whole transaction
     */
    fun beginTransaction() {
        getDatabase().beginTransaction()
    }

    /**
     * End transaction (commit or rollback)
     */
    fun endTransaction() {
        getDatabase().endTransaction()
    }

    /**
     * Mark transaction as successful (next call to endTransaction will be a commit)
     */
    fun setTransactionSuccessful() {
        getDatabase().setTransactionSuccessful()
    }

    /**
     * Register a soup.
     *
     * Create table for soupName with a column for the soup itself and columns for paths specified in indexSpecs
     * Create indexes on the new table to make lookup faster
     * Create rows in soup index map table for indexSpecs
     */
    fun registerSoup(soupName: String, indexSpecs: Array<IndexSpec>) {
        val db = getDatabase()
        synchronized(db) {
            if (soupName == null) throw SmartStoreException("Bogus soup name:$soupName")
            if (indexSpecs.isEmpty()) {
                throw SmartStoreException("No indexSpecs specified for soup: $soupName")
            }
            if (hasSoup(soupName)) return // soup already exist - do nothing

            // First get a table name
            var soupTableName: String? = null
            val soupMapValues = ContentValues()
            soupMapValues.put(SOUP_NAME_COL, soupName)

            try {
                db.beginTransaction()
                val soupId = DBHelper.getInstance(db).insert(db, SOUP_ATTRS_TABLE, soupMapValues)
                soupTableName = getSoupTableName(soupId)

                // Do the rest - create table / indexes
                registerSoupUsingTableName(soupName, indexSpecs, soupTableName)

                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
            if (SalesforceSDKManager.getInstance().isTestRun) {
                logRegisterSoupEvent(indexSpecs)
            } else {
                threadPool.execute {
                    logRegisterSoupEvent(indexSpecs)
                }
            }
        }
    }

    /**
     * Log the soup event.
     */
    private fun logRegisterSoupEvent(indexSpecs: Array<IndexSpec>) {
        val features = JSONArray()
        if (IndexSpec.hasJSON1(indexSpecs)) {
            features.put("JSON1")
        }
        if (IndexSpec.hasFTS(indexSpecs)) {
            features.put("FTS")
        }
        val attributes = JSONObject()
        try {
            attributes.put("features", features)
        } catch (e: JSONException) {
            SmartStoreLogger.e(TAG, "Exception thrown while building page object", e)
        }
        EventBuilderHelper.createAndStoreEventSync("registerSoup", null, TAG, attributes)
    }

    /**
     * Helper method for registerSoup
     */
    internal fun registerSoupUsingTableName(soupName: String, indexSpecs: Array<IndexSpec>, soupTableName: String) {
        // Prepare SQL for creating soup table and its indices
        val createTableStmt = StringBuilder() // to create new soup table
        val createFtsStmt = StringBuilder() // to create fts table
        val createIndexStmts = ArrayList<String>() // to create indices on new soup table
        val soupIndexMapInserts = ArrayList<ContentValues>() // to be inserted in soup index map table
        val indexSpecsToCache = Array(indexSpecs.size) { indexSpecs[it] }
        val columnsForFts = ArrayList<String>()

        createTableStmt.append("CREATE TABLE ").append(soupTableName).append(" (")
            .append(ID_COL).append(" INTEGER PRIMARY KEY AUTOINCREMENT")

        createTableStmt.append(", ").append(SOUP_COL).append(" TEXT")

        createTableStmt.append(", ").append(CREATED_COL).append(" INTEGER")
            .append(", ").append(LAST_MODIFIED_COL).append(" INTEGER")

        val createIndexFormat = "CREATE INDEX %s_%s_idx on %s ( %s )"

        for (col in arrayOf(CREATED_COL, LAST_MODIFIED_COL)) {
            createIndexStmts.add(String.format(createIndexFormat, soupTableName, col, soupTableName, col))
        }

        var i = 0
        for (indexSpec in indexSpecs) {
            // Column name or expression the db index is on
            var columnName = "${soupTableName}_$i"
            if (TypeGroup.value_indexed_with_json_extract.isMember(indexSpec.type)) {
                columnName = "json_extract($SOUP_COL, '$.${indexSpec.path}')"
            }

            // for create table
            if (TypeGroup.value_extracted_to_column.isMember(indexSpec.type)) {
                val columnType = indexSpec.type.columnType
                createTableStmt.append(", ").append(columnName).append(" ").append(columnType)
            }

            // for fts
            if (indexSpec.type == Type.full_text) {
                columnsForFts.add(columnName)
            }

            // for insert
            val values = ContentValues()
            values.put(SOUP_NAME_COL, soupName)
            values.put(PATH_COL, indexSpec.path)
            values.put(COLUMN_NAME_COL, columnName)
            values.put(COLUMN_TYPE_COL, indexSpec.type.toString())
            soupIndexMapInserts.add(values)

            // for create index
            createIndexStmts.add(String.format(createIndexFormat, soupTableName, "" + i, soupTableName, columnName))

            // for the cache
            indexSpecsToCache[i] = IndexSpec(indexSpec.path, indexSpec.type, columnName)

            i++
        }
        createTableStmt.append(")")

        // fts
        if (columnsForFts.size > 0) {
            createFtsStmt.append(String.format("CREATE VIRTUAL TABLE %s%s USING %s(%s)", soupTableName, FTS_SUFFIX, ftsExtension, TextUtils.join(",", columnsForFts)))
        }

        // Run SQL for creating soup table and its indices
        val db = getDatabase()
        db.execSQL(createTableStmt.toString())

        if (columnsForFts.size > 0) {
            db.execSQL(createFtsStmt.toString())
        }

        for (createIndexStmt in createIndexStmts) {
            db.execSQL(createIndexStmt)
        }

        try {
            db.beginTransaction()
            for (values in soupIndexMapInserts) {
                DBHelper.getInstance(db).insert(db, SOUP_INDEX_MAP_TABLE, values)
            }

            db.setTransactionSuccessful()

            // Add to soupNameToTableNamesMap
            DBHelper.getInstance(db).cacheTableName(soupName, soupTableName)

            // Add to soupNameToExistMap
            DBHelper.getInstance(db).cacheHasSoup(soupName, true)

            // Add to soupNameToIndexSpecsMap
            DBHelper.getInstance(db).cacheIndexSpecs(soupName, indexSpecsToCache)
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Finish long operations that were interrupted
     */
    fun resumeLongOperations() {
        val db = getDatabase()
        synchronized(db) {
            for (longOperation in getLongOperations()) {
                try {
                    longOperation.run()
                } catch (e: Exception) {
                    SmartStoreLogger.e(TAG, "Unexpected error", e)
                }
            }
        }
    }

    /**
     * @return unfinished long operations
     */
    fun getLongOperations(): Array<LongOperation> {
        val db = getDatabase()
        val longOperations = ArrayList<LongOperation>()
        synchronized(db) {
            var cursor: Cursor? = null
            try {
                cursor = DBHelper.getInstance(db).query(
                    db,
                    LONG_OPERATIONS_STATUS_TABLE,
                    arrayOf(ID_COL, TYPE_COL, DETAILS_COL, STATUS_COL),
                    null,
                    null,
                    null
                )
                if (cursor.moveToFirst()) {
                    do {
                        try {
                            val rowId = cursor.getLong(0)
                            val operationType = LongOperationType.valueOf(cursor.getString(1))
                            val details = JSONObject(cursor.getString(2))
                            val statusStr = cursor.getString(3)

                            longOperations.add(operationType.getOperation(this, rowId, details, statusStr))
                        } catch (e: Exception) {
                            SmartStoreLogger.e(TAG, "Unexpected error", e)
                        }
                    } while (cursor.moveToNext())
                }
            } finally {
                safeClose(cursor)
            }
        }
        return longOperations.toTypedArray()
    }

    /**
     * Alter soup using only soup name without extra soup features.
     */
    @Throws(JSONException::class)
    fun alterSoup(soupName: String, indexSpecs: Array<IndexSpec>, reIndexData: Boolean) {
        val operation = AlterSoupLongOperation(this, soupName, indexSpecs, reIndexData)
        operation.run()
    }

    /**
     * Re-index all soup elements for passed indexPaths
     * NB: only indexPath that have IndexSpec on them will be indexed
     */
    fun reIndexSoup(soupName: String, indexPaths: Array<String>, handleTx: Boolean) {
        val db = getDatabase()
        synchronized(db) {
            val soupTableName = DBHelper.getInstance(db).getSoupTableName(db, soupName)
                ?: throw SmartStoreException("Soup: $soupName does not exist")

            // Getting index specs from indexPaths skipping json1 index specs
            val mapAllSpecs = IndexSpec.mapForIndexSpecs(getSoupIndexSpecs(soupName))
            val indexSpecsList = ArrayList<IndexSpec>()
            for (indexPath in indexPaths) {
                if (mapAllSpecs.containsKey(indexPath)) {
                    val indexSpec = mapAllSpecs[indexPath]!!
                    if (TypeGroup.value_extracted_to_column.isMember(indexSpec.type)) {
                        indexSpecsList.add(indexSpec)
                    }
                } else {
                    SmartStoreLogger.w(TAG, "Can not re-index $indexPath - it does not have an index")
                }
            }
            val indexSpecs = indexSpecsList.toTypedArray()
            if (indexSpecs.isEmpty()) {
                // Nothing to do
                return
            }

            val hasFts = IndexSpec.hasFTS(indexSpecs)

            if (handleTx) {
                db.beginTransaction()
            }
            var cursor: Cursor? = null
            try {
                val projection = arrayOf(ID_COL, SOUP_COL)
                cursor = DBHelper.getInstance(db).query(db, soupTableName, projection, null, null, null)
                if (cursor.moveToFirst()) {
                    do {
                        val soupEntryId = cursor.getString(0)
                        try {
                            val soupRaw = cursor.getString(1)
                            val soupElt = JSONObject(soupRaw)
                            val contentValues = ContentValues()
                            projectIndexedPaths(soupElt, contentValues, indexSpecs, TypeGroup.value_extracted_to_column)
                            DBHelper.getInstance(db).update(db, soupTableName, contentValues, ID_PREDICATE, soupEntryId)

                            // Fts
                            if (hasFts) {
                                val soupTableNameFts = soupTableName + FTS_SUFFIX
                                val contentValuesFts = ContentValues()
                                projectIndexedPaths(soupElt, contentValuesFts, indexSpecs, TypeGroup.value_extracted_to_fts_column)
                                DBHelper.getInstance(db).update(db, soupTableNameFts, contentValuesFts, ROWID_PREDICATE, soupEntryId)
                            }
                        } catch (e: JSONException) {
                            SmartStoreLogger.w(TAG, "Could not parse soup element $soupEntryId", e)
                            // Should not have happen - just keep going
                        }
                    } while (cursor.moveToNext())
                }
            } finally {
                if (handleTx) {
                    db.setTransactionSuccessful()
                    db.endTransaction()
                }
                safeClose(cursor)
            }
        }
    }

    /**
     * Return indexSpecs of soup
     */
    fun getSoupIndexSpecs(soupName: String): Array<IndexSpec> {
        val db = getDatabase()
        synchronized(db) {
            val soupTableName = DBHelper.getInstance(db).getSoupTableName(db, soupName)
                ?: throw SmartStoreException("Soup: $soupName does not exist")
            return DBHelper.getInstance(db).getIndexSpecs(db, soupName)
        }
    }

    /**
     * Return true if the given path is indexed on the given soup
     */
    fun hasIndexForPath(soupName: String, path: String): Boolean {
        val db = getDatabase()
        synchronized(db) {
            return DBHelper.getInstance(db).hasIndexForPath(db, soupName, path)
        }
    }

    /**
     * Clear all rows from a soup
     */
    fun clearSoup(soupName: String) {
        val db = getDatabase()
        synchronized(db) {
            val soupTableName = DBHelper.getInstance(db).getSoupTableName(db, soupName)
                ?: throw SmartStoreException("Soup: $soupName does not exist")
            db.beginTransaction()
            try {
                DBHelper.getInstance(db).delete(db, soupTableName, null)
                if (hasFTS(soupName)) {
                    DBHelper.getInstance(db).delete(db, soupTableName + FTS_SUFFIX, null)
                }
            } finally {
                db.setTransactionSuccessful()
                db.endTransaction()
            }
        }
    }

    /**
     * Check if soup exists
     *
     * @return true if soup exists, false otherwise
     */
    fun hasSoup(soupName: String): Boolean {
        val db = getDatabase()
        synchronized(db) {
            return DBHelper.getInstance(db).hasSoup(db, soupName)
        }
    }

    /**
     * Destroy a soup
     *
     * Drop table for soupName
     * Cleanup entries in soup index map table
     */
    fun dropSoup(soupName: String) {
        val db = getDatabase()
        synchronized(db) {
            val soupTableName = DBHelper.getInstance(db).getSoupTableName(db, soupName)
            if (soupTableName != null) {
                db.execSQL("DROP TABLE IF EXISTS $soupTableName")
                if (hasFTS(soupName)) {
                    db.execSQL("DROP TABLE IF EXISTS $soupTableName$FTS_SUFFIX")
                }

                try {
                    db.beginTransaction()
                    DBHelper.getInstance(db).delete(db, SOUP_ATTRS_TABLE, SOUP_NAME_PREDICATE, soupName)
                    DBHelper.getInstance(db).delete(db, SOUP_INDEX_MAP_TABLE, SOUP_NAME_PREDICATE, soupName)
                    db.setTransactionSuccessful()

                    // Remove from cache
                    DBHelper.getInstance(db).removeFromCache(soupName)
                } finally {
                    db.endTransaction()
                }
            }
        }
    }

    /**
     * Destroy all the soups in the smartstore
     */
    fun dropAllSoups() {
        val db = getDatabase()
        synchronized(db) {
            val soupNames = getAllSoupNames()
            for (soupName in soupNames) {
                dropSoup(soupName)
            }
        }
    }

    /**
     * @return all soup names in the smartstore
     */
    fun getAllSoupNames(): List<String> {
        val db = getDatabase()
        synchronized(db) {
            val soupNames = ArrayList<String>()
            var cursor: Cursor? = null
            try {
                cursor = DBHelper.getInstance(db).query(db, SOUP_ATTRS_TABLE, arrayOf(SOUP_NAME_COL), SOUP_NAME_COL, null, null)
                if (cursor.moveToFirst()) {
                    do {
                        soupNames.add(cursor.getString(0))
                    } while (cursor.moveToNext())
                }
            } finally {
                safeClose(cursor)
            }
            return soupNames
        }
    }

    /**
     * Run a query given by its query spec
     * Returns results from selected page
     */
    @Throws(JSONException::class)
    fun query(querySpec: QuerySpec, pageIndex: Int): JSONArray {
        return queryWithArgs(querySpec, pageIndex)
    }

    /**
     * Run a query given by its query spec with optional "where args" (i.e. bind args)
     * Provided bind args will be substituted to the ? found in the query
     * NB: Bind args are only supported for smart queries
     * Returns results from selected page
     */
    @Throws(JSONException::class)
    fun queryWithArgs(querySpec: QuerySpec, pageIndex: Int, vararg whereArgs: String): JSONArray {
        if (whereArgs.isNotEmpty() && querySpec.queryType != QueryType.smart) {
            throw SmartStoreException("whereArgs can only be provided for smart queries")
        }

        val resultAsArray = JSONArray()
        runQuery(resultAsArray, null, querySpec, pageIndex, *whereArgs)
        return resultAsArray
    }

    /**
     * Run a query given by its query Spec
     * Returns results from selected page without deserializing any JSON
     */
    fun queryAsString(resultBuilder: StringBuilder, querySpec: QuerySpec, pageIndex: Int) {
        try {
            runQuery(null, resultBuilder, querySpec, pageIndex)
        } catch (e: JSONException) {
            // shouldn't happen since we call runQuery with a string builder
            throw SmartStoreException("Unexpected json exception", e)
        }
    }

    @Throws(JSONException::class)
    private fun runQuery(
        resultAsArray: JSONArray?,
        resultAsStringBuilder: StringBuilder?,
        querySpec: QuerySpec,
        pageIndex: Int,
        vararg whereArgs: String
    ) {
        val computeResultAsString = resultAsStringBuilder != null

        val db = getDatabase()
        synchronized(db) {
            val qt = querySpec.queryType
            val sql = convertSmartSql(querySpec.smartSql ?: "")

            // Page
            val offsetRows = querySpec.pageSize * pageIndex
            val numberRows = querySpec.pageSize
            val limit = "$offsetRows,$numberRows"
            var cursor: Cursor? = null
            try {
                cursor = DBHelper.getInstance(db).limitRawQuery(
                    db, sql, limit,
                    *(if (querySpec.getArgs() != null) querySpec.getArgs()!! else whereArgs)
                )

                if (computeResultAsString) {
                    resultAsStringBuilder!!.append("[")
                }

                var currentRow = 0
                if (cursor.moveToFirst()) {
                    do {
                        if (computeResultAsString && currentRow > 0) {
                            resultAsStringBuilder!!.append(", ")
                        }
                        currentRow++

                        // Smart queries
                        if (qt == QueryType.smart || querySpec.selectPaths != null) {
                            if (computeResultAsString) {
                                getDataFromRow(null, resultAsStringBuilder, cursor)
                            } else {
                                val rowArray = JSONArray()
                                getDataFromRow(rowArray, null, cursor)
                                resultAsArray!!.put(rowArray)
                            }
                        }
                        // Exact/like/range queries
                        else {
                            val rowAsString = cursor.getString(0)

                            if (computeResultAsString) {
                                resultAsStringBuilder!!.append(rowAsString)
                            } else {
                                resultAsArray!!.put(JSONObject(rowAsString))
                            }
                        }
                    } while (cursor.moveToNext())
                }
                if (computeResultAsString) {
                    resultAsStringBuilder!!.append("]")
                }

            } finally {
                safeClose(cursor)
            }
        }
    }

    @Throws(JSONException::class)
    private fun getDataFromRow(resultAsArray: JSONArray?, resultAsStringBuilder: StringBuilder?, cursor: Cursor) {
        val computeResultAsString = resultAsStringBuilder != null
        val columnCount = cursor.columnCount
        if (computeResultAsString) {
            resultAsStringBuilder!!.append("[")
        }
        for (i in 0 until columnCount) {
            if (computeResultAsString && i > 0) {
                resultAsStringBuilder!!.append(",")
            }
            val valueType = cursor.getType(i)
            val columnName = cursor.getColumnName(i)
            when {
                valueType == Cursor.FIELD_TYPE_NULL -> {
                    if (computeResultAsString) {
                        resultAsStringBuilder!!.append("null")
                    } else {
                        resultAsArray!!.put(null)
                    }
                }
                valueType == Cursor.FIELD_TYPE_STRING -> {
                    var raw = cursor.getString(i)
                    if (columnName == SOUP_COL || columnName.startsWith("$SOUP_COL:") /* :num is appended to column name when result set has more than one column with same name */) {
                        if (computeResultAsString) {
                            resultAsStringBuilder!!.append(raw)
                        } else {
                            resultAsArray!!.put(JSONObject(raw))
                        }
                        // Note: we could end up returning a string if you aliased the column
                    } else {
                        if (computeResultAsString) {
                            raw = escapeStringValue(raw)
                            resultAsStringBuilder!!.append("\"").append(raw).append("\"")
                        } else {
                            resultAsArray!!.put(raw)
                        }
                    }
                }
                valueType == Cursor.FIELD_TYPE_INTEGER -> {
                    if (computeResultAsString) {
                        resultAsStringBuilder!!.append(cursor.getLong(i))
                    } else {
                        resultAsArray!!.put(cursor.getLong(i))
                    }
                }
                valueType == Cursor.FIELD_TYPE_FLOAT -> {
                    if (computeResultAsString) {
                        resultAsStringBuilder!!.append(cursor.getDouble(i))
                    } else {
                        resultAsArray!!.put(cursor.getDouble(i))
                    }
                }
            }
        }
        if (computeResultAsString) {
            resultAsStringBuilder!!.append("]")
        }
    }

    private fun escapeStringValue(raw: String): String {
        val sb = StringBuilder()

        for (i in raw.indices) {
            val c = raw[i]
            when (c) {
                '\\', '"' -> {
                    sb.append('\\')
                    sb.append(c)
                }
                '/' -> {
                    sb.append('\\')
                    sb.append(c)
                }
                '\b' -> sb.append("\\b")
                '\t' -> sb.append("\\t")
                '\n' -> sb.append("\\n")
                '\u000C' -> sb.append("\\f")
                '\r' -> sb.append("\\r")
                else -> {
                    if (c < ' ') {
                        val t = "000" + Integer.toHexString(c.code)
                        sb.append("\\u" + t.substring(t.length - 4))
                    } else {
                        sb.append(c)
                    }
                }
            }
        }
        return sb.toString()
    }

    /**
     * @return count of results for a query
     */
    fun countQuery(querySpec: QuerySpec): Int {
        val db = getDatabase()
        synchronized(db) {
            val countSql = convertSmartSql(querySpec.countSmartSql ?: "")
            return DBHelper.getInstance(db).countRawCountQuery(db, countSql, *querySpec.getArgs()!!)
        }
    }

    fun convertSmartSql(smartSql: String): String {
        val db = getDatabase()
        synchronized(db) {
            return SmartSqlHelper.getInstance(db).convertSmartSql(db, smartSql)
        }
    }

    /**
     * Create (and commits)
     * Note: Passed soupElt is modified (last modified date and soup entry id fields)
     * @return soupElt created or null if creation failed
     */
    @Throws(JSONException::class)
    fun create(soupName: String, soupElt: JSONObject): JSONObject? {
        val db = getDatabase()
        synchronized(db) {
            return create(soupName, soupElt, true)
        }
    }

    /**
     * Create
     * Note: Passed soupElt is modified (last modified date and soup entry id fields)
     */
    @Throws(JSONException::class)
    fun create(soupName: String, soupElt: JSONObject, handleTx: Boolean): JSONObject? {
        val db = getDatabase()
        synchronized(db) {
            val soupTableName = DBHelper.getInstance(db).getSoupTableName(db, soupName)
                ?: throw SmartStoreException("Soup: $soupName does not exist")
            val indexSpecs = DBHelper.getInstance(db).getIndexSpecs(db, soupName)

            try {
                if (handleTx) {
                    db.beginTransaction()
                }
                val now = System.currentTimeMillis()
                val soupEntryId = DBHelper.getInstance(db).getNextId(db, soupTableName)

                // Adding fields to soup element
                soupElt.put(SOUP_ENTRY_ID, soupEntryId)
                soupElt.put(SOUP_LAST_MODIFIED_DATE, now)
                val contentValues = ContentValues()
                contentValues.put(ID_COL, soupEntryId)
                contentValues.put(CREATED_COL, now)
                contentValues.put(LAST_MODIFIED_COL, now)
                contentValues.put(SOUP_COL, soupElt.toString())
                projectIndexedPaths(soupElt, contentValues, indexSpecs, TypeGroup.value_extracted_to_column)

                // Inserting into database
                var success = DBHelper.getInstance(db).insert(db, soupTableName, contentValues) == soupEntryId

                // Fts
                if (success && hasFTS(soupName)) {
                    val soupTableNameFts = soupTableName + FTS_SUFFIX
                    val contentValuesFts = ContentValues()
                    contentValuesFts.put(ROWID_COL, soupEntryId)
                    projectIndexedPaths(soupElt, contentValuesFts, indexSpecs, TypeGroup.value_extracted_to_fts_column)
                    // InsertHelper not working against virtual fts table
                    db.insert(soupTableNameFts, null, contentValuesFts)
                }

                // Commit if successful
                return if (success) {
                    if (handleTx) {
                        db.setTransactionSuccessful()
                    }
                    soupElt
                } else {
                    null
                }
            } finally {
                if (handleTx) {
                    db.endTransaction()
                }
            }
        }
    }

    /**
     * @return true if soup has at least one full-text search index
     */
    private fun hasFTS(soupName: String): Boolean {
        val db = getDatabase()
        synchronized(db) {
            return DBHelper.getInstance(db).hasFTS(db, soupName)
        }
    }

    /**
     * Populate content values by projecting index specs that have a type in typeGroup
     */
    private fun projectIndexedPaths(soupElt: JSONObject, contentValues: ContentValues, indexSpecs: Array<IndexSpec>, typeGroup: TypeGroup) {
        for (indexSpec in indexSpecs) {
            if (typeGroup.isMember(indexSpec.type)) {
                projectIndexedPath(soupElt, contentValues, indexSpec)
            }
        }
    }

    private fun projectIndexedPath(soupElt: JSONObject, contentValues: ContentValues, indexSpec: IndexSpec) {
        val value = project(soupElt, indexSpec.path)

        contentValues.put(indexSpec.columnName, null as String?) // fall back
        if (value != null) {
            try {
                when (indexSpec.type) {
                    Type.integer -> contentValues.put(indexSpec.columnName, (value as Number).toLong())
                    Type.string, Type.full_text -> contentValues.put(indexSpec.columnName, value.toString())
                    Type.floating -> contentValues.put(indexSpec.columnName, (value as Number).toDouble())
                    else -> {}
                }
            } catch (e: Exception) {
                // Ignore (will use the null value)
                SmartStoreLogger.e(TAG, "Unexpected error", e)
            }
        }
    }

    /**
     * Retrieve
     * @return JSONArray of JSONObject's with the given soupEntryIds
     */
    @Throws(JSONException::class)
    fun retrieve(soupName: String, vararg soupEntryIds: Long): JSONArray {
        val db = getDatabase()
        synchronized(db) {
            val soupTableName = DBHelper.getInstance(db).getSoupTableName(db, soupName)
                ?: throw SmartStoreException("Soup: $soupName does not exist")

            val result = JSONArray()
            var cursor: Cursor? = null
            try {
                cursor = DBHelper.getInstance(db).query(
                    db, soupTableName, arrayOf(SOUP_COL), null, null,
                    getSoupEntryIdsPredicate(soupEntryIds.toTypedArray())
                )
                if (!cursor.moveToFirst()) {
                    return result
                }
                do {
                    val raw = cursor.getString(cursor.getColumnIndex(SOUP_COL))
                    result.put(JSONObject(raw))
                } while (cursor.moveToNext())
            } finally {
                safeClose(cursor)
            }
            return result
        }
    }

    /**
     * Update (and commits)
     * Note: Passed soupElt is modified (last modified date and soup entry id fields)
     * @return soupElt updated or null if update failed
     */
    @Throws(JSONException::class)
    fun update(soupName: String, soupElt: JSONObject, soupEntryId: Long): JSONObject? {
        val db = getDatabase()
        synchronized(db) {
            return update(soupName, soupElt, soupEntryId, true)
        }
    }

    /**
     * Update
     * Note: Passed soupElt is modified (last modified date and soup entry id fields)
     */
    @Throws(JSONException::class)
    fun update(soupName: String, soupElt: JSONObject, soupEntryId: Long, handleTx: Boolean): JSONObject? {
        val db = getDatabase()
        synchronized(db) {
            try {
                if (handleTx) {
                    db.beginTransaction()
                }

                val soupTableName = DBHelper.getInstance(db).getSoupTableName(db, soupName)
                    ?: throw SmartStoreException("Soup: $soupName does not exist")
                val indexSpecs = DBHelper.getInstance(db).getIndexSpecs(db, soupName)

                val now = System.currentTimeMillis()

                // In the case of an upsert with external id, _soupEntryId won't be in soupElt
                soupElt.put(SOUP_ENTRY_ID, soupEntryId)
                // Updating last modified field in soup element
                soupElt.put(SOUP_LAST_MODIFIED_DATE, now)

                // Preparing data for row
                val contentValues = ContentValues()
                contentValues.put(LAST_MODIFIED_COL, now)
                projectIndexedPaths(soupElt, contentValues, indexSpecs, TypeGroup.value_extracted_to_column)
                contentValues.put(SOUP_COL, soupElt.toString())

                // Updating database
                var success = DBHelper.getInstance(db).update(db, soupTableName, contentValues, ID_PREDICATE, soupEntryId.toString()) == 1

                // Fts
                if (success && hasFTS(soupName)) {
                    val soupTableNameFts = soupTableName + FTS_SUFFIX
                    val contentValuesFts = ContentValues()
                    projectIndexedPaths(soupElt, contentValuesFts, indexSpecs, TypeGroup.value_extracted_to_fts_column)
                    success = DBHelper.getInstance(db).update(db, soupTableNameFts, contentValuesFts, ROWID_PREDICATE, soupEntryId.toString()) == 1
                }

                return if (success) {
                    if (handleTx) {
                        db.setTransactionSuccessful()
                    }
                    soupElt
                } else {
                    null
                }
            } finally {
                if (handleTx) {
                    db.endTransaction()
                }
            }
        }
    }

    /**
     * Upsert (and commits)
     * @return soupElt upserted or null if upsert failed
     */
    @Throws(JSONException::class)
    fun upsert(soupName: String, soupElt: JSONObject, externalIdPath: String): JSONObject? {
        val db = getDatabase()
        synchronized(db) {
            return upsert(soupName, soupElt, externalIdPath, true)
        }
    }

    /**
     * Upsert (and commits) expecting _soupEntryId in soupElt for updates
     */
    @Throws(JSONException::class)
    fun upsert(soupName: String, soupElt: JSONObject): JSONObject? {
        val db = getDatabase()
        synchronized(db) {
            return upsert(soupName, soupElt, SOUP_ENTRY_ID)
        }
    }

    /**
     * Upsert
     */
    @Throws(JSONException::class)
    fun upsert(soupName: String, soupElt: JSONObject, externalIdPath: String, handleTx: Boolean): JSONObject? {
        val db = getDatabase()
        synchronized(db) {
            var entryId = -1L
            if (externalIdPath == SOUP_ENTRY_ID) {
                if (soupElt.has(SOUP_ENTRY_ID)) {
                    entryId = soupElt.getLong(SOUP_ENTRY_ID)
                }
            } else {
                val externalIdObj = project(soupElt, externalIdPath)
                if (externalIdObj != null) {
                    entryId = lookupSoupEntryId(soupName, externalIdPath, externalIdObj.toString())
                } else {
                    // Cannot have empty values for user-defined external ID upsert.
                    throw SmartStoreException(String.format("For upsert with external ID path '%s', value cannot be empty for any entries.", externalIdPath))
                }
            }

            // If we have an entryId, let's do an update, otherwise let's do a create
            return if (entryId != -1L) {
                update(soupName, soupElt, entryId, handleTx)
            } else {
                create(soupName, soupElt, handleTx)
            }
        }
    }

    /**
     * Look for a soup element where fieldPath's value is fieldValue
     * Return its soupEntryId
     * Return -1 if not found
     * Throw an exception if fieldName is not indexed
     * Throw an exception if more than one soup element are found
     */
    fun lookupSoupEntryId(soupName: String, fieldPath: String, fieldValue: String): Long {
        val db = getDatabase()
        synchronized(db) {
            val soupTableName = DBHelper.getInstance(db).getSoupTableName(db, soupName)
                ?: throw SmartStoreException("Soup: $soupName does not exist")
            val columnName = DBHelper.getInstance(db).getColumnNameForPath(db, soupName, fieldPath)

            var cursor: Cursor? = null
            try {
                cursor = db.query(soupTableName, arrayOf(ID_COL), "$columnName = ?", arrayOf(fieldValue), null, null, null)
                if (cursor.count > 1) {
                    throw SmartStoreException(String.format("There are more than one soup elements where %s is %s", fieldPath, fieldValue))
                }
                return if (cursor.moveToFirst()) {
                    cursor.getLong(0)
                } else {
                    -1 // not found
                }
            } finally {
                safeClose(cursor)
            }
        }
    }

    /**
     * Delete soup elements given by their ids (and commits)
     */
    fun delete(soupName: String, vararg soupEntryIds: Long) {
        val db = getDatabase()
        synchronized(db) {
            delete(soupName, soupEntryIds, true)
        }
    }

    /**
     * Delete soup elements given by their ids
     */
    fun delete(soupName: String, soupEntryIds: LongArray, handleTx: Boolean) {
        val db = getDatabase()
        synchronized(db) {
            val soupTableName = DBHelper.getInstance(db).getSoupTableName(db, soupName)
                ?: throw SmartStoreException("Soup: $soupName does not exist")
            if (handleTx) {
                db.beginTransaction()
            }
            try {
                DBHelper.getInstance(db).delete(db, soupTableName, getSoupEntryIdsPredicate(soupEntryIds.toTypedArray()))

                if (hasFTS(soupName)) {
                    DBHelper.getInstance(db).delete(db, soupTableName + FTS_SUFFIX, getRowIdsPredicate(soupEntryIds.toTypedArray()))
                }

                if (handleTx) {
                    db.setTransactionSuccessful()
                }
            } finally {
                if (handleTx) {
                    db.endTransaction()
                }
            }
        }
    }

    /**
     * Delete soup elements selected by querySpec (and commits)
     * @param querySpec Query returning entries to delete (if querySpec uses smartSQL, it must select soup entry ids)
     */
    fun deleteByQuery(soupName: String, querySpec: QuerySpec) {
        val db = getDatabase()
        synchronized(db) {
            deleteByQuery(soupName, querySpec, true)
        }
    }

    /**
     * Delete soup elements selected by querySpec
     */
    fun deleteByQuery(soupName: String, querySpec: QuerySpec, handleTx: Boolean) {
        val db = getDatabase()
        synchronized(db) {
            val soupTableName = DBHelper.getInstance(db).getSoupTableName(db, soupName)
                ?: throw SmartStoreException("Soup: $soupName does not exist")
            if (handleTx) {
                db.beginTransaction()
            }
            try {
                val subQuerySql = String.format("SELECT %s FROM (%s) LIMIT %d", ID_COL, convertSmartSql(querySpec.idsSmartSql ?: ""), querySpec.pageSize)
                val args = querySpec.getArgs()

                DBHelper.getInstance(db).delete(db, soupTableName, buildInStatement(ID_COL, subQuerySql), *args!!)

                if (hasFTS(soupName)) {
                    DBHelper.getInstance(db).delete(db, soupTableName + FTS_SUFFIX, buildInStatement(ROWID_COL, subQuerySql), *args)
                }

                if (handleTx) {
                    db.setTransactionSuccessful()
                }
            } finally {
                if (handleTx) {
                    db.endTransaction()
                }
            }
        }
    }

    /**
     * @return predicate to match soup entries by id
     */
    private fun getSoupEntryIdsPredicate(soupEntryIds: Array<Long>): String {
        return buildInStatement(ID_COL, TextUtils.join(",", soupEntryIds))
    }

    /**
     * @return predicate to match entries by rowid
     */
    private fun getRowIdsPredicate(rowids: Array<Long>): String {
        return buildInStatement(ROWID_COL, TextUtils.join(",", rowids))
    }

    /**
     * @return in statement
     */
    private fun buildInStatement(col: String, inPredicate: String): String {
        return String.format("%s IN (%s)", col, inPredicate)
    }


    private fun safeClose(cursor: Cursor?) {
        cursor?.close()
    }

    /**
     * Get SQLCipher runtime settings
     *
     * @return list of SQLCipher runtime settings
     */
    fun getRuntimeSettings(): List<String> {
        return queryPragma("cipher_settings")
    }

    /**
     * Get SQLCipher compile options
     *
     * @return list of SQLCipher compile options
     */
    fun getCompileOptions(): List<String> {
        return queryPragma("compile_options")
    }

    /**
     * Get SQLCipher version
     *
     * @return SQLCipher version
     */
    fun getSQLCipherVersion(): String {
        return TextUtils.join(" ", queryPragma("cipher_version"))
    }

    /**
     * Get SQLCipher provider version
     *
     * @return SQLCipher provider version
     */
    fun getCipherProviderVersion(): String {
        return TextUtils.join(" ", queryPragma("cipher_provider_version"))
    }

    /**
     * Get SQLCipher FIPS status
     *
     * @return true if using a FIPS enabled SQLCipher edition
     */
    fun getCipherFIPSStatus(): Boolean {
        return TextUtils.join(" ", queryPragma("cipher_fips_status")) == "1"
    }

    private fun queryPragma(pragma: String): List<String> {
        val db = getDatabase()
        val results = ArrayList<String>()
        var c: Cursor? = null
        try {
            c = db.rawQuery("PRAGMA $pragma", null)
            while (c.moveToNext()) {
                results.add(c.getString(0))
            }
        } finally {
            safeClose(c)
        }
        return results
    }

    /**
     * Enum for column type
     */
    enum class Type(val columnType: String?) {
        string("TEXT"),
        integer("INTEGER"),
        floating("REAL"),
        full_text("TEXT"),
        json1(null)
    }

    /**
     * Enum for type groups
     */
    enum class TypeGroup {
        value_extracted_to_column {
            override fun isMember(type: Type): Boolean {
                return type == Type.string || type == Type.integer || type == Type.floating || type == Type.full_text
            }
        },
        value_extracted_to_fts_column {
            override fun isMember(type: Type): Boolean {
                return type == Type.full_text
            }
        },
        value_indexed_with_json_extract {
            override fun isMember(type: Type): Boolean {
                return type == Type.json1
            }
        };

        abstract fun isMember(type: Type): Boolean
    }

    /**
     * Enum for fts extensions
     */
    enum class FtsExtension {
        fts4,
        fts5
    }

    /**
     * Exception thrown by smart store
     */
    open class SmartStoreException : RuntimeException {
        constructor(message: String) : super(message)
        constructor(message: String, t: Throwable) : super(message, t)

        companion object {
            private const val serialVersionUID = -6369452803270075464L
        }
    }

    companion object {
        private const val TAG = "SmartStore"

        // Table to keep track of soup names and attributes.
        const val SOUP_ATTRS_TABLE = "soup_attrs"

        // Fts table suffix
        const val FTS_SUFFIX = "_fts"

        // Table to keep track of soup's index specs
        const val SOUP_INDEX_MAP_TABLE = "soup_index_map"

        // Table to keep track of status of long operations in flight
        internal const val LONG_OPERATIONS_STATUS_TABLE = "long_operations_status"

        // Columns of the soup index map table
        const val SOUP_NAME_COL = "soupName"
        const val PATH_COL = "path"
        const val COLUMN_NAME_COL = "columnName"
        const val COLUMN_TYPE_COL = "columnType"

        // Columns of a soup table
        const val ID_COL = "id"
        const val CREATED_COL = "created"
        const val LAST_MODIFIED_COL = "lastModified"
        const val SOUP_COL = "soup"

        // Column of a fts soup table
        const val ROWID_COL = "rowid"

        // Columns of long operations status table
        const val TYPE_COL = "type"
        const val DETAILS_COL = "details"
        const val STATUS_COL = "status"

        // JSON fields added to soup element on insert/update
        const val SOUP_ENTRY_ID = "_soupEntryId"
        const val SOUP_LAST_MODIFIED_DATE = "_soupLastModifiedDate"
        const val SOUP_CREATED_DATE = "_soupCreatedDate"

        // Predicates
        const val SOUP_NAME_PREDICATE = "$SOUP_NAME_COL = ?"
        const val ID_PREDICATE = "$ID_COL = ?"
        const val ROWID_PREDICATE = "$ROWID_COL =?"

        // Needed when using commercial or enterprise editions of SQLCipher
        var LICENSE_KEY: String? = null

        /**
         * Set license key for SQLCipher
         * Needed when using commercial or enterprise editions of SQLCipher
         * Should be called before using SmartStore
         * @param licenseKey The license key string provided by Zetetic
         */
        @JvmStatic
        fun setLicenseKey(licenseKey: String?) {
            LICENSE_KEY = licenseKey
        }

        /**
         * Changes the encryption key on the smartstore.
         */
        @JvmStatic
        @Synchronized
        fun changeKey(db: SQLiteDatabase, oldKey: String, newKey: String) {
            synchronized(db) {
                if (!TextUtils.isEmpty(newKey)) {
                    DBOpenHelper.changeKey(db, oldKey, newKey)
                }
            }
        }

        /**
         * Create soup index map table to keep track of soups' index specs
         * Create soup name map table to keep track of soup name to table name mappings
         * Called when the database is first created
         */
        @JvmStatic
        fun createMetaTables(db: SQLiteDatabase) {
            synchronized(db) {
                // Create soup_index_map table
                var sb = StringBuilder()
                sb.append("CREATE TABLE ").append(SOUP_INDEX_MAP_TABLE).append(" (")
                    .append(SOUP_NAME_COL).append(" TEXT")
                    .append(",").append(PATH_COL).append(" TEXT")
                    .append(",").append(COLUMN_NAME_COL).append(" TEXT")
                    .append(",").append(COLUMN_TYPE_COL).append(" TEXT")
                    .append(")")
                db.execSQL(sb.toString())
                // Add index on soup_name column
                db.execSQL(String.format("CREATE INDEX %s on %s ( %s )", SOUP_INDEX_MAP_TABLE + "_0", SOUP_INDEX_MAP_TABLE, SOUP_NAME_COL))

                // Create soup_names table
                // The table name for the soup will simply be table_<soupId>
                sb = StringBuilder()
                sb.append("CREATE TABLE ").append(SOUP_ATTRS_TABLE).append(" (")
                    .append(ID_COL).append(" INTEGER PRIMARY KEY AUTOINCREMENT")
                    .append(",").append(SOUP_NAME_COL).append(" TEXT")

                sb.append(")")
                db.execSQL(sb.toString())
                // Add index on soup_name column
                db.execSQL(String.format("CREATE INDEX %s on %s ( %s )", SOUP_ATTRS_TABLE + "_0", SOUP_ATTRS_TABLE, SOUP_NAME_COL))

                // Create alter_soup_status table
                createLongOperationsStatusTable(db)
            }
        }

        /**
         * Create long_operations_status table
         */
        @JvmStatic
        fun createLongOperationsStatusTable(db: SQLiteDatabase) {
            synchronized(db) {
                val sb = StringBuilder()
                sb.append("CREATE TABLE IF NOT EXISTS ").append(LONG_OPERATIONS_STATUS_TABLE).append(" (")
                    .append(ID_COL).append(" INTEGER PRIMARY KEY AUTOINCREMENT")
                    .append(",").append(TYPE_COL).append(" TEXT")
                    .append(",").append(DETAILS_COL).append(" TEXT")
                    .append(",").append(STATUS_COL).append(" TEXT")
                    .append(", ").append(CREATED_COL).append(" INTEGER")
                    .append(", ").append(LAST_MODIFIED_COL).append(" INTEGER")
                    .append(")")
                db.execSQL(sb.toString())
            }
        }

        @JvmStatic
        fun getSoupTableName(soupId: Long): String {
            return "TABLE_$soupId"
        }

        /**
         * @param soup
         * @param path
         * @return object at path in soup
         *
         * Examples (in pseudo code):
         *
         * json = {"a": {"b": [{"c":"xx"}, {"c":"xy"}, {"d": [{"e":1}, {"e":2}]}, {"d": [{"e":3}, {"e":4}]}] }}
         * projectIntoJson(jsonObj, "a") = {"b": [{"c":"xx"}, {"c":"xy"}, {"d": [{"e":1}, {"e":2}]}, {"d": [{"e":3}, {"e":4}]} ]}
         * projectIntoJson(json, "a.b") = [{c:"xx"}, {c:"xy"}, {"d": [{"e":1}, {"e":2}]}, {"d": [{"e":3}, {"e":4}]}]
         * projectIntoJson(json, "a.b.c") = ["xx", "xy"]                                     // new in 4.1
         * projectIntoJson(json, "a.b.d") = [[{"e":1}, {"e":2}], [{"e":3}, {"e":4}]]         // new in 4.1
         * projectIntoJson(json, "a.b.d.e") = [[1, 2], [3, 4]]                               // new in 4.1
         */
        @JvmStatic
        fun project(soup: JSONObject?, path: String): Any? {
            val result = projectReturningNULLObject(soup, path)
            return if (result === JSONObject.NULL) null else result
        }

        /**
         * Same as project but returns JSONObject.NULL if node found but without value and null if node not found
         */
        @JvmStatic
        fun projectReturningNULLObject(soup: JSONObject?, path: String): Any? {
            if (soup == null) {
                return null
            }
            if (path.isEmpty()) {
                return soup
            }
            val pathElements = path.split("\\.".toRegex()).toTypedArray()
            return projectRecursive(soup, pathElements, 0)
        }

        private fun projectRecursive(jsonObj: Any?, pathElements: Array<String>, index: Int): Any? {
            var result: Any? = null
            if (index == pathElements.size) {
                return jsonObj
            }

            if (null != jsonObj) {
                val pathElement = pathElements[index]

                if (jsonObj is JSONObject) {
                    val dictVal = jsonObj.opt(pathElement)
                    result = projectRecursive(dictVal, pathElements, index + 1)
                } else if (jsonObj is JSONArray) {
                    result = JSONArray()
                    for (i in 0 until jsonObj.length()) {
                        val arrayElt = jsonObj.opt(i)
                        val resultPart = projectRecursive(arrayElt, pathElements, index)
                        if (resultPart != null) {
                            result.put(resultPart)
                        }
                    }
                    if (result.length() == 0) {
                        result = null
                    }
                }
            }

            return result
        }

        /**
         * Updates the given table with a new name and adds columns if any.
         *
         * @param db Database to update
         * @param oldName Old name of the table to be renamed, null if table should not be renamed.
         * @param newName New name of the table to be renamed, null if table should not be renamed.
         * @param columns Columns to add. Null if no new columns should be added.
         */
        @JvmStatic
        @Synchronized
        fun updateTableNameAndAddColumns(db: SQLiteDatabase, oldName: String?, newName: String?, columns: Array<String>?) {
            var sb = StringBuilder()
            if (columns != null && columns.isNotEmpty()) {
                for (column in columns) {
                    sb.append("ALTER TABLE ").append(oldName).append(" ADD COLUMN ").append(column).append(" INTEGER DEFAULT 0;")
                }
                db.execSQL(sb.toString())
            }
            if (oldName != null && newName != null) {
                sb = StringBuilder()
                sb.append("ALTER TABLE ").append(oldName).append(" RENAME TO ").append(newName).append(';')
                db.execSQL(sb.toString())
            }
        }
    }
}
