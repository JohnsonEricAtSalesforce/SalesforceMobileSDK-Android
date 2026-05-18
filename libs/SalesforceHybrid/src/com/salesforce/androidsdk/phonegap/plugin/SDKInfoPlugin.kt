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

import android.content.Context
import android.content.pm.PackageManager.NameNotFoundException
import android.content.res.Resources
import android.text.TextUtils
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.config.BootConfig
import com.salesforce.androidsdk.phonegap.util.SalesforceHybridLogger
import org.apache.cordova.CallbackContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException

/**
 * PhoneGap plugin for SDK info.
 */
class SDKInfoPlugin : ForcePlugin() {

    /**
     * Supported plugin actions that the client can take.
     */
    private enum class Action {
        getInfo,
        registerAppFeature,
        unregisterAppFeature
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
                Action.getInfo -> {
                    getInfo(args, callbackContext)
                    true
                }
                Action.registerAppFeature -> {
                    registerAppFeature(args, callbackContext)
                    true
                }
                Action.unregisterAppFeature -> {
                    unregisterAppFeature(args, callbackContext)
                    true
                }
            }
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    /**
     * Native implementation for "getInfo" action.
     * @param callbackContext Used when calling back into Javascript.
     * @throws JSONException
     */
    @Throws(JSONException::class)
    protected fun getInfo(args: JSONArray, callbackContext: CallbackContext) {
        SalesforceHybridLogger.i(TAG, "getInfo called")
        try {
            callbackContext.success(getSDKInfo(cordova.activity))
        } catch (e: NameNotFoundException) {
            callbackContext.error(e.message)
        }
    }

    /**
     * Native implementation for "registerAppFeature" action.
     * @param callbackContext Used when calling back into Javascript.
     * @throws JSONException
     */
    @Throws(JSONException::class)
    protected fun registerAppFeature(args: JSONArray, callbackContext: CallbackContext) {
        SalesforceHybridLogger.i(TAG, "registerAppFeature called")

        // Parse args.
        val arg0 = args.getJSONObject(0)
        if (arg0 != null) {
            val appFeatureCode = arg0.getString("feature")
            if (!TextUtils.isEmpty(appFeatureCode)) {
                SalesforceSDKManager.getInstance().registerUsedAppFeature(appFeatureCode)
            }
        }
        callbackContext.success()
    }

    /**
     * Native implementation for "unregisterAppFeature" action.
     * @param callbackContext Used when calling back into Javascript.
     * @throws JSONException
     */
    @Throws(JSONException::class)
    protected fun unregisterAppFeature(args: JSONArray, callbackContext: CallbackContext) {
        SalesforceHybridLogger.i(TAG, "unregisterAppFeature called")

        // Parse args.
        val arg0 = args.getJSONObject(0)
        if (arg0 != null) {
            val appFeatureCode = arg0.getString("feature")
            if (!TextUtils.isEmpty(appFeatureCode)) {
                SalesforceSDKManager.getInstance().unregisterUsedAppFeature(appFeatureCode)
            }
        }
        callbackContext.success()
    }

    companion object {
        // Keys in sdk info map
        private const val SDK_VERSION = "sdkVersion"
        private const val APP_NAME = "appName"
        private const val APP_VERSION = "appVersion"
        private const val FORCE_PLUGINS_AVAILABLE = "forcePluginsAvailable"
        private const val BOOT_CONFIG = "bootConfig"
        private const val TAG = "SDKInfoPlugin"

        // Cached
        private var forcePlugins: List<String>? = null

        /**
         * @return sdk info as JSONObject
         * @throws NameNotFoundException
         * @throws JSONException
         */
        @JvmStatic
        @Throws(NameNotFoundException::class, JSONException::class)
        fun getSDKInfo(ctx: Context): JSONObject {
            var appName = ""
            try {
                val packageInfo = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
                appName = ctx.getString(packageInfo.applicationInfo!!.labelRes)
            } catch (nfe: Resources.NotFoundException) {
                // A test harness such as Gradle does NOT have an application name.
                SalesforceHybridLogger.w(TAG, "getSDKInfo failed", nfe)
            }
            val data = JSONObject()
            data.put(SDK_VERSION, SalesforceSDKManager.SDK_VERSION)
            data.put(APP_NAME, appName)
            data.put(APP_VERSION, SalesforceSDKManager.getInstance().appVersion)
            data.put(FORCE_PLUGINS_AVAILABLE, JSONArray(getForcePlugins(ctx)))
            data.put(BOOT_CONFIG, BootConfig.getBootConfig(ctx).asJSON())
            return data
        }

        /**
         * @param ctx
         * @return list of force plugins (read from XML the first time, and stored in field afterwards)
         */
        @JvmStatic
        fun getForcePlugins(ctx: Context): List<String> {
            if (forcePlugins == null) {
                forcePlugins = getForcePluginsFromXML(ctx)
            }
            return forcePlugins!!
        }

        /**
         * @param ctx
         * @return list of force plugins (read from XML)
         */
        @JvmStatic
        fun getForcePluginsFromXML(ctx: Context): List<String> {
            val services = mutableListOf<String>()
            var id = ctx.resources.getIdentifier("config", "xml", ctx.packageName)
            if (id == 0) {
                id = ctx.resources.getIdentifier("plugins", "xml", ctx.packageName)
            }
            if (id != 0) {
                val xml = ctx.resources.getXml(id)
                var eventType = -1
                while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                    if (eventType == org.xmlpull.v1.XmlPullParser.START_TAG && xml.name == "feature") {
                        val service = xml.getAttributeValue(null, "name")
                        if (service.startsWith("com.salesforce.")) {
                            services.add(service)
                        }
                    }
                    try {
                        eventType = xml.next()
                    } catch (e: XmlPullParserException) {
                        SalesforceHybridLogger.w(TAG, "getForcePluginsFromXML failed", e)
                    } catch (e: IOException) {
                        SalesforceHybridLogger.w(TAG, "getForcePluginsFromXML failed", e)
                    }
                }
            }
            return services
        }
    }
}
