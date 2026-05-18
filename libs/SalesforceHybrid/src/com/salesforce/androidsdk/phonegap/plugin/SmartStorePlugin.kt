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
package com.salesforce.androidsdk.phonegap.plugin

import android.util.SparseArray
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.androidsdk.phonegap.plugin.PluginConstants.CURSOR_ID
import com.salesforce.androidsdk.phonegap.plugin.PluginConstants.ENTRIES
import com.salesforce.androidsdk.phonegap.plugin.PluginConstants.ENTRY_IDS
import com.salesforce.androidsdk.phonegap.plugin.PluginConstants.EXTERNAL_ID_PATH
import com.salesforce.androidsdk.phonegap.plugin.PluginConstants.INDEX
import com.salesforce.androidsdk.phonegap.plugin.PluginConstants.INDEXES
import com.salesforce.androidsdk.phonegap.plugin.PluginConstants.IS_GLOBAL_STORE
import com.salesforce.androidsdk.phonegap.plugin.PluginConstants.PATH
import com.salesforce.androidsdk.phonegap.plugin.PluginConstants.PATHS
import com.salesforce.androidsdk.phonegap.plugin.PluginConstants.QUERY_SPEC
import com.salesforce.androidsdk.phonegap.plugin.PluginConstants.RE_INDEX_DATA
import com.salesforce.androidsdk.phonegap.plugin.PluginConstants.SOUP_NAME
import com.salesforce.androidsdk.phonegap.plugin.PluginConstants.STORE_NAME
import com.salesforce.androidsdk.phonegap.plugin.PluginConstants.TYPE
import com.salesforce.androidsdk.phonegap.util.SalesforceHybridLogger
import com.salesforce.androidsdk.smartstore.app.SmartStoreSDKManager
import com.salesforce.androidsdk.smartstore.store.DBOpenHelper
import com.salesforce.androidsdk.smartstore.store.IndexSpec
import com.salesforce.androidsdk.smartstore.store.QuerySpec
import com.salesforce.androidsdk.smartstore.store.QuerySpec.QueryType
import com.salesforce.androidsdk.smartstore.store.SmartStore
import com.salesforce.androidsdk.smartstore.store.SmartStore.SmartStoreException
import com.salesforce.androidsdk.smartstore.store.StoreCursor
import com.salesforce.androidsdk.smartstore.ui.SmartStoreInspectorActivity
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.apache.cordova.CallbackContext
import org.apache.cordova.PluginResult
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * PhoneGap plugin for smart store.
 */
open class SmartStorePlugin : ForcePlugin() {

    /**
     * Supported plugin actions that the client can take.
     */
    internal enum class Action {
        pgAlterSoup,
        pgClearSoup,
        pgCloseCursor,
        pgGetDatabaseSize,
        pgGetSoupIndexSpecs,
        pgMoveCursorToPageIndex,
        pgQuerySoup,
        pgRegisterSoup,
        pgReIndexSoup,
        pgRemoveFromSoup,
        pgRemoveSoup,
        pgRetrieveSoupEntries,
        pgRunSmartQuery,
        pgShowInspector,
        pgSoupExists,
        pgUpsertSoupEntries,
        pgGetAllGlobalStores,
        pgGetAllStores,
        pgRemoveStore,
        pgRemoveAllGlobalStores,
        pgRemoveAllStores
    }

