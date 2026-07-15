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
package com.salesforce.androidsdk.accounts

import android.accounts.AccountManager
import android.app.DownloadManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import com.salesforce.androidsdk.app.Features
import com.salesforce.androidsdk.app.SalesforceSDKManager
import com.salesforce.androidsdk.auth.ScopeParser
import com.salesforce.androidsdk.util.MapUtil
import com.salesforce.androidsdk.util.SalesforceSDKLogger
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File

/**
 * This class represents a single user account that is currently
 * logged in against a Salesforce endpoint. It encapsulates data
 * that is used to uniquely identify a single user account.
 *
 * @author bhariharan
 */
class UserAccount {

    /**
     * Returns the auth token for this user account.
     */
    var authToken: String? = null
        internal set

    private var _refreshToken: String? = null

    /**
     * Returns the refresh token for this user account.
     *
     * This property consults [AccountManager] for the most current refresh token,
     * falling back to the in-memory value if unavailable.
     */
    val refreshToken: String?
        get() {
            if (accountName != null) {
                try {
                    val accMgr = AccountManager.get(SalesforceSDKManager.getInstance().appContext)
                    val accountType = SalesforceSDKManager.getInstance().accountType
                    for (account in accMgr.getAccountsByType(accountType)) {
                        if (accountName == account.name) {
                            val encryptedPassword = accMgr.getPassword(account)
                            if (encryptedPassword != null) {
                                val newestRefreshToken = SalesforceSDKManager.decrypt(
                                    encryptedPassword, SalesforceSDKManager.encryptionKey
                                )
                                if (newestRefreshToken != null) {
                                    return newestRefreshToken
                                }
                            }
                            break
                        }
                    }
                } catch (e: Exception) {
                    SalesforceSDKLogger.w(
                        TAG,
                        "Failed to read latest refresh token from AccountManager; using in-memory value",
                        e
                    )
                }
            }
            return _refreshToken
        }

    /**
     * Returns the in-memory refresh token snapshot, without consulting
     * [AccountManager].
     *
     * Intended for persistence call sites that are about to write a refresh
     * token to storage (account creation, update, token migration, duplicate
     * user reconciliation). Using [refreshToken] at those sites would read
     * back the value currently persisted in [AccountManager] — i.e. the value
     * we are about to overwrite — instead of the value the caller built this
     * [UserAccount] with.
     *
     * @return The refresh token field as set at construction time.
     */
    val refreshTokenForPersistence: String?
        get() = _refreshToken

    /**
     * Returns the login server for this user account.
     */
    var loginServer: String? = null
        internal set

    /**
     * Returns the identity URL for this user account.
     */
    var idUrl: String? = null
        internal set

    /**
     * Returns the instance server for this user account.
     */
    var instanceServer: String? = null
        internal set

    /**
     * Returns the API instance server for this user account.
     */
    var apiInstanceServer: String? = null
        internal set

    /**
     * Returns the org ID for this user account.
     */
    var orgId: String? = null
        internal set

    /**
     * Returns the user ID for this user account.
     */
    var userId: String? = null
        internal set

    /**
     * Returns the username for this user account.
     */
    var username: String? = null
        internal set

    /**
     * Returns the account name for this user account.
     */
    var accountName: String? = null
        internal set

    /**
     * Returns the community ID for this user account.
     */
    var communityId: String? = null
        internal set

    /**
     * Returns the community URL for this user account.
     */
    var communityUrl: String? = null
        internal set

    /**
     * Returns the first name for this user account.
     */
    var firstName: String? = null
        internal set

    /**
     * Returns the last name for this user account.
     */
    var lastName: String? = null
        internal set

    /**
     * Returns the display name for this user account.
     */
    var displayName: String? = null
        internal set

    /**
     * Returns the email for this user account.
     */
    var email: String? = null
        internal set

    /**
     * Returns the photo URL for this user.
     */
    var photoUrl: String? = null
        internal set

    /**
     * Returns the thumbnail URL for this user.
     */
    var thumbnailUrl: String? = null
        internal set

    /**
     * Returns the Lightning domain for this user.
     */
    var lightningDomain: String? = null
        internal set

    /**
     * Returns the Lightning SID for this user.
     */
    var lightningSid: String? = null
        internal set

    /**
     * Returns the VF domain for this user.
     */
    @get:JvmName("getVFDomain")
    var vfDomain: String? = null
        internal set

