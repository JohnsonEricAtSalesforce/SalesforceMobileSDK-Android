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
package com.salesforce.samples.appconfigurator

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.os.Bundle

class AppConfiguratorState private constructor(ctx: Context) {

    // Copied from com.salesforce.androidsdk.config.RuntimeConfig
    enum class ConfigKey {
        AppServiceHosts,
        AppServiceHostLabels,
        ManagedAppOAuthID,
        ManagedAppCallbackURL,
        RequireCertAuth,
        ManagedAppCertAlias,
        OnlyShowAuthorizedHosts,
        IDPAppURLScheme
    }

    // State
    var loginServers: String
        private set
    var loginServersLabels: String
        private set
    var remoteAccessConsumerKey: String
        private set
    var oauthRedirectURI: String
        private set
    private var requireCertAuth: Boolean
    var certAlias: String?
        private set
    private var onlyShowAuthorizedHosts: Boolean
    var idpAppURLScheme: String?
        private set

    val targetApp: String
        get() = DEFAULT_TARGET_APP

    init {
        val prefs = ctx.getSharedPreferences(PREFS_KEY, Context.MODE_PRIVATE)
        loginServers = prefs.getString(ConfigKey.AppServiceHosts.name, DEFAULT_LOGIN_SERVERS)!!
        loginServersLabels = prefs.getString(ConfigKey.AppServiceHostLabels.name, DEFAULT_LOGIN_SERVERS_LABELS)!!
        remoteAccessConsumerKey = prefs.getString(ConfigKey.ManagedAppOAuthID.name, DEFAULT_REMOTE_ACCESS_CONSUMER_KEY)!!
        oauthRedirectURI = prefs.getString(ConfigKey.ManagedAppCallbackURL.name, DEFAULT_OAUTH_REDIRECT_URI)!!
        requireCertAuth = prefs.getBoolean(ConfigKey.RequireCertAuth.name, false)
        certAlias = prefs.getString(ConfigKey.ManagedAppCertAlias.name, null)
        onlyShowAuthorizedHosts = prefs.getBoolean(ConfigKey.OnlyShowAuthorizedHosts.name, false)
        idpAppURLScheme = prefs.getString(ConfigKey.IDPAppURLScheme.name, null)
    }

    fun requiresCertAuth(): Boolean = requireCertAuth

    fun shouldOnlyShowAuthorizedHosts(): Boolean = onlyShowAuthorizedHosts

    /**
     * Save configurations to preferences and as app restrictions on target app
     */
    fun saveConfigurations(
        ctx: Context,
        loginServers: String,
        loginServersLabels: String,
        remoteAccessConsumerKey: String,
        oauthRedirectURI: String,
        requireCertAuth: Boolean,
        certAlias: String,
        onlyShowAuthorizedHosts: Boolean,
        idpAppURLScheme: String
    ) {
        // Save to fields
        this.loginServers = loginServers
        this.loginServersLabels = loginServersLabels
        this.remoteAccessConsumerKey = remoteAccessConsumerKey
        this.oauthRedirectURI = oauthRedirectURI
        this.requireCertAuth = requireCertAuth
        this.certAlias = certAlias
        this.onlyShowAuthorizedHosts = onlyShowAuthorizedHosts
        this.idpAppURLScheme = idpAppURLScheme

        // Save to preferences
        ctx.getSharedPreferences(PREFS_KEY, Context.MODE_PRIVATE)
            .edit()
            .putString(ConfigKey.AppServiceHosts.name, loginServers)
            .putString(ConfigKey.AppServiceHostLabels.name, loginServersLabels)
            .putString(ConfigKey.ManagedAppOAuthID.name, remoteAccessConsumerKey)
            .putString(ConfigKey.ManagedAppCallbackURL.name, oauthRedirectURI)
            .putBoolean(ConfigKey.RequireCertAuth.name, requireCertAuth)
            .putString(ConfigKey.ManagedAppCertAlias.name, certAlias)
            .putBoolean(ConfigKey.OnlyShowAuthorizedHosts.name, onlyShowAuthorizedHosts)
            .putString(ConfigKey.IDPAppURLScheme.name, idpAppURLScheme)
            .apply()

        // Save to app restrictions on target app
        val devicePolicyManager =
            ctx.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val restrictions = Bundle()
        if (loginServers.isNotEmpty()) restrictions.putStringArray(ConfigKey.AppServiceHosts.name, loginServers.split(",").toTypedArray())
        if (loginServersLabels.isNotEmpty()) restrictions.putStringArray(ConfigKey.AppServiceHostLabels.name, loginServersLabels.split(",").toTypedArray())
        if (remoteAccessConsumerKey.isNotEmpty()) restrictions.putString(ConfigKey.ManagedAppOAuthID.name, remoteAccessConsumerKey)
        if (oauthRedirectURI.isNotEmpty()) restrictions.putString(ConfigKey.ManagedAppCallbackURL.name, oauthRedirectURI)
        restrictions.putBoolean(ConfigKey.RequireCertAuth.name, requireCertAuth)
        if (certAlias.isNotEmpty()) restrictions.putString(ConfigKey.ManagedAppCertAlias.name, certAlias)
        restrictions.putBoolean(ConfigKey.OnlyShowAuthorizedHosts.name, onlyShowAuthorizedHosts)
        if (idpAppURLScheme.isNotEmpty()) restrictions.putString(ConfigKey.IDPAppURLScheme.name, idpAppURLScheme)
        devicePolicyManager.setApplicationRestrictions(
            AppConfiguratorAdminReceiver.getComponentName(ctx),
            targetApp, restrictions
        )
    }

    companion object {
        // Prefs
        private const val PREFS_KEY = "AppConfiguratorPrefs"

        // Default values
        private const val DEFAULT_TARGET_APP = "com.salesforce.samples.configuredapp"
        private const val DEFAULT_LOGIN_SERVERS = "https://test.salesforce.com,https://login.salesforce.com"
        private const val DEFAULT_LOGIN_SERVERS_LABELS = "sandbox,production"
        private const val DEFAULT_REMOTE_ACCESS_CONSUMER_KEY = "__CONSUMER_KEY__"
        private const val DEFAULT_OAUTH_REDIRECT_URI = "__REDIRECT_URI__"

        // Singleton instance
        @Volatile
        private var INSTANCE: AppConfiguratorState? = null

        @Synchronized
        fun getInstance(ctx: Context): AppConfiguratorState {
            if (INSTANCE == null) {
                INSTANCE = AppConfiguratorState(ctx)
            }
            return INSTANCE!!
        }
    }
}
