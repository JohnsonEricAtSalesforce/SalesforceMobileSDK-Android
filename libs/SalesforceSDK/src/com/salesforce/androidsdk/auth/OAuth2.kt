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

import android.accounts.NetworkErrorException
import android.net.Uri
import android.text.TextUtils
import androidx.annotation.VisibleForTesting
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.rest.RestResponse
import com.salesforce.androidsdk.util.SalesforceSDKLogger
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URISyntaxException
import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Helper methods for common OAuth2 requests.
 *
 * The typical OAuth2 flow is:
 *
 * <ol>
 * <li> The authorization flow is started by presenting the web-based
 * authorization screen to the user.  This will prompt him/her to login and
 * to authorize our application. The result will be a callback with an
 * authorization code, or an error.</li>
 *
 * <li> Use the authorization code from (a) with the token end point, to get
 * access and refresh tokens, as well as basic identity, and instance
 * information.</li>
 *
 * <li> Use the access token from (b) to call the identity service, which will
 * let us obtain additional information about the user.</li>
 *
 * <li> Use the access token from (b) to issue REST API requests against the
 * authenticated user.</li>
 * </ol>
 *
 * If the access token expires, a new one can be obtained by using the refresh token from (b)
 * with the token end point -- completely bypassing (a).
 *
 * @see <a href="http://wiki.developerforce.com/index.php/Digging_Deeper_into_OAuth_2.0_on_Force.com">Digging Deeper into OAuth 2.0 on Force.com</a>
 */
object OAuth2 {

    // OAuth Configuration
    const val OAUTH_DISPLAY_TOUCH = "touch"
    const val OAUTH_RESPONSE_TYPE_TOKEN = "token"
    const val OAUTH_RESPONSE_TYPE_ACTIVATED_CLIENT_CODE = "activated_client_code"
    const val OAUTH_RESPONSE_TYPE_CODE = "code"
    private const val OAUTH_REDIRECT_APPROVED = "redirect_approved"
    const val LOGIN_HINT = "login_hint"
    private const val OAUTH_RESPONSE_TYPE_HYBRID_TOKEN = "hybrid_token"
    private const val OAUTH_PARAM_DEVICE_ID = "device_id"

    // Keys in HTTP POST
    private const val ACCESS_TOKEN = "access_token"
    private const val CLIENT_ID = "client_id"
    private const val CLIENT_SECRET = "client_secret"
    private const val FORMAT = "format"
    private const val GRANT_TYPE = "grant_type"
    private const val ASSERTION = "assertion"
    private const val REDIRECT_URI = "redirect_uri"
    private const val REFRESH_TOKEN = "refresh_token"
    private const val CODE = "code"
    private const val CODE_VERIFIER = "code_verifier"

    // Values in HTTP POST
    private const val AUTHORIZATION_CODE = "authorization_code"
    private const val HYBRID_AUTH_CODE = "hybrid_auth_code"
    private const val HYBRID_REFRESH = "hybrid_refresh"
    private const val BEARER = "Bearer "
    private const val GRANTED_DELEGATION = "urn:ietf:params:oauth:grant-type:token-exchange"
    private const val JWT_BEARER = "urn:ietf:params:oauth:grant-type:jwt-bearer"
    private const val JSON = "json"
    private const val REQUEST_TOKEN_TYPE = "request_token_type"
    private const val REQUEST_TOKEN_TYPE_VALUE = "urn:salesforce:params:oauth:token-type:connected-app-consumer"
    private const val SUBJECT_TOKEN = "subject_token"
    private const val SUBJECT_TOKEN_TYPE = "subject_token_type"
    private const val SUBJECT_TOKEN_TYPE_VALUE = "urn:ietf:params:oauth:token-type:access_token"
    private const val TOKEN = "token"

