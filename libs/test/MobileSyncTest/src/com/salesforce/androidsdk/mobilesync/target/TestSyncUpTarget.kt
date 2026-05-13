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
package com.salesforce.androidsdk.mobilesync.target

import com.salesforce.androidsdk.mobilesync.manager.SyncManager

import org.json.JSONException
import org.json.JSONObject

import java.io.IOException
import java.net.HttpURLConnection

/**
 * Custom sync up target for tests.
 */
class TestSyncUpTarget : SyncUpTarget {

    private val syncBehavior: SyncBehavior

    enum class SyncBehavior {
        SOFT_FAIL_ON_SYNC, // doesn't update server but doesn't throw exception, so sync should not end up in failed state
        HARD_FAIL_ON_SYNC, // doesn't update server and throw exception, so sync should end up in failed state
        NO_FAIL
    }

    @Throws(JSONException::class)
    constructor(target: JSONObject) : super(target) {
        this.syncBehavior = SyncBehavior.valueOf(target.getString(SYNC_BEHAVIOR))
    }

    constructor(syncBehavior: SyncBehavior) : super() {
        this.syncBehavior = syncBehavior
    }

    @Throws(JSONException::class)
    override fun asJSON(): JSONObject {
        val target = JSONObject()
        target.put(ANDROID_IMPL, javaClass.name)
        target.put(SYNC_BEHAVIOR, syncBehavior.name)
        return target
    }

    @Throws(JSONException::class, IOException::class)
    override fun createOnServer(syncManager: SyncManager, objectType: String, fields: Map<String, Any>): String? {
        return when (syncBehavior) {
            SyncBehavior.SOFT_FAIL_ON_SYNC -> null
            SyncBehavior.HARD_FAIL_ON_SYNC -> throw RuntimeException("create hard fail")
            else -> { // case NO_FAIL:
                val id = "ID." + seq++
                if (actionCollector != null) {
                    actionCollector!!.createdRecordIds.add(id)
                }
                id
            }
        }
    }

    @Throws(IOException::class)
    override fun deleteOnServer(syncManager: SyncManager, objectType: String, objectId: String): Int {
        return when (syncBehavior) {
            SyncBehavior.SOFT_FAIL_ON_SYNC -> HttpURLConnection.HTTP_NOT_FOUND
            SyncBehavior.HARD_FAIL_ON_SYNC -> throw RuntimeException("delete hard fail")
            else -> { // case NO_FAIL:
                if (actionCollector != null) {
                    actionCollector!!.deletedRecordIds.add(objectId)
                }
                HttpURLConnection.HTTP_OK
            }
        }
    }



    @Throws(IOException::class)
    override fun updateOnServer(syncManager: SyncManager, objectType: String, objectId: String, fields: Map<String, Any>): Int {
        return when (syncBehavior) {
            SyncBehavior.SOFT_FAIL_ON_SYNC -> HttpURLConnection.HTTP_NOT_FOUND
            SyncBehavior.HARD_FAIL_ON_SYNC -> throw RuntimeException("update hard fail")
            else -> { // case NO_FAIL:
                if (actionCollector != null) {
                    actionCollector!!.updatedRecordIds.add(objectId)
                }
                HttpURLConnection.HTTP_OK
            }
        }
    }

    class ActionCollector {
        val createdRecordIds: MutableList<String> = ArrayList()
        val updatedRecordIds: MutableList<String> = ArrayList()
        val deletedRecordIds: MutableList<String> = ArrayList()
    }

    companion object {
        const val SYNC_BEHAVIOR = "SYNC_BEHAVIOR"

        private var seq = 0
        private var actionCollector: ActionCollector? = null

        fun setActionCollector(collector: ActionCollector?) {
            actionCollector = collector
        }
    }
}
