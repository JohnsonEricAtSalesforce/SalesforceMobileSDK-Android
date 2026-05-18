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
package com.salesforce.androidsdk.config

import android.content.Context
import android.content.res.Resources
import android.text.TextUtils
import com.salesforce.androidsdk.R
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.config.RuntimeConfig.ConfigKey
import com.salesforce.androidsdk.util.ResourceReaderHelper
import com.salesforce.androidsdk.util.SalesforceSDKLogger
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Class encapsulating the application configuration (consumer key, oauth scopes, refresh behavior).
 *
 * @author wmathurin
 */
open class BootConfig {

    private var configIsHybrid = false

    private var _remoteAccessConsumerKey: String? = null
    private var _oauthRedirectURI: String? = null
    private var _oauthScopes: Array<String>? = null
    private var _isLocal = false
    private var _startPage: String? = null
    private var _errorPage: String? = null
    private var _shouldAuthenticate = false
    private var _attemptOfflineLoad = false
    private var _unauthenticatedStartPage: String? = null

    /**
     * Returns the consumer key value specified for your remote access object or connected app.
     *
     * @return Consumer key value specified for your remote access object or connected app.
     */
    open val remoteAccessConsumerKey: String?
        get() = _remoteAccessConsumerKey

    /**
     * Returns the redirect URI value specified for your remote access object or connected app.
     *
     * @return Redirect URI value specified for your remote access object or connected app.
     */
    open val oauthRedirectURI: String?
        get() = _oauthRedirectURI

    /**
     * Returns the authorization/access scope(s) that the application needs to ask for at login.
     *
     * @return Authorization/access scope(s) that the application needs to ask for at login.
     */
    open val oauthScopes: Array<String>?
        get() = _oauthScopes

    /**
     * Returns if the start page is local or a VF page.
     *
     * @return True - if start page is in assets/www, False - if it's a VF page.
     */
    open val isLocal: Boolean
        get() = _isLocal

    /**
     * Returns the path to the start page (local or remote).
     *
     * @return Path to start page (local or remote).
     */
    open val startPage: String?
        get() = _startPage

    /**
     * Returns the path to the local error page.
     *
     * @return Path to local error page.
     */
    open val errorPage: String?
        get() = _errorPage

    /**
     * Returns the path to the optional unauthenticated start page.
     *
     * @return URL of the unauthenticated start page.
     */
    open val unauthenticatedStartPage: String?
        get() = _unauthenticatedStartPage

    /**
     * Returns whether the app should go through login flow the first time or not.
     *
     * @return True - if the app should go through login flow, False - otherwise.
     */
    open fun shouldAuthenticate(): Boolean = _shouldAuthenticate

    /**
     * Returns whether the app should attempt to load cached content when offline.
     *
     * @return True - if the app should attempt to load cached content, False - otherwise.
     */
    open fun attemptOfflineLoad(): Boolean = _attemptOfflineLoad

    /**
     * Use runtime configurations (from MDM provider) if any.
     *
     * @param ctx Context.
     */
    private fun readFromRuntimeConfig(ctx: Context) {
        val runtimeConfig = RuntimeConfig.getRuntimeConfig(ctx)
        val mdmRemoteAccessConsumeKey = runtimeConfig.getString(ConfigKey.ManagedAppOAuthID)
        val mdmOauthRedirectURI = runtimeConfig.getString(ConfigKey.ManagedAppCallbackURL)
        if (!TextUtils.isEmpty(mdmRemoteAccessConsumeKey)) {
            _remoteAccessConsumerKey = mdmRemoteAccessConsumeKey
        }
        if (!TextUtils.isEmpty(mdmOauthRedirectURI)) {
            _oauthRedirectURI = mdmOauthRedirectURI
        }
    }

    /**
     * @return boot config as JSONObject.
     */
    fun asJSON(): JSONObject {
        try {
            val config = JSONObject()
            config.put(REMOTE_ACCESS_CONSUMER_KEY, _remoteAccessConsumerKey)
            config.put(OAUTH_REDIRECT_URI, _oauthRedirectURI)
            if (_oauthScopes != null) {
                config.put(OAUTH_SCOPES, JSONArray(_oauthScopes!!.toList()))
            }
            config.put(IS_LOCAL, _isLocal)
            config.put(START_PAGE, _startPage)
            config.put(ERROR_PAGE, _errorPage)
            config.put(SHOULD_AUTHENTICATE, _shouldAuthenticate)
            config.put(ATTEMPT_OFFLINE_LOAD, _attemptOfflineLoad)
            config.put(UNAUTHENTICATED_START_PAGE, _unauthenticatedStartPage)
            return config
        } catch (e: JSONException) {
            throw RuntimeException(e)
        }
    }