    // Keys in JSON responses
    private const val INSTANCE_URL = "instance_url"
    private const val API_INSTANCE_URL = "api_instance_url"
    private const val ID = "id"
    private const val ERROR = "error"
    private const val ERROR_DESCRIPTION = "error_description"
    private const val MOBILE_POLICY = "mobile_policy"
    private const val PIN_LENGTH = "pin_length"
    private const val SCREEN_LOCK = "screen_lock"
    private const val SCREEN_LOCK_TIMEOUT = "screen_lock_timeout"
    private const val BIOMETRIC_AUTHENTICATION = "biometric_authentication"
    private const val BIOMETRIC_AUTHENTICATION_TIMEOUT = "biometric_authentication_timeout"
    const val SFDC_COMMUNITY_ID = "sfdc_community_id"
    const val SFDC_COMMUNITY_URL = "sfdc_community_url"
    const val PHOTOS = "photos"
    const val PICTURE = "picture"
    const val THUMBNAIL = "thumbnail"
    const val MOBILE_PHONE = "mobile_phone"
    const val MOBILE_PHONE_VERIFIED = "mobile_phone_verified"
    const val USERNAME = "username"
    const val NICK_NAME = "nick_name"
    const val DISPLAY_NAME = "display_name"
    const val FIRST_NAME = "first_name"
    const val LAST_NAME = "last_name"
    const val EMAIL = "email"
    const val EMAIL_VERIFIED = "email_verified"
    const val LANGUAGE = "language"
    const val LOCALE = "locale"
    const val ORGANIZATION_ID = "organization_id"
    const val USER_ID = "user_id"
    const val USER_TYPE = "user_type"
    const val STATUS = "status"
    const val ACTIVE = "active"
    const val URLS = "urls"
    const val LAST_MODIFIED_DATE = "last_modified_date"
    const val ID_TOKEN = "id_token"
    const val LIGHTNING_DOMAIN = "lightning_domain"
    const val LIGHTNING_SID = "lightning_sid"
    const val VF_DOMAIN = "visualforce_domain"
    const val VF_SID = "visualforce_sid"
    const val CONTENT_DOMAIN = "content_domain"
    const val CONTENT_SID = "content_sid"
    const val CSRF_TOKEN = "csrf_token"
    const val COOKIE_CLIENT_SRC = "cookie_client_src"
    const val COOKIE_SID_CLIENT = "cookie_sid_client"
    const val SID_COOKIE_NAME = "sid_cookie_name"
    const val PARENT_SID = "parent_sid"
    const val TOKEN_FORMAT = "token_format"
    const val BEACON_CHILD_CONSUMER_KEY = "beacon_child_consumer_key"
    const val BEACON_CHILD_CONSUMER_SECRET = "beacon_child_consumer_secret"
    const val SCOPE = "scope"
    const val FRONTDOOR_URL_KEY = "frontdoorUrl"

    // OAuth paths
    private const val OAUTH_AUTH_PATH = "/services/oauth2/authorize?display=%s&response_type=%s&client_id=%s&redirect_uri=%s"
    private const val OAUTH_TOKEN_PATH = "/services/oauth2/token"
    private const val OAUTH_REVOKE_PATH = "/services/oauth2/revoke"

    // Default values
    const val BIOMETRIC_AUTHENTICATION_DEFAULT_TIMEOUT = 0

    private const val TAG = "OAuth2"

    @VisibleForTesting
    internal val TIMESTAMP_FORMAT: DateFormat by lazy {
        val df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        df.timeZone = TimeZone.getTimeZone("GMT")
        df
    }

    /**
     * Enumeration of reasons why the user was logged out.
     */
    enum class LogoutReason {
        /**
         * Logout action initiated by user
         */
        USER_LOGOUT,

        /**
         * Device PIN screen lock is now required by administrator (and wasn't before)
         */
        DB_VERSION_MISMATCH,

        /**
         * SDK version has been upgraded
         */
        SDK_VERSION_MISMATCH,

        /**
         * Biometric authentication timeout has occurred
         */
        BIOMETRIC_TIMEOUT,

        /**
         * MDM policy indicated app must be biometrically authenticated on each launch
         */
        BIOMETRIC_ENROLLMENT_CHANGED,

        /**
         * Screen lock timeout has occurred
         */
        SCREEN_LOCK_TIMEOUT,

        /**
         * Refresh token was revoked
         */
        REFRESH_TOKEN_EXPIRED
    }

    /**
     * Builds the URL to the authorization web page for this login server.
     * You need not provide the 'refresh_token' scope, as it is provided automatically.
     *
     * @param useWebServerAuthentication True to use web server flow, False to use user agent flow
     * @param useHybridAuthentication    True to use "hybrid" flow
     * @param loginServer                Base protocol and server to use (e.g. https://login.salesforce.com).
     * @param clientId                   OAuth client ID.
     * @param callbackUrl                OAuth callback URL or redirect URL.
     * @param scopes                     A list of OAuth scopes to request (e.g. {"visualforce", "api"}). If null,
     *                                   the default OAuth scope is provided.
     * @param loginHint                  When applicable, the Salesforce Welcome Login hint
     * @param displayType                OAuth display type. If null, the default of 'touch' is used.
     * @param codeChallenge              Code challenge to use when using web server flow
     * @param addlParams                 Any additional parameters that may be
     *                                   added to the request. When using
     *                                   Salesforce Mobile App Attestation, the
     *                                   "attestation" parameter should be added
     *                                   to this map.
     * @return A URL to start the OAuth flow in a web browser/view.
     * @see <a href="https://help.salesforce.com/apex/HTViewHelpDoc?language=en&id=remoteaccess_oauth_scopes.htm">RemoteAccess OAuth Scopes</a>
     */
    @JvmStatic
    fun getAuthorizationUrl(
        useWebServerAuthentication: Boolean,
        useHybridAuthentication: Boolean,
        loginServer: URI,
        clientId: String,
        callbackUrl: String,
        scopes: Array<String>?,
        loginHint: String?,
        displayType: String?,
        codeChallenge: String?,
        addlParams: Map<String, String>?
    ): URI {
        val sb = StringBuilder(loginServer.toString())

        val responseType = when {
            useWebServerAuthentication -> OAUTH_RESPONSE_TYPE_CODE
            useHybridAuthentication -> OAUTH_RESPONSE_TYPE_HYBRID_TOKEN
            else -> OAUTH_RESPONSE_TYPE_TOKEN
        }

        sb.append("/services/oauth2/authorize").append(getBrandedLoginPath())
        sb.append("?display=").append(displayType ?: OAUTH_DISPLAY_TOUCH)
        sb.append("&response_type=").append(responseType)
        sb.append("&client_id=").append(Uri.encode(clientId))

        if (scopes != null && scopes.isNotEmpty()) {
            sb.append("&scope=").append(Uri.encode(scopes.joinToString(" ")))
        }

        if (!TextUtils.isEmpty(loginHint)) {
            sb.append("&login_hint=").append(Uri.encode(loginHint))
        }

        sb.append("&redirect_uri=").append(callbackUrl)
        sb.append("&device_id=").append(SalesforceSDKManager.getInstance().deviceId)

        if (useWebServerAuthentication && !TextUtils.isEmpty(codeChallenge)) {
            sb.append("&code_challenge=").append(Uri.encode(codeChallenge))
        }

        if (addlParams != null && addlParams.isNotEmpty()) {
            for ((key, value) in addlParams) {
                sb.append("&").append(key).append("=").append(Uri.encode(value ?: ""))
            }
        }

        return URI.create(sb.toString())
    }

