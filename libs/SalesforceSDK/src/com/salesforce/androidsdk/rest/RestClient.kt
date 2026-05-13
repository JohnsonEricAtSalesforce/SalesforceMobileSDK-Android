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
package com.salesforce.androidsdk.rest

import androidx.annotation.VisibleForTesting
import com.salesforce.androidsdk.accounts.UserAccount
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.HttpAccess
import com.salesforce.androidsdk.auth.OAuth2
import com.salesforce.androidsdk.security.BiometricAuthenticationManager
import com.salesforce.androidsdk.util.SalesforceSDKLogger
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URISyntaxException

/**
 * RestClient allows you to send authenticated HTTP requests to a force.com server.
 */
class RestClient(
    internal val clientInfo: ClientInfo,
    authToken: String?,
    private val httpAccessor: HttpAccess,
    private val authTokenProvider: AuthTokenProvider?
) {

    @VisibleForTesting
    internal lateinit var oAuthRefreshInterceptor: OAuthRefreshInterceptor
    private lateinit var okHttpClientBuilder: OkHttpClient.Builder
    private lateinit var okHttpClient: OkHttpClient

    init {
        setOAuthRefreshInterceptor(authToken)
        setOkHttpClientBuilder()
        setOkHttpClient(null)
    }

    /**
     * AuthTokenProvider interface.
     * RestClient will call its authTokenProvider to refresh its authToken once it has expired.
     */
    interface AuthTokenProvider {
        fun getInstanceUrl(): String?
        fun getNewAuthToken(): String?
        fun getRefreshToken(): String?
        fun getLastRefreshTime(): Long
    }

    /**
     * AsyncRequestCallback interface.
     * Interface through which the result of an asynchronous request is handled.
     */
    interface AsyncRequestCallback {

        /**
         * NB: onSuccess runs on a network thread
         * If you are making your call from an activity and need to make UI changes
         * make sure to first consume the response and then call runOnUiThread
         *
         * result.consumeQuietly(); // consume before going back to main thread
         * runOnUiThread(new Runnable() {
         *         @Override
         *         public void run() { ... }
         *     });
         * @param request
         * @param response
         */
        fun onSuccess(request: RestRequest, response: RestResponse)

        /**
         * NB: onError runs on a network thread
         * If you are making your call from an activity and need to make UI changes
         * make sure to call runOnUiThread
         *
         * runOnUiThread(new Runnable() {
         *         @Override
         *         public void run() { ... }
         *     });
         * @param exception
         */
        fun onError(exception: Exception)
    }

    private fun getCacheKey(): String {
        return computeCacheKey(clientInfo.orgId, clientInfo.userId)
    }

    /**
     * Sets the OAuthRefreshInterceptor associated with this user account.
     */
    @Synchronized
    private fun setOAuthRefreshInterceptor(authToken: String?) {
        val cacheKey = getCacheKey()
        var oAuthRefreshInterceptor = OAUTH_REFRESH_INTERCEPTORS[cacheKey]

        // If none cached, create new one
        if (oAuthRefreshInterceptor == null) {
            oAuthRefreshInterceptor = OAuthRefreshInterceptor(clientInfo, authToken, authTokenProvider)
            OAUTH_REFRESH_INTERCEPTORS[cacheKey] = oAuthRefreshInterceptor
        }
        this.oAuthRefreshInterceptor = oAuthRefreshInterceptor
    }

    /**
     * Sets the OkHttpclient.Builder associated with this user account. The OkHttpclient.Builder
     * are cached in a map and reused as and when a user account
     * switch occurs, to prevent multiple threads being spawned unnecessarily.
     */
    @Synchronized
    private fun setOkHttpClientBuilder() {
        val cacheKey = getCacheKey()
        var okHttpClientBuilder = OK_CLIENT_BUILDERS[cacheKey]

        // If none cached, create new one
        if (okHttpClientBuilder == null) {
            okHttpClientBuilder = httpAccessor.createNewClientBuilder()
            if (cacheKey != "unauthenticated") {
                okHttpClientBuilder.addInterceptor(getOAuthRefreshInterceptor())
            }

            OK_CLIENT_BUILDERS[getCacheKey()] = okHttpClientBuilder
        }
        this.okHttpClientBuilder = okHttpClientBuilder
    }

    /**
     * Sets the OkHttpclient associated with this user account. The OkHttpclient
     * are cached in a map and reused as and when a user account
     * switch occurs, to prevent multiple threads being spawned unnecessarily.
     */
    @Synchronized
    fun setOkHttpClient(okHttpClient: OkHttpClient?) {
        var okHttpClient = okHttpClient
        val cacheKey = getCacheKey()

        // If a valid client passed in, caches it.
        if (okHttpClient != null) {
            OK_CLIENTS[cacheKey] = okHttpClient
        }
        okHttpClient = OK_CLIENTS[cacheKey]

        // If none cached, create new one
        if (okHttpClient == null) {
            okHttpClient = getOkHttpClientBuilder().build()
            OK_CLIENTS[cacheKey] = okHttpClient
        }
        this.okHttpClient = okHttpClient
    }

    /**
     * Set the client info. Used by clients to implement Login As
     * @param clientInfo The new client info to set
     */
    fun setClientInfo(clientInfo: ClientInfo) {
        getOAuthRefreshInterceptor().clientInfo = clientInfo
    }

    /**
     * @return credentials as JSONObject
     */
    val jsonCredentials: JSONObject
        get() {
            val clientInfo = getClientInfo()
            val data = mutableMapOf<String, String?>()
            data[ACCESS_TOKEN] = getAuthToken()
            data[REFRESH_TOKEN] = getRefreshToken()
            data[USER_ID] = clientInfo.userId
            data[ORG_ID] = clientInfo.orgId
            data[LOGIN_URL] = clientInfo.loginUrl.toString()
            data[IDENTITY_URL] = clientInfo.identityUrl.toString()
            data[INSTANCE_URL] = clientInfo.instanceUrl.toString()
            data[USER_AGENT] = SalesforceSDKManager.getInstance().userAgent
            data[COMMUNITY_ID] = clientInfo.communityId
            data[COMMUNITY_URL] = clientInfo.communityUrl
            return JSONObject(data as Map<*, *>)
        }

    override fun toString(): String {
        val sb = StringBuilder()
        sb.append("RestClient: {\n")
            .append(this.oAuthRefreshInterceptor.clientInfo.toString())
            .append("   timeSinceLastRefresh: ").append(oAuthRefreshInterceptor.getElapsedTimeSinceLastRefresh()).append("\n")
            .append("}\n")
        return sb.toString()
    }

    /**
     * @return The authToken for this RestClient.
     */
    @Synchronized
    fun getAuthToken(): String? {
        return oAuthRefreshInterceptor.getAuthToken()
    }

    /**
     * @return The refresh token, if available.
     */
    fun getRefreshToken(): String? {
        return oAuthRefreshInterceptor.getRefreshToken()
    }

    /**
     * @return The client info.
     */
    fun getClientInfo(): ClientInfo {
        return oAuthRefreshInterceptor.clientInfo
    }

    /**
     * @return underlying OAuthRefreshInterceptor
     */
    fun getOAuthRefreshInterceptor(): OAuthRefreshInterceptor {
        return oAuthRefreshInterceptor
    }

    /**
     * @return underlying OkHttpClient.Builder
     */
    fun getOkHttpClientBuilder(): OkHttpClient.Builder {
        return okHttpClientBuilder
    }

    /**
     * @return underlying OkHttpClient
     */
    fun getOkHttpClient(): OkHttpClient {
        return okHttpClient
    }

    /**
     * Builds an OK HTTP request from a REST request.
     *
     * @param restRequest The REST request.
     * @return The HTTP request
     */
    fun buildRequest(restRequest: RestRequest): Request? {
        val uri = oAuthRefreshInterceptor.clientInfo.resolveUrl(restRequest) ?: return null
        val url = uri.toString().toHttpUrl()
        val builder = Request.Builder()
            .url(url)
            .method(restRequest.method.toString(), restRequest.requestBody)

        // Adding additional headers
        val additionalHttpHeaders = restRequest.additionalHttpHeaders
        if (additionalHttpHeaders != null) {
            for ((key, value) in additionalHttpHeaders) {
                builder.addHeader(key, value)
            }
        }
        return builder.build()
    }

    /**
     * Returns a new web socket for the provided request and web socket listener.
     * @param request The request
     * @param listener The web socket listener
     * @return The web socket
     */
    fun newWebSocket(
        request: Request,
        listener: WebSocketListener
    ): WebSocket {
        return okHttpClient.newWebSocket(request, listener)
    }

    /**
     * Send the given restRequest and process the result asynchronously with the given callback.
     * Note: Intended to be used by code on the UI thread.
     * @param restRequest
     * @param callback
     * @return okHttp Call object (through which you can cancel the request or get the request back)
     */
    fun sendAsync(restRequest: RestRequest, callback: AsyncRequestCallback): Call {
        val request = buildRequest(restRequest)
        val call = okHttpClient.newCall(request!!)
        call.enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                callback.onError(e)
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                callback.onSuccess(restRequest, RestResponse(response))
            }
        })
        return call
    }

    /**
     * Send the given restRequest synchronously and return a RestResponse
     * Note: Cannot be used by code on the UI thread (use sendAsync instead).
     * @param restRequest
     * @return
     * @throws IOException
     */
    @Throws(IOException::class)
    fun sendSync(restRequest: RestRequest): RestResponse? {
        val request = buildRequest(restRequest) ?: return null
        val response = okHttpClient.newCall(request).execute()
        return RestResponse(response)
    }

    /**
     * Send the given restRequest synchronously and return a RestResponse
     * Note: Cannot be used by code on the UI thread (use sendAsync instead).
     * @param restRequest
     * @param interceptors Interceptor(s) to add to the network client before making the request
     * @return
     * @throws IOException
     */
    @Throws(IOException::class)
    fun sendSync(restRequest: RestRequest, vararg interceptors: Interceptor): RestResponse {
        val request = buildRequest(restRequest)

        // builder that shares the same connection pool, dispatcher, and configuration with the original client
        val clientBuilder = getOkHttpClient().newBuilder()
        for (interceptor in interceptors) {
            clientBuilder.addNetworkInterceptor(interceptor)
        }
        val response = clientBuilder.build().newCall(request!!).execute()
        return RestResponse(response)
    }

    /**
     * All immutable information for an authenticated client (e.g. username, org ID, etc.).
     *
     * TODO revisit this class - some of information is NOT immutable e.g. sids
     */
    open class ClientInfo(
        val instanceUrl: URI,
        val loginUrl: URI,
        val identityUrl: URI,
        val accountName: String?,
        val username: String?,
        val userId: String?,
        val orgId: String?,
        val communityId: String?,
        val communityUrl: String?,
        val firstName: String?,
        val lastName: String?,
        val displayName: String?,
        val email: String?,
        val photoUrl: String?,
        val thumbnailUrl: String?,
        val additionalOauthValues: Map<String, String>?,
        val lightningDomain: String?,
        val lightningSid: String?,
        val vfDomain: String?,
        val vfSid: String?,
        val contentDomain: String?,
        val contentSid: String?,
        val csrfToken: String?
    ) {

        /**
         * @return unique id built from user id and org id
         */
        open fun buildUniqueId(): String {
            return this.userId + this.orgId
        }

        override fun toString(): String {
            val sb = StringBuilder()
            sb.append("  ClientInfo: {\n")
                .append("     loginUrl: ").append(loginUrl.toString()).append("\n")
                .append("     identityUrl: ").append(identityUrl.toString()).append("\n")
                .append("     instanceUrl: ").append(instanceUrl.toString()).append("\n")
                .append("     accountName: ").append(accountName).append("\n")
                .append("     username: ").append(username).append("\n")
                .append("     userId: ").append(userId).append("\n")
                .append("     orgId: ").append(orgId).append("\n")
                .append("     communityId: ").append(communityId).append("\n")
                .append("     communityUrl: ").append(communityUrl).append("\n")
                .append("     firstName: ").append(firstName).append("\n")
                .append("     lastName: ").append(lastName).append("\n")
                .append("     displayName: ").append(displayName).append("\n")
                .append("     email: ").append(email).append("\n")
                .append("     photoUrl: ").append(photoUrl).append("\n")
                .append("     thumbnailUrl: ").append(thumbnailUrl).append("\n")
                .append("     lightningDomain: ").append(lightningDomain).append("\n")
                .append("     lightningSid: ").append(lightningSid).append("\n")
                .append("     vfDomain: ").append(vfDomain).append("\n")
                .append("     vfSid: ").append(vfSid).append("\n")
                .append("     contentDomain: ").append(contentDomain).append("\n")
                .append("     contentSid: ").append(contentSid).append("\n")
                .append("     csrfToken: ").append(csrfToken).append("\n")
                .append("     additionalOauthValues: ").append(additionalOauthValues).append("\n")
                .append("  }\n")
            return sb.toString()
        }

        /**
         * Returns a string representation of the instance URL. If this is a
         * community user, the community URL will be returned. If not, the
         * instance URL will be returned.
         *
         * @return Instance URL.
         */
        fun getInstanceUrlAsString(): String {
            if (communityUrl != null && "" != communityUrl.trim()) {
                return communityUrl
            }
            return instanceUrl.toString()
        }

        /**
         * Returns a URI representation of the instance URL. If this is a
         * community user, the community URL will be returned. If not, the
         * instance URL will be returned.
         *
         * @return Instance URL.
         */
        fun resolveInstanceUrl(): URI? {
            if (communityUrl != null && communityUrl.trim().isNotEmpty()) {
                var uri: URI? = null
                try {
                    uri = URI(communityUrl)
                } catch (e: URISyntaxException) {
                    SalesforceSDKLogger.e(TAG, "Exception thrown while parsing URL: $communityUrl", e)
                }
                return uri
            }
            return instanceUrl
        }

        /**
         * Resolves the given [RestRequest] to its URL.
         * @param request The Rest request to resolve.
         * @return The URI associated with the Rest request.
         */
        fun resolveUrl(request: RestRequest): URI? {
            return resolveUrl(request.path, request.endpoint)
        }

        /**
         * Resolves the given path against the community URL or the instance
         * URL, depending on whether the user is a community user or not.
         *
         * @param path Path.
         * @return Resolved URL.
         */
        open fun resolveUrl(path: String): URI? {
            return resolveUrl(path, RestRequest.RestEndpoint.INSTANCE)
        }

        /**
         * Resolves the given path against the community URL, login URL, or instance
         * URL.  If the user is a community user, the community URL will be used.  Otherwise,
         * the URL will be built from the
         * [RestRequest.RestEndpoint] parameter.
         * @param path     Path
         * @param endpoint The Rest endpoint of the URL.
         * @return Resolved URL.
         */
        fun resolveUrl(path: String, endpoint: RestRequest.RestEndpoint): URI? {
            var path = path
            var resolvedPathStr = path

            // Resolve URL only for a relative URL.
            if (!path.matches(Regex("[hH][tT][tT][pP][sS]?://.*"))) {
                val resolvedUrlBuilder = StringBuilder()
                if (communityUrl != null && communityUrl.trim().isNotEmpty()) {
                    resolvedUrlBuilder.append(communityUrl)
                } else if (endpoint == RestRequest.RestEndpoint.INSTANCE) {
                    resolvedUrlBuilder.append(instanceUrl.toString())
                } else if (endpoint == RestRequest.RestEndpoint.LOGIN) {
                    resolvedUrlBuilder.append(loginUrl.toString())
                }
                if (!resolvedUrlBuilder.toString().endsWith("/")) {
                    resolvedUrlBuilder.append("/")
                }
                if (path.startsWith("/")) {
                    path = path.substring(1)
                }
                resolvedUrlBuilder.append(path)
                resolvedPathStr = resolvedUrlBuilder.toString()
            }
            var uri: URI? = null
            try {
                uri = URI(resolvedPathStr)
            } catch (e: URISyntaxException) {
                SalesforceSDKLogger.e(TAG, "Exception thrown while parsing URL: $resolvedPathStr", e)
            }
            return uri
        }
    }

    /**
     * Use a unauthenticated client info when do not need authentication support (e.g.
     * if you are talking to non-salesforce servers)
     *
     * NB: Your RestRequest's path will need to be a complete URL
     */
    class UnauthenticatedClientInfo : ClientInfo(
        null!!, null!!, null!!, null, null,
        null, null, null, null, null,
        null, null, null, null, null,
        null, null, null, null,
        null, null, null, null
    ) {

        override fun buildUniqueId(): String {
            return NOUSER
        }

        override fun toString(): String {
            return javaClass.simpleName
        }

        override fun resolveUrl(path: String): URI? {
            var uri: URI? = null
            try {
                uri = URI(path)
            } catch (e: URISyntaxException) {
                SalesforceSDKLogger.e(TAG, "Exception thrown while parsing URL: $path", e)
            }
            return uri
        }

        companion object {
            const val NOUSER = "nouser"
        }
    }

    /**
     * Network interceptor that does oauth refresh and request retry when access token has expired.
     */
    class OAuthRefreshInterceptor(
        var clientInfo: ClientInfo,
        authToken: String?,
        private val authTokenProvider: AuthTokenProvider?
    ) : Interceptor {

        private var authToken: String? = authToken

        @Throws(IOException::class)
        private fun shouldRefresh(response: Response): Boolean {
            val responseCode = response.code

            // most calls return 401 if oauth access token is not valid
            val isNotAuthorized = responseCode == HttpURLConnection.HTTP_UNAUTHORIZED

            // service/oauth2 calls return 403 with Bad_OAuth_Token response if oauth access is not valid
            val hasBadOAuthToken = responseCode == HttpURLConnection.HTTP_FORBIDDEN &&
                    response.request.url.encodedPath.startsWith(RestRequest.SERVICES_OAUTH2) &&
                    (response.body != null && response.body!!.string() == "Bad_OAuth_Token")

            if (isNotAuthorized || hasBadOAuthToken) {
                SalesforceSDKLogger.d(TAG, "response request url: ${response.request.url}")
                SalesforceSDKLogger.d(TAG, "response code: ${response.code}")

                // Is biometric enabled and locked?
                val bioAuthManager = SalesforceSDKManager.getInstance().biometricAuthenticationManager as? BiometricAuthenticationManager
                return bioAuthManager == null || bioAuthManager.shouldAllowRefresh()
            } else {
                return false
            }
        }

        @Throws(IOException::class)
        override fun intercept(chain: Interceptor.Chain): Response {
            var request = chain.request()
            request = buildAuthenticatedRequest(request)
            var response = chain.proceed(request)

            /*
             * Standard access token expiry returns 401 as the error code.
             */
            if (shouldRefresh(response)) {
                SalesforceSDKLogger.d(TAG, "shouldRefresh() returned true")

                val curInstanceUrl = clientInfo.resolveInstanceUrl()
                if (curInstanceUrl != null) {
                    val currentInstanceUrl = curInstanceUrl.toString().toHttpUrl()
                    if (currentInstanceUrl != null) {

                        // Checks if the host of the request is the same as instance URL.
                        val isHostInstanceUrl = currentInstanceUrl.host == request.url.host
                        refreshAccessToken()
                        if (getAuthToken() != null) {
                            request = buildAuthenticatedRequest(request)

                            /*
                             * During instance migration, the instance URL could change. Hence, the host
                             * needs to be adjusted to replace the old instance URL with the new instance
                             * URL before replaying this request. However, this adjustment should be applied
                             * only if the host of the request was the old instance URL. This avoids
                             * accidental manipulation of the host for requests where the caller has
                             * passed in their own fully formed host URL that is not instance URL.
                             *
                             * We also need to cover the case where the host changes during refresh
                             * because the replayed request will fail.
                             */
                            val refreshInstanceUrl = clientInfo.resolveInstanceUrl()
                            val refreshUpdatedUrl = refreshInstanceUrl != null &&
                                    refreshInstanceUrl.host != request.url.host
                            if (isHostInstanceUrl && refreshUpdatedUrl) {
                                val updatedInstanceUrl = refreshInstanceUrl?.toString()?.toHttpUrl()
                                if (updatedInstanceUrl != null) {
                                    request = adjustHostInRequest(request, updatedInstanceUrl.host)
                                }
                            }
                            response.close()
                            response = chain.proceed(request)
                        }
                    }
                }
            }
            return response
        }

        /**
         * Build new request which has the new host. This is essential in case of instance migration
         *
         * @param request
         * @param host    the host segment of the url to be placed
         * @return
         */
        private fun adjustHostInRequest(request: Request, host: String): Request {
            val urlBuilder = request.url.newBuilder()

            // Only replace the host
            urlBuilder.host(host)
            val builder = request.newBuilder()
            builder.url(urlBuilder.build())
            return builder.build()
        }

        /**
         * Build new request which has authentication header
         * @param request
         * @return
         */
        private fun buildAuthenticatedRequest(request: Request): Request {
            val builder = request.newBuilder()
            setAuthHeader(builder)
            return builder.build()
        }

        /**
         * @return The authToken for this RestClient.
         */
        @Synchronized
        fun getAuthToken(): String? {
            return authToken
        }

        /**
         * Set auth header
         *
         * @param builder
         */
        private fun setAuthHeader(builder: Request.Builder) {
            if (authToken != null) { //Add Auth token to each request if authorized
                OAuth2.addAuthorizationHeader(builder, authToken)
            }
        }

        /**
         * Change authToken for this RestClient
         *
         * @param newAuthToken
         */
        @Synchronized
        private fun setAuthToken(newAuthToken: String?) {
            authToken = newAuthToken
        }

        /**
         * @return The refresh token, if available.
         */
        fun getRefreshToken(): String? {
            return authTokenProvider?.getRefreshToken()
        }

        /**
         * @return Elapsed time (ms) since the last refresh.
         */
        fun getElapsedTimeSinceLastRefresh(): Long {
            val lastRefreshTime = authTokenProvider?.getLastRefreshTime() ?: -1
            return if (lastRefreshTime < 0) {
                -1
            } else {
                System.currentTimeMillis() - lastRefreshTime
            }
        }

        /**
         * Swaps the existing access token for a new one.
         */
        @Throws(IOException::class)
        fun refreshAccessToken() {

            // If we haven't retried already and we have an accessTokenProvider
            // Then let's try to get a new authToken
            if (authTokenProvider != null) {
                val newAuthToken = authTokenProvider.getNewAuthToken()

                if (newAuthToken == null || authTokenProvider.getInstanceUrl() == null) {
                    throw RefreshTokenRevokedException("Could not refresh token")
                }

                // Use new token
                setAuthToken(newAuthToken)

                // Check if the instanceUrl changed
                val instanceUrl = authTokenProvider.getInstanceUrl()
                if (!clientInfo.instanceUrl.toString().equals(instanceUrl, ignoreCase = true)) {
                    try {

                        // Create a new ClientInfo
                        clientInfo = ClientInfo(
                            URI(instanceUrl),
                            clientInfo.loginUrl, clientInfo.identityUrl,
                            clientInfo.accountName, clientInfo.username,
                            clientInfo.userId, clientInfo.orgId, clientInfo.communityId,
                            clientInfo.communityUrl, clientInfo.firstName, clientInfo.lastName,
                            clientInfo.displayName, clientInfo.email, clientInfo.photoUrl,
                            clientInfo.thumbnailUrl, clientInfo.additionalOauthValues,
                            clientInfo.lightningDomain, clientInfo.lightningSid,
                            clientInfo.vfDomain, clientInfo.vfSid,
                            clientInfo.contentDomain, clientInfo.contentSid, clientInfo.csrfToken
                        )
                    } catch (ex: URISyntaxException) {
                        SalesforceSDKLogger.w(TAG, "Invalid server URL", ex)
                    }
                }
            }
        }
    }

    /**
     * Exception thrown when refresh token was found to be revoked
     */
    class RefreshTokenRevokedException : IOException {

        constructor(msg: String) : super(msg)

        constructor(msg: String, cause: Throwable) : super(msg, cause)

        companion object {
            private const val serialVersionUID = 2L
        }
    }

    companion object {
        // Keys in credentials map
        private const val USER_AGENT = "userAgent"
        private const val INSTANCE_URL = "instanceUrl"
        private const val LOGIN_URL = "loginUrl"
        private const val IDENTITY_URL = "identityUrl"
        private const val ORG_ID = "orgId"
        private const val USER_ID = "userId"
        private const val REFRESH_TOKEN = "refreshToken"
        private const val ACCESS_TOKEN = "accessToken"
        private const val COMMUNITY_ID = "communityId"
        private const val COMMUNITY_URL = "communityUrl"
        private const val TAG = "RestClient"

        private val OAUTH_REFRESH_INTERCEPTORS = mutableMapOf<String, OAuthRefreshInterceptor>()
        private val OK_CLIENT_BUILDERS = mutableMapOf<String, OkHttpClient.Builder>()
        private val OK_CLIENTS = mutableMapOf<String, OkHttpClient>()

        /**
         * Remove cached OkHttpClient.Builder, OkHttpClient and OAuthRefreshInterceptor for the given user
         */
        @JvmStatic
        @Synchronized
        fun clearCaches(userAccount: UserAccount?) {
            val orgId = userAccount?.orgId
            val userId = userAccount?.userId
            val cacheKey = computeCacheKey(orgId, userId)
            OAUTH_REFRESH_INTERCEPTORS.remove(cacheKey)
            OK_CLIENT_BUILDERS.remove(cacheKey)
            val client = OK_CLIENTS.remove(cacheKey)
            client?.dispatcher?.cancelAll()
        }

        /**
         * Clear caches of org-id/user-id to OkHttpClient.Builder, OkHttpClient and OAuthRefreshInterceptor
         */
        @JvmStatic
        @Synchronized
        fun clearCaches() {
            OAUTH_REFRESH_INTERCEPTORS.clear()
            OK_CLIENT_BUILDERS.clear()
            OK_CLIENTS.clear()
        }

        private fun computeCacheKey(orgId: String?, userId: String?): String {
            return if (orgId != null && userId != null) "$orgId-$userId" else "unauthenticated"
        }
    }
}
