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

import android.content.ContentValues
import android.text.TextUtils
import com.salesforce.androidsdk.smartstore.store.SmartStore.SmartStoreException
import com.salesforce.androidsdk.smartstore.util.SmartStoreLogger
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.json.JSONException
import org.json.JSONObject

/**
 * Class taking care of alter soup
 * Two entry points:
 * - AlterSoupLongOperation(...) + run() => when asked to alterSoup in SmartStore
 * - LongOperation.getOperation(...) + run() => when completing interrupted long operations when opening the database
 */
class AlterSoupLongOperation : LongOperation {

    // Soup being altered
    protected var soupName: String? = null

    // Backing table for soup being altered
    private var soupTableName: String? = null

    // Last step completed
    private var afterStep: AlterSoupStep? = null

    // New index specs
    private var newIndexSpecs: Array<IndexSpec>? = null

    // Old index specs
    private var oldIndexSpecs: Array<IndexSpec>? = null

    // True if soup elements should be brought to memory to be re-indexed
    private var reIndexData = false

    // Instance of smartstore
    private var store: SmartStore? = null

    // Underlying database
    private var db: SQLiteDatabase? = null

    // Row id for long_operations_status
    private var rowId: Long = 0

    /**
     * Default constructor when reading back from long operations status table
     * Should be followed by a call to: initFromDbRow
     */
    constructor()

    /**
     * Constructor
     *
     * @param store
     * @param soupName
     * @param newIndexSpecs
     * @param reIndexData
     * @throws JSONException
     */
    @Throws(JSONException::class)
    constructor(store: SmartStore, soupName: String, newIndexSpecs: Array<IndexSpec>, reIndexData: Boolean) {
        synchronized(SmartStore::class.java) {
            // Setting store field
            this.store = store

            // Setting db field
            this.db = store.database

            // Setting soupName field
            this.soupName = soupName

            // Get backing table for soup
            this.soupTableName = DBHelper.getInstance(db!!).getSoupTableName(db!!, soupName)
                ?: throw SmartStoreException("Soup: $soupName does not exist")

            // Setting newIndexSpecs field
            this.newIndexSpecs = newIndexSpecs

            // Setting reIndexData field
            this.reIndexData = reIndexData

            // Get old indexSpecs
            this.oldIndexSpecs = DBHelper.getInstance(db!!).getIndexSpecs(db!!, soupName)

            // Create row in alter status table - auto commit
            this.rowId = createLongOperationDbRow()

            // Last step completed
            this.afterStep = AlterSoupStep.STARTING
        }
    }

    override fun run() {
        run(AlterSoupStep.LAST)
    }

    /**
     * Used by test only
     * @param toStep
     */
    fun run(toStep: AlterSoupStep) {
        alterSoupInternal(toStep)
    }

    /**
     * @return last step completed
     */
    fun getLastStepCompleted(): AlterSoupStep? {
        return afterStep
    }

    @Throws(JSONException::class)
    override fun initFromDbRow(store: SmartStore, rowId: Long, details: JSONObject, statusStr: String) {
        this.store = store
        this.db = store.database
        this.rowId = rowId
        this.afterStep = AlterSoupStep.valueOf(statusStr)
        this.soupName = details.getString(SOUP_NAME)
        this.newIndexSpecs = IndexSpec.fromJSON(details.getJSONArray(NEW_INDEX_SPECS))
        this.oldIndexSpecs = IndexSpec.fromJSON(details.getJSONArray(OLD_INDEX_SPECS))
        this.reIndexData = details.getBoolean(RE_INDEX_DATA)
        this.soupTableName = details.getString(SOUP_TABLE_NAME)
    }