    /**
     * Build the URL to the authorization web page for this login server.
     * You need not provide refresh_token, as it is provided automatically.
     *
     * @param loginServer    the base protocol and server to use (e.g.
     * https://login.salesforce.com)
     * @param clientId       OAuth client ID
     * @param callbackUrl    OAuth callback URL
     * @param scopes         An array of OAuth scopes to request (e.g. {"visualforce", "api"}). If null, the default OAuth scope is provided.
     * @param displayType    Must be "touch" or "page" - "touch" should be used for mobile OSs and "page" for desktop OSs
     * @param responseType   Must be "token" or "code" - "token" should be used for user agent flow (implicit grant) and "code" for web server flow (authorization grant)
     * @return a well-formed URL to the authorization page.
     */
    @JvmStatic
    fun getAuthorizationUrl(
        loginServer: URI,
        clientId: String,
        callbackUrl: String,
        scopes: Array<String>?,
        displayType: String,
        responseType: String
    ): URI {
        val scopesStr = computeScopeParameter(scopes)
        val brandedLoginPath = getBrandedLoginPath()
        var path = String.format(OAUTH_AUTH_PATH, displayType, responseType, clientId, callbackUrl)
        path += "&$scopesStr$brandedLoginPath"
        return URI.create("$loginServer$path")
    }

    /**
     * Build the URL to the authorization web page for this login server.
     * You need not provide refresh_token, as it is provided automatically.
     *
     * @param loginServer    the base protocol and server to use (e.g.
     * https://login.salesforce.com)
     * @param clientId       OAuth client ID
     * @param callbackUrl    OAuth callback URL
     * @param scopes         An array of OAuth scopes to request (e.g. {"visualforce", "api"}). If null, the default OAuth scope is provided.
     * @return a well-formed URL to the authorization page.
     */
    @JvmStatic
    fun getAuthorizationUrl(
        loginServer: URI,
        clientId: String,
        callbackUrl: String,
        scopes: Array<String>?
    ): URI {
        return getAuthorizationUrl(loginServer, clientId, callbackUrl, scopes, OAUTH_DISPLAY_TOUCH, OAUTH_RESPONSE_TYPE_TOKEN)
    }

    /**
     * Returns branded login path from SDK manager.
     *
     * @return Branded login path.
     */
    private fun getBrandedLoginPath(): String {
        val loginBrand = SalesforceSDKManager.getInstance().loginBrand
        return if (TextUtils.isEmpty(loginBrand)) "" else "&startURL=${loginBrand}"
    }

    /**
     * @deprecated Replaced by {@link com.salesforce.androidsdk.ui.UiBridgeUtil.getFrontdoorUrl() UiBridgeUtil.getFrontdoorUrl()}.
     *
     * Build the URL to the web page for a frontdoor URL
     * @param loginServer    the base protocol and server to use (e.g. https://login.salesforce.com)
     * @param instanceServer the base protocol and server to use (e.g. https://na1.salesforce.com)
     * @param accessToken    OAuth access token (session ID)
     * @param url            the path to use (e.g. /home/home.jsp)
     * @param logLevel       the desired server log level
     * @return a well-formed frontdoor URL.
     */
    @Deprecated("Replaced by UiBridgeUtil.getFrontdoorUrl()")
    @JvmStatic
    fun getFrontdoorUrl(
        loginServer: String,
        instanceServer: String,
        accessToken: String,
        url: String,
        logLevel: String?
    ): String {
        val encodedUrl = Uri.encode(url)
        val encodedRetUrl = Uri.encode("$instanceServer$url")
        val frontdoorUrl = ("$loginServer/secur/frontdoor.jsp?display=touch&sid=$accessToken" +
                "&retURL=$encodedRetUrl&display=touch")
        return if (logLevel != null) {
            "$frontdoorUrl&un=$logLevel&pw=$logLevel$encodedUrl"
        } else {
            frontdoorUrl
        }
    }

