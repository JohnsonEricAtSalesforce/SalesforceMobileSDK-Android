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
package com.salesforce.androidsdk.phonegap.plugin

import com.salesforce.androidsdk.mobilesync.manager.SyncManager
import com.salesforce.androidsdk.mobilesync.manager.SyncManager.SyncUpdateCallback
import com.salesforce.androidsdk.mobilesync.target.SyncDownTarget
import com.salesforce.androidsdk.mobilesync.target.SyncUpTarget
import com.salesforce.androidsdk.mobilesync.util.SyncOptions
import com.salesforce.androidsdk.mobilesync.util.SyncState
import com.salesforce.androidsdk.phonegap.plugin.PluginConstants.IS_GLOBAL_STORE
import com.salesforce.androidsdk.phonegap.plugin.PluginConstants.OPTIONS
import com.salesforce.androidsdk.phonegap.plugin.PluginConstants.SOUP_NAME
import com.salesforce.androidsdk.phonegap.plugin.PluginConstants.STORE_NAME
import com.salesforce.androidsdk.phonegap.plugin.PluginConstants.SYNC_NAME
import com.salesforce.androidsdk.phonegap.plugin.PluginConstants.TARGET
import com.salesforce.androidsdk.phonegap.util.SalesforceHybridLogger
import com.salesforce.androidsdk.util.JSONObjectHelper
import org.apache.cordova.CallbackContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * PhoneGap plugin for mobile sync.
 */
class MobileSyncPlugin : ForcePlugin() {

    /**
     * Supported plugin actions that the client can take.
     */
    private enum class Action {
        syncUp,
        syncDown,
        getSyncStatus,
        reSync,
        cleanResyncGhosts,
        deleteSync
    }

    @Throws(JSONException::class)
    override fun execute(
        actionStr: String,
        jsVersion: JavaScriptPluginVersion,
        args: JSONArray,
        callbackContext: CallbackContext
    ): Boolean {
        val start = System.currentTimeMillis()

        // Figure out action.
        val action: Action
        try {
            action = Action.valueOf(actionStr)
        } catch (e: IllegalArgumentException) {
            SalesforceHybridLogger.e(TAG, "Unknown action $actionStr", e)
            return false
        }

        // Not running smartstore action on the main thread.
        cordova.threadPool.execute {
            // All smart store actions need to be serialized.
            synchronized(MobileSyncPlugin::class.java) {
                try {
                    when (action) {
                        Action.syncUp -> syncUp(args, callbackContext)
                        Action.syncDown -> syncDown(args, callbackContext)
                        Action.getSyncStatus -> getSyncStatus(args, callbackContext)
                        Action.reSync -> reSync(args, callbackContext)
                        Action.cleanResyncGhosts -> cleanResyncGhosts(args, callbackContext)
                        Action.deleteSync -> deleteSync(args, callbackContext)
                    }
                } catch (e: Exception) {
                    SalesforceHybridLogger.e(TAG, "Exception thrown", e)
                    callbackContext.error(e.message)
                }
                SalesforceHybridLogger.d(TAG, "Total time for $action -> ${System.currentTimeMillis() - start}")
            }
        }
        SalesforceHybridLogger.d(TAG, "Main thread time for $action -> ${System.currentTimeMillis() - start}")
        return true
    }

    /**
     * Native implementation of syncUp.
     *
     * @param args
     * @param callbackContext
     * @throws JSONException
     */
    @Throws(Exception::class)
    private fun syncUp(args: JSONArray, callbackContext: CallbackContext) {
        // Parse args.
        val arg0 = args.getJSONObject(0)
        val target = arg0.getJSONObject(TARGET)
        val soupName = arg0.getString(SOUP_NAME)
        val options = arg0.optJSONObject(OPTIONS)
        val syncName = JSONObjectHelper.optString(arg0, SYNC_NAME)
        val isGlobal = SmartStorePlugin.getIsGlobal(arg0)
        val storeName = SmartStorePlugin.getStoreName(arg0)
        val syncManager = getSyncManager(arg0)
        val sync = syncManager.syncUp(
            SyncUpTarget.fromJSON(target),
            SyncOptions.fromJSON(options),
            soupName,
            syncName,
            object : SyncUpdateCallback {
                override fun onUpdate(sync: SyncState) {
                    handleSyncUpdate(sync, isGlobal, storeName)
                }
            }
        )
        callbackContext.success(sync.asJSON())
    }

    /**
     * Native implementation of syncDown.
     *
     * @param args
     * @param callbackContext
     * @throws JSONException
     */
    @Throws(Exception::class)
    private fun syncDown(args: JSONArray, callbackContext: CallbackContext) {
        // Parse args.
        val arg0 = args.getJSONObject(0)
        val target = arg0.getJSONObject(TARGET)
        val soupName = arg0.getString(SOUP_NAME)
        val options = arg0.getJSONObject(OPTIONS)
        val syncName = JSONObjectHelper.optString(arg0, SYNC_NAME)
        val isGlobal = SmartStorePlugin.getIsGlobal(arg0)
        val storeName = SmartStorePlugin.getStoreName(arg0)
        val syncManager = getSyncManager(arg0)
        val sync = syncManager.syncDown(
            SyncDownTarget.fromJSON(target),
            SyncOptions.fromJSON(options),
            soupName,
            syncName,
            object : SyncUpdateCallback {
                override fun onUpdate(sync: SyncState) {
                    handleSyncUpdate(sync, isGlobal, storeName)
                }
            }
        )
        callbackContext.success(sync.asJSON())
    }