    @Throws(JSONException::class)
    override fun execute(
        actionStr: String,
        jsVersion: JavaScriptPluginVersion,
        args: JSONArray,
        callbackContext: CallbackContext
    ): Boolean {
        val start = System.currentTimeMillis()

        // Figure out action
        val action: Action
        try {
            action = Action.valueOf(actionStr)
        } catch (e: IllegalArgumentException) {
            SalesforceHybridLogger.e(TAG, "Unknown action: $actionStr", e)
            return false
        }

        // Not running smartstore action on the main thread
        cordova.threadPool.execute {
            // All smart store actions need to be serialized
            synchronized(this) {
                try {
                    when (action) {
                        Action.pgAlterSoup -> alterSoup(args, callbackContext)
                        Action.pgClearSoup -> clearSoup(args, callbackContext)
                        Action.pgCloseCursor -> closeCursor(args, callbackContext)
                        Action.pgGetDatabaseSize -> getDatabaseSize(args, callbackContext)
                        Action.pgGetSoupIndexSpecs -> getSoupIndexSpecs(args, callbackContext)
                        Action.pgMoveCursorToPageIndex -> moveCursorToPageIndex(args, callbackContext)
                        Action.pgQuerySoup -> querySoup(args, callbackContext)
                        Action.pgRegisterSoup -> registerSoup(args, callbackContext)
                        Action.pgReIndexSoup -> reIndexSoup(args, callbackContext)
                        Action.pgRemoveFromSoup -> removeFromSoup(args, callbackContext)
                        Action.pgRemoveSoup -> removeSoup(args, callbackContext)
                        Action.pgRetrieveSoupEntries -> retrieveSoupEntries(args, callbackContext)
                        Action.pgRunSmartQuery -> runSmartQuery(args, callbackContext)
                        Action.pgShowInspector -> showInspector(args, callbackContext)
                        Action.pgSoupExists -> soupExists(args, callbackContext)
                        Action.pgUpsertSoupEntries -> upsertSoupEntries(args, callbackContext)
                        Action.pgGetAllGlobalStores -> getAllGlobalStorePrefixes(args, callbackContext)
                        Action.pgGetAllStores -> getAllStorePrefixes(args, callbackContext)
                        Action.pgRemoveStore -> removeStore(args, callbackContext)
                        Action.pgRemoveAllGlobalStores -> removeAllGlobalStores(args, callbackContext)
                        Action.pgRemoveAllStores -> removeAllStores(args, callbackContext)
                    }
                } catch (e: Exception) {
                    SalesforceHybridLogger.w(TAG, "execute call failed", e)
                    callbackContext.error(e.message)
                }
                SalesforceHybridLogger.d(TAG, "Total time for $action -> ${System.currentTimeMillis() - start}")
            }
        }
        SalesforceHybridLogger.d(TAG, "Main thread time for $action -> ${System.currentTimeMillis() - start}")
        return true
    }

    /**
     * Native implementation of pgRemoveFromSoup
     * @param args JSONArray with arguments from JS
     * @param callbackContext CallbackContext for plugin
     * @throws Exception
     */
    @Throws(Exception::class)
    private fun removeFromSoup(args: JSONArray, callbackContext: CallbackContext) {
        // Parse args
        val arg0 = args.getJSONObject(0)
        val soupName = arg0.getString(SOUP_NAME)
        val smartStore = getSmartStore(arg0)
        val jsonSoupEntryIds = arg0.optJSONArray(ENTRY_IDS)
        val querySpecJson = arg0.optJSONObject(QUERY_SPEC)

        if (jsonSoupEntryIds != null) {
            val soupEntryIds = LongArray(jsonSoupEntryIds.length()) { i ->
                jsonSoupEntryIds.getLong(i)
            }
            // Run remove
            smartStore.delete(soupName, *soupEntryIds)
        } else {
            val querySpec = QuerySpec.fromJSON(soupName, querySpecJson)
            // Run remove
            smartStore.deleteByQuery(soupName, querySpec)
        }
        callbackContext.success()
    }

    /**
     * Native implementation of pgRetrieveSoupEntries
     * @param args JSONArray with arguments from JS
     * @param callbackContext CallbackContext for plugin
     * @throws Exception
     */
    @Throws(Exception::class)
    private fun retrieveSoupEntries(args: JSONArray, callbackContext: CallbackContext) {
        // Parse args
        val arg0 = args.getJSONObject(0)
        val soupName = arg0.getString(SOUP_NAME)
        val smartStore = getSmartStore(arg0)
        val jsonSoupEntryIds = arg0.getJSONArray(ENTRY_IDS)
        val soupEntryIds = LongArray(jsonSoupEntryIds.length()) { i ->
            jsonSoupEntryIds.getLong(i)
        }

        // Run retrieve
        val result = smartStore.retrieve(soupName, *soupEntryIds)
        val pluginResult = PluginResult(PluginResult.Status.OK, result)
        callbackContext.sendPluginResult(pluginResult)
    }