    /**
     * @param scopes An array of OAuth scopes to request (e.g. {"visualforce", "api"}). If null, "web" "api" and "refresh_token" are used.
     * @return the OAuth scope parameter as a URL encoded string.
     */
    private fun computeScopeParameter(scopes: Array<String>?): String {
        var scopesStr = "scope=web+api"
        if (scopes != null && scopes.isNotEmpty()) {
            scopesStr = "scope=${TextUtils.join("+", scopes)}"
        }
        scopesStr += "+$REFRESH_TOKEN"
        return scopesStr
    }

    /**
     * Exchange an authorization code for an access/refresh token pair.
     * *** Warning: this method calls network synchronously - do not call it from the main thread ***
     *
     * @param httpAccess    HTTP accessor
     * @param loginServer   The base protocol and server to use (e.g. https://login.salesforce.com)
     * @param clientId      OAuth client ID
     * @param code          authorization code
     * @param codeVerifier  PKCE code verifier
     * @param callbackUrl   OAuth callback URL
     * @return TokenEndpointResponse
     * @throws OAuthFailedException if we get a network error when exchanging the code, or if a non-200 response is received
     */
    @JvmStatic
    @Throws(OAuthFailedException::class)
    fun exchangeCode(
        httpAccess: HttpAccess,
        loginServer: URI,
        clientId: String,
        code: String,
        codeVerifier: String,
        callbackUrl: String
    ): TokenEndpointResponse {
        val useHybridAuthentication = SalesforceSDKManager.getInstance().useHybridAuthentication
        val grantType = if (useHybridAuthentication) HYBRID_AUTH_CODE else AUTHORIZATION_CODE

        val bodyBuilder = FormBody.Builder()
            .add(GRANT_TYPE, grantType)
            .add(CLIENT_ID, clientId)
            .add(FORMAT, JSON)
            .add(CODE, code)
            .add(CODE_VERIFIER, codeVerifier)
            .add(REDIRECT_URI, callbackUrl)

        return makeTokenEndpointRequest(httpAccess, loginServer, bodyBuilder.build())
    }

    /**
     * Refresh the access token.
     * *** Warning: this method calls network synchronously - do not call it from the main thread ***
     *
     * @param httpAccess         HTTP accessor
     * @param loginServer        The base protocol and server to use (e.g. https://login.salesforce.com)
     * @param clientId           OAuth client ID
     * @param refreshToken       user's refresh token
     * @param additionalOauthKeys additional oauth keys (if any)
     * @return TokenEndpointResponse
     * @throws OAuthFailedException if we get a network error, or if a non-200 response is received
     */
    @JvmStatic
    @Throws(OAuthFailedException::class)
    fun refreshAuthToken(
        httpAccess: HttpAccess,
        loginServer: URI,
        clientId: String,
        refreshToken: String?,
        additionalOauthKeys: Map<String, String>?
    ): TokenEndpointResponse {
        val useHybridAuthentication = SalesforceSDKManager.getInstance().useHybridAuthentication
        val grantType = if (useHybridAuthentication) HYBRID_REFRESH else REFRESH_TOKEN

        val bodyBuilder = FormBody.Builder()
            .add(GRANT_TYPE, grantType)
            .add(CLIENT_ID, clientId)
            .add(REFRESH_TOKEN, refreshToken ?: "")
            .add(FORMAT, JSON)

        if (additionalOauthKeys != null && additionalOauthKeys.isNotEmpty()) {
            for ((key, value) in additionalOauthKeys) {
                // Safely ignore missing values since, for instance, a user account that is being upgraded may not have received that value yet.
                if (!TextUtils.isEmpty(value)) {
                    bodyBuilder.add(key, value)
                }
            }
        }
        return makeTokenEndpointRequest(httpAccess, loginServer, bodyBuilder.build())
    }

    /**
     * Revoke the user's refresh token.
     * *** Warning: this method calls network synchronously - do not call it from the main thread ***
     * Note: As of writing (2024) this call will never succeed because revoke API is an authenticated endpoint.
     * However, there is no harm in attempting and the debug logs produced may help developers better understand the
     * state of their app.
     *
     * @param httpAccess   HTTP accessor
     * @param loginServer  The base protocol and server to use (e.g. https://login.salesforce.com)
     * @param refreshToken user's refresh token
     */
    @JvmStatic
    @Throws(IOException::class)
    fun revokeRefreshToken(
        httpAccess: HttpAccess,
        loginServer: URI,
        refreshToken: String?
    ) {
        val body = FormBody.Builder()
            .add(TOKEN, refreshToken ?: "")
            .build()
        val request = Request.Builder()
            .url("$loginServer$OAUTH_REVOKE_PATH")
            .post(body)
            .build()
        httpAccess.getOkHttpClient().newCall(request).execute()
    }