    /**
     * Helper method for alterSoup
     * @param toStep
     */
    private fun alterSoupInternal(toStep: AlterSoupStep) {
        when (afterStep) {
            AlterSoupStep.STARTING -> {
                renameOldSoupTable()
                if (toStep == AlterSoupStep.RENAME_OLD_SOUP_TABLE) return
                dropOldIndexes()
                if (toStep == AlterSoupStep.DROP_OLD_INDEXES) return
                registerSoupUsingTableName()
                if (toStep == AlterSoupStep.REGISTER_SOUP_USING_TABLE_NAME) return
                copyTable()
                if (toStep == AlterSoupStep.COPY_TABLE) return
                if (reIndexData) reIndexSoup()
                if (toStep == AlterSoupStep.RE_INDEX_SOUP) return
                dropOldTable()
                store!!.getLongOperations()
                if (toStep == AlterSoupStep.DROP_OLD_TABLE) return
            }
            AlterSoupStep.RENAME_OLD_SOUP_TABLE -> {
                dropOldIndexes()
                if (toStep == AlterSoupStep.DROP_OLD_INDEXES) return
                registerSoupUsingTableName()
                if (toStep == AlterSoupStep.REGISTER_SOUP_USING_TABLE_NAME) return
                copyTable()
                if (toStep == AlterSoupStep.COPY_TABLE) return
                if (reIndexData) reIndexSoup()
                if (toStep == AlterSoupStep.RE_INDEX_SOUP) return
                dropOldTable()
                store!!.getLongOperations()
                if (toStep == AlterSoupStep.DROP_OLD_TABLE) return
            }
            AlterSoupStep.DROP_OLD_INDEXES -> {
                registerSoupUsingTableName()
                if (toStep == AlterSoupStep.REGISTER_SOUP_USING_TABLE_NAME) return
                copyTable()
                if (toStep == AlterSoupStep.COPY_TABLE) return
                if (reIndexData) reIndexSoup()
                if (toStep == AlterSoupStep.RE_INDEX_SOUP) return
                dropOldTable()
                store!!.getLongOperations()
                if (toStep == AlterSoupStep.DROP_OLD_TABLE) return
            }
            AlterSoupStep.REGISTER_SOUP_USING_TABLE_NAME -> {
                copyTable()
                if (toStep == AlterSoupStep.COPY_TABLE) return
                if (reIndexData) reIndexSoup()
                if (toStep == AlterSoupStep.RE_INDEX_SOUP) return
                dropOldTable()
                store!!.getLongOperations()
                if (toStep == AlterSoupStep.DROP_OLD_TABLE) return
            }
            AlterSoupStep.COPY_TABLE -> {
                if (reIndexData) reIndexSoup()
                if (toStep == AlterSoupStep.RE_INDEX_SOUP) return
                dropOldTable()
                store!!.getLongOperations()
                if (toStep == AlterSoupStep.DROP_OLD_TABLE) return
            }
            AlterSoupStep.RE_INDEX_SOUP -> {
                dropOldTable()
                store!!.getLongOperations()
                if (toStep == AlterSoupStep.DROP_OLD_TABLE) return
            }
            AlterSoupStep.DROP_OLD_TABLE -> {
                // Nothing left to do
            }
            else -> {}
        }
    }

    /**
     * Step 1: rename old table
     */
    protected fun renameOldSoupTable() {
        try {
            db!!.beginTransaction()

            // Rename backing table for soup
            db!!.execSQL("ALTER TABLE $soupTableName RENAME TO ${getOldSoupTableName()}")

            // Renaming fts table if any
            if (IndexSpec.hasFTS(oldIndexSpecs!!)) {
                db!!.execSQL("ALTER TABLE $soupTableName${SmartStore.FTS_SUFFIX} RENAME TO ${getOldSoupTableName()}${SmartStore.FTS_SUFFIX}")
            }

            // Update row in alter status table
            updateLongOperationDbRow(AlterSoupStep.RENAME_OLD_SOUP_TABLE)

            db!!.setTransactionSuccessful()
        } finally {
            db!!.endTransaction()
        }
    }

    /**
     * Step 2: drop old indexes / remove entries in soup_index_map / cleanup cache
     */
    protected fun dropOldIndexes() {
        try {
            db!!.beginTransaction()

            val dropIndexFormat = "DROP INDEX IF EXISTS %s_%s_idx"
            // Removing db indexes on table (otherwise registerSoup will fail to create indexes with the same name)
            for (col in arrayOf(SmartStore.CREATED_COL, SmartStore.LAST_MODIFIED_COL)) {
                db!!.execSQL(String.format(dropIndexFormat, soupTableName, col))
            }
            for (i in oldIndexSpecs!!.indices) {
                db!!.execSQL(String.format(dropIndexFormat, soupTableName, "" + i))
            }

            // Cleaning up soup index map table and cache
            DBHelper.getInstance(db!!).delete(db!!, SmartStore.SOUP_INDEX_MAP_TABLE, SmartStore.SOUP_NAME_PREDICATE, soupName!!)

            // Remove from cache
            DBHelper.getInstance(db!!).removeFromCache(soupName!!)

            // Update row in alter status table
            updateLongOperationDbRow(AlterSoupStep.DROP_OLD_INDEXES)

            db!!.setTransactionSuccessful()
        } finally {
            db!!.endTransaction()
        }
    }

    /**
     * Step 3: register soup with new indexes
     */
    protected fun registerSoupUsingTableName() {
        try {
            db!!.beginTransaction()

            // Create new table for soup
            store!!.registerSoupUsingTableName(soupName!!, newIndexSpecs!!, soupTableName!!)

            // Update row in alter status table
            updateLongOperationDbRow(AlterSoupStep.REGISTER_SOUP_USING_TABLE_NAME)

            db!!.setTransactionSuccessful()
        } finally {
            db!!.endTransaction()
        }
    }

