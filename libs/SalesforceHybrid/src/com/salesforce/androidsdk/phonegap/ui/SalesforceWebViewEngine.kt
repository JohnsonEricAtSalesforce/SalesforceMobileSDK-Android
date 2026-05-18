/*
 * Copyright (c) 2015-present, salesforce.com, inc.
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
import org.apache.cordova.CordovaInterface
import org.apache.cordova.CordovaPreferences
import org.apache.cordova.CordovaResourceApi
import org.apache.cordova.CordovaWebView
import org.apache.cordova.CordovaWebViewEngine
import org.apache.cordova.NativeToJsMessageQueue
import org.apache.cordova.PluginManager
import org.apache.cordova.engine.SystemWebView
import org.apache.cordova.engine.SystemWebViewEngine

/**
 * Salesforce specific implementation of Cordova's SystemWebViewEngine.
 *
 * @author bhariharan
 */
open class SalesforceWebViewEngine : SystemWebViewEngine {

    /**
     * Used when created via reflection.
     *
     * @param context Context.
     * @param preferences Preferences.
     */
    constructor(context: Context, preferences: CordovaPreferences) : super(SalesforceWebView(context))

    constructor(webView: SystemWebView) : super(webView, null)

    constructor(webView: SystemWebView, preferences: CordovaPreferences?) : super(webView, preferences)

    override fun init(
        parentWebView: CordovaWebView,
        cordova: CordovaInterface,
        client: CordovaWebViewEngine.Client,
        resourceApi: CordovaResourceApi,
        pluginManager: PluginManager,
        nativeToJsMessageQueue: NativeToJsMessageQueue
    ) {
        super.init(parentWebView, cordova, client, resourceApi, pluginManager, nativeToJsMessageQueue)
        if (webView != null) {
            (webView as SalesforceWebView).setWebViewClient(this)
        }
    }

    /**
     * Returns the Cordova interface being used.
     *
     * @return CordovaInterface instance.
     */
    val cordovaInterface: CordovaInterface?
        get() = cordova
}