    /**
     * Swap a JWT for an access token.
     * *** Warning: this method calls network synchronously - do not call it from the main thread ***
     *
     * @param httpAccess  HTTP accessor
     * @param loginServer The base protocol and server to use (e.g. https://login.salesforce.com)
     * @param jwt         user's jwt
     * @return TokenEndpointResponse
     * @throws OAuthFailedException if we get a network error, or if a non-200 response is received
     */
    @JvmStatic
    @Throws(OAuthFailedException::class)
    fun swapJWTForTokens(
        httpAccess: HttpAccess,
        loginServer: URI,
        jwt: String
    ): TokenEndpointResponse {
        val body = FormBody.Builder()
            .add(FORMAT, JSON)
            .add(GRANT_TYPE, JWT_BEARER)
            .add(ASSERTION, jwt)
            .build()
        return makeTokenEndpointRequest(httpAccess, loginServer, body)
    }

    /**
     * Swap a beacon token for a connected app token.
     * *** Warning: this method calls network synchronously - do not call it from the main thread ***
     *
     * @param httpAccess  HTTP accessor
     * @param loginServer The base protocol and server to use (e.g. https://login.salesforce.com)
     * @param accessToken Current access token
     * @return TokenEndpointResponse
     * @throws OAuthFailedException if we get a network error, or if a non-200 response is received
     */
    @JvmStatic
    @Throws(OAuthFailedException::class)
    fun getOpenIDToken(
        httpAccess: HttpAccess,
        loginServer: URI,
        accessToken: String
    ): TokenEndpointResponse {
        val body = FormBody.Builder()
            .add(FORMAT, JSON)
            .add(GRANT_TYPE, GRANTED_DELEGATION)
            .add(REQUEST_TOKEN_TYPE, REQUEST_TOKEN_TYPE_VALUE)
            .add(SUBJECT_TOKEN, accessToken)
            .add(SUBJECT_TOKEN_TYPE, SUBJECT_TOKEN_TYPE_VALUE)
            .build()
        return makeTokenEndpointRequest(httpAccess, loginServer, body)
    }

    /**
     * Call the identity service for information about the user.
     * *** Warning: this method calls network synchronously - do not call it from the main thread ***
     *
     * @param httpAccess   HTTP accessor
     * @param identityUrl  URL of ID service.
     * @param accessToken  OAuth access token.
     * @return {@link IdServiceResponse}
     * @throws NetworkErrorException
     * @throws IOException
     */
    @JvmStatic
    @Throws(NetworkErrorException::class, IOException::class)
    fun callIdentityService(
        httpAccess: HttpAccess,
        identityUrl: String,
        accessToken: String
    ): IdServiceResponse {
        val requestBuilder = Request.Builder()
            .url(identityUrl)
            .get()
        addAuthorizationHeader(requestBuilder, accessToken)
        val request = requestBuilder.build()
        val response = httpAccess.getOkHttpClient().newCall(request).execute()
        val statusCode = response.code
        if (statusCode != HttpURLConnection.HTTP_OK) {
            throw NetworkErrorException("HTTP $statusCode Error")
        }
        return IdServiceResponse(response)
    }

    /**
     * Add authorization header to request
     * @param builder
     * @param authToken
     */
    @JvmStatic
    fun addAuthorizationHeader(builder: Request.Builder, authToken: String?) {
        if (authToken != null) {
            builder.header("Authorization", BEARER + authToken)
        }
    }

    /**
     * Make a call to the token endpoint.
     *
     * @param httpAccess HTTP accessor
     * @param loginServer The base protocol and server to use (e.g. https://login.salesforce.com)
     * @param body Body to post
     * @return TokenEndpointResponse
     * @throws OAuthFailedException if we get a network error, or if a non-200 response is received
     */
    @Throws(OAuthFailedException::class)
    private fun makeTokenEndpointRequest(
        httpAccess: HttpAccess,
        loginServer: URI,
        body: FormBody
    ): TokenEndpointResponse {
        try {
            val tokenUrl = StringBuilder(loginServer.toString())
                .append(OAUTH_TOKEN_PATH)
                .append("?")
                .append(OAUTH_PARAM_DEVICE_ID)
                .append("=")
                .append(SalesforceSDKManager.getInstance().deviceId)
                .toString()

            val request = Request.Builder()
                .url(tokenUrl)
                .post(body)
                .build()
            val response = httpAccess.getOkHttpClient().newCall(request).execute()

            // Non-200 status code is definitely an error - try to parse as an error response
            if (!response.isSuccessful) {
                val errorResponse = TokenErrorResponse(response)
                throw OAuthFailedException(errorResponse, response.code)
            }

            // Otherwise try to parse as a token response
            return TokenEndpointResponse(response)
        } catch (e: IOException) {
            throw OAuthFailedException("Error during OAuth token request", e)
        }
    }

    /**
     * Exception thrown when refresh fails.
     */
    class OAuthFailedException : IOException {
        val tokenErrorResponse: TokenErrorResponse
        val httpStatusCode: Int

        constructor(tokenErrorResponse: TokenErrorResponse, httpStatusCode: Int) : super(tokenErrorResponse.toString()) {
            this.tokenErrorResponse = tokenErrorResponse
            this.httpStatusCode = httpStatusCode
        }

