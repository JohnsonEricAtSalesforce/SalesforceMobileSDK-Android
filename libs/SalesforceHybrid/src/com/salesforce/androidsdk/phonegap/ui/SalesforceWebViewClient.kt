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
package com.salesforce.androidsdk.phonegap.ui

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import com.salesforce.androidsdk.app.Features
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.phonegap.util.SalesforceHybridLogger
import org.apache.cordova.CordovaWebView
import org.apache.cordova.engine.SystemWebView
import org.apache.cordova.engine.SystemWebViewClient
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets

open class SalesforceWebViewClient(parentEngine: SalesforceWebViewEngine) : SystemWebViewClient(parentEngine) {

    // The first non-reserved URL that's loaded will be considered the app's "home page", for caching purposes.
    protected var foundHomeUrl = false
    protected var ctx: Context? = null
    protected var cordovaWebView: CordovaWebView? = null

    init {
        cordovaWebView = parentEngine.cordovaWebView
        val cordova = parentEngine.cordovaInterface
        if (cordova != null) {
            ctx = cordova.activity
        }
        val webView = parentEngine.view as? SystemWebView
        val uaStr = SalesforceSDKManager.getInstance().userAgent
        if (webView != null) {
            val webSettings = webView.settings

            // Setting custom user agent and a bunch of other settings.
            val origUserAgent = webSettings.userAgentString
            val extendedUserAgentString = "$uaStr Hybrid ${origUserAgent ?: ""}"
            webSettings.userAgentString = extendedUserAgentString

            webSettings.domStorageEnabled = true
            webSettings.allowFileAccess = true
            webSettings.cacheMode = WebSettings.LOAD_DEFAULT
        }
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        return if (SalesforceWebViewClientHelper.shouldOverrideUrlLoading(ctx, view, url)) {
            true
        } else {
            super.shouldOverrideUrlLoading(view, request)
        }
    }

    override fun onPageFinished(view: WebView, url: String) {
        if (!foundHomeUrl && SalesforceWebViewClientHelper.onHomePage(SalesforceSDKManager.getInstance().appContext, view, url)) {
            foundHomeUrl = true
        }
        super.onPageFinished(view, url)
    }

    override fun shouldInterceptRequest(view: WebView, url: String): WebResourceResponse? {
        val response = super.shouldInterceptRequest(view, url)

        // Already intercepted (e.g. if url is not whitelisted).
        if (response != null) {
            return response
        }

        // Not a localhost request.
        val origUri = Uri.parse(url)
        val host = origUri.host
        if (host == null || host != "localhost") {
            return null
        }

        // Localhost request.
        SalesforceSDKManager.getInstance().registerUsedAppFeature(Features.FEATURE_LOCALHOST)
        return try {
            val localPath = WWW_DIR + origUri.path

            // Trying to access file outside assets/www.
            if (!isFileUnder(localPath)) {
                throw IOException("Trying to access file outside assets/www")
            } else {
                val localUri = Uri.parse("file://$localPath")
                val resourceApi = cordovaWebView!!.resourceApi
                val result = resourceApi.openForRead(localUri, true)
                SalesforceHybridLogger.i(TAG, "Loading local file: $localUri")
                WebResourceResponse(result.mimeType, StandardCharsets.UTF_8.name(), result.inputStream)
            }
        } catch (e: IOException) {
            SalesforceHybridLogger.e(TAG, "Invalid localhost URL: $url", e)
            WebResourceResponse("text/plain", StandardCharsets.UTF_8.name(), null)
        }
    }

    @Throws(IOException::class)
    private fun isFileUnder(filePath: String): Boolean {
        val file = File(filePath)
        val dir = File(WWW_DIR)
        return file.canonicalPath.indexOf(dir.canonicalPath) == 0
    }

    companion object {
        const val WWW_DIR = "/android_asset/www"
        private const val TAG = "SalesforceWebViewClient"
    }
}
