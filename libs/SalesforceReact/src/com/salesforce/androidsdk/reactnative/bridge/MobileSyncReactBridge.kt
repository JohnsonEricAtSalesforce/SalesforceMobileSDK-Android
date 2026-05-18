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

import com.facebook.react.bridge.Callback
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.ReadableMap
import com.salesforce.androidsdk.mobilesync.manager.SyncManager
import com.salesforce.androidsdk.mobilesync.target.SyncDownTarget
import com.salesforce.androidsdk.mobilesync.target.SyncUpTarget
import com.salesforce.androidsdk.mobilesync.util.SyncOptions
import com.salesforce.androidsdk.mobilesync.util.SyncState
import com.salesforce.androidsdk.reactnative.util.SalesforceReactLogger
import org.json.JSONObject

class MobileSyncReactBridge(reactContext: ReactApplicationContext) :
    ReactContextBaseJavaModule(reactContext) {

    override fun getName(): String {
        return TAG
    }

    /**
     * Native implementation of syncUp
     * @param args
     * @param successCallback
     * @param errorCallback
     */
    @ReactMethod
    fun syncUp(args: ReadableMap, successCallback: Callback, errorCallback: Callback) {
        // Parse args
        val target = JSONObject(ReactBridgeHelper.toJavaMap(args.getMap(TARGET)))
        val soupName = args.getString(SOUP_NAME)!!
        val options = JSONObject(ReactBridgeHelper.toJavaMap(args.getMap(OPTIONS)))
        val syncName = if (args.hasKey(SYNC_NAME)) args.getString(SYNC_NAME) else null
        try {
            val syncManager = getSyncManager(args)
            syncManager.syncUp(
                SyncUpTarget.fromJSON(target),
                SyncOptions.fromJSON(options),
                soupName,
                syncName,
                object : SyncManager.SyncUpdateCallback {
                    override fun onUpdate(sync: SyncState) {
                        handleSyncUpdate(sync, successCallback, errorCallback)
                    }
                }
            )
        } catch (e: Exception) {
            SalesforceReactLogger.e(TAG, "syncUp call failed", e)
            errorCallback.invoke(e.toString())
        }
    }

    /**
     * Native implementation of syncDown
     * @param args
     * @param successCallback
     * @param errorCallback
     */
    @ReactMethod
    fun syncDown(args: ReadableMap, successCallback: Callback, errorCallback: Callback) {
        // Parse args
        val target = JSONObject(ReactBridgeHelper.toJavaMap(args.getMap(TARGET)))
        val soupName = args.getString(SOUP_NAME)!!
        val options = JSONObject(ReactBridgeHelper.toJavaMap(args.getMap(OPTIONS)))
        val syncName = if (args.hasKey(SYNC_NAME)) args.getString(SYNC_NAME) else null
        try {
            val syncManager = getSyncManager(args)
            syncManager.syncDown(
                SyncDownTarget.fromJSON(target),
                SyncOptions.fromJSON(options),
                soupName,
                syncName,
                object : SyncManager.SyncUpdateCallback {
                    override fun onUpdate(sync: SyncState) {
                        handleSyncUpdate(sync, successCallback, errorCallback)
                    }
                }
            )
        } catch (e: Exception) {
            SalesforceReactLogger.e(TAG, "syncDown call failed", e)
            errorCallback.invoke(e.toString())
        }
    }

    /**
     * Native implementation of getSyncStatus
     * @param args
     * @param successCallback
     * @param errorCallback
     */
    @ReactMethod
    fun getSyncStatus(args: ReadableMap, successCallback: Callback, errorCallback: Callback) {
        try {
            val syncManager = getSyncManager(args)
            val sync: SyncState? = if (args.hasKey(SYNC_ID) && !args.isNull(SYNC_ID)) {
                syncManager.getSyncStatus(args.getInt(SYNC_ID).toLong())
            } else if (args.hasKey(SYNC_NAME) && !args.isNull(SYNC_NAME)) {
                syncManager.getSyncStatus(args.getString(SYNC_NAME))
            } else {
                throw SyncManager.MobileSyncException("neither $SYNC_ID nor $SYNC_NAME were specified")
            }
            ReactBridgeHelper.invoke(successCallback, sync?.asJSON())
        } catch (e: Exception) {
            SalesforceReactLogger.e(TAG, "getSyncStatusByName call failed", e)
            errorCallback.invoke(e.toString())
        }
    }

    /**
     * Native implementation of deleteSync
     * @param args
     * @param successCallback
     * @param errorCallback
     */
    @ReactMethod
    fun deleteSync(args: ReadableMap, successCallback: Callback, errorCallback: Callback) {
        try {
            val syncManager = getSyncManager(args)
            if (args.hasKey(SYNC_ID) && !args.isNull(SYNC_ID)) {
                syncManager.deleteSync(args.getInt(SYNC_ID).toLong())
            } else if (args.hasKey(SYNC_NAME) && !args.isNull(SYNC_NAME)) {
                syncManager.deleteSync(args.getString(SYNC_NAME))
            } else {
                throw SyncManager.MobileSyncException("neither $SYNC_ID nor $SYNC_NAME were specified")
            }
            successCallback.invoke()
        } catch (e: Exception) {
            SalesforceReactLogger.e(TAG, "deleteSyncById call failed", e)
            errorCallback.invoke(e.toString())
        }
    }

    /**
     * Native implementation of reSync
     * @param args
     * @param successCallback
     * @param errorCallback
     */
    @ReactMethod
    fun reSync(args: ReadableMap, successCallback: Callback, errorCallback: Callback) {
        try {
            val syncManager = getSyncManager(args)
            val callback = object : SyncManager.SyncUpdateCallback {
                override fun onUpdate(sync: SyncState) {
                    handleSyncUpdate(sync, successCallback, errorCallback)
                }
            }

            if (args.hasKey(SYNC_ID) && !args.isNull(SYNC_ID)) {
                syncManager.reSync(args.getInt(SYNC_ID).toLong(), callback)
            } else if (args.hasKey(SYNC_NAME) && !args.isNull(SYNC_NAME)) {
                syncManager.reSync(args.getString(SYNC_NAME)!!, callback)
            } else {
                throw SyncManager.MobileSyncException("neither $SYNC_ID nor $SYNC_NAME were specified")
            }
        } catch (e: Exception) {
            SalesforceReactLogger.e(TAG, "reSync call failed", e)
            errorCallback.invoke(e.toString())
        }
    }

    /**
     * Native implementation of cleanResyncGhosts
     * @param args
     * @param successCallback
     * @param errorCallback
     */
    @ReactMethod
    fun cleanResyncGhosts(args: ReadableMap, successCallback: Callback, errorCallback: Callback) {
        // Parse args
        val syncId = args.getInt(SYNC_ID).toLong()
        try {
            val syncManager = getSyncManager(args)
            syncManager.cleanResyncGhosts(syncId, object : SyncManager.CleanResyncGhostsCallback {
                override fun onSuccess(numRecords: Int) {
                    successCallback.invoke(numRecords)
                }

                override fun onError(e: Exception?) {
                    errorCallback.invoke(e.toString())
                }
            })
        } catch (e: Exception) {
            SalesforceReactLogger.e(TAG, "cleanResyncGhosts call failed", e)
            errorCallback.invoke(e.toString())
        }
    }

    /**
     * Sync update handler
     * @param sync
     * @param successCallback
     * @param errorCallback
     */
    private fun handleSyncUpdate(sync: SyncState, successCallback: Callback, errorCallback: Callback) {
        try {
            when (sync.status) {
                SyncState.Status.NEW -> {}
                SyncState.Status.RUNNING -> {}
                SyncState.Status.DONE -> ReactBridgeHelper.invoke(successCallback, sync.asJSON())
                SyncState.Status.FAILED ->
                    // Return sync to React Native with the error message in the JSON
                    ReactBridgeHelper.invoke(errorCallback, sync.asJSON())
                else -> {}
            }
        } catch (e: Exception) {
            SalesforceReactLogger.e(TAG, "handleSyncUpdate call failed", e)
        }
    }

    /**
     * Return sync manager to use
     * @param args Arguments passed to the bridge
     * @return
     */
    @Throws(Exception::class)
    private fun getSyncManager(args: ReadableMap): SyncManager {
        val smartStore = SmartStoreReactBridge.getSmartStore(args)
        return SyncManager.getInstance(null, null, smartStore)
    }

    companion object {
        // Keys in json from/to javascript
        internal const val TARGET = "target"
        internal const val SOUP_NAME = "soupName"
        internal const val OPTIONS = "options"
        internal const val SYNC_ID = "syncId"
        internal const val SYNC_NAME = "syncName"
        const val TAG = "MobileSyncReactBridge"
    }
}