        constructor(message: String, cause: Throwable) : super(message, cause) {
            this.tokenErrorResponse = TokenErrorResponse(Response.Builder().build())
            this.httpStatusCode = -1
        }

        /**
         * Returns true if the error is invalid_grant (which typically means invalid/expired refresh token).
         */
        val isRefreshTokenInvalid: Boolean
            get() = tokenErrorResponse.error == "invalid_grant"

        companion object {
            private const val serialVersionUID = 1L
        }
    }

    /**
     * Helper class to parse a call to the identity service.
     */
    class IdServiceResponse {
        var username: String? = null
        var email: String? = null
        var pictureUrl: String? = null
        var thumbnailUrl: String? = null
        var firstName: String? = null
        var lastName: String? = null
        var displayName: String? = null
        var mobilePhone: String? = null
        var mobilePhoneVerified = false
        var nickName: String? = null
        var emailVerified = false
        var orgId: String? = null
        var userId: String? = null
        var userType: String? = null
        var language: String? = null
        var locale: String? = null
        var isActive = false
        var restUrl: String? = null
        var restSObjectsUrl: String? = null
        var restSearchUrl: String? = null
        var restQueryUrl: String? = null
        var restRecentUrl: String? = null
        var profileUrl: String? = null
        var chatterFeedsUrl: String? = null
        var chatterGroupsUrl: String? = null
        var chatterUsersUrl: String? = null
        var chatterFeedItemsUrl: String? = null
        var lastModifiedDate: Date? = null
        var pinLength = 0
        var screenLock = false
        var screenLockTimeout = 0
        var biometricAuth = false
        var biometricAuthTimeout = 0
        var customPermissions: JSONObject? = null
        var customAttributes: JSONObject? = null

        /**
         * Parameterized constructor.
         *
         * @param response HTTP response.
         */
        constructor(response: Response) {
            try {
                val parsedResponse = RestResponse(response).asJSONObject()
                populateFromJSON(parsedResponse)
            } catch (e: Exception) {
                SalesforceSDKLogger.w(TAG, "Could not parse identity response", e)
            }
        }

        /**
         * Parameterized constructor.
         *
         * @param json JSON object.
         */
        constructor(json: JSONObject) {
            populateFromJSON(json)
        }

        private fun populateFromJSON(parsedResponse: JSONObject) {
            try {
                username = parsedResponse.getString(USERNAME)
                email = parsedResponse.getString(EMAIL)
                orgId = parsedResponse.getString(ORGANIZATION_ID)
                userId = parsedResponse.getString(USER_ID)
                userType = parsedResponse.optString(USER_TYPE)
                language = parsedResponse.optString(LANGUAGE)
                locale = parsedResponse.optString(LOCALE)
                mobilePhone = parsedResponse.optString(MOBILE_PHONE)
                mobilePhoneVerified = parsedResponse.optBoolean(MOBILE_PHONE_VERIFIED)
                nickName = parsedResponse.optString(NICK_NAME)
                emailVerified = parsedResponse.optBoolean(EMAIL_VERIFIED)
                val status = parsedResponse.getString(STATUS)
                isActive = ACTIVE.equals(status, ignoreCase = true)
                pictureUrl = parsedResponse.getJSONObject(PHOTOS).getString(PICTURE)
                thumbnailUrl = parsedResponse.getJSONObject(PHOTOS).getString(THUMBNAIL)
                val lastModifiedDateStr = parsedResponse.getString(LAST_MODIFIED_DATE)
                lastModifiedDate = parseDateString(lastModifiedDateStr)
                firstName = parsedResponse.optString(FIRST_NAME)
                lastName = parsedResponse.optString(LAST_NAME)
                displayName = parsedResponse.optString(DISPLAY_NAME)
                val urlsObject = parsedResponse.getJSONObject(URLS)
                restUrl = urlsObject.optString("rest")
                restSObjectsUrl = urlsObject.optString("sobjects")
                restSearchUrl = urlsObject.optString("search")
                restQueryUrl = urlsObject.optString("query")
                restRecentUrl = urlsObject.optString("recent")
                profileUrl = urlsObject.optString("profile")
                chatterFeedsUrl = urlsObject.optString("feeds")
                chatterGroupsUrl = urlsObject.optString("groups")
                chatterUsersUrl = urlsObject.optString("users")
                chatterFeedItemsUrl = urlsObject.optString("feed_items")

                // Mobile policies
                var mobilePolicyConfigured = false
                if (parsedResponse.has(MOBILE_POLICY)) {
                    mobilePolicyConfigured = true
                }

                if (mobilePolicyConfigured) {
                    val mobilePolicyObject = parsedResponse.getJSONObject(MOBILE_POLICY)
                    pinLength = mobilePolicyObject.optInt(PIN_LENGTH)

                    biometricAuth = mobilePolicyObject.has(BIOMETRIC_AUTHENTICATION_TIMEOUT)
                    biometricAuthTimeout = if (mobilePolicyObject.has(BIOMETRIC_AUTHENTICATION_TIMEOUT)) {
                        mobilePolicyObject.getInt(BIOMETRIC_AUTHENTICATION_TIMEOUT)
                    } else {
                        BIOMETRIC_AUTHENTICATION_DEFAULT_TIMEOUT
                    }
                }

                if (mobilePolicyConfigured) {
                    val mobilePolicyObject = parsedResponse.getJSONObject(MOBILE_POLICY)
                    screenLock = mobilePolicyObject.has(SCREEN_LOCK_TIMEOUT)
                    screenLockTimeout = mobilePolicyObject.getInt(SCREEN_LOCK_TIMEOUT)

                    if (screenLock && biometricAuth) {
                        SalesforceSDKLogger.w(TAG, "Ignoring ScreenLock because BiometricAuthentication is enabled.")
                        screenLock = false
                    }
                }

                // Parse custom permissions and attributes
                customPermissions = parsedResponse.optJSONObject("custom_permissions")
                customAttributes = parsedResponse.optJSONObject("custom_attributes")
            } catch (e: Exception) {
                SalesforceSDKLogger.w(TAG, "Could not parse identity response", e)
            }
        }

