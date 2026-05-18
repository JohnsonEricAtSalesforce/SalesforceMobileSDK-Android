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
package com.salesforce.androidsdk.auth

import android.net.Uri
import android.text.TextUtils
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
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
 * 1. The authorization flow is started by presenting the web-based
 *    authorization screen to the user. This will prompt him/her to login and to
 *    authorize our application. The result will be a callback with an
 *    authorization code, or an error.
 *
 * 2. Use the authorization code from (a) with the token end point, to get
 *    access and refresh tokens, as well as other metadata.
 *
 * 3. Use the access token from (b) to call the identity service, which will let
 *    us find out the user's username.
 *
 * 4. Store the username, and refresh and access tokens somewhere safe (like the
 *    AccountManager).
 *
 * 5. If the access token becomes invalid, use the refresh token to get another
 *    access token.
 *
 * 6. If the refresh token becomes invalid, go back to the beginning.
 */
class OAuth2 {

    enum class LogoutReason {
        // Corrupted client state
        CORRUPT_STATE,

        // Corrupted client state detected by application
        CORRUPT_STATE_APP_CONFIGURATION_SETTINGS,      // bad configuration settings
        CORRUPT_STATE_APP_PROVIDER_ERROR_INVALID_USER, // invalid user
        CORRUPT_STATE_APP_INVALID_RESTCLIENT,          // invalid rest client
        CORRUPT_STATE_APP_OTHER,                       // other

        // Corrupted client state detected by Mobile SDK
        CORRUPT_STATE_MSDK,

        REFRESH_TOKEN_EXPIRED,   // Refresh token expired
        SSDK_LOGOUT_POLICY,      // SSDK initiated logout for policy violation
        TIMEOUT,                 // Timeout while waiting for server response
        UNEXPECTED,              // Unexpected error or crash
        UNEXPECTED_RESPONSE,     // Unexpected response from server
        UNKNOWN,                 // Unknown
        USER_LOGOUT,             // User initiated logout
        REFRESH_TOKEN_ROTATED;   // Refresh token rotated

        override fun toString(): String {
            return this.name.lowercase(Locale.ROOT)
        }
    }

    /**
     * Exception thrown when the refresh flow fails.
     */
    class OAuthFailedException(
        val tokenErrorResponse: TokenErrorResponse,
        val httpStatusCode: Int
    ) : Exception(tokenErrorResponse.toString()) {

        // Keep field accessible for Java callers that used ofe.response.error directly
        @JvmField
        val response: TokenErrorResponse = tokenErrorResponse

        /**
         * Returns if the refresh token is valid.
         *
         * @return True - if refresh token is valid, False - otherwise.
         */
        val isRefreshTokenInvalid: Boolean
            get() = httpStatusCode == HttpURLConnection.HTTP_UNAUTHORIZED
                    || httpStatusCode == HttpURLConnection.HTTP_FORBIDDEN
                    || httpStatusCode == HttpURLConnection.HTTP_BAD_REQUEST

        companion object {
            private const val serialVersionUID = 1L
        }
    }

    /**
     * Helper class to parse an identity service response.
     */
    class IdServiceResponse {

        @JvmField var username: String? = null
        @JvmField var email: String? = null
        @JvmField var firstName: String? = null
        @JvmField var lastName: String? = null
        @JvmField var displayName: String? = null
        @JvmField var pictureUrl: String? = null
        @JvmField var thumbnailUrl: String? = null
        @JvmField var screenLock = false
        @JvmField var screenLockTimeout = -1
        @JvmField var biometricAuth = false
        @JvmField var biometricAuthTimeout = -1
        @JvmField var customAttributes: JSONObject? = null
        @JvmField var customPermissions: JSONObject? = null