    /**
     * Native implementation of pgCloseCursor
     * @param args JSONArray with arguments from JS
     * @param callbackContext CallbackContext for plugin
     * @throws Exception
     */
    @Throws(Exception::class)
    private fun closeCursor(args: JSONArray, callbackContext: CallbackContext) {
        // Parse args
        val arg0 = args.getJSONObject(0)
        val cursorId = arg0.getInt(CURSOR_ID)
        val smartStore = getSmartStore(arg0)

        // Drop cursor from storeCursors map
        getSmartStoreCursors(smartStore).remove(cursorId)
        callbackContext.success()
    }

    /**
     * Native implementation of pgMoveCursorToPageIndex
     * @param args JSONArray with arguments from JS
     * @param callbackContext CallbackContext for plugin
     * @throws Exception
     */
    @Throws(Exception::class)
    private fun moveCursorToPageIndex(args: JSONArray, callbackContext: CallbackContext) {
        // Parse args
        val arg0 = args.getJSONObject(0)
        val cursorId = arg0.getInt(CURSOR_ID)
        val index = arg0.getInt(INDEX)
        val smartStore = getSmartStore(arg0)

        // Get cursor
        val storeCursor = getSmartStoreCursors(smartStore).get(cursorId)
        if (storeCursor == null) {
            callbackContext.error("Invalid cursor id")
            return
        }

        // Change page
        storeCursor.moveToPageIndex(index)

        // Build json result
        val result = if (useQueryAsString) storeCursor.getDataSerialized(smartStore) else storeCursor.getDataDeserialized(smartStore)

        // Done
        callbackContext.success(result)
    }

    /**
     * Native implementation of pgShowInspector
     * @param args JSONArray with arguments from JS
     * @param callbackContext CallbackContext for plugin
     * @throws JSONException
     */
    @Throws(JSONException::class)
    private fun showInspector(args: JSONArray, callbackContext: CallbackContext) {
        // Parse args
        val arg0 = args.getJSONObject(0)
        val isGlobal = getIsGlobal(arg0)
        val storeName = getStoreName(arg0)
        val activity = cordova.activity
        activity.startActivity(SmartStoreInspectorActivity.getIntent(activity, isGlobal, storeName))
    }

    /**
     * Native implementation of pgSoupExists
     * @param args JSONArray with arguments from JS
     * @param callbackContext CallbackContext for plugin
     * @throws Exception
     */
    @Throws(Exception::class)
    private fun soupExists(args: JSONArray, callbackContext: CallbackContext) {
        // Parse args
        val arg0 = args.getJSONObject(0)
        val soupName = arg0.getString(SOUP_NAME)
        val smartStore = getSmartStore(arg0)

        // Run hasSoup
        val exists = smartStore.hasSoup(soupName)
        val pluginResult = PluginResult(PluginResult.Status.OK, exists)
        callbackContext.sendPluginResult(pluginResult)
    }

    /**
     * @param args JSONArray with arguments from JS
     * @param callbackContext CallbackContext for plugin
     * @throws JSONException
     */
    @Throws(JSONException::class)
    private fun getAllGlobalStorePrefixes(args: JSONArray, callbackContext: CallbackContext) {
        // return list of StoreConfigs
        val globalDBNames = SmartStoreSDKManager.getInstance().getGlobalStoresPrefixList()
        sendStoreConfig(callbackContext, globalDBNames, true)
    }

    /**
     * @param args JSONArray with arguments from JS
     * @param callbackContext CallbackContext for plugin
     * @throws JSONException
     */
    @Throws(JSONException::class)
    private fun getAllStorePrefixes(args: JSONArray, callbackContext: CallbackContext) {
        // return list of StoreConfigs
        val userDBNames = SmartStoreSDKManager.getInstance().getUserStoresPrefixList()
        sendStoreConfig(callbackContext, userDBNames, false)
    }

    /**
     * @param args JSONArray with arguments from JS
     * @param callbackContext CallbackContext for plugin
     * @throws Exception
     */
    @Throws(Exception::class)
    private fun removeStore(args: JSONArray, callbackContext: CallbackContext) {
        val arg0 = args.getJSONObject(0)
        val isGlobal = getIsGlobal(arg0)
        val storeName = getStoreName(arg0)
        if (isGlobal) {
            SmartStoreSDKManager.getInstance().removeGlobalSmartStore(storeName)
        } else {
            val account = UserAccountManager.getInstance().cachedCurrentUser
                ?: throw Exception("No user account found")
            SmartStoreSDKManager.getInstance().removeSmartStore(storeName, account, account.communityId)
        }
        val pluginResult = PluginResult(PluginResult.Status.OK, true)
        callbackContext.sendPluginResult(pluginResult)
    }