        companion object {
            @JvmStatic
            fun parseDateString(dateString: String?): Date? {
                return try {
                    if (dateString == null) null else TIMESTAMP_FORMAT.parse(dateString)
                } catch (e: ParseException) {
                    SalesforceSDKLogger.w(TAG, "Could not parse date string $dateString", e)
                    null
                }
            }
        }
    }

    /**
     * Helper class to parse a token refresh error response.
     */
    class TokenErrorResponse {
        var error: String? = null
        var errorDescription: String? = null

        /**
         * Parameterized constructor built from an error response.
         *
         * @param response Error response.
         */
        constructor(response: Response) {
            try {
                val parsedResponse = RestResponse(response).asJSONObject()
                error = parsedResponse.getString(ERROR)
                errorDescription = parsedResponse.getString(ERROR_DESCRIPTION)
            } catch (e: Exception) {
                SalesforceSDKLogger.w(TAG, "Could not parse token error response", e)
            }
        }

        override fun toString(): String {
            return "$error:$errorDescription"
        }
    }

    /**
     * Helper class to parse a token refresh response.
     */
    class TokenEndpointResponse {
        var authToken: String? = null
        var refreshToken: String? = null
        var instanceUrl: String? = null
        var apiInstanceUrl: String? = null
        var idUrl: String? = null
        var idUrlWithInstance: String? = null
        var orgId: String? = null
        var userId: String? = null
        var code: String? = null
        var communityId: String? = null
        var communityUrl: String? = null
        var additionalOauthValues: MutableMap<String, String>? = null
        var idToken: String? = null
        var lightningDomain: String? = null
        var lightningSid: String? = null
        var vfDomain: String? = null
        var vfSid: String? = null
        var contentDomain: String? = null
        var contentSid: String? = null
        var csrfToken: String? = null
        var cookieClientSrc: String? = null
        var cookieSidClient: String? = null
        var sidCookieName: String? = null
        var parentSid: String? = null
        var tokenFormat: String? = null
        var beaconChildConsumerKey: String? = null
        var beaconChildConsumerSecret: String? = null
        var scope: String? = null

        /**
         * Parameterized constructor built from params during user agent login flow.
         *
         * @param callbackUrlParams Callback URL parameters.
         * @param additionalOauthKeys Additional oauth keys.
         */
        @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
        constructor(callbackUrlParams: Map<String, String>, additionalOauthKeys: List<String>?) {
            try {
                authToken = callbackUrlParams[ACCESS_TOKEN]
                refreshToken = callbackUrlParams[REFRESH_TOKEN]
                instanceUrl = callbackUrlParams[INSTANCE_URL]
                apiInstanceUrl = callbackUrlParams[API_INSTANCE_URL]
                idUrl = callbackUrlParams[ID]
                code = callbackUrlParams[CODE]
                computeOtherFields()
                communityId = callbackUrlParams[SFDC_COMMUNITY_ID]
                communityUrl = callbackUrlParams[SFDC_COMMUNITY_URL]
                if (additionalOauthKeys != null && additionalOauthKeys.isNotEmpty()) {
                    additionalOauthValues = HashMap()
                    for (key in additionalOauthKeys) {
                        if (!TextUtils.isEmpty(key)) {
                            additionalOauthValues!![key] = callbackUrlParams[key] ?: ""
                        }
                    }
                }
                idToken = callbackUrlParams[ID_TOKEN]
                lightningDomain = callbackUrlParams[LIGHTNING_DOMAIN]
                lightningSid = callbackUrlParams[LIGHTNING_SID]
                vfDomain = callbackUrlParams[VF_DOMAIN]
                vfSid = callbackUrlParams[VF_SID]
                contentDomain = callbackUrlParams[CONTENT_DOMAIN]
                contentSid = callbackUrlParams[CONTENT_SID]
                csrfToken = callbackUrlParams[CSRF_TOKEN]
                cookieClientSrc = callbackUrlParams[COOKIE_CLIENT_SRC]
                cookieSidClient = callbackUrlParams[COOKIE_SID_CLIENT]
                sidCookieName = callbackUrlParams[SID_COOKIE_NAME]
                parentSid = callbackUrlParams[PARENT_SID]
                tokenFormat = callbackUrlParams.getOrDefault(TOKEN_FORMAT, "")
                scope = callbackUrlParams[SCOPE]

                // NB: beacon apps not supported with user agent flow so no beacon child fields expected
            } catch (e: Exception) {
                SalesforceSDKLogger.w(TAG, "Could not parse token endpoint response", e)
            }
        }