    /**
     * Native implementation of getSyncStatus.
     *
     * @param args
     * @param callbackContext
     * @throws JSONException
     */
    @Throws(Exception::class)
    private fun getSyncStatus(args: JSONArray, callbackContext: CallbackContext) {
        // Parse args.
        val arg0 = args.getJSONObject(0)
        val syncManager = getSyncManager(arg0)

        val sync: SyncState? = when {
            arg0.has(SYNC_ID) && !arg0.isNull(SYNC_ID) ->
                syncManager.getSyncStatus(arg0.getLong(SYNC_ID))
            arg0.has(SYNC_NAME) && !arg0.isNull(SYNC_NAME) ->
                syncManager.getSyncStatus(arg0.getString(SYNC_NAME))
            else ->
                throw SyncManager.MobileSyncException("neither $SYNC_ID nor $SYNC_NAME were specified")
        }

        // cordova can't return null, so returning {} when sync is not found
        // cordova.force.js turns it back into a null
        callbackContext.success(sync?.asJSON() ?: JSONObject())
    }

    /**
     * Native implementation of deleteSync.
     *
     * @param args
     * @param callbackContext
     * @throws JSONException
     */
    @Throws(Exception::class)
    private fun deleteSync(args: JSONArray, callbackContext: CallbackContext) {
        // Parse args.
        val arg0 = args.getJSONObject(0)
        val syncManager = getSyncManager(arg0)

        when {
            arg0.has(SYNC_ID) && !arg0.isNull(SYNC_ID) ->
                syncManager.deleteSync(arg0.getLong(SYNC_ID))
            arg0.has(SYNC_NAME) && !arg0.isNull(SYNC_NAME) ->
                syncManager.deleteSync(arg0.getString(SYNC_NAME))
            else ->
                throw SyncManager.MobileSyncException("neither $SYNC_ID nor $SYNC_NAME were specified")
        }

        callbackContext.success()
    }

    /**
     * Native implementation of reSync.
     *
     * @param args
     * @param callbackContext
     * @throws JSONException
     */
    @Throws(Exception::class)
    private fun reSync(args: JSONArray, callbackContext: CallbackContext) {
        // Parse args.
        val arg0 = args.getJSONObject(0)
        val isGlobal = SmartStorePlugin.getIsGlobal(arg0)
        val storeName = SmartStorePlugin.getStoreName(arg0)
        val syncManager = getSyncManager(arg0)

        val callback = object : SyncUpdateCallback {
            override fun onUpdate(sync: SyncState) {
                handleSyncUpdate(sync, isGlobal, storeName)
            }
        }

        val sync: SyncState = when {
            arg0.has(SYNC_ID) && !arg0.isNull(SYNC_ID) ->
                syncManager.reSync(arg0.getLong(SYNC_ID), callback)
            arg0.has(SYNC_NAME) && !arg0.isNull(SYNC_NAME) ->
                syncManager.reSync(arg0.getString(SYNC_NAME), callback)
            else ->
                throw SyncManager.MobileSyncException("neither $SYNC_ID nor $SYNC_NAME were specified")
        }
        callbackContext.success(sync.asJSON())
    }

    /**
     * Native implementation of cleanResyncGhosts.
     *
     * @param args
     * @param callbackContext
     * @throws JSONException
     */
    @Throws(Exception::class)
    private fun cleanResyncGhosts(args: JSONArray, callbackContext: CallbackContext) {
        // Parse args.
        val arg0 = args.getJSONObject(0)
        val syncId = arg0.getLong(SYNC_ID)
        val syncManager = getSyncManager(arg0)
        syncManager.cleanResyncGhosts(syncId, object : SyncManager.CleanResyncGhostsCallback {
            override fun onSuccess(numRecords: Int) {
                callbackContext.success(numRecords)
            }

            override fun onError(e: Exception?) {
                callbackContext.error(e?.message)
            }
        })
    }

    /**
     * Sync update handler.
     *
     * @param sync
     */
    private fun handleSyncUpdate(sync: SyncState, isGlobal: Boolean, storeName: String) {
        cordova.activity.runOnUiThread {
            try {
                val jsonObject = sync.asJSON()
                jsonObject.put(IS_GLOBAL_STORE, isGlobal)
                jsonObject.put(STORE_NAME, storeName)
                val syncAsString = jsonObject.toString()
                val js = "javascript:document.dispatchEvent(new CustomEvent(\"$SYNC_EVENT_TYPE\", { \"$DETAIL\": $syncAsString}))"
                webView.loadUrl(js)
            } catch (e: Exception) {
                SalesforceHybridLogger.e(TAG, "Failed to dispatch event", e)
            }
        }
    }

    /**
     * Return sync manager to use.
     *
     * @param arg0
     * @return SyncManager
     */
    @Throws(Exception::class)
    private fun getSyncManager(arg0: JSONObject): SyncManager {
        val smartStore = SmartStorePlugin.getSmartStore(arg0)
        return SyncManager.getInstance(null, null, smartStore)
    }

    companion object {
        // Keys in json from/to javascript
        private const val SYNC_ID = "syncId"
        private const val TAG = "MobileSyncPlugin"

        // Event
        private const val SYNC_EVENT_TYPE = "sync"
        private const val DETAIL = "detail"
    }
}