    /**
     * Returns the VF SID for this user.
     */
    @get:JvmName("getVFSid")
    var vfSid: String? = null
        internal set

    /**
     * Returns the content domain for this user.
     */
    var contentDomain: String? = null
        internal set

    /**
     * Returns the content SID for this user.
     */
    var contentSid: String? = null
        internal set

    /**
     * Returns the CSRF token for this user.
     */
    @get:JvmName("getCSRFToken")
    var csrfToken: String? = null
        internal set

    /**
     * Whether or not the user was added through native headless authentication.
     */
    var nativeLogin: Boolean = false
        internal set

    /**
     * Returns the language for this user.
     */
    var language: String? = null
        internal set

    /**
     * Returns the locale for this user.
     */
    var locale: String? = null
        internal set

    /**
     * Returns the cookie client src.
     */
    var cookieClientSrc: String? = null
        internal set

    /**
     * Returns the cookie sid client.
     */
    var cookieSidClient: String? = null
        internal set

    /**
     * Returns the sid cookie name.
     */
    var sidCookieName: String? = null
        internal set

    /**
     * Returns the oauth client id.
     */
    var clientId: String? = null
        internal set

    /**
     * Returns the parent sid.
     */
    var parentSid: String? = null
        internal set

    /**
     * Returns the token format.
     */
    var tokenFormat: String? = null
        internal set

    /**
     * Returns the additional OAuth values for this user.
     */
    var additionalOauthValues: Map<String, String>? = null
        internal set

    /**
     * Returns the beacon child consumer key.
     */
    var beaconChildConsumerKey: String? = null
        internal set

    /**
     * Returns the beacon child consumer secret.
     */
    var beaconChildConsumerSecret: String? = null
        internal set

    /**
     * Returns the OAuth scopes returned by the token endpoint.
     */
    var scope: String? = null
        internal set

    private var _featureFlags: MutableSet<String> = HashSet()

    /**
     * The persisted per-user feature flags (e.g. BW, SU, MS). Reads return an
     * unmodifiable snapshot; writes replace the in-memory set with a copy.
     */
    var featureFlags: Set<String>
        get() = _featureFlags.toSet()
        set(flags) {
            _featureFlags = HashSet(flags)
        }

    /**
     * Returns the OAuth client id to use for refresh.
     * In the case of beacon app, the beacon child consumer key returned during login
     * should be used instead of the configured consumer key.
     */
    val clientIdForRefresh: String?
        get() = if (!TextUtils.isEmpty(beaconChildConsumerKey)) beaconChildConsumerKey else clientId

