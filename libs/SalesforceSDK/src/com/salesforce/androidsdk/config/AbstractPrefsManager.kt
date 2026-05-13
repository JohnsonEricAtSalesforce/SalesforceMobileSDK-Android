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
package com.salesforce.androidsdk.config

import android.content.Context
import android.content.SharedPreferences
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.app.SalesforceSDKManager
import org.json.JSONObject
import java.io.File

abstract class AbstractPrefsManager {

    /**
     *
     * @return name to use for pref file
     */
    protected abstract fun getFilenameRoot(): String

    /**
     *
     * @return true if org level and false if user level
     */
    protected abstract fun isOrgLevel(): Boolean

    /**
     * Sets the prefs for the specified user account.
     *
     * @param attribs prefs.
     * @param account UserAccount instance.
     */
    fun setPrefs(attribs: JSONObject?, account: UserAccount?) {
        if (attribs != null) {
            val sp = getSharedPreferences(account)
            val e = sp.edit()
            val keys = attribs.keys()
            while (keys.hasNext()) {
                val currentKey = keys.next()
                val currentValue = attribs.optString(currentKey)
                e.putString(currentKey, currentValue)
            }
            e.commit()
        }
    }

    /**
     * Sets the prefs for the specified user account.
     *
     * @param attribs prefs.
     * @param account UserAccount instance.
     */
    fun setPrefs(attribs: Map<String, String>, account: UserAccount?) {
        setPrefs(JSONObject(attribs), account)
    }

    private fun getSharedPreferences(account: UserAccount?): SharedPreferences {
        var sharedPrefPath = getFilenameRoot()
        if (account != null) {
            sharedPrefPath = getFilenameRoot() + if (isOrgLevel()) {
                account.getOrgLevelFilenameSuffix()
            } else {
                account.getUserLevelFilenameSuffix()
            }
        }
        return SalesforceSDKManager.getInstance().appContext.getSharedPreferences(
            sharedPrefPath,
            Context.MODE_PRIVATE
        )
    }

    /**
     * Returns the pref value for the specified key, for a user account.
     *
     * @param key Key.
     * @param account UserAccount instance.
     * @return Corresponding value.
     */
    fun getPref(key: String, account: UserAccount?): String? {
        val sp = getSharedPreferences(account)
        @Suppress("UNCHECKED_CAST")
        val customAttributes = sp.all as Map<String, String>?
        return customAttributes?.get(key)
    }

    /**
     * Returns all the prefs for a user account.
     *
     * @param account UserAccount instance.
     * @return Corresponding value.
     */
    fun getPrefs(account: UserAccount?): Map<String, String>? {
        val sp = getSharedPreferences(account)
        @Suppress("UNCHECKED_CAST")
        return sp.all as Map<String, String>?
    }

    /**
     * Clears the stored prefs for the specified user.
     *
     * @param account UserAccount instance.
     */
    fun reset(account: UserAccount?) {
        val sp = getSharedPreferences(account)
        val editor = sp.edit()
        editor.clear()
        editor.commit()
    }

    /**
     * Clears the stored prefs for all users.
     *
     * @return true if successful
     */
    fun resetAll(): Boolean {
        val sharedPrefPath = SalesforceSDKManager.getInstance().appContext.applicationInfo.dataDir + "/shared_prefs"
        val dir = File(sharedPrefPath)
        val fileFilter = { _: File, filename: String ->
            filename.startsWith(getFilenameRoot())
        }
        var success = true
        for (file in dir.listFiles() ?: emptyArray()) {
            if (fileFilter(dir, file.name)) {

                // Removes extension of filename before feeding it to SharedPreferences.
                var filename = file.name
                filename = filename.substring(0, filename.lastIndexOf('.'))

                /*
                 * Removes the SharedPreferences object explicitly before removing the
                 * underlying file. The OS caches the contents of SharedPreferences files
                 * in memory for fast retrieval. If we don't explicitly remove it here,
                 * the next write to the same file will result in stale data being re-added.
                 */
                val sp = SalesforceSDKManager.getInstance().appContext.getSharedPreferences(
                    filename,
                    Context.MODE_PRIVATE
                )
                sp.edit().clear().commit()
                success = file.delete() && success // NB: delete file even if another delete failed
            }
        }
        return success
    }
}