        @JvmField var idUrl: String? = null
        @JvmField var assertedUser = false
        @JvmField var userId: String? = null
        @JvmField var orgId: String? = null
        @JvmField var nickname: String? = null
        @JvmField var photos: String? = null
        @JvmField var urls: String? = null
        @JvmField var enterpriseSoapUrl: String? = null
        @JvmField var metadataSoapUrl: String? = null
        @JvmField var partnerSoapUrl: String? = null
        @JvmField var restUrl: String? = null
        @JvmField var restSObjectsUrl: String? = null
        @JvmField var restSearchUrl: String? = null
        @JvmField var restQueryUrl: String? = null
        @JvmField var restRecentUrl: String? = null
        @JvmField var profileUrl: String? = null
        @JvmField var chatterFeedsUrl: String? = null
        @JvmField var chatterGroupsUrl: String? = null
        @JvmField var chatterUsersUrl: String? = null
        @JvmField var chatterFeedItemsUrl: String? = null
        @JvmField var isActive = false
        @JvmField var userType: String? = null
        @JvmField var language: String? = null
        @JvmField var locale: String? = null
        @JvmField var utcOffset = 0
        @JvmField var mobilePolicyConfigured = false
        @JvmField var lastModifiedDate: Date? = null
        @JvmField var nativeLogin = false

        /**
         * Parameterized constructor built from identity service response.
         *
         * @param response Identity service response.
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
         * Parameterized constructor built from identity service response json.
         *
         * @param jsonObject Identity service response json.
         */
        constructor(jsonObject: JSONObject) {
            populateFromJSON(jsonObject)
        }