    /**
     * Parameterized constructor.
     *
     * @param authToken                 Auth token.
     * @param refreshToken              Refresh token.
     * @param loginServer               Login server.
     * @param idUrl                     Identity URL.
     * @param instanceServer            Instance server.
     * @param orgId                     Org ID.
     * @param userId                    User ID.
     * @param username                  Username.
     * @param accountName               Account name.
     * @param communityId               Community ID.
     * @param communityUrl              Community URL.
     * @param firstName                 First Name.
     * @param lastName                  Last Name.
     * @param displayName               Display Name.
     * @param email                     Email.
     * @param photoUrl                  Photo URL.
     * @param thumbnailUrl              Thumbnail URL.
     * @param additionalOauthValues     Additional OAuth values.
     * @param lightningDomain           Lightning domain.
     * @param lightningSid              Lightning SID.
     * @param vfDomain                  VF domain.
     * @param vfSid                     VF SID.
     * @param contentDomain             Content domain.
     * @param contentSid                Content SID.
     * @param csrfToken                 CSRF token.
     * @param nativeLogin               If the account was added with native auth.
     * @param language                  User's language.
     * @param locale                    User's locale.
     * @param cookieClientSrc           Cookie client src.
     * @param cookieSidClient           Cookie sid client.
     * @param sidCookieName             Sid cookie name.
     * @param clientId                  OAuth client id.
     * @param parentSid                 Parent sid.
     * @param tokenFormat               Token format.
     * @param beaconChildConsumerKey    Beacon child consumer key.
     * @param beaconChildConsumerSecret Beacon child consumer secret.
     * @param apiInstanceServer         API instance server.
     * @param scope                     Scope.
     */
    internal constructor(
        authToken: String?,
        refreshToken: String?,
        loginServer: String?,
        idUrl: String?,
        instanceServer: String?,
        orgId: String?,
        userId: String?,
        username: String?,
        accountName: String?,
        communityId: String?,
        communityUrl: String?,
        firstName: String?,
        lastName: String?,
        displayName: String?,
        email: String?,
        photoUrl: String?,
        thumbnailUrl: String?,
        additionalOauthValues: Map<String, String>?,
        lightningDomain: String?,
        lightningSid: String?,
        vfDomain: String?,
        vfSid: String?,
        contentDomain: String?,
        contentSid: String?,
        csrfToken: String?,
        nativeLogin: Boolean,
        language: String?,
        locale: String?,
        cookieClientSrc: String?,
        cookieSidClient: String?,
        sidCookieName: String?,
        clientId: String?,
        parentSid: String?,
        tokenFormat: String?,
        beaconChildConsumerKey: String?,
        beaconChildConsumerSecret: String?,
        apiInstanceServer: String?,
        scope: String?
    ) {
        this.authToken = authToken
        this._refreshToken = refreshToken
        this.loginServer = loginServer
        this.idUrl = idUrl
        this.instanceServer = instanceServer
        this.apiInstanceServer = apiInstanceServer
        this.orgId = orgId
        this.userId = userId
        this.username = username
        this.accountName = accountName
        this.communityId = communityId
        this.communityUrl = communityUrl
        this.firstName = firstName
        this.lastName = lastName
        this.displayName = displayName
        this.email = email
        this.photoUrl = photoUrl
        this.thumbnailUrl = thumbnailUrl
        this.additionalOauthValues = additionalOauthValues
        this.lightningDomain = lightningDomain
        this.lightningSid = lightningSid
        this.vfDomain = vfDomain
        this.vfSid = vfSid
        this.contentDomain = contentDomain
        this.contentSid = contentSid
        this.csrfToken = csrfToken
        this.nativeLogin = nativeLogin
        this.language = language
        this.locale = locale
        this.cookieClientSrc = cookieClientSrc
        this.cookieSidClient = cookieSidClient
        this.sidCookieName = sidCookieName
        this.clientId = clientId
        this.parentSid = parentSid
        this.tokenFormat = tokenFormat
        this.beaconChildConsumerKey = beaconChildConsumerKey
        this.beaconChildConsumerSecret = beaconChildConsumerSecret
        this.scope = scope
        SalesforceSDKManager.getInstance().registerUsedAppFeature(Features.FEATURE_USER_AUTH)
    }

    /**
     * Construct UserAccount from JSON.
     *
     * @param object JSON object.
     * @param applicationName The application name.
     * @param additionalOauthKeys Additional auth keys.
     */
    internal constructor(
        jsonObject: JSONObject?,
        applicationName: String,
        additionalOauthKeys: List<String>?
    ) {
        if (jsonObject != null) {
            authToken = jsonObject.optString(AUTH_TOKEN, null)
            _refreshToken = jsonObject.optString(REFRESH_TOKEN, null)
            loginServer = jsonObject.optString(LOGIN_SERVER, null)
            idUrl = jsonObject.optString(ID_URL, null)
            instanceServer = jsonObject.optString(INSTANCE_SERVER, null)
            apiInstanceServer = jsonObject.optString(API_INSTANCE_SERVER, null)
            orgId = jsonObject.optString(ORG_ID, null)
            userId = jsonObject.optString(USER_ID, null)
            username = jsonObject.optString(USERNAME, null)
            if (!TextUtils.isEmpty(username) && !TextUtils.isEmpty(instanceServer)) {
                accountName = String.format("%s (%s) (%s)", username, instanceServer, applicationName)
            }
            communityId = jsonObject.optString(COMMUNITY_ID, null)
            communityUrl = jsonObject.optString(COMMUNITY_URL, null)
            firstName = jsonObject.optString(FIRST_NAME, null)
            lastName = jsonObject.optString(LAST_NAME, null)
            displayName = jsonObject.optString(DISPLAY_NAME, null)
            email = jsonObject.optString(EMAIL, null)
            photoUrl = jsonObject.optString(PHOTO_URL, null)
            thumbnailUrl = jsonObject.optString(THUMBNAIL_URL, null)
            lightningDomain = jsonObject.optString(LIGHTNING_DOMAIN, null)
            lightningSid = jsonObject.optString(LIGHTNING_SID, null)
            vfDomain = jsonObject.optString(VF_DOMAIN, null)
            vfSid = jsonObject.optString(VF_SID, null)
            contentDomain = jsonObject.optString(CONTENT_DOMAIN, null)
            contentSid = jsonObject.optString(CONTENT_SID, null)
            csrfToken = jsonObject.optString(CSRF_TOKEN, null)
            nativeLogin = jsonObject.optBoolean(NATIVE_LOGIN)
            language = jsonObject.optString(LANGUAGE, null)
            locale = jsonObject.optString(LOCALE, null)
            cookieClientSrc = jsonObject.optString(COOKIE_CLIENT_SRC, null)
            cookieSidClient = jsonObject.optString(COOKIE_SID_CLIENT, null)
            sidCookieName = jsonObject.optString(SID_COOKIE_NAME, null)
            clientId = jsonObject.optString(CLIENT_ID, null)
            parentSid = jsonObject.optString(PARENT_SID, null)
            tokenFormat = jsonObject.optString(TOKEN_FORMAT, null)
            beaconChildConsumerKey = jsonObject.optString(BEACON_CHILD_CONSUMER_KEY, null)
            beaconChildConsumerSecret = jsonObject.optString(BEACON_CHILD_CONSUMER_SECRET, null)
            scope = jsonObject.optString(SCOPE, null)
            @Suppress("UNCHECKED_CAST")
            additionalOauthValues = MapUtil.addJSONObjectToMap(
                jsonObject, additionalOauthKeys, additionalOauthValues as? MutableMap<String, String>
            )
        }
    }

