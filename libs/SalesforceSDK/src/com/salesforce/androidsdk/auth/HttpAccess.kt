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
package com.salesforce.androidsdk.auth

import android.content.Context
import android.net.ConnectivityManager
import com.salesforce.androidsdk.app.SalesforceSDKManager
import okhttp3.ConnectionSpec
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.TlsVersion
import java.io.IOException
import java.util.Collections
import java.util.concurrent.TimeUnit

/**
 * Generic HTTP Access layer - used internally by [com.salesforce.androidsdk.rest.RestClient]
 * and [OAuth2]. This class watches network changes as well.
 */
open class HttpAccess(app: Context?, private var userAgent: String?) {

    // Connection manager.
    private val conMgr: ConnectivityManager?

    private var _okHttpClient: OkHttpClient? = null

    init {
        // Only null in tests.
        conMgr = if (app == null) {
            null
        } else {
            // Gets the connectivity manager and current network type.
            app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        }
    }

    /**
     * @return okHttpClient.Builder with appropriate connection spec
     * and user agent interceptor for an authenticated client.
     *
     * @deprecated To be removed in 14.0. Please use [createNewClientBuilder] instead.
     */
    @Deprecated("To be removed in 14.0. Please use createNewClientBuilder() instead.",
        replaceWith = ReplaceWith("createNewClientBuilder()"))
    fun getOkHttpClientBuilder(): OkHttpClient.Builder {
        return createNewClientBuilder()
    }

    /**
     * @return okHttpClient.Builder with appropriate connection spec
     * and user agent interceptor for an unauthenticated client.
     *
     * @deprecated To be removed in 14.0. Please use [createNewClientBuilder] instead.
     */
    @Deprecated("To be removed in 14.0. Please use createNewClientBuilder() instead.",
        replaceWith = ReplaceWith("createNewClientBuilder()"))
    fun getUnauthenticatedOkHttpBuilder(): OkHttpClient.Builder {
        return createNewClientBuilder()
    }

    /**
     * Creates a new OkHttp Client Builder with appropriate connection spec
     * and user agent interceptor.
     *
     * @return the okHttpClient.Builder.
     */
    fun createNewClientBuilder(): OkHttpClient.Builder {
        val connectionSpec = ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
            .tlsVersions(TlsVersion.TLS_1_2)
            .build()
        return OkHttpClient.Builder()
            .connectionSpecs(Collections.singletonList(connectionSpec))
            .connectTimeout(CONNECT_TIMEOUT.toLong(), TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT.toLong(), TimeUnit.SECONDS)
            .addNetworkInterceptor(UserAgentInterceptor())
    }

    /**
     * Returns okHttpClient tied to this HttpAccess - builds one if needed.
     */
    val okHttpClient: OkHttpClient
        @Synchronized
        get() {
            if (_okHttpClient == null) {
                _okHttpClient = createNewClientBuilder().build()
            }
            return _okHttpClient!!
        }

    /**
     * Returns the status of network connectivity.
     *
     * @return True - if network connectivity is available, False - otherwise.
     */
    @Synchronized
    fun hasNetwork(): Boolean {
        var isConnected = true
        if (conMgr != null) {
            @Suppress("DEPRECATION")
            val activeInfo = conMgr.activeNetworkInfo
            if (activeInfo == null || !activeInfo.isConnected) {
                isConnected = false
            }
        }
        return isConnected
    }

    /**
     * Returns the current user agent.
     *
     * @return User agent.
     */
    fun getUserAgent(): String? {
        return userAgent
    }

    /**
     * Exception thrown if the device is offline, during an attempted HTTP call.
     */
    class NoNetworkException(msg: String) : IOException(msg) {
        companion object {
            private const val serialVersionUID = 1L
        }
    }

    /**
     * Interceptor that adds user agent header.
     */
    class UserAgentInterceptor : Interceptor {

        private var userAgent: String? = null

        /**
         * Use this constructor to have the user agent computed for each call.
         */
        constructor()

        constructor(userAgent: String?) {
            this.userAgent = userAgent
        }

        @Throws(IOException::class)
        override fun intercept(chain: Interceptor.Chain): Response {
            val originalRequest = chain.request()
            val requestWithUserAgent = originalRequest.newBuilder()
                .header(USER_AGENT, userAgent ?: SalesforceSDKManager.getInstance().userAgent)
                .build()
            return chain.proceed(requestWithUserAgent)
        }
    }

    companion object {
        // Timeouts.
        const val CONNECT_TIMEOUT = 60
        const val READ_TIMEOUT = 20

        // User agent header name.
        private const val USER_AGENT = "User-Agent"

        // Singleton instance.
        @JvmField
        var DEFAULT: HttpAccess? = null

        /**
         * Initializes HttpAccess. Should be called from the application.
         */
        @JvmStatic
        @Synchronized
        fun init(app: Context) {
            if (DEFAULT == null) {
                DEFAULT = HttpAccess(app, null /* user agent will be calculated at request time */)
            }
        }
    }
}
