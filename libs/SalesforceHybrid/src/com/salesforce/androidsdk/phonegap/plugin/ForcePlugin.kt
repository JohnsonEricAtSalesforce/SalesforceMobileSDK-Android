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
package com.salesforce.androidsdk.phonegap.plugin

import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.phonegap.util.SalesforceHybridLogger
import org.apache.cordova.CallbackContext
import org.apache.cordova.CordovaPlugin
import org.json.JSONArray
import org.json.JSONException

/**
 * Abstract super class for all Salesforce plugins
 */
abstract class ForcePlugin : CordovaPlugin() {

    /**
     * Executes the plugin request and returns PluginResult.
     *
     * @param action        The action to execute
     * @param args          JSONArray of arguments for the plugin (possibly starting with version)
     * @param callbackContext Used when calling back into Javascript.
     * @return              A PluginResult object with a status and message.
     * @throws              JSONException
     */
    @Throws(JSONException::class)
    override fun execute(action: String, args: JSONArray, callbackContext: CallbackContext): Boolean {
        // args is an array
        // when versioned, the first element is the string "pluginSDKVersion:xxx" where xxx is the version
        var processedArgs = args
        var jsVersionStr = ""
        if (args.length() > 0) {
            val firstArg = args.optString(0)
            if (firstArg.startsWith(VERSION_KEY)) {
                jsVersionStr = firstArg.substring(VERSION_KEY.length + 1)
                processedArgs = shift(args)
            }
        }

        val jsVersion = JavaScriptPluginVersion(jsVersionStr)
        SalesforceHybridLogger.i(TAG, "${javaClass.simpleName}.execute, action: $action, jsVersion: $jsVersion")
        if (jsVersion.isOlder()) {
            SalesforceHybridLogger.w(TAG, "${javaClass.simpleName}.execute is being called by js from older sdk, jsVersion: $jsVersion, nativeVersion: ${SalesforceSDKManager.SDK_VERSION}")
        } else if (jsVersion.isNewer()) {
            SalesforceHybridLogger.w(TAG, "${javaClass.simpleName}.execute is being called by js from newer sdk, jsVersion: $jsVersion, nativeVersion: ${SalesforceSDKManager.SDK_VERSION}")
        }
        return execute(action, jsVersion, processedArgs, callbackContext)
    }

    /**
     * @param args
     * @return copy of args JSONArray without the first element
     * @throws JSONException
     */
    @Throws(JSONException::class)
    private fun shift(args: JSONArray): JSONArray {
        val res = JSONArray()
        for (i in 1 until args.length()) {
            res.put(args.get(i))
        }
        return res
    }

    /**
     * Abstract method to concrete subclass need to implement
     * @param actionStr     The action to execute
     * @param jsVersion     The version targeted
     * @param args          JSONArray of arguments for the plugin.
     * @param callbackContext Used when calling back into Javascript.
     * @return              Whether the action was valid.
     * @throws JSONException
     */
    @Throws(JSONException::class)
    protected abstract fun execute(
        actionStr: String,
        jsVersion: JavaScriptPluginVersion,
        args: JSONArray,
        callbackContext: CallbackContext
    ): Boolean

    companion object {
        private const val VERSION_KEY = "pluginSDKVersion"
        private const val TAG = "ForcePlugin"
    }
}