    /**
     * Construct UserAccount from JSON.
     *
     * @param jsonObject JSON object.
     */
    constructor(jsonObject: JSONObject?) : this(
        jsonObject,
        SalesforceSDKManager.getInstance().applicationName,
        SalesforceSDKManager.getInstance().additionalOauthKeys
    )

    /**
     * Construct UserAccount from Bundle.
     *
     * @param bundle Bundle.
     * @param additionalOauthKeys Additional oauth keys.
     */
    internal constructor(bundle: Bundle?, additionalOauthKeys: List<String>?) {
        if (bundle != null) {
            authToken = bundle.getString(AUTH_TOKEN)
            _refreshToken = bundle.getString(REFRESH_TOKEN)
            loginServer = bundle.getString(LOGIN_SERVER)
            idUrl = bundle.getString(ID_URL)
            instanceServer = bundle.getString(INSTANCE_SERVER)
            apiInstanceServer = bundle.getString(API_INSTANCE_SERVER)
            orgId = bundle.getString(ORG_ID)
            userId = bundle.getString(USER_ID)
            username = bundle.getString(USERNAME)
            accountName = bundle.getString(ACCOUNT_NAME)
            communityId = bundle.getString(COMMUNITY_ID)
            communityUrl = bundle.getString(COMMUNITY_URL)
            firstName = bundle.getString(FIRST_NAME)
            lastName = bundle.getString(LAST_NAME)
            displayName = bundle.getString(DISPLAY_NAME)
            email = bundle.getString(EMAIL)
            photoUrl = bundle.getString(PHOTO_URL)
            thumbnailUrl = bundle.getString(THUMBNAIL_URL)
            lightningDomain = bundle.getString(LIGHTNING_DOMAIN)
            lightningSid = bundle.getString(LIGHTNING_SID)
            vfDomain = bundle.getString(VF_DOMAIN)
            vfSid = bundle.getString(VF_SID)
            contentDomain = bundle.getString(CONTENT_DOMAIN)
            contentSid = bundle.getString(CONTENT_SID)
            csrfToken = bundle.getString(CSRF_TOKEN)
            nativeLogin = bundle.getBoolean(NATIVE_LOGIN)
            language = bundle.getString(LANGUAGE)
            locale = bundle.getString(LOCALE)
            cookieClientSrc = bundle.getString(COOKIE_CLIENT_SRC)
            cookieSidClient = bundle.getString(COOKIE_SID_CLIENT)
            sidCookieName = bundle.getString(SID_COOKIE_NAME)
            clientId = bundle.getString(CLIENT_ID)
            parentSid = bundle.getString(PARENT_SID)
            tokenFormat = bundle.getString(TOKEN_FORMAT)
            beaconChildConsumerKey = bundle.getString(BEACON_CHILD_CONSUMER_KEY)
            beaconChildConsumerSecret = bundle.getString(BEACON_CHILD_CONSUMER_SECRET)
            scope = bundle.getString(SCOPE)
            @Suppress("UNCHECKED_CAST")
            additionalOauthValues = MapUtil.addBundleToMap(
                bundle, additionalOauthKeys, additionalOauthValues as? MutableMap<String, String?>
            ) as? Map<String, String>
        }
    }