    /**
     * Initializes this BootConfig object by reading the config from XML.
     *
     * @param ctx Context.
     */
    private fun readFromXML(ctx: Context) {
        val res = ctx.resources
        _remoteAccessConsumerKey = res.getString(R.string.remoteAccessConsumerKey)
        _oauthRedirectURI = res.getString(R.string.oauthRedirectURI)
        _oauthScopes = try {
            res.getStringArray(R.array.oauthScopes)
        } catch (e: Resources.NotFoundException) {
            // oauthScopes is optional, leave it as null
            null
        }
    }

    /**
     * Initializes this BootConfig object by parsing a JSON object.
     *
     * @param config JSON object representing boot config.
     */
    private fun parseBootConfig(config: JSONObject) {
        try {
            // Required fields.
            _remoteAccessConsumerKey = config.getString(REMOTE_ACCESS_CONSUMER_KEY)
            _oauthRedirectURI = config.getString(OAUTH_REDIRECT_URI)

            // Optional oauthScopes field.
            if (config.has(OAUTH_SCOPES)) {
                val jsonScopes = config.getJSONArray(OAUTH_SCOPES)
                _oauthScopes = Array(jsonScopes.length()) { i ->
                    jsonScopes.getString(i)
                }
            } else {
                _oauthScopes = null
            }

            _isLocal = config.getBoolean(IS_LOCAL)
            _startPage = config.getString(START_PAGE)
            _errorPage = config.getString(ERROR_PAGE)

            // Optional fields.
            _shouldAuthenticate = config.optBoolean(SHOULD_AUTHENTICATE, DEFAULT_SHOULD_AUTHENTICATE)
            _attemptOfflineLoad = config.optBoolean(ATTEMPT_OFFLINE_LOAD, DEFAULT_ATTEMPT_OFFLINE_LOAD)
            _unauthenticatedStartPage = config.optString(UNAUTHENTICATED_START_PAGE)
        } catch (e: JSONException) {
            throw BootConfigException("Failed to parse $HYBRID_BOOTCONFIG_PATH", e)
        }
    }

    /**
     * Exception thrown for all bootconfig parsing errors.
     */
    class BootConfigException : RuntimeException {
        constructor(msg: String) : super(msg)
        constructor(msg: String, cause: Throwable) : super(msg, cause)

        companion object {
            private const val serialVersionUID = 1L
        }
    }