    /**
     * @param args JSONArray with arguments from JS
     * @param callbackContext CallbackContext for plugin
     * @throws JSONException
     */
    @Throws(JSONException::class)
    private fun removeAllGlobalStores(args: JSONArray, callbackContext: CallbackContext) {
        SmartStoreSDKManager.getInstance().removeAllGlobalStores()
        val pluginResult = PluginResult(PluginResult.Status.OK, true)
        callbackContext.sendPluginResult(pluginResult)
    }

    /**
     * @param args JSONArray with arguments from JS
     * @param callbackContext CallbackContext for plugin
     * @throws JSONException
     */
    @Throws(JSONException::class)
    private fun removeAllStores(args: JSONArray, callbackContext: CallbackContext) {
        SmartStoreSDKManager.getInstance().removeAllUserStores()
        val pluginResult = PluginResult(PluginResult.Status.OK, true)
        callbackContext.sendPluginResult(pluginResult)
    }

    /**
     * Native implementation of pgUpsertSoupEntries
     * @param args JSONArray with arguments from JS
     * @param callbackContext CallbackContext for plugin
     * @throws Exception
     */
    @Throws(Exception::class)
    private fun upsertSoupEntries(args: JSONArray, callbackContext: CallbackContext) {
        // Parse args
        val arg0 = args.getJSONObject(0)
        val soupName = arg0.getString(SOUP_NAME)
        val smartStore = getSmartStore(arg0)
        val entriesJson = arg0.getJSONArray(ENTRIES)
        val externalIdPath = arg0.getString(EXTERNAL_ID_PATH)
        val entries = mutableListOf<JSONObject>()
        for (i in 0 until entriesJson.length()) {
            entries.add(entriesJson.getJSONObject(i))
        }

        // Run upsert
        synchronized(smartStore.database) {
            smartStore.beginTransaction()
            try {
                val results = JSONArray()
                for (entry in entries) {
                    results.put(smartStore.upsert(soupName, entry, externalIdPath, false))
                }
                smartStore.setTransactionSuccessful()
                val pluginResult = PluginResult(PluginResult.Status.OK, results)
                callbackContext.sendPluginResult(pluginResult)
            } finally {
                smartStore.endTransaction()
            }
        }
    }

    /**
     * Native implementation of pgRegisterSoup
     * @param args JSONArray with arguments from JS
     * @param callbackContext CallbackContext for plugin
     * @throws Exception
     */
    @Throws(Exception::class)
    private fun registerSoup(args: JSONArray, callbackContext: CallbackContext) {
        // Parse args
        val arg0 = args.getJSONObject(0)
        val soupName: String? = if (arg0.isNull(SOUP_NAME)) null else arg0.getString(SOUP_NAME)
        val indexSpecs = getIndexSpecsFromArg(arg0)
        val smartStore = getSmartStore(arg0)

        // Run register
        smartStore.registerSoup(soupName, indexSpecs)
        callbackContext.success(soupName ?: "")
    }

    /**
     * Native implementation of pgQuerySoup
     * @param args JSONArray with arguments from JS
     * @param callbackContext CallbackContext for plugin
     * @throws Exception
     */
    @Throws(Exception::class)
    private fun querySoup(args: JSONArray, callbackContext: CallbackContext) {
        // Parse args
        val arg0 = args.getJSONObject(0)
        val soupName = arg0.getString(SOUP_NAME)
        val smartStore = getSmartStore(arg0)
        val querySpecJson = arg0.getJSONObject(QUERY_SPEC)
        val querySpec = QuerySpec.fromJSON(soupName, querySpecJson)
        if (querySpec.queryType == QueryType.smart) {
            throw RuntimeException("Smart queries can only be run through runSmartQuery")
        }

        // Run query
        runQuery(smartStore, querySpec, callbackContext)
    }