        private fun populateFromJSON(parsedResponse: JSONObject) {
            try {
                username = parsedResponse.getString(USERNAME)
                email = parsedResponse.getString(EMAIL)
                firstName = parsedResponse.getString(FIRST_NAME)
                lastName = parsedResponse.getString(LAST_NAME)
                displayName = parsedResponse.getString(DISPLAY_NAME)
                val photosObj = parsedResponse.optJSONObject(PHOTOS)
                if (photosObj != null) {
                    pictureUrl = photosObj.getString(PICTURE)
                    thumbnailUrl = photosObj.getString(THUMBNAIL)
                }

                idUrl = parsedResponse.getString(ID_URL)
                assertedUser = parsedResponse.optBoolean(ASSERTED_USER)
                userId = parsedResponse.getString(USER_ID)
                orgId = parsedResponse.getString(ORG_ID)
                nickname = parsedResponse.getString(NICKNAME)
                val urlsObj = parsedResponse.optJSONObject(URLS)
                if (urlsObj != null) {
                    enterpriseSoapUrl = urlsObj.getString(ENTERPRISE_SOAP_URL)
                    metadataSoapUrl = urlsObj.getString(METADATA_SOAP_URL)
                    partnerSoapUrl = urlsObj.getString(PARTNER_SOAP_URL)
                    restUrl = urlsObj.getString(REST_URL)
                    restSObjectsUrl = urlsObj.getString(REST_SOBJECTS_URL)
                    restSearchUrl = urlsObj.getString(REST_SEARCH_URL)
                    restQueryUrl = urlsObj.getString(REST_QUERY_URL)
                    restRecentUrl = urlsObj.getString(REST_RECENT_URL)
                    profileUrl = urlsObj.getString(PROFILE_URL)
                    chatterFeedsUrl = urlsObj.getString(CHATTER_FEEDS_URL)
                    chatterGroupsUrl = urlsObj.getString(CHATTER_GROUPS_URL)
                    chatterUsersUrl = urlsObj.getString(CHATTER_USERS_URL)
                    chatterFeedItemsUrl = urlsObj.getString(CHATTER_FEED_ITEMS_URL)
                }
                isActive = parsedResponse.optBoolean(IS_ACTIVE)
                userType = parsedResponse.getString(USER_TYPE)
                language = parsedResponse.getString(LANGUAGE)
                locale = parsedResponse.getString(LOCALE)
                utcOffset = parsedResponse.optInt(UTC_OFFSET, -1)
                mobilePolicyConfigured = parsedResponse.has(MOBILE_POLICY)
                lastModifiedDate = parseDateString(parsedResponse.getString(LAST_MODIFIED_DATE))
                nativeLogin = parsedResponse.optBoolean(NATIVE_LOGIN)

                customAttributes = parsedResponse.optJSONObject(CUSTOM_ATTRIBUTES)
                customPermissions = parsedResponse.optJSONObject(CUSTOM_PERMISSIONS)

                if (customAttributes != null && customAttributes!!.has(BIOMETRIC_AUTHENTICATION)) {
                    biometricAuth = true
                    if (customAttributes!!.has(BIOMETRIC_AUTHENTICATION_TIMEOUT)) {
                        biometricAuthTimeout = customAttributes!!.getInt(BIOMETRIC_AUTHENTICATION_TIMEOUT)
                    }

                    if (biometricAuthTimeout < 1) {
                        // Set to the lowest session timeout value (15 minutes) if not specified.
                        biometricAuthTimeout = BIOMETRIC_AUTHENTICATION_DEFAULT_TIMEOUT
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
            } catch (e: Exception) {
                SalesforceSDKLogger.w(TAG, "Could not parse identity response", e)
            }
        }

        companion object {
            @JvmStatic
            fun parseDateString(dateString: String?): Date? {
                return try {
                    TIMESTAMP_FORMAT.parse(dateString)
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
    class TokenErrorResponse(response: Response) {

        @JvmField var error: String? = null
        @JvmField var errorDescription: String? = null

        init {
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

        @JvmField var authToken: String? = null
        @JvmField var refreshToken: String? = null
        @JvmField var instanceUrl: String? = null
        @JvmField var apiInstanceUrl: String? = null
        @JvmField var idUrl: String? = null
        @JvmField var idUrlWithInstance: String? = null
        @JvmField var orgId: String? = null
        @JvmField var userId: String? = null
        @JvmField var code: String? = null
        @JvmField var communityId: String? = null
        @JvmField var communityUrl: String? = null
        @JvmField var additionalOauthValues: Map<String, String>? = null
        @JvmField var idToken: String? = null
        @JvmField var lightningDomain: String? = null
        @JvmField var lightningSid: String? = null
        @JvmField var vfDomain: String? = null
        @JvmField var vfSid: String? = null
        @JvmField var contentDomain: String? = null
        @JvmField var contentSid: String? = null
        @JvmField var csrfToken: String? = null
        @JvmField var cookieClientSrc: String? = null
        @JvmField var cookieSidClient: String? = null
        @JvmField var sidCookieName: String? = null
        @JvmField var parentSid: String? = null
        @JvmField var tokenFormat: String? = null
        @JvmField var beaconChildConsumerKey: String? = null
        @JvmField var beaconChildConsumerSecret: String? = null
        @JvmField var scope: String? = null

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
                    val values = HashMap<String, String>()
                    for (key in additionalOauthKeys) {
                        if (!TextUtils.isEmpty(key)) {
                            val v = callbackUrlParams[key]
                            if (v != null) {
                                values[key] = v
                            }
                        }
                    }
                    additionalOauthValues = values
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
            else null
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
                    val values = HashMap<String, String>()
                    for (key in additionalOauthKeys) {
                        if (!TextUtils.isEmpty(key)) {
                            val value = parsedResponse.optString(key)
                            values[key] = value
                        }
                    }
                    additionalOauthValues = values
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
            else null
        )

        @Throws(URISyntaxException::class)
        private fun computeOtherFields() {
            idUrlWithInstance = idUrl!!.replace(URI(idUrl).host, URI(instanceUrl).host)
            val idUrlFragments = idUrl!!.split("/")
            userId = idUrlFragments[idUrlFragments.size - 1]
            orgId = idUrlFragments[idUrlFragments.size - 2]
        }
    }

    companion object {
        private const val ACCESS_TOKEN = "access_token"
        @JvmField internal val CLIENT_ID = "client_id"
        @JvmField internal val GRANT_TYPE = "grant_type"
        private const val ERROR = "error"
        private const val ERROR_DESCRIPTION = "error_description"
        @JvmField internal val FORMAT = "format"
        private const val ID = "id"
        private const val INSTANCE_URL = "instance_url"
        private const val API_INSTANCE_URL = "api_instance_url"
        @JvmField internal val JSON = "json"
        private const val MOBILE_POLICY = "mobile_policy"
        private const val SCREEN_LOCK_TIMEOUT = "screen_lock"
        private const val BIOMETRIC_AUTHENTICATION = "ENABLE_BIOMETRIC_AUTHENTICATION"
        private const val BIOMETRIC_AUTHENTICATION_TIMEOUT = "BIOMETRIC_AUTHENTICATION_TIMEOUT"
        private const val BIOMETRIC_AUTHENTICATION_DEFAULT_TIMEOUT = 15
        private const val HYBRID_REFRESH = "hybrid_refresh"
        const val LOGIN_HINT = "login_hint"
        private const val REFRESH_TOKEN = "refresh_token"

        /**
         * OAuth 2.0 authorization endpoint request body parameter names:
         * Salesforce App Attestation External Client App Attestation.
         *
         * This method is not intended for public use outside of Salesforce Mobile SDK.
         *
         * TODO: Make this internal when no longer referenced by Java. ECJ20260421
         */
        const val ATTESTATION = "attestation"
        @JvmField internal val RESPONSE_TYPE = "response_type"
        private const val SCOPE = "scope"
        @JvmField internal val REDIRECT_URI = "redirect_uri"
        private const val DEVICE_ID = "device_id"
        private const val TOKEN = "token"
        private const val HYBRID_TOKEN = "hybrid_token"
        private const val USERNAME = "username"
        private const val EMAIL = "email"
        private const val FIRST_NAME = "first_name"
        private const val LAST_NAME = "last_name"
        private const val DISPLAY_NAME = "display_name"
        private const val PHOTOS = "photos"
        private const val PICTURE = "picture"
        private const val THUMBNAIL = "thumbnail"
        @JvmField internal val AUTHORIZATION_CODE = "authorization_code"
        @JvmField internal val HYBRID_AUTH_CODE = "hybrid_auth_code"
        @JvmField internal val CODE = "code"
        @JvmField internal val CODE_CHALLENGE = "code_challenge"
        @JvmField internal val CODE_VERIFIER = "code_verifier"
        private const val CUSTOM_ATTRIBUTES = "custom_attributes"
        private const val CUSTOM_PERMISSIONS = "custom_permissions"
        private const val SFDC_COMMUNITY_ID = "sfdc_community_id"
        @JvmField internal val SFDC_COMMUNITY_URL = "sfdc_community_url"
        private const val ID_TOKEN = "id_token"
        private const val AND = "&"
        private const val EQUAL = "="
        private const val QUESTION = "?"
        private const val TOUCH = "touch"
        private const val FRONTDOOR = "/secur/frontdoor.jsp?"
        const val FRONTDOOR_URL_KEY = "frontdoor_uri"
        private const val SID = "sid"
        private const val RETURL = "retURL"
        @JvmField internal val AUTHORIZATION = "Authorization"
        private const val BEARER = "Bearer "
        private const val ASSERTION = "assertion"
        private const val JWT_BEARER = "urn:ietf:params:oauth:grant-type:jwt-bearer"
        @JvmField internal val OAUTH_AUTH_PATH = "/services/oauth2/authorize"
        private const val REVOKE_REASON = "revoke_reason"

        /** Endpoint path for Salesforce Identity API initialize headless, password-less login flow */
        @JvmStatic internal var OAUTH_ENDPOINT_HEADLESS_INIT_PASSWORDLESS_LOGIN = "/services/auth/headless/init/passwordless/login"

        /** Endpoint path for Salesforce Identity API initialize headless registration flow */
        @JvmStatic internal var OAUTH_ENDPOINT_HEADLESS_INIT_REGISTRATION = "/services/auth/headless/init/registration"

        /** Endpoint path for Salesforce Identity API headless forgot password flow */
        @JvmStatic internal var OAUTH_ENDPOINT_HEADLESS_FORGOT_PASSWORD = "/services/auth/headless/forgot_password"

        private const val OAUTH_DISPLAY_PARAM = "?display="
        @JvmField internal val OAUTH_TOKEN_PATH = "/services/oauth2/token"
        private const val OAUTH_REVOKE_PATH = "/services/oauth2/revoke"
        private const val LIGHTNING_DOMAIN = "lightning_domain"
        private const val LIGHTNING_SID = "lightning_sid"
        private const val VF_DOMAIN = "visualforce_domain"
        private const val VF_SID = "visualforce_sid"
        private const val CONTENT_DOMAIN = "content_domain"
        private const val CONTENT_SID = "content_sid"
        private const val CSRF_TOKEN = "csrf_token"
        private const val EMPTY_STRING = ""
        private const val FORWARD_SLASH = "/"
        private const val TAG = "OAuth2"
        private const val ID_URL = "id"
        private const val ASSERTED_USER = "asserted_user"
        private const val USER_ID = "user_id"
        private const val ORG_ID = "organization_id"
        private const val NICKNAME = "nick_name"
        private const val URLS = "urls"
        private const val ENTERPRISE_SOAP_URL = "enterprise"
        private const val METADATA_SOAP_URL = "metadata"
        private const val PARTNER_SOAP_URL = "partner"
        private const val REST_URL = "rest"
        private const val REST_SOBJECTS_URL = "sobjects"
        private const val REST_SEARCH_URL = "search"
        private const val REST_QUERY_URL = "query"
        private const val REST_RECENT_URL = "recent"
        private const val PROFILE_URL = "profile"
        private const val CHATTER_FEEDS_URL = "feeds"
        private const val CHATTER_GROUPS_URL = "groups"
        private const val CHATTER_USERS_URL = "users"
        private const val CHATTER_FEED_ITEMS_URL = "feed_items"
        private const val IS_ACTIVE = "active"
        private const val USER_TYPE = "user_type"
        private const val LANGUAGE = "language"
        private const val LOCALE = "locale"
        private const val UTC_OFFSET = "utcOffset"
        private const val LAST_MODIFIED_DATE = "last_modified_date"
        private const val NATIVE_LOGIN = "nativeLogin"
        private const val COOKIE_CLIENT_SRC = "cookie-clientSrc"
        private const val COOKIE_SID_CLIENT = "cookie-sid_Client"
        private const val SID_COOKIE_NAME = "sidCookieName"
        private const val PARENT_SID = "parent_sid"
        private const val TOKEN_FORMAT = "token_format"
        private const val BEACON_CHILD_CONSUMER_SECRET = "beacon_child_consumer_secret"
        private const val BEACON_CHILD_CONSUMER_KEY = "beacon_child_consumer_key"

        @JvmField
        val TIMESTAMP_FORMAT: DateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        /**
         * Builds the URL to the authorization web page for this login server.
         * You need not provide the 'refresh_token' scope, as it is provided automatically.
         *
         * This overload defaults `loginHint` to null and does not enable Salesforce Welcome Login hint.
         *
         * @param useWebServerAuthentication True to use web server flow, False to use user agent flow.
         * @param useHybridAuthentication True to use "hybrid" flow.
         * @param loginServer Base protocol and server to use (e.g. https://login.salesforce.com).
         * @param clientId OAuth client ID.
         * @param callbackUrl OAuth callback URL or redirect URL.
         * @param scopes A list of OAuth scopes to request. If null, the default OAuth scope is provided.
         * @param displayType OAuth display type. If null, the default of 'touch' is used.
         * @param codeChallenge Code challenge to use when using web server flow.
         * @param addlParams Any additional parameters.
         * @return A URL to start the OAuth flow in a web browser/view.
         */
        @JvmStatic
        fun getAuthorizationUrl(
            useWebServerAuthentication: Boolean,
            useHybridAuthentication: Boolean,
            loginServer: URI,
            clientId: String,
            callbackUrl: String,
            scopes: Array<String>?,
            displayType: String?,
            codeChallenge: String?,
            addlParams: Map<String, String>?
        ): URI {
            return getAuthorizationUrl(
                useWebServerAuthentication,
                useHybridAuthentication,
                loginServer,
                clientId,
                callbackUrl,
                scopes,
                null,
                displayType,
                codeChallenge,
                addlParams
            )
        }

        /**
         * Builds the URL to the authorization web page for this login server.
         * You need not provide the 'refresh_token' scope, as it is provided automatically.
         *
         * @param useWebServerAuthentication True to use web server flow, False to use user agent flow.
         * @param useHybridAuthentication True to use "hybrid" flow.
         * @param loginServer Base protocol and server to use (e.g. https://login.salesforce.com).
         * @param clientId OAuth client ID.
         * @param callbackUrl OAuth callback URL or redirect URL.
         * @param scopes A list of OAuth scopes to request. If null, the default OAuth scope is provided.
         * @param loginHint When applicable, the Salesforce Welcome Login hint.
         * @param displayType OAuth display type. If null, the default of 'touch' is used.
         * @param codeChallenge Code challenge to use when using web server flow.
         * @param addlParams Any additional parameters.
         * @return A URL to start the OAuth flow in a web browser/view.
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
                useWebServerAuthentication -> CODE
                useHybridAuthentication -> HYBRID_TOKEN
                else -> TOKEN
            }
            sb.append(OAUTH_AUTH_PATH).append(getBrandedLoginPath())
            sb.append(OAUTH_DISPLAY_PARAM).append(displayType ?: TOUCH)
            sb.append(AND).append(RESPONSE_TYPE).append(EQUAL).append(responseType)
            sb.append(AND).append(CLIENT_ID).append(EQUAL).append(Uri.encode(clientId))
            if (scopes != null && scopes.isNotEmpty()) {
                sb.append(AND).append(SCOPE).append(EQUAL).append(Uri.encode(computeScopeParameter(scopes)))
            }
            if (!TextUtils.isEmpty(loginHint)) {
                sb.append(AND).append(LOGIN_HINT).append(EQUAL).append(Uri.encode(loginHint))
            }
            sb.append(AND).append(REDIRECT_URI).append(EQUAL).append(callbackUrl)
            sb.append(AND).append(DEVICE_ID).append(EQUAL).append(SalesforceSDKManager.getInstance().deviceId)
            if (useWebServerAuthentication) {
                sb.append(AND).append(CODE_CHALLENGE).append(EQUAL).append(Uri.encode(codeChallenge))
            }
            if (addlParams != null && addlParams.isNotEmpty()) {
                for ((key, value) in addlParams) {
                    val v = value ?: EMPTY_STRING
                    sb.append(AND).append(key).append(EQUAL).append(Uri.encode(v))
                }
            }
            return URI.create(sb.toString())
        }

        @JvmStatic
        private fun getBrandedLoginPath(): String {
            var brandedLoginPath = SalesforceSDKManager.getInstance().loginBrand
            if (brandedLoginPath == null || brandedLoginPath.trim().isEmpty()) {
                brandedLoginPath = EMPTY_STRING
            } else {
                if (!brandedLoginPath.startsWith(FORWARD_SLASH)) {
                    brandedLoginPath = FORWARD_SLASH + brandedLoginPath
                }
                if (brandedLoginPath.endsWith(FORWARD_SLASH)) {
                    brandedLoginPath = brandedLoginPath.substring(0, brandedLoginPath.length - 1)
                }
            }
            return brandedLoginPath
        }

        /**
         * Returns a 'frontdoor'ed URL.
         * Front door will authenticate client navigating to that URL using given access token.
         *
         * @param url the URL to "frontdoor".
         * @param accessToken access token to use as sid.
         * @param instanceURL instance url for the sid.
         * @param addlParams additional parameters.
         * @return 'frontdoor'ed URL (or the original url if access token or instance url are null).
         * @deprecated Use [com.salesforce.androidsdk.rest.RestRequest.getRequestForSingleAccess] instead.
         */
        @JvmStatic
        @Deprecated("Use RestRequest.getRequestForSingleAccess(String) instead")
        fun getFrontdoorUrl(
            url: URI,
            accessToken: String?,
            instanceURL: String?,
            addlParams: Map<String, String>?
        ): URI {
            if (accessToken == null || instanceURL == null) {
                return url
            }
            val sb = StringBuilder(instanceURL)
            sb.append(FRONTDOOR)
            sb.append(SID).append(EQUAL).append(accessToken)
            sb.append(AND).append(RETURL).append(EQUAL).append(Uri.encode(url.toString()))
            if (addlParams != null && addlParams.isNotEmpty()) {
                for ((key, value) in addlParams) {
                    val v = value ?: EMPTY_STRING
                    sb.append(AND).append(key).append(EQUAL).append(Uri.encode(v))
                }
            }
            return URI.create(sb.toString())
        }

        /**
         * Computes the scope parameter from an array of scopes.
         *
         * @param scopes Array of scopes.
         * @return Scope parameter string (possibly empty).
         */
        @JvmStatic
        fun computeScopeParameter(scopes: Array<String>): String {
            return ScopeParser.computeScopeParameter(scopes)
        }

        /**
         * Exchange code for credentials.
         *
         * @param httpAccessor HTTPAccess instance.
         * @param loginServer Login server.
         * @param clientId Client ID.
         * @param code Code returned from the IDP.
         * @param codeVerifier Code verifier used to generate 'code_challenge'.
         * @param callbackUrl Callback URL.
         * @return Full set of credentials.
         * @throws OAuthFailedException See [OAuthFailedException].
         * @throws IOException See [IOException].
         */
        @JvmStatic
        @Throws(OAuthFailedException::class, IOException::class)
        fun exchangeCode(
            httpAccessor: HttpAccess,
            loginServer: URI,
            clientId: String,
            code: String,
            codeVerifier: String,
            callbackUrl: String
        ): TokenEndpointResponse {
            return exchangeCode(httpAccessor, loginServer, clientId, code, codeVerifier, callbackUrl, SalesforceSDKManager.getInstance())
        }

        /**
         * An internal, testable Salesforce Mobile SDK overload of
         * [exchangeCode].
         */
        @JvmStatic
        @Throws(OAuthFailedException::class, IOException::class)
        fun exchangeCode(
            httpAccessor: HttpAccess,
            loginServer: URI,
            clientId: String,
            code: String,
            codeVerifier: String,
            callbackUrl: String,
            salesforceSdkManager: SalesforceSDKManager
        ): TokenEndpointResponse {
            val builder = FormBody.Builder()
            val useHybridAuthentication = SalesforceSDKManager.getInstance().useHybridAuthentication
            val grantType = if (useHybridAuthentication) HYBRID_AUTH_CODE else AUTHORIZATION_CODE
            builder.add(GRANT_TYPE, grantType)
            builder.add(CLIENT_ID, clientId)
            builder.add(FORMAT, JSON)
            builder.add(CODE, code)
            builder.add(CODE_VERIFIER, codeVerifier)
            builder.add(REDIRECT_URI, callbackUrl)
            return makeTokenEndpointRequest(httpAccessor, loginServer, builder, salesforceSdkManager)
        }

        /**
         * Gets a new auth token using the refresh token.
         *
         * @param httpAccessor HttpAccess instance.
         * @param loginServer Login server.
         * @param clientId Client ID.
         * @param refreshToken Refresh token.
         * @param addlParams Additional parameters.
         * @return Token response.
         * @throws OAuthFailedException See [OAuthFailedException].
         * @throws IOException See [IOException].
         */
        @JvmStatic
        @Throws(OAuthFailedException::class, IOException::class)
        fun refreshAuthToken(
            httpAccessor: HttpAccess?,
            loginServer: URI,
            clientId: String,
            refreshToken: String,
            addlParams: Map<String, String>?
        ): TokenEndpointResponse {
            val builder = FormBody.Builder()
            val useHybridAuthentication = SalesforceSDKManager.getInstance().useHybridAuthentication
            val grantType = if (useHybridAuthentication) HYBRID_REFRESH else REFRESH_TOKEN
            builder.add(GRANT_TYPE, grantType)
            builder.add(CLIENT_ID, clientId)
            builder.add(REFRESH_TOKEN, refreshToken)
            builder.add(FORMAT, JSON)
            if (addlParams != null) {
                for ((key, value) in addlParams) {
                    // Safely ignore missing values since, for instance, a user account that is being upgraded may not have received that value yet.
                    if (value != null) {
                        builder.add(key, value)
                    }
                }
            }
            return makeTokenEndpointRequest(httpAccessor!!, loginServer, builder, SalesforceSDKManager.getInstance())
        }

        /**
         * Revokes the existing refresh token.
         *
         * @param httpAccessor HttpAccess instance.
         * @param loginServer Login server.
         * @param refreshToken Refresh token.
         * @param reason The reason the refresh token is being revoked.
         */
        @JvmStatic
        fun revokeRefreshToken(httpAccessor: HttpAccess, loginServer: URI, refreshToken: String, reason: LogoutReason) {
            val request = buildRevokeRefreshTokenRequest(loginServer, refreshToken, reason)
            try {
                httpAccessor.okHttpClient.newCall(request).execute()
            } catch (e: IOException) {
                SalesforceSDKLogger.w(TAG, "Exception thrown while revoking refresh token", e)
            }
        }

        @JvmStatic
        protected fun buildRevokeRefreshTokenRequest(loginServer: URI, refreshToken: String, reason: LogoutReason): Request {
            val requestUrl = loginServer.toString() + OAUTH_REVOKE_PATH
            val body = FormBody.Builder()
                .add(TOKEN, refreshToken)
                .add(REVOKE_REASON, reason.toString())
                .build()
            return Request.Builder().url(requestUrl).post(body).build()
        }

        /**
         * Swaps a JWT for regular OAuth tokens.
         *
         * @param httpAccessor HttpAccess instance.
         * @param loginServerUrl The server that the auth code was generated from.
         * @param jwt JWT issued by the OAuth authorization flow.
         * @throws IOException See [IOException].
         * @throws OAuthFailedException See [OAuthFailedException].
         */
        @JvmStatic
        @Throws(IOException::class, OAuthFailedException::class)
        fun swapJWTForTokens(
            httpAccessor: HttpAccess,
            loginServerUrl: URI,
            jwt: String
        ): TokenEndpointResponse {
            val formBodyBuilder = FormBody.Builder()
                .add(GRANT_TYPE, JWT_BEARER)
                .add(ASSERTION, jwt)
            return makeTokenEndpointRequest(httpAccessor, loginServerUrl, formBodyBuilder, SalesforceSDKManager.getInstance())
        }

        /**
         * Calls the identity service to determine the username of the user and the mobile policy.
         *
         * @param httpAccessor HttpAccessor instance.
         * @param identityServiceIdUrl Identity service URL.
         * @param authToken Access token.
         * @return IdServiceResponse instance.
         * @throws IOException See [IOException].
         */
        @JvmStatic
        @Throws(IOException::class)
        fun callIdentityService(
            httpAccessor: HttpAccess,
            identityServiceIdUrl: String,
            authToken: String
        ): IdServiceResponse {
            val builder = Request.Builder().url(identityServiceIdUrl).get()
            addAuthorizationHeader(builder, authToken)
            val request = builder.build()
            val response = httpAccessor.okHttpClient.newCall(request).execute()
            return IdServiceResponse(response)
        }

        /**
         * Adds the authorization header to request builder.
         *
         * @param builder Builder instance.
         * @param authToken Access token.
         */
        @JvmStatic
        fun addAuthorizationHeader(builder: Request.Builder, authToken: String): Request.Builder {
            return builder.header(AUTHORIZATION, BEARER + authToken)
        }

        @JvmStatic
        @VisibleForTesting
        @WorkerThread
        @Throws(OAuthFailedException::class, IOException::class)
        fun makeTokenEndpointRequest(
            httpAccessor: HttpAccess,
            loginServer: URI,
            formBodyBuilder: FormBody.Builder,
            salesforceSdkManager: SalesforceSDKManager
        ): TokenEndpointResponse {
            val sb = StringBuilder(loginServer.toString())
            sb.append(OAUTH_TOKEN_PATH)
            sb.append(QUESTION).append(DEVICE_ID).append(EQUAL).append(salesforceSdkManager.deviceId)

            val appAttestationClient = salesforceSdkManager.appAttestationClient
            val challenge = appAttestationClient?.fetchMobileAppAttestationChallengeBlocking()
            val attestationValue = if (challenge != null) appAttestationClient.createAppAttestationBlocking(challenge) else null
            if (attestationValue != null) {
                // Note: The attestation value is appended to the token endpoint
                // query string without Uri.encode by design. The value produced
                // by OAuthAuthorizationAttestation.toBase64String() is accepted
                // as-is by the Salesforce token endpoint's server-side contract.
                // This has been verified end-to-end; do not wrap in Uri.encode.
                sb.append(AND).append(ATTESTATION).append(EQUAL).append(attestationValue)
            }

            val refreshPath = sb.toString()
            val body = formBodyBuilder.build()
            val request = Request.Builder().url(refreshPath).post(body).build()
            val response = httpAccessor.okHttpClient.newCall(request).execute()
            return if (response.isSuccessful) {
                TokenEndpointResponse(response)
            } else {
                throw OAuthFailedException(TokenErrorResponse(response), response.code)
            }
        }

        /**
         * Fetches an OpenID token from the Salesforce backend.
         *
         * @param loginServer Login server.
         * @param clientId Client ID.
         * @param refreshToken Refresh token.
         * @return OpenID token.
         */
        @JvmStatic
        fun getOpenIDToken(loginServer: String, clientId: String, refreshToken: String): String? {
            var idToken: String? = null
            try {
                val tr = refreshAuthToken(
                    HttpAccess.DEFAULT,
                    URI(loginServer), clientId, refreshToken, null
                )
                idToken = tr.idToken
            } catch (e: Exception) {
                SalesforceSDKLogger.e(TAG, "Exception thrown while fetching OpenID token", e)
            }
            return idToken
        }
    }
}
