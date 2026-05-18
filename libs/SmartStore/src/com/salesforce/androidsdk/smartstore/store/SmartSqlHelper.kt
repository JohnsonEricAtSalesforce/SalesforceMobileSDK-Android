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

import com.salesforce.androidsdk.smartstore.store.SmartStore.SmartStoreException
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.util.Locale
import java.util.regex.Pattern

/**
 * SmartSqlHelper "smart" sql Helper
 *
 * Singleton class that provides helpful methods for converting/running "smart" sql
 */
class SmartSqlHelper private constructor() {

    /**
     * Convert "smart" sql query to actual sql
     * A "smart" sql query is a query where columns are of the form {soupName:path} and tables are of the form {soupName}
     *
     * NB: only select's are allowed
     *     only indexed path can be referenced (alternatively you can do {soupName:_soupEntryId} or {soupName:_soupLastModifiedDate}
     *     to get an entire soup element back, do {soupName:_soup}
     *
     * @param db
     * @param smartSql
     * @return actual sql
     */
    fun convertSmartSql(db: SQLiteDatabase, smartSql: String): String {
        // Select's only
        val smartSqlLowerCase = smartSql.lowercase(Locale.getDefault()).trim()
        if (smartSqlLowerCase.startsWith("insert") || smartSqlLowerCase.startsWith("update") || smartSqlLowerCase.startsWith("delete")) {
            throw SmartSqlException("Only SELECT are supported")
        }

        // Replacing {soupName} and {soupName:path}
        val sql = StringBuffer()
        val matcher = SOUP_PATH_PATTERN.matcher(smartSql)
        while (matcher.find()) {
            val fullMatch = matcher.group()
            val match = matcher.group(1)
            val position = matcher.start()

            val beforeStr = smartSql.substring(0, position)
            if (beforeStr.matches(Regex(INSIDE_QUOTED_STRING_REGEXP))
                && !beforeStr.matches(Regex(INSIDE_QUOTED_STRING_FOR_FTS_MATCH_PREDICATE_REGEXP))
            ) {
                continue
            }

            val parts = match.split(":")
            val soupName = parts[0]
            val soupTableName = getSoupTableNameForSmartSql(db, soupName, position)
            val tableQualified = smartSql[position - 1] == '.'
            val tableQualifier = if (tableQualified) "" else "$soupTableName."

            // {soupName}
            if (parts.size == 1) {
                matcher.appendReplacement(sql, soupTableName)
            } else if (parts.size == 2) {
                val path = parts[1]

                // {soupName:_soup}
                when (path) {
                    SOUP -> matcher.appendReplacement(sql, tableQualifier + SmartStore.SOUP_COL)
                    // {soupName:_soupEntryId}
                    SmartStore.SOUP_ENTRY_ID -> matcher.appendReplacement(sql, tableQualifier + SmartStore.ID_COL)
                    // {soupName:_soupCreatedDate}
                    SmartStore.SOUP_CREATED_DATE -> matcher.appendReplacement(sql, tableQualifier + SmartStore.CREATED_COL)
                    // {soupName:_soupLastModifiedDate}
                    SmartStore.SOUP_LAST_MODIFIED_DATE -> matcher.appendReplacement(sql, tableQualifier + SmartStore.LAST_MODIFIED_COL)
                    // {soupName:path}
                    else -> {
                        val columnName = getColumnNameForPathForSmartSql(db, soupName, path, position)
                        matcher.appendReplacement(sql, columnName!!.replace("$", "\\$") /* treat any $ as literal */)
                    }
                }
            } else if (parts.size > 2) {
                reportSmartSqlError("Invalid soup/path reference $fullMatch", position)
            }
        }
        matcher.appendTail(sql)

        // SQL query as string
        var sqlStr = sql.toString()

        // With json1 support, the column name could be an expression of the form json_extract(soup, '$.x.y.z')
        // We can't have TABLE_x.json_extract(soup, ...) or table_alias.json_extract(soup, ...) in the sql query
        // Instead we should have json_extract(TABLE_x.soup, ...)
        sqlStr = sqlStr.replace(Regex(TABLE_DOT_JSON_EXTRACT_REGEXP), "json_extract($1.soup")

        // Done
        return sqlStr
    }

    private fun getColumnNameForPathForSmartSql(db: SQLiteDatabase, soupName: String, path: String, position: Int): String? {
        var columnName: String?
        val indexed = DBHelper.getInstance(db).hasIndexForPath(db, soupName, path)

        if (!indexed) {
            // Thanks to the json1 extension we can query the data even if it is not indexed
            columnName = "json_extract(${SmartStore.SOUP_COL}, '\$.$path')"
        } else {
            try {
                columnName = DBHelper.getInstance(db).getColumnNameForPath(db, soupName, path)
            } catch (e: SmartStoreException) {
                reportSmartSqlError(e.message!!, position)
                columnName = null
            }
        }

        return columnName
    }

    private fun getSoupTableNameForSmartSql(db: SQLiteDatabase, soupName: String, position: Int): String {
        val soupTableName = DBHelper.getInstance(db).getSoupTableName(db, soupName)
        if (soupTableName == null) {
            reportSmartSqlError("Unknown soup $soupName", position)
        }
        return soupTableName!!
    }

    private fun reportSmartSqlError(message: String, position: Int) {
        throw SmartSqlException("$message at character $position")
    }

    /**
     * Exception thrown when smart sql failed to be parsed
     */
    class SmartSqlException(message: String) : SmartStoreException(message) {
        companion object {
            private const val serialVersionUID = -525130153073212701L
        }
    }

    companion object {
        private const val NO_STRINGS_OR_FULL_STRINGS_REGEXP = "^([^']|'[^']*')*"
        //  ^           # the start of the string, then
        //  ([^']       # either not a quote character
        //  |'[^']*'    # or a fully quoted string
        //  )*          # as many times as you want

        private const val INSIDE_QUOTED_STRING_REGEXP = NO_STRINGS_OR_FULL_STRINGS_REGEXP + "'[^']*"

        private const val INSIDE_QUOTED_STRING_FOR_FTS_MATCH_PREDICATE_REGEXP = NO_STRINGS_OR_FULL_STRINGS_REGEXP + "MATCH[ ]+'[^']*"

        @JvmField
        val SOUP_PATH_PATTERN: Pattern = Pattern.compile("\\{([^}]+)\\}")

        const val TABLE_DOT_JSON_EXTRACT_REGEXP = "(\\w+)\\.json_extract\\(soup"

        const val SOUP = "_soup"

        private var INSTANCES: MutableMap<SQLiteDatabase, SmartSqlHelper>? = null

        /**
         * Returns the instance of this class associated with the database specified.
         *
         * @param db Database.
         * @return Instance of this class.
         */
        @JvmStatic
        @Synchronized
        fun getInstance(db: SQLiteDatabase): SmartSqlHelper {
            if (INSTANCES == null) {
                INSTANCES = HashMap()
            }
            var instance = INSTANCES!![db]
            if (instance == null) {
                instance = SmartSqlHelper()
                INSTANCES!![db] = instance
            }
            return instance
        }
    }
}
