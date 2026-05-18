/*
 * Copyright (c) 2018-present, salesforce.com, inc.
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
package com.salesforce.androidsdk.util

import android.content.Intent
import android.text.TextUtils
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.HttpAccess
import com.salesforce.androidsdk.rest.RestResponse
import okhttp3.Request
import org.json.JSONObject

/**
 * This class has utility methods that help with authentication related functionality.
 *
 * @author bhariharan
 */
object AuthConfigUtil {

    const val AUTH_CONFIG_COMPLETE_INTENT_ACTION = "com.salesforce.AUTH_CONFIG_COMPLETE"

    const val WAS_REQUEST_SUCCESSFUL_EXTRA = "com.salesforce.WAS_REQUEST_SUCCESSFUL"

    private const val FORWARD_SLASH = "/"
    private const val MY_DOMAIN_AUTH_CONFIG_ENDPOINT = "/.well-known/auth-configuration"
    private const val TAG = "AuthConfigUtil"

    /**
     * Returns the auth config associated with a my domain login endpoint. This call
     * should be made from a background thread since it makes a network request.
     *
     * @param loginUrl Login URL.
     * @return Auth config.
     */
    @JvmStatic
    fun getMyDomainAuthConfig(loginUrl: String?): MyDomainAuthConfig? {
        return getMyDomainAuthConfig(null, loginUrl)
    }

    /**
     * Returns the auth config associated with a my domain login endpoint. This call
     * should be made from a background thread since it makes a network request.
     *
     * @param httpAccess The HTTP access to use for API integration.  Defaults
     * to null to use the default HTTP access.  This parameter is intended for
     * testing purposes only and should not be used in release builds.
     * @param loginUrl Login URL.
     * @return Auth config.
     */
    @JvmStatic
    fun getMyDomainAuthConfig(httpAccess: HttpAccess?, loginUrl: String?): MyDomainAuthConfig? {
        if (TextUtils.isEmpty(loginUrl)) {
            return null
        }
        var effectiveLoginUrl = loginUrl!!
        var authConfig: MyDomainAuthConfig? = null
        if (effectiveLoginUrl.endsWith(FORWARD_SLASH)) {
            effectiveLoginUrl = effectiveLoginUrl.substring(0, effectiveLoginUrl.length - 1)
        }
        val authConfigUrl = effectiveLoginUrl + MY_DOMAIN_AUTH_CONFIG_ENDPOINT
        val request = Request.Builder().url(authConfigUrl).get().build()
        try {
            val httpAccessResolved = httpAccess ?: HttpAccess.DEFAULT!!
            val response = httpAccessResolved.okHttpClient.newCall(request).execute()
            if (response.isSuccessful) {
                authConfig = MyDomainAuthConfig(RestResponse(response).asJSONObject())
            }
        } catch (e: Exception) {
            SalesforceSDKLogger.e(TAG, "Auth config request was not successful", e)
        }
        val intent = Intent(AUTH_CONFIG_COMPLETE_INTENT_ACTION)
        // Android 14 requires non-exported receiver to be invoked with explicit intents
        // See https://developer.android.com/about/versions/14/behavior-changes-14#safer-intents
        intent.setPackage(SalesforceSDKManager.getInstance().appContext.packageName)
        intent.putExtra(WAS_REQUEST_SUCCESSFUL_EXTRA, authConfig != null)
        SalesforceSDKManager.getInstance().appContext.sendBroadcast(intent)
        return authConfig
    }

    /**
     * This class represents my domain auth config.
     *
     * @author bhariharan
     */
    class MyDomainAuthConfig(val authConfig: JSONObject?) {

        var isBrowserLoginEnabled: Boolean = false
            private set
        var isShareBrowserSessionEnabled: Boolean = false
            private set
        var ssoUrls: List<String>? = null
            private set
        var loginPageUrl: String? = null
            private set

        init {
            var ssoUrlsList = ArrayList<String>()
            if (authConfig != null) {
                val mobileSDK = authConfig.optJSONObject(MOBILE_SDK_KEY)
                if (mobileSDK != null) {
                    isBrowserLoginEnabled = mobileSDK.optBoolean(USE_NATIVE_BROWSER_KEY)
                    isShareBrowserSessionEnabled = mobileSDK.optBoolean(SHARE_BROWSER_SESSION_KEY)
                }

                // Parses SAML provider list and adds it to the list of SSO URLs.
                val samlProviders = authConfig.optJSONArray(SAML_PROVIDERS_KEY)
                if (samlProviders != null && samlProviders.length() > 0) {
                    for (i in 0 until samlProviders.length()) {
                        val provider = samlProviders.optJSONObject(i)
                        if (provider != null) {
                            val ssoUrl = provider.optString(SSO_URL_KEY)
                            if (!TextUtils.isEmpty(ssoUrl)) {
                                ssoUrlsList.add(ssoUrl)
                            }
                        }
                    }
                }

                // Parses auth provider list and adds it to the list of SSO URLs.
                val authProviders = authConfig.optJSONArray(AUTH_PROVIDERS_KEY)
                if (authProviders != null && authProviders.length() > 0) {
                    for (i in 0 until authProviders.length()) {
                        val provider = authProviders.optJSONObject(i)
                        if (provider != null) {
                            val ssoUrl = provider.optString(SSO_URL_KEY)
                            if (!TextUtils.isEmpty(ssoUrl)) {
                                ssoUrlsList.add(ssoUrl)
                            }
                        }
                    }
                }
                ssoUrls = if (ssoUrlsList.size > 0) ssoUrlsList else null
                val loginPageConfig = authConfig.optJSONObject(LOGIN_PAGE_KEY)
                if (loginPageConfig != null) {
                    loginPageUrl = loginPageConfig.optString(LOGIN_PAGE_URL_KEY)
                }
            }
        }

        companion object {
            private const val MOBILE_SDK_KEY = "MobileSDK"
            private const val USE_NATIVE_BROWSER_KEY = "UseAndroidNativeBrowserForAuthentication"
            private const val SHARE_BROWSER_SESSION_KEY = "shareBrowserSessionAndroid"
            private const val SAML_PROVIDERS_KEY = "SamlProviders"
            private const val AUTH_PROVIDERS_KEY = "AuthProviders"
            private const val SSO_URL_KEY = "SsoUrl"
            private const val LOGIN_PAGE_KEY = "LoginPage"
            private const val LOGIN_PAGE_URL_KEY = "LoginPageUrl"
        }
    }
}