    /**
     * Step 4: copy data from old soup table to new soup table
     */
    protected fun copyTable() {
        db!!.beginTransaction()
        try {
            // We need column names in the index specs
            this.newIndexSpecs = store!!.getSoupIndexSpecs(soupName!!)

            // Move data (core columns + indexed paths that we are still indexing)
            copyOldData()

            // Update row in alter status table
            updateLongOperationDbRow(AlterSoupStep.COPY_TABLE)

            db!!.setTransactionSuccessful()
        } finally {
            db!!.endTransaction()
        }
    }

    /**
     * Step 5: re-index soup for new indexes (optional step)
     */
    protected fun reIndexSoup() {
        // Putting path--type of old index specs in a set
        val oldPathTypeSet = HashSet<String>()
        for (oldIndexSpec in oldIndexSpecs!!) {
            oldPathTypeSet.add(oldIndexSpec.getPathType())
        }

        // Filtering out the ones that do not have their path--type in oldPathTypeSet
        val indexPaths = ArrayList<String>()
        for (indexSpec in newIndexSpecs!!) {
            if (!oldPathTypeSet.contains(indexSpec.getPathType())) {
                indexPaths.add(indexSpec.path)
            }
        }

        db!!.beginTransaction()
        try {
            store!!.reIndexSoup(soupName!!, indexPaths.toTypedArray(), false)
            updateLongOperationDbRow(AlterSoupStep.RE_INDEX_SOUP)

            db!!.setTransactionSuccessful()
        } finally {
            db!!.endTransaction()
        }
    }

    /**
     * Step 6: drop old soup table
     */
    protected fun dropOldTable() {
        db!!.beginTransaction()
        try {
            // Drop old table
            db!!.execSQL("DROP TABLE ${getOldSoupTableName()}")

            // Dropping FTS table if any
            if (IndexSpec.hasFTS(oldIndexSpecs!!)) {
                db!!.execSQL("DROP TABLE IF EXISTS ${getOldSoupTableName()}${SmartStore.FTS_SUFFIX}")
            }

            // Update status row
            updateLongOperationDbRow(AlterSoupStep.DROP_OLD_TABLE)

            db!!.setTransactionSuccessful()
        } finally {
            db!!.endTransaction()
        }
    }

    /**
     * Create row in long operations status table for a new alter soup operation
     * @return
     * @throws JSONException
     */
    @Throws(JSONException::class)
    protected fun createLongOperationDbRow(): Long {
        val status = AlterSoupStep.STARTING
        val details = getDetails()

        val now = System.currentTimeMillis()
        val contentValues = ContentValues()
        contentValues.put(SmartStore.TYPE_COL, LongOperationType.alterSoup.toString())
        contentValues.put(SmartStore.STATUS_COL, status.toString())
        contentValues.put(SmartStore.DETAILS_COL, details.toString())
        contentValues.put(SmartStore.CREATED_COL, now)
        contentValues.put(SmartStore.LAST_MODIFIED_COL, now)
        SmartStoreLogger.i(TAG, "$soupName $status")
        return DBHelper.getInstance(db!!).insert(db!!, SmartStore.LONG_OPERATIONS_STATUS_TABLE, contentValues)
    }

    @Throws(JSONException::class)
    override fun getDetails(): JSONObject {
        val details = JSONObject()
        details.put(SOUP_NAME, soupName)
        details.put(SOUP_TABLE_NAME, soupTableName)
        details.put(OLD_INDEX_SPECS, IndexSpec.toJSON(oldIndexSpecs!!))
        details.put(NEW_INDEX_SPECS, IndexSpec.toJSON(newIndexSpecs!!))
        details.put(RE_INDEX_DATA, reIndexData)
        return details
    }

    /**
     * Update row in long operations status table for on-going alter soup operation
     * Delete row if newStatus is AlterStatus.LAST
     * @param newStatus
     */
    protected fun updateLongOperationDbRow(newStatus: AlterSoupStep) {
        if (newStatus == AlterSoupStep.LAST) {
            DBHelper.getInstance(db!!).delete(db!!, SmartStore.LONG_OPERATIONS_STATUS_TABLE, SmartStore.ID_PREDICATE, rowId.toString() + "")
        } else {
            val now = System.currentTimeMillis()
            val contentValues = ContentValues()
            contentValues.put(SmartStore.STATUS_COL, newStatus.toString())
            contentValues.put(SmartStore.LAST_MODIFIED_COL, now)
            DBHelper.getInstance(db!!).update(db!!, SmartStore.LONG_OPERATIONS_STATUS_TABLE, contentValues, SmartStore.ID_PREDICATE, rowId.toString() + "")
        }
        SmartStoreLogger.i(TAG, "$soupName $newStatus")
    }

