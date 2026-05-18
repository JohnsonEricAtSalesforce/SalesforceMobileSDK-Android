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
import android.content.RestrictionsManager
import android.os.Bundle
import com.salesforce.androidsdk.analytics.EventBuilderHelper
import com.salesforce.androidsdk.app.Features
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.util.SalesforceSDKLogger
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * Classes responsible for reading runtime configurations (from MDM provider).
 * For an example, see the ConfiguratorApp and ConfiguredApp sample applications.
 */
class RuntimeConfig internal constructor(ctx: Context) {

    enum class ConfigKey {
        // The keys here should match the key entries in 'app_restrictions.xml'.
        AppServiceHosts,
        AppServiceHostLabels,
        ManagedAppOAuthID,
        ManagedAppCallbackURL,
        RequireCertAuth,
        ManagedAppCertAlias,
        OnlyShowAuthorizedHosts,
        IDPAppPackageName
    }

    private val isManaged: Boolean
    private var configurations: Bundle?

    init {
        configurations = getRestrictions(ctx)
        isManaged = hasRestrictionsProvider(ctx)

        // Register MDM App Feature for user agent reporting.
        if (isManaged && configurations != null && !configurations!!.isEmpty) {
            SalesforceSDKManager.getInstance().registerUsedAppFeature(Features.FEATURE_MDM)
            if (getBoolean(ConfigKey.RequireCertAuth)) {
                SalesforceSDKManager.getInstance().registerUsedAppFeature(Features.FEATURE_CERT_AUTH)
            }
        }

        // Logs analytics event for MDM.
        val threadPool = Executors.newFixedThreadPool(1)
        threadPool.execute {
            val attributes = JSONObject()
            try {
                attributes.put("mdmIsActive", isManaged)
                if (configurations != null) {
                    val mdmValues = JSONObject()
                    val keys = configurations!!.keySet()
                    for (key in keys) {
                        mdmValues.put(key, JSONObject.wrap(configurations!!.get(key)))
                    }
                    attributes.put("mdmConfigs", mdmValues)
                }
            } catch (e: JSONException) {
                SalesforceSDKLogger.e(TAG, "Exception thrown while creating JSON", e)
            }
            EventBuilderHelper.createAndStoreEventSync(
                "mdmConfiguration", null, TAG, attributes
            )
        }
    }

    /**
     * Returns true if application is managed
     * @return boolean
     */
    fun isManagedApp(): Boolean {
        return isManaged
    }

    /**
     * Get string run time configuration
     * @param configKey key
     * @return string value
     */
    fun getString(configKey: ConfigKey): String? {
        return configurations?.getString(configKey.name)
    }

    /**
     * Get string array run time configuration
     * @param configKey key
     * @return string array value
     */
    fun getStringArray(configKey: ConfigKey): Array<String>? {
        return configurations?.getStringArray(configKey.name)
    }

    /**
     * Get string array run time configuration either stored as a string array or stored in
     * a string field as CSV
     * @param configKey key
     * @return string array value
     */
    fun getStringArrayStoredAsArrayOrCSV(configKey: ConfigKey): Array<String>? {
        var result = getStringArray(configKey)
        if (result != null) {
            return result
        }
        val csv = getString(configKey)
        if (csv != null) {
            result = csv.split(",").toTypedArray()
        }
        return result
    }

    /**
     * Get boolean run time configuration
     * @param configKey key
     * @return boolean value
     */
    fun getBoolean(configKey: ConfigKey): Boolean {
        return configurations != null && configurations!!.getBoolean(configKey.name)
    }

    @Throws(JSONException::class)
    private fun getJSONArray(configKey: ConfigKey): JSONArray? {
        val array = getStringArray(configKey)
        return if (array == null) null else JSONArray(array.toList())
    }

    /**
     * Get run time config as a JSONObject
     * @return JSONObject for run time config.
     */
    fun asJSON(): JSONObject {
        try {
            val jsonObject = JSONObject()
            jsonObject.put(ConfigKey.AppServiceHosts.name, getJSONArray(ConfigKey.AppServiceHosts))
            jsonObject.put(ConfigKey.AppServiceHostLabels.name, getJSONArray(ConfigKey.AppServiceHostLabels))
            jsonObject.put(ConfigKey.ManagedAppOAuthID.name, getString(ConfigKey.ManagedAppOAuthID))
            jsonObject.put(ConfigKey.ManagedAppCallbackURL.name, getJSONArray(ConfigKey.ManagedAppCallbackURL))
            jsonObject.put(ConfigKey.RequireCertAuth.name, getBoolean(ConfigKey.RequireCertAuth))
            jsonObject.put(ConfigKey.ManagedAppCertAlias.name, getString(ConfigKey.ManagedAppCertAlias))
            jsonObject.put(ConfigKey.OnlyShowAuthorizedHosts.name, getJSONArray(ConfigKey.OnlyShowAuthorizedHosts))
            jsonObject.put(ConfigKey.IDPAppPackageName.name, getString(ConfigKey.IDPAppPackageName))
            return jsonObject
        } catch (e: JSONException) {
            throw RuntimeException(e)
        }
    }

    internal fun getRestrictions(ctx: Context): Bundle {
        val restrictionsManager = ctx.getSystemService(Context.RESTRICTIONS_SERVICE) as RestrictionsManager
        return restrictionsManager.applicationRestrictions
    }

    /**
     * Test only: set the bundle restrictions
     */
    internal fun setRestrictions(restrictions: Bundle?) {
        this.configurations = restrictions
    }

    private fun hasRestrictionsProvider(ctx: Context): Boolean {
        val restrictionsManager = ctx.getSystemService(Context.RESTRICTIONS_SERVICE) as RestrictionsManager
        return restrictionsManager.hasRestrictionsProvider()
    }

    companion object {
        private const val TAG = "RuntimeConfig"

        @Volatile
        private var INSTANCE: RuntimeConfig? = null

        /**
         * Method to (build and) get the singleton instance.
         *
         * @param ctx Context.
         * @return RuntimeConfig instance.
         */
        @JvmStatic
        fun getRuntimeConfig(ctx: Context): RuntimeConfig {
            if (INSTANCE == null) {
                INSTANCE = RuntimeConfig(ctx)
            }
            return INSTANCE!!
        }
    }
}