    /**
     * Native implementation of pgRunSmartSql
     * @param args JSONArray with arguments from JS
     * @param callbackContext CallbackContext for plugin
     */
    @Throws(Exception::class)
    private fun runSmartQuery(args: JSONArray, callbackContext: CallbackContext) {
        // Parse args
        val arg0 = args.getJSONObject(0)
        val querySpecJson = arg0.getJSONObject(QUERY_SPEC)
        val smartStore = getSmartStore(arg0)
        val querySpec = QuerySpec.fromJSON(null, querySpecJson)
        if (querySpec.queryType != QueryType.smart) {
            throw RuntimeException("runSmartQuery can only run smart queries")
        }

        // Run query
        runQuery(smartStore, querySpec, callbackContext)
    }

    /**
     * Helper for querySoup and runSmartSql
     * @param querySpec
     * @param callbackContext CallbackContext for plugin
     * @throws JSONException
     */
    @Throws(JSONException::class)
    private fun runQuery(smartStore: SmartStore, querySpec: QuerySpec, callbackContext: CallbackContext) {
        // Build store cursor
        val storeCursor = StoreCursor(smartStore, querySpec)
        getSmartStoreCursors(smartStore).put(storeCursor.cursorId, storeCursor)

        // Build json result
        val result = if (useQueryAsString) storeCursor.getDataSerialized(smartStore) else storeCursor.getDataDeserialized(smartStore)

        // Done
        callbackContext.success(result)
    }

    /**
     * Native implementation of pgRemoveSoup
     * @param args JSONArray with arguments from JS
     * @param callbackContext CallbackContext for plugin
     * @throws Exception
     */
    @Throws(Exception::class)
    private fun removeSoup(args: JSONArray, callbackContext: CallbackContext) {
        // Parse args
        val arg0 = args.getJSONObject(0)
        val soupName = arg0.getString(SOUP_NAME)
        val smartStore = getSmartStore(arg0)

        // Run remove
        smartStore.dropSoup(soupName)
        callbackContext.success()
    }

    /**
     * Native implementation of pgClearSoup
     * @param args JSONArray with arguments from JS
     * @param callbackContext CallbackContext for plugin
     * @throws Exception
     */
    @Throws(Exception::class)
    private fun clearSoup(args: JSONArray, callbackContext: CallbackContext) {
        // Parse args
        val arg0 = args.getJSONObject(0)
        val soupName = arg0.getString(SOUP_NAME)
        val smartStore = getSmartStore(arg0)

        // Run clear
        smartStore.clearSoup(soupName)
        callbackContext.success()
    }

    /**
     * Native implementation of pgGetDatabaseSize
     * @param args JSONArray with arguments from JS
     * @param callbackContext CallbackContext for plugin
     * @throws Exception
     */
    @Throws(Exception::class)
    private fun getDatabaseSize(args: JSONArray, callbackContext: CallbackContext) {
        // Parse args
        val arg0 = args.optJSONObject(0)
        val smartStore = getSmartStore(arg0)
        val databaseSize = smartStore.getDatabaseSize()
        callbackContext.success(databaseSize)
    }

    /**
     * Native implementation of pgAlterSoup
     * @param args JSONArray with arguments from JS
     * @param callbackContext CallbackContext for plugin
     * @throws Exception
     */
    @Throws(Exception::class)
    private fun alterSoup(args: JSONArray, callbackContext: CallbackContext) {
        // Parse args
        val arg0 = args.getJSONObject(0)
        val soupName = arg0.getString(SOUP_NAME)
        val indexSpecs = getIndexSpecsFromArg(arg0)
        val reIndexData = arg0.getBoolean(RE_INDEX_DATA)
        val smartStore = getSmartStore(arg0)

        // Run alter
        smartStore.alterSoup(soupName, indexSpecs, reIndexData)
        callbackContext.success(soupName)
    }

    /**
     * Native implementation of pgReIndexSoup
     * @param args JSONArray with arguments from JS
     * @param callbackContext CallbackContext for plugin
     * @throws Exception
     */
    @Throws(Exception::class)
    private fun reIndexSoup(args: JSONArray, callbackContext: CallbackContext) {
        // Parse args
        val arg0 = args.getJSONObject(0)
        val soupName = arg0.getString(SOUP_NAME)
        val smartStore = getSmartStore(arg0)
        val indexPathsJson = arg0.getJSONArray(PATHS)
        val indexPaths = mutableListOf<String>()
        for (i in 0 until indexPathsJson.length()) {
            indexPaths.add(indexPathsJson.getString(i))
        }

        // Run register
        smartStore.reIndexSoup(soupName, indexPaths.toTypedArray(), true)
        callbackContext.success(soupName)
    }

