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
package com.salesforce.androidsdk.reactnative.bridge

import android.util.SparseArray
import com.facebook.react.bridge.Callback
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.salesforce.androidsdk.accounts.UserAccountManager
import com.salesforce.androidsdk.reactnative.util.SalesforceReactLogger
import com.salesforce.androidsdk.smartstore.app.SmartStoreSDKManager
import com.salesforce.androidsdk.smartstore.store.DBOpenHelper
import com.salesforce.androidsdk.smartstore.store.IndexSpec
import com.salesforce.androidsdk.smartstore.store.QuerySpec
import com.salesforce.androidsdk.smartstore.store.SmartStore
import com.salesforce.androidsdk.smartstore.store.StoreCursor
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class SmartStoreReactBridge(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    override fun getName(): String {
        return "SmartStoreReactBridge"
    }

    /**
     * Native implementation of removeFromSoup
     * @param args
     * @param successCallback
     * @param errorCallback
     */
    @ReactMethod
    fun removeFromSoup(args: ReadableMap, successCallback: Callback, errorCallback: Callback) {
        // Parse args
        val soupName = args.getString(SOUP_NAME)!!

        // Run remove
        try {
            val smartStore = getSmartStore(args)
            val arraySoupEntryIds: ReadableArray? =
                if (!args.hasKey(ENTRY_IDS) || args.isNull(ENTRY_IDS)) null else args.getArray(ENTRY_IDS)
            val mapQuerySpec: ReadableMap? =
                if (!args.hasKey(QUERY_SPEC) || args.isNull(QUERY_SPEC)) null else args.getMap(QUERY_SPEC)
            if (arraySoupEntryIds != null) {
                val ids = ReactBridgeHelper.toJavaList(arraySoupEntryIds)
                val soupEntryIds = LongArray(ids.size) { i ->
                    (ids[i] as Double).toLong()
                }
                smartStore.delete(soupName, *soupEntryIds)
            } else {
                val querySpecJson = JSONObject(ReactBridgeHelper.toJavaMap(mapQuerySpec))
                val querySpec = QuerySpec.fromJSON(soupName, querySpecJson)
                smartStore.deleteByQuery(soupName, querySpec)
            }
            successCallback.invoke()
        } catch (e: Exception) {
            SalesforceReactLogger.e(TAG, "removeFromSoup call failed", e)
            errorCallback.invoke(e.toString())
        }
    }

    /**
     * Native implementation of retrieveSoupEntries
     * @param args
     * @param successCallback
     * @param errorCallback
     */
    @ReactMethod
    fun retrieveSoupEntries(args: ReadableMap, successCallback: Callback, errorCallback: Callback) {
        // Parse args
        val soupName = args.getString(SOUP_NAME)!!

        // Run retrieve
        try {
            val smartStore = getSmartStore(args)
            val soupEntryIdsFromJs = ReactBridgeHelper.toJavaList(args.getArray(ENTRY_IDS)) // we get Double's back
            val soupEntryIds = LongArray(soupEntryIdsFromJs.size) { i ->
                (soupEntryIdsFromJs[i] as Double).toLong()
            }
            val result = smartStore.retrieve(soupName, *soupEntryIds)
            ReactBridgeHelper.invoke(successCallback, result)
        } catch (e: Exception) {
            SalesforceReactLogger.e(TAG, "retrieveSoupEntries call failed", e)
            errorCallback.invoke(e.toString())
        }
    }

    /**
     * Native implementation of closeCursor
     * @param args
     * @param successCallback
     * @param errorCallback
     */
    @ReactMethod
    fun closeCursor(args: ReadableMap, successCallback: Callback, errorCallback: Callback) {
        // Parse args
        val cursorId = args.getInt(CURSOR_ID)
        try {
            val smartStore = getSmartStore(args)

            // Drop cursor from storeCursors map
            getSmartStoreCursors(smartStore).remove(cursorId)
            successCallback.invoke()
        } catch (e: Exception) {
            errorCallback.invoke(e.toString())
        }
    }

    /**
     * Native implementation of moveCursorToPageIndex
     * @param args
     * @param successCallback
     * @param errorCallback
     */
    @ReactMethod
    fun moveCursorToPageIndex(args: ReadableMap, successCallback: Callback, errorCallback: Callback) {
        // Parse args
        val cursorId = args.getInt(CURSOR_ID)
        val index = args.getInt(INDEX)
        val smartStore: SmartStore
        try {
            smartStore = getSmartStore(args)
        } catch (e: Exception) {
            errorCallback.invoke(e.toString())
            return
        }

        // Get cursor
        val storeCursor = getSmartStoreCursors(smartStore).get(cursorId)
        if (storeCursor == null) {
            errorCallback.invoke("Invalid cursor id")
            return
        }

        // Change page
        storeCursor.moveToPageIndex(index)

        // Build json result
        val result = storeCursor.getDataSerialized(smartStore)
        ReactBridgeHelper.invoke(successCallback, result)
    }

    /**
     * Native implementation of soupExists
     * @param args
     * @param successCallback
     * @param errorCallback
     */
    @ReactMethod
    fun soupExists(args: ReadableMap, successCallback: Callback, errorCallback: Callback) {
        // Parse args
        val soupName = args.getString(SOUP_NAME)!!
        try {
            val smartStore = getSmartStore(args)

            // Run check
            val exists = smartStore.hasSoup(soupName)
            ReactBridgeHelper.invoke(successCallback, exists)
        } catch (e: Exception) {
            errorCallback.invoke(e.toString())
        }
    }

    /**
     * Native implementation of upsertSoupEntries
     * @param args
     * @param successCallback
     * @param errorCallback
     */
    @ReactMethod
    fun upsertSoupEntries(args: ReadableMap, successCallback: Callback, errorCallback: Callback) {
        // Parse args
        val soupName = args.getString(SOUP_NAME)!!
        val smartStore: SmartStore
        try {
            smartStore = getSmartStore(args)
        } catch (e: Exception) {
            errorCallback.invoke(e.toString())
            return
        }
        val entriesList = ReactBridgeHelper.toJavaList(args.getArray(ENTRIES))
        val externalIdPath = args.getString(EXTERNAL_ID_PATH)!!
        val entries = mutableListOf<JSONObject>()
        for (i in entriesList.indices) {
            @Suppress("UNCHECKED_CAST")
            entries.add(JSONObject(entriesList[i] as Map<*, *>))
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
                ReactBridgeHelper.invoke(successCallback, results)
            } catch (e: Exception) {
                SalesforceReactLogger.e(TAG, "upsertSoupEntries call failed", e)
                errorCallback.invoke(e.toString())
            } finally {
                smartStore.endTransaction()
            }
        }
    }

    /**
     * Native implementation of registerSoup
     * @param args
     * @param successCallback
     * @param errorCallback
     */
    @ReactMethod
    fun registerSoup(args: ReadableMap, successCallback: Callback, errorCallback: Callback) {
        try {
            // Parse args.
            val smartStore = getSmartStore(args)
            val soupName = if (args.isNull(SOUP_NAME)) null else args.getString(SOUP_NAME)
            val indexSpecs = getIndexSpecsFromArg(args)

            smartStore.registerSoup(soupName, indexSpecs)
            ReactBridgeHelper.invoke(successCallback, soupName!!)
        } catch (e: Exception) {
            SalesforceReactLogger.e(TAG, "registerSoup call failed", e)
            errorCallback.invoke(e.toString())
        }
    }

    /**
     * Native implementation of querySoup
     * @param args
     * @param successCallback
     * @param errorCallback
     */
    @ReactMethod
    fun querySoup(args: ReadableMap, successCallback: Callback, errorCallback: Callback) {
        // Parse args
        val soupName = args.getString(SOUP_NAME)!!
        try {
            val smartStore = getSmartStore(args)
            val querySpecJson = JSONObject(ReactBridgeHelper.toJavaMap(args.getMap(QUERY_SPEC)))
            val querySpec = QuerySpec.fromJSON(soupName, querySpecJson)
            if (querySpec.queryType == QuerySpec.QueryType.smart) {
                throw RuntimeException("Smart queries can only be run through runSmartQuery")
            }

            // Run query
            runQuery(smartStore, querySpec, successCallback)
        } catch (e: Exception) {
            SalesforceReactLogger.e(TAG, "querySoup call failed", e)
            errorCallback.invoke(e.toString())
        }
    }

    /**
     * Native implementation of runSmartSql
     * @param args
     * @param successCallback
     * @param errorCallback
     */
    @ReactMethod
    fun runSmartQuery(args: ReadableMap, successCallback: Callback, errorCallback: Callback) {
        // Parse args
        val querySpecJson = JSONObject(ReactBridgeHelper.toJavaMap(args.getMap(QUERY_SPEC)))
        try {
            val smartStore = getSmartStore(args)
            val querySpec = QuerySpec.fromJSON(null, querySpecJson)
            if (querySpec.queryType != QuerySpec.QueryType.smart) {
                throw RuntimeException("runSmartQuery can only run smart queries")
            }

            // Run query
            runQuery(smartStore, querySpec, successCallback)
        } catch (e: Exception) {
            SalesforceReactLogger.e(TAG, "runSmartQuery call failed", e)
            errorCallback.invoke(e.toString())
        }
    }

    /**
     * Helper for querySoup and runSmartSql
     * @param smartStore
     * @param querySpec
     * @param successCallback
     * @throws JSONException
     */
    @Throws(JSONException::class)
    private fun runQuery(smartStore: SmartStore, querySpec: QuerySpec, successCallback: Callback) {
        // Build store cursor
        val storeCursor = StoreCursor(smartStore, querySpec)
        getSmartStoreCursors(smartStore).put(storeCursor.cursorId, storeCursor)

        // Build json result
        val result = storeCursor.getDataSerialized(smartStore)

        // Done
        ReactBridgeHelper.invoke(successCallback, result)
    }

    /**
     * Native implementation of removeSoup
     * @param args
     * @param successCallback
     * @param errorCallback
     */
    @ReactMethod
    fun removeSoup(args: ReadableMap, successCallback: Callback, errorCallback: Callback) {
        try {
            // Parse args
            val soupName = args.getString(SOUP_NAME)!!
            val smartStore = getSmartStore(args)

            // Run remove
            smartStore.dropSoup(soupName)
            successCallback.invoke()
        } catch (e: Exception) {
            errorCallback.invoke(e.toString())
        }
    }

    /**
     * Native implementation of clearSoup
     * @param args
     * @param successCallback
     * @param errorCallback
     */
    @ReactMethod
    fun clearSoup(args: ReadableMap, successCallback: Callback, errorCallback: Callback) {
        try {
            // Parse args
            val soupName = args.getString(SOUP_NAME)!!
            val smartStore = getSmartStore(args)

            // Run clear
            smartStore.clearSoup(soupName)
            successCallback.invoke()
        } catch (e: Exception) {
            errorCallback.invoke(e.toString())
        }
    }

    /**
     * Native implementation of getDatabaseSize
     * @param args
     * @param successCallback
     * @param errorCallback
     */
    @ReactMethod
    fun getDatabaseSize(args: ReadableMap, successCallback: Callback, errorCallback: Callback) {
        try {
            // Parse args
            val smartStore = getSmartStore(args)
            val databaseSize = smartStore.getDatabaseSize()
            ReactBridgeHelper.invoke(successCallback, databaseSize)
        } catch (e: Exception) {
            errorCallback.invoke(e.toString())
        }
    }

    /**
     * Native implementation of alterSoup
     * @param args
     * @param successCallback
     * @param errorCallback
     */
    @ReactMethod
    fun alterSoup(args: ReadableMap, successCallback: Callback, errorCallback: Callback) {
        try {
            // Parse args.
            val smartStore = getSmartStore(args)
            val soupName = args.getString(SOUP_NAME)!!
            val indexSpecs = getIndexSpecsFromArg(args)
            val reIndexData = args.getBoolean(RE_INDEX_DATA)

            smartStore.alterSoup(soupName, indexSpecs, reIndexData)
            ReactBridgeHelper.invoke(successCallback, soupName)
        } catch (e: Exception) {
            SalesforceReactLogger.e(TAG, "alterSoup call failed", e)
            errorCallback.invoke(e.toString())
        }
    }

    /**
     * Native implementation of reIndexSoup
     * @param args
     * @param successCallback
     * @param errorCallback
     */
    @ReactMethod
    fun reIndexSoup(args: ReadableMap, successCallback: Callback, errorCallback: Callback) {
        // Parse args
        val soupName = args.getString(SOUP_NAME)!!
        try {
            val smartStore = getSmartStore(args)
            val indexPaths = ReactBridgeHelper.toJavaStringList(args.getArray(PATHS))

            // Run register
            smartStore.reIndexSoup(soupName, indexPaths.toTypedArray(), true)
            ReactBridgeHelper.invoke(successCallback, soupName)
        } catch (e: Exception) {
            errorCallback.invoke(e.toString())
        }
    }

    /**
     * Native implementation of getSoupIndexSpecs
     * @param args
     * @param successCallback
     * @param errorCallback
     */
    @ReactMethod
    fun getSoupIndexSpecs(args: ReadableMap, successCallback: Callback, errorCallback: Callback) {
        // Get soup index specs
        try {
            // Parse args
            val soupName = args.getString(SOUP_NAME)!!
            val smartStore = getSmartStore(args)
            val indexSpecs = smartStore.getSoupIndexSpecs(soupName)
            val indexSpecsJson = JSONArray()
            for (indexSpec in indexSpecs) {
                val indexSpecJson = JSONObject()
                indexSpecJson.put(PATH, indexSpec.path)
                indexSpecJson.put(TYPE, indexSpec.type)
                indexSpecsJson.put(indexSpecJson)
            }
            ReactBridgeHelper.invoke(successCallback, indexSpecsJson)
        } catch (e: Exception) {
            SalesforceReactLogger.e(TAG, "getSoupIndexSpecs call failed", e)
            errorCallback.invoke(e.toString())
        }
    }

    /**
     * Native implementation of getAllGlobalStores
     * @param args
     * @param successCallback
     * @param errorCallback
     */
    @ReactMethod
    @Throws(JSONException::class)
    fun getAllGlobalStores(args: ReadableMap, successCallback: Callback, errorCallback: Callback) {
        // return list of StoreConfigs
        val globalDBNames = SmartStoreSDKManager.getInstance().getGlobalStoresPrefixList()
        val storeList = JSONArray()
        try {
            for (i in globalDBNames.indices) {
                val dbName = JSONObject()
                dbName.put(IS_GLOBAL_STORE, true)
                dbName.put(STORE_NAME, globalDBNames[i])
                storeList.put(dbName)
            }
            ReactBridgeHelper.invoke(successCallback, storeList)
        } catch (e: JSONException) {
            SalesforceReactLogger.e(TAG, "getAllGlobalStorePrefixes call failed", e)
            errorCallback.invoke(e.toString())
        }
    }

    /**
     * Native implementation of getAllStores
     * @param args
     * @param successCallback
     * @param errorCallback
     */
    @ReactMethod
    fun getAllStores(args: ReadableMap, successCallback: Callback, errorCallback: Callback) {
        // return list of StoreConfigs
        val userStoreNames = SmartStoreSDKManager.getInstance().getUserStoresPrefixList()
        val storeList = JSONArray()
        try {
            for (i in userStoreNames.indices) {
                val dbName = JSONObject()
                dbName.put(IS_GLOBAL_STORE, false)
                dbName.put(STORE_NAME, userStoreNames[i])
                storeList.put(dbName)
            }
            ReactBridgeHelper.invoke(successCallback, storeList)
        } catch (e: JSONException) {
            SalesforceReactLogger.e(TAG, "getAllStorePrefixes call failed", e)
            errorCallback.invoke(e.toString())
        }
    }

    /**
     * Native implementation of removeStore
     * @param args
     * @param successCallback
     * @param errorCallback
     */
    @ReactMethod
    fun removeStore(args: ReadableMap, successCallback: Callback, errorCallback: Callback) {
        val isGlobal = getIsGlobal(args)
        val storeName = getStoreName(args)
        if (isGlobal) {
            SmartStoreSDKManager.getInstance().removeGlobalSmartStore(storeName)
            ReactBridgeHelper.invoke(successCallback, true)
        } else {
            val account = UserAccountManager.getInstance().cachedCurrentUser
            if (account == null) {
                errorCallback.invoke("No user account found")
            } else {
                SmartStoreSDKManager.getInstance().removeSmartStore(storeName, account, account.communityId)
                ReactBridgeHelper.invoke(successCallback, true)
            }
        }
    }

    /**
     * Native implementation of removeAllGlobalStores
     * @param args
     * @param successCallback
     * @param errorCallback
     */
    @ReactMethod
    fun removeAllGlobalStores(args: ReadableMap, successCallback: Callback, errorCallback: Callback) {
        SmartStoreSDKManager.getInstance().removeAllGlobalStores()
        ReactBridgeHelper.invoke(successCallback, true)
    }

    /**
     * Native implementation of removeAllStores
     * @param args
     * @param successCallback
     * @param errorCallback
     */
    @ReactMethod
    fun removeAllStores(args: ReadableMap, successCallback: Callback, errorCallback: Callback) {
        SmartStoreSDKManager.getInstance().removeAllUserStores()
        ReactBridgeHelper.invoke(successCallback, true)
    }

    /**
     * Build index specs array from javascript argument
     * @param args
     * @return
     * @throws JSONException
     */
    @Throws(JSONException::class)
    private fun getIndexSpecsFromArg(args: ReadableMap): Array<IndexSpec> {
        val indexesJson = JSONArray(ReactBridgeHelper.toJavaList(args.getArray(INDEXES)))
        return IndexSpec.fromJSON(indexesJson)
    }

    companion object {
        // Log tag
        internal const val TAG = "SmartStoreReactBridge"

        // Keys in json from/to javascript
        internal const val RE_INDEX_DATA = "reIndexData"
        internal const val CURSOR_ID = "cursorId"
        internal const val TYPE = "type"
        internal const val SOUP_NAME = "soupName"
        internal const val PATH = "path"
        internal const val PATHS = "paths"
        internal const val QUERY_SPEC = "querySpec"
        internal const val SOUP_SPEC = "soupSpec"
        internal const val SOUP_SPEC_NAME = "name"
        internal const val SOUP_SPEC_FEATURES = "features"
        internal const val EXTERNAL_ID_PATH = "externalIdPath"
        internal const val ENTRIES = "entries"
        internal const val ENTRY_IDS = "entryIds"
        internal const val INDEX = "index"
        internal const val INDEXES = "indexes"
        internal const val IS_GLOBAL_STORE = "isGlobalStore"
        internal const val STORE_NAME = "storeName"

        // Map of cursor id to StoreCursor, per database.
        private val STORE_CURSORS = HashMap<SQLiteDatabase, SparseArray<StoreCursor>>()

        @Synchronized
        private fun getSmartStoreCursors(store: SmartStore): SparseArray<StoreCursor> {
            val db = store.database
            if (!STORE_CURSORS.containsKey(db)) {
                STORE_CURSORS[db] = SparseArray()
            }
            return STORE_CURSORS[db]!!
        }

        /**
         * Return the value of the isGlobalStore argument
         * @param args
         * @return
         */
        private fun getIsGlobal(args: ReadableMap?): Boolean {
            return args?.getBoolean(IS_GLOBAL_STORE) ?: false
        }

        /**
         * Return smartstore to use
         * @param args arguments passed in bridge call
         * @return
         */
        @JvmStatic
        @Throws(Exception::class)
        fun getSmartStore(args: ReadableMap?): SmartStore {
            val isGlobal = getIsGlobal(args)
            val storeName = getStoreName(args)
            return if (isGlobal) {
                SmartStoreSDKManager.getInstance().getGlobalSmartStore(storeName)
            } else {
                val account = UserAccountManager.getInstance().cachedCurrentUser
                    ?: throw Exception("No user account found")
                SmartStoreSDKManager.getInstance().getSmartStore(storeName, account, account.communityId)
            }
        }

        /**
         * Return the value of the storename argument
         * @param args arguments passed in bridge call
         * @return
         */
        private fun getStoreName(args: ReadableMap?): String {
            val storeName = if (args != null && args.hasKey(STORE_NAME)) args.getString(STORE_NAME) else DBOpenHelper.DEFAULT_DB_NAME
            return if (!storeName.isNullOrBlank()) storeName else DBOpenHelper.DEFAULT_DB_NAME
        }
    }
}