    /**
     * Helper method
     *
     * @return insert statement to copy data from soup old backing table to soup new backing table
     */
    private fun copyOldData() {
        val mapOldSpecs = IndexSpec.mapForIndexSpecs(oldIndexSpecs!!)
        val mapNewSpecs = IndexSpec.mapForIndexSpecs(newIndexSpecs!!).toMutableMap()

        // Figuring out paths we are keeping
        val oldPaths = mapOldSpecs.keys
        val keptPaths = mapNewSpecs.keys.toMutableSet()
        keptPaths.retainAll(oldPaths)

        // Compute list of columns to copy from / list of columns to copy into
        val oldColumns = ArrayList<String>()
        val newColumns = ArrayList<String>()

        // Adding core columns
        val columns = arrayOf(SmartStore.ID_COL, SmartStore.SOUP_COL, SmartStore.CREATED_COL, SmartStore.LAST_MODIFIED_COL)

        for (column in columns) {
            oldColumns.add(column)
            newColumns.add(column)
        }

        // Adding indexed path columns that we are keeping
        for (keptPath in keptPaths) {
            val oldIndexSpec = mapOldSpecs[keptPath]!!
            val newIndexSpec = mapNewSpecs[keptPath]!!
            if (newIndexSpec.type.getColumnType() == null) {
                // we are now using json1, there is no column to populate
                continue
            }

            if (oldIndexSpec.type.getColumnType() == null // we were using json1 - so columnName will be an expression
                || oldIndexSpec.type.getColumnType() == newIndexSpec.type.getColumnType()
            ) {
                oldColumns.add(oldIndexSpec.columnName!!)
                newColumns.add(newIndexSpec.columnName!!)
            }
        }

        // Compute copy statement
        val copyToSoupTable = String.format(
            "INSERT INTO %s (%s) SELECT %s FROM %s",
            soupTableName, TextUtils.join(",", newColumns),
            TextUtils.join(",", oldColumns), getOldSoupTableName()
        )

        // Execute copy
        db!!.execSQL(copyToSoupTable)

        // Fts
        if (IndexSpec.hasFTS(newIndexSpecs!!)) {
            // Compute list of columns to copy from / list of columns to copy into for the fts table
            val oldColumnsFts = ArrayList<String>()
            val newColumnsFts = ArrayList<String>()

            // Adding rowid column
            oldColumnsFts.add(SmartStore.ID_COL)
            newColumnsFts.add(SmartStore.ROWID_COL)

            // Adding indexed path columns that we are keeping
            for (keptPath in keptPaths) {
                val oldIndexSpec = mapOldSpecs[keptPath]!!
                val newIndexSpec = mapNewSpecs[keptPath]!!
                if ((oldIndexSpec.type.getColumnType() == null // we were using json1 - so columnName will be an expression
                            || oldIndexSpec.type.getColumnType() == newIndexSpec.type.getColumnType())
                    && newIndexSpec.type == SmartStore.Type.full_text
                ) {
                    oldColumnsFts.add(oldIndexSpec.columnName!!)
                    newColumnsFts.add(newIndexSpec.columnName!!)
                }
            }

            // Compute copy statement for fts table
            val copyToFtsTable = String.format(
                "INSERT INTO %s%s (%s) SELECT %s FROM %s",
                soupTableName, SmartStore.FTS_SUFFIX, TextUtils.join(",", newColumnsFts),
                TextUtils.join(",", oldColumnsFts), getOldSoupTableName()
            )

            // Execute copy
            db!!.execSQL(copyToFtsTable)
        }
    }

    /**
     * Return name old backing table should be renamed to
     */
    private fun getOldSoupTableName(): String {
        return this.soupTableName + "_old"
    }

    /**
     * Enum for alter steps
     */
    enum class AlterSoupStep {
        STARTING,
        RENAME_OLD_SOUP_TABLE,
        DROP_OLD_INDEXES,
        REGISTER_SOUP_USING_TABLE_NAME,
        COPY_TABLE,
        RE_INDEX_SOUP,
        DROP_OLD_TABLE;

        companion object {
            val LAST = DROP_OLD_TABLE
        }
    }

    companion object {
        // Fields of details for alter soup long operation row in long_operations_status table
        private const val SOUP_NAME = "soupName"
        private const val SOUP_TABLE_NAME = "soupTableName"
        private const val OLD_SOUP_SPEC = "oldSoupFeatures"
        private const val NEW_SOUP_SPEC = "newSoupFeatures"
        private const val OLD_INDEX_SPECS = "oldIndexSpecs"
        private const val NEW_INDEX_SPECS = "newIndexSpecs"
        private const val RE_INDEX_DATA = "reIndexData"
        const val TAG = "AlterSoup:Status"
    }
}
