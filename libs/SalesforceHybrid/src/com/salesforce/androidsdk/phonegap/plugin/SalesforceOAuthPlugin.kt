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

import com.salesforce.androidsdk.phonegap.ui.SalesforceDroidGapActivity
import com.salesforce.androidsdk.phonegap.ui.SalesforceWebViewClientHelper
import com.salesforce.androidsdk.phonegap.util.SalesforceHybridLogger
import org.apache.cordova.CallbackContext
import org.apache.cordova.PluginResult
import org.json.JSONArray
import org.json.JSONException

/**
 * PhoneGap plugin for Salesforce OAuth.
 */
class SalesforceOAuthPlugin : ForcePlugin() {

    /**
     * Supported plugin actions that the client can take.
     */
    private enum class Action {
        authenticate,
        getAuthCredentials,
        logoutCurrentUser,
        getAppHomeUrl
    }

    @Throws(JSONException::class)
    override fun execute(
        actionStr: String,
        jsVersion: JavaScriptPluginVersion,
        args: JSONArray,
        callbackContext: CallbackContext
    ): Boolean {
        // Not running plugin actions on the main thread.
        cordova.threadPool.execute {
            // Figure out action.
            try {
                val action = Action.valueOf(actionStr)
                when (action) {
                    Action.authenticate -> authenticate(callbackContext)
                    Action.getAuthCredentials -> getAuthCredentials(callbackContext)
                    Action.logoutCurrentUser -> logoutCurrentUser(callbackContext)
                    Action.getAppHomeUrl -> getAppHomeUrl(callbackContext)
                }
            } catch (e: Exception) {
                SalesforceHybridLogger.w(TAG, "execute: ${e.message}", e)
                callbackContext.error(e.message)
            }
        }
        return true
    }

    /**
     * Native implementation for "authenticate" action
     * @param callbackContext Used when calling back into Javascript.
     * @throws JSONException
     */
    @Throws(JSONException::class)
    protected fun authenticate(callbackContext: CallbackContext) {
        SalesforceHybridLogger.i(TAG, "authenticate called")
        (cordova.activity as SalesforceDroidGapActivity).authenticate(callbackContext)

        // Done.
        val noop = PluginResult(PluginResult.Status.NO_RESULT)
        noop.keepCallback = true
        callbackContext.sendPluginResult(noop)
    }

    /**
     * Native implementation for "getAuthCredentials" action.
     * @param callbackContext Used when calling back into Javascript.
     * @throws JSONException
     */
    @Throws(JSONException::class)
    protected fun getAuthCredentials(callbackContext: CallbackContext) {
        SalesforceHybridLogger.i(TAG, "getAuthCredentials called")
        (cordova.activity as SalesforceDroidGapActivity).getAuthCredentials(callbackContext)
    }

    /**
     * Native implementation for getAppHomeUrl
     * @param callbackContext Used when calling back into Javascript.
     */
    protected fun getAppHomeUrl(callbackContext: CallbackContext) {
        SalesforceHybridLogger.i(TAG, "getAppHomeUrl called")
        callbackContext.success(SalesforceWebViewClientHelper.getAppHomeUrl(cordova.activity))
    }

    /**
     * Native implementation for "logout" action
     * @param callbackContext Used when calling back into Javascript.
     */
    protected fun logoutCurrentUser(callbackContext: CallbackContext) {
        SalesforceHybridLogger.i(TAG, "logoutCurrentUser called")
        (cordova.activity as SalesforceDroidGapActivity).logout(callbackContext)
    }

    companion object {
        private const val TAG = "SalesforceOAuthPlugin"
    }
}