    /**
     * Native implementation of pgGetSoupIndexSpecs
     * @param args JSONArray with arguments from JS
     * @param callbackContext CallbackContext for plugin
     * @throws Exception
     */
    @Throws(Exception::class)
    private fun getSoupIndexSpecs(args: JSONArray, callbackContext: CallbackContext) {
        // Parse args
        val arg0 = args.getJSONObject(0)
        val soupName = arg0.getString(SOUP_NAME)
        val smartStore = getSmartStore(arg0)

        // Get soup index specs
        val indexSpecs = smartStore.getSoupIndexSpecs(soupName)
        val indexSpecsJson = JSONArray()
        for (indexSpec in indexSpecs) {
            val indexSpecJson = JSONObject()
            indexSpecJson.put(PATH, indexSpec.path)
            indexSpecJson.put(TYPE, indexSpec.type)
            indexSpecsJson.put(indexSpecJson)
        }
        callbackContext.success(indexSpecsJson)
    }

    /**
     * Build index specs array from json object argument
     * @param arg0
     * @return
     * @throws JSONException
     */
    @Throws(JSONException::class)
    private fun getIndexSpecsFromArg(arg0: JSONObject): Array<IndexSpec> {
        val indexesJson = arg0.getJSONArray(INDEXES)
        return IndexSpec.fromJSON(indexesJson)
    }

    @Throws(JSONException::class)
    private fun sendStoreConfig(callbackContext: CallbackContext, dbNames: List<String>, isGlobal: Boolean) {
        val jsonArray = JSONArray()
        for (name in dbNames) {
            val obj = JSONObject()
            obj.put(STORE_NAME, name)
            obj.put(IS_GLOBAL_STORE, true)
            jsonArray.put(obj)
        }
        val pluginResult = PluginResult(PluginResult.Status.OK, jsonArray)
        callbackContext.sendPluginResult(pluginResult)
    }

    companion object {
        private const val TAG = "SmartStorePlugin"

        // Map of cursor id to StoreCursor, per database.
        private val STORE_CURSORS: MutableMap<SQLiteDatabase, SparseArray<StoreCursor>> = HashMap()

        @Synchronized
        private fun getSmartStoreCursors(store: SmartStore): SparseArray<StoreCursor> {
            val db = store.database
            if (!STORE_CURSORS.containsKey(db)) {
                STORE_CURSORS[db] = SparseArray()
            }
            return STORE_CURSORS[db]!!
        }

        private var useQueryAsString = true

        @JvmStatic
        protected fun setUseQueryAsString(b: Boolean) {
            useQueryAsString = b
        }

        /**
         * Return smartstore to use
         * @param arg0 first argument passed in plugin call
         * @return
         */
        @JvmStatic
        @Throws(Exception::class)
        fun getSmartStore(arg0: JSONObject?): SmartStore {
            val isGlobal = getIsGlobal(arg0)
            val storeName = getStoreName(arg0)
            return if (isGlobal) {
                SmartStoreSDKManager.getInstance().getGlobalSmartStore(storeName)
            } else {
                val account = UserAccountManager.getInstance().cachedCurrentUser
                    ?: throw Exception("No user account found")
                SmartStoreSDKManager.getInstance().getSmartStore(storeName, account, account.communityId)
            }
        }

        /**
         * Return the value of the isGlobalStore argument
         * @param arg0
         * @return
         */
        @JvmStatic
        fun getIsGlobal(arg0: JSONObject?): Boolean {
            return arg0?.optBoolean(IS_GLOBAL_STORE, false) ?: false
        }

        /**
         * Return the value of the storename argument
         * @param arg0
         * @return
         */
        @JvmStatic
        fun getStoreName(arg0: JSONObject?): String {
            return arg0?.optString(STORE_NAME, DBOpenHelper.DEFAULT_DB_NAME) ?: DBOpenHelper.DEFAULT_DB_NAME
        }
    }
}
