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
package com.salesforce.androidsdk.phonegap.ui

import android.content.Context
import android.net.Uri
import android.text.TextUtils
import android.webkit.WebView
import com.salesforce.androidsdk.config.BootConfig
import com.salesforce.androidsdk.util.AuthConfigUtil
import com.salesforce.androidsdk.util.EventsObservable
import com.salesforce.androidsdk.util.EventsObservable.EventType
import com.salesforce.androidsdk.util.UriFragmentParser
import java.io.File
import java.net.HttpURLConnection
import java.util.Locale

/**
 * Helper class for SalesforceWebViewClient.
 */
object SalesforceWebViewClientHelper {

    private const val TAG = "SalesforceWebViewClientHelper"
    private const val SFDC_WEB_VIEW_CLIENT_SETTINGS = "sfdc_gapviewclient"
    private const val APP_HOME_URL_PROP_KEY = "app_home_url"
    private const val HYBRID_SESSION_REDIRECT = "/services/identity/mobileauthredirect"
    private const val FRONTDOOR = "frontdoor.jsp"
    private const val START_URL_PARAM = "startURL"
    private const val RET_URL_PARAM = "retURL"
    private const val EC_PARAM = "ec"
    private const val QUESTION_MARK = "?"

    // Full and partial URLs to exclude from consideration when determining the home page URL.
    private val RESERVED_URL_PATTERNS = listOf("/secur/frontdoor.jsp", "/secur/contentDoor")

    /**
     * To be called from shouldOverrideUrlLoading.
     *
     * @param ctx           Context.
     * @param view          The webview initiating the callback.
     * @param url           The url of the page.
     * @return              True if url loading should be overridden, false otherwise.
     */
    @JvmStatic
    fun shouldOverrideUrlLoading(ctx: Context?, view: WebView, url: String): Boolean {
        val startURL = isLoginRedirect(ctx, url)
        if (startURL != null && ctx is SalesforceDroidGapActivity) {
            ctx.refresh(startURL)
            return true
        }
        return false
    }

    /**
     * To be called from onPageFinished.
     * Return true if we have arrived on the actual home page and false otherwise.
     *
     * @param ctx           Context.
     * @param view          The webview initiating the callback.
     * @param url           The url of the page.
     */
    @JvmStatic
    fun onHomePage(ctx: Context, view: WebView, url: String): Boolean {
        // The first URL that's loaded that's not one of the URLs used in the bootstrap process will
        // be considered the "app home URL", which can be loaded directly in the event that the app is offline.
        if (!isReservedUrl(url)) {
            val sp = ctx.getSharedPreferences(SFDC_WEB_VIEW_CLIENT_SETTINGS, Context.MODE_PRIVATE)
            val e = sp.edit()
            e.putString(APP_HOME_URL_PROP_KEY, url)
            e.commit()
            EventsObservable.get().notifyEvent(EventType.GapWebViewPageFinished, url)
            return true
        }
        return false
    }

    /**
     * @return app's home page
     */
    @JvmStatic
    fun getAppHomeUrl(ctx: Context): String? {
        val sp = ctx.getSharedPreferences(SFDC_WEB_VIEW_CLIENT_SETTINGS, Context.MODE_PRIVATE)
        return sp.getString(APP_HOME_URL_PROP_KEY, null)
    }

    /**
     * @param ctx   Context.
     * @return      true if there is a cached version of the app's home page
     */
    @JvmStatic
    fun hasCachedAppHome(ctx: Context): Boolean {
        val cachedAppHomeUrl = getAppHomeUrl(ctx)
        return cachedAppHomeUrl != null && File(cachedAppHomeUrl).exists()
    }

    private fun isReservedUrl(url: String?): Boolean {
        if (TextUtils.isEmpty(url)) {
            return false
        }
        for (reservedUrlPattern in RESERVED_URL_PATTERNS) {
            if (url!!.lowercase(Locale.US).contains(reservedUrlPattern.lowercase(Locale.US))) {
                return true
            }
        }
        return false
    }

    private fun isLoginRedirect(ctx: Context?, url: String): String? {
        val uri = Uri.parse(url)
        val params = UriFragmentParser.parse(uri)
        var retURL = params[RET_URL_PARAM]
        retURL = if (TextUtils.isEmpty(retURL)) params[START_URL_PARAM] else retURL
        if (TextUtils.isEmpty(retURL) || retURL!!.contains(FRONTDOOR)) {
            retURL = ctx?.let { BootConfig.getBootConfig(it).startPage }
        }
        if (isSessionExpirationRedirect(url) || isSamlLoginRedirect(ctx, url) || isVFPageRedirect(params)) {
            return retURL
        }
        return null
    }

    private fun isSessionExpirationRedirect(url: String?): Boolean {
        return url != null && url.contains(HYBRID_SESSION_REDIRECT)
    }

    private fun isSamlLoginRedirect(ctx: Context?, url: String): Boolean {
        if (ctx is SalesforceDroidGapActivity) {
            val authConfig = ctx.authConfig
            if (authConfig != null) {
                val loginPageUrl = authConfig.loginPageUrl
                if (loginPageUrl != null && url.contains(loginPageUrl)) {
                    return true
                }
                val ssoUrls = authConfig.ssoUrls
                if (!ssoUrls.isNullOrEmpty()) {
                    for (ssoUrl in ssoUrls) {
                        var trimmedSsoUrl = ssoUrl
                        val paramsIndex = trimmedSsoUrl.indexOf(QUESTION_MARK)
                        if (paramsIndex != -1) {
                            trimmedSsoUrl = trimmedSsoUrl.substring(0, paramsIndex)
                        }
                        if (url.contains(trimmedSsoUrl)) {
                            return true
                        }
                    }
                }
            }
        }
        return false
    }

    private fun isVFPageRedirect(params: Map<String, String>): Boolean {
        val ec = params[EC_PARAM]
        val ecInt = ec?.toIntOrNull() ?: -1
        return ecInt == HttpURLConnection.HTTP_MOVED_PERM || ecInt == HttpURLConnection.HTTP_MOVED_TEMP
    }
}