    companion object {
        private const val TAG = "BootConfig"

        // We expect a assets/www/bootconfig.json file to be provided by hybrid apps.
        private val HYBRID_BOOTCONFIG_PATH = "www" +
                System.getProperty("file.separator") + "bootconfig.json"

        // bootconfig.json should contain a map with the following keys.
        private const val REMOTE_ACCESS_CONSUMER_KEY = "remoteAccessConsumerKey"
        private const val OAUTH_REDIRECT_URI = "oauthRedirectURI"
        private const val OAUTH_SCOPES = "oauthScopes"
        private const val IS_LOCAL = "isLocal"
        private const val START_PAGE = "startPage"
        private const val ERROR_PAGE = "errorPage"
        private const val SHOULD_AUTHENTICATE = "shouldAuthenticate"
        private const val ATTEMPT_OFFLINE_LOAD = "attemptOfflineLoad"
        private const val UNAUTHENTICATED_START_PAGE = "unauthenticatedStartPage"

        // Default for optional configs.
        private const val DEFAULT_SHOULD_AUTHENTICATE = true
        private const val DEFAULT_ATTEMPT_OFFLINE_LOAD = true

        @JvmStatic
        private var INSTANCE: BootConfig? = null

        /**
         * Method to (build and) get the singleton instance.
         *
         * @param ctx Context.
         * @return BootConfig instance.
         */
        @JvmStatic
        fun getBootConfig(ctx: Context): BootConfig {
            if (INSTANCE == null) {
                if (SalesforceSDKManager.getInstance().isHybrid) {
                    INSTANCE = getHybridBootConfig(ctx, HYBRID_BOOTCONFIG_PATH)
                } else {
                    INSTANCE = BootConfig().also { it.readFromXML(ctx) }
                }
                INSTANCE!!.readFromRuntimeConfig(ctx)
            }
            return INSTANCE!!
        }

        /**
         * Gets a hybrid boot config instance from its JSON configuration file.
         *
         * @param ctx The context used to stage the JSON configuration file.
         * @param assetFilePath The relative path to the file, from the assets/ folder of the context.
         * @return A BootConfig representing the hybrid boot config object.
         */
        @JvmStatic
        internal fun getHybridBootConfig(ctx: Context, assetFilePath: String): BootConfig {
            val hybridBootConfig = BootConfig()
            hybridBootConfig.configIsHybrid = true
            val bootConfigJsonObj = readFromJSON(ctx, assetFilePath)
            hybridBootConfig.parseBootConfig(bootConfigJsonObj)
            return hybridBootConfig
        }

        /**
         * Gets a native boot config instance from XML resources.
         * Package-private for testing purposes.
         *
         * @param ctx The context used to read XML resources.
         * @return A BootConfig representing the native boot config object.
         */
        @JvmStatic
        internal fun getNativeBootConfig(ctx: Context): BootConfig {
            val nativeBootConfig = BootConfig()
            nativeBootConfig.readFromXML(ctx)
            return nativeBootConfig
        }

        /**
         * Validates a boot config's inputs against basic sanity tests.
         *
         * @param config The BootConfig instance to validate.
         * @throws BootConfigException If the boot config is invalid.
         */
        @JvmStatic
        fun validateBootConfig(config: BootConfig?) {
            if (config == null) {
                throw BootConfigException("No boot config provided.")
            }

            if (config.configIsHybrid) {
                // startPage must be a relative URL.
                if (isAbsoluteUrl(config.startPage)) {
                    throw BootConfigException("Start page should not be absolute URL.")
                }

                // unauthenticatedStartPage doesn't make sense in a local setup. Warn accordingly.
                if (config.isLocal && config.unauthenticatedStartPage != null) {
                    SalesforceSDKLogger.w(TAG, "$UNAUTHENTICATED_START_PAGE set for local app, but it will never be used.")
                }

                // unauthenticatedStartPage doesn't make sense in a remote setup with authentication. Warn accordingly.
                if (!config.isLocal && config.shouldAuthenticate() && config.unauthenticatedStartPage != null) {
                    SalesforceSDKLogger.w(TAG, "$UNAUTHENTICATED_START_PAGE set for remote app with authentication, but it will never be used.")
                }

                // Lack of unauthenticatedStartPage with remote deferred authentication is an error.
                if (!config.isLocal && !config.shouldAuthenticate() && TextUtils.isEmpty(config.unauthenticatedStartPage)) {
                    throw BootConfigException("$UNAUTHENTICATED_START_PAGE required for remote app with deferred authentication.")
                }

                // unauthenticatedStartPage, if present, must be an absolute URL.
                if (!TextUtils.isEmpty(config.unauthenticatedStartPage)
                    && !isAbsoluteUrl(config.unauthenticatedStartPage)
                ) {
                    throw BootConfigException("$UNAUTHENTICATED_START_PAGE should be absolute URL.")
                }
            }
        }

        /**
         * Convenience method to determine whether a configured startPage value is an absolute URL.
         *
         * @return true if startPage is an absolute URL, false otherwise.
         */
        @JvmStatic
        fun isAbsoluteUrl(urlString: String?): Boolean {
            return urlString != null && (urlString.startsWith("http://") || urlString.startsWith("https://"))
        }

        /**
         * Initializes this BootConfig object by reading the content of the JSON configuration file
         * at the specified path.
         *
         * @param ctx Context.
         * @param assetsFilePath The relative file path to the assets/ folder of the context.
         * @return A JSONObject parsed from the file.
         */
        private fun readFromJSON(ctx: Context, assetsFilePath: String): JSONObject {
            val jsonStr = ResourceReaderHelper.readAssetFile(ctx, assetsFilePath)
                ?: throw BootConfigException("Failed to open $assetsFilePath")
            return try {
                JSONObject(jsonStr)
            } catch (e: JSONException) {
                throw BootConfigException("Failed to parse $assetsFilePath", e)
            }
        }
    }
}