    /**
     * Construct UserAccount from Bundle.
     *
     * @param bundle Bundle.
     */
    constructor(bundle: Bundle?) : this(
        bundle,
        SalesforceSDKManager.getInstance().additionalOauthKeys
    )

    /**
     * Checks whether the provided scope exists in this account's scope list.
     *
     * @param scopeToCheck Scope name to check.
     * @return True if present, false otherwise.
     */
    fun hasScope(scopeToCheck: String?): Boolean {
        return ScopeParser(scope).hasScope(scopeToCheck)
    }

    /**
     * Fetches this user's profile photo from the cache.
     *
     * @return User's profile photo.
     */
    val profilePhoto: Bitmap?
        get() {
            val file = profilePhotoFile ?: return null
            val bitmapOptions = BitmapFactory.Options()
            bitmapOptions.inPreferredConfig = Bitmap.Config.ARGB_8888
            return BitmapFactory.decodeFile(file.absolutePath, bitmapOptions)
        }

    /**
     * Fetches this user's profile photo from the server and stores it in the cache.
     */
    fun downloadProfilePhoto() {
        val file = profilePhotoFile
        if (photoUrl == null || file == null) {
            return
        }
        val srcUri = Uri.parse(photoUrl) ?: return
        val destUri = Uri.fromFile(file) ?: return

        // Checks if DownloadManager is enabled on the device, to ensure it doesn't crash.
        val pm = SalesforceSDKManager.getInstance().appContext.packageManager
        val state = pm.getApplicationEnabledSetting("com.android.providers.downloads")
        if (state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
            state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        ) {
            val downloadReq = DownloadManager.Request(srcUri)
            downloadReq.setDestinationUri(destUri)
            downloadReq.addRequestHeader(AUTHORIZATION, BEARER + authToken)
            downloadReq.setNotificationVisibility(DownloadManager.Request.VISIBILITY_HIDDEN)
            @Suppress("DEPRECATION")
            downloadReq.setVisibleInDownloadsUi(false)
            val downloadManager = SalesforceSDKManager.getInstance().appContext
                .getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            downloadManager?.enqueue(downloadReq)
        }
    }

    /**
     * Returns the org level storage path for this user account, relative to
     * the higher level directory of app data. The higher level directory
     * could be 'files'. The output is of the format '/{orgID}/'.
     * This storage path is meant for data that can be shared
     * across multiple users of the same org.
     */
    val orgLevelStoragePath: String
        get() {
            val sb = StringBuilder(FORWARD_SLASH)
            sb.append(orgId)
            sb.append(FORWARD_SLASH)
            return sb.toString()
        }

    /**
     * Returns the user level storage path for this user account, relative to
     * the higher level directory of app data. The higher level directory
     * could be 'files'. The output is of the format '/{orgID}/{userId}/'.
     * This storage path is meant for data that is unique to a particular
     * user in an org, but common across all the communities that the
     * user is a member of within that org.
     */
    val userLevelStoragePath: String
        get() {
            val sb = StringBuilder(FORWARD_SLASH)
            sb.append(orgId)
            sb.append(FORWARD_SLASH)
            sb.append(userId)
            sb.append(FORWARD_SLASH)
            return sb.toString()
        }

    /**
     * Returns the storage path for this user account, relative to the higher
     * level directory of app data. The higher level directory could be 'files'.
     * The output is of the format '/{orgID}/{userID}/{communityID}/'.
     * If 'communityID' is null or the internal community ID, then the output
     * would be '/{orgID}/{userID}/internal/'. This storage path is meant for
     * data that is unique to a particular user in a specific community.
     */
    val communityLevelStoragePath: String
        get() {
            var leafDir = INTERNAL_COMMUNITY_PATH
            if (!TextUtils.isEmpty(communityId) && communityId != INTERNAL_COMMUNITY_ID) {
                leafDir = communityId!!
            }
            return getCommunityLevelStoragePath(leafDir)
        }

