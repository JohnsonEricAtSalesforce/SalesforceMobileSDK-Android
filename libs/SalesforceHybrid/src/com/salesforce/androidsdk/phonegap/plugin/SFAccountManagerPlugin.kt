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

import android.content.Intent
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.phonegap.util.SalesforceHybridLogger
import org.apache.cordova.CallbackContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Cordova plugin that provides methods related to user account management.
 *
 * @author bhariharan
 */
class SFAccountManagerPlugin : ForcePlugin() {

    /**
     * Supported plugin actions.
     */
    private enum class Action {
        getUsers,
        getCurrentUser,
        logout,
        switchToUser
    }

    @Throws(JSONException::class)
    override fun execute(
        actionStr: String,
        jsVersion: JavaScriptPluginVersion,
        args: JSONArray,
        callbackContext: CallbackContext
    ): Boolean {
        return try {
            val action = Action.valueOf(actionStr)
            when (action) {
                Action.getUsers -> {
                    getUsers(callbackContext)
                    true
                }
                Action.getCurrentUser -> {
                    getCurrentUser(callbackContext)
                    true
                }
                Action.logout -> {
                    logout(args, callbackContext)
                    true
                }
                Action.switchToUser -> {
                    switchToUser(args, callbackContext)
                    true
                }
            }
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    /**
     * Native implementation for the 'getUsers' action.
     *
     * @param callbackContext Used when calling back into Javascript.
     */
    protected fun getUsers(callbackContext: CallbackContext) {
        SalesforceHybridLogger.i(TAG, "getUsers called")
        val userAccounts = SalesforceSDKManager.getInstance().userAccountManager.authenticatedUsers
        val accounts = JSONArray()
        if (!userAccounts.isNullOrEmpty()) {
            for (account in userAccounts) {
                accounts.put(account.toJson())
            }
        }
        callbackContext.success(accounts)
    }

    /**
     * Native implementation for the 'getCurrentUser' action.
     *
     * @param callbackContext Used when calling back into Javascript.
     */
    protected fun getCurrentUser(callbackContext: CallbackContext) {
        SalesforceHybridLogger.i(TAG, "getCurrentUser called")
        val userAccount = SalesforceSDKManager.getInstance().userAccountManager.currentUser
        var account = JSONObject()
        if (userAccount != null) {
            account = userAccount.toJson()
        }
        callbackContext.success(account)
    }

    /**
     * Native implementation for the 'logout' action.
     *
     * @param args Arguments passed in, namely the account to logout of.
     * @param callbackContext Used when calling back into Javascript.
     */
    protected fun logout(args: JSONArray, callbackContext: CallbackContext) {
        SalesforceHybridLogger.i(TAG, "logout called")
        var account = SalesforceSDKManager.getInstance().userAccountManager.currentUser
        if (args.length() > 0) {
            val user = args.optJSONObject(0)
            if (user != null) {
                account = UserAccount(user)
            }
        }
        SalesforceSDKManager.getInstance().userAccountManager.signoutUser(account, cordova.activity)
        callbackContext.success()
    }

    /**
     * Native implementation for the 'switchToUser' action.
     *
     * @param args Arguments passed in, namely the account to switch to.
     * @param callbackContext Used when calling back into Javascript.
     */
    protected fun switchToUser(args: JSONArray, callbackContext: CallbackContext) {
        SalesforceHybridLogger.i(TAG, "switchToUser called")
        var account: UserAccount? = null
        val userAccounts = SalesforceSDKManager.getInstance().userAccountManager.authenticatedUsers

        /*
         * If no user is specified to switch to, we check the number of users
         * available. If only 1 user is signed in, we automatically launch the
         * login activity. If more than 1 user is already signed in, we bring
         * up the default account switcher screen, where a selection can be
         * made on which account to switch to.
         */
        if (args.length() == 0) {
            if (userAccounts == null || userAccounts.size == 1) {
                SalesforceSDKManager.getInstance().userAccountManager.switchToNewUser()
            } else {
                val i = Intent(
                    SalesforceSDKManager.getInstance().appContext,
                    SalesforceSDKManager.getInstance().accountSwitcherActivityClass
                )
                i.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                SalesforceSDKManager.getInstance().appContext.startActivity(i)
            }
        } else {
            val user = args.optJSONObject(0)
            if (user != null) {
                account = UserAccount(user)
            }
            SalesforceSDKManager.getInstance().userAccountManager.switchToUser(account)
        }
        callbackContext.success()
    }

    companion object {
        private const val TAG = "SFAccountManagerPlugin"
    }
}