        /**
         * Parameterized constructor built from params during user agent login flow.
         *
         * @param callbackUrlParams Callback URL parameters.
         */
        constructor(callbackUrlParams: Map<String, String>) : this(
            callbackUrlParams,
            if (SalesforceSDKManager.getInstance() != null)
                SalesforceSDKManager.getInstance().additionalOauthKeys
            else
                null
        )

        /**
         * Parameterized constructor built from refresh flow response
         * or code exchange response (web server login flow).
         *
         * @param response Token endpoint response.
         * @param additionalOauthKeys Additional oauth keys.
         */
        @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
        constructor(response: Response, additionalOauthKeys: List<String>?) {
            try {
                val parsedResponse = RestResponse(response).asJSONObject()
                SalesforceSDKLogger.d(TAG, "parsedResponse-->$parsedResponse")
                authToken = parsedResponse.getString(ACCESS_TOKEN)
                instanceUrl = parsedResponse.getString(INSTANCE_URL)
                if (parsedResponse.has(API_INSTANCE_URL)) {
                    apiInstanceUrl = parsedResponse.getString(API_INSTANCE_URL)
                }
                idUrl = parsedResponse.getString(ID)
                computeOtherFields()
                if (parsedResponse.has(REFRESH_TOKEN)) {
                    refreshToken = parsedResponse.getString(REFRESH_TOKEN)
                }
                if (parsedResponse.has(SFDC_COMMUNITY_ID)) {
                    communityId = parsedResponse.getString(SFDC_COMMUNITY_ID)
                }
                if (parsedResponse.has(SFDC_COMMUNITY_URL)) {
                    communityUrl = parsedResponse.getString(SFDC_COMMUNITY_URL)
                }
                if (additionalOauthKeys != null && additionalOauthKeys.isNotEmpty()) {
                    additionalOauthValues = HashMap()
                    for (key in additionalOauthKeys) {
                        if (!TextUtils.isEmpty(key)) {
                            val value = parsedResponse.optString(key)
                            additionalOauthValues!![key] = value
                        }
                    }
                }
                idToken = parsedResponse.optString(ID_TOKEN)
                lightningDomain = parsedResponse.optString(LIGHTNING_DOMAIN)
                lightningSid = parsedResponse.optString(LIGHTNING_SID)
                vfDomain = parsedResponse.optString(VF_DOMAIN)
                vfSid = parsedResponse.optString(VF_SID)
                contentDomain = parsedResponse.optString(CONTENT_DOMAIN)
                contentSid = parsedResponse.optString(CONTENT_SID)
                csrfToken = parsedResponse.optString(CSRF_TOKEN)
                cookieClientSrc = parsedResponse.optString(COOKIE_CLIENT_SRC)
                cookieSidClient = parsedResponse.optString(COOKIE_SID_CLIENT)
                sidCookieName = parsedResponse.optString(SID_COOKIE_NAME)
                parentSid = parsedResponse.optString(PARENT_SID)
                tokenFormat = parsedResponse.optString(TOKEN_FORMAT)

                // Beacon child fields expected when using a beacon app and web server flow
                if (parsedResponse.has(BEACON_CHILD_CONSUMER_KEY)) {
                    beaconChildConsumerKey = parsedResponse.getString(BEACON_CHILD_CONSUMER_KEY)
                }
                if (parsedResponse.has(BEACON_CHILD_CONSUMER_SECRET)) {
                    beaconChildConsumerSecret = parsedResponse.getString(BEACON_CHILD_CONSUMER_SECRET)
                }
                scope = parsedResponse.optString(SCOPE)
            } catch (e: Exception) {
                SalesforceSDKLogger.w(TAG, "Could not parse token endpoint response", e)
            }
        }

        /**
         * Parameterized constructor built from refresh flow response
         * or code exchange response (web server login flow).
         *
         * @param response Token endpoint response.
         */
        constructor(response: Response) : this(
            response,
            if (SalesforceSDKManager.getInstance() != null)
                SalesforceSDKManager.getInstance().additionalOauthKeys
            else
                null
        )

        @Throws(URISyntaxException::class)
        private fun computeOtherFields() {
            idUrlWithInstance = idUrl!!.replace(URI(idUrl).host, URI(instanceUrl).host)
            val idUrlFragments = idUrl!!.split("/")
            userId = idUrlFragments[idUrlFragments.size - 1]
            orgId = idUrlFragments[idUrlFragments.size - 2]
        }
    }
}