    /**
     * Returns the storage path for this user account, relative to the higher
     * level directory of app data. The higher level directory could be 'files'.
     * The output is of the format '/{orgID}/{userID}/{communityID}/'.
     * If 'communityID' is null or the internal community ID, then the output
     * would be '/{orgID}/{userID}/internal/'. This storage path is meant for
     * data that is unique to a particular user in a specific community.
     *
     * @param communityId Community ID. Pass 'null' for internal community.
     * @return File storage path.
     */
    fun getCommunityLevelStoragePath(communityId: String?): String {
        val sb = StringBuilder(FORWARD_SLASH)
        sb.append(orgId)
        sb.append(FORWARD_SLASH)
        sb.append(userId)
        sb.append(FORWARD_SLASH)
        var leafDir = INTERNAL_COMMUNITY_PATH
        if (!TextUtils.isEmpty(communityId) && communityId != INTERNAL_COMMUNITY_ID) {
            leafDir = communityId!!
        }
        sb.append(leafDir)
        sb.append(FORWARD_SLASH)
        return sb.toString()
    }

    /**
     * Returns a unique suffix for this user account, that can be appended
     * to a file to uniquely identify this account, at an org level.
     * The output is of the format '_{orgID}'. This suffix is meant
     * for data that can be shared across multiple users of the same org.
     */
    val orgLevelFilenameSuffix: String
        get() {
            val sb = StringBuilder(UNDERSCORE)
            sb.append(orgId)
            return sb.toString()
        }

    /**
     * Returns a unique suffix for this user account, that can be appended
     * to a file to uniquely identify this account, at a user level.
     * The output is of the format '_{orgID}_{userID}'. This suffix
     * is meant for data that is unique to a particular user in an org,
     * but common across all the communities that the user is a member
     * of within that org.
     */
    val userLevelFilenameSuffix: String
        get() {
            val sb = StringBuilder(UNDERSCORE)
            sb.append(orgId)
            sb.append(UNDERSCORE)
            sb.append(userId)
            return sb.toString()
        }

    /**
     * Returns a unique suffix for this user account, that can be appended
     * to a file to uniquely identify this account, at a community level.
     * The output is of the format '_{orgID}_{userID}_{communityID}'.
     * If 'communityID' is null or the internal community ID, then the output
     * would be '_{orgID}_{userID}_internal'. This storage path is meant for
     * data that is unique to a particular user in a specific community.
     */
    val communityLevelFilenameSuffix: String
        get() {
            var leafDir = INTERNAL_COMMUNITY_PATH
            if (!TextUtils.isEmpty(communityId) && communityId != INTERNAL_COMMUNITY_ID) {
                leafDir = communityId!!
            }
            return getCommunityLevelFilenameSuffix(leafDir)
        }

    /**
     * Returns a unique suffix for this user account, that can be appended
     * to a file to uniquely identify this account, at a community level.
     * The output is of the format '_{orgID}_{userID}_{communityID}'.
     * If 'communityID' is null or the internal community ID, then the output
     * would be '_{orgID}_{userID}_internal'. This storage path is meant for
     * data that is unique to a particular user in a specific community.
     *
     * @param communityId Community ID. Pass 'null' for internal community.
     * @return Filename suffix.
     */
    fun getCommunityLevelFilenameSuffix(communityId: String?): String {
        val sb = StringBuilder(UNDERSCORE)
        sb.append(orgId)
        sb.append(UNDERSCORE)
        sb.append(userId)
        sb.append(UNDERSCORE)
        var leafDir = INTERNAL_COMMUNITY_PATH
        if (!TextUtils.isEmpty(communityId) && communityId != INTERNAL_COMMUNITY_ID) {
            leafDir = communityId!!
        }
        sb.append(leafDir)
        return sb.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is UserAccount) {
            return false
        }
        if (userId == null || orgId == null || other.userId == null || other.orgId == null) {
            return false
        }
        return other.userId == userId && other.orgId == orgId
    }

    override fun hashCode(): Int {
        var result = userId.hashCode()
        result = result xor (orgId.hashCode() + result * 37)
        return result
    }

    /**
     * Returns a JSON representation of this instance.
     *
     * @param additionalOauthKeys Values to pull from the additionalOauthValues.
     * @return JSONObject instance.
     */
    internal fun toJson(additionalOauthKeys: List<String>?): JSONObject {
        var jsonObject = JSONObject()
        try {
            jsonObject.put(AUTH_TOKEN, authToken)
            jsonObject.put(REFRESH_TOKEN, refreshToken)
            jsonObject.put(LOGIN_SERVER, loginServer)
            jsonObject.put(ID_URL, idUrl)
            jsonObject.put(INSTANCE_SERVER, instanceServer)
            jsonObject.put(API_INSTANCE_SERVER, apiInstanceServer)
            jsonObject.put(ORG_ID, orgId)
            jsonObject.put(USER_ID, userId)
            jsonObject.put(USERNAME, username)
            jsonObject.put(COMMUNITY_ID, communityId)
            jsonObject.put(COMMUNITY_URL, communityUrl)
            jsonObject.put(FIRST_NAME, firstName)
            jsonObject.put(LAST_NAME, lastName)
            jsonObject.put(DISPLAY_NAME, displayName)
            jsonObject.put(EMAIL, email)
            jsonObject.put(PHOTO_URL, photoUrl)
            jsonObject.put(THUMBNAIL_URL, thumbnailUrl)
            jsonObject.put(LIGHTNING_DOMAIN, lightningDomain)
            jsonObject.put(LIGHTNING_SID, lightningSid)
            jsonObject.put(VF_DOMAIN, vfDomain)
            jsonObject.put(VF_SID, vfSid)
            jsonObject.put(CONTENT_DOMAIN, contentDomain)
            jsonObject.put(CONTENT_SID, contentSid)
            jsonObject.put(CSRF_TOKEN, csrfToken)
            jsonObject.put(NATIVE_LOGIN, nativeLogin)
            jsonObject.put(LANGUAGE, language)
            jsonObject.put(LOCALE, locale)
            jsonObject.put(COOKIE_CLIENT_SRC, cookieClientSrc)
            jsonObject.put(COOKIE_SID_CLIENT, cookieSidClient)
            jsonObject.put(SID_COOKIE_NAME, sidCookieName)
            jsonObject.put(PARENT_SID, parentSid)
            jsonObject.put(TOKEN_FORMAT, tokenFormat)
            jsonObject.put(BEACON_CHILD_CONSUMER_KEY, beaconChildConsumerKey)
            jsonObject.put(BEACON_CHILD_CONSUMER_SECRET, beaconChildConsumerSecret)
            jsonObject.put(SCOPE, scope)
            if (_featureFlags.isNotEmpty()) {
                val flagsArray = JSONArray()
                for (f in _featureFlags) flagsArray.put(f)
                jsonObject.put(FEATURE_FLAGS, flagsArray)
            }
            jsonObject = MapUtil.addMapToJSONObject(additionalOauthValues, additionalOauthKeys, jsonObject) ?: jsonObject
        } catch (e: JSONException) {
            SalesforceSDKLogger.e(TAG, "Unable to convert to JSON", e)
        }
        return jsonObject
    }

    /**
     * Returns a JSON representation of this instance.
     *
     * @return JSONObject instance.
     */
    fun toJson(): JSONObject {
        return toJson(SalesforceSDKManager.getInstance().additionalOauthKeys)
    }

    /**
     * Returns a representation of this instance in a bundle.
     *
     * @param additionalOauthKeys Values to pull from the additionalOauthValues.
     * @return Bundle instance.
     */
    internal fun toBundle(additionalOauthKeys: List<String>?): Bundle {
        var bundle = Bundle()
        bundle.putString(AUTH_TOKEN, authToken)
        bundle.putString(REFRESH_TOKEN, refreshToken)
        bundle.putString(LOGIN_SERVER, loginServer)
        bundle.putString(ID_URL, idUrl)
        bundle.putString(INSTANCE_SERVER, instanceServer)
        bundle.putString(API_INSTANCE_SERVER, apiInstanceServer)
        bundle.putString(ORG_ID, orgId)
        bundle.putString(USER_ID, userId)
        bundle.putString(USERNAME, username)
        bundle.putString(ACCOUNT_NAME, accountName)
        bundle.putString(COMMUNITY_ID, communityId)
        bundle.putString(COMMUNITY_URL, communityUrl)
        bundle.putString(FIRST_NAME, firstName)
        bundle.putString(LAST_NAME, lastName)
        bundle.putString(DISPLAY_NAME, displayName)
        bundle.putString(EMAIL, email)
        bundle.putString(LANGUAGE, language)
        bundle.putString(LOCALE, locale)
        bundle.putString(PHOTO_URL, photoUrl)
        bundle.putString(THUMBNAIL_URL, thumbnailUrl)
        bundle.putString(LIGHTNING_DOMAIN, lightningDomain)
        bundle.putString(LIGHTNING_SID, lightningSid)
        bundle.putString(VF_DOMAIN, vfDomain)
        bundle.putString(VF_SID, vfSid)
        bundle.putString(CONTENT_DOMAIN, contentDomain)
        bundle.putString(CONTENT_SID, contentSid)
        bundle.putString(CSRF_TOKEN, csrfToken)
        bundle.putBoolean(NATIVE_LOGIN, nativeLogin)
        bundle.putString(COOKIE_CLIENT_SRC, cookieClientSrc)
        bundle.putString(COOKIE_SID_CLIENT, cookieSidClient)
        bundle.putString(SID_COOKIE_NAME, sidCookieName)
        bundle.putString(CLIENT_ID, clientId)
        bundle.putString(PARENT_SID, parentSid)
        bundle.putString(TOKEN_FORMAT, tokenFormat)
        bundle.putString(BEACON_CHILD_CONSUMER_KEY, beaconChildConsumerKey)
        bundle.putString(BEACON_CHILD_CONSUMER_SECRET, beaconChildConsumerSecret)
        bundle.putString(SCOPE, scope)
        bundle = MapUtil.addMapToBundle(additionalOauthValues, additionalOauthKeys, bundle) ?: bundle
        return bundle
    }

    /**
     * Returns a representation of this instance in a bundle.
     *
     * @return Bundle instance.
     */
    fun toBundle(): Bundle {
        return toBundle(SalesforceSDKManager.getInstance().additionalOauthKeys)
    }

    private val profilePhotoFile: File?
        get() {
            val filename = PROFILE_PHOTO_PATH_PREFIX + userLevelFilenameSuffix + JPG
            val baseDir = SalesforceSDKManager.getInstance().appContext.externalCacheDir
            return if (baseDir != null) File(baseDir, filename) else null
        }

    companion object {
        const val AUTH_TOKEN = "authToken"
        const val REFRESH_TOKEN = "refreshToken"
        const val LOGIN_SERVER = "loginServer"
        const val ID_URL = "idUrl"
        const val INSTANCE_SERVER = "instanceServer"
        const val API_INSTANCE_SERVER = "apiInstanceServer"
        const val ORG_ID = "orgId"
        const val USER_ID = "userId"
        const val USERNAME = "username"
        const val ACCOUNT_NAME = "accountName"
        const val COMMUNITY_ID = "communityId"
        const val COMMUNITY_URL = "communityUrl"
        const val INTERNAL_COMMUNITY_ID = "000000000000000AAA"
        const val INTERNAL_COMMUNITY_PATH = "internal"
        const val EMAIL = "email"
        const val FIRST_NAME = "first_name"
        const val DISPLAY_NAME = "display_name"
        const val LAST_NAME = "last_name"
        const val PHOTO_URL = "photoUrl"
        const val THUMBNAIL_URL = "thumbnailUrl"
        const val LIGHTNING_DOMAIN = "lightningDomain"
        const val LIGHTNING_SID = "lightningSid"
        const val VF_DOMAIN = "vfDomain"
        const val VF_SID = "vfSid"
        const val CONTENT_DOMAIN = "contentDomain"
        const val CONTENT_SID = "contentSid"
        const val CSRF_TOKEN = "csrfToken"
        const val NATIVE_LOGIN = "native_login"
        const val LANGUAGE = "language"
        const val LOCALE = "locale"
        const val COOKIE_CLIENT_SRC = "cookie-clientSrc"
        const val COOKIE_SID_CLIENT = "cookie-sid_Client"
        const val SID_COOKIE_NAME = "sidCookieName"
        const val CLIENT_ID = "clientId"
        const val PARENT_SID = "parentSid"
        const val TOKEN_FORMAT = "tokenFormat"
        const val BEACON_CHILD_CONSUMER_KEY = "auto_installed_app_org_consumer_key"
        const val BEACON_CHILD_CONSUMER_SECRET = "auto_installed_app_org_consumer_secret"
        const val SCOPE = "scope"
        const val FEATURE_FLAGS = "feature_flags"

        private const val TAG = "UserAccount"
        private const val FORWARD_SLASH = "/"
        private const val UNDERSCORE = "_"
        private const val PROFILE_PHOTO_PATH_PREFIX = "profile_photo_"
        private const val AUTHORIZATION = "Authorization"
        private const val BEARER = "Bearer "
        private const val JPG = ".jpg"
    }
}
